-- ================================================
-- 와썹하우스 Mock Data
-- 비밀번호: Test1234! (BCrypt)
-- ================================================

-- 1. LOCATIONS (5곳)
INSERT INTO locations (id, name, address, map_url, status, max_capacity, memo, created_at, updated_at)
VALUES
    ('a1000000-0000-0000-0000-000000000001', '연남동 루프탑 하우스', '서울 마포구 연남로 45-3 4층', 'https://maps.google.com/?q=연남동+루프탑', 'ACTIVE', 20, '루프탑 공간, 우천 시 실내 사용 가능', NOW(), NOW()),
    ('a1000000-0000-0000-0000-000000000002', '성수동 커뮤니티 스페이스', '서울 성동구 성수이로 78 2층', 'https://maps.google.com/?q=성수동+커뮤니티', 'ACTIVE', 16, '빔프로젝터 구비, 음향 장비 있음', NOW(), NOW()),
    ('a1000000-0000-0000-0000-000000000003', '홍대 소셜클럽', '서울 마포구 와우산로 29나길 12', 'https://maps.google.com/?q=홍대+소셜클럽', 'ACTIVE', 20, '바 형태 공간, 음료 포함', NOW(), NOW()),
    ('a1000000-0000-0000-0000-000000000004', '이태원 가든하우스', '서울 용산구 이태원로 200', 'https://maps.google.com/?q=이태원+가든하우스', 'ACTIVE', 12, '정원 있음, 소규모 모임 최적', NOW(), NOW()),
    ('a1000000-0000-0000-0000-000000000005', '합정 북카페 라운지', '서울 마포구 합정동 358-3', 'https://maps.google.com/?q=합정+북카페', 'ACTIVE', 14, '책과 함께하는 분위기, 조용한 모임 적합', NOW(), NOW());


-- 2. USERS (관리자 1 + 일반 10명)
INSERT INTO users (id, email, password, name, gender, age, nickname, phone, instagram_id, mbti, job, intro, is_admin, created_at, updated_at)
VALUES
    ('b1000000-0000-0000-0000-000000000001', 'admin@whatsuphouse.com', '$2b$10$a/2hYslOSx8ABK7iUnPGZ.NaYrLmA2kG3mr44Q.Pa..YXVeMF9NDy', '김큐레이터', 'MALE', 30, '큐레이터', '01012340000', 'curator_wh', 'ENFJ', '플랫폼 운영', '와썹하우스를 운영합니다', TRUE, NOW(), NOW()),
    ('b1000000-0000-0000-0000-000000000002', 'user1@test.com', '$2b$10$cHigH8AL.RdEUVOVEh2tLOmE8muqJKxkGtB.ouLGRc72HTbG1qZOq', '이지은', 'FEMALE', 26, '지은이', '01011112222', 'jieun_26', 'INFP', '그래픽 디자이너', '조용한 게 좋아요', FALSE, NOW(), NOW()),
    ('b1000000-0000-0000-0000-000000000003', 'user2@test.com', '$2b$10$XRop.OqqkfryGqQ86waHI.Bepb1jAKGpz93eRHJB03X3ZeALOxydy', '박준서', 'MALE', 29, '준서', '01022223333', 'junseoPark', 'ENTP', '개발자', '새로운 사람 만나는 걸 좋아해요', FALSE, NOW(), NOW()),
    ('b1000000-0000-0000-0000-000000000004', 'user3@test.com', '$2b$10$KW4eGLYhP0QuTSMe3rfJA.Z.JWRmbX0CgQKwG3rp2NJHtZlbIreKS', '최수아', 'FEMALE', 24, '수아', '01033334444', 'sua_choi', 'ISFJ', '대학원생', '독서와 카페 좋아요', FALSE, NOW(), NOW()),
    ('b1000000-0000-0000-0000-000000000005', 'user4@test.com', '$2b$10$dd3Aew6R5FKrS1BdaxXzeubRSvHLzWGdWOnAsLGfpvnYm54wyre9K', '정민준', 'MALE', 31, '민준정', '01044445555', 'minjun31', 'ESTJ', '마케터', '활동적인 걸 좋아해요', FALSE, NOW(), NOW()),
    ('b1000000-0000-0000-0000-000000000006', 'user5@test.com', '$2b$10$4w1w4Av49xu5ZDyJ3fQ.XuBDvGvPdEq6ByAYiK2tB7Jrmfxon4A0.', '한예림', 'FEMALE', 27, '예림', '01055556666', 'yerim_h', 'ENFP', '프리랜서', '음악이랑 재즈 좋아해요', FALSE, NOW(), NOW()),
    ('b1000000-0000-0000-0000-000000000007', 'user6@test.com', '$2b$10$5il6v3ChFnVodaCYtdSeY.5EbOkV.fkTeEcA5iUY1e7pL4h1V.V/i', '오태양', 'MALE', 25, '태양오', '01066667777', 'taeyang_o', 'ISTP', 'UX 디자이너', '미니멀하게 살아요', FALSE, NOW(), NOW()),
    ('b1000000-0000-0000-0000-000000000008', 'user7@test.com', '$2b$10$Nr8yMahMnOOJzzxciQ2skOP88VmwrjnRvK0r94NcSfWocsUyKUVP6', '윤서연', 'FEMALE', 28, '서연윤', '01077778888', 'seoyeon_y', 'INFJ', '사진작가', '빛과 공간을 담아요', FALSE, NOW(), NOW()),
    ('b1000000-0000-0000-0000-000000000009', 'user8@test.com', '$2b$10$0unoneJYduXEOwDymuCXSuqOTgEz.MH8ONYnuCywDEQtAuNBfm5SC', '임도현', 'MALE', 32, '도현임', '01088889999', 'dohyun_im', 'INTJ', '회사원', '퇴근 후 맥주 한 잔', FALSE, NOW(), NOW()),
    ('b1000000-0000-0000-0000-000000000010', 'user9@test.com', '$2b$10$I8fggcguYS7HmhLAqX54Q.5vUO1Rp8VjBs2AMvQMCi3JyNi87s.oO', '강나연', 'FEMALE', 23, '나연강', '01099990000', 'nayeon_k', 'ESFP', '대학생', '활발하고 낯가림 없어요', FALSE, NOW(), NOW()),
    ('b1000000-0000-0000-0000-000000000011', 'user10@test.com', '$2b$10$kPkrhFO659b9o0ss/2ADPeC4yteXKw.rm6MQ7ZpTeySHqZG7mxxEG', '송재원', 'MALE', 30, '재원송', '01010101010', 'jaewon_s', 'ENTJ', '스타트업 창업자', '연결과 확장이 삶의 테마예요', FALSE, NOW(), NOW());


