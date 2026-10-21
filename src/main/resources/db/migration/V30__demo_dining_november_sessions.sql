-- V30: 심사용 우연한 식탁(숨김 RANDOM_TABLE)에 11월 목요일 회차 4개 추가 + 우연한 식탁 설명의 '📍 홍대·합정' 줄 삭제.
-- 1. 회차는 그 모임의 가장 최근 회차를 본뜬다(시간·정원·가격·테이블 인원·매칭 실행 시각 간격).
--    장소는 비운다(모든 회차가 서울 4개 지역에서 열린다, KAN-430). 신청 마감은 그 주 화요일 23:59.
--    같은 날짜 회차가 이미 있으면 건너뛴다(멱등). 숨김 식탁 모임이 없거나 회차가 없으면 아무것도 넣지 않는다.
-- 2. 모든 회차가 4개 지역에서 열리므로 설명의 고정 장소 줄은 지운다.

WITH tpl AS (
    SELECT s.*
      FROM gathering_sessions s
      JOIN gatherings g ON g.id = s.gathering_id
     WHERE g.gathering_type = 'RANDOM_TABLE' AND g.hidden = true AND g.deleted_at IS NULL
       AND s.deleted_at IS NULL
     ORDER BY s.event_date DESC
     LIMIT 1
)
INSERT INTO gathering_sessions (id, gathering_id, event_date, start_time, end_time, location_id, max_attendees,
                                price_override, apply_deadline_at, status, match_run_at, auto_confirm_grace_minutes,
                                table_size_min, table_size_max, min_group_score, max_age_gap, created_at, updated_at)
SELECT gen_random_uuid(), tpl.gathering_id, d.day, tpl.start_time, tpl.end_time, NULL, tpl.max_attendees,
       tpl.price_override, (d.day - 2) + TIME '23:59', 'OPEN',
       CASE WHEN tpl.match_run_at IS NULL THEN NULL
            ELSE d.day + (tpl.match_run_at - tpl.event_date::timestamp) END,
       tpl.auto_confirm_grace_minutes, tpl.table_size_min, tpl.table_size_max, tpl.min_group_score,
       tpl.max_age_gap, now(), now()
  FROM tpl
 CROSS JOIN (VALUES (DATE '2026-11-05'), (DATE '2026-11-12'), (DATE '2026-11-19'), (DATE '2026-11-26')) AS d(day)
 WHERE NOT EXISTS (SELECT 1 FROM gathering_sessions x
                    WHERE x.gathering_id = tpl.gathering_id AND x.event_date = d.day AND x.deleted_at IS NULL);

UPDATE gatherings
   SET description = regexp_replace(description, E'[ \t]*📍 홍대·합정[^\n]*\n?', '', 'g'),
       updated_at = now()
 WHERE gathering_type = 'RANDOM_TABLE' AND deleted_at IS NULL AND description LIKE '%📍 홍대·합정%';
