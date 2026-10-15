package net.datasa.tanoshimi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.dto.WeatherAdviceDTO;
import net.datasa.tanoshimi.domain.dto.WeatherAdviceResponse;
import net.datasa.tanoshimi.domain.dto.WeatherResult;
import net.datasa.tanoshimi.domain.entity.ActiveStatus;
import net.datasa.tanoshimi.domain.entity.ActivityEntity;
import net.datasa.tanoshimi.domain.entity.TourEntity;
import net.datasa.tanoshimi.domain.entity.VenueType;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.ActivityRepository;
import net.datasa.tanoshimi.repository.TourRepository;
import net.datasa.tanoshimi.util.WeatherClient;
import net.datasa.tanoshimi.domain.dto.WeatherAlternative;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * "그날 날씨에 따라 추천/비추천" 챗봇 로직.
 *
 * <p>adviseForTour - 파티 tour 의 날씨를 확인해 나쁘면 경고 문구 + 대안 패키지를 제시한다.
 * PlannerController 의 AI 추천 경로에서 사용한다.
 *
 * <p>[v16 신규] checkActivityWeather - 플래너에 액티비티를 담을 때, 그 장소가 실외인데
 * 날씨가 나쁘면 경고하고 같은 지역 실내 대안을 제시한다. 날씨는 저장하지 않고 항상
 * 실시간(WeatherClient)으로 조회한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherAdvisorService {
	
	private final WeatherClient weatherClient;
	private final TourRepository tourRepository;
	private final ActivityRepository activityRepository;
	private final MessageSource messageSource;   // ← 추가
	
	// ----------------------------------------------------------------
	// PlannerController 의 AI 추천 경로에서 사용
	// ----------------------------------------------------------------

	public WeatherAdviceDTO adviseForTour(TourEntity tour, LocalDate date) {
		if (tour.getLatitude() == null || tour.getLongitude() == null) {
			// 좌표가 없는 더미데이터는 날씨 조회 자체를 생략하고 항상 추천 처리
			return new WeatherAdviceDTO(null, "이 지역은 날씨 정보를 아직 제공하지 않습니다.", true, List.of());
		}
		
		WeatherResult weather = weatherClient.getForecast(
				tour.getLatitude().doubleValue(), tour.getLongitude().doubleValue(), date);
		
		if (weather.isGood()) {
			String msg = String.format("%s %s은(는) %s로 여행하기 좋은 날씨예요!",
					date, tour.getRegion(), weather.condition());
			return new WeatherAdviceDTO(weather, msg, true, List.of());
		}
		
		String msg = String.format(
				"%s %s은(는) %s(강수확률 %d%%)로 예보돼 있어요. 이 날씨도 괜찮으시면 그대로 진행할 수 있고, 같은 날짜에 날씨 좋은 다른 지역도 보여드릴 수 있어요.",
				date, tour.getRegion(), weather.condition(), weather.precipProb());
		
		List<TourEntity> alternatives = findGoodWeatherAlternatives(tour, date);
		return new WeatherAdviceDTO(weather, msg, false, alternatives);
	}
	
	/** 같은 날짜 기준, 다른 지역 패키지 중 날씨 좋은 곳을 최대 3개 추천. */
	private List<TourEntity> findGoodWeatherAlternatives(TourEntity excludeTour, LocalDate date) {
		return tourRepository.findByStatusOrderByIdDesc(ActiveStatus.active).stream()
				.filter(t -> !t.getId().equals(excludeTour.getId()))
				.filter(t -> t.getLatitude() != null && t.getLongitude() != null)
				.filter(t -> {
					WeatherResult w = weatherClient.getForecast(
							t.getLatitude().doubleValue(), t.getLongitude().doubleValue(), date);
					return w.isGood();
				})
				.sorted(Comparator.comparingInt(TourEntity::getPriceKrw))
				.limit(3)
				.toList();
	}
	
	// ----------------------------------------------------------------
	// [TNSM-72] AI가 구글검색으로 새로 찾아준 장소(DB에 없음, activityId=null)용 날씨체크.
	// ChatbotActivityService.recommend() 프롬프트가 kind=custom 항목에 latitude/longitude/
	// venueType을 같이 돌려주도록 해서, DB 조회 없이 좌표로 바로 날씨를 확인한다.
	// [수정] 예전엔 DB 밖 장소는 대체 장소를 하나도 주지 않았다. 지역명이 없으니
	// 좌표 기준으로 가까운(반경 NEARBY_KM) 실내 장소를 찾아서 같이 내려준다.
	// ----------------------------------------------------------------
	
	@Transactional(readOnly = true)
	public WeatherAdviceResponse checkCoordsWeather(double latitude, double longitude, String venueTypeStr, LocalDate date) {
		VenueType vType;
		try {
			vType = (venueTypeStr != null && !venueTypeStr.isBlank()) ? VenueType.valueOf(venueTypeStr.trim().toLowerCase()) : VenueType.mixed;
		} catch (IllegalArgumentException e) {
			vType = VenueType.mixed;
		}
		
		WeatherResult weather = weatherClient.getForecast(latitude, longitude, date);
		
		if (!weather.isGood() && vType == VenueType.outdoor) {
			List<WeatherAlternative> alternatives = indoorAlternatives(
					activityRepository.findByStatus(ActiveStatus.active), latitude, longitude, null, true);
			return badWeather(weather, alternatives);
		}
		return goodWeather(weather);
	}
	
	// ----------------------------------------------------------------
	// [v16 신규] 액티비티를 계획표에 담을 때 쓰는 날씨체크
	// ----------------------------------------------------------------
	
	@Transactional(readOnly = true)
	public WeatherAdviceResponse checkActivityWeather(Long activityId, LocalDate date) {
		ActivityEntity activity = activityRepository.findById(activityId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND));
		
		// VenueType null 방어 - TODO: 지금은 임시로 mixed 처리(경고 없이 통과됨).
		// ActivitySearchService로 방금 검색해서 아직 판정 안 된 장소가 여기 걸릴 수 있음.
		VenueType vType = activity.getVenueType() != null ? activity.getVenueType() : VenueType.mixed;
		
		if (activity.getLatitude() == null || activity.getLongitude() == null) {
			// 좌표 없는 액티비티는 날씨 조회 생략, 항상 통과
			return WeatherAdviceResponse.builder()
					.recommendable(true)
					.weatherCondition(msg("weather.cond.unknown"))
					.message(msg("weather.advice.noinfo"))
					.alternatives(null)
					.build();
		}
		
		double lat = activity.getLatitude().doubleValue();
		double lon = activity.getLongitude().doubleValue();
		WeatherResult weather = weatherClient.getForecast(lat, lon, date);
		
		if (!weather.isGood() && vType == VenueType.outdoor) {
			List<WeatherAlternative> alternatives = indoorAlternatives(
					activityRepository.findByRegionAndStatus(activity.getRegion(), ActiveStatus.active),
					lat, lon, activity.getId(), false);
			return badWeather(weather, alternatives);
		}
		return goodWeather(weather);
	}
	
	// ----------------------------------------------------------------
	// 공통 헬퍼
	// ----------------------------------------------------------------
	
	/** 대체 장소를 찾는 반경(km). 같은 지역 안이면 거리 제한 없이 가까운 순으로만 정렬한다. */
	static final double NEARBY_KM = 30.0;
	static final int MAX_ALTERNATIVES = 3;
	
	/**
	 * 실내(indoor) 장소를 우선, 그다음 혼합(mixed)을 가까운 순으로 최대 3개.
	 * [수정] 예전엔 정렬 없이 DB 순서대로 3개를 잘라서, 멀리 있는 곳이 먼저 나오기도 했다.
	 */
	List<WeatherAlternative> indoorAlternatives(List<ActivityEntity> candidates, double lat, double lon,
												Long excludeId, boolean limitByDistance) {
		return candidates.stream()
				.filter(a -> excludeId == null || !excludeId.equals(a.getId()))
				.filter(a -> a.getVenueType() == VenueType.indoor || a.getVenueType() == VenueType.mixed)
				.filter(a -> a.getLatitude() != null && a.getLongitude() != null)
				.filter(a -> !limitByDistance || distanceKm(lat, lon, a) <= NEARBY_KM)
				.sorted(Comparator
						.comparing((ActivityEntity a) -> a.getVenueType() == VenueType.indoor ? 0 : 1)
						.thenComparingDouble(a -> distanceKm(lat, lon, a)))
				.limit(MAX_ALTERNATIVES)
				.map(WeatherAlternative::from)
				.toList();
	}
	
	/** 두 좌표 사이 거리(km) - 하버사인 공식 */
	private static double distanceKm(double lat, double lon, ActivityEntity a) {
		double lat2 = a.getLatitude().doubleValue();
		double lon2 = a.getLongitude().doubleValue();
		double dLat = Math.toRadians(lat2 - lat);
		double dLon = Math.toRadians(lon2 - lon);
		double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
		return 6371.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
	}
	
	private WeatherAdviceResponse badWeather(WeatherResult weather, List<WeatherAlternative> alternatives) {
		String cond = conditionLabel(weather.condition());
		String key = alternatives.isEmpty() ? "weather.advice.bad" : "weather.advice.bad.alt";
		return WeatherAdviceResponse.builder()
				.recommendable(false)
				.weatherCondition(cond)
				.message(msg(key, cond))
				.alternatives(alternatives)
				.build();
	}
	
	private WeatherAdviceResponse goodWeather(WeatherResult weather) {
		return WeatherAdviceResponse.builder()
				.recommendable(true)
				.weatherCondition(conditionLabel(weather.condition()))
				.message(msg("weather.advice.good"))
				.alternatives(null)
				.build();
	}
	
	/**
	 * 날씨 클라이언트는 "비", "맑음" 같은 한국어 코드를 돌려준다. 화면 언어에 맞게 바꿔서 보여준다.
	 * [수정] 예전엔 일본어 화면에서도 경고 문구가 한국어로 고정돼 있었다.
	 * 목록에 없는 값(실제 API의 상세 설명 등)은 그대로 둔다.
	 */
	String conditionLabel(String condition) {
		if (condition == null) return msg("weather.cond.unknown");
		String code = switch (condition) {
			case "맑음" -> "clear";
			case "흐림" -> "cloudy";
			case "비" -> "rain";
			case "눈" -> "snow";
			case "폭염" -> "heat";
			case "한파" -> "cold";
			default -> null;
		};
		return code == null ? condition : msg("weather.cond." + code);
	}
	
	private String msg(String key, Object... args) {
		return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
	}
}