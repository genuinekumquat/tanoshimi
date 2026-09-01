-- =====================================================================
-- 마이페이지 확인용 데모 데이터 (v2 - 칭호 판정까지 확인 가능한 분량)
-- 담당: 김민규(⑥ 마이페이지) / 로컬 확인용, 커밋 필수 아님
--
-- 왜 필요한가
--  - data.sql 의 시드 파티 4건은 전부 status='recruiting' 이라 완료된 여행이 0건이다.
--    그러면 지도는 전 지역 회색, 칭호는 하나도 안 붙는다(고장이 아니다).
--  - v1 은 지도만 채웠는데 칭호 기준(탐험가 15회, 광역시별 5회, 지도 수집가 10개 지역)에
--    한참 못 미쳐 '첫 발자국' 하나만 붙었다. 판정 로직을 눈으로 확인할 수 없었다.
--  - 그래서 유자차에게 완료 파티 29건(13개 지역)을 만들어 준다.
--
-- 실행 후 유자차로 로그인:  yuja@test.com / Test1234!
--
-- 붙어야 하는 칭호 12종 (TitleService 판정 결과와 대조용)
--   T1  첫 발자국        여행 1회
--   T15 탐험가           여행 15회
--   R_ALL8   8도 정복자   전국 8개 권역(수도권/강원/충청/전북/광주·전남/대구·경북/부산·울산·경남/제주)
--   R_JEJU   감귤 마니아  제주 5회
--   M_SEOUL  한강뷰 마스터 서울 5회
--   M_BUSAN  부산 갈매기  부산 5회
--   J_KANSAI 간사이 마스터 간사이 5회 (오사카 3 + 교토 2)
--   MAN40    매너왕       매너온도 40도 (아래에서 41.0 으로 올린다)
--   P_HOST   파티리더     개설 10회 (데모 파티를 유자차가 개설)
--   P_JOIN   프로참석러   참여 10회
--   A_FEST   축제 마니아  축제 파티 5회 (오사카 3 + 교토 2)
--   A_MAP    지도 수집가  10개 지역
--
-- 스냅(사진)도 함께 넣는다
--   지도에서 지역에 마우스를 올리면 그 지역 스냅이 뜨는데, 시드에 게시글이 하나도 없어
--   확인이 안 된다. 지역 태그를 붙인 글 21건을 넣어 준다(썸네일은 자리표시 ph1~ph4).
--
-- 잠긴 채로 남아야 정상인 것
--   T30/T50/T80/T100 (30회 미만), 명예 OO인 5종 · 여행 거리 4종 · 야경 헌터
--   (집 주소/스냅 분류가 없어 판정 자체를 안 한다 - TitleService 주석 참고)
--
-- 여러 번 실행해도 결과가 같다('[demo]' 파티를 지우고 다시 넣는다).
-- =====================================================================
USE tanoshimi;

SET SQL_SAFE_UPDATES = 0;

SET @uid = (SELECT id FROM users WHERE email = 'yuja@test.com');
SET @tid = (SELECT MIN(id) FROM tours);

-- 1. 이전 데모 데이터 정리 (FK 때문에 자식 먼저)
DELETE FROM party_members
 WHERE party_id IN (SELECT id FROM (SELECT id FROM parties WHERE title LIKE '[demo]%') x);
DELETE FROM parties WHERE title LIKE '[demo]%';

-- 2. 완료 파티 생성 (유자차가 개설 -> 파티리더 조건도 함께 충족)
INSERT INTO parties
  (owner_user_id, tour_id, title, region, departure_date, duration_days, capacity, style_tag, status)
