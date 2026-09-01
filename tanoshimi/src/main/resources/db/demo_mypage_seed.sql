-- =====================================================================
-- 마이페이지 "내 여행"/히트맵/칭호 확인용 데모 시드 - 유자차(yuja@test.com) 계정
-- 담당: 김민규(⑥ 마이페이지) / 로컬 확인용, 커밋 필수 아님
--
-- [v20-7] 다음 3개 스크립트가 하던 일을 순서대로 합친 것이다:
--   demo_heatmap_data.sql(완료 파티 29건 + 지역 태그 스냅 21건)
--   → reset_yuja_demo_trips.sql(SOLO 여행 2건 + 그 여행에 연결된 스냅 3건)
--   → link_snaps_all_pending_party_trips.sql(파티 29건 전부에 스냅 1장씩 연동)
-- 예전엔 이 세 파일을 정확한 순서로 따로 실행해야 했는데, 이제 이 파일 하나면 된다(구
-- 파일들은 db/ 에 "폐기됨" 안내만 남기고 실제 내용은 이 파일로 옮겼다).
-- demo_my_trips.sql(v19 시절 구식 SOLO 2건)과 demo_solo_travel_gyeongbuk.sql(trip_id 없는
-- 구식 스냅 - 지금 my_trips 모델에서는 여행 횟수에 아예 안 잡힘)은 팀 논의 후 통합 대상에서
-- 제외했다(2026-09-01) - 지금 설계와 안 맞는 예전 데이터라 빼고 단순화하기로 함.
--
-- ⚠️ 반드시 "두 번" 실행해야 완전한 효과가 난다.
--   1차 실행 - 파티 29건 + 지역 태그 스냅 21건 + SOLO 여행 2건 + 그 스냅 3건까지 채워진다.
--     이 시점엔 파티→"내 여행"(my_trips) 자동 동기화가 아직 안 됐다(그건 앱 코드가 하는
--     일 - MyTripService.syncFromCompletedParties - SQL만으로는 대신할 수 없다). 그래서
--     "파티 스냅 연동" 파트는 대상이 0건이라 조용히 아무 것도 안 하고 넘어간다(정상 동작,
--     에러 아님).
--   → 서버를 켜고 유자차로 로그인해서 /mypage 또는 /mypage/mytrip 을 한 번 열어본다.
--   2차 실행 - 이번엔 my_trips 에 파티 29건이 다 있으니 "파티 스냅 연동" 파트가 실제로
--     29건 전부에 스냅을 채워 카운트 상태로 만든다.
--   → 브라우저 새로고침 - 히트맵/칭호/"내 여행" 목록이 전부 채워진 상태로 보여야 한다.
--
-- 여러 번 실행해도 안전하다(전부 지우고-다시넣기/NOT EXISTS 기반이라 멱등) - "두 번"은
-- 최소 필요 횟수일 뿐, 세 번 네 번 실행해도 결과는 똑같다.
--
-- 실행 경로(다른 db/*.sql과 동일, tanoshimi/tanoshimi 기준):
--   mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/demo_mypage_seed.sql
-- fresh DB라면 이 스크립트 전에 schema.sql → data.sql → migration_v16~v19 순서로 먼저
-- 실행돼 있어야 한다 - 자세한 순서는 db/README.md 참고.
--
-- 실행 후 유자차로 로그인: yuja@test.com / Test1234!
--
-- 확인 가능한 칭호 12종, 지역별 상세 등 더 자세한 배경은
-- claude/마이페이지-지도목업-코드반영-이어서.md 의 v20-5·v20-6·v20-7 섹션 참고.
-- =====================================================================
USE tanoshimi;

SET SQL_SAFE_UPDATES = 0;

SET @uid = (SELECT id FROM users WHERE email = 'yuja@test.com');
SET @tid = (SELECT MIN(id) FROM tours);
SELECT @uid AS yuja_user_id; -- NULL이면 계정이 없다는 뜻 - 아래 문장들은 조용히 0건 처리됨

-- ---------------------------------------------------------------------
-- PART 1. 완료 파티 29건 (지역 다양성 확보 - 칭호 판정용, 유자차가 개설 → 파티리더 조건도 함께 충족)
-- ---------------------------------------------------------------------
DELETE FROM party_members
 WHERE party_id IN (SELECT id FROM (SELECT id FROM parties WHERE title LIKE '[demo]%') x);
DELETE FROM parties WHERE title LIKE '[demo]%';

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

INSERT IGNORE INTO party_members (party_id, user_id, role)
SELECT id, @uid, 'owner' FROM parties WHERE title LIKE '[demo]%';

UPDATE users SET manner_temp = 41.0 WHERE id = @uid; -- 매너왕(40도) 확인용, 기본 시드 39.2론 미달

-- ---------------------------------------------------------------------
-- PART 2. 지역 태그 스냅 21건 (지도 호버 시 사진 표시용 - PostService.regionTaggedPosts 근거,
--   trip 연결/여행 횟수 집계와는 무관한 순수 장식용 데이터)
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- PART 3. "내 여행" SOLO 2건 + 그 여행을 선택해서 올린 것처럼 trip_id가 채워진 스냅 3건
--   ("여행을 먼저 등록하고, 글쓰기에서 그 여행을 선택"하는 정방향 플로우의 결과를 SQL로
--   재현. 여행지는 지도에서 바로 확인되도록 골랐다 - 오사카=간사이 드릴다운 하위 현 이름과
--   일치, 부산=한국 오버뷰 이름과 바로 매칭.)
--   [v20-7 통합 시 변경] 원래 reset_yuja_demo_trips.sql은 유자차의 my_trips/posts를 전부
--   지우고 다시 채웠는데, 이 파일에 합치면서 그러면 PART 1/2가 만든 파티·지역태그 스냅까지
--   같이 지워지므로, 이 두 SOLO 여행(제목으로 식별)과 그에 딸린 posts만 좁혀서 지우도록
--   바꿨다 - 최종 결과는 이전과 동일, 다른 파트를 덮어쓰지 않을 뿐.
-- ---------------------------------------------------------------------
DELETE FROM post_likes WHERE post_id IN (
    SELECT id FROM (
        SELECT p.id FROM posts p
        WHERE p.user_id = @uid
          AND p.trip_id IN (SELECT id FROM my_trips WHERE user_id = @uid AND source = 'SOLO'
                               AND title IN ('오사카 벚꽃 여행', '부산 당일치기'))
    ) x);
DELETE FROM post_comments WHERE post_id IN (
    SELECT id FROM (
        SELECT p.id FROM posts p
        WHERE p.user_id = @uid
          AND p.trip_id IN (SELECT id FROM my_trips WHERE user_id = @uid AND source = 'SOLO'
                               AND title IN ('오사카 벚꽃 여행', '부산 당일치기'))
    ) x);
DELETE FROM posts WHERE user_id = @uid
  AND trip_id IN (SELECT id FROM my_trips WHERE user_id = @uid AND source = 'SOLO'
                     AND title IN ('오사카 벚꽃 여행', '부산 당일치기'));
DELETE FROM my_trips WHERE user_id = @uid AND source = 'SOLO'
  AND title IN ('오사카 벚꽃 여행', '부산 당일치기');

INSERT INTO my_trips (user_id, source, party_id, title, destination, start_date, end_date, memo)
VALUES (@uid, 'SOLO', NULL, '오사카 벚꽃 여행', '오사카',
        DATE_SUB(CURDATE(), INTERVAL 5 DAY), DATE_SUB(CURDATE(), INTERVAL 3 DAY),
        '도톤보리, 오사카성 다녀옴');
SET @trip_osaka = LAST_INSERT_ID();

INSERT INTO my_trips (user_id, source, party_id, title, destination, start_date, end_date, memo)
VALUES (@uid, 'SOLO', NULL, '부산 당일치기', '부산',
        DATE_SUB(CURDATE(), INTERVAL 1 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY),
        '해운대 바다 보고 옴');
SET @trip_busan = LAST_INSERT_ID();

INSERT INTO posts (user_id, party_id, trip_id, title, content, region, thumbnail_url, like_count, created_at, updated_at)
VALUES
  (@uid, NULL, @trip_osaka, '오사카성 스냅', '오사카성 앞에서 찍었어요.', '오사카', 'ph1', 0, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),
  (@uid, NULL, @trip_osaka, '도톤보리 스냅', '도톤보리 야경입니다.', '오사카', 'ph2', 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
  (@uid, NULL, @trip_busan, '해운대 스냅', '해운대에서 찍은 사진.', '부산', 'ph3', 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));

-- ---------------------------------------------------------------------
-- PART 4. 완료 파티가 my_trips에 이미 동기화돼 있다면(=이 스크립트 2차 실행), 아직 스냅이
--   없는 PARTY 여행 전부에 스냅을 1장씩 채워 카운트 상태로 만든다. 1차 실행 때는 my_trips에
--   파티 행이 아직 없어서(앱을 한 번도 안 열어봄) 이 파트는 0건 처리되고 조용히 넘어간다 -
--   에러 아님, 정상 동작. NOT EXISTS 기반이라 몇 번을 더 돌려도 안전(이미 채워진 건 건너뜀).
-- ---------------------------------------------------------------------
SELECT COUNT(*) AS pending_party_trips_before
  FROM my_trips mt
 WHERE mt.user_id = @uid AND mt.source = 'PARTY'
   AND NOT EXISTS (SELECT 1 FROM posts p WHERE p.trip_id = mt.id AND p.blinded = FALSE);

INSERT INTO posts (user_id, party_id, trip_id, title, content, region, thumbnail_url, like_count, created_at, updated_at)
SELECT
    mt.user_id, NULL, mt.id,
    CONCAT(mt.destination, ' 스냅'),
    CONCAT(mt.title, '에서 찍은 스냅입니다.'),
    mt.destination,
    ELT(1 + (mt.id MOD 4), 'ph1', 'ph2', 'ph3', 'ph4'),
    0, mt.start_date, mt.start_date
FROM my_trips mt
WHERE mt.user_id = @uid AND mt.source = 'PARTY'
  AND NOT EXISTS (SELECT 1 FROM posts p WHERE p.trip_id = mt.id AND p.blinded = FALSE);

SELECT COUNT(*) AS pending_party_trips_after
  FROM my_trips mt
 WHERE mt.user_id = @uid AND mt.source = 'PARTY'
   AND NOT EXISTS (SELECT 1 FROM posts p WHERE p.trip_id = mt.id AND p.blinded = FALSE);

-- ---------------------------------------------------------------------
-- 확인
-- ---------------------------------------------------------------------
SELECT COUNT(*) AS 완료파티, COUNT(DISTINCT region) AS 지역수
  FROM parties p JOIN party_members pm ON pm.party_id = p.id
 WHERE pm.user_id = @uid AND p.status = 'completed';

SELECT region, COUNT(*) AS 횟수
  FROM parties p JOIN party_members pm ON pm.party_id = p.id
 WHERE pm.user_id = @uid AND p.status = 'completed'
 GROUP BY region ORDER BY 횟수 DESC, region;

SELECT id, source, title, destination, start_date, end_date
  FROM my_trips WHERE user_id = @uid ORDER BY source, start_date DESC;

SET SQL_SAFE_UPDATES = 1;

-- =====================================================================
-- 되돌리기 (유자차 계정을 전부 지우고 빈 상태로만 두고 싶을 때)
-- =====================================================================
-- USE tanoshimi;
-- SET SQL_SAFE_UPDATES = 0;
-- SET @uid = (SELECT id FROM users WHERE email = 'yuja@test.com');
-- DELETE FROM party_members WHERE party_id IN (SELECT id FROM (SELECT id FROM parties WHERE title LIKE '[demo]%') x);
-- DELETE FROM parties WHERE title LIKE '[demo]%';
-- DELETE FROM post_likes WHERE post_id IN (SELECT id FROM posts WHERE user_id = @uid);
-- DELETE FROM post_comments WHERE post_id IN (SELECT id FROM posts WHERE user_id = @uid);
-- DELETE FROM posts WHERE user_id = @uid;
-- DELETE FROM my_trips WHERE user_id = @uid;
-- DELETE FROM user_titles WHERE user_id = @uid;
-- UPDATE users SET manner_temp = 39.2 WHERE id = @uid;
-- SET SQL_SAFE_UPDATES = 1;
