#!/usr/bin/env python3
"""Generate operating seed SQL from WhatsUpHouse Google Form CSV exports.

The output is intended to replace V2 mock operational records with real-ish
operations data while keeping deterministic UUIDs for repeatable verification.
"""

from __future__ import annotations

import argparse
import csv
import json
import random
import re
import unicodedata
import uuid
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[1]
DOWNLOADS_DIR = Path("/Users/tjmedia/Downloads")
OUTPUT_DIR = REPO_ROOT / "build" / "generated" / "operating-data"
TODAY = date(2026, 6, 24)

NAMESPACE = uuid.UUID("7b8278f6-1d48-4eaa-8d2d-6e062145b6e1")
PASSWORD_HASH = "$2a$10$a/2hYslOSx8ABK7iUnPGZ.NaYrLmA2kG3mr44Q.Pa..YXVeMF9NDy"

PAMPAM_LOCATION_ID = "a2000000-0000-0000-0000-000000000001"
SNU_LOCATION_ID = "a2000000-0000-0000-0000-000000000002"
RANDOM_TABLE_LOCATION_ID = str(uuid.uuid5(NAMESPACE, "location:random-table:yeonnam"))
ADMIN_USER_ID = "b1000000-0000-0000-0000-000000000001"
ADMIN_PARTICIPANT_ID = "b1000000-0000-0000-0000-000000000001"
ANONYMOUS_USER_ID = str(uuid.uuid5(NAMESPACE, "user:anonymous-review"))
ANONYMOUS_PARTICIPANT_ID = str(uuid.uuid5(NAMESPACE, "participant:anonymous-review"))
RANDOM_TABLE_ONE_PRODUCT_ID = "d1000000-0000-0000-0000-000000000001"
RANDOM_TABLE_FOUR_PRODUCT_ID = "d1000000-0000-0000-0000-000000000004"
REVIEW_LIKE_COUNTS = [43, 41, 39, 37, 35, 33, 31, 29, 27, 25, 23, 21, 19, 17, 15, 13, 11, 9, 8, 7, 6, 5, 4, 3, 2]
PHOTO_REVIEW_COUNT = 6
REVIEW_IMAGE_URLS = [
    f"https://mcvtfdwsxmtqgxzlfqjx.supabase.co/storage/v1/object/public/whatsup-images/mock/review{index}.JPG"
    for index in range(1, PHOTO_REVIEW_COUNT + 1)
]
TITLE_LEVEL_VIEW_COUNTS = {
    "퇴근 게더링": 100,
    "우연한 식탁": 50,
}

THUMBNAIL_URL = (
    "https://mcvtfdwsxmtqgxzlfqjx.supabase.co/storage/v1/object/public/"
    "whatsup-images/mock/home-2.png"
)


@dataclass(frozen=True)
class GeneratedRow:
    table: str
    values: dict[str, Any]


def normalize_text(value: Any) -> str:
    return unicodedata.normalize("NFC", str(value or "")).strip()


def truncate(value: str | None, max_len: int) -> str | None:
    if value is None:
        return None
    return value[:max_len]


def stable_uuid(key: str) -> str:
    return str(uuid.uuid5(NAMESPACE, key))


def find_csv(kind: str) -> Path:
    needles_by_kind = {
        "regular": ["와썹하우스", "퇴근", "게더링", "응답"],
        "random_table": ["우연한", "식탁", "응답"],
        "reviews": ["와썹하우스", "게더링", "만족도", "응답"],
    }
    needles = needles_by_kind[kind]
    matches: list[Path] = []
    for path in DOWNLOADS_DIR.glob("*.csv"):
        name = normalize_text(path.name)
        if all(needle in name for needle in needles):
            matches.append(path)
    if not matches:
        raise FileNotFoundError(f"Could not find CSV for {kind} in {DOWNLOADS_DIR}")
    matches.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return matches[0]


def read_csv(path: Path) -> list[list[str]]:
    with path.open(newline="", encoding="utf-8-sig") as fp:
        return [[normalize_text(cell) for cell in row] for row in csv.reader(fp)]


def parse_korean_timestamp(value: str) -> datetime | None:
    match = re.search(
        r"(\d{4})\.\s*(\d{1,2})\.\s*(\d{1,2})\s*(오전|오후)\s*(\d{1,2}):(\d{2}):(\d{2})",
        value,
    )
    if not match:
        return None
    year, month, day, ampm, hour, minute, second = match.groups()
    hour_int = int(hour)
    if ampm == "오후" and hour_int != 12:
        hour_int += 12
    if ampm == "오전" and hour_int == 12:
        hour_int = 0
    return datetime(int(year), int(month), int(day), hour_int, int(minute), int(second))


def extract_date_tokens(value: str) -> list[tuple[int, int]]:
    tokens: list[tuple[int, int]] = []
    for month, day in re.findall(r"(?<!\d)(\d{1,2})\s*[./]\s*(\d{1,2})(?!\d)", value):
        try:
            tokens.append((int(month), int(day)))
        except ValueError:
            continue
    return tokens


def infer_event_date(month_day: tuple[int, int], submitted_at: datetime | None) -> date | None:
    month, day = month_day
    base_year = submitted_at.year if submitted_at else TODAY.year
    candidates: list[tuple[int, date]] = []
    for year in (base_year - 1, base_year, base_year + 1):
        try:
            candidate = date(year, month, day)
        except ValueError:
            continue
        delta = (candidate - (submitted_at.date() if submitted_at else TODAY)).days
        score = abs(delta)
        if delta < -21:
            score += 10_000
        if delta > 260:
            score += 3_000
        if candidate > date(TODAY.year, 12, 31):
            score += 20_000
        if candidate > TODAY.replace(year=TODAY.year) and (candidate - TODAY).days > 180:
            score += 20_000
        candidates.append((score, candidate))
    if not candidates:
        return None
    return min(candidates, key=lambda item: item[0])[1]


def adjust_regular_event_dates(raw_choice: str, event_dates: list[date]) -> list[date]:
    generation_numbers = [int(item) for item in re.findall(r"(\d+)\s*기", raw_choice)]
    if not generation_numbers:
        return event_dates

    adjusted: list[date] = []
    all_2026_generation = min(generation_numbers) >= 65
    all_2025_generation = max(generation_numbers) <= 64
    for item in event_dates:
        if all_2026_generation and item.year < 2026:
            adjusted.append(item.replace(year=2026))
        elif all_2025_generation and item.year > 2025:
            adjusted.append(item.replace(year=2025))
        else:
            adjusted.append(item)
    return list(dict.fromkeys(adjusted))


def birth_year_from_two_digits(value: int) -> int:
    return 1900 + value if value >= 40 else 2000 + value


def korean_age_from_birth_year(birth_year: int, submitted_at: datetime | None) -> int | None:
    기준연도 = submitted_at.year if submitted_at else TODAY.year
    age = 기준연도 - birth_year + 1
    return age if 18 <= age <= 69 else None


