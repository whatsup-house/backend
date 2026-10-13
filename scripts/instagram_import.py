#!/usr/bin/env python3
"""인스타그램 '내 정보 다운로드' ZIP(HTML 형식)의 게시물·릴스를 피드 게시물로 일괄 등록한다. (KAN-381)

일회성 이관 스크립트. 앱 코드와 무관하며 stdlib 만 사용한다.

준비
  - ffmpeg / ffprobe:  brew install ffmpeg
  - ZIP 압축 해제:      unzip instagram-xxxx.zip -d ~/ig-extract
  - 서버에 R2 설정(R2_*)이 되어 있어야 영상 업로드 URL 발급(POST /api/admin/feed/video-upload-url)이 된다.

환경변수 (--dry-run 에는 필요 없음)
  API_BASE_URL     예: https://api.whatsup.house  (끝의 / 생략)
  ADMIN_EMAIL      관리자 계정 이메일
  ADMIN_PASSWORD   관리자 계정 비밀번호

실행 (dry-run 먼저)
  python3 scripts/instagram_import.py ~/ig-extract --dry-run
  API_BASE_URL=https://api.whatsup.house ADMIN_EMAIL=... ADMIN_PASSWORD=... \\
      python3 scripts/instagram_import.py ~/ig-extract

  --limit N 으로 처음 N개만 올려 볼 수 있다. 다시 돌려도 이미 등록된 게시물(게시 시각 + 캡션 앞부분 일치)은 건너뛴다.
  등록은 모두 visible=false(숨김). 확인 후 관리자 화면에서 노출한다.

동작
  1. posts_1.html + posts.html(여기에만 있는 게시물) + reels.html 파싱. 스토리·보관함은 가져오지 않는다.
  2. 정리: 캡션 없는 항목 제외 → 같은 타입 안 캡션 완전 일치는 최신 1개만 → #서울대입구모임 제거
          → "N월 회고록" 을 "YYYY년 N월 회고록" 으로(회고 월 > 게시 월이면 전년도).
  3. 게시물마다: 사진은 긴 변 1080px jpg 로 줄여 POST /api/images,
     영상은 720p H.264/AAC(+faststart) 재인코딩 → 업로드 URL 발급 → R2 로 PUT, 첫 프레임 포스터는 POST /api/images.
  4. POST /api/admin/feed (visible=false, postedAt=인스타 게시 시각, media 는 원본 순서).
  미디어 파일이 없는 게시물·실패한 게시물은 건너뛰고 끝에 목록을 출력한다.

자체 검사: python3 scripts/test_instagram_import.py
"""

from __future__ import annotations

import argparse
import html
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

MEDIA_DIR = "your_instagram_activity/media"
BOX = "pam _3-95 _2ph- _a6-g uiBoxWhite noborder"
SEOUL_TAG = "#서울대입구모임"
CAPTION_MAX = 2000  # FeedPostCreateRequest.caption @Size(max = 2000)
MEDIA_MAX = 20  # FeedPostCreateRequest.media @Size(max = 20)
VIDEO_MAX_BYTES = 50 * 1024 * 1024  # 업로드 URL 발급 한도
IMAGE_MAX_BYTES = 5 * 1024 * 1024  # multipart 한도
DEDUP_PREFIX_LEN = 30


# ── 파싱 (실제 ZIP 으로 검증된 방식 — 정규식을 바꾸지 말 것) ───────────────────


@dataclass
class Item:
    type: str  # "post" | "reel"
    posted_at: datetime
    caption: str
    media: list[str]  # 압축 해제 루트 기준 상대 경로, 원본 순서
    original_caption: str = ""
    changes: list[str] = field(default_factory=list)

    def label(self) -> str:
        first = self.caption.splitlines()[0] if self.caption else "(캡션 없음)"
        return f"[{self.type}] {self.posted_at:%Y-%m-%d %H:%M} {first[:40]}"


def load(root: Path, filename: str) -> str:
    s = (root / MEDIA_DIR / filename).read_text(encoding="utf-8")
    return re.sub(r"<style.*?</style>", "", s, flags=re.S)


def parse_date(d: str) -> datetime:
    """'10월 07, 2026 5:16 오전' → datetime(2026, 10, 7, 5, 16)"""
    m = re.match(r"(\d+)월 (\d+), (\d+) (\d+):(\d+) (오전|오후)", d.strip())
    if not m:
        raise ValueError(f"알 수 없는 날짜 형식: {d!r}")
    mo, day, y, h, mi, ap = m.groups()
    h = int(h) % 12 + (12 if ap == "오후" else 0)
    return datetime(int(y), int(mo), int(day), h, int(mi))


