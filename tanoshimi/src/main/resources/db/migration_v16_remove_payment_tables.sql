-- =====================================================================
-- 마이그레이션: 결제·예약 기능 완전 제거 (v16 - 결제 기능 삭제 확정 반영)
--
-- ⚠️ 실행 전 확인하세요 ⚠️
-- 이 파일은 migration_v16_planner_manner_ai.sql 과 분리된 별도 파일입니다.
-- 아래 Java 코드를 먼저 정리(삭제/수정)한 뒤에 실행하세요. 코드 정리 전에
-- 이 마이그레이션을 먼저 돌리면, 아직 해당 코드를 안 건드린 팀원의 빌드가
-- 깨집니다(엔티티가 참조하는 테이블이 없어져서 애플리케이션 기동 시점에
-- Hibernate 에러가 납니다).
--
--   - PackageController: POST /api/packages/{id}/reserve, POST /api/reservations/{reservationId}/pay
--   - ReservationService: reserve(), pay(), previewWeather() 등 예약/결제 관련 메서드
--   - ReservationEntity / ReservationPaymentEntity / TripSchedulePaymentEntity (또는 동일 테이블에
--     매핑된 엔티티) - 클래스 자체를 삭제하거나, 다른 곳에서 참조 안 하는지 확인
--   - trip_schedules 쪽 reservation_id 를 쓰는 로직(있다면) 제거
--
-- 위 정리가 끝난 뒤 이 파일을 실행하면 됩니다. 실행 후에는 schema.sql 에서도
-- 이 세 테이블 정의를 제거해서, 앞으로 새로 클론받는 사람은 애초에 이 테이블
-- 없이 시작하도록 팀장님(또는 담당자)이 schema.sql 을 업데이트해주세요.
-- =====================================================================
USE tanoshimi;

-- 1. trip_schedules.reservation_id: FK/UNIQUE KEY 먼저 제거 후 컬럼 삭제
ALTER TABLE trip_schedules DROP FOREIGN KEY fk_ts_reservation;
ALTER TABLE trip_schedules DROP KEY uk_schedule_reservation;
ALTER TABLE trip_schedules DROP COLUMN reservation_id;

-- 2. 결제 관련 테이블 삭제 (자식 → 부모 순서)
DROP TABLE IF EXISTS trip_schedule_payments;
DROP TABLE IF EXISTS reservation_payments;
DROP TABLE IF EXISTS reservations;