def parse_age(value: str, submitted_at: datetime | None = None) -> int | None:
    text = normalize_text(value)
    if not text:
        return None

    age_match = re.search(r"(?<!\d)(1[89]|[2-6]\d)\s*(?:살|세)", text)
    if age_match:
        return int(age_match.group(1))
    age_match = re.search(r"만\s*(1[89]|[2-6]\d)", text)
    if age_match:
        return int(age_match.group(1))
    leading_age_match = re.match(r"\s*(1[89]|[2-6]\d)\s*(?:\(|/|,|$)", text)
    if leading_age_match:
        return int(leading_age_match.group(1))
    slash_age_match = re.search(r"/\s*(1[89]|[2-6]\d)(?!\d)", text)
    if slash_age_match:
        return int(slash_age_match.group(1))
    parenthesized_age_match = re.search(r"\(\s*(1[89]|[2-6]\d)\s*\)", text)
    if parenthesized_age_match:
        return int(parenthesized_age_match.group(1))

    plain_match = re.fullmatch(r"(1[89]|[2-6]\d)", text)
    if plain_match:
        return int(plain_match.group(1))

    year_match = re.search(r"(19[6-9]\d|20[0-1]\d|2020)", text)
    if year_match:
        age = korean_age_from_birth_year(int(year_match.group(1)), submitted_at)
        if age is not None:
            return age

    short_year_match = re.search(r"(?<!\d)(\d{2})\s*년\s*생", text)
    if short_year_match:
        age = korean_age_from_birth_year(birth_year_from_two_digits(int(short_year_match.group(1))), submitted_at)
        if age is not None:
            return age
    short_year_match = re.fullmatch(r"(?:빠른\s*)?(\d{2})\s*년?", text)
    if short_year_match:
        age = korean_age_from_birth_year(birth_year_from_two_digits(int(short_year_match.group(1))), submitted_at)
        if age is not None:
            return age
    plain_short_year = re.fullmatch(r"(8[0-9]|9[0-9]|0[0-9])", text)
    if plain_short_year:
        age = korean_age_from_birth_year(birth_year_from_two_digits(int(plain_short_year.group(1))), submitted_at)
        if age is not None:
            return age
    return None


def regular_event_metadata(raw_choice: str, event_date: date) -> dict[str, Any]:
    text = normalize_text(raw_choice).replace(" ", "")
    metadata = {
        "title": "퇴근 게더링",
        "description": "퇴근 후 새로운 사람들과 편하게 대화하고 연결되는 와썹하우스 정기 게더링입니다.",
        "how_to_run": ["체크인", "자기소개와 아이스브레이킹", "소그룹 대화", "마무리 네트워킹"],
        "price": 40000,
    }
    if "러닝크루" in text and event_date.month == 5 and event_date.day == 10:
        return {
            "title": "와썹 러닝 크루",
            "description": "와썹하우스에서 함께 달리고 가볍게 교류하는 러닝 모임입니다.",
            "how_to_run": ["집결과 스트레칭", "함께 러닝", "러닝 후 가벼운 네트워킹"],
            "price": 0,
        }
    if "초등학교" in text and event_date.month == 5 and event_date.day == 5:
        return {
            "title": "어른이날: 와썹 초등학교",
            "description": "어른이날을 맞아 어린 시절의 놀이와 대화를 다시 꺼내보는 와썹하우스 특별 게더링입니다.",
            "how_to_run": ["체크인", "어른이날 아이스브레이킹", "추억 기반 프로그램", "마무리 네트워킹"],
            "price": 40000,
        }
    if "생일파티" in text and event_date.month == 5 and event_date.day == 24:
        return {
            "title": "웃지마 주인장 생일 파티",
            "description": "와썹하우스 주인장의 생일을 함께 축하하며 대화하는 특별 파티입니다.",
            "how_to_run": ["체크인", "생일 파티 프로그램", "자유 대화", "마무리 네트워킹"],
            "price": 40000,
        }
    if "영화게더링" in text and (event_date.month, event_date.day) in {(5, 31), (6, 28)}:
        return {
            "title": "영화 게더링",
            "description": "영화를 매개로 취향과 이야기를 나누는 와썹하우스 특별 게더링입니다.",
            "how_to_run": ["체크인", "영화 취향 아이스브레이킹", "영화 주제 대화", "마무리 네트워킹"],
            "price": 40000,
        }
    if "할로윈게더링" in text and event_date.month == 10 and event_date.day == 31:
        return {
            "title": "할로윈 게더링",
            "description": "할로윈 분위기 속에서 새로운 사람들과 특별한 저녁을 보내는 와썹하우스 게더링입니다.",
            "how_to_run": ["체크인", "할로윈 아이스브레이킹", "소그룹 대화", "마무리 네트워킹"],
            "price": 40000,
        }
    return metadata


def regular_gathering_key(event_date: date, title: str) -> str:
    return f"gathering:regular:{event_date.isoformat()}:{title}"


def normalize_phone(value: str, fallback_index: int) -> str:
    text = normalize_text(value)
    match = re.search(r"01[016789]\D*\d{3,4}\D*\d{4}", text)
    if match:
        digits = re.sub(r"\D", "", match.group(0))
        if len(digits) == 10:
            return digits[:3] + "0" + digits[3:]
        return digits[:11]
    digits = re.sub(r"\D", "", text)
    if len(digits) >= 11 and digits.startswith("01"):
        return digits[:11]
    return f"010{fallback_index % 100_000_000:08d}"


def normalize_gender(value: str) -> str | None:
    text = normalize_text(value).upper()
    if text in {"남", "남자", "MALE", "M"}:
        return "MALE"
    if text in {"여", "여자", "FEMALE", "F"}:
        return "FEMALE"
    return None


def normalize_mbti(value: str) -> str | None:
    text = re.sub(r"[^A-Za-z]", "", normalize_text(value)).upper()
    return text[:4] if re.fullmatch(r"[EI][NS][FT][JP]", text[:4]) else None


def parse_name_from_regular(value: str, row_number: int) -> str:
    text = normalize_text(value)
    if not text:
        return f"참가자{row_number}"
    text = re.split(r"[/,(\n]", text, maxsplit=1)[0].strip()
    text = re.sub(r"\d+.*$", "", text).strip()
    return truncate(text or f"참가자{row_number}", 50) or f"참가자{row_number}"


def timestamp_sql(submitted_at: datetime | None) -> str:
    if not submitted_at:
        return "NOW()"
    return sql_literal(submitted_at.strftime("%Y-%m-%d %H:%M:%S"))


def sql_literal(value: Any) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, date) and not isinstance(value, datetime):
        return f"'{value.isoformat()}'"
    text = "\n".join(line.rstrip() for line in str(value).splitlines()).replace("'", "''")
    return f"'{text}'"


def jsonb_literal(value: dict[str, Any] | list[Any] | None) -> str:
    if value is None:
        return "NULL"
    return sql_literal(json.dumps(value, ensure_ascii=False, separators=(",", ":"))) + "::jsonb"


def values_sql(rows: list[GeneratedRow], columns: list[str]) -> str:
    chunks: list[str] = []
    for row in rows:
        rendered: list[str] = []
        for column in columns:
            value = row.values.get(column)
            if column in {"how_to_run", "form_snapshot", "options", "validation", "value"}:
                rendered.append(jsonb_literal(value))
            else:
                rendered.append(sql_literal(value))
        chunks.append("    (" + ", ".join(rendered) + ")")
    return ",\n".join(chunks)


def append_insert(lines: list[str], table: str, columns: list[str], rows: list[GeneratedRow], chunk_size: int = 400) -> None:
    if not rows:
        return
    for start in range(0, len(rows), chunk_size):
        chunk = rows[start : start + chunk_size]
        lines.append(f"INSERT INTO {table} ({', '.join(columns)})")
        lines.append("VALUES")
        lines.append(values_sql(chunk, columns))
        lines.append("ON CONFLICT (id) DO NOTHING;")
        lines.append("")


def location_for_regular(event_date: date) -> str:
    return SNU_LOCATION_ID if event_date <= date(2026, 4, 30) else PAMPAM_LOCATION_ID