def entries(s: str) -> list[tuple[str, str]]:
    """(날짜 문자열, 그 날짜 앞의 본문 조각) 목록."""
    ch = re.split(r'<div class="_3-94 _a6-o">([^<]+)</div>', s)
    return [(ch[i + 1], ch[i]) for i in range(0, len(ch) - 1, 2)]


def media_of(c: str, kind: str) -> list[str]:
    return list(dict.fromkeys(re.findall(r'src="(media/%s/[^"]+\.(?:jpg|mp4|webp))"' % kind, c)))


def h2_caption(c: str) -> str:
    c = c[c.rfind(BOX):]
    h = re.findall(r"<h2[^>]*>(.*?)</h2>", c, flags=re.S)
    return html.unescape(h[-1]).strip() if h else ""


def parse_items(root: Path) -> list[Item]:
    items: list[Item] = []
    # 게시물: posts_1.html + posts.html 에만 있는 것
    posts = [(d, h2_caption(c), media_of(c[c.rfind(BOX):], "posts")) for d, c in entries(load(root, "posts_1.html"))]
    known = {m for _, _, ms in posts for m in ms}
    posts_html = root / MEDIA_DIR / "posts.html"
    if posts_html.exists():
        for d, seg in entries(load(root, "posts.html")):
            ms = media_of(seg, "posts")
            if ms and not known & set(ms):
                cap = re.search(r"<img [^>]*/></a><div>(.*?)</div>", seg, flags=re.S)
                posts.append((d, html.unescape(cap.group(1)).strip() if cap else "", ms))
    for d, cap, ms in posts:
        items.append(Item("post", parse_date(d), cap, ms, cap))
    # 릴스
    for d, c in entries(load(root, "reels.html")):
        c = c[c.rfind(BOX):]
        cap = h2_caption(c)
        items.append(Item("reel", parse_date(d), cap, media_of(c, "reels"), cap))
    return items


# ── 정리 규칙 ────────────────────────────────────────────────────────────────


def strip_tag(cap: str) -> str:
    cap = re.sub(r"[ \t]*#서울대입구모임(?![\w가-힣])", "", cap)
    return re.sub(r"[ \t]+$", "", cap, flags=re.M)


def year_retro(cap: str, posted: datetime) -> str:
    """'N월 회고록' → 'YYYY년 N월 회고록'. 회고 월이 게시 월보다 크면 전년도. 이미 연도가 있으면 그대로."""

    def rep(m: re.Match) -> str:
        mo = int(m.group(1))
        y = posted.year - (1 if mo > posted.month else 0)
        return f"{y}년 {mo}월 회고록"

    return re.sub(r"(?<![년\d])(?<!년 )(\d{1,2})월 회고록", rep, cap)


@dataclass
class CleanReport:
    raw: dict[str, int]
    no_caption: list[Item]
    duplicates: list[tuple[Item, Item]]  # (버린 것, 남긴 것)
    final: dict[str, int]


def count_by_type(items: list[Item]) -> dict[str, int]:
    return {t: sum(i.type == t for i in items) for t in ("post", "reel")}


def clean(items: list[Item]) -> tuple[list[Item], CleanReport]:
    raw = count_by_type(items)
    # 1) 캡션 없는 것 제외
    no_caption = [i for i in items if not i.caption]
    items = [i for i in items if i.caption]
    # 2) 같은 타입 안에서 캡션 전체가 같으면 최신 1개만
    items = sorted(items, key=lambda i: i.posted_at, reverse=True)
    kept_by_key: dict[tuple[str, str], Item] = {}
    duplicates: list[tuple[Item, Item]] = []
    kept: list[Item] = []
    for i in items:
        k = (i.type, i.caption)
        if k in kept_by_key:
            duplicates.append((i, kept_by_key[k]))
            continue
        kept_by_key[k] = i
        kept.append(i)
    # 3) #서울대입구모임 제거, 4) 회고록 연도
    for i in kept:
        stripped = strip_tag(i.caption)
        if stripped != i.caption:
            i.changes.append(f"{SEOUL_TAG} 제거")
        retro = year_retro(stripped, i.posted_at)
        if retro != stripped:
            before = re.findall(r"(?<![년\d])(?<!년 )\d{1,2}월 회고록", stripped)
            after = re.findall(r"\d{4}년 \d{1,2}월 회고록", retro)
            i.changes.append(f"회고록 연도: {', '.join(before)} → {', '.join(after)}")
        i.caption = retro
    return kept, CleanReport(raw, no_caption, duplicates, count_by_type(kept))


