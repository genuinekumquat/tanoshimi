-- =====================================================================
-- 마이페이지 여행 지도(히트맵) 확인용 데모 데이터
-- 담당: 김민규(⑥ 마이페이지) / 로컬 확인용, 커밋 필수 아님
--
-- 왜 필요한가
--  - data.sql 의 시드 파티 4개는 전부 status='recruiting' 이다.
--  - 히트맵은 "완료된 파티"만 집계하므로(TravelHeatmapService), 시드 그대로 띄우면
--    지도가 전 지역 회색으로 나오고 "0번의 여행"으로 표시된다. 지도가 고장난 게 아니다.
--  - 이 스크립트는 시드 파티를 완료 처리하고, 한국 지역 파티 2개를 추가해서
--    일본 지도(간사이/간토/홋카이도)와 한국 지도(서울/부산) 양쪽에 색이 들어오게 한다.
--
-- 실행 후 유자차 계정으로 로그인해서 확인:  yuja@test.com / Test1234!
--
-- ※ user_id 를 3으로 박지 않고 이메일로 찾는다. INSERT 가 중간에 실패하면
--   AUTO_INCREMENT 가 밀려 번호가 달라질 수 있어서(InnoDB 는 실패한 문장이 쓴
--   번호를 되돌려주지 않는다), 유니크 제약이 걸린 email 로 찾는 편이 안전하다.
--
-- 여러 번 실행해도 결과가 같다(중복 파티가 생기지 않는다). 시드 상태로 되돌리려면
-- 파일 맨 아래 "되돌리기" 블록의 주석을 풀어서 실행하면 된다.
-- =====================================================================
USE tanoshimi;

-- MySQL Workbench 는 safe update mode 가 기본 ON 이라, 인덱스가 없는 컬럼(region)만으로
-- UPDATE/DELETE 하면 Error 1175 로 막는다. 이 세션에서만 잠시 끈다(맨 아래에서 되돌림).
SET @old_safe_updates = @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;

SET @uid = (SELECT id FROM users WHERE email = 'yuja@test.com');
SET @tid = (SELECT MIN(id) FROM tours);

-- 1. 시드 파티 4개(오사카/홋카이도/교토/도쿄)를 완료 처리
UPDATE parties
   SET status = 'completed', departure_date = '2026-05-01'
 WHERE region IN ('오사카', '홋카이도', '교토', '도쿄');

-- 2. 유자차를 참가자로 추가 (홋카이도는 이미 본인이 파티장)
INSERT IGNORE INTO party_members (party_id, user_id, role)
SELECT id, @uid, 'member' FROM parties WHERE region IN ('오사카', '교토', '도쿄');

-- 3. 한국 지도 확인용 완료 파티 2개 추가 (이미 있으면 건너뜀)
INSERT INTO parties
  (owner_user_id, tour_id, title, region, departure_date, duration_days, capacity, status)
SELECT * FROM (
  SELECT @uid AS a, @tid AS b, '서울 한강 나들이' AS c, '서울' AS d,
         '2026-06-10' AS e, 3 AS f, 4 AS g, 'completed' AS h
  UNION ALL
  SELECT @uid, @tid, '부산 해운대 여행', '부산', '2026-07-05', 2, 4, 'completed'
) AS src
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT region FROM parties) AS p WHERE p.region = src.d
);

-- 4. 새로 만든 한국 파티에 유자차를 파티장으로 등록
INSERT IGNORE INTO party_members (party_id, user_id, role)
SELECT id, @uid, 'owner' FROM parties WHERE region IN ('서울', '부산');

-- 5. 결과 확인 - 지역 6개가 전부 completed 로 나오면 준비 끝
SELECT p.id, p.region, p.status, COUNT(pm.id) AS members
  FROM parties p
  LEFT JOIN party_members pm ON pm.party_id = p.id
 GROUP BY p.id, p.region, p.status
 ORDER BY p.id;

-- safe update mode 원래대로 복구
SET SQL_SAFE_UPDATES = @old_safe_updates;


-- =====================================================================
-- 되돌리기 (시드 상태로 복구하고 싶을 때만 실행)
-- =====================================================================
-- USE tanoshimi;
-- SET SQL_SAFE_UPDATES = 0;
-- DELETE FROM party_members WHERE party_id IN (SELECT id FROM (SELECT id FROM parties WHERE region IN ('서울','부산')) x);
-- DELETE FROM parties WHERE region IN ('서울','부산');
-- UPDATE parties SET status='recruiting' WHERE region IN ('오사카','홋카이도','교토','도쿄');
-- DELETE FROM party_members
--  WHERE role='member'
--    AND user_id = (SELECT id FROM users WHERE email='yuja@test.com');
