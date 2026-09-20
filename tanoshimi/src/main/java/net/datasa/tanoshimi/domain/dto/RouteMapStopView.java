package net.datasa.tanoshimi.domain.dto;

/**
 * "지도로 보기" 화면(planner/route-map)에 내려주는 정거장 1건.
 * 장소 단건 "보기" 링크는 항상 mapQuery(이름+지역) 텍스트 검색을 쓴다 - 좌표 핀보다 이름 검색이
 * 리뷰/사진 딸린 정상적인 장소 카드를 보여준다. hasLocation/latitude/longitude는 구간 "길찾기"
 * (실제 경로 계산)에만 쓰는 좌표다.
 */
public record RouteMapStopView(
        int dayIndex, int startMinute, int durationMinute,
        String title, String memo, boolean hasLocation,
        String latitude, String longitude, String mapQuery
) {
}