def missing_media(root: Path, item: Item) -> list[str]:
    return [m for m in item.media if not (root / m).is_file()]


# ── 미디어 변환 (ffmpeg) ──────────────────────────────────────────────────────


def run(cmd: list[str]) -> str:
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"{cmd[0]} 실패: {result.stderr.strip()[-500:]}")
    return result.stdout


def probe_size(path: Path) -> tuple[int, int]:
    out = run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height",
               "-of", "json", str(path)])
    stream = json.loads(out)["streams"][0]
    return int(stream["width"]), int(stream["height"])


def resize_image(src: Path, dst: Path) -> None:
    """긴 변 1080px 이하 jpg. 작은 사진은 키우지 않는다."""
    vf = "scale=w='min(1080,iw)':h='min(1080,ih)':force_original_aspect_ratio=decrease"
    run(["ffmpeg", "-y", "-v", "error", "-i", str(src), "-vf", vf, "-frames:v", "1", "-q:v", "3", str(dst)])


def transcode_video(src: Path, dst: Path) -> None:
    """짧은 변 720px 이하 H.264 + AAC, faststart(moov 앞으로) — 웹에서 바로 재생 시작."""
    vf = ("scale=w='if(gt(iw,ih),-2,min(720,iw))':h='if(gt(iw,ih),min(720,ih),-2)',"
          "scale=trunc(iw/2)*2:trunc(ih/2)*2")
    run(["ffmpeg", "-y", "-v", "error", "-i", str(src), "-vf", vf,
         "-c:v", "libx264", "-preset", "medium", "-crf", "23", "-pix_fmt", "yuv420p",
         "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", str(dst)])


def extract_poster(video: Path, dst: Path) -> None:
    run(["ffmpeg", "-y", "-v", "error", "-i", str(video), "-frames:v", "1", "-q:v", "3", str(dst)])


# ── API ───────────────────────────────────────────────────────────────────────


class ApiError(RuntimeError):
    def __init__(self, status: int, body: str):
        super().__init__(f"HTTP {status}: {body[:300]}")
        self.status = status


