package net.datasa.tanoshimi.service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.dto.WeatherAdviceDTO;
import net.datasa.tanoshimi.domain.dto.WeatherResult;
import net.datasa.tanoshimi.domain.entity.ActiveStatus;
import net.datasa.tanoshimi.domain.entity.TourEntity;
import net.datasa.tanoshimi.repository.TourRepository;
import net.datasa.tanoshimi.util.WeatherClient;
import org.springframework.stereotype.Service;

/**
 * 날씨 기반 여행 추천 챗봇 서비스.
 * 예약 진행 전 해당 지역의 날씨를 확인하여 경고를 주거나 대안 패키지를 제시합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherAdvisorService {

    private final WeatherClient weatherClient;
    private final TourRepository tourRepository;

    private static class CacheEntry {
        final WeatherResult result;
        final long timestamp;

        CacheEntry(WeatherResult result) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > (1000 * 60 * 60);
        }
    }

    private final Map<String, CacheEntry> weatherCache = new ConcurrentHashMap<>();

    public WeatherAdviceDTO adviseForTour(TourEntity tour, LocalDate date) {
        if (tour.getLatitude() == null || tour.getLongitude() == null) {
            return new WeatherAdviceDTO(null, "이 지역은 날씨 정보를 아직 제공하지 않습니다. 원하시면 계속 예약을 진행해드릴게요.", true, List.of());
        }

        WeatherResult weather = getWeatherForRegion(tour.getRegion(), tour.getLatitude().doubleValue(), tour.getLongitude().doubleValue(), date);

        if (weather.isGood()) {
            String msg = String.format("✈️ 선택하신 날짜(%s)의 %s 날씨는 '%s'입니다! 야외 활동하기 참 좋은 날씨네요. 예약 진행을 원하시면 우측의 예약 버튼을 눌러주세요.",
                    date, tour.getRegion(), weather.condition());
            return new WeatherAdviceDTO(weather, msg, true, List.of());
        }

        String msg = String.format(
                "☔ 아쉽게도 %s %s 지역은 '%s'(강수확률 %d%%) 예보가 있어요.\n" +
                "이대로 일정을 진행하실 수 있지만, 가능하시다면 실내 위주의 액티비티를 강하게 권장드려요.\n\n" +
                "💡 만약 일정을 바꿀 의향이 있으시다면, 거리가 가까우면서도 날씨가 좋은 다른 여행지는 어떠신가요?",
                date, tour.getRegion(), weather.condition(), weather.precipProb());

        List<TourEntity> alternatives = findGoodWeatherAlternatives(tour, date);
        
        if (alternatives.isEmpty()) {
            msg += "\n\n(아쉽게도 현재 같은 날짜에 날씨가 더 좋은 주변 지역이 없네요. ㅠㅠ)";
        }
        
        return new WeatherAdviceDTO(weather, msg, false, alternatives);
    }

    private WeatherResult getWeatherForRegion(String region, double lat, double lon, LocalDate date) {
        String cacheKey = region + "_" + date.toString();
        CacheEntry entry = weatherCache.get(cacheKey);

        if (entry != null && !entry.isExpired()) {
            return entry.result;
        }

        WeatherResult freshResult;
        try {
            freshResult = weatherClient.getForecast(lat, lon, date);
        } catch (Exception e) {
            log.error("Failed to check weather for region: {}", region, e);
            if (entry != null) {
                return entry.result; 
            }
            return new WeatherResult("알 수 없음", 20.0, 10.0, 10, true);
        }

        weatherCache.put(cacheKey, new CacheEntry(freshResult));
        return freshResult;
    }

    /** 
     * 대안 추천 로직 최적화:
     * 1. 외부 API 호출을 병렬(Parallel Stream)로 처리하여 병목 수십 배 단축.
     * 2. Haversine 공식을 도입하여 원래 목적지와 지리적으로 가장 "가까운" 지역부터 우선 추천.
     */
    private List<TourEntity> findGoodWeatherAlternatives(TourEntity excludeTour, LocalDate date) {
        List<TourEntity> allTours = tourRepository.findByStatusOrderByIdDesc(ActiveStatus.active);
        
        Map<String, List<TourEntity>> toursByRegion = allTours.stream()
                .filter(t -> t.getLatitude() != null && t.getLongitude() != null)
                .filter(t -> t.getRegion() != null && !t.getRegion().equals(excludeTour.getRegion()))
                .collect(Collectors.groupingBy(TourEntity::getRegion));

        double baseLat = excludeTour.getLatitude().doubleValue();
        double baseLon = excludeTour.getLongitude().doubleValue();

        // 1. Parallel stream을 사용하여 여러 지역의 날씨 상태를 동시에(비동기적으로) 가져오기
        return toursByRegion.values().parallelStream()
                .map(regionTours -> {
                    TourEntity repTour = regionTours.get(0);
                    WeatherResult w = getWeatherForRegion(repTour.getRegion(), repTour.getLatitude().doubleValue(), repTour.getLongitude().doubleValue(), date);
                    if (w.isGood()) {
                        // 해당 지역 내에서 제일 저렴한 투어를 반환값으로 포장
                        TourEntity bestInRegion = regionTours.stream()
                                .min(Comparator.comparingInt(TourEntity::getPriceKrw))
                                .orElse(repTour);
                        return bestInRegion;
                    }
                    return null;
                })
                .filter(Objects::nonNull) // 날씨 좋은 곳만 통과
                .sorted((t1, t2) -> {
                    // 2. Haversine 거리 계산을 통해 물리적으로 '가장 가까운 거리'의 대체 여행지를 우선 정렬
                    double dist1 = calculateHaversineDistance(baseLat, baseLon, t1.getLatitude().doubleValue(), t1.getLongitude().doubleValue());
                    double dist2 = calculateHaversineDistance(baseLat, baseLon, t2.getLatitude().doubleValue(), t2.getLongitude().doubleValue());
                    return Double.compare(dist1, dist2);
                })
                .limit(3)
                .toList();
    }

    /** 두 위도/경도 간의 실제 거리(km)를 구하는 Haversine 공식 */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 지구 반지름 (km)
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
                 
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
