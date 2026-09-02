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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * "그날 날씨에 따라 추천/비추천" 챗봇 로직.
 *
 * <p>[기존] 예약 전에 이 서비스로 날씨를 확인 -> 나쁘면 경고 문구 + 대안 패키지 제시
 * -> 사용자가 "그래도 갈게요" 를 누르면 ReservationService.reserve(..., weatherAck=true) 로 진행.
 * (ReservationService, PlannerController 가 아직 이 경로를 쓰고 있어 그대로 유지 -
 *  예약/결제 코드 정리 작업 때 같이 정리될 예정, 지금은 손대지 않음)
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
	
	// ----------------------------------------------------------------
	// 기존 메서드 - ReservationService, PlannerController 가 사용 중이므로 그대로 유지
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
	// [v16 신규] 액티비티를 계획표에 담을 때 쓰는 날씨체크
	// ----------------------------------------------------------------
	
	@Transactional(readOnly = true)
	public WeatherAdviceResponse checkActivityWeather(Long activityId, LocalDate date) {
		ActivityEntity activity = activityRepository.findById(activityId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND));
		
		String region = activity.getRegion();
		
		// VenueType null 방어 - TODO: 지금은 임시로 mixed 처리(경고 없이 통과됨).
		// ActivitySearchService로 방금 검색해서 아직 판정 안 된 장소가 여기 걸릴 수 있음.
		// 나중에 ChatbotActivityService의 판정 로직을 여기서도 호출해서
		// null이면 그때그때 판정부터 하고 진행하도록 개선 필요.
		VenueType vType = activity.getVenueType();
		if (vType == null) {
			vType = VenueType.mixed;
		}
		
		if (activity.getLatitude() == null || activity.getLongitude() == null) {
			// 좌표 없는 액티비티는 날씨 조회 생략, 항상 통과
			return WeatherAdviceResponse.builder()
					.isRecommendable(true)
					.weatherCondition("정보 없음")
					.message("이 장소는 날씨 정보를 제공하지 않아요. 그대로 추가할 수 있어요.")
					.alternatives(null)
					.build();
		}
		
		WeatherResult weather = weatherClient.getForecast(
				activity.getLatitude().doubleValue(), activity.getLongitude().doubleValue(), date);
		boolean isBadWeather = !weather.isGood();
		
		if (isBadWeather && vType == VenueType.outdoor) {
			List<ActivityEntity> alternatives = activityRepository.findByRegionAndStatus(region, ActiveStatus.active).stream()
					.filter(a -> a.getVenueType() == VenueType.indoor || a.getVenueType() == VenueType.mixed)
					.limit(3)
					.toList();
			
			return WeatherAdviceResponse.builder()
					.isRecommendable(false)
					.weatherCondition(weather.condition())
					.message(String.format("선택하신 날짜에 '%s' 예보가 있습니다. 야외 활동 대신 이런 실내 명소는 어떠세요?", weather.condition()))
					.alternatives(alternatives)
					.build();
		}
		
		return WeatherAdviceResponse.builder()
				.isRecommendable(true)
				.weatherCondition(weather.condition())
				.message("일정에 추가하기 좋은 조건입니다!")
				.alternatives(null)
				.build();
	}
}