package net.datasa.tanoshimi.domain.dto;

import net.datasa.tanoshimi.domain.entity.PostEntity;

/**
 * [TNSM-53] 홈 화면 "스냅사진 둘러보기" 피드 카드 뷰.
 *
 * <p>PostEntity를 그대로 JS로 직렬화하면 LAZY 필드(party 등)에서 open-in-view=false 환경에
 * LazyInitializationException이 날 수 있다(PostSnapView 주석 참고 - 이 프로젝트에 이미 있는
 * 사례). 그래서 트랜잭션 안에 있는 PostService에서 미리 값을 꺼내 이 DTO로 넘긴다.
 */
public record PostSnapCardView(
        Long id, String title, String region, String thumbnailUrl, int likeCount, String authorName
) {
    public static PostSnapCardView of(PostEntity p) {
        return new PostSnapCardView(
                p.getId(), p.getTitle(), p.getRegion(), p.getThumbnailUrl(),
                p.getLikeCount(), p.getUser().getName());
    }
}
