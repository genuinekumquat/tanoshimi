-- =====================================================================
-- 마이그레이션: v16 제안서 반영 (플래너 편집권/자동저장/롤백, 매너online 이력,
--              AI 크레딧 관리, 장소검색 API 캐싱, 프로필 꾸미기, 파티 완료 처리)
-- 이미 schema.sql 을 실행하신 분들은 이 파일만 실행하면 됩니다.
-- (신규 설치라면 schema.sql 에 이미 반영돼 있어서 안 돌려도 무방)
--
-- 팀 리뷰 반영 사항:
--  1) parties 에 종료일 계산용 duration_days 컬럼 추가 (기존에 departure_date만
--     있고 "며칠짜리 여행인지"가 없어서 파티 완료 자동처리 스케줄러가 계산할 수
--     없었던 문제 수정)
--  2) parties.status 신규 값은 한글이 아닌 completed 로 통일 (기존 영어 3종
--     recruiting/full/closed 와 섞이지 않도록. 화면 표시는 '완료'로 하면 됨)
--  3) trip_schedules.locked_by_user_id 는 NOT NULL 로 확정, 파티 생성 시
--     방장(owner_user_id)으로 초기화하고 편집권 회수 시에도 NULL이 아닌
--     방장 ID로 복귀시킵니다 (파티 생성 직후 방장이 스스로에게 편집권을
--     부여하는 불필요한 단계를 없애기 위함 - 파티/계획표 생성 로직에서
--     INSERT 시 반드시 locked_by_user_id = owner_user_id 로 명시 세팅해야
--     합니다. MySQL은 컬럼 DEFAULT가 다른 컬럼을 참조할 수 없어 앱 코드에서
--     처리해야 함)
-- =====================================================================
USE tanoshimi;

-- ---------------------------------------------------------------------
-- 1. parties: 종료일 계산용 duration_days 추가 + status 에 completed 추가
-- ---------------------------------------------------------------------
ALTER TABLE parties
  ADD COLUMN duration_days TINYINT NOT NULL DEFAULT 1
    COMMENT '여행 일수. departure_date + duration_days = 종료일(파티 완료 자동처리 스케줄러 판단 기준)'
    AFTER departure_date;

ALTER TABLE parties
  MODIFY COLUMN status ENUM('recruiting','full','closed','completed') NOT NULL DEFAULT 'recruiting'
    COMMENT 'completed = 여행 종료일 경과 후 스케줄러가 자동 전환(UI 표시는 완료)';

-- ---------------------------------------------------------------------
-- 2. trip_schedules: 편집권(lock) 소유자 + 마지막 저장시각
--    locked_by_user_id 는 NOT NULL - 방장(owner_user_id)으로 초기화되며,
--    편집권 회수 시에도 NULL이 아닌 방장 ID로 복귀합니다.
--    (컬럼은 일단 NULL 허용으로 추가 → 기존 행 백필 → NOT NULL로 변경,
--     이렇게 하는 이유는 이미 존재하는 trip_schedules 행이 있을 수 있어서
--     한 번에 NOT NULL로 추가하면 실패하기 때문)
-- ---------------------------------------------------------------------
ALTER TABLE trip_schedules
  ADD COLUMN locked_by_user_id BIGINT NULL
    COMMENT '[v16] 현재 편집권을 가진 파티원. 파티 생성 시 방장(owner_user_id)으로 초기화, 회수 시에도 방장 ID로 복귀(앱 코드에서 명시 세팅)'
    AFTER status,
  ADD COLUMN last_saved_at DATETIME NULL
    COMMENT '마지막 저장(자동/수동) 시각 - 화면 상단 표시용'
    AFTER locked_by_user_id;

-- 기존 trip_schedules 행이 있다면 방장 ID로 백필 (파티당 계획표 1개이므로 party_id로 조인)
UPDATE trip_schedules ts
JOIN parties p ON ts.party_id = p.id
SET ts.locked_by_user_id = p.owner_user_id
WHERE ts.locked_by_user_id IS NULL;

ALTER TABLE trip_schedules
  MODIFY COLUMN locked_by_user_id BIGINT NOT NULL
    COMMENT '[v16] 현재 편집권을 가진 파티원. 파티 생성 시 방장(owner_user_id)으로 초기화, 회수 시에도 방장 ID로 복귀(앱 코드에서 명시 세팅)';

ALTER TABLE trip_schedules
  ADD CONSTRAINT fk_ts_locked_by FOREIGN KEY (locked_by_user_id) REFERENCES users(id);

-- ---------------------------------------------------------------------
-- 3. trip_schedule_items: 고정(LOCK)/이동가능 구분
-- ---------------------------------------------------------------------
ALTER TABLE trip_schedule_items
  ADD COLUMN is_fixed BOOLEAN NOT NULL DEFAULT FALSE
    COMMENT 'true=고정(LOCK, 항공·숙박 등), false=이동가능(AI 동선최적화 대상)'
    AFTER source;