-- 3. GATHERINGS (10개)
INSERT INTO gatherings (id, title, description, location_id, event_date, start_time, end_time, price, max_attendees, status, thumbnail_url, created_at, updated_at)
VALUES
    ('c1000000-0000-0000-0000-000000000001', '봄밤의 루프탑 재즈 소셜', '루프탑에서 재즈 음악을 들으며 새로운 사람들과 가볍게 대화를 나눠요. 음료와 간식이 제공됩니다.', 'a1000000-0000-0000-0000-000000000001', '2026-04-05', '19:00', '21:30', 25000, 16, 'COMPLETED', 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800', NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000002', '일요일 오후의 북클럽 모임', '한 달에 한 권, 같이 읽고 느낀 점을 나눠요. 이번 달 책: 김영하 작가의 여행의 이유.', 'a1000000-0000-0000-0000-000000000005', '2026-04-06', '14:00', '16:30', 15000, 10, 'COMPLETED', 'https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=800', NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000003', '직장인을 위한 목요 네트워킹', '퇴근 후 가볍게. 같은 시대를 사는 2030 직장인들의 솔직한 이야기.', 'a1000000-0000-0000-0000-000000000002', '2026-04-09', '19:30', '21:30', 20000, 14, 'COMPLETED', 'https://images.unsplash.com/photo-1515187029135-18ee286d815b?w=800', NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000004', '감성 필름 사진 산책', '홍대 골목을 함께 걸으며 필름 카메라로 일상을 담아요. 카메라 없어도 OK, 대여 가능.', 'a1000000-0000-0000-0000-000000000003', '2026-04-12', '13:00', '16:00', 18000, 12, 'CLOSED', 'https://images.unsplash.com/photo-1500051638674-ff996a0ec29e?w=800', NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000005', '소규모 요리 클래스 — 이탈리안 파스타', '직접 반죽부터 만드는 생파스타 클래스. 만든 음식을 함께 먹으며 마무리해요.', 'a1000000-0000-0000-0000-000000000004', '2026-04-13', '12:00', '15:00', 35000, 8, 'CLOSED', 'https://images.unsplash.com/photo-1556909114-f6e7ad7d3136?w=800', NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000006', '와인 & 치즈 나이트', '자연주의 내추럴 와인 3종 테이스팅. 소믈리에가 직접 설명해드려요.', 'a1000000-0000-0000-0000-000000000003', '2026-04-16', '19:00', '21:30', 40000, 16, 'OPEN', 'https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?w=800', NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000007', '봄 피크닉 소셜 — 한강', '돗자리 펴고 한강에서 만나요. 각자 먹거리 하나씩 가져오면 돼요.', 'a1000000-0000-0000-0000-000000000001', '2026-04-19', '15:00', '18:00', 10000, 20, 'OPEN', 'https://images.unsplash.com/photo-1523301343968-6a6ebf63c672?w=800', NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000008', '수요 저녁 — 새로운 사람과 밥 한 끼', '처음 만나는 사람들과 저녁 한 끼. 자연스럽게 이야기 나눠요.', 'a1000000-0000-0000-0000-000000000002', '2026-04-22', '18:30', '20:30', 22000, 12, 'OPEN', 'https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=800', NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000009', '드로잉 클래스 — 일상 스케치', '그림 못 그려도 OK. 볼펜 하나로 내 일상을 그려보는 시간.', 'a1000000-0000-0000-0000-000000000005', '2026-04-26', '14:00', '16:30', 28000, 10, 'OPEN', 'https://images.unsplash.com/photo-1513364776144-60967b0f800f?w=800', NOW(), NOW()),
    ('c1000000-0000-0000-0000-000000000010', '4월 마지막 밤 — 루프탑 파티', '4월을 마무리하는 루프탑 소셜. DJ 세트, 음료 무제한.', 'a1000000-0000-0000-0000-000000000001', '2026-04-30', '20:00', '23:00', 35000, 20, 'OPEN', 'https://images.unsplash.com/photo-1496337589254-7e19d01cec44?w=800', NOW(), NOW());
