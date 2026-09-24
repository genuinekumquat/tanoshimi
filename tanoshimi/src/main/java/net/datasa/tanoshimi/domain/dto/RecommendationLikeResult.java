package net.datasa.tanoshimi.domain.dto;

/** 관광지 추천 좋아요 토글 결과 - 누른 뒤 내가 좋아요 상태인지와 갱신된 좋아요 수. */
public record RecommendationLikeResult(boolean liked, int likeCount) {
}