class Api:
    """로그인은 HttpOnly 쿠키로 토큰을 주므로 Set-Cookie 에서 꺼내 Bearer 헤더로 보낸다.
    accessToken 은 1시간짜리라 401 이 나면 refreshToken 으로 한 번 갱신하고 재시도한다."""

    def __init__(self, base_url: str, email: str, password: str):
        self.base_url = base_url.rstrip("/")
        self.email = email
        self.password = password
        self.access_token = ""
        self.refresh_token = ""

    def login(self) -> None:
        _, headers = self._send("POST", "/api/auth/login", json_body={"email": self.email, "password": self.password},
                                auth=False)
        self._store_tokens(headers)
        if not self.access_token:
            raise RuntimeError("로그인 응답에 accessToken 쿠키가 없습니다.")

    def _refresh(self) -> None:
        _, headers = self._send("POST", "/api/auth/refresh", auth=False,
                                extra_headers={"Cookie": f"refreshToken={self.refresh_token}"})
        self._store_tokens(headers)

    def _store_tokens(self, headers) -> None:
        for raw in headers.get_all("Set-Cookie") or []:
            m = re.match(r"\s*(accessToken|refreshToken)=([^;]+)", raw)
            if m and m.group(1) == "accessToken":
                self.access_token = m.group(2)
            elif m:
                self.refresh_token = m.group(2)

    def request(self, method: str, path: str, **kwargs) -> dict:
        try:
            body, _ = self._send(method, path, **kwargs)
        except ApiError as e:
            if e.status != 401 or not self.refresh_token:
                raise
            self._refresh()
            body, _ = self._send(method, path, **kwargs)
        return json.loads(body) if body else {}

    def _send(self, method: str, path: str, json_body=None, multipart=None, auth: bool = True,
              extra_headers: dict | None = None):
        headers = dict(extra_headers or {})
        data = None
        if json_body is not None:
            data = json.dumps(json_body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        elif multipart is not None:
            data, headers["Content-Type"] = encode_multipart(*multipart)
        if auth:
            headers["Authorization"] = f"Bearer {self.access_token}"
        req = urllib.request.Request(self.base_url + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=120) as res:
                return res.read().decode("utf-8"), res.headers
        except urllib.error.HTTPError as e:
            raise ApiError(e.code, e.read().decode("utf-8", "replace")) from None

    # 업무 API

    def list_feed_posts(self) -> list[dict]:
        posts, page = [], 0
        while True:
            data = self.request("GET", f"/api/admin/feed?page={page}&size=100")["data"]
            posts.extend(data["content"])
            page += 1
            if page >= data["totalPages"]:
                return posts

    def upload_image(self, path: Path) -> str:
        """POST /api/images (folder=feed) → tempPath. 게시물 등록 시 서버가 정식 경로로 옮긴다."""
        fields = {"folder": "feed"}
        files = {"file": (path.name, "image/jpeg", path.read_bytes())}
        return self.request("POST", "/api/images", multipart=(fields, files))["data"]["tempPath"]

    def upload_video(self, path: Path) -> str:
        size = path.stat().st_size
        data = self.request("POST", "/api/admin/feed/video-upload-url",
                            json_body={"contentType": "video/mp4", "size": size})["data"]
        # Content-Type·Content-Length 가 서명에 포함돼 있어 그대로 보내야 한다. 인증 헤더는 붙이지 않는다.
        req = urllib.request.Request(data["uploadUrl"], data=path.read_bytes(), method="PUT",
                                     headers={"Content-Type": "video/mp4", "Content-Length": str(size)})
        try:
            with urllib.request.urlopen(req, timeout=600):
                pass
        except urllib.error.HTTPError as e:
            raise ApiError(e.code, "R2 PUT 실패: " + e.read().decode("utf-8", "replace")) from None
        return data["videoUrl"]

    def create_feed_post(self, item: Item, media: list[dict]) -> dict:
        body = {
            "media": media,
            "caption": item.caption,
            "postedAt": item.posted_at.isoformat(),
            "visible": False,
        }
        return self.request("POST", "/api/admin/feed", json_body=body)["data"]


def encode_multipart(fields: dict[str, str], files: dict[str, tuple[str, str, bytes]]) -> tuple[bytes, str]:
    boundary = uuid.uuid4().hex
    parts: list[bytes] = []
    for name, value in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
    for name, (filename, content_type, content) in files.items():
        parts.append((f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'
                      f"Content-Type: {content_type}\r\n\r\n").encode() + content + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


# ── 중복 방지 ─────────────────────────────────────────────────────────────────


def dedup_key(posted_at: datetime | str, caption: str | None) -> tuple[str, str]:
    """게시 시각(분 단위) + 캡션 앞부분. 서버 응답의 postedAt 은 'YYYY-MM-DDTHH:MM:SS' 문자열."""
    at = posted_at if isinstance(posted_at, datetime) else datetime.fromisoformat(posted_at)
    return at.strftime("%Y-%m-%dT%H:%M"), (caption or "").strip()[:DEDUP_PREFIX_LEN]


# ── 실행 ──────────────────────────────────────────────────────────────────────


def validate(item: Item) -> str | None:
    if len(item.caption) > CAPTION_MAX:
        return f"캡션 {len(item.caption)}자 > {CAPTION_MAX}자"
    if not item.media:
        return "미디어 없음"
    if len(item.media) > MEDIA_MAX:
        return f"미디어 {len(item.media)}개 > {MEDIA_MAX}개"
    return None


def print_report(report: CleanReport, items: list[Item]) -> None:
    print(f"원본: 게시물 {report.raw['post']}개, 릴스 {report.raw['reel']}개")
    print(f"정리 후: 게시물 {report.final['post']}개, 릴스 {report.final['reel']}개, 합계 {len(items)}개")
    print(f"\n[제외] 캡션 없음 {len(report.no_caption)}개")
    for i in report.no_caption:
        print(f"  - {i.label()}  미디어 {len(i.media)}개")
    print(f"\n[제외] 캡션 중복(최신 1개만 유지) {len(report.duplicates)}개")
    for dropped, kept in report.duplicates:
        print(f"  - {dropped.label()}  (유지: {kept.posted_at:%Y-%m-%d %H:%M})")
    changed = [i for i in items if i.changes]
    print(f"\n[캡션 변경] {len(changed)}개")
    for i in changed:
        print(f"  - {i.label()}: {'; '.join(i.changes)}")


def process(api: Api, root: Path, item: Item, work: Path) -> dict:
    media: list[dict] = []
    for index, rel in enumerate(item.media):
        src = root / rel
        if src.suffix.lower() == ".mp4":
            video = work / f"{index}.mp4"
            transcode_video(src, video)
            if video.stat().st_size > VIDEO_MAX_BYTES:
                raise RuntimeError(f"재인코딩 후에도 50MB 초과: {rel}")
            poster = work / f"{index}-poster.jpg"
            extract_poster(video, poster)
            width, height = probe_size(video)
            media.append({"type": "VIDEO", "url": api.upload_video(video), "posterUrl": api.upload_image(poster),
                          "width": width, "height": height})
        else:
            image = work / f"{index}.jpg"
            resize_image(src, image)
            if image.stat().st_size > IMAGE_MAX_BYTES:
                raise RuntimeError(f"줄인 사진이 5MB 초과: {rel}")
            width, height = probe_size(image)
            media.append({"type": "IMAGE", "url": api.upload_image(image), "width": width, "height": height})
    return api.create_feed_post(item, media)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="인스타그램 백업 → 피드 게시물 일괄 등록 (KAN-381)")
    parser.add_argument("root", type=Path, help="인스타그램 ZIP 압축 해제 경로")
    parser.add_argument("--dry-run", action="store_true", help="업로드 없이 정리 결과만 출력")
    parser.add_argument("--limit", type=int, default=0, help="처음 N개만 처리 (0=전체)")
    args = parser.parse_args(argv)

    root: Path = args.root.expanduser().resolve()
    items, report = clean(parse_items(root))
    items.sort(key=lambda i: i.posted_at)  # 오래된 것부터 등록
    print_report(report, items)

    missing = [(i, missing_media(root, i)) for i in items]
    missing = [(i, ms) for i, ms in missing if ms]
    invalid = [(i, reason) for i in items if (reason := validate(i))]
    if missing:
        print(f"\n[건너뜀] 미디어 파일 없음 {len(missing)}개")
        for i, ms in missing:
            print(f"  - {i.label()}: {', '.join(ms)}")
    if invalid:
        print(f"\n[건너뜀] 서버 제약 위반 {len(invalid)}개")
        for i, reason in invalid:
            print(f"  - {i.label()}: {reason}")
    skip = {id(i) for i, _ in missing} | {id(i) for i, _ in invalid}
    targets = [i for i in items if id(i) not in skip]

    if args.dry_run:
        videos = sum(m.endswith(".mp4") for i in targets for m in i.media)
        print(f"\n[dry-run] 등록 대상 {len(targets)}개 (사진 {sum(len(i.media) for i in targets) - videos}장, "
              f"영상 {videos}개). 업로드하지 않았습니다.")
        return 0

    env = {k: os.environ.get(k, "") for k in ("API_BASE_URL", "ADMIN_EMAIL", "ADMIN_PASSWORD")}
    if not all(env.values()):
        print("API_BASE_URL, ADMIN_EMAIL, ADMIN_PASSWORD 환경변수가 필요합니다.", file=sys.stderr)
        return 2
    run(["ffmpeg", "-version"])
    api = Api(env["API_BASE_URL"], env["ADMIN_EMAIL"], env["ADMIN_PASSWORD"])
    api.login()
    existing = {dedup_key(p["postedAt"], p.get("caption")) for p in api.list_feed_posts()}
    print(f"\n기존 피드 게시물 {len(existing)}개 확인")

    if args.limit:
        targets = targets[:args.limit]
    created, skipped, failed = [], [], []
    for n, item in enumerate(targets, 1):
        prefix = f"({n}/{len(targets)}) {item.label()}"
        if dedup_key(item.posted_at, item.caption) in existing:
            skipped.append(item)
            print(f"{prefix} — 이미 등록됨, 건너뜀")
            continue
        started = time.monotonic()
        try:
            with tempfile.TemporaryDirectory(prefix="ig-import-") as work:
                post = process(api, root, item, Path(work))
            existing.add(dedup_key(item.posted_at, item.caption))
            created.append(item)
            print(f"{prefix} — 등록 {post['id']} ({time.monotonic() - started:.0f}s)")
        except Exception as e:  # 한 게시물 실패가 전체를 멈추지 않게 한다
            failed.append((item, str(e)))
            print(f"{prefix} — 실패: {e}")

    print(f"\n완료: 등록 {len(created)}개, 이미 등록 {len(skipped)}개, 실패 {len(failed)}개, "
          f"미디어 없음 {len(missing)}개, 제약 위반 {len(invalid)}개")
    if failed:
        print("\n[실패]")
        for item, reason in failed:
            print(f"  - {item.label()}: {reason}")
    if missing:
        print("\n[미디어 파일 없음]")
        for item, ms in missing:
            print(f"  - {item.label()}: {', '.join(ms)}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
