package net.datasa.tanoshimi.domain.dto;

import java.util.Map;

/**
 * 마이페이지 "지도 정복 히트맵" 집계 결과.
 *
 * @param regions        지역명(parties.region 에 저장된 한글 그대로) → 그 지역 집계.
 *                       지도 키(osaka, capital 등)로의 변환은 화면(mypage-heatmap.js)이
 *                       map-data.js 의 지역명을 보고 처리한다 - 지역 목록이 늘어날 때마다
 *                       자바 쪽 switch 문을 같이 고쳐야 하는 걸 피하기 위함.
 * @param totalTrips     완료한 여행 총 횟수
 * @param visitedRegions 한 번이라도 다녀온 지역 수
 */
public record TravelHeatmapView(Map<String, RegionVisitView> regions, int totalTrips, int visitedRegions) {}
