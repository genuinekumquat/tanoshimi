package net.datasa.tanoshimi.service;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.ActiveStatus;
import net.datasa.tanoshimi.domain.entity.ActivityEntity;
import net.datasa.tanoshimi.domain.entity.VenueType;
import net.datasa.tanoshimi.repository.ActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계획표 화면의 AI 챗봇 액티비티 추천.
 * "AI가 추천" 과 "검색으로 추천받기" 둘 다 이 서비스를 거친다 - 차이는 keyword 유무뿐이다.
 *
 * <p>날씨가 나쁜 날은 indoor 위주로, 좋은 날은 outdoor 를 우선 노출한다
 * (mixed 는 항상 포함). 실제로는 여기서 LLM 을 호출해 자연어 질의를 해석하지만,
 * 지금은 keyword 를 제목/스타일태그에 단순 포함 검색으로 대체했다.
 * TODO(다음 단계): 실제 LLM 기반 자연어 추천으로 교체, 임베딩 유사도 검색 도입.
 *
 * <p>[v16 신규] venue_type 이 아직 판정되지 않은(null) 액티비티가 추천 후보에 섞여 있으면,
 * "AI가 처음 조회될 때 판정 후 캐싱"(필드제약조건 확정 사항)하고, 이후 조회는 캐시값을
 * 재사용한다 - judgeAndCacheVenueType() 참고.
 */
@Service
@RequiredArgsConstructor
public class ChatbotActivityService {

    private final ActivityRepository activityRepository;
    private final WeatherAdvisorService weatherAdvisorService;

    /** 날씨 기반 추천. keyword 가 있으면 추가로 필터링(=검색 추천 겸용). */
    @Transactional
    public List<ActivityEntity> recommend(String region, LocalDate date, String keyword, boolean todayIsBadWeather) {
        // venue_type 미판정 액티비티가 있으면 이 시점에 전부 판정해서 캐싱해둔다 -
        // 그래야 아래 findByRegionAndVenueTypeAndStatus 필터링에 걸리기 시작한다.
        activityRepository.findByRegionAndStatus(region, ActiveStatus.active).stream()
                .filter(ActivityEntity::needsVenueTypeJudgement)
                .forEach(this::judgeAndCacheVenueType);

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

    /**
     * [v16 신규] "AI가 실내/실외/혼합을 판정"하는 부분 - 실제로는 여기서 LLM/분류 모델을
     * 호출해야 하지만, 지금은 style_tag 키워드 기반 간단한 규칙으로 대체한다(추천 로직
     * 전체가 아직 LLM 미연동인 것과 같은 수준의 임시 구현). 판정 결과는 즉시 캐싱되어
     * 다음 조회부터는 재판정하지 않는다.
     */
    private void judgeAndCacheVenueType(ActivityEntity activity) {
        String tag = activity.getStyleTag() == null ? "" : activity.getStyleTag();
        String desc = activity.getDescription() == null ? "" : activity.getDescription();
        String haystack = (tag + " " + desc + " " + activity.getTitle());

        VenueType judged;
        if (containsAny(haystack, "박물관", "미술관", "실내", "카페", "온천", "쇼핑", "수족관")) {
            judged = VenueType.indoor;
        } else if (containsAny(haystack, "해변", "산", "공원", "산책", "야경", "등산", "야외")) {
            judged = VenueType.outdoor;
        } else {
            judged = VenueType.mixed;
        }
        activity.cacheVenueType(judged);
        activityRepository.save(activity);
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }
}