VALUES
  (@uid, @tid, '[demo] 서울 여행 1', '서울', DATE_SUB(CURDATE(), INTERVAL 7 DAY), 3, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 서울 여행 2', '서울', DATE_SUB(CURDATE(), INTERVAL 14 DAY), 4, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 서울 여행 3', '서울', DATE_SUB(CURDATE(), INTERVAL 21 DAY), 2, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 서울 여행 4', '서울', DATE_SUB(CURDATE(), INTERVAL 28 DAY), 3, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 서울 여행 5', '서울', DATE_SUB(CURDATE(), INTERVAL 35 DAY), 4, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 부산 여행 1', '부산', DATE_SUB(CURDATE(), INTERVAL 42 DAY), 2, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 부산 여행 2', '부산', DATE_SUB(CURDATE(), INTERVAL 49 DAY), 3, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 부산 여행 3', '부산', DATE_SUB(CURDATE(), INTERVAL 56 DAY), 4, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 부산 여행 4', '부산', DATE_SUB(CURDATE(), INTERVAL 63 DAY), 2, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 부산 여행 5', '부산', DATE_SUB(CURDATE(), INTERVAL 70 DAY), 3, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 제주 여행 1', '제주', DATE_SUB(CURDATE(), INTERVAL 77 DAY), 4, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 제주 여행 2', '제주', DATE_SUB(CURDATE(), INTERVAL 84 DAY), 2, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 제주 여행 3', '제주', DATE_SUB(CURDATE(), INTERVAL 91 DAY), 3, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 제주 여행 4', '제주', DATE_SUB(CURDATE(), INTERVAL 98 DAY), 4, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 제주 여행 5', '제주', DATE_SUB(CURDATE(), INTERVAL 105 DAY), 2, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 오사카 여행 1', '오사카', DATE_SUB(CURDATE(), INTERVAL 112 DAY), 3, 4, '축제', 'completed'),
  (@uid, @tid, '[demo] 오사카 여행 2', '오사카', DATE_SUB(CURDATE(), INTERVAL 119 DAY), 4, 4, '축제', 'completed'),
  (@uid, @tid, '[demo] 오사카 여행 3', '오사카', DATE_SUB(CURDATE(), INTERVAL 126 DAY), 2, 4, '축제', 'completed'),
  (@uid, @tid, '[demo] 교토 여행 1', '교토', DATE_SUB(CURDATE(), INTERVAL 133 DAY), 3, 4, '축제', 'completed'),
  (@uid, @tid, '[demo] 교토 여행 2', '교토', DATE_SUB(CURDATE(), INTERVAL 140 DAY), 4, 4, '축제', 'completed'),
  (@uid, @tid, '[demo] 도쿄 여행 1', '도쿄', DATE_SUB(CURDATE(), INTERVAL 147 DAY), 2, 4, '액티비티', 'completed'),
  (@uid, @tid, '[demo] 도쿄 여행 2', '도쿄', DATE_SUB(CURDATE(), INTERVAL 154 DAY), 3, 4, '액티비티', 'completed'),
  (@uid, @tid, '[demo] 홋카이도 여행 1', '홋카이도', DATE_SUB(CURDATE(), INTERVAL 161 DAY), 4, 4, '액티비티', 'completed'),
  (@uid, @tid, '[demo] 후쿠오카 여행 1', '후쿠오카', DATE_SUB(CURDATE(), INTERVAL 168 DAY), 2, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 대구 여행 1', '대구', DATE_SUB(CURDATE(), INTERVAL 175 DAY), 3, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 강원 여행 1', '강원', DATE_SUB(CURDATE(), INTERVAL 182 DAY), 4, 4, '액티비티', 'completed'),
  (@uid, @tid, '[demo] 대전 여행 1', '대전', DATE_SUB(CURDATE(), INTERVAL 189 DAY), 2, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 전북 여행 1', '전북', DATE_SUB(CURDATE(), INTERVAL 196 DAY), 3, 4, '힐링', 'completed'),
  (@uid, @tid, '[demo] 광주 여행 1', '광주', DATE_SUB(CURDATE(), INTERVAL 203 DAY), 4, 4, '힐링', 'completed');

-- 3. 개설자를 파티원으로 등록 (히트맵·칭호는 party_members 기준으로 집계한다)
INSERT IGNORE INTO party_members (party_id, user_id, role)
SELECT id, @uid, 'owner' FROM parties WHERE title LIKE '[demo]%';

-- 4. 매너온도 41.0 (매너왕 40도 확인용. 기본 시드는 39.2 라 아슬아슬하게 미달)
UPDATE users SET manner_temp = 41.0 WHERE id = @uid;

-- 5. 지역 태그가 붙은 스냅(게시글). 지도 호버 시 그 지역 사진으로 뜬다.
DELETE FROM posts WHERE user_id = @uid AND content LIKE '%에서 찍은 스냅입니다.';
INSERT INTO posts
  (user_id, party_id, title, content, region, thumbnail_url, like_count, created_at, updated_at)
VALUES
  (@uid, NULL, '한강 야경', '서울에서 찍은 스냅입니다.', '서울', 'ph1', 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (@uid, NULL, '북촌 한옥마을', '서울에서 찍은 스냅입니다.', '서울', 'ph3', 0, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY)),
  (@uid, NULL, '남산타워', '서울에서 찍은 스냅입니다.', '서울', 'ph2', 0, DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY)),
  (@uid, NULL, '감천문화마을', '부산에서 찍은 스냅입니다.', '부산', 'ph4', 0, DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_SUB(NOW(), INTERVAL 16 DAY)),
  (@uid, NULL, '해운대 해변', '부산에서 찍은 스냅입니다.', '부산', 'ph1', 0, DATE_SUB(NOW(), INTERVAL 21 DAY), DATE_SUB(NOW(), INTERVAL 21 DAY)),
  (@uid, NULL, '광안대교', '부산에서 찍은 스냅입니다.', '부산', 'ph2', 0, DATE_SUB(NOW(), INTERVAL 26 DAY), DATE_SUB(NOW(), INTERVAL 26 DAY)),
  (@uid, NULL, '성산일출봉', '제주에서 찍은 스냅입니다.', '제주', 'ph3', 0, DATE_SUB(NOW(), INTERVAL 31 DAY), DATE_SUB(NOW(), INTERVAL 31 DAY)),
  (@uid, NULL, '우도 자전거', '제주에서 찍은 스냅입니다.', '제주', 'ph1', 0, DATE_SUB(NOW(), INTERVAL 36 DAY), DATE_SUB(NOW(), INTERVAL 36 DAY)),
  (@uid, NULL, '한라산 등반', '제주에서 찍은 스냅입니다.', '제주', 'ph4', 0, DATE_SUB(NOW(), INTERVAL 41 DAY), DATE_SUB(NOW(), INTERVAL 41 DAY)),
  (@uid, NULL, '도톤보리 야경', '오사카에서 찍은 스냅입니다.', '오사카', 'ph1', 0, DATE_SUB(NOW(), INTERVAL 46 DAY), DATE_SUB(NOW(), INTERVAL 46 DAY)),
  (@uid, NULL, '우메다 스카이', '오사카에서 찍은 스냅입니다.', '오사카', 'ph2', 0, DATE_SUB(NOW(), INTERVAL 51 DAY), DATE_SUB(NOW(), INTERVAL 51 DAY)),
  (@uid, NULL, '오사카성', '오사카에서 찍은 스냅입니다.', '오사카', 'ph3', 0, DATE_SUB(NOW(), INTERVAL 56 DAY), DATE_SUB(NOW(), INTERVAL 56 DAY)),
  (@uid, NULL, '기요미즈데라', '교토에서 찍은 스냅입니다.', '교토', 'ph4', 0, DATE_SUB(NOW(), INTERVAL 61 DAY), DATE_SUB(NOW(), INTERVAL 61 DAY)),
  (@uid, NULL, '아라시야마 대나무숲', '교토에서 찍은 스냅입니다.', '교토', 'ph3', 0, DATE_SUB(NOW(), INTERVAL 66 DAY), DATE_SUB(NOW(), INTERVAL 66 DAY)),
  (@uid, NULL, '시부야 스크램블', '도쿄에서 찍은 스냅입니다.', '도쿄', 'ph2', 0, DATE_SUB(NOW(), INTERVAL 71 DAY), DATE_SUB(NOW(), INTERVAL 71 DAY)),
  (@uid, NULL, '센소지', '도쿄에서 찍은 스냅입니다.', '도쿄', 'ph1', 0, DATE_SUB(NOW(), INTERVAL 76 DAY), DATE_SUB(NOW(), INTERVAL 76 DAY)),
  (@uid, NULL, '오타루 운하', '홋카이도에서 찍은 스냅입니다.', '홋카이도', 'ph3', 0, DATE_SUB(NOW(), INTERVAL 81 DAY), DATE_SUB(NOW(), INTERVAL 81 DAY)),
  (@uid, NULL, '캐널시티', '후쿠오카에서 찍은 스냅입니다.', '후쿠오카', 'ph4', 0, DATE_SUB(NOW(), INTERVAL 86 DAY), DATE_SUB(NOW(), INTERVAL 86 DAY)),
  (@uid, NULL, '서문시장', '대구에서 찍은 스냅입니다.', '대구', 'ph2', 0, DATE_SUB(NOW(), INTERVAL 91 DAY), DATE_SUB(NOW(), INTERVAL 91 DAY)),
  (@uid, NULL, '속초 바다', '강원에서 찍은 스냅입니다.', '강원', 'ph1', 0, DATE_SUB(NOW(), INTERVAL 96 DAY), DATE_SUB(NOW(), INTERVAL 96 DAY)),
  (@uid, NULL, '무등산', '광주에서 찍은 스냅입니다.', '광주', 'ph3', 0, DATE_SUB(NOW(), INTERVAL 101 DAY), DATE_SUB(NOW(), INTERVAL 101 DAY));

-- 6. 확인
SELECT COUNT(*) AS 완료파티, COUNT(DISTINCT region) AS 지역수
  FROM parties p JOIN party_members pm ON pm.party_id = p.id
 WHERE pm.user_id = @uid AND p.status = 'completed';

SELECT region, COUNT(*) AS 횟수
  FROM parties p JOIN party_members pm ON pm.party_id = p.id
 WHERE pm.user_id = @uid AND p.status = 'completed'
 GROUP BY region ORDER BY 횟수 DESC, region;

SET SQL_SAFE_UPDATES = 1;

-- =====================================================================
-- 되돌리기
-- =====================================================================
-- USE tanoshimi;
-- SET SQL_SAFE_UPDATES = 0;
-- DELETE FROM party_members WHERE party_id IN (SELECT id FROM (SELECT id FROM parties WHERE title LIKE '[demo]%') x);
-- DELETE FROM parties WHERE title LIKE '[demo]%';
-- DELETE FROM posts WHERE content LIKE '%에서 찍은 스냅입니다.';
-- DELETE FROM user_titles WHERE user_id = (SELECT id FROM users WHERE email='yuja@test.com');
-- UPDATE users SET manner_temp = 39.2 WHERE email = 'yuja@test.com';
-- SET SQL_SAFE_UPDATES = 1;
