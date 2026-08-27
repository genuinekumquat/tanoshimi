package net.datasa.tanoshimi.domain.dto;

import java.math.BigDecimal;

/** [v16 신규] 장소검색 API(제공사 미확정 - Google Places vs OSM) 조회 결과 공용 형태. */
public record PlaceSearchResult(
        String externalPlaceId, String provider, String title, String description,
        BigDecimal latitude, BigDecimal longitude, String thumbnailUrl
) {
}
