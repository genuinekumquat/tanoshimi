package net.datasa.tanoshimi.domain.dto;

/**
 * 마이페이지 히트맵의 지역 한 칸.
 *
 * @param trips 그 지역에서 완료한 여행(파티) 수
 * @param days  그 지역에서 보낸 총 여행일수
 * @param score 지도 색 단계를 정하는 여행 지수 (TravelHeatmapService 참고)
 */
public record RegionVisitView(int trips, int days, int score) {}
