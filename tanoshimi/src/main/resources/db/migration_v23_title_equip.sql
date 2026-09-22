-- =====================================================================
-- 마이그레이션: 칭호 "장착"(대표 칭호 지정) 기능 - user_titles.equipped 컬럼 추가
-- 관련: TNSM-20(지도 정복 히트맵 + 칭호 시스템)
--
-- 배경
--  - mypage-titles-manage.js 의 대표 칭호 수정 모달은 화면 상태만 바꾸고 저장 API가
--    없었다(TitleService.latestTitle 주석, migration_v16_mypage_titles.sql 참고 -
--    스키마 변경 조율이 ⑤ 담당이라 협의 후 별도 마이그레이션으로 추가하기로 했었다).
--  - 이 마이그레이션으로 그 컬럼을 추가한다. 기본값은 FALSE - 기존 사용자는 전부
--    미장착 상태로 시작하고, TitleService.latestTitle() 이 "장착한 칭호가 없으면
--    가장 최근에 딴 칭호를 대표로 보여준다"는 기존 동작을 그대로 유지한다.
--  - 한 사용자가 동시에 두 개를 장착할 수 없게 UNIQUE 로 막는 대신 애플리케이션
--    레벨(TitleService.equipTitle)에서 기존 장착을 해제하고 새로 장착한다 - 부분
--    유니크 인덱스(WHERE equipped) 는 MySQL 이 지원하지 않는다.
-- =====================================================================
USE tanoshimi;

ALTER TABLE user_titles
    ADD COLUMN equipped BOOLEAN NOT NULL DEFAULT FALSE COMMENT '대표 칭호로 장착했는지' AFTER title_id;

CREATE INDEX idx_ut_user_equipped ON user_titles (user_id, equipped);