def status_for_date(event_date: date) -> str:
    return "COMPLETED" if event_date < TODAY else "OPEN"


def application_status_for_date(event_date: date) -> str:
    return "ATTENDED" if event_date < TODAY else "CONFIRMED"


def build_regular(rows: list[list[str]]) -> tuple[list[GeneratedRow], list[GeneratedRow], list[GeneratedRow], list[dict[str, Any]], list[dict[str, Any]], Counter[date], Counter[tuple[date, str, int]]]:
    data_rows = rows[1:]
    regular_dates: Counter[date] = Counter()
    regular_events: Counter[tuple[date, str, int]] = Counter()
    parsed_applications: list[dict[str, Any]] = []
    rejected: list[dict[str, Any]] = []

    for index, row in enumerate(data_rows, start=2):
        submitted_at = parse_korean_timestamp(row[0] if len(row) > 0 else "")
        raw_date = row[2] if len(row) > 2 else ""
        event_dates: list[date] = []
        for token in extract_date_tokens(raw_date):
            event_date = infer_event_date(token, submitted_at)
            if event_date:
                event_dates.append(event_date)
        event_dates = adjust_regular_event_dates(raw_date, list(dict.fromkeys(event_dates)))
        for event_date in event_dates:
            metadata = regular_event_metadata(raw_date, event_date)
            regular_dates[event_date] += 1
            regular_events[(event_date, metadata["title"], metadata["price"])] += 1
        if not event_dates:
            if raw_date:
                rejected.append({"source": "regular", "row": index, "reason": "date_parse_failed", "raw_date": raw_date})
            continue

        chosen_date = event_dates[0]
        chosen_metadata = regular_event_metadata(raw_date, chosen_date)
        name = parse_name_from_regular(row[9] if len(row) > 9 else "", index)
        phone = normalize_phone(row[16] if len(row) > 16 else "", index)
        gender = normalize_gender(row[4] if len(row) > 4 else "")
        age = parse_age(row[15] if len(row) > 15 else "", submitted_at)
        mbti = normalize_mbti(row[18] if len(row) > 18 else "")
        job = truncate(row[17] if len(row) > 17 else None, 50)
        intro = row[20] if len(row) > 20 else None
        instagram = truncate(row[19] if len(row) > 19 else None, 100)
        referrer = truncate((row[23] if len(row) > 23 else "") or (row[25] if len(row) > 25 else ""), 50)
        form_snapshot = {
            "source": "google_form_regular",
            "source_row": index,
            "raw_event_choice": raw_date,
            "parsed_event_dates": [item.isoformat() for item in event_dates],
            "event_title": chosen_metadata["title"],
            "question_to_ask": row[24] if len(row) > 24 else None,
            "photo_consent": row[21] if len(row) > 21 else None,
            "privacy_consent": row[22] if len(row) > 22 else None,
        }
        parsed_applications.append(
            {
                "source_row": index,
                "submitted_at": submitted_at,
                "event_date": chosen_date,
                "event_title": chosen_metadata["title"],
                "name": name,
                "phone": phone,
                "gender": gender,
                "age": age,
                "instagram_id": instagram,
                "job": job,
                "mbti": mbti,
                "intro": intro,
                "referrer_name": referrer,
                "form_snapshot": form_snapshot,
            }
        )

    gathering_rows: list[GeneratedRow] = []
    form_rows: list[GeneratedRow] = []
    question_rows: list[GeneratedRow] = []
    curated_regular_date = min(
        (
            event_date
            for event_date, title, _price in regular_events
            if title == "퇴근 게더링" and event_date >= TODAY
        ),
        default=None,
    )
    for (event_date, title, price), source_count in sorted(regular_events.items()):
        metadata = regular_event_metadata(title, event_date)
        # regular_event_metadata expects raw choice text; preserve explicit grouped title metadata here.
        if title != "퇴근 게더링":
            metadata = {
                **metadata,
                "title": title,
                "price": price,
            }
        gathering_id = stable_uuid(regular_gathering_key(event_date, title))
        gathering_rows.append(
            GeneratedRow(
                "gatherings",
                {
                    "id": gathering_id,
                    "title": metadata["title"],
                    "description": metadata["description"],
                    "how_to_run": metadata["how_to_run"],
                    "location_id": location_for_regular(event_date),
                    "event_date": event_date,
                    "start_time": "19:30",
                    "end_time": "21:30",
                    "price": metadata["price"],
                    "max_attendees": max(16, source_count),
                    "gathering_type": "REGULAR",
                    "status": status_for_date(event_date),
                    "thumbnail_url": THUMBNAIL_URL,
                    "is_curated": title == "퇴근 게더링" and event_date == curated_regular_date,
                    "curated_rank": 1 if title == "퇴근 게더링" and event_date == curated_regular_date else 0,
                    "created_at": f"{event_date.isoformat()} 00:00:00",
                    "updated_at": f"{event_date.isoformat()} 00:00:00",
                },
            )
        )

        form_id = stable_uuid(f"form:regular:{event_date.isoformat()}:{title}")
        form_rows.append(
            GeneratedRow(
                "forms",
                {
                    "id": form_id,
                    "gathering_id": gathering_id,
                    "is_template": False,
                    "gathering_type": "REGULAR",
                    "guide_text": None,
                    "created_at": f"{event_date.isoformat()} 00:00:00",
                    "updated_at": f"{event_date.isoformat()} 00:00:00",
                },
            )
        )
        for order, key, label in [(0, "name", "이름"), (1, "phone", "연락처"), (2, "email", "이메일")]:
            question_rows.append(
                GeneratedRow(
                    "form_questions",
                    {
                        "id": stable_uuid(f"question:regular:{event_date.isoformat()}:{title}:{key}"),
                        "form_id": form_id,
                        "question_key": key,
                        "type": "SHORT_TEXT",
                        "label": label,
                        "placeholder": None,
                        "required": key != "email",
                        "display_order": order,
                        "options": None,
                        "validation": None,
                        "is_matching_field": False,
                        "is_system_reserved": True,
                        "matching_strategy": None,
                        "matching_weight": None,
                        "created_at": f"{event_date.isoformat()} 00:00:00",
                        "updated_at": f"{event_date.isoformat()} 00:00:00",
                    },
                )
            )

    application_rows: list[GeneratedRow] = []
    for seq, item in enumerate(parsed_applications, start=1):
        event_date = item["event_date"]
        title = item["event_title"]
        created_at = item["submitted_at"].strftime("%Y-%m-%d %H:%M:%S") if item["submitted_at"] else f"{event_date} 00:00:00"
        application_rows.append(
            GeneratedRow(
                "applications",
                {
                    "id": stable_uuid(f"application:regular:{item['source_row']}"),
                    "created_at": created_at,
                    "updated_at": created_at,
                    "booking_number": f"WH-G-{seq:06d}",
                    "name": item["name"],
                    "phone": item["phone"],
                    "email": None,
                    "form_snapshot": item["form_snapshot"],
                    "gender": item["gender"],
                    "age": item["age"],
                    "instagram_id": item["instagram_id"],
                    "job": item["job"],
                    "mbti": item["mbti"],
                    "intro": item["intro"],
                    "referrer_name": item["referrer_name"],
                    "status": application_status_for_date(event_date),
                    "payment_confirmed_at": created_at,
                    "gathering_id": stable_uuid(regular_gathering_key(event_date, title)),
                    "participant_id": None,
                },
            )
        )

    return gathering_rows, form_rows, question_rows, application_rows, rejected, regular_dates, regular_events


