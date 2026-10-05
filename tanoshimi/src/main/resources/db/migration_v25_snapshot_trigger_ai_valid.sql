-- =====================================================================
-- 마이그레이션 v25: trip_schedule_snapshots.trigger_type 에 'ai_valid' 추가
--
-- 배경
--  - 계획표 "일정 AI 검증 브리핑"(POST /api/planner/{id}/ai-validate)은 검증 전에 현재 일정을
--    SnapshotTrigger.ai_valid 로 임시저장한다. 그런데 schema.sql / v16 의 ENUM 에는
--    'auto','manual' 두 값뿐이라, schema.sql 로 새로 만든 DB 에서는 이 저장이
--    "Data truncated for column 'trigger_type'" 로 실패한다.
--  - Hibernate(ddl-auto: update)가 테이블을 처음 만든 DB 는 이미 'ai_valid' 가 들어 있어
--    로컬에선 드러나지 않았다. 기존 컬럼 타입은 ddl-auto 가 고치지 않는다.
--
-- 실행 전 확인
--  - 값 추가만 하므로 기존 데이터는 그대로다. 여러 번 실행해도 안전하다.
--  - 새로 만드는 DB 는 갱신된 schema.sql 에 반영돼 있어 실행하지 않아도 된다.
-- =====================================================================
USE tanoshimi;

ALTER TABLE trip_schedule_snapshots
    MODIFY trigger_type ENUM('auto','manual','ai_valid') NOT NULL
        COMMENT '자동저장(20분 주기) / 수동저장 / AI 검증 직전 임시저장(ai_valid) 구분';
