-- =====================================================================
-- 마이그레이션: 관광지 추천 좋아요 중복 방지 - recommendation_likes 테이블 추가
--
-- 배경
--  - /recommendations 의 하트는 누를 때마다 recommendation.like_count 만 +1 해서
--    한 사람이 무한정 누를 수 있었다.
--  - 게시글 좋아요(post_likes)와 같은 방식으로 누른 사람을 기록하고, 다시 누르면 취소한다.
--  - 기존 like_count 값은 그대로 둔다(누가 눌렀는지 기록이 없어 되돌릴 기준이 없다).
-- =====================================================================
USE tanoshimi;

CREATE TABLE IF NOT EXISTS recommendation_likes (
    id                BIGINT   NOT NULL AUTO_INCREMENT,
    recommendation_id BIGINT   NOT NULL,
    user_id           BIGINT   NOT NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rl_rec_user (recommendation_id, user_id),
    CONSTRAINT fk_rl_rec  FOREIGN KEY (recommendation_id) REFERENCES recommendation(id),
    CONSTRAINT fk_rl_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
