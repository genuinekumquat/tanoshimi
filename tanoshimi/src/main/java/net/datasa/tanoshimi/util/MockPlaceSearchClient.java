package net.datasa.tanoshimi.util;

import java.math.BigDecimal;
import java.util.List;
import net.datasa.tanoshimi.domain.dto.PlaceSearchResult;
import org.springframework.stereotype.Component;

/**
 * [v16 신규] 장소검색 API 제공사 미확정 상태에서 쓰는 더미 구현체.
 * 실제로는 지도 좌표를 조회해오지 않고, 지역명을 기준으로 그럴듯한 좌표를 결정론적으로
 * 생성한다(WeatherClient 의 MockWeatherClient 와 동일한 접근 - 같은 입력엔 같은 결과).
 */
@Component
public class MockPlaceSearchClient implements PlaceSearchClient {

    @Override
    public List<PlaceSearchResult> search(String query, String region) {
        int seed = (query + region).hashCode();
        double latBase = 35.0 + (seed % 1000) / 1000.0 * 5;
        double lngBase = 130.0 + (seed % 700) / 700.0 * 10;
        String placeId = "mock-" + Integer.toHexString(seed);
        return List.of(new PlaceSearchResult(
                placeId, "osm", query,
                region + "에서 검색된 장소 (장소검색 API 제공사 확정 전까지의 임시 데이터)",
                BigDecimal.valueOf(latBase).setScale(7, java.math.RoundingMode.HALF_UP),
                BigDecimal.valueOf(lngBase).setScale(7, java.math.RoundingMode.HALF_UP),
                "ph1"
        ));
    }
}