RANDOM_TABLE_QUESTIONS = [
    ("name", "SHORT_TEXT", "이름", True, True, False),
    ("phone", "SHORT_TEXT", "연락처", True, True, False),
    ("instagram_id", "SHORT_TEXT", "Instagram ID", False, False, False),
    ("age", "NUMBER", "나이", True, False, True),
    ("gender", "SINGLE_CHOICE", "성별", True, False, True),
    ("job", "SHORT_TEXT", "직업", True, False, False),
    ("mbti", "SHORT_TEXT", "MBTI", False, False, False),
    ("interests", "MULTI_CHOICE", "나의 요즘 관심사", True, False, True),
    ("personality", "MULTI_CHOICE", "나는 어떤 사람인가요?", True, False, True),
    ("desired_people", "MULTI_CHOICE", "어떤 사람을 만나고 싶으신가요?", True, False, True),
    ("budget", "MULTI_CHOICE", "식사 예산", True, False, False),
    ("available_dates", "MULTI_CHOICE", "참가 가능한 날짜", True, False, False),
    ("ticket_product", "SINGLE_CHOICE", "참가권 선택", True, False, False),
    ("privacy_consent", "SHORT_TEXT", "개인정보 수집 및 이용 동의", True, False, False),
]


def split_multi(value: str) -> list[str]:
    return [item.strip() for item in normalize_text(value).split(",") if item.strip()]


def json_ready(value: Any) -> Any:
    if isinstance(value, date):
        return value.isoformat()
    if isinstance(value, list):
        return [json_ready(item) for item in value]
    if isinstance(value, dict):
        return {key: json_ready(item) for key, item in value.items()}
    return value


def random_ticket_product(value: str) -> str:
    return "RANDOM_TABLE_FOUR" if "4회권" in value else "RANDOM_TABLE_ONE"


def ticket_product_details(product: str) -> tuple[int, int]:
    if product == "RANDOM_TABLE_FOUR":
        return 4, 18_000
    return 1, 8_000


def ticket_product_id(product: str) -> str:
    if product == "RANDOM_TABLE_FOUR":
        return RANDOM_TABLE_FOUR_PRODUCT_ID
    return RANDOM_TABLE_ONE_PRODUCT_ID


def ticket_product_name(product: str) -> str:
    if product == "RANDOM_TABLE_FOUR":
        return "우연한 식탁 4회권"
    return "우연한 식탁 1회권"


