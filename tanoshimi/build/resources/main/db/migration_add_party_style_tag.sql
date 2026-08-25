-- =====================================================================
-- 마이그레이션: parties 테이블에 style_tag 컬럼 추가
-- 이미 schema.sql + data.sql 을 한 번 실행하신 분들은 전체를 다시 돌릴 필요 없이
-- 이 파일 하나만 실행하면 됩니다. (신규 설치라면 schema.sql 에 이미 반영돼 있어서 안 돌려도 무방)
-- =====================================================================
USE tanoshimi;

ALTER TABLE parties
  ADD COLUMN style_tag VARCHAR(100) NULL COMMENT '축제/먹거리/문화체험/액티비티/힐링 - 메인 태그 필터용'
  AFTER capacity;

-- 기존 더미 파티 4건에 태그 백필 (연결된 tour 의 style_tag 와 맞춤)
UPDATE parties SET style_tag = '축제'   WHERE id = 1;  -- 오사카 하츠모데
UPDATE parties SET style_tag = '액티비티' WHERE id = 2;  -- 홋카이도 스키
UPDATE parties SET style_tag = '힐링'   WHERE id = 3;  -- 교토 벚꽃
UPDATE parties SET style_tag = '액티비티' WHERE id = 4;  -- 도쿄 디즈니
