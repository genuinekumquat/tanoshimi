-- =====================================================================
-- 메인 "🔥 모집 마감 임박 파티"(7일 안에 출발) 확인·시연용 데모 시드
--
-- 메인은 오늘부터 7일 안에 출발하는 모집중 파티만 보여주므로, 날짜를 고정값으로 넣으면
-- 며칠만 지나도 목록이 빈다. 그래서 출발일을 "실행한 날(CURDATE()) + N일"로 넣는다.
-- → 발표/시연 전날(또는 당일) 한 번 더 실행하면 출발일이 그날 기준으로 다시 맞춰진다.
--
-- 여러 번 실행해도 안전하다(멱등):
--   - 파티는 제목으로 찾아서 없을 때만 새로 만들고, 있으면 출발일·상태만 오늘 기준으로 갱신
--   - 파티원/채팅방/채팅방 멤버는 이미 있으면 건너뜀
--   파티를 지우지 않으므로 시연 중에 생긴 신청·채팅 기록도 그대로 남는다.
--
-- 유자차(yuja@test.com)는 일부러 어느 파티에도 넣지 않았다 - 유자차로 로그인해서
-- 바로 "참여 신청" 흐름을 보여줄 수 있게.
--
-- 실행 경로(다른 db/*.sql과 동일, tanoshimi/tanoshimi 기준):
--   mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/demo_urgent_parties_seed.sql
-- 전제: schema.sql → data.sql(데모 유저) 가 먼저 들어가 있어야 한다.
-- =====================================================================
USE tanoshimi;

SET SQL_SAFE_UPDATES = 0;

SET @seolsan = (SELECT id FROM users WHERE email = 'seolsan@test.com');
SET @mochi   = (SELECT id FROM users WHERE email = 'mochi@test.com');
SET @ramen   = (SELECT id FROM users WHERE email = 'ramenlover@test.com');
SET @yuki    = (SELECT id FROM users WHERE email = 'yuki@test.jp');
SET @kenta   = (SELECT id FROM users WHERE email = 'kenta@test.jp');
SET @haruka  = (SELECT id FROM users WHERE email = 'haruka@test.jp');

-- ---------------------------------------------------------------------
-- 1. 파티 6건 - (제목, 파티장, 출발 D+N, 지역, 태그, 정원, 일수, 1인 예산(만원 단위 - 화면에 "N만원 내외"), 썸네일 팔레트)
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_urgent;
CREATE TEMPORARY TABLE tmp_urgent (
    title VARCHAR(200), owner_id BIGINT, d_plus INT, region VARCHAR(50), style_tag VARCHAR(100),
    capacity TINYINT, duration_days TINYINT, budget_krw INT, thumbnail_url VARCHAR(500), description TEXT
) DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_urgent VALUES
('내일 출발! 오사카 도톤보리 먹방 번개', @ramen, 1, '오사카', '먹거리', 4, 2, 30, 'ph4',
 '타코야키부터 쿠시카츠까지, 도톤보리 일대를 하루 종일 먹으면서 걸어요. 급하게 한 분만 더 모십니다!'),
('후쿠오카 야타이 포장마차 투어', @seolsan, 2, '후쿠오카', '먹거리', 4, 3, 35, 'ph2',
 '나카스 강변 야타이에서 라멘·오뎅 먹고, 다음 날은 다자이후 텐만구까지 가볍게 다녀와요.'),
('교토 기온 밤 산책 & 료칸 1박', @mochi, 3, '교토', '힐링', 4, 2, 45, 'ph3',
 '해 질 무렵 기온 거리와 야사카 신사를 천천히 걷고, 온천 딸린 료칸에서 쉬어가는 일정이에요.'),
('도쿄 시부야·하라주쿠 카페 투어', @haruka, 5, '도쿄', '문화체험', 5, 3, 40, 'ph1',
 '渋谷と原宿のカフェ巡りをしましょう! 한국어·일본어 둘 다 괜찮아요.'),
('오사카 유니버설 스튜디오 같이 뛰어요', @kenta, 6, '오사카', '액티비티', 4, 2, 38, 'ph2',
 '닌텐도 월드 오픈런 목표! 익스프레스 패스는 각자 구매, 아침 7시 난바역 집합이에요.'),
('삿포로 오타루 운하 당일치기', @yuki, 7, '홋카이도', '힐링', 3, 3, 50, 'ph3',
 '삿포로에서 JR 타고 오타루로 넘어가 운하 산책과 오르골당 구경, 저녁엔 스시 먹어요.');

-- 없으면 새로 만들기
INSERT INTO parties (owner_user_id, tour_id, title, description, region, departure_date, duration_days,
                     budget_krw, capacity, style_tag, gender_restriction, nationality_restriction, status, thumbnail_url)
SELECT t.owner_id, NULL, t.title, t.description, t.region, CURDATE() + INTERVAL t.d_plus DAY, t.duration_days,
       t.budget_krw, t.capacity, t.style_tag, 'all', 'all', 'recruiting', t.thumbnail_url
FROM tmp_urgent t
WHERE t.owner_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM parties p WHERE p.title = t.title);

-- 이미 있으면 출발일·상태를 오늘 기준으로 다시 맞추기
UPDATE parties p JOIN tmp_urgent t ON p.title = t.title
SET p.departure_date = CURDATE() + INTERVAL t.d_plus DAY,
    p.status = 'recruiting',
    p.blinded = FALSE;

-- ---------------------------------------------------------------------
-- 2. 파티원 (파티장 + 참여자) - 잔여석이 파티마다 다르게 보이도록
--    도톤보리 3/4(잔여1) · 후쿠오카 2/4 · 교토 3/4(잔여1) · 도쿄 2/5 · USJ 2/4 · 오타루 2/3(잔여1)
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_urgent_members;
CREATE TEMPORARY TABLE tmp_urgent_members (title VARCHAR(200), user_id BIGINT, role VARCHAR(10)) DEFAULT CHARSET=utf8mb4;
INSERT INTO tmp_urgent_members
SELECT title, owner_id, 'owner' FROM tmp_urgent;
INSERT INTO tmp_urgent_members VALUES
('내일 출발! 오사카 도톤보리 먹방 번개', @seolsan, 'member'),
('내일 출발! 오사카 도톤보리 먹방 번개', @kenta,   'member'),
('후쿠오카 야타이 포장마차 투어',        @ramen,   'member'),
('교토 기온 밤 산책 & 료칸 1박',         @yuki,    'member'),
('교토 기온 밤 산책 & 료칸 1박',         @haruka,  'member'),
('도쿄 시부야·하라주쿠 카페 투어',       @mochi,   'member'),
('오사카 유니버설 스튜디오 같이 뛰어요', @ramen,   'member'),
('삿포로 오타루 운하 당일치기',          @mochi,   'member');

INSERT IGNORE INTO party_members (party_id, user_id, role)
SELECT p.id, m.user_id, m.role
FROM tmp_urgent_members m JOIN parties p ON p.title = m.title
WHERE m.user_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- 3. 파티 채팅방 + 채팅방 멤버 (앱의 파티 생성 흐름과 동일하게 파티당 채팅방 1개)
-- ---------------------------------------------------------------------
INSERT INTO chat_rooms (type, party_id)
SELECT 'party', p.id
FROM parties p JOIN tmp_urgent t ON p.title = t.title
WHERE NOT EXISTS (SELECT 1 FROM chat_rooms r WHERE r.type = 'party' AND r.party_id = p.id);

INSERT IGNORE INTO chat_room_members (room_id, user_id)
SELECT r.id, m.user_id
FROM tmp_urgent_members m
JOIN parties p ON p.title = m.title
JOIN chat_rooms r ON r.type = 'party' AND r.party_id = p.id
WHERE m.user_id IS NOT NULL;

DROP TEMPORARY TABLE tmp_urgent_members;
DROP TEMPORARY TABLE tmp_urgent;

-- 결과 확인
SELECT p.id, p.title, p.departure_date, DATEDIFF(p.departure_date, CURDATE()) AS d_day,
       p.capacity, (SELECT COUNT(*) FROM party_members pm WHERE pm.party_id = p.id) AS joined
FROM parties p
WHERE p.status = 'recruiting' AND p.departure_date BETWEEN CURDATE() AND CURDATE() + INTERVAL 7 DAY
ORDER BY p.departure_date;
