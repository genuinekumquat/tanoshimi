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
import java.util.Comparator;
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
        
        // [TNSM-62] 키워드 기반 로컬 폴백 - 항상 먼저 계산해 둔다.
        // Gemini 결제 크레딧이 없어 폴백으로 빠지는 경우(또는 Mock이 입력과 무관한 고정 응답을
        // 주는 경우)에도, 적어도 사용자가 입력한 단어와 관련된 장소부터 보여주기 위함이다.
        // (docs/known-issue-ai-api-keys-and-tami.md 의 B-1 제안 반영)
        List<RecommendationDto> keywordMatchedFallback = keywordMatchedFallback(pool, keyword);
        Set<Long> poolIds = pool.stream().map(ActivityEntity::getId).collect(Collectors.toSet());
        
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
            
            // [TNSM-62] 응답 검증 - "recommend" 항목인데 activityId가 이번 조회의 pool(지역·날씨
            // 필터링된 범위) 밖을 가리키면 신뢰할 수 없는 응답으로 보고 폴백으로 보낸다.
            // MockGeminiClient는 입력과 무관하게 오사카 ID(1,2,3)를 고정 반환하므로, 다른 지역을
            // 조회하면 이 검증에서 걸러져 키워드 매칭 폴백으로 넘어간다 - 그래야 "뭘 물어봐도
            // 똑같은 답"이 아니라 최소한 입력/지역에 맞는 답이 나온다.
            boolean hasOutOfPoolRecommend = resp.stream()
                    .filter(dto -> "recommend".equals(dto.getKind()))
                    .anyMatch(dto -> dto.getActivityId() == null || !poolIds.contains(dto.getActivityId()));
            if (hasOutOfPoolRecommend) {
                throw new IllegalStateException("AI 응답이 현재 조회 범위(지역/날씨) 밖의 activityId를 포함함");
            }
            
            return resp;
        } catch (Exception e) {
            log.error("AI recommendation parse error", e);
            return keywordMatchedFallback;
        }
    }
    
    /**
     * [TNSM-62] 키워드 ↔ DB 관광지 이름/설명 매칭 폴백.
     * Gemini를 못 쓰는 상황(크레딧 소진·Mock)에서도 "뭘 물어보든 같은 5개"가 되지 않도록,
     * 사용자가 입력한 단어가 제목/설명/스타일태그에 들어있는 항목을 앞으로 올린다.
     * 매칭되는 게 하나도 없으면 기존 동작대로 pool 순서 그대로 앞 5개를 보여준다.
     */
    private List<RecommendationDto> keywordMatchedFallback(List<ActivityEntity> pool, String keyword) {
        // 키워드를 공백/쉼표 기준으로 나눠 토큰화 - "라멘 먹으러 가고싶어" 같은 문장에서도
        // "라멘" 토큰이 활동 설명에 포함되면 매칭되게 한다.
        String[] tokens = keyword.trim().toLowerCase().split("[\\s,]+");
        
        return pool.stream()
                .sorted(Comparator.comparingInt((ActivityEntity a) -> -matchScore(a, tokens)))
                .limit(5)
                .map(a -> new RecommendationDto("recommend", a.getId(), a.getTitle(), a.getDurationMin(), a.getPriceKrw(), a.getDescription()))
                .toList();
    }
    
    private int matchScore(ActivityEntity a, String[] tokens) {
        String haystack = ((a.getTitle() != null ? a.getTitle() : "") + " "
                + (a.getDescription() != null ? a.getDescription() : "") + " "
                + (a.getStyleTag() != null ? a.getStyleTag() : "")).toLowerCase();
        int score = 0;
        for (String token : tokens) {
            if (!token.isBlank() && haystack.contains(token)) {
                score++;
            }
        }
        return score;
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