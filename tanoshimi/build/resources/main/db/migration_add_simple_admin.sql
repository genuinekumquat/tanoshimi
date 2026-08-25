-- =====================================================================
-- 마이그레이션: 간단한 관리자 데모 계정 추가 (아이디 admin / 비밀번호 admin)
-- 이미 schema.sql + data.sql 을 실행하신 분들은 이 파일만 실행하면 됩니다.
-- 로그인 화면에서 이메일 칸에 "admin", 비밀번호 칸에 "admin" 을 입력하면 됩니다.
-- =====================================================================
USE tanoshimi;

INSERT INTO users
(email, password, name, phone, phone_verified, gender, birth_date, nationality,
 role, status, manner_temp, points_krw, points_jpy, intro)
VALUES
('admin', '$2a$10$Dd8y1AZKKlfZa1iTiY6hvu0qOUFpEXnF2mY5m/SaxsiC10v5HRV3C',
 '관리자', '01099999999', TRUE, 'male', '1990-01-01', 'KR',
 'admin', 'active', 36.5, 0, 0, NULL)
ON DUPLICATE KEY UPDATE password = VALUES(password), role = 'admin', status = 'active';
