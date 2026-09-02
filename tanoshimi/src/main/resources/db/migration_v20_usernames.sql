-- =====================================================================
-- 마이그레이션 v20: users.username (프로필 URL "/{username}" 용 아이디)
-- 담당: 김민규(⑥ 마이페이지)
--
-- 배경
--  - 그동안 개인 프로필은 "/users/{id}"(숫자 id)로만 접근했다. "/yuja" 같은 사람이 읽기
--    쉬운 주소(vanity URL)를 붙이기 위해 users.username 을 새로 둔다.
--  - 형식/예약어 규칙은 UsernamePolicy.java 참고(영문 소문자로 시작하는 3~20자,
--    소문자/숫자/밑줄, 항상 소문자로 정규화 저장). 신규 가입자는 회원가입 화면에서
--    직접 고르지만, 이 컬럼을 NOT NULL UNIQUE 로 만들려면 이미 있는 회원들 것부터
--    채워야 한다(백필) - 이 스크립트가 그 백필이다.
--
-- 안전하게 재실행 가능 (MySQL 8.0 표준 문법만 사용 - MariaDB 전용 확장은 안 씀)
--  - 컬럼 추가: "ALTER TABLE ... ADD COLUMN IF NOT EXISTS" 는 MariaDB 전용 문법이라
--    실제 MySQL 8.0 에서는 문법 오류가 난다(ERROR 1064). 대신 information_schema.columns
--    로 존재 여부를 먼저 확인하고, 없을 때만 ALTER TABLE 을 실행하는 임시 프로시저로
--    감싼다 - demo_mypage_seed.sql 의 _seed_demo_parties 가드 프로시저와 같은 패턴
--    (DROP PROCEDURE IF EXISTS -> DELIMITER -> CREATE PROCEDURE -> CALL -> DROP PROCEDURE).
--  - 백필은 username 이 아직 없거나(NULL), 예전 버전 이 스크립트가 무조건 "base+id" 로
--    채워놓은 값처럼 보이는 사람만 대상으로 한다 - 즉 재실행해도 사람이 직접 고른 진짜
--    아이디는 절대 건드리지 않는다(demo_mypage_seed.sql 의 NOT EXISTS 관례와 같은 정신).
--  - UNIQUE 인덱스도 같은 이유로 프로시저로 감싼다 - "CREATE INDEX ... IF NOT EXISTS" 류
--    문법은 MySQL 8.0 마이너 버전마다 지원이 들쭉날쭉해서 믿을 수 없다. 대신
--    information_schema.statistics 로 존재 여부를 먼저 확인하고, 없을 때만
--    ALTER TABLE ... ADD UNIQUE INDEX 를 실행하는 프로시저로 감싼다.
--
-- 백필 규칙(생성 방식) - [v20-2 패치] 이전 버전은 무조건 "base+숫자id" 를 붙여서
-- yuja@test.com(id=3) 이 "yuja" 대신 "yuja3" 가 되는 등 불필요한 접미사가 늘 붙는
-- 문제가 있었다. 이번 버전은:
--  - base = 이메일 로컬파트(@ 앞부분)에서 영숫자만 남기고 소문자화, 최대 20자로 자름.
--    base 가 비어 있으면 'user', 숫자로 시작하면 앞에 'u' 를 붙여 형식(영문 시작)을 맞춘다.
--  - 먼저 base 그대로를 시도한다. 이미 다른 사람이 쓰고 있을 때만(기존 DB 값이든, 같은
--    배치에서 방금 앞사람에게 배정된 값이든 둘 다 확인) base2, base3, ... 순서로 숫자를
--    늘려가며 처음으로 비어 있는 값을 찾는다(최대 1000번 시도, 그래도 못 찾으면 이전
--    방식대로 base+id 로 확정해 무한루프 없이 종료 - 사실상 절대 안 일어날 상황).
--  - 사람마다 한 명씩(id 오름차순) 순서대로 배정하므로, 같은 base 를 가진 사람들끼리도
--    서로 겹치지 않는다.
--
-- [v20-3 패치] "ADD COLUMN username VARCHAR(30) ..." 에 콜레이션을 안 적어주면 MySQL 8 은
-- 서버 기본 콜레이션(utf8mb4_0900_ai_ci)을 붙인다 - 그런데 이 DB는 schema.sql 맨 위
-- "CREATE DATABASE ... COLLATE utf8mb4_unicode_ci" 때문에 users.email 을 비롯한 기존
-- 문자열 컬럼이 전부 utf8mb4_unicode_ci 다. 그 상태로 username = email 파생값(REGEXP_REPLACE/
-- SUBSTRING_INDEX 결과) 비교/JOIN 을 하면 "Illegal mix of collations" 에러가 난다(실사용
-- 중 실제로 겪음). 그래서 ADD COLUMN 에 COLLATE utf8mb4_unicode_ci 를 명시로 박아
-- email 과 같은 콜레이션으로 맞춘다(schema.sql 의 CREATE TABLE users 도 동일하게 맞춰둠 -
-- fresh install 도 처음부터 일치하도록). 이후 REGEXP_REPLACE/SUBSTRING_INDEX/LOWER 로
-- email 에서 파생한 문자열은 원본 컬럼(email, utf8mb4_unicode_ci)의 콜레이션을 그대로
-- 물려받으므로, username 컬럼과 비교할 때 더 이상 섞이지 않는다.
--
-- 실행 전제: schema.sql 의 users 테이블이 이미 있어야 한다(email, id 컬럼 사용).
-- 실행: mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/migration_v20_usernames.sql
-- =====================================================================
USE tanoshimi;