def build_random_table(rows: list[list[str]]) -> tuple[
    list[GeneratedRow],
    list[GeneratedRow],
    list[GeneratedRow],
    list[GeneratedRow],
    list[GeneratedRow],
    list[GeneratedRow],
    list[GeneratedRow],
    list[GeneratedRow],
    Counter[date],
]:
    if len(rows) < 3 or (rows[1] and rows[1][0] != "타임스탬프"):
        raise ValueError("Unexpected random table CSV shape: row 2 should be the real form header")
    data_rows = rows[2:]

    parsed: list[dict[str, Any]] = []
    date_counts: Counter[date] = Counter()
    for index, row in enumerate(data_rows, start=3):
        submitted_at = parse_korean_timestamp(row[0] if len(row) > 0 else "")
        available_dates: list[date] = []
        for token in extract_date_tokens(row[12] if len(row) > 12 else ""):
            event_date = infer_event_date(token, submitted_at)
            if event_date:
                available_dates.append(event_date)
                date_counts[event_date] += 1
        available_dates = list(dict.fromkeys(available_dates))
        if not available_dates:
            continue
        chosen_date = available_dates[0]
        created_at = submitted_at.strftime("%Y-%m-%d %H:%M:%S") if submitted_at else f"{chosen_date} 00:00:00"
        parsed.append(
            {
                "source_row": index,
                "created_at": created_at,
                "event_date": chosen_date,
                "available_dates": available_dates,
                "name": truncate(row[1] if len(row) > 1 else f"참가자{index}", 50),
                "phone": normalize_phone(row[2] if len(row) > 2 else "", 50_000 + index),
                "instagram_id": truncate(row[3] if len(row) > 3 else None, 100),
                "age": parse_age(row[4] if len(row) > 4 else "", submitted_at),
                "gender": normalize_gender(row[5] if len(row) > 5 else ""),
                "job": truncate(row[6] if len(row) > 6 else None, 50),
                "mbti": normalize_mbti(row[7] if len(row) > 7 else ""),
                "interests": split_multi(row[8] if len(row) > 8 else ""),
                "personality": split_multi(row[9] if len(row) > 9 else ""),
                "desired_people": split_multi(row[10] if len(row) > 10 else ""),
                "budget": split_multi(row[11] if len(row) > 11 else ""),
                "ticket_product": random_ticket_product(row[13] if len(row) > 13 else ""),
                "ticket_product_raw": row[13] if len(row) > 13 else "",
                "privacy_consent": row[14] if len(row) > 14 else None,
            }
        )

    gathering_rows: list[GeneratedRow] = []
    form_rows: list[GeneratedRow] = []
    question_rows: list[GeneratedRow] = []
    question_id_by_date_key: dict[tuple[date, str], str] = {}
    curated_random_date = min((event_date for event_date in date_counts if event_date >= TODAY), default=None)
    for event_date, source_count in sorted(date_counts.items()):
        gathering_id = stable_uuid(f"gathering:random_table:{event_date.isoformat()}")
        gathering_rows.append(
            GeneratedRow(
                "gatherings",
                {
                    "id": gathering_id,
                    "title": "우연한 식탁",
                    "description": "취향과 가능 날짜를 바탕으로 낯선 사람들과 한 끼를 나누는 와썹하우스 식사 모임입니다.",
                    "how_to_run": ["신청서 기반 매칭", "장소 확정 안내", "테이블별 식사", "후속 네트워킹"],
                    "location_id": RANDOM_TABLE_LOCATION_ID,
                    "event_date": event_date,
                    "start_time": "19:00",
                    "end_time": "21:30",
                    "price": 8000,
                    "max_attendees": max(8, source_count),
                    "gathering_type": "RANDOM_TABLE",
                    "status": status_for_date(event_date),
                    "thumbnail_url": THUMBNAIL_URL,
                    "is_curated": event_date == curated_random_date,
                    "curated_rank": 2 if event_date == curated_random_date else 0,
                    "created_at": f"{event_date.isoformat()} 00:00:00",
                    "updated_at": f"{event_date.isoformat()} 00:00:00",
                },
            )
        )
        form_id = stable_uuid(f"form:random_table:{event_date.isoformat()}")
        form_rows.append(
            GeneratedRow(
                "forms",
                {
                    "id": form_id,
                    "gathering_id": gathering_id,
                    "is_template": False,
                    "gathering_type": "RANDOM_TABLE",
                    "guide_text": "매칭에 사용할 정보를 입력해 주세요.",
                    "created_at": f"{event_date.isoformat()} 00:00:00",
                    "updated_at": f"{event_date.isoformat()} 00:00:00",
                },
            )
        )
        for order, (key, qtype, label, required, reserved, matching) in enumerate(RANDOM_TABLE_QUESTIONS):
            qid = stable_uuid(f"question:random_table:{event_date.isoformat()}:{key}")
            question_id_by_date_key[(event_date, key)] = qid
            options = None
            if key == "ticket_product":
                options = {"choices": ["RANDOM_TABLE_ONE", "RANDOM_TABLE_FOUR"]}
            question_rows.append(
                GeneratedRow(
                    "form_questions",
                    {
                        "id": qid,
                        "form_id": form_id,
                        "question_key": key,
                        "type": qtype,
                        "label": label,
                        "placeholder": None,
                        "required": required,
                        "display_order": order,
                        "options": options,
                        "validation": None,
                        "is_matching_field": matching,
                        "is_system_reserved": reserved,
                        "matching_strategy": "OVERLAP" if matching and qtype == "MULTI_CHOICE" else ("DIVERSE" if key == "gender" else ("SAME" if key == "age" else None)),
                        "matching_weight": 1 if matching else None,
                        "created_at": f"{event_date.isoformat()} 00:00:00",
                        "updated_at": f"{event_date.isoformat()} 00:00:00",
                    },
                )
            )

    application_rows: list[GeneratedRow] = []
    answer_rows: list[GeneratedRow] = []
    participant_rows: list[GeneratedRow] = []
    ticket_pass_rows: list[GeneratedRow] = []
    ticket_transaction_rows: list[GeneratedRow] = []
    answer_keys = [
        "instagram_id",
        "age",
        "gender",
        "job",
        "mbti",
        "interests",
        "personality",
        "desired_people",
        "budget",
        "available_dates",
        "ticket_product",
        "privacy_consent",
    ]
    for seq, item in enumerate(parsed, start=1):
        application_id = stable_uuid(f"application:random_table:{item['source_row']}")
        participant_id = stable_uuid(f"participant:random_table:{item['source_row']}")
        ticket_pass_id = stable_uuid(f"ticket_pass:random_table:{item['source_row']}")
        participant_email = f"random-table-{item['source_row']}@whatsuphouse.local"
        event_date = item["event_date"]
        total_count, purchase_amount = ticket_product_details(item["ticket_product"])
        product_id = ticket_product_id(item["ticket_product"])
        product_name = ticket_product_name(item["ticket_product"])
        remaining_count = total_count - 1
        ticket_status = "USED_UP" if remaining_count == 0 else "ACTIVE"
        snapshot = {
            "source": "google_form_random_table",
            "source_row": item["source_row"],
            "available_dates": [item_date.isoformat() for item_date in item["available_dates"]],
            "ticket_product": item["ticket_product"],
            "ticket_product_raw": item["ticket_product_raw"],
        }
        participant_rows.append(
            GeneratedRow(
                "participants",
                {
                    "id": participant_id,
                    "user_id": None,
                    "participant_type": "GUEST",
                    "name": item["name"],
                    "email": participant_email,
                    "phone": item["phone"],
                    "email_verified_at": None,
                    "account_status": "ACTIVE",
                    "random_table_eligibility": "APPROVED",
                    "created_at": item["created_at"],
                    "updated_at": item["created_at"],
                },
            )
        )
        application_rows.append(
            GeneratedRow(
                "applications",
                {
                    "id": application_id,
                    "created_at": item["created_at"],
                    "updated_at": item["created_at"],
                    "booking_number": f"WH-RT-{seq:06d}",
                    "name": item["name"],
                    "phone": item["phone"],
                    "email": None,
                    "form_snapshot": snapshot,
                    "gender": item["gender"],
                    "age": item["age"],
                    "instagram_id": item["instagram_id"],
                    "job": item["job"],
                    "mbti": item["mbti"],
                    "intro": None,
                    "referrer_name": None,
                    "status": application_status_for_date(event_date),
                    "payment_confirmed_at": item["created_at"],
                    "gathering_id": stable_uuid(f"gathering:random_table:{event_date.isoformat()}"),
                    "participant_id": participant_id,
                },
            )
        )
        ticket_pass_rows.append(
            GeneratedRow(
                "ticket_passes",
                {
                    "id": ticket_pass_id,
                    "participant_id": participant_id,
                    "application_id": application_id,
                    "product": item["ticket_product"],
                    "product_id": product_id,
                    "product_name": product_name,
                    "total_count": total_count,
                    "remaining_count": remaining_count,
                    "purchase_amount": purchase_amount,
                    "status": ticket_status,
                    "payment_deadline": None,
                    "payment_confirmed_at": item["created_at"],
                    "activated_at": item["created_at"],
                    "created_at": item["created_at"],
                    "updated_at": item["created_at"],
                },
            )
        )
        ticket_transaction_rows.append(
            GeneratedRow(
                "ticket_transactions",
                {
                    "id": stable_uuid(f"ticket_transaction:random_table:{item['source_row']}:issue"),
                    "ticket_pass_id": ticket_pass_id,
                    "application_id": None,
                    "transaction_type": "ISSUE",
                    "quantity": total_count,
                    "balance_after": total_count,
                    "reason": "구글폼 운영 데이터 이용권 발급",
                    "created_at": item["created_at"],
                },
            )
        )
        ticket_transaction_rows.append(
            GeneratedRow(
                "ticket_transactions",
                {
                    "id": stable_uuid(f"ticket_transaction:random_table:{item['source_row']}:use"),
                    "ticket_pass_id": ticket_pass_id,
                    "application_id": application_id,
                    "transaction_type": "USE",
                    "quantity": -1,
                    "balance_after": remaining_count,
                    "reason": "우연한 식탁 참가 확정",
                    "created_at": item["created_at"],
                },
            )
        )
        for key in answer_keys:
            value = item[key]
            if isinstance(value, list):
                answer_value = {"value": json_ready(value)}
            elif isinstance(value, date):
                answer_value = {"value": value.isoformat()}
            else:
                answer_value = {"value": value}
            answer_rows.append(
                GeneratedRow(
                    "application_answers",
                    {
                        "id": stable_uuid(f"answer:random_table:{item['source_row']}:{key}"),
                        "application_id": application_id,
                        "question_id": question_id_by_date_key[(event_date, key)],
                        "value": answer_value,
                        "created_at": item["created_at"],
                        "updated_at": item["created_at"],
                    },
                )
            )

    return (
        gathering_rows,
        form_rows,
        question_rows,
        application_rows,
        answer_rows,
        participant_rows,
        ticket_pass_rows,
        ticket_transaction_rows,
        date_counts,
    )


def build_reviews(rows: list[list[str]], regular_dates: Counter[date]) -> tuple[list[GeneratedRow], list[GeneratedRow], Counter[date]]:
    data_rows = rows[1:]
    representative_regular_date = min(regular_dates)
    application_rows: list[GeneratedRow] = []
    review_rows: list[GeneratedRow] = []
    linked_dates: Counter[date] = Counter()

    for seq, row in enumerate(data_rows, start=1):
        submitted_at = parse_korean_timestamp(row[0] if len(row) > 0 else "")
        event_date = representative_regular_date
        linked_dates[event_date] += 1
        created_at = submitted_at.strftime("%Y-%m-%d %H:%M:%S") if submitted_at else f"{event_date} 00:00:00"
        content = row[11] if len(row) > 11 and row[11] else row[12] if len(row) > 12 else ""
        if not content:
            content = "좋은 시간이었습니다."
        application_id = stable_uuid(f"application:review:{seq}")
        gathering_id = stable_uuid(regular_gathering_key(event_date, "퇴근 게더링"))
        application_rows.append(
            GeneratedRow(
                "applications",
                {
                    "id": application_id,
                    "created_at": created_at,
                    "updated_at": created_at,
                    "booking_number": f"WH-RV-{seq:06d}",
                    "name": "익명",
                    "phone": f"0109{seq % 10_000_000:07d}",
                    "email": None,
                    "form_snapshot": {
                        "source": "google_form_review",
                        "source_row": seq + 1,
                        "overall_rating": row[4] if len(row) > 4 else None,
                        "recommend_score": row[14] if len(row) > 14 else None,
                        "rejoin_intent": row[15] if len(row) > 15 else None,
                        "linked_by": "gathering_type:REGULAR:퇴근 게더링",
                    },
                    "gender": "FEMALE",
                    "age": None,
                    "instagram_id": None,
                    "job": None,
                    "mbti": None,
                    "intro": None,
                    "referrer_name": None,
                    "status": "ATTENDED",
                    "payment_confirmed_at": created_at,
                    "gathering_id": gathering_id,
                    "participant_id": ANONYMOUS_PARTICIPANT_ID,
                },
            )
        )
        review_rows.append(
            GeneratedRow(
                "reviews",
                {
                    "id": stable_uuid(f"review:{seq}"),
                    "created_at": created_at,
                    "updated_at": created_at,
                    "review_content": content,
                    "review_type": "TEXT",
                    "like_count": 0,
                    "notified_like_milestone": 0,
                    "is_home_featured": seq <= 12,
                    "home_display_order": seq if seq <= 12 else 0,
                    "application_id": application_id,
                    "gathering_id": gathering_id,
                    "user_id": ANONYMOUS_USER_ID,
                },
            )
        )
    return application_rows, review_rows, linked_dates


