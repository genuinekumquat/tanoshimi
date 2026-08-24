-- =====================================================================
-- 마이그레이션: 신고 기능(reports 테이블) 추가
-- 이미 schema.sql 을 실행하신 분들은 이 파일만 실행하면 됩니다.
-- =====================================================================
USE tanoshimi;

CREATE TABLE IF NOT EXISTS reports (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    reporter_id   BIGINT       NOT NULL,
    target_type   ENUM('post','party','user') NOT NULL,
    target_id     BIGINT       NOT NULL,
    target_label  VARCHAR(200) NOT NULL,
    reason        VARCHAR(500) NOT NULL,
    status        ENUM('pending','resolved','dismissed') NOT NULL DEFAULT 'pending',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at   DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_report_status (status, created_at),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
