-- V27: 예전 체험 계정 이름("체험" + 8자리)을 "김민준(심사용)" 형태로 바꾼다. (KAN-428)
--
-- 1. 성·이름 목록은 DemoAccountService(KAN-422)와 같다. 성별(FEMALE/MALE)에 맞는 이름 목록을 쓴다.
-- 2. 후보 이름은 md5 순서로 섞어 고정하고, 이미 어떤 users.nickname에도 쓰인 이름은 뺀다(닉네임 UNIQUE).
--    체험 계정은 성별별 created_at 순번으로 남은 후보와 1:1로 짝지어 중복이 없다.
--    후보가 바닥나면 "체험xxxxxxxx(심사용)"으로 둔다.
-- 3. 신청서 이름(applications.name)은 회원 이름 복사본이라 같은 값이면 함께 바꾼다.
--    application_answers(입력 원문)·deposit_notifications.parsed_name(은행 알림 원문)·payout_requests.holder(계좌 예금주)는 기록이라 두지 않는다.
-- 이미 "(심사용)"으로 끝나는 계정은 건너뛰므로 다시 돌려도 바뀌지 않는다.

WITH surnames(surname, s_ord) AS (
    SELECT * FROM unnest(ARRAY['김','이','박','최','정','강','조','윤','장','임',
                               '한','오','서','신','권','황','안','송','류','홍']) WITH ORDINALITY
),
given_names(gender, given) AS (
    SELECT 'MALE', unnest(ARRAY['민준','서준','도윤','예준','시우','주원','하준','지호','준서','건우',
                                '현우','우진','지훈','태정','성민','동현','재원','승현','정우','민재'])
    UNION ALL
    SELECT 'FEMALE', unnest(ARRAY['서연','지우','서윤','하은','민서','지유','윤서','채원','수아','지민',
                                  '예린','다은','소연','유진','수빈','은지','혜원','가은','나연','세아'])
),
candidates AS (
    SELECT g.gender, s.surname || g.given || '(심사용)' AS new_name,
           row_number() OVER (PARTITION BY g.gender ORDER BY md5(s.surname || g.given), s.s_ord) AS rn
      FROM given_names g
     CROSS JOIN surnames s
     WHERE NOT EXISTS (SELECT 1 FROM users x WHERE x.nickname = s.surname || g.given || '(심사용)')
),
legacy AS (
    SELECT u.id, u.name AS old_name,
           CASE WHEN u.gender = 'MALE' THEN 'MALE' ELSE 'FEMALE' END AS gender,
           row_number() OVER (PARTITION BY CASE WHEN u.gender = 'MALE' THEN 'MALE' ELSE 'FEMALE' END
                              ORDER BY u.created_at, u.id) AS rn
      FROM users u
     WHERE u.is_demo = true
       AND u.name LIKE '체험%'
       AND u.name NOT LIKE '%(심사용)'
),
mapping AS (
    SELECT l.id, l.old_name, COALESCE(c.new_name, l.old_name || '(심사용)') AS new_name
      FROM legacy l
      LEFT JOIN candidates c ON c.gender = l.gender AND c.rn = l.rn
),
renamed AS (
    UPDATE users u
       SET name = m.new_name, nickname = m.new_name, updated_at = now()
      FROM mapping m
     WHERE u.id = m.id
    RETURNING u.id, m.old_name, m.new_name
)
UPDATE applications a
   SET name = r.new_name, updated_at = now()
  FROM renamed r
 WHERE a.user_id = r.id
   AND a.name = r.old_name;