def ghost_name(index: int) -> str:
    digit = str((index - 1) % 9 + 1)
    return digit * 3 if index <= 9 else f"{digit * 3}{index:02d}"


def build_review_likes_and_images(review_rows: list[GeneratedRow]) -> tuple[list[GeneratedRow], list[GeneratedRow], list[GeneratedRow], dict[str, int]]:
    ghost_users: list[GeneratedRow] = []
    review_like_rows: list[GeneratedRow] = []
    review_image_rows: list[GeneratedRow] = []
    max_likes = max(REVIEW_LIKE_COUNTS)

    for index in range(1, max_likes + 1):
        name = ghost_name(index)
        ghost_users.append(
            GeneratedRow(
                "users",
                {
                    "id": stable_uuid(f"user:review-like-ghost:{index}"),
                    "email": f"ghost-like-{index:03d}@whatsuphouse.local",
                    "password": PASSWORD_HASH,
                    "name": name,
                    "gender": "MALE" if index % 2 else "FEMALE",
                    "age": 20 + (index % 20),
                    "birth_date": f"{2006 - (index % 20):04d}-01-01",
                    "nickname": name,
                    "phone": f"0108{index:07d}",
                    "instagram_id": None,
                    "mbti": None,
                    "job": "UNKNOWN",
                    "intro": "후기 좋아요 검증용 유령 회원",
                    "is_admin": False,
                    "mileage_balance": 0,
                    "account_status": "ACTIVE",
                    "created_at": "2025-01-01 00:00:00",
                    "updated_at": "2025-01-01 00:00:00",
                },
            )
        )

    selected_reviews = sorted(
        review_rows,
        key=lambda row: (-len(normalize_text(row.values["review_content"])), row.values["id"]),
    )[: len(REVIEW_LIKE_COUNTS)]
    photo_reviews = set(random.Random(20260624).sample([row.values["id"] for row in selected_reviews], PHOTO_REVIEW_COUNT))
    like_count_by_review_id: dict[str, int] = {}
    for review_rank, (review, like_count) in enumerate(zip(selected_reviews, REVIEW_LIKE_COUNTS, strict=True), start=1):
        review_id = review.values["id"]
        like_count_by_review_id[review_id] = like_count
        if review_id in photo_reviews:
            review.values["review_type"] = "PHOTO"
            image_url = REVIEW_IMAGE_URLS[len(review_image_rows) % len(REVIEW_IMAGE_URLS)]
            review_image_rows.append(
                GeneratedRow(
                    "review_images",
                    {
                        "id": stable_uuid(f"review_image:{review_id}:0"),
                        "review_id": review_id,
                        "image_url": image_url,
                        "display_order": 0,
                        "created_at": review.values["created_at"],
                        "updated_at": review.values["updated_at"],
                    },
                )
            )
        for ghost_index in range(1, like_count + 1):
            review_like_rows.append(
                GeneratedRow(
                    "review_likes",
                    {
                        "id": stable_uuid(f"review_like:{review_id}:{ghost_index}"),
                        "review_id": review_id,
                        "user_id": stable_uuid(f"user:review-like-ghost:{ghost_index}"),
                        "created_at": f"2026-06-24 10:{review_rank:02d}:{ghost_index % 60:02d}",
                    },
                )
            )
    return ghost_users, review_like_rows, review_image_rows, like_count_by_review_id


