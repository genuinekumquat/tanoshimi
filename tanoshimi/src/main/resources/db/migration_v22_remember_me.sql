-- =====================================================================
-- 마이그레이션 v22: 자동 로그인("로그인 상태 유지") 기능 추가
-- 담당: ①(인증·회원가입)
-- (파일명 정정: 결제·예약 제거 마이그레이션과 v21 번호가 겹쳐 v22 로 옮김. schema.sql 에도
--  persistent_logins 정의가 포함됐으므로, 새 DB 는 이 파일을 실행하지 않아도 된다.)
--
-- 배경
--  - 로그인 화면에서 체크박스를 선택하면, 세션이 만료돼도(브라우저를 새로 켜도) 다시 로그인
--    절차 없이 자동으로 인증된다. Spring Security의 PersistentTokenBasedRememberMeServices를
--    사용 - 로그인마다 series/token 쌍을 이 테이블에 저장하고, 자동 로그인이 성공할 때마다
--    token을 새로 갱신(rotate)한다. 쿠키가 탈취돼도 원래 사용자가 다시 로그인/자동로그인하면
--    토큰이 바뀌어 있어 탈취범의 쿠키는 즉시 무효화된다(해시 기반 TokenBasedRememberMeServices와
--    달리 서버에서 개별 세션을 무효화할 수 있는 이유).
--  - 로그아웃하면 이 테이블에서 해당 계정의 행이 삭제되고 쿠키도 함께 만료된다
--    (PersistentTokenBasedRememberMeServices가 LogoutHandler를 겸함 - SecurityConfig 참고).
--  - 컬럼 이름/타입은 Spring Security의 JdbcTokenRepositoryImpl 기본 스키마를 그대로 따른다
--    (username/series/token/last_used) - 다르게 만들면 라이브러리 내장 쿼리가 깨진다.
--    username은 users.email(VARCHAR(255))을 그대로 저장하므로 같은 길이로 맞춘다.
--
-- 실행 전 확인
--  - 새 테이블 생성만 있고 기존 데이터 변경은 없다. CREATE TABLE IF NOT EXISTS로 재실행해도 안전.
-- =====================================================================
USE tanoshimi;

CREATE TABLE IF NOT EXISTS persistent_logins (
    username    VARCHAR(255) NOT NULL COMMENT 'users.email과 동일 - 로그인 ID',
    series      VARCHAR(64)  NOT NULL COMMENT '기기(브라우저)별 로그인 세션 식별자 - 쿠키에 저장되는 값의 절반',
    token       VARCHAR(64)  NOT NULL COMMENT '자동 로그인마다 갱신되는 1회용 토큰 - 쿠키의 나머지 절반',
    last_used   TIMESTAMP    NOT NULL COMMENT '이 토큰이 마지막으로 사용된 시각',
    PRIMARY KEY (series)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='자동 로그인(remember-me) 세션 저장소 - Spring Security JdbcTokenRepositoryImpl 표준 스키마';

-- 확인
SHOW TABLES LIKE 'persistent_logins';
