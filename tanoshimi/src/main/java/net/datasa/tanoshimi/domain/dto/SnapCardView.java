package net.datasa.tanoshimi.domain.dto;

/**
 * 메인 페이지 "인기 스냅" 카드 뷰.
 *
 * <p>기존 "스냅사진 둘러보기" 섹션은 이름만 스냅이고 실제로는 파티 모집글({@link PartyCardView})을
 * 렌더링하고 있었다. 이 뷰는 커뮤니티에 올라온 진짜 사진 글(posts)을 좋아요순으로 보여주기 위한
 * 것이며, 카드 클릭 시 게시글 상세(/board/{id})로 이동한다.
 */
public record SnapCardView(
        Long id, String title, String region, String thumbnailUrl, int likeCount, String authorName
) {}
