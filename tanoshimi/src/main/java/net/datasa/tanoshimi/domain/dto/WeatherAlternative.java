package net.datasa.tanoshimi.domain.dto;

import net.datasa.tanoshimi.domain.entity.ActivityEntity;

/**
 * 날씨가 나쁠 때 플래너 경고 모달에 보여줄 실내 대체 장소.
 * 예전엔 ActivityEntity 를 그대로 내려보냈는데, 화면에서 쓰지도 않았고(미표시 버그)
 * 엔티티를 JSON 으로 직접 노출하는 것도 위험해서 필요한 값만 담는 DTO 로 분리했다.
 */
public record WeatherAlternative(
		Long activityId,
		String title,
		int durationMin,
		String venueType,
		Double latitude,
		Double longitude
) {
	public static WeatherAlternative from(ActivityEntity a) {
		return new WeatherAlternative(
				a.getId(),
				a.getTitle(),
				a.getDurationMin(),
				a.getVenueType() != null ? a.getVenueType().name() : null,
				a.getLatitude() != null ? a.getLatitude().doubleValue() : null,
				a.getLongitude() != null ? a.getLongitude().doubleValue() : null);
	}
}