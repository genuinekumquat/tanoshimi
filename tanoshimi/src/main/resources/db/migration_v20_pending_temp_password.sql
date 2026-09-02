-- =====================================================================
-- 마이그레이션 v20: 임시 비밀번호를 즉시 password에 반영하지 않도록 변경
-- 담당: ①(인증·회원가입)
--
-- 배경
--  - v19에서는 비밀번호 재발급 요청 즉시 password 컬럼을 임시 비밀번호로 덮어썼는데,
--    이러면 이메일 소유 확인 없이 "이메일 주소만 아는" 누구나 남의 현재 비밀번호를
--    즉시 무효화시킬 수 있는 계정 잠금형 악용이 가능했다.
--  - 이제 재발급 요청은 임시 비밀번호를 pending_temp_password_hash에만 대기시키고,
--    원래 비밀번호는 그대로 로그인에 쓸 수 있게 둔다. 실제로 그 임시 비밀번호로
--    로그인에 성공하는 순간에만 password로 승격되고 must_change_password가 켜진다
--    (TempPasswordAuthenticationProvider).
--
-- 실행 전 확인
--  - 컬럼 추가만 있고 기존 데이터 삭제/변경은 없다. NULL 기본값이라 기존 회원은 영향 없다.
-- =====================================================================
USE tanoshimi;

ALTER TABLE users
    ADD COLUMN pending_temp_password_hash VARCHAR(255) NULL AFTER must_change_password,
    ADD COLUMN pending_temp_password_expires_at DATETIME NULL AFTER pending_temp_password_hash;
