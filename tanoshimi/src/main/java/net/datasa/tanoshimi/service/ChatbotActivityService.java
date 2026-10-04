package net.datasa.tanoshimi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.dto.RecommendationDto;
import net.datasa.tanoshimi.domain.entity.*;
import net.datasa.tanoshimi.repository.ActivityRepository;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import net.datasa.tanoshimi.util.GeminiClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
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
    
    // [TNSM-72 추가제안] "또 추천해줘"를 눌러도 매번 같은 5개가 나오는 문제를 줄이기 위해,
    // 계획표(scheduleId)별로 최근에 보여준 activityId를 메모리에 잠깐 기억해둔다.
    // 서버 재시작하면 날아가는 가벼운 캐시라서 DB 스키마 변경 없이 적용 가능.
    private static final int RECENTLY_SHOWN_MAX = 15;
    private final Map<Long, Deque<Long>> recentlyShownByScheduleId = new ConcurrentHashMap<>();
    
    private Set<Long> recentlyShown(Long scheduleId) {
        if (scheduleId == null) return Set.of();
        Deque<Long> deque = recentlyShownByScheduleId.get(scheduleId);
        return deque == null ? Set.of() : new HashSet<>(deque);
    }
    
    private void recordShown(Long scheduleId, List<RecommendationDto> items) {
        if (scheduleId == null) return;
        Deque<Long> deque = recentlyShownByScheduleId.computeIfAbsent(scheduleId, k -> new ConcurrentLinkedDeque<>());
        for (RecommendationDto dto : items) {
            if (dto.getActivityId() != null) {
                deque.addLast(dto.getActivityId());
            }
        }
        while (deque.size() > RECENTLY_SHOWN_MAX) {
            deque.pollFirst();
        }
    }
    
    /**
     * [기존 호출부 방어용 오버로딩]
     * 정태웅님의 PlannerController가 깨지지 않도록 기존 4개짜리 파라미터 메서드를 남겨둡니다.
     */
    @Transactional
    public RecommendResult recommend(Long scheduleId, String region, LocalDate date, String keyword, String pastStyleTags, boolean todayIsBadWeather) {
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
        
        // [TNSM-72 추가제안] "또 추천해줘"를 눌러도 똑같은 5개가 반복해서 나오는 문제 완화.
        // 이 계획표(scheduleId)에서 최근에 보여준 activityId들은 일단 pool에서 빼고 고른다.
        // 다 빼고 나니 pool이 너무 작아지면(= 그 지역에 활동이 몇 개 안 됨) 다양성보다
        // "추천 자체가 비어버리는 것"이 더 나쁘니 원래 pool로 되돌린다.
        Set<Long> recentlyShownIds = recentlyShown(scheduleId);
        if (!recentlyShownIds.isEmpty()) {
            List<ActivityEntity> fresh = pool.stream()
                    .filter(a -> !recentlyShownIds.contains(a.getId()))
                    .toList();
            // [TNSM-72 수정] 원래 "5개 이상 안 겹치는 게 남아야만" 썼는데, 지금 데이터가
            // 지역당 3~5개뿐이라 이 조건이 항상 실패해서 다양성 캐시가 아예 작동을 안 했다.
            // 1개라도 안 본 게 남아있으면 그것부터 우선 보여주는 게 낫다 - fresh가 완전히
            // 0개일 때만(= 그 지역 활동을 전부 다 보여준 상태) 어쩔 수 없이 원래 풀로 되돌린다.
            if (!fresh.isEmpty()) {
                pool = fresh;
            }
        }
        
        // Return existing items if no keyword specified
        if (keyword == null || keyword.isBlank()) {
            // [TNSM-72] 이 경로는 AI를 전혀 호출하지 않는데도 컨트롤러에서 미리 크레딧을
            // 차감해버리고 있었다 - usedAi=false로 돌려줘서 컨트롤러가 환불하게 한다.
            List<RecommendationDto> items = pool.stream().limit(5)
                    .map(a -> new RecommendationDto("recommend", a.getId(), a.getTitle(), a.getDurationMin(), a.getPriceKrw(), a.getDescription(), null, null, null))
                    .toList();
            recordShown(scheduleId, items);
            return new RecommendResult(items, false);
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
                            "If using an existing item, set 'kind' to 'recommend', keeping its exact 'activityId', 'title', 'durationMin', and leave 'latitude', 'longitude', 'venueType' as null. " +
                            "If you invent a new web-sourced activity, set 'kind' to 'custom', 'activityId' to null, give it a good 'title' and 'durationMin', " +
                            "AND include its real-world 'latitude', 'longitude' (numbers, from Google Search) and 'venueType' " +
                            "(one of exactly: \"indoor\", \"outdoor\", \"mixed\" - so we can warn the user if the weather is bad for that day). " +
                            "Output ONLY a valid JSON array with keys: kind, activityId (number or null), title (string), durationMin (number), " +
                            "latitude (number or null), longitude (number or null), venueType (string or null). Strip markdown blocks.",
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
            
            // [TNSM-72] AI가 사실은 pool 안에 있는(=DB에 실제 존재하는) 장소인데도 'kind'를
            // "custom"으로 잘못 표시하면서 latitude/longitude를 안 채워주는 경우가 실제로 있었다
            // (예: "오다이바 야경"). 이러면 프론트의 confirmWeatherOk가 activityId도 좌표도 없는
            // 카드로 보고 날씨체크 자체를 건너뛰어버려서, 날씨가 나빠도 경고가 전혀 안 뜬다.
            // 그래서 'custom'인데 제목이 pool 안의 실제 항목과 같으면 'recommend'로 되돌려서
            // 진짜 activityId 기반 날씨체크(/api/weather/check)를 타게 한다.
            Map<String, ActivityEntity> poolByNormalizedTitle = pool.stream()
                    .collect(Collectors.toMap(a -> normalizeTitle(a.getTitle()), a -> a, (a, b) -> a));
            resp = resp.stream()
                    .map(dto -> {
                        if (!"custom".equals(dto.getKind())) return dto;
                        ActivityEntity matched = poolByNormalizedTitle.get(normalizeTitle(dto.getTitle()));
                        if (matched == null) return dto;
                        return new RecommendationDto("recommend", matched.getId(), matched.getTitle(),
                                matched.getDurationMin(), matched.getPriceKrw(), matched.getDescription(),
                                null, null, null);
                    })
                    .toList();
            
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
            
            // [TNSM-72] 프롬프트가 AI에게 요구하는 필드엔 priceKrw/description이 없어서,
            // kind="recommend"로 정상 응답된 항목도 Jackson이 priceKrw=0/description=null로
            // 채워버려 추천 카드에 가격·설명이 비어 보이는 버그였다. activityId는 바로 위에서
            // poolIds 안에 있는 것만 통과시켰으니 신뢰할 수 있어서, pool의 실제 값으로 채운다.
            Map<Long, ActivityEntity> poolById = pool.stream()
                    .collect(Collectors.toMap(ActivityEntity::getId, a -> a, (a, b) -> a));
            resp = resp.stream()
                    .map(dto -> {
                        if (!"recommend".equals(dto.getKind()) || dto.getActivityId() == null) return dto;
                        ActivityEntity real = poolById.get(dto.getActivityId());
                        if (real == null) return dto;
                        return new RecommendationDto(dto.getKind(), dto.getActivityId(), dto.getTitle(),
                                dto.getDurationMin(), real.getPriceKrw(), real.getDescription(),
                                dto.getLatitude(), dto.getLongitude(), dto.getVenueType());
                    })
                    .toList();
            
            recordShown(scheduleId, resp);
            return new RecommendResult(resp, true);
        } catch (Exception e) {
            // [TNSM-72] AI 호출/파싱이 실패해서 폴백으로 빠진 경우다 - usedAi=false로 돌려줘서
            // 컨트롤러가 미리 차감한 크레딧 1개를 환불하게 한다. 이전엔 이 경로로 빠져도
            // 크레딧은 이미 깎인 채로 끝나서, 사용자가 AI 추천을 못 받았는데도 크레딧만
            // 날아가는 문제가 있었다.
            log.error("AI recommendation parse error", e);
            recordShown(scheduleId, keywordMatchedFallback);
            return new RecommendResult(keywordMatchedFallback, false);
        }
    }
    
    /**
     * [TNSM-72] 컨트롤러가 "이번 호출이 진짜 AI 추천이었는지, 폴백이었는지"를 알아야
     * 실패 시 크레딧을 환불할 수 있어서 추가한 결과 래퍼.
     */
    public record RecommendResult(List<RecommendationDto> items, boolean usedAi) {}
    
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
                .map(a -> new RecommendationDto("recommend", a.getId(), a.getTitle(), a.getDurationMin(), a.getPriceKrw(), a.getDescription(), null, null, null))
                .toList();
    }
    
    /** [TNSM-72] 공백 제거 + 소문자화해서 "오다이바 야경"과 "오다이바야경" 같은 표기 차이를 흡수한다. */
    private String normalizeTitle(String title) {
        return title == null ? "" : title.replaceAll("\\s+", "").toLowerCase();
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
            
            // [TNSM-72] RealGeminiClient.ask()는 API 호출이 실패하면(429 한도초과, 빈 응답 등)
            // 빈 문자열이 아니라 {"briefing":...,"newSchedule":[]} 같은 안내용 JSON을 돌려준다.
            // isBlank()만 체크하면 이걸 못 걸러내서 VenueType.valueOf()가 IllegalArgumentException을
            // 던지고, 그게 바로 아래 catch에서 VenueType.mixed로 "영구 캐싱"돼버렸다 - cacheVenueType()
            // 이후엔 다시 판정하지 않으므로, API가 잠깐 막힌 순간에 생긴 액티비티는 진짜 outdoor여도
            // 평생 mixed로 고정되어 날씨 경고가 안 뜨는 버그였다. JSON 폴백 응답도 "API 응답 없음"과
            // 동일하게 취급해서 캐싱하지 않고 다음 요청 때 재판정하도록 한다.
            if (aiResponse == null || aiResponse.isBlank() || aiResponse.trim().startsWith("{")) {
                throw new IllegalStateException("API 응답 없음 또는 폴백 JSON: " + aiResponse);
            }
            
            String cleanResponse = aiResponse.trim().toLowerCase();
            // [TNSM-72] "Reply ONLY with the single word"라고 프롬프트에 써놔도 Gemini가
            // 종종 "Outdoor." 처럼 마침표를 붙이거나 "This place is OUTDOOR." 처럼 문장으로
            // 답하는 경우가 있다. VenueType.valueOf()는 완전히 똑같은 문자열만 허용해서
            // 이런 경우 전부 IllegalArgumentException이 나고, 그걸 mixed로 "영구 캐싱"해버리면
            // 실제로는 outdoor인 곳도 포맷이 조금만 어긋나면 평생 mixed로 고정되는 2차 버그가
            // 생긴다. valueOf 대신 포함(contains) 검사로 느슨하게 판정한다.
            VenueType judged = null;
            for (VenueType vt : VenueType.values()) {
                if (cleanResponse.contains(vt.name())) {
                    judged = vt;
                    break;
                }
            }
            if (judged == null) {
                throw new IllegalStateException("AI 응답에서 venueType을 못 찾음: " + cleanResponse);
            }
            activity.cacheVenueType(judged);
            activityRepository.save(activity);
        } catch (Exception e) {
            // [TNSM-72] 판정 실패는 "mixed가 맞다"는 뜻이 아니다. 여기서 캐싱해버리면
            // cacheVenueType() 이후엔 재판정을 안 하므로, 아예 캐싱하지 않고 넘어가서
            // 다음 호출(API가 정상일 때) 때 다시 판정하도록 한다(needsVenueTypeJudgement()가
            // 계속 true로 남음).
            log.error("AI 호출/파싱 중 오류 발생. 장소: {}", activity.getTitle(), e);
        }
    }
}