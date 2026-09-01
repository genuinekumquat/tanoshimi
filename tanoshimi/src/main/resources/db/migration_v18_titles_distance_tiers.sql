-- =====================================================================
-- 마이그레이션 v18: 여행 거리 칭호 상위 2종 추가 (지구 반바퀴/한바퀴 클럽)
-- 담당: 김민규(⑥ 마이페이지)
--
-- 배경
--  - v17에서 심은 여행 거리 4종(D400~D10000, 최대 1만km)은 목업 원본 그대로였는데,
--    지구 둘레 기준 상위 등급 2종(20,000km/40,000km)이 화면 기획에 빠져 있던 게
--    뒤늦게 확인되어 추가한다.
--  - 이 2종도 v17의 다른 여행 거리 칭호와 마찬가지로 아직 판정 로직이 없다
--    (TitleService 는 users.home_lat/lng 가 생겨야 누적 거리를 계산할 수 있음 -
--    migration_v17_titles_catalog.sql 하단 참고). 행만 추가해두고 화면엔 잠금
--    상태로 보인다. 나머지 4종과 함께 데이터가 생기면 판정을 붙이면 된다.
--
-- 실행 전제: migration_v17_titles_catalog.sql 이 이미 적용되어 있어야 한다
-- (titles.category 컬럼과 기존 38종이 먼저 있어야 함).
-- INSERT IGNORE 라 이미 들어가 있으면(예: data.sql로 새로 설치한 DB) 조용히 건너뛴다.
-- =====================================================================
USE tanoshimi;

INSERT IGNORE INTO titles (code, name, category, condition_desc, icon_key) VALUES
  ('D20000', '지구 반바퀴 클럽', '여행 거리', '누적 20,000km 이상', '🛰️'),
  ('D40000', '지구 한바퀴 클럽', '여행 거리', '누적 40,000km 이상', '🚀');

-- 확인 - 여행 거리 칭호가 6종, 전체가 40종이어야 한다
SELECT COUNT(*) AS distance_title_count FROM titles WHERE category = '여행 거리';
SELECT COUNT(*) AS total_title_count FROM titles;
