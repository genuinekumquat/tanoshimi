package net.datasa.tanoshimi.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.ActiveStatus;
import net.datasa.tanoshimi.domain.entity.ActivityEntity;
import net.datasa.tanoshimi.domain.entity.VenueType;
import net.datasa.tanoshimi.repository.ActivityRepository;
import org.springframework.stereotype.Service;

/**
 * 계획표 화면의 AI 챗봇 액티비티 추천.
 * "AI가 추천" 과 "검색으로 추천받기" 둘 다 이 서비스를 거친다 - 차이는 keyword 유무뿐이다.
 */
@Service
@RequiredArgsConstructor
public class ChatbotActivityService {

    private final ActivityRepository activityRepository;

    /** 날씨 및 키워드 기반 맞춤 추천 */
    public List<ActivityEntity> recommend(String region, LocalDate date, String keyword, boolean todayIsBadWeather) {
        List<ActivityEntity> pool = new ArrayList<>();
        
        // 날씨가 안 좋으면 실내(indoor)와 실내외혼합(mixed)만 먼저 가져옴
        if (todayIsBadWeather) {
            pool.addAll(activityRepository.findByRegionAndVenueTypeAndStatus(region, VenueType.indoor, ActiveStatus.active));
            pool.addAll(activityRepository.findByRegionAndVenueTypeAndStatus(region, VenueType.mixed, ActiveStatus.active));
        } else {
            // 날씨 좋으면 전부 다 
            pool.addAll(activityRepository.findByRegionAndStatus(region, ActiveStatus.active));
            // 맑은 날엔 야외 액티비티 가중치(앞에 배치되도록 섞기 전 약간의 정렬 혹은 그냥 전체 포함)
        }

        // 해당 지역에 데이터가 없으면 전 지역에서라도 가져옴
        if (pool.isEmpty()) {
            pool.addAll(activityRepository.findByStatus(ActiveStatus.active));
        }

        // 추천을 요청할 때마다 다양한 결과가 나오도록 셔플
        Collections.shuffle(pool);

        if (keyword == null || keyword.isBlank()) {
            // 키워드가 없으면 랜덤하게 6개 제안
            return pool.stream().limit(6).toList();
        }
        
        String kw = keyword.toLowerCase().trim();
        List<ActivityEntity> results = pool.stream()
                .filter(a -> (a.getTitle() != null && a.getTitle().toLowerCase().contains(kw))
                        || (a.getStyleTag() != null && a.getStyleTag().toLowerCase().contains(kw))
                        || (a.getDescription() != null && a.getDescription().toLowerCase().contains(kw)))
                .limit(6)
                .toList();
        
        // 검색 결과가 아예 없으면 기본 랜덤 리스트라도 반환
        if (results.isEmpty()) {
            return pool.stream().limit(6).toList();
        }
        return results;
    }
}