-- 1. 컬럼 추가 - username 컬럼이 없을 때만 추가한다(재실행해도 안전)
DROP PROCEDURE IF EXISTS _v20_add_username_column;
DELIMITER $$
CREATE PROCEDURE _v20_add_username_column()
BEGIN
  IF (SELECT COUNT(*) FROM information_schema.columns
       WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'username') = 0 THEN
    ALTER TABLE users
        ADD COLUMN username VARCHAR(30) COLLATE utf8mb4_unicode_ci NULL
        COMMENT '[v20] 프로필 URL(/{username}) 아이디' AFTER email;
  END IF;
END$$
DELIMITER ;
CALL _v20_add_username_column();
DROP PROCEDURE _v20_add_username_column;

-- 2. 백필 - base 를 먼저 그대로 시도하고, 충돌할 때만 숫자를 늘려간다(idempotent).
--    대상: username 이 NULL 이거나, "base+id" 형태(예전 버전이 무조건 붙였던 패턴)라서
--    아직 사람이 직접 고른 값이 아니라고 볼 수 있는 행.
DROP PROCEDURE IF EXISTS _v20_backfill_usernames;
DELIMITER $$
CREATE PROCEDURE _v20_backfill_usernames()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE cur_id BIGINT;
  -- [v20-4 패치] 로컬 변수는 COLLATE 를 안 적으면 세션(연결) 기본 콜레이션(이 서버에선
  -- utf8mb4_0900_ai_ci)을 받는다 - users.username/email 은 DB 기본값인
  -- utf8mb4_unicode_ci 라서, 이 두 변수를 username/email 파생값과 비교하면 또
  -- "Illegal mix of collations" 가 난다(실사용 중 실제로 겪음). 그래서 명시로 맞춘다.
  DECLARE cur_base VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  DECLARE candidate VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  DECLARE suffix INT;
  DECLARE tries INT;
  DECLARE dup_count INT;

  DECLARE cur CURSOR FOR
      SELECT id,
             CASE
                 WHEN LOWER(REGEXP_REPLACE(SUBSTRING_INDEX(email, '@', 1), '[^A-Za-z0-9]', '')) = ''
                     THEN 'user'
                 WHEN LOWER(REGEXP_REPLACE(SUBSTRING_INDEX(email, '@', 1), '[^A-Za-z0-9]', '')) REGEXP '^[0-9]'
                     THEN CONCAT('u', LOWER(REGEXP_REPLACE(SUBSTRING_INDEX(email, '@', 1), '[^A-Za-z0-9]', '')))
                 ELSE LOWER(REGEXP_REPLACE(SUBSTRING_INDEX(email, '@', 1), '[^A-Za-z0-9]', ''))
             END AS base_raw
        FROM users
       WHERE username IS NULL
          OR username = CONCAT(  -- (email 파생값이라 이미 같은 콜레이션 - 그래도 안전하게 아래서 한 번 더 고정)
                 LEFT(
                     CASE
                         WHEN LOWER(REGEXP_REPLACE(SUBSTRING_INDEX(email, '@', 1), '[^A-Za-z0-9]', '')) = ''
                             THEN 'user'
                         WHEN LOWER(REGEXP_REPLACE(SUBSTRING_INDEX(email, '@', 1), '[^A-Za-z0-9]', '')) REGEXP '^[0-9]'
                             THEN CONCAT('u', LOWER(REGEXP_REPLACE(SUBSTRING_INDEX(email, '@', 1), '[^A-Za-z0-9]', '')))
                         ELSE LOWER(REGEXP_REPLACE(SUBSTRING_INDEX(email, '@', 1), '[^A-Za-z0-9]', ''))
                     END, 20),
                 id) COLLATE utf8mb4_unicode_ci
       ORDER BY id ASC;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO cur_id, cur_base;
    IF done THEN
      LEAVE read_loop;
    END IF;

    SET candidate = LEFT(cur_base, 20);
    SET suffix = 1;
    SET tries = 0;

    find_free: LOOP
      -- belt-and-suspenders: 위 DECLARE 로 이미 콜레이션이 맞지만, 만에 하나 어느 쪽이든
      -- 드리프트하는 경우에 대비해 비교 자체에도 명시로 고정해둔다.
      SELECT COUNT(*) INTO dup_count FROM users WHERE username = candidate COLLATE utf8mb4_unicode_ci AND id <> cur_id;
      SET tries = tries + 1;
      IF dup_count = 0 THEN
        LEAVE find_free;
      END IF;
      IF tries >= 1000 THEN
        -- 사실상 일어날 수 없는 극단적 상황 - 예전 방식(base+id)으로 안전하게 확정하고 종료
        SET candidate = CONCAT(LEFT(cur_base, 20), cur_id);
        LEAVE find_free;
      END IF;
      SET suffix = suffix + 1;
      SET candidate = CONCAT(LEFT(cur_base, 20), suffix);
    END LOOP find_free;

    UPDATE users SET username = candidate WHERE id = cur_id;
  END LOOP read_loop;
  CLOSE cur;
END$$
DELIMITER ;
CALL _v20_backfill_usernames();
DROP PROCEDURE _v20_backfill_usernames;

-- 3. NOT NULL 강제 - 이미 NOT NULL 이면 재실행해도 그대로 통과(자연히 idempotent)
ALTER TABLE users MODIFY COLUMN username VARCHAR(30) NOT NULL;

-- 4. UNIQUE 인덱스 - 이미 있으면 건너뛴다(조건부 생성, information_schema.statistics 로 확인)
DROP PROCEDURE IF EXISTS _v20_add_username_unique_index;
DELIMITER $$
CREATE PROCEDURE _v20_add_username_unique_index()
BEGIN
  IF (SELECT COUNT(*) FROM information_schema.statistics
       WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = 'uk_users_username') = 0 THEN
    ALTER TABLE users ADD UNIQUE INDEX uk_users_username (username);
  END IF;
END$$
DELIMITER ;
CALL _v20_add_username_unique_index();
DROP PROCEDURE _v20_add_username_unique_index;

-- 확인
SELECT COUNT(*) AS still_missing_username FROM users WHERE username IS NULL OR username = '';
SELECT COUNT(*) AS total_users, COUNT(DISTINCT username) AS distinct_usernames FROM users;