def build_sql(all_rows: dict[str, list[GeneratedRow]]) -> str:
    lines: list[str] = [
        "-- Generated by scripts/generate_operating_seed_sql.py",
        "-- Replaces mock operational records with Google Form based operating data.",
        "",
        "DELETE FROM matching_members;",
        "DELETE FROM matching_groups;",
        "DELETE FROM application_answers;",
        "DELETE FROM review_likes;",
        "DELETE FROM review_images;",
        "DELETE FROM reviews;",
        "DELETE FROM ticket_transactions;",
        "DELETE FROM ticket_passes;",
        "DELETE FROM applications;",
        "DELETE FROM form_questions;",
        "DELETE FROM forms;",
        "UPDATE carousel_slides SET gathering_id = NULL WHERE gathering_id IS NOT NULL;",
        "DELETE FROM gatherings;",
        "DELETE FROM content_translations WHERE entity_type IN ('GATHERING', 'REVIEW', 'FORM');",
        "",
    ]

    location_rows = [
        GeneratedRow(
            "locations",
            {
                "id": PAMPAM_LOCATION_ID,
                "name": "팜팜발리",
                "address": "서울 마포구 와우산로 21길 20",
                "naver_map_url": "https://naver.me/xHgIyXJR",
                "kakao_map_url": "https://map.kakao.com/?q=팜팜발리",
                "status": "ACTIVE",
                "max_capacity": 30,
                "memo": "2026년 5월 이후 퇴근 게더링 장소",
                "created_at": "2025-01-01 00:00:00",
                "updated_at": "2025-01-01 00:00:00",
            },
        ),
        GeneratedRow(
            "locations",
            {
                "id": SNU_LOCATION_ID,
                "name": "서울대입구역",
                "address": "서울 관악구 봉천로 559",
                "naver_map_url": "https://naver.me/Fx9cfs2u",
                "kakao_map_url": "https://map.kakao.com/?q=서울대입구역",
                "status": "ACTIVE",
                "max_capacity": 40,
                "memo": "2026년 4월까지 퇴근 게더링 장소",
                "created_at": "2025-01-01 00:00:00",
                "updated_at": "2025-01-01 00:00:00",
            },
        ),
        GeneratedRow(
            "locations",
            {
                "id": RANDOM_TABLE_LOCATION_ID,
                "name": "연남동",
                "address": "서울 마포구 연남동",
                "naver_map_url": None,
                "kakao_map_url": "https://map.kakao.com/?q=연남동",
                "status": "ACTIVE",
                "max_capacity": 20,
                "memo": "우연한 식탁 기본 지역",
                "created_at": "2025-01-01 00:00:00",
                "updated_at": "2025-01-01 00:00:00",
            },
        ),
    ]
    user_rows = [
        GeneratedRow(
            "users",
            {
                "id": ADMIN_USER_ID,
                "email": "admin@whatsuphouse.com",
                "password": PASSWORD_HASH,
                "name": "김큐레이터",
                "gender": "MALE",
                "age": 30,
                "birth_date": "1996-01-01",
                "nickname": "큐레이터",
                "phone": "01012340000",
                "instagram_id": "curator_wh",
                "mbti": "ENFJ",
                "job": "STARTUP_FOUNDER",
                "intro": "와썹하우스를 운영합니다",
                "is_admin": True,
                "mileage_balance": 0,
                "account_status": "ACTIVE",
                "created_at": "2025-01-01 00:00:00",
                "updated_at": "2025-01-01 00:00:00",
            },
        ),
        GeneratedRow(
            "users",
            {
                "id": ANONYMOUS_USER_ID,
                "email": "anonymous-review@whatsuphouse.local",
                "password": PASSWORD_HASH,
                "name": "익명",
                "gender": "FEMALE",
                "age": 30,
                "birth_date": "1996-01-01",
                "nickname": "익명",
                "phone": "01000009999",
                "instagram_id": None,
                "mbti": None,
                "job": "UNKNOWN",
                "intro": "구글폼 후기 익명 계정",
                "is_admin": False,
                "mileage_balance": 0,
                "account_status": "ACTIVE",
                "created_at": "2025-01-01 00:00:00",
                "updated_at": "2025-01-01 00:00:00",
            },
        )
    ]
    participant_rows = [
        GeneratedRow(
            "participants",
            {
                "id": ADMIN_PARTICIPANT_ID,
                "user_id": ADMIN_USER_ID,
                "participant_type": "MEMBER",
                "name": "김큐레이터",
                "email": "admin@whatsuphouse.com",
                "phone": "01012340000",
                "email_verified_at": "2025-01-01 00:00:00",
                "account_status": "ACTIVE",
                "random_table_eligibility": "UNREVIEWED",
                "created_at": "2025-01-01 00:00:00",
                "updated_at": "2025-01-01 00:00:00",
            },
        ),
        GeneratedRow(
            "participants",
            {
                "id": ANONYMOUS_PARTICIPANT_ID,
                "user_id": ANONYMOUS_USER_ID,
                "participant_type": "MEMBER",
                "name": "익명",
                "email": "anonymous-review@whatsuphouse.local",
                "phone": "01000009999",
                "email_verified_at": "2025-01-01 00:00:00",
                "account_status": "ACTIVE",
                "random_table_eligibility": "APPROVED",
                "created_at": "2025-01-01 00:00:00",
                "updated_at": "2025-01-01 00:00:00",
            },
        )
    ]
    ticket_product_rows = [
        GeneratedRow(
            "ticket_products",
            {
                "id": RANDOM_TABLE_ONE_PRODUCT_ID,
                "name": "우연한 식탁 1회권",
                "session_count": 1,
                "price": 8000,
                "created_at": "2025-01-01 00:00:00",
                "updated_at": "2025-01-01 00:00:00",
            },
        ),
        GeneratedRow(
            "ticket_products",
            {
                "id": RANDOM_TABLE_FOUR_PRODUCT_ID,
                "name": "우연한 식탁 4회권",
                "session_count": 4,
                "price": 18000,
                "created_at": "2025-01-01 00:00:00",
                "updated_at": "2025-01-01 00:00:00",
            },
        ),
    ]

    append_insert(
        lines,
        "locations",
        ["id", "name", "address", "naver_map_url", "kakao_map_url", "status", "max_capacity", "memo", "created_at", "updated_at"],
        location_rows,
    )
    append_insert(
        lines,
        "users",
        [
            "id",
            "email",
            "password",
            "name",
            "gender",
            "age",
            "birth_date",
            "nickname",
            "phone",
            "instagram_id",
            "mbti",
            "job",
            "intro",
            "is_admin",
            "mileage_balance",
            "account_status",
            "created_at",
            "updated_at",
        ],
        user_rows + all_rows.get("users", []),
    )
    lines.append(
        "UPDATE users SET password = "
        + sql_literal(PASSWORD_HASH)
        + ", is_admin = TRUE, account_status = 'ACTIVE', updated_at = NOW() "
        + "WHERE email = 'admin@whatsuphouse.com';"
    )
    lines.append("")
    append_insert(
        lines,
        "participants",
        [
            "id",
            "user_id",
            "participant_type",
            "name",
            "email",
            "phone",
            "email_verified_at",
            "account_status",
            "random_table_eligibility",
            "created_at",
            "updated_at",
        ],
        participant_rows + all_rows.get("participants", []),
    )
    if ticket_product_rows:
        lines.append("INSERT INTO ticket_products (id, name, session_count, price, created_at, updated_at)")
        lines.append("VALUES")
        lines.append(values_sql(ticket_product_rows, ["id", "name", "session_count", "price", "created_at", "updated_at"]))
        lines.append(
            "ON CONFLICT (id) DO UPDATE SET "
            "name = EXCLUDED.name, "
            "session_count = EXCLUDED.session_count, "
            "price = EXCLUDED.price, "
            "updated_at = EXCLUDED.updated_at;"
        )
        lines.append("")
    append_insert(
        lines,
        "gatherings",
        [
            "id",
            "title",
            "description",
            "how_to_run",
            "location_id",
            "event_date",
            "start_time",
            "end_time",
            "price",
            "max_attendees",
            "gathering_type",
            "status",
            "thumbnail_url",
            "is_curated",
            "curated_rank",
            "created_at",
            "updated_at",
        ],
        all_rows["gatherings"],
    )
    append_insert(
        lines,
        "forms",
        ["id", "gathering_id", "is_template", "gathering_type", "guide_text", "created_at", "updated_at"],
        all_rows["forms"],
    )
    append_insert(
        lines,
        "form_questions",
        [
            "id",
            "form_id",
            "question_key",
            "type",
            "label",
            "placeholder",
            "required",
            "display_order",
            "options",
            "validation",
            "is_matching_field",
            "is_system_reserved",
            "matching_strategy",
            "matching_weight",
            "created_at",
            "updated_at",
        ],
        all_rows["form_questions"],
    )
    append_insert(
        lines,
        "applications",
        [
            "id",
            "created_at",
            "updated_at",
            "booking_number",
            "name",
            "phone",
            "email",
            "form_snapshot",
            "gender",
            "age",
            "instagram_id",
            "job",
            "mbti",
            "intro",
            "referrer_name",
            "status",
            "payment_confirmed_at",
            "gathering_id",
            "participant_id",
        ],
        all_rows["applications"],
    )
    append_insert(
        lines,
        "ticket_passes",
        [
            "id",
            "participant_id",
            "application_id",
            "product",
            "product_id",
            "product_name",
            "total_count",
            "remaining_count",
            "purchase_amount",
            "status",
            "payment_deadline",
            "payment_confirmed_at",
            "activated_at",
            "created_at",
            "updated_at",
        ],
        all_rows.get("ticket_passes", []),
    )
    append_insert(
        lines,
        "ticket_transactions",
        [
            "id",
            "ticket_pass_id",
            "application_id",
            "transaction_type",
            "quantity",
            "balance_after",
            "reason",
            "created_at",
        ],
        all_rows.get("ticket_transactions", []),
    )
    append_insert(
        lines,
        "application_answers",
        ["id", "application_id", "question_id", "value", "created_at", "updated_at"],
        all_rows["application_answers"],
    )
    append_insert(
        lines,
        "reviews",
        [
            "id",
            "created_at",
            "updated_at",
            "review_content",
            "review_type",
            "like_count",
            "notified_like_milestone",
            "is_home_featured",
            "home_display_order",
            "application_id",
            "gathering_id",
            "user_id",
        ],
        all_rows["reviews"],
    )
    append_insert(
        lines,
        "review_images",
        ["id", "review_id", "image_url", "display_order", "created_at", "updated_at"],
        all_rows.get("review_images", []),
    )
    append_insert(
        lines,
        "review_likes",
        ["id", "review_id", "user_id", "created_at"],
        all_rows.get("review_likes", []),
    )
    lines.append("")
    return "\n".join(lines)


