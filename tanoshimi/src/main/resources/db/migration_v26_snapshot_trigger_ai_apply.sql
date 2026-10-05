-- =====================================================================
-- 마이그레이션 v26: trip_schedule_snapshots.trigger_type 에 'ai_apply' 추가
--
-- 배경
--  - 계획표 "일정 AI 검증 브리핑"이 AI 추천 일정을 실제로 반영한 직후, 그 결과를
--    SnapshotTrigger.ai_apply("AI 추천 반영")로 자동 저장한다. ENUM 에 값이 없으면
--    "Data truncated for column 'trigger_type'" 로 저장이 실패한다.
--
-- 실행 전 확인
--  - 값 추가만 하므로 기존 데이터는 그대로다. 여러 번 실행해도 안전하다.
--  - v25(ai_valid)를 안 돌린 DB 도 이것 하나로 둘 다 들어간다.
--  - 새로 만드는 DB 는 갱신된 schema.sql 에 반영돼 있어 실행하지 않아도 된다.
-- =====================================================================
USE tanoshimi;

ALTER TABLE trip_schedule_snapshots
    MODIFY trigger_type ENUM('auto','manual','ai_valid','ai_apply') NOT NULL
        COMMENT '자동저장(20분 주기) / 수동저장 / AI 검증 직전 임시저장(ai_valid) / AI 추천 반영 직후 저장(ai_apply) 구분';
