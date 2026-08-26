package net.datasa.tanoshimi.util;

import java.util.List;
import net.datasa.tanoshimi.domain.dto.PlaceSearchResult;

/**
 * [v16 신규] 장소검색 API 인터페이스. 기능명세서 확정 사항: "장소검색·경로계산 API 제공사
 * (Google vs OpenStreetMap) 미확정 - 팀 논의 후 확정 예정." 그래서 WeatherClient 와
 * 동일한 패턴으로 인터페이스로 추상화해두고, 지금은 MockPlaceSearchClient 로 동작한다.
 * 제공사가 확정되면 이 인터페이스의 새 구현체(GooglePlaceSearchClient 등)만 추가하고
 * @Primary 로 교체하면 되며, 호출부(ActivitySearchService 등) 코드는 바뀌지 않는다.
 */
public interface PlaceSearchClient {
    List<PlaceSearchResult> search(String query, String region);
}
