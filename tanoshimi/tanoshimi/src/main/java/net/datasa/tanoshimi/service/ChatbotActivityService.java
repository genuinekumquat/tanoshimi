package net.datasa.tanoshimi.service;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.ActiveStatus;
import net.datasa.tanoshimi.domain.entity.ActivityEntity;
import net.datasa.tanoshimi.domain.entity.VenueType;
import net.datasa.tanoshimi.repository.ActivityRepository;
import org.springframework.stereotype.Service;

/**
 * 계획표 화면의 AI 챗봇 액티비티 추천.
 * "AI가 추천" 과 "검색으로 추천받기" 둘 다 이 서비스를 거친다 - 차이는 keyword 유무뿐이다.
 *
 * <p>날씨가 나쁜 날은 indoor 위주로, 좋은 날은 outdoor 를 우선 노출한다
 * (mixed 는 항상 포함). 실제로는 여기서 LLM 을 호출해 자연어 질의를 해석하지만,
 * 지금은 keyword 를 제목/스타일태그에 단순 포함 검색으로 대체했다.
 * TODO(다음 단계): 실제 LLM 기반 자연어 추천으로 교체, 임베딩 유사도 검색 도입.
 */
@Service
@RequiredArgsConstructor
public class ChatbotActivityService {

    private final ActivityRepository activityRepository;
    private final WeatherAdvisorService weatherAdvisorService;

    /** 날씨 기반 추천. keyword 가 있으면 추가로 필터링(=검색 추천 겸용). */
    public List<ActivityEntity> recommend(String region, LocalDate date, String keyword, boolean todayIsBadWeather) {
        List<ActivityEntity> pool;
        if (todayIsBadWeather) {
            pool = activityRepository.findByRegionAndVenueTypeAndStatus(region, VenueType.indoor, ActiveStatus.active);
            pool = new java.util.ArrayList<>(pool);
            pool.addAll(activityRepository.findByRegionAndVenueTypeAndStatus(region, VenueType.mixed, ActiveStatus.active));
        } else {
            pool = activityRepository.findByRegionAndStatus(region, ActiveStatus.active);
        }

        
        if (pool.isEmpty()) {
            pool = activityRepository.findByStatus(ActiveStatus.active);
        }

        if (keyword == null || keyword.isBlank()) {
            return pool.stream().limit(6).toList();
        }
        String kw = keyword.trim();
        List<ActivityEntity> results = pool.stream()
                .filter(a -> a.getTitle().contains(kw)
                        || (a.getStyleTag() != null && a.getStyleTag().contains(kw))
                        || (a.getDescription() != null && a.getDescription().contains(kw)))
                .limit(6)
                .toList();
        
        if (results.isEmpty()) {
            return pool.stream().limit(6).toList();
        }
        return results;
    }
}
