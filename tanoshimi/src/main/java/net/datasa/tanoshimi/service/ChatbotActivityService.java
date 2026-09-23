package net.datasa.tanoshimi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.entity.ActiveStatus;
import net.datasa.tanoshimi.domain.entity.ActivityEntity;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PartyStatus;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.domain.entity.VenueType;
import net.datasa.tanoshimi.repository.ActivityRepository;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import net.datasa.tanoshimi.util.GeminiClient;
import net.datasa.tanoshimi.domain.dto.RecommendationDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 누락되었던 import 추가 완료

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotActivityService {
    
    private final ActivityRepository activityRepository;
    private final WeatherAdvisorService weatherAdvisorService;
    private final GeminiClient geminiClient; // MockGeminiClient가 자동으로 주입됨
    private final PartyMemberRepository partyMemberRepository; // [TNSM-18] 활동이력 조회용
    
    /**
     * [기존 호출부 방어용 오버로딩]
     * 정태웅님의 PlannerController가 깨지지 않도록 기존 4개짜리 파라미터 메서드를 남겨둡니다.
     */
    @Transactional
    public List<RecommendationDto> recommend(String region, LocalDate date, String keyword, String pastStyleTags, boolean todayIsBadWeather) {
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
        
        // Return existing items if no keyword specified
        if (keyword == null || keyword.isBlank()) {
            return pool.stream().limit(5).map(a -> new RecommendationDto("recommend", a.getId(), a.getTitle(), a.getDurationMin(), a.getPriceKrw(), a.getDescription())).toList();
        }
        
        try {
            StringBuilder poolContext = new StringBuilder();
            pool.stream().limit(20).forEach(a -> {
                poolContext.append(String.format("ID:%d, Title:%s, Duration:%d min, Desc:%s\n", a.getId(), a.getTitle(), a.getDurationMin(), a.getDescription()));
            });
            
            // [TNSM-50] region/pastStyleTags/date를 프롬프트에 실제로 반영 (기존엔 keyword만 사용해 무시되고 있었음)
            String safeTags = (pastStyleTags != null && !pastStyleTags.isBlank()) ? pastStyleTags : "None";
            
            String prompt = String.format(
                    "You are a helpful travel planner for the region '%s'. " +
                            "User request: '%s'. " +
                            "User's travel style and companions: '%s'. " +
                            "Travel Date: %s. " +
                            "Return a JSON array of exactly 5 recommended schedule items tailored to the user's style, companions, and region. " +
                            "Requirement: 2 or 3 items MUST be the most famous, representative must-visit spots. " +
                            "The remaining 2 or 3 items MUST be creative, varied, lesser-known, or unique spots that rotate randomly so if I ask again, I get different suggestions! " +
                            "Use Google Search to find real tourist information for this region. " +
                            "You can pick from these existing DB items if relevant:\n%s\n" +
                            "If using an existing item, set 'kind' to 'recommend', keeping its exact 'activityId', 'title', 'durationMin'. " +
                            "If you invent a new web-sourced activity, set 'kind' to 'custom', 'activityId' to null, and give it a good 'title' and 'durationMin'. " +
                            "Output ONLY a valid JSON array with keys: kind, activityId (number or null), title (string), durationMin (number). Strip markdown blocks.",
                    region, keyword, safeTags, date != null ? date.toString() : "Unknown", poolContext.toString()
            );
            
            // 수정 후
            String aiResponse = geminiClient.ask(prompt);
            String jsonRaw = aiResponse == null ? "" : aiResponse.trim();

            // 응답 전체가 진짜 배열(예: "[...]")로 시작하는 경우만 신뢰한다.
            // {"briefing":..., "newSchedule": []} 같은 "에러를 감싼 객체" 안의 []는
            // 진짜 추천 배열이 아니므로 여기서 걸러내고 바로 폴백으로 보낸다.
            if (!jsonRaw.startsWith("[")) {
                throw new IllegalStateException("AI 응답이 배열 형식이 아님: " + jsonRaw);
            }
            
            ObjectMapper mapper = new ObjectMapper();
            List<RecommendationDto> resp = mapper.readValue(jsonRaw, new TypeReference<List<RecommendationDto>>() {});
            return resp;
        } catch (Exception e) {
            log.error("AI recommendation parse error", e);
            return pool.stream().limit(5).map(a -> new RecommendationDto("recommend", a.getId(), a.getTitle(), a.getDurationMin(), a.getPriceKrw(), a.getDescription())).toList();
        }
    }
    
    /**
     * [TNSM-18] 활동 이력 기반 추천 - 사용자가 완료한 파티들의 style_tag 를 모아서
     * 콤마로 이어붙인 문자열로 만든다. PlannerController.recommend() 의 pastStyleTags 로 넘길 용도.
     *
     * <p>PartyMemberRepository.findPartiesByUserAndStatus() 는 [⑥ 마이페이지] 히트맵·칭호
     * 집계용으로 이미 있던 것을 그대로 재사용한다. v16 제안서 기준, 예약이력이 아닌
     * 완료된 파티 참여 이력의 스타일 태그를 반영한다.
     *
     * <p>완료한 파티가 없거나 style_tag 가 전부 비어있으면 null 을 반환한다
     * (recommend() 쪽에서 null 이면 "None"으로 안전하게 처리됨).
     */
    public String buildPastStyleTags(UserEntity user) {
        List<PartyEntity> completedParties =
                partyMemberRepository.findPartiesByUserAndStatus(user, PartyStatus.completed);
        
        Set<String> tags = completedParties.stream()
                .map(PartyEntity::getStyleTag)
                .filter(tag -> tag != null && !tag.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new)); // 중복 제거 + 순서 유지
        
        if (tags.isEmpty()) {
            return null;
        }
        return String.join(", ", tags);
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
            
            String cleanResponse = aiResponse.trim().toLowerCase();
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