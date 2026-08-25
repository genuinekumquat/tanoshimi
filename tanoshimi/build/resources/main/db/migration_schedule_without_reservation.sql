-- =====================================================================
-- 마이그레이션: 계획표를 예약 없이도 만들 수 있게 변경
-- 파티를 만들면 즉시 계획표가 생기고(초안), 패키지를 실제로 예약/결제하면
-- 그 시점에 reservation_id 가 채워지면서 항공/체크인 블록이 고정 추가된다.
-- =====================================================================
USE tanoshimi;

ALTER TABLE trip_schedules
  MODIFY COLUMN reservation_id BIGINT NULL;

ALTER TABLE trip_schedules
  ADD COLUMN party_id BIGINT NULL AFTER id;

ALTER TABLE trip_schedules
  ADD CONSTRAINT fk_ts_party FOREIGN KEY (party_id) REFERENCES parties(id);

ALTER TABLE trip_schedules
  ADD UNIQUE KEY uk_schedule_party (party_id);

-- 기존에 이미 예약을 통해 만들어진 계획표는 그 예약이 속한 파티로 역채움
UPDATE trip_schedules ts
JOIN reservations r ON ts.reservation_id = r.id
SET ts.party_id = r.party_id
WHERE r.party_id IS NOT NULL;