-- ---------------------------------------------------------------------
-- 4. trip_schedule_snapshots (신규) - 자동/수동 저장마다 적재, 롤백의 소스
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_schedule_snapshots (
    id            BIGINT   NOT NULL AUTO_INCREMENT,
    schedule_id   BIGINT   NOT NULL,
    snapshot_data JSON     NOT NULL COMMENT '저장 시점의 전체 trip_schedule_items 스냅샷',
    trigger_type  ENUM('auto','manual') NOT NULL COMMENT '자동저장(20분 주기) vs 수동저장 구분',
    created_by    BIGINT   NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_snapshot_schedule (schedule_id, created_at),
    CONSTRAINT fk_tss_schedule FOREIGN KEY (schedule_id) REFERENCES trip_schedules(id),
    CONSTRAINT fk_tss_user FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 5. activities: venue_type nullable화 + 장소검색 API 캐싱 컬럼
--    (제공사는 Google vs OpenStreetMap 팀 미확정 - place_provider 는 값 후보만 정의)
-- ---------------------------------------------------------------------
ALTER TABLE activities
  MODIFY COLUMN venue_type ENUM('indoor','outdoor','mixed') NULL
    COMMENT '판정 전 NULL 허용 - AI가 처음 조회될 때 판정 후 채움(기존엔 NOT NULL이었음)';

ALTER TABLE activities
  ADD COLUMN external_place_id VARCHAR(200) NULL COMMENT '장소검색 API의 장소 고유 id(중복 저장 방지)' AFTER longitude,
  ADD COLUMN place_provider ENUM('google','osm') NULL COMMENT '어느 API로 조회했는지(제공사 미확정 - 값 후보만 정의)' AFTER external_place_id,
  ADD COLUMN cached_at DATETIME NULL COMMENT 'venue_type 캐싱 갱신 시각' AFTER place_provider;

ALTER TABLE activities
  ADD UNIQUE KEY uk_activities_place (external_place_id);

-- ---------------------------------------------------------------------
-- 6. manner_temp_logs (신규) - 매너online 가산/감산 이력(감사 로그)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS manner_temp_logs (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    delta      DECIMAL(3,1) NOT NULL COMMENT '+0.5 / +0.3 / -1.0 / -0.5 등',
    reason     ENUM('party_complete','host_bonus','report_penalty','leave_penalty') NOT NULL,
    related_id BIGINT       NULL COMMENT '관련 party_id 또는 report_id(다형 참조, FK 없음)',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_mtl_user (user_id, created_at),
    CONSTRAINT fk_mtl_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 7. ai_credit_usage (신규) - 사용자별 일일 AI 크레딧 사용량
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_credit_usage (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    usage_date  DATE   NOT NULL,
    used_count  INT    NOT NULL DEFAULT 0,
    daily_limit INT    NOT NULL COMMENT '전원 동일 값 지급',
    PRIMARY KEY (id),
    UNIQUE KEY uk_acu_user_date (user_id, usage_date),
    CONSTRAINT fk_acu_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 8. reports: 게시판·파티 신고 콘텐츠 최종조치 결과
-- ---------------------------------------------------------------------
ALTER TABLE reports
  ADD COLUMN action_taken ENUM('none','hidden','deleted') NOT NULL DEFAULT 'none'
    COMMENT '게시판·파티 신고 콘텐츠 최종조치 결과'
    AFTER status,
  ADD COLUMN actioned_by BIGINT NULL COMMENT '조치한 관리자(⑤)' AFTER action_taken;

ALTER TABLE reports
  ADD CONSTRAINT fk_report_actioned_by FOREIGN KEY (actioned_by) REFERENCES users(id);

-- ---------------------------------------------------------------------
-- 9. user_profile_theme (신규) - 프로필 배경 꾸미기 설정
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_profile_theme (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    theme_key  VARCHAR(50) NOT NULL COMMENT '사전 정의된 배경/스킨 키',
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_upt_user (user_id),
    CONSTRAINT fk_upt_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 10. 기존 더미 파티 4건에 duration_days 백필 (data.sql 의 title 기준으로 추정)
--     실제 여행 일수를 몰라서 tours.duration_nights + 1(숙박일수+1)로 맞춰둡니다.
--     필요하면 팀에서 값만 수정하세요 - 스키마 자체엔 영향 없습니다.
-- ---------------------------------------------------------------------
UPDATE parties p
JOIN tours t ON p.tour_id = t.id
SET p.duration_days = t.duration_nights + 1
WHERE p.tour_id IS NOT NULL;