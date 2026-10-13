#!/usr/bin/env python3
"""instagram_import.py 자체 검사 (네트워크·ffmpeg 불필요). usage: python3 scripts/test_instagram_import.py"""

import sys
import tempfile
import unittest
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from instagram_import import (  # noqa: E402
    BOX, Item, clean, dedup_key, encode_multipart, main, missing_media, parse_date, parse_items, strip_tag,
    year_retro,
)

DATE_DIV = '<div class="_3-94 _a6-o">{}</div>'


def post_block(caption: str, *media: str) -> str:
    imgs = "".join(f'<a><img src="{m}" /></a>' for m in media)
    h2 = f"<h2>{caption}</h2>" if caption else ""
    return f'<div class="{BOX}">{h2}{imgs}</div>'


class ParseTest(unittest.TestCase):

    def test_parse_date(self):
        self.assertEqual(parse_date("10월 07, 2026 5:16 오전"), datetime(2026, 10, 7, 5, 16))
        self.assertEqual(parse_date("1월 03, 2026 12:05 오전"), datetime(2026, 1, 3, 0, 5))
        self.assertEqual(parse_date("1월 03, 2026 12:05 오후"), datetime(2026, 1, 3, 12, 5))
        self.assertEqual(parse_date(" 3월 21, 2025 11:59 오후 "), datetime(2025, 3, 21, 23, 59))
        with self.assertRaises(ValueError):
            parse_date("2026-10-07")

    def test_parse_items_merges_posts_html_only_entries(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            media = root / "your_instagram_activity/media"
            media.mkdir(parents=True)
            (media / "posts_1.html").write_text(
                "<style>.x{}</style>"
                + post_block("첫 글 &amp; 사진", "media/posts/1.jpg", "media/posts/2.mp4", "media/posts/1.jpg")
                + DATE_DIV.format("10월 07, 2026 5:16 오전")
                + post_block("", "media/posts/3.jpg")
                + DATE_DIV.format("10월 08, 2026 1:00 오후"),
                encoding="utf-8")
            (media / "posts.html").write_text(
                '<a><img src="media/posts/1.jpg" /></a><div>중복</div>' + DATE_DIV.format("10월 07, 2026 5:16 오전")
                + '<a><img src="media/posts/9.webp" /></a><div>posts.html 에만</div>'
                + DATE_DIV.format("9월 01, 2026 9:00 오전"),
                encoding="utf-8")
            (media / "reels.html").write_text(
                post_block("릴스", "media/reels/r.mp4") + DATE_DIV.format("8월 15, 2026 8:30 오후"),
                encoding="utf-8")

            items = parse_items(root)

        self.assertEqual([(i.type, i.caption, i.media) for i in items], [
            ("post", "첫 글 & 사진", ["media/posts/1.jpg", "media/posts/2.mp4"]),
            ("post", "", ["media/posts/3.jpg"]),
            ("post", "posts.html 에만", ["media/posts/9.webp"]),
            ("reel", "릴스", ["media/reels/r.mp4"]),
        ])
        self.assertEqual(items[3].posted_at, datetime(2026, 8, 15, 20, 30))


class CleanRuleTest(unittest.TestCase):

    def test_year_retro(self):
        p = datetime(2026, 1, 3)
        self.assertEqual(year_retro("“와썹하우스 12월 회고록”", p), "“와썹하우스 2025년 12월 회고록”")
        self.assertEqual(year_retro("9월 회고록", datetime(2026, 10, 4)), "2026년 9월 회고록")
        self.assertEqual(year_retro("1월 회고록", p), "2026년 1월 회고록")
        self.assertEqual(year_retro("2025년 12월 회고록", p), "2025년 12월 회고록")
        self.assertEqual(year_retro("2025년12월 회고록", p), "2025년12월 회고록")
        self.assertEqual(year_retro("12월 모임 공지", p), "12월 모임 공지")

    def test_strip_tag(self):
        self.assertEqual(strip_tag("#a #서울대입구모임 #b"), "#a #b")
        self.assertEqual(strip_tag("본문\n#서울대입구모임\n끝"), "본문\n\n끝")
        self.assertEqual(strip_tag("#서울대입구모임맛집 유지"), "#서울대입구모임맛집 유지")
        self.assertEqual(strip_tag("서울대입구역 도보 5분"), "서울대입구역 도보 5분")

    def test_clean(self):
        items = [
            Item("post", datetime(2026, 1, 1), "", ["a.jpg"]),
            Item("post", datetime(2026, 1, 2), "같은 캡션", ["b.jpg"]),
            Item("post", datetime(2026, 1, 5), "같은 캡션", ["c.jpg"]),
            Item("reel", datetime(2026, 1, 3), "같은 캡션", ["r.mp4"]),
            Item("post", datetime(2026, 1, 4), "[모집] 와인 나잇\n1차", ["d.jpg"]),
            Item("post", datetime(2026, 1, 6), "[모집] 와인 나잇\n2차", ["e.jpg"]),
            Item("post", datetime(2026, 1, 7), "12월 회고록 #서울대입구모임", ["f.jpg"]),
        ]

        kept, report = clean(items)

        self.assertEqual(report.raw, {"post": 6, "reel": 1})
        self.assertEqual(report.final, {"post": 4, "reel": 1})
        self.assertEqual([i.media for i in report.no_caption], [["a.jpg"]])
        self.assertEqual([(d.media, k.media) for d, k in report.duplicates], [(["b.jpg"], ["c.jpg"])])
        retro = next(i for i in kept if i.media == ["f.jpg"])
        self.assertEqual(retro.caption, "2025년 12월 회고록")
        self.assertEqual(retro.changes, ["#서울대입구모임 제거", "회고록 연도: 12월 회고록 → 2025년 12월 회고록"])
        self.assertNotIn("#서울대입구모임", "".join(i.caption for i in kept))


class HelperTest(unittest.TestCase):

    def test_dedup_key_matches_server_format(self):
        local = dedup_key(datetime(2026, 10, 7, 5, 16), "  와썹하우스 " + "가" * 50)
        server = dedup_key("2026-10-07T05:16:00", "와썹하우스 " + "가" * 50 + " 관리자가 뒤를 고침")
        self.assertEqual(local, server)
        self.assertNotEqual(dedup_key("2026-10-07T05:17:00", "a"), dedup_key(datetime(2026, 10, 7, 5, 16), "a"))
        self.assertEqual(dedup_key("2026-10-07T05:16:00", None), ("2026-10-07T05:16", ""))

    def test_missing_media(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "media/posts").mkdir(parents=True)
            (root / "media/posts/1.jpg").write_bytes(b"x")
            item = Item("post", datetime(2026, 1, 1), "c", ["media/posts/1.jpg", "media/posts/2.jpg"])
            self.assertEqual(missing_media(root, item), ["media/posts/2.jpg"])

    def test_encode_multipart(self):
        body, content_type = encode_multipart({"folder": "feed"}, {"file": ("a.jpg", "image/jpeg", b"\xff\xd8")})
        boundary = content_type.split("boundary=")[1]
        self.assertTrue(body.startswith(f"--{boundary}\r\n".encode()))
        self.assertIn(b'name="folder"\r\n\r\nfeed\r\n', body)
        self.assertIn(b'filename="a.jpg"\r\nContent-Type: image/jpeg\r\n\r\n\xff\xd8\r\n', body)
        self.assertTrue(body.endswith(f"--{boundary}--\r\n".encode()))

    def test_dry_run_needs_no_env(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            media = root / "your_instagram_activity/media"
            media.mkdir(parents=True)
            (media / "posts_1.html").write_text(
                post_block("글", "media/posts/1.jpg") + DATE_DIV.format("10월 07, 2026 5:16 오전"), encoding="utf-8")
            (media / "reels.html").write_text("", encoding="utf-8")
            self.assertEqual(main([str(root), "--dry-run"]), 0)


if __name__ == "__main__":
    unittest.main()
