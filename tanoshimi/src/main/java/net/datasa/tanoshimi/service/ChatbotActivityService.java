package net.datasa.tanoshimi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.entity.ActiveStatus;
import net.datasa.tanoshimi.domain.entity.ActivityEntity;
import net.datasa.tanoshimi.domain.entity.VenueType;
import net.datasa.tanoshimi.repository.ActivityRepository;
import net.datasa.tanoshimi.util.GeminiClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 누락되었던 import 추가 완료

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotActivityService {
    
    private final ActivityRepository activityRepository;
    private final WeatherAdvisorService weatherAdvisorService;
    private final GeminiClient geminiClient; // MockGeminiClient가 자동으로 주입됨
    
    /**
     * [기존 호출부 방어용 오버로딩]
     * 정태웅님의 PlannerController가 깨지지 않도록 기존 4개짜리 파라미터 메서드를 남겨둡니다.
     */
    @Transactional
    public List<ActivityEntity> recommend(String region, LocalDate date, String keyword, boolean todayIsBadWeather) {
        return recommend(region, date, keyword, null, todayIsBadWeather);
    }
    
    /** 실제 로직을 수행하는 5개 파라미터 메서드 */
    @Transactional
    public List<ActivityEntity> recommend(String region, LocalDate date, String keyword, String pastStyleTags, boolean todayIsBadWeather) {
        activityRepository.findByRegionAndStatus(region, ActiveStatus.active).stream()
                .filter(ActivityEntity::needsVenueTypeJudgement)
                .forEach(this::judgeAndCacheVenueType);
        
        List<ActivityEntity> pool;
        if (todayIsBadWeather) {
            pool = activityRepository.findByRegionAndVenueTypeAndStatus(region, VenueType.indoor, ActiveStatus.active);
            pool = new ArrayList<>(pool);
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
        
        String extractedTag = extractCoreTagWithGemini(keyword, pastStyleTags);
        
        List<ActivityEntity> results = pool.stream()
                .filter(a -> a.getTitle().contains(extractedTag)
                        || (a.getStyleTag() != null && a.getStyleTag().contains(extractedTag))
                        || (a.getDescription() != null && a.getDescription().contains(extractedTag)))
                .limit(6)
                .toList();
        
        if (results.isEmpty()) {
            return pool.stream().limit(6).toList();
        }
        return results;
    }
    
    private String extractCoreTagWithGemini(String keyword, String pastStyleTags) {
        try {
            String prompt = String.format(
                    "You are a travel assistant. User request: '%s'. Past tags: '%s'. " +
                            "Extract the single most important Korean search keyword. Reply ONLY with the single keyword.",
                    keyword, pastStyleTags != null ? pastStyleTags : "None"
            );
            
            String aiResponse = geminiClient.ask(prompt); // Mock 객체가 응답
            
            if (aiResponse == null || aiResponse.isBlank()) return keyword.trim();
            return aiResponse.trim();
        } catch (Exception e) {
            log.error("제미나이 파싱 오류", e);
            return keyword.trim();
        }
    }
    
    private void judgeAndCacheVenueType(ActivityEntity activity) {
        if (activity.getVenueType() != null) return;
        
        try {
            String prompt = String.format(
                    "Classify this place as exactly one of the following: INDOOR, OUTDOOR, or MIXED. " +
                            "Place: '%s'. Reply ONLY with the single word.",
                    activity.getTitle()
            );
            
            String aiResponse = geminiClient.ask(prompt); // Mock 객체가 응답
            
            if (aiResponse == null || aiResponse.isBlank()) {
                throw new IllegalStateException("API 응답 없음");
            }
            
            String cleanResponse = aiResponse.trim().toUpperCase();
            activity.cacheVenueType(VenueType.valueOf(cleanResponse));
            activityRepository.save(activity);
        } catch (IllegalArgumentException e) {
            activity.cacheVenueType(VenueType.mixed);
            activityRepository.save(activity);
        } catch (Exception e) {
            log.error("AI 호출 중 오류 발생. 장소: {}", activity.getTitle(), e);
        }
    }
}