-- V32: 우연한 식탁 재배치 횟수(KAN-436). 매칭 상태가 REALLOCATING으로 바뀔 때마다 +1. 기존 신청은 0부터 센다.
-- 대기 중 일정·지역 바꾸기(PUT /api/dining/me/applications/{id}/wishes)는 기존 테이블만 써서 스키마 변경이 없다.
ALTER TABLE applications ADD COLUMN IF NOT EXISTS reallocation_count INTEGER NOT NULL DEFAULT 0;
