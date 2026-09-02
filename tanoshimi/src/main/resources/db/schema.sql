-- =====================================================================
-- 타노시미 투어 — 통합 스키마 (2026-08-22 최종 반영본)
-- 이전 산출물 3개를 병합: ① TourFest 인증모듈 초안 ② DB설계_재검토 MD
-- ③ 오늘 세션 신규 요구사항(파티 제한·파티전용페이지·채팅·팔로우·알림·투표)
--
-- 실행: mysql -u root -p < schema.sql
-- =====================================================================
CREATE DATABASE IF NOT EXISTS tanoshimi
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE tanoshimi;

-- ---------------------------------------------------------------------
-- 1. users (회원)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    email           VARCHAR(255) NOT NULL COMMENT '로그인 ID로 사용',
    password        VARCHAR(255) NOT NULL COMMENT 'BCrypt 해시. 소셜 전용 계정은 랜덤 해시',
    name            VARCHAR(50)  NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    phone_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE COMMENT '임시 비밀번호로 로그인해서 강제 변경이 걸린 상태',
    pending_temp_password_hash VARCHAR(255) NULL COMMENT '재발급 요청으로 대기 중인 임시 비밀번호 - 로그인 성공 시에만 password로 승격',
    pending_temp_password_expires_at DATETIME NULL,
    gender          ENUM('male','female') NOT NULL,
    birth_date      DATE         NOT NULL COMMENT '성인 인증 + 파티 연령제한 매칭용',
    nationality     ENUM('KR','JP') NOT NULL COMMENT '한국인/일본인 파티 제한 필터용',
    preferred_lang  ENUM('ko','ja') NOT NULL DEFAULT 'ko' COMMENT '재방문 시 기본 언어',
    manner_temp     DECIMAL(4,1) NOT NULL DEFAULT 36.5,
    points_krw      INT          NOT NULL DEFAULT 0,
    points_jpy      INT          NOT NULL DEFAULT 0,
    role            ENUM('user','admin') NOT NULL DEFAULT 'user',
    status          ENUM('active','suspended') NOT NULL DEFAULT 'active',
    profile_image_url VARCHAR(500) NULL,
    intro           VARCHAR(300) NULL COMMENT '자기소개',
    social_provider VARCHAR(20)  NULL COMMENT 'google / naver / line (로컬 가입은 NULL)',
    social_id       VARCHAR(255) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_phone (phone),
    UNIQUE KEY uk_users_social (social_provider, social_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 2. phone_verifications (휴대폰 인증 - 알리고)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS phone_verifications (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    phone         VARCHAR(20)  NOT NULL,
    code_hash     VARCHAR(255) NOT NULL,
    purpose       ENUM('signup','find_password','change_phone') NOT NULL,
    expires_at    DATETIME     NOT NULL,
    attempt_count INT          NOT NULL DEFAULT 0,
    verified_at   DATETIME     NULL,
    used_at       DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_pv_phone_purpose (phone, purpose, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 회원가입 본인인증은 이제 email_verifications 로 전환(알리고 사업자등록번호 이슈).
-- 이 테이블은 change_phone 등 phone 자체를 검증해야 하는 목적으로 남겨둠(현재 미사용).

-- ---------------------------------------------------------------------
-- 2-1. email_verifications (이메일 본인인증 - 회원가입용)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS email_verifications (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    code_hash     VARCHAR(255) NOT NULL,
    purpose       ENUM('signup','find_password','change_phone') NOT NULL,
    expires_at    DATETIME     NOT NULL,
    attempt_count INT          NOT NULL DEFAULT 0,
    verified_at   DATETIME     NULL,
    used_at       DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ev_email_purpose (email, purpose, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 3. titles / user_titles (칭호 시스템)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS titles (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    code           VARCHAR(50)  NOT NULL,
    name           VARCHAR(50)  NOT NULL,
    condition_desc VARCHAR(200) NULL,
    icon_key       VARCHAR(50)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_title_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_titles (
    id        BIGINT   NOT NULL AUTO_INCREMENT,
    user_id   BIGINT   NOT NULL,
    title_id  BIGINT   NOT NULL,
    earned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_title (user_id, title_id),
    CONSTRAINT fk_ut_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_ut_title FOREIGN KEY (title_id) REFERENCES titles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 4. tours (항공+숙박+교통 패키지 - 고정가 더미데이터)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tours (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    title               VARCHAR(200)  NOT NULL,
    region              VARCHAR(50)   NOT NULL COMMENT '일본 지역. 지도 매핑 키와 동일 문자열',
    category            VARCHAR(50)   NULL,
    description         TEXT          NULL,
    price_krw           INT           NOT NULL,
    price_jpy           INT           NOT NULL,
    duration_nights     TINYINT       NOT NULL,
    dep_time            TIME          NULL COMMENT '출발 항공편 대략 시간 (계산 기준점, 실시간 데이터 아님)',
    arr_time            TIME          NULL,
    checkin_time        TIME          NOT NULL DEFAULT '15:00:00',
    checkout_time       TIME          NOT NULL DEFAULT '11:00:00',
    venue_type          ENUM('indoor','outdoor','mixed') NOT NULL DEFAULT 'mixed',
    style_tag           VARCHAR(100)  NULL COMMENT '축제,먹거리,힐링 등 콤마구분',
    companion_recommend VARCHAR(50)   NULL COMMENT '가족/친구/연인',
    min_participants    TINYINT       NOT NULL DEFAULT 1,
    max_participants    TINYINT      NOT NULL DEFAULT 8,
    includes_summary    TEXT          NULL,
    thumbnail_url       VARCHAR(500) NULL,
    location_address    VARCHAR(300) NULL COMMENT '집결지 주소 (구글맵 표시 겸용)',
    latitude            DECIMAL(10,7) NULL COMMENT '지도 + 날씨 API 조회용 좌표',
    longitude           DECIMAL(10,7) NULL,
    external_flight_url VARCHAR(500) NULL COMMENT '항공편 개별 확인용 외부 링크(스카이스캐너 등)',
    external_hotel_url  VARCHAR(500) NULL COMMENT '호텔 개별 확인용 외부 링크(아고다 등)',
    status              ENUM('active','hidden') NOT NULL DEFAULT 'active',
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_tours_price CHECK (price_krw > 0 AND price_jpy > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 5. activities (계획표에 끌어다 넣는 개별 액티비티 - 사이트 제공, 유료)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS activities (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    title          VARCHAR(200)  NOT NULL,
    region         VARCHAR(50)   NOT NULL COMMENT 'tours.region 과 동일 값 사용',
    venue_type     ENUM('indoor','outdoor','mixed') NULL COMMENT '[v16] 판정 전 NULL 허용 - AI가 처음 조회될 때 판정 후 채움(기존엔 NOT NULL이었음)',
    style_tag      VARCHAR(100)  NULL COMMENT '축제/먹거리/명소/이동/휴식 - AI 추천 필터 겸 색상범례',
    duration_min   INT           NOT NULL DEFAULT 60 COMMENT '기본 소요시간(분) - 드래그 시 초기 칸 크기',
    price_krw      INT           NOT NULL DEFAULT 0,
    price_jpy      INT           NOT NULL DEFAULT 0,
    description    TEXT          NULL,
    thumbnail_url  VARCHAR(500)  NULL,
    latitude       DECIMAL(10,7) NULL COMMENT '날씨 API 조회용',
    longitude      DECIMAL(10,7) NULL,
    external_place_id VARCHAR(200) NULL COMMENT '[v16] 장소검색 API의 장소 고유 id(중복 저장 방지)',
    place_provider ENUM('google','osm') NULL COMMENT '[v16] 어느 API로 조회했는지(제공사 미확정 - 값 후보만 정의)',
    cached_at      DATETIME      NULL COMMENT '[v16] venue_type 캐싱 갱신 시각',
    status         ENUM('active','hidden') NOT NULL DEFAULT 'active',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_activities_place (external_place_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 6. parties (번개모임)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS parties (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    owner_user_id            BIGINT       NOT NULL COMMENT '파티장',
    tour_id                  BIGINT       NULL COMMENT '아직 패키지를 안 정했으면 NULL',
    title                    VARCHAR(200) NOT NULL,
    description              TEXT         NULL,
    region                   VARCHAR(50)  NOT NULL,
    departure_date           DATE         NOT NULL,
    duration_days            TINYINT      NOT NULL DEFAULT 1 COMMENT '여행 일수. departure_date + duration_days = 종료일(파티 완료 자동처리 스케줄러 판단 기준)',
    budget_krw               INT          NULL COMMENT '1인 예산(원) - 목록 표시용',
    capacity                 TINYINT      NOT NULL,
    style_tag                VARCHAR(100) NULL COMMENT '축제/먹거리/문화체험/액티비티/힐링 - 메인 태그 필터용. tour 가 있으면 보통 그 값을 따라간다',
    gender_restriction       ENUM('all','male_only','female_only') NOT NULL DEFAULT 'all',
    age_min                  TINYINT      NULL,
    age_max                  TINYINT      NULL,
    nationality_restriction  ENUM('all','kr_only','jp_only') NOT NULL DEFAULT 'all',
    status                   ENUM('recruiting','full','closed','completed') NOT NULL DEFAULT 'recruiting' COMMENT 'completed = 여행 종료일 경과 후 스케줄러가 자동 전환(UI 표시는 완료)',
    thumbnail_url            VARCHAR(500) NULL,
    created_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_party_owner FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT fk_party_tour FOREIGN KEY (tour_id) REFERENCES tours(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 7. party_members (승인된 파티원 - 파티 전용 페이지 접근권한 판단 기준)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS party_members (
    id        BIGINT   NOT NULL AUTO_INCREMENT,
    party_id  BIGINT   NOT NULL,
    user_id   BIGINT   NOT NULL,
    role      ENUM('owner','member') NOT NULL DEFAULT 'member',
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_party_user (party_id, user_id),
    CONSTRAINT fk_pm_party FOREIGN KEY (party_id) REFERENCES parties(id),
    CONSTRAINT fk_pm_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 8. party_applications (참가 신청 - 방장 승인 방식)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS party_applications (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    party_id       BIGINT       NOT NULL,
    applicant_id   BIGINT       NOT NULL,
    message        VARCHAR(300) NULL COMMENT '자기소개 · 참가 이유',
    status         ENUM('pending','approved','rejected') NOT NULL DEFAULT 'pending',
    applied_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at    DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_party_applicant (party_id, applicant_id),
    CONSTRAINT fk_pa_party FOREIGN KEY (party_id) REFERENCES parties(id),
    CONSTRAINT fk_pa_applicant FOREIGN KEY (applicant_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 9. reservations (패키지 예약)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reservations (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    party_id            BIGINT      NULL COMMENT '파티 없이 혼자 예약하는 개인 이용자는 NULL',
    booked_by_user_id   BIGINT      NOT NULL COMMENT '예약 버튼을 누른 사람(파티장 또는 개인)',
    tour_id             BIGINT      NOT NULL,
    people_count        TINYINT     NOT NULL,
    reservation_number  VARCHAR(30) NOT NULL,
    departure_date      DATE        NOT NULL,
    status              ENUM('pending','confirmed','cancelled') NOT NULL DEFAULT 'pending',
    weather_ack         BOOLEAN     NOT NULL DEFAULT FALSE COMMENT 'AI가 날씨 비추천했는데도 사용자가 감수하고 진행',
    weather_ack_note    VARCHAR(200) NULL COMMENT '예약 시점 날씨 요약(참고용 스냅샷, 실시간 재조회 안 함)',
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at        DATETIME    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reservation_number (reservation_number),
    CONSTRAINT fk_res_party FOREIGN KEY (party_id) REFERENCES parties(id),
    CONSTRAINT fk_res_booker FOREIGN KEY (booked_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_res_tour FOREIGN KEY (tour_id) REFERENCES tours(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 10. reservation_payments (패키지 대금 - 인원별 KRW/JPY 분할 결제)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reservation_payments (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT NOT NULL,
    user_id        BIGINT NOT NULL,
    currency       ENUM('KRW','JPY') NOT NULL,
    amount         INT    NOT NULL,
    status         ENUM('ready','paid','failed') NOT NULL DEFAULT 'ready',
    paid_at        DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_res_user (reservation_id, user_id),
    CONSTRAINT fk_rp_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    CONSTRAINT fk_rp_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 11. trip_schedules (계획표 - 파티 하나당 1개, 여러 명이 동시 편집)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_schedules (
    id             BIGINT   NOT NULL AUTO_INCREMENT,
    party_id       BIGINT   NULL,
    reservation_id BIGINT   NULL,
       status         ENUM('draft','submitted','confirmed') NOT NULL DEFAULT 'draft',
    locked_by_user_id BIGINT NOT NULL COMMENT '[v16] 현재 편집권을 가진 파티원. 파티 생성 시 방장(owner_user_id)으로 초기화, 편집권 회수 시에도 NULL이 아닌 방장 ID로 복귀(앱 코드에서 명시적으로 세팅 - DEFAULT로 다른 컬럼 참조 불가)',
    last_saved_at  DATETIME NULL COMMENT '[v16] 마지막 저장(자동/수동) 시각 - 화면 상단 표시용',
    submitted_at   DATETIME NULL,
    confirmed_at   DATETIME NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_party (party_id),
    UNIQUE KEY uk_schedule_reservation (reservation_id),
    CONSTRAINT fk_ts_party FOREIGN KEY (party_id) REFERENCES parties(id),
    CONSTRAINT fk_ts_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    CONSTRAINT fk_ts_locked_by FOREIGN KEY (locked_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 12. trip_schedule_items (계획표 블록 - 분(1분) 단위 시간 관리)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_schedule_items (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    schedule_id     BIGINT       NOT NULL,
    day_index       TINYINT      NOT NULL COMMENT '1일차, 2일차 ...',
    start_minute    SMALLINT     NOT NULL COMMENT '0~1439, 하루 중 시작 시각(분)',
    duration_minute SMALLINT     NOT NULL COMMENT '1분 단위 조절 가능',
    source          ENUM('package_default','activity','custom') NOT NULL,
    is_fixed        BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '[v16] true=고정(LOCK, 항공·숙박 등), false=이동가능(AI 동선최적화 대상)',
    activity_id     BIGINT       NULL COMMENT 'source=activity 일 때만',
    title           VARCHAR(200) NOT NULL,
    memo            VARCHAR(300) NULL,
    price_krw       INT          NOT NULL DEFAULT 0 COMMENT '추가 시점 가격 스냅샷(activities 실시간 참조 안 함)',
    price_jpy       INT          NOT NULL DEFAULT 0,
    added_by        BIGINT       NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_tsi_schedule FOREIGN KEY (schedule_id) REFERENCES trip_schedules(id),
    CONSTRAINT fk_tsi_activity FOREIGN KEY (activity_id) REFERENCES activities(id),
    CONSTRAINT fk_tsi_user FOREIGN KEY (added_by) REFERENCES users(id),
    CONSTRAINT ck_tsi_start CHECK (start_minute BETWEEN 0 AND 1439),
    CONSTRAINT ck_tsi_duration CHECK (duration_minute >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 13. trip_schedule_payments (액티비티 결제 - 제출 시점에 인원별 생성)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_schedule_payments (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    currency    ENUM('KRW','JPY') NOT NULL,
    amount      INT    NOT NULL,
    status      ENUM('ready','paid','failed') NOT NULL DEFAULT 'ready',
    paid_at     DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_user (schedule_id, user_id),
    CONSTRAINT fk_tsp_schedule FOREIGN KEY (schedule_id) REFERENCES trip_schedules(id),
    CONSTRAINT fk_tsp_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 14. trip_schedule_votes (완성된 계획표 찬반 투표)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_schedule_votes (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT   NOT NULL,
    user_id     BIGINT   NOT NULL,
    vote        ENUM('agree','disagree') NOT NULL,
    voted_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vote_schedule_user (schedule_id, user_id),
    CONSTRAINT fk_tsv_schedule FOREIGN KEY (schedule_id) REFERENCES trip_schedules(id),
    CONSTRAINT fk_tsv_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 15. posts (여행 게시판 글 / 마이페이지 피드)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS posts (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    party_id      BIGINT       NULL COMMENT '어떤 여행/파티에 대한 인증글인지(선택)',
    title         VARCHAR(200) NOT NULL,
    content       TEXT         NOT NULL,
    region        VARCHAR(50)  NULL,
    thumbnail_url VARCHAR(500) NULL,
    like_count    INT          NOT NULL DEFAULT 0 COMMENT '비정규화 카운트(조회 성능용)',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_post_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_post_party FOREIGN KEY (party_id) REFERENCES parties(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS post_likes (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    post_id    BIGINT   NOT NULL,
    user_id    BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_like_post_user (post_id, user_id),
    CONSTRAINT fk_pl_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT fk_pl_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS post_comments (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    post_id    BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    content    VARCHAR(300) NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_pc_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT fk_pc_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 16. follows (팔로우)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS follows (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    follower_id BIGINT   NOT NULL COMMENT '팔로우 하는 사람',
    followee_id BIGINT   NOT NULL COMMENT '팔로우 당하는 사람',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_follow_pair (follower_id, followee_id),
    CONSTRAINT fk_follow_follower FOREIGN KEY (follower_id) REFERENCES users(id),
    CONSTRAINT fk_follow_followee FOREIGN KEY (followee_id) REFERENCES users(id),
    CONSTRAINT ck_follow_not_self CHECK (follower_id <> followee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 17. chat_rooms / chat_room_members / chat_messages
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_rooms (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    type       ENUM('party','dm') NOT NULL,
    party_id   BIGINT   NULL COMMENT 'type=party 일 때만',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_room_party FOREIGN KEY (party_id) REFERENCES parties(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_room_members (
    id        BIGINT   NOT NULL AUTO_INCREMENT,
    room_id   BIGINT   NOT NULL,
    user_id   BIGINT   NOT NULL,
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_user (room_id, user_id),
    CONSTRAINT fk_crm_room FOREIGN KEY (room_id) REFERENCES chat_rooms(id),
    CONSTRAINT fk_crm_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_messages (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    room_id       BIGINT       NOT NULL,
    sender_id     BIGINT       NOT NULL,
    content       VARCHAR(1000) NOT NULL,
    original_lang ENUM('ko','ja') NOT NULL COMMENT '작성 당시 언어(번역 버튼의 source lang)',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_room_created (room_id, created_at),
    CONSTRAINT fk_cm_room FOREIGN KEY (room_id) REFERENCES chat_rooms(id),
    CONSTRAINT fk_cm_sender FOREIGN KEY (sender_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 18. notifications (알림)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(30)  NOT NULL COMMENT 'trip_reminder, party_approved 등',
    title      VARCHAR(200) NOT NULL,
    message    VARCHAR(300) NOT NULL,
    link_url   VARCHAR(255) NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notif_user (user_id, is_read, created_at),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 19. reports (신고 - 게시글/파티/사용자 공용)
--     target_type + target_id 로 무엇을 신고했는지 가리킨다(다형 연관).
--     서로 다른 테이블(posts/parties/users)을 가리킬 수 있어서 target_id 에는
--     FK 를 걸지 않고, 신고 시점의 제목/이름을 target_label 에 그대로 스냅샷으로
--     남겨서 관리자 화면에서 굳이 매번 조인하지 않아도 뭘 신고했는지 바로 보이게 한다.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reports (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    reporter_id   BIGINT       NOT NULL,
    target_type   ENUM('post','party','user') NOT NULL,
    target_id     BIGINT       NOT NULL,
    target_label  VARCHAR(200) NOT NULL,
    reason        VARCHAR(500) NOT NULL,
    status        ENUM('pending','resolved','dismissed') NOT NULL DEFAULT 'pending',
    action_taken  ENUM('none','hidden','deleted') NOT NULL DEFAULT 'none' COMMENT '[v16] 게시판·파티 신고 콘텐츠 최종조치 결과',
    actioned_by   BIGINT       NULL COMMENT '[v16] 조치한 관리자(⑤)',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at   DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_report_status (status, created_at),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES users(id),
    CONSTRAINT fk_report_actioned_by FOREIGN KEY (actioned_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 20. trip_schedule_snapshots [v16 신규] (계획표 저장 시점 스냅샷 - 롤백의 소스)
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
-- 21. manner_temp_logs [v16 신규] (매너온도 가산/감산 이력 - 감사 로그)
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
-- 22. ai_credit_usage [v16 신규] (사용자별 일일 AI 크레딧 사용량)
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
-- 23. user_profile_theme [v16 신규] (프로필 배경 꾸미기 설정)
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
