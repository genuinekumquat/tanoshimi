-- =====================================================================
-- 마이그레이션 v18: 회원가입 본인인증 채널을 SMS -> 이메일로 전환
-- 담당: ①(인증·회원가입)
--
-- 배경
--  - 알리고 등 SMS API는 사업자등록번호 없이는 실사용이 어려워, 회원가입 본인인증을
--    이메일로 전환한다.
--  - phone_verifications 테이블/PhoneVerificationService 는 지우지 않는다. change_phone
--    (휴대폰 번호 변경) 처럼 실제 "그 번호를 갖고 있는지"를 확인해야 하는 목적에는
--    이메일로 대체할 수 없어서, 나중을 위해 그대로 남겨둔다.
--
-- 실행 전 확인
--  - 이 마이그레이션은 새 테이블만 추가한다(기존 데이터 삭제/변경 없음). 실행 순서
--    상관없이 안전하지만, EmailVerificationService 를 쓰는 코드가 먼저 배포된 뒤에
--    돌려도 상관없다(테이블이 없어도 에러는 나중에 그 API를 호출할 때만 남).
-- =====================================================================
USE tanoshimi;

CREATE TABLE IF NOT EXISTS email_verifications (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    code_hash     VARCHAR(255) NOT NULL,
    purpose       ENUM('signup','find_password','change_phone') NOT NULL,
    expires_at    DATETIME     NOT NULL,
    attempt_count INT          NOT NULL DEFAULT 0,
    verified_at   DATETIME     NULL,
    used_at       DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ev_email_purpose (email, purpose, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