def write_rejected(path: Path, rejected: list[dict[str, Any]]) -> None:
    with path.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.DictWriter(fp, fieldnames=["source", "row", "reason", "raw_date"])
        writer.writeheader()
        writer.writerows(rejected)


def write_qa_markdown(path: Path, report: dict[str, Any]) -> None:
    lines = [
        "# Operating Seed QA",
        "",
        "## Counts",
        "",
        f"- Regular gathering dates: {report['regular']['unique_dates']}",
        f"- Regular applications: {report['regular']['applications']}",
        f"- Random table dates: {report['random_table']['unique_dates']}",
        f"- Random table applications: {report['random_table']['applications']}",
        f"- Reviews: {report['reviews']['reviews']}",
        f"- Rejected regular rows: {report['regular']['rejected_rows']}",
        "",
        "## Date Ranges",
        "",
        f"- Regular: {report['regular']['min_date']} to {report['regular']['max_date']}",
        f"- Random table: {report['random_table']['min_date']} to {report['random_table']['max_date']}",
        "",
        "## Policy Checks Encoded In SQL",
        "",
        "- 퇴근 게더링 price: 40000",
        "- 와썹 러닝 크루 price: 0",
        "- Other special gatherings price: 40000",
        "- Random table gathering display price: 8000",
        "- Random table ticket pass prices: single 8000, four-pack 18000",
        "- Regular gatherings through 2026-04-30: 서울대입구역",
        "- Regular gatherings from 2026-05-01: 팜팜발리",
        "- Random table location: 연남동",
        f"- Review likes seeded on top long reviews: {len(report['reviews']['like_counts'])}",
        f"- Photo reviews among liked long reviews: {report['reviews']['photo_reviews']}",
        f"- Review ghost like users: {report['reviews']['ghost_like_users']}",
        f"- Review seeded like counts: {report['reviews']['like_counts']}",
        f"- Home most-viewed title-level signals: {report['home']['title_level_view_counts']}",
        "- Home curated representatives: rank 1 퇴근 게더링, rank 2 우연한 식탁",
        f"- Regular age non-empty parse rate: {report['regular']['age_quality']['parsed_nonempty']}/{report['regular']['age_quality']['nonempty']}",
        "",
    ]
    path.write_text("\n".join(lines), encoding="utf-8")


def regular_age_quality(rows: list[list[str]]) -> dict[str, int]:
    nonempty = 0
    parsed = 0
    empty = 0
    for row in rows[1:]:
        raw_age = row[15] if len(row) > 15 else ""
        if not raw_age:
            empty += 1
            continue
        nonempty += 1
        submitted_at = parse_korean_timestamp(row[0] if len(row) > 0 else "")
        if parse_age(raw_age, submitted_at) is not None:
            parsed += 1
    return {
        "empty": empty,
        "nonempty": nonempty,
        "parsed_nonempty": parsed,
        "unparsed_nonempty": nonempty - parsed,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, default=OUTPUT_DIR)
    args = parser.parse_args()

    output_dir = args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)

    csv_paths = {
        "regular": find_csv("regular"),
        "random_table": find_csv("random_table"),
        "reviews": find_csv("reviews"),
    }
    regular_rows = read_csv(csv_paths["regular"])
    random_rows = read_csv(csv_paths["random_table"])
    review_rows = read_csv(csv_paths["reviews"])

    regular_gatherings, regular_forms, regular_questions, regular_applications, rejected, regular_dates, regular_events = build_regular(regular_rows)
    (
        random_gatherings,
        random_forms,
        random_questions,
        random_applications,
        random_answers,
        random_participants,
        random_ticket_passes,
        random_ticket_transactions,
        random_dates,
    ) = build_random_table(random_rows)
    review_applications, reviews, review_links = build_reviews(review_rows, regular_dates)
    ghost_users, review_likes, review_images, like_count_by_review_id = build_review_likes_and_images(reviews)
    for review in reviews:
        review.values["like_count"] = like_count_by_review_id.get(review.values["id"], 0)

    all_rows = {
        "gatherings": regular_gatherings + random_gatherings,
        "users": ghost_users,
        "participants": random_participants,
        "forms": regular_forms + random_forms,
        "form_questions": regular_questions + random_questions,
        "applications": regular_applications + random_applications + review_applications,
        "ticket_passes": random_ticket_passes,
        "ticket_transactions": random_ticket_transactions,
        "application_answers": random_answers,
        "reviews": reviews,
        "review_images": review_images,
        "review_likes": review_likes,
    }
    sql = build_sql(all_rows)
    sql_path = output_dir / "V2__operating_data_overlay.sql"
    report_path = output_dir / "operating_seed_report.json"
    rejected_path = output_dir / "rejected_rows.csv"
    qa_path = output_dir / "operating_seed_qa.md"

    sql_path.write_text(sql, encoding="utf-8")
    write_rejected(rejected_path, rejected)

    report = {
        "csv_paths": {key: str(value) for key, value in csv_paths.items()},
        "sql_path": str(sql_path),
        "regular": {
            "source_rows_including_header": len(regular_rows),
            "unique_dates": len(regular_dates),
            "unique_events": len(regular_events),
            "min_date": min(regular_dates).isoformat(),
            "max_date": max(regular_dates).isoformat(),
            "applications": len(regular_applications),
            "rejected_rows": len(rejected),
            "applications_by_event_date": dict(
                sorted(
                    Counter(
                        row.values["form_snapshot"]["parsed_event_dates"][0]
                        for row in regular_applications
                    ).items()
                )
            ),
            "date_choice_counts": {item.isoformat(): count for item, count in sorted(regular_dates.items())},
            "event_counts": {
                f"{event_date.isoformat()}|{title}|{price}": count
                for (event_date, title, price), count in sorted(regular_events.items())
            },
            "age_quality": regular_age_quality(regular_rows),
        },
        "random_table": {
            "source_rows_including_headers": len(random_rows),
            "unique_dates": len(random_dates),
            "min_date": min(random_dates).isoformat(),
            "max_date": max(random_dates).isoformat(),
            "applications": len(random_applications),
            "date_choice_counts": {item.isoformat(): count for item, count in sorted(random_dates.items())},
            "ticket_products": Counter(
                row.values["form_snapshot"]["ticket_product"] for row in random_applications
            ),
        },
        "reviews": {
            "source_rows_including_header": len(review_rows),
            "reviews": len(reviews),
            "linked_dates": {item.isoformat(): count for item, count in sorted(review_links.items())},
            "home_featured": sum(1 for row in reviews if row.values["is_home_featured"]),
            "ghost_like_users": len(ghost_users),
            "seeded_likes": len(review_likes),
            "photo_reviews": len(review_images),
            "photo_image_urls": [row.values["image_url"] for row in review_images],
            "like_counts": sorted(like_count_by_review_id.values(), reverse=True),
        },
        "home": {
            "title_level_view_counts": TITLE_LEVEL_VIEW_COUNTS,
        },
        "sql_counts": {table: len(rows) for table, rows in all_rows.items()},
    }
    report["random_table"]["ticket_products"] = dict(report["random_table"]["ticket_products"])
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    write_qa_markdown(qa_path, report)

    print(json.dumps({
        "sql_path": str(sql_path),
        "report_path": str(report_path),
        "rejected_path": str(rejected_path),
        "qa_path": str(qa_path),
        "counts": report["sql_counts"],
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
