-- V29: 회차×지역 매칭 · 식당 20곳 시드. (KAN-431)
--
-- 1. matching_rule_settings.min_tables_per_region: 한 회차·지역의 자동 매칭 테이블이 이 수 이상일 때만 그 지역 테이블을 만든다.
-- 2. match_runs.region_results: 실행의 지역별 결과 [{region, candidateCount, tableCount, adopted}]. 이전 실행은 빈 목록.
-- 3. 소개팅 장소로 알려진 4~6인 착석 가능 식당 20곳(지역별 5곳). 지역은 DiningRegion 라벨, 가격대는 PRICE_RANGE 선택지 문구 그대로.
--    같은 이름·지역의 삭제되지 않은 식당이 있으면 건너뛰므로 다시 돌려도 바뀌지 않는다.

ALTER TABLE matching_rule_settings ADD COLUMN IF NOT EXISTS min_tables_per_region INTEGER NOT NULL DEFAULT 2;

ALTER TABLE match_runs ADD COLUMN IF NOT EXISTS region_results JSONB NOT NULL DEFAULT '[]'::jsonb;

INSERT INTO venues (id, name, address, map_url, price_range, region, is_active, created_at, updated_at)
SELECT gen_random_uuid(), v.name, v.address, v.map_url, v.price_range, v.region, TRUE, now(), now()
  FROM (VALUES
    ('홍대/합정', '카멜로 연남', '서울특별시 마포구 연희로1길 57 1.5층', '10,000~20,000원', 'https://map.naver.com/p/search/%EC%B9%B4%EB%A9%9C%EB%A1%9C%20%EC%97%B0%EB%82%A8%20%EC%97%B0%EB%82%A8%EB%8F%99'),
    ('홍대/합정', '연남토마 연남점', '서울특별시 마포구 월드컵북로6길 61', '10,000~20,000원', 'https://map.naver.com/p/search/%EC%97%B0%EB%82%A8%ED%86%A0%EB%A7%88%20%EC%97%B0%EB%82%A8%EC%A0%90'),
    ('홍대/합정', '프리모 바치오바치 홍대본점', '서울특별시 마포구 와우산로23길 44', '20,000~30,000원', 'https://map.naver.com/p/search/%ED%94%84%EB%A6%AC%EB%AA%A8%20%EB%B0%94%EC%B9%98%EC%98%A4%EB%B0%94%EC%B9%98%20%ED%99%8D%EB%8C%80'),
    ('홍대/합정', '키츠스키야키', '서울특별시 마포구 양화로7길 6-5 1층', '20,000~30,000원', 'https://map.naver.com/p/search/%ED%82%A4%EC%B8%A0%EC%8A%A4%ED%82%A4%EC%95%BC%ED%82%A4%20%ED%95%A9%EC%A0%95'),
    ('홍대/합정', '숙성회 화도', '서울특별시 마포구 독막로6길 5 1층', '30,000원 이상 (상관 없음)', 'https://map.naver.com/p/search/%EC%88%99%EC%84%B1%ED%9A%8C%20%ED%99%94%EB%8F%84%20%ED%95%A9%EC%A0%95'),
    ('잠실', '금금 송리단길점', '서울특별시 송파구 백제고분로41길 38 1층', '10,000~20,000원', 'https://map.naver.com/p/search/%EA%B8%88%EA%B8%88%20%EC%86%A1%EB%A6%AC%EB%8B%A8%EA%B8%B8'),
    ('잠실', '송리단취향', '서울특별시 송파구 백제고분로45길 20 1층 102호', '10,000~20,000원', 'https://map.naver.com/p/search/%EC%86%A1%EB%A6%AC%EB%8B%A8%EC%B7%A8%ED%96%A5%20%EC%86%A1%EB%A6%AC%EB%8B%A8%EA%B8%B8'),
    ('잠실', '인딕슬로우', '서울특별시 송파구 백제고분로45길 28 벤도치빌딩 2층', '20,000~30,000원', 'https://map.naver.com/p/search/%EC%9D%B8%EB%94%95%EC%8A%AC%EB%A1%9C%EC%9A%B0%20%EC%86%A1%EB%A6%AC%EB%8B%A8%EA%B8%B8'),
    ('잠실', '퍼프룸 X 치즈룸 월드타워', '서울특별시 송파구 올림픽로 300 롯데월드몰 5층', '20,000~30,000원', 'https://map.naver.com/p/search/%ED%8D%BC%ED%94%84%EB%A3%B8%20X%20%EC%B9%98%EC%A6%88%EB%A3%B8%20%EC%9E%A0%EC%8B%A4'),
    ('잠실', '엘리스 리틀 이태리', '서울특별시 송파구 백제고분로41길 43-21', '30,000원 이상 (상관 없음)', 'https://map.naver.com/p/search/%EC%97%98%EB%A6%AC%EC%8A%A4%20%EB%A6%AC%ED%8B%80%20%EC%9D%B4%ED%83%9C%EB%A6%AC%20%EC%86%A1%EB%A6%AC%EB%8B%A8%EA%B8%B8'),
    ('강남/신논현', '을지다락 강남점', '서울특별시 강남구 강남대로96길 22 2층', '10,000~20,000원', 'https://map.naver.com/p/search/%EC%9D%84%EC%A7%80%EB%8B%A4%EB%9D%BD%20%EA%B0%95%EB%82%A8%EC%A0%90'),
    ('강남/신논현', '브리비트 강남역점', '서울특별시 강남구 강남대로102길 31 1층', '10,000~20,000원', 'https://map.naver.com/p/search/%EB%B8%8C%EB%A6%AC%EB%B9%84%ED%8A%B8%20%EA%B0%95%EB%82%A8%EC%97%AD'),
    ('강남/신논현', '어거스트힐', '서울특별시 강남구 강남대로106길 25', '20,000~30,000원', 'https://map.naver.com/p/search/%EC%96%B4%EA%B1%B0%EC%8A%A4%ED%8A%B8%ED%9E%90%20%EA%B0%95%EB%82%A8'),
    ('강남/신논현', '모아', '서울특별시 강남구 테헤란로5길 31 지상1층', '20,000~30,000원', 'https://map.naver.com/p/search/%EB%AA%A8%EC%95%84%20%EA%B0%95%EB%82%A8%EC%97%AD'),
    ('강남/신논현', '영동그집', '서울특별시 강남구 봉은사로1길 42-8', '30,000원 이상 (상관 없음)', 'https://map.naver.com/p/search/%EC%98%81%EB%8F%99%EA%B7%B8%EC%A7%91%20%EB%85%BC%ED%98%84'),
    ('을지로/종로', '익선취향', '서울특별시 종로구 수표로28길 17-32 1층', '10,000~20,000원', 'https://map.naver.com/p/search/%EC%9D%B5%EC%84%A0%EC%B7%A8%ED%96%A5%20%EC%9D%B5%EC%84%A0%EB%8F%99'),
    ('을지로/종로', '떼르세로 코스테스', '서울특별시 중구 마른내로2길 30 2층', '20,000~30,000원', 'https://map.naver.com/p/search/%EB%96%BC%EB%A5%B4%EC%84%B8%EB%A1%9C%20%EC%BD%94%EC%8A%A4%ED%85%8C%EC%8A%A4%20%EC%9D%84%EC%A7%80%EB%A1%9C'),
    ('을지로/종로', '이층양옥', '서울특별시 종로구 돈화문로11나길 28', '20,000~30,000원', 'https://map.naver.com/p/search/%EC%9D%B4%EC%B8%B5%EC%96%91%EC%98%A5%20%EC%9D%B5%EC%84%A0%EB%8F%99'),
    ('을지로/종로', '루뽀', '서울특별시 종로구 새문안로5길 31 센터포인트빌딩 2층', '30,000원 이상 (상관 없음)', 'https://map.naver.com/p/search/%EB%A3%A8%EB%BD%80%20%EA%B4%91%ED%99%94%EB%AC%B8'),
    ('을지로/종로', '녹지 종로직영점', '서울특별시 종로구 청계천로 65 한남빌딩 1~3층', '30,000원 이상 (상관 없음)', 'https://map.naver.com/p/search/%EB%85%B9%EC%A7%80%20%EC%A2%85%EB%A1%9C%EC%A7%81%EC%98%81%EC%A0%90')
  ) AS v(region, name, address, price_range, map_url)
 WHERE NOT EXISTS (SELECT 1 FROM venues x
                    WHERE x.name = v.name AND x.region = v.region AND x.deleted_at IS NULL);
