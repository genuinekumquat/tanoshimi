package net.datasa.tanoshimi.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.WeatherAdviceDTO;
import net.datasa.tanoshimi.domain.dto.WeatherResult;
import net.datasa.tanoshimi.domain.entity.ActiveStatus;
import net.datasa.tanoshimi.domain.entity.TourEntity;
import net.datasa.tanoshimi.repository.TourRepository;
import net.datasa.tanoshimi.util.WeatherClient;
import org.springframework.stereotype.Service;

/**
 * "그날 날씨에 따라 추천/비추천" 챗봇 로직.
 *
 * <p>흐름: 예약 전에 이 서비스로 날씨를 확인 -> 나쁘면 경고 문구 + 대안 패키지 제시
 * -> 사용자가 "그래도 갈게요" 를 누르면 ReservationService.reserve(..., weatherAck=true) 로 진행.
 * 날씨는 저장하지 않고 항상 실시간(WeatherClient) 으로 조회한다.
 */
@Service
@RequiredArgsConstructor
public class WeatherAdvisorService {

    private final WeatherClient weatherClient;
    private final TourRepository tourRepository;

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
}
