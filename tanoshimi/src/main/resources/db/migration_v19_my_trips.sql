-- =====================================================================
-- 마이그레이션 v19: "내 여행"(my_trips) 신규 + posts.trip_id 연결 컬럼
-- 담당: 김민규(⑥ 마이페이지)
--
-- 배경
--  - v18까지는 완료된 파티 + 지역 태그 스냅(party_id NULL 인 posts)을 직접 스캔해서
--    "여행 횟수"를 셌다. 두 가지 문제가 있었다.
--    1) 파티는 스냅 하나 없이 완료만 되면 카운트됐다 - 지도에 스냅이 없는 권역인데도
--       "8도 정복자" 같은 칭호가 붙는 것처럼 보이는 원인이었다.
--    2) 개별 여행은 스냅 단위로 셌는데, 한 번 여행에서 스냅을 며칠에 걸쳐 여러 장 올리면
--       여행은 한 번인데 횟수가 여러 번으로 잡힐 수 있었다.
--  - v19부터 "여행"은 이 테이블의 행 하나다. 파티가 완료되면 자동으로 한 행이 생기고
--    (MyTripService.syncFromCompletedParties, 멱등), 개별 여행은 사용자가 마이페이지
--    "내 여행"에서 직접 추가/수정/삭제한다. TitleService/TravelHeatmapService는 이제
--    parties/posts를 직접 스캔하지 않고 이 테이블만 본다.
--  - posts.trip_id 는 스냅을 올릴 때 "여행 선택"으로 고른 여행을 기록하는 용도다(선택).
--    여행 횟수 집계에는 관여하지 않는다 - my_trips 자체가 근거이므로 사진을 몇 장 올리든
--    횟수가 늘지 않는다.
--
-- 실행 전제: schema.sql 의 users/parties/posts 테이블이 이미 있어야 한다.
-- =====================================================================
USE tanoshimi;

CREATE TABLE IF NOT EXISTS my_trips (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    source       ENUM('SOLO','PARTY') NOT NULL DEFAULT 'SOLO' COMMENT 'PARTY = 파티 완료 자동 등록(수정/삭제 불가), SOLO = 사용자 직접 등록',
    party_id     BIGINT       NULL COMMENT 'source=PARTY일 때만 채워짐. 같은 파티 중복 등록 방지 근거',
    title        VARCHAR(200) NOT NULL COMMENT '여행 이름',
    destination  VARCHAR(100) NOT NULL COMMENT '여행지(자유 입력 - 위치태그 선택 아님)',
    start_date   DATE         NOT NULL COMMENT '가는 날',
    end_date     DATE         NOT NULL COMMENT '오는 날 - 화면에서 (end-start)로 n박 m일 계산',
    memo         VARCHAR(500) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_my_trips_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_my_trips_party FOREIGN KEY (party_id) REFERENCES parties(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 같은 사용자가 같은 파티로 두 번 자동 등록되지 않게(party_id 가 NULL 인 SOLO 여행은
-- MySQL 에서 NULL 끼리 유니크 충돌로 안 치므로 여러 건 허용됨 - 의도한 동작).
CREATE UNIQUE INDEX uq_my_trips_user_party ON my_trips (user_id, party_id);

ALTER TABLE posts
    ADD COLUMN trip_id BIGINT NULL COMMENT '[v19] 이 스냅이 딸린 내 여행(선택)' AFTER party_id,
    ADD CONSTRAINT fk_posts_trip FOREIGN KEY (trip_id) REFERENCES my_trips(id);

-- 확인
SELECT COUNT(*) AS my_trips_table_exists FROM information_schema.tables
 WHERE table_schema = 'tanoshimi' AND table_name = 'my_trips';
SELECT COUNT(*) AS posts_trip_id_exists FROM information_schema.columns
 WHERE table_schema = 'tanoshimi' AND table_name = 'posts' AND column_name = 'trip_id';
