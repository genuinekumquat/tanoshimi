-- =====================================================================
-- 마이그레이션 v19: 비밀번호 재발급(임시 비밀번호) 기능 추가
-- 담당: ①(인증·회원가입)
--
-- 배경
--  - 워크플로우: 이메일 입력 -> 임시 비밀번호를 이메일로 발급 -> 그 비밀번호로 로그인하면
--    강제로 비밀번호 변경 모달을 띄운다.
--  - users.must_change_password 가 true인 동안은 로그인 후 어느 페이지를 가든 헤더
--    프래그먼트가 강제 변경 모달을 띄운다(fragments/layout.html).
--
-- 실행 전 확인
--  - 컬럼 추가만 있고 기존 데이터 삭제/변경은 없다. DEFAULT FALSE라 기존 회원은 영향 없다.
-- =====================================================================
USE tanoshimi;

ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE AFTER phone_verified;
