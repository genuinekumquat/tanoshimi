-- =====================================================================
-- 마이그레이션: 결제·예약 기능 완전 제거 (v21)
--
-- 이 PR(feature/remove-reservation-payment)에서 관련 Java 코드
-- (ReservationService / ReservationEntity / ReservationPaymentEntity /
--  TripSchedulePaymentEntity / TripReminderScheduler / TripScheduleEntity.reservation 등)를
-- 모두 제거했다. schema.sql 에서도 reservations / reservation_payments /
-- trip_schedule_payments 테이블과 trip_schedules.reservation_id 컬럼을 삭제했다.
--
-- 새로 클론받는 사람은 갱신된 schema.sql 로 시작하므로 이 파일이 필요 없다.
-- 기존 로컬/개발 DB 에만 아래를 한 번 실행해서 잔여 테이블·컬럼을 정리하면 된다.
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
