package net.datasa.tanoshimi.domain.dto;

/** 메인 페이지 "모집 마감 임박" 카드 + 파티 게시판 카드 공용 뷰. */
public record PartyCardView(
        Long id, String title, String region, String departureDate,
        Integer budgetKrw, int capacity, int joinedCount, String thumbnailUrl, String styleTag
) {
    public int remaining() { return capacity - joinedCount; }
}
