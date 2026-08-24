-- =====================================================================
-- 마이그레이션: 기존 데모 유저 전원에게 기본 칭호(새내기 탐험가) 소급 부여
-- 이미 schema.sql + data.sql 을 실행하신 분들은 이 파일만 실행하면 됩니다.
-- (앞으로 새로 가입하는 회원은 가입 즉시 자동으로 부여되니 이 마이그레이션은 1회성입니다)
-- =====================================================================
USE tanoshimi;

INSERT INTO user_titles (user_id, title_id, earned_at)
SELECT u.id, t.id, NOW() FROM users u CROSS JOIN titles t
WHERE t.code = 'NEWBIE'
  AND NOT EXISTS (SELECT 1 FROM user_titles ut WHERE ut.user_id = u.id AND ut.title_id = t.id);
