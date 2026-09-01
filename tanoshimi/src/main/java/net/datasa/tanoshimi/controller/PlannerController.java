package net.datasa.tanoshimi.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.RecommendationDto;
import net.datasa.tanoshimi.domain.dto.ScheduleItemRequest;
import net.datasa.tanoshimi.domain.dto.ScheduleItemView;
import net.datasa.tanoshimi.domain.entity.*;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import net.datasa.tanoshimi.repository.TripScheduleItemRepository;
import net.datasa.tanoshimi.repository.TripScheduleRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.*;
import net.datasa.tanoshimi.util.GeminiClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Planner page and REST API.
 * Uses REST API for CRUD operations, and broadcasts mapping via WebSocket.
 */
@Controller
@RequiredArgsConstructor
public class PlannerController {

    private final TripScheduleRepository scheduleRepository;
    private final TripScheduleItemRepository itemRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final UserRepository userRepository;
    private final TripPlannerService plannerService;
    private final TripPlannerLockService lockService;
    private final ChatbotActivityService chatbotActivityService;
    private final WeatherAdvisorService weatherAdvisorService;
    private final AiCreditService aiCreditService;
    private final RouteOptimizationService routeOptimizationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final GeminiClient geminiClient;
	
    @GetMapping("/planner/{scheduleId}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public String planner(@PathVariable Long scheduleId, @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        TripScheduleEntity schedule = getScheduleWithContext(scheduleId);
        TourEntity tour = schedule.getReservation() != null ? schedule.getReservation().getTour() : null;

        boolean isOwner = principal != null && schedule.getParty() != null
                && schedule.getParty().getOwner().getId().equals(principal.getId());
        boolean isLockHolder = principal != null && schedule.isLockedBy(principal.getId());
        // 편집권 보유자 표시용 - lockedBy 가 비어있는 레거시 데이터는 파티장을 기본값으로 보여준다.
        UserEntity effectiveLockHolder = schedule.getLockedBy() != null ? schedule.getLockedBy()
                : (schedule.getParty() != null ? schedule.getParty().getOwner() : null);

        model.addAttribute("schedule", schedule);
        model.addAttribute("tour", tour);
        model.addAttribute("items", plannerService.getItems(schedule));
        model.addAttribute("isPartyOwner", isOwner);
        model.addAttribute("isLockHolder", isLockHolder);
        model.addAttribute("lockedByUserId", effectiveLockHolder != null ? effectiveLockHolder.getId() : null);
        model.addAttribute("lockedByName", effectiveLockHolder != null ? effectiveLockHolder.getName() : null);
        // [방장 전용 메뉴] 편집권을 넘길 수 있는 대상(방장 본인 제외) 목록
        if (isOwner) {
            model.addAttribute("partyMembers", partyMemberRepository.findByParty(schedule.getParty()).stream()
                    .filter(m -> !m.getUser().getId().equals(principal.getId()))
                    .map(PartyMemberEntity::getUser)
                    .toList());
        }
        model.addAttribute("aiCreditRemaining", principal == null ? 0
                : aiCreditService.remaining(userRepository.findById(principal.getId()).orElse(null)));
        return "planner/index";
    }

    @GetMapping("/api/planner/{scheduleId}/items")
    @ResponseBody
    public ApiResponse<List<ScheduleItemView>> items(@PathVariable Long scheduleId) {
        return ApiResponse.ok(plannerService.getItems(getSchedule(scheduleId)));
    }

    @PostMapping("/api/planner/{scheduleId}/items")
    @ResponseBody
    public ApiResponse<Long> addItem(@PathVariable Long scheduleId, @Valid @RequestBody ScheduleItemRequest request,
                                     @AuthenticationPrincipal CustomUserDetails principal) {
        TripScheduleEntity schedule = getSchedule(scheduleId);
        UserEntity user = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Long itemId = plannerService.addItem(schedule.getId(), user, request);
        try { broadcast(scheduleId); } catch(Exception ignored) {}
        return ApiResponse.ok(itemId);
    }

    /** Resize / move functionality */
    @PatchMapping("/api/planner/items/{itemId}")
    @ResponseBody
    public ApiResponse<Void> resize(@PathVariable Long itemId, @RequestParam int startMinute,
                                    @RequestParam int durationMinute, @RequestParam(required=false) Integer dayIndex,
                                    @AuthenticationPrincipal CustomUserDetails principal) {
        plannerService.resizeItem(itemId, principal.getId(), startMinute, durationMinute, dayIndex);
        return ApiResponse.okMessage("변경되었습니다.");
    }

    @DeleteMapping("/api/planner/items/{itemId}")
    @ResponseBody
    public ApiResponse<Void> remove(@PathVariable Long itemId, @AuthenticationPrincipal CustomUserDetails principal) {
        plannerService.removeItem(itemId, principal.getId());
        return ApiResponse.okMessage("삭제되었습니다.");
    }

    // ===================== [v16 신규] 편집권(lock) =====================

    @PostMapping("/api/planner/{scheduleId}/lock/{targetUserId}")
    @ResponseBody
    public ApiResponse<Void> grantLock(@PathVariable Long scheduleId, @PathVariable Long targetUserId,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity owner = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        lockService.grantLock(scheduleId, owner, targetUserId);
        broadcast(scheduleId);
        return ApiResponse.okMessage("편집권을 넘겼습니다.");
    }

    @DeleteMapping("/api/planner/{scheduleId}/lock")
    @ResponseBody
    public ApiResponse<Void> revokeLock(@PathVariable Long scheduleId, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity owner = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        lockService.revokeLock(scheduleId, owner);
        broadcast(scheduleId);
        return ApiResponse.okMessage("편집권을 회수했습니다. 이제 파티장만 편집할 수 있습니다.");
    }

    // ===================== [v16 신규] 자동/수동 저장 · 스냅샷 롤백 =====================

    /** trigger=manual(수동 저장 버튼) 또는 trigger=auto(프론트에서 10분마다 자동 호출). */
    @PostMapping("/api/planner/{scheduleId}/save")
    @ResponseBody
    public ApiResponse<Void> save(@PathVariable Long scheduleId, @RequestParam(defaultValue = "manual") String trigger,
                                  @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity actor = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        lockService.save(scheduleId, actor, SnapshotTrigger.valueOf(trigger));
        return ApiResponse.okMessage("저장되었습니다.");
    }

    @GetMapping("/api/planner/{scheduleId}/snapshots")
    @ResponseBody
    public ApiResponse<List<net.datasa.tanoshimi.domain.dto.SnapshotSummaryView>> snapshots(@PathVariable Long scheduleId) {
        List<net.datasa.tanoshimi.domain.dto.SnapshotSummaryView> views = lockService.listSnapshots(scheduleId).stream()
                .map(s -> new net.datasa.tanoshimi.domain.dto.SnapshotSummaryView(
                        s.getId(), s.getTriggerType().name(), s.getCreatedBy().getName(), s.getCreatedAt().toString()))
                .toList();
        return ApiResponse.ok(views);
    }

    @PostMapping("/api/planner/{scheduleId}/snapshots/{snapshotId}/rollback")
    @ResponseBody
    public ApiResponse<Void> rollback(@PathVariable Long scheduleId, @PathVariable Long snapshotId,
                                      @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity owner = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        lockService.rollback(scheduleId, snapshotId, owner);
        broadcast(scheduleId);
        return ApiResponse.okMessage("선택한 시점으로 되돌렸습니다.");
    }

    // ===================== [v16 신규] AI 동선 최적화 =====================

    @PostMapping("/api/planner/{scheduleId}/optimize-route")
    @ResponseBody
    public ApiResponse<Void> optimizeRoute(@PathVariable Long scheduleId, @RequestParam int dayIndex,
                                           @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity user = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!aiCreditService.tryConsume(user)) {
            throw new BusinessException(ErrorCode.AI_CREDIT_EXCEEDED);
        }
        routeOptimizationService.optimizeDay(getSchedule(scheduleId), (byte) dayIndex);
        broadcast(scheduleId);
        return ApiResponse.okMessage("동선을 최적화했습니다.");
    }

    /** AI Recommendation API */
    @GetMapping("/api/planner/{scheduleId}/recommend")
    @ResponseBody
    public ApiResponse<List<RecommendationDto>> recommend(@PathVariable Long scheduleId,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String region,
                                                        @RequestParam String date,
                                                        @AuthenticationPrincipal CustomUserDetails principal) {
        // [v16 신규] AI 추천은 크레딧을 소모한다 - 전원 동일한 1일 총량, 자정 초기화.
        UserEntity requester = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!aiCreditService.tryConsume(requester)) {
            throw new BusinessException(ErrorCode.AI_CREDIT_EXCEEDED);
        }
        TripScheduleEntity schedule = getScheduleWithContext(scheduleId);
        TourEntity tour = schedule.getReservation() != null ? schedule.getReservation().getTour() : null;
        String targetRegion = tour != null ? tour.getRegion() : region;
        if (targetRegion == null || targetRegion.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지역을 선택해주세요.");
        }
        LocalDate d = LocalDate.parse(date);

        boolean badWeather = false;
        if (tour != null && tour.getLatitude() != null) {
            var advice = weatherAdvisorService.adviseForTour(tour, d);
            badWeather = !advice.recommend();
        }
        return ApiResponse.ok(chatbotActivityService.recommend(targetRegion, d, keyword, "", badWeather));
    }

    // ===================== [신규] AI 일정 검증 =====================

    @PostMapping("/api/planner/{scheduleId}/ai-validate")
    @ResponseBody
    public ApiResponse<Object> aiValidate(@PathVariable Long scheduleId, @RequestParam(defaultValue = "대중교통") String mode,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity user = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!aiCreditService.tryConsume(user)) {
            throw new BusinessException(ErrorCode.AI_CREDIT_EXCEEDED);
        }
        
        TripScheduleEntity schedule = getScheduleWithContext(scheduleId);
        List<ScheduleItemView> items = plannerService.getItems(schedule);
        
        if (items.isEmpty()) {
            return ApiResponse.ok(java.util.Map.of("briefing", "일정이 비어 있습니다. 항목을 추가한 후 검증해 주세요."));
        }
        
        // [v16 신규] AI 검증 전 시간표(ai_valid) 임시저장
        lockService.save(scheduleId, user, net.datasa.tanoshimi.domain.entity.SnapshotTrigger.ai_valid);
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("다음은 여행자의 현재 일정표입니다. 사용자는 '").append(mode).append("'단어를 참고하여 이동합니다.\n");
        prompt.append("각 일정 항목의 시간과 장소를 분석하여, 선택한 교통수단(자동차 또는 대중교통)으로 물리적으로나 시간상으로 이동이 불가능한 경우가 있는지 검증해주세요.\n");
        prompt.append("만약 불가능하다면, 'A와 B 둘 다 불가능합니다. A를 가시겠습니까, B를 가시겠습니까?'와 같이 선택지를 제시하고, 각각의 선택지에 따른 장단점을 비교하는 브리핑을 작성해주세요.\n");
        prompt.append("매일의 1일차, 2일차 등 가장 이른 첫 번째 출발 스케줄 직전에 '호텔 출발' 이라는 일정을 짧게 꼭 추가해줘. 단, 하루가 끝날 때 돌아가는 일정은 만들지 마라.\n");
        prompt.append("참고로, 'source'가 'package_default'인 항목(예: 비행기 시간, 체크인)은 절대 변경 불가능한 요소로 간주하세요.\n\n");
        prompt.append("추가로, 일정 중간에 활동이 없는 빈 시간이 길게 비어있다면, 그 시간대와 동선을 고려해 짧게 즐길 수 있는 추천 활동(유명 카페, 간식, 산책로 등)을 검색하여 일정 브리핑에 꼭 포함해주세요.\n\n");
        prompt.append("결과물은 반드시 Markdown을 포함하지 않은 순수 JSON 포맷으로 작성해주세요.\n");
        prompt.append("{\"briefing\": \"브리핑 내용\", \"newSchedule\": [{\"dayIndex\": 1, \"startMinute\": 720, \"durationMinute\": 60, \"title\": \"...\", \"source\": \"...\", \"activityId\": null}, ...]}\n");
        prompt.append("newSchedule 배열은 기존 일정을 대체할 새로운 추천 일정 전체 리스트입니다. 주의사항: JSON의 키 값이나 구조를 절대 바꾸지 마세요.\n\n");
        prompt.append("【일정표 데이터】\n");
        
        for (ScheduleItemView item : items) {
            String time = String.format("%02d:%02d ~ %02d:%02d", 
                    item.startMinute() / 60, item.startMinute() % 60,
                    (item.startMinute() + item.durationMinute()) / 60,
                    (item.startMinute() + item.durationMinute()) % 60);
            boolean isFixed = "package_default".equals(item.source());
            prompt.append(String.format("- %d일차 | 시간: %s | 제목: %s | 고정여부: %b | id: %d | source: %s\n",
                    item.dayIndex(), time, item.title() + " (Memo:" + item.memo() + ")", isFixed, item.id(), item.source()));
        }
        
        String responseText = geminiClient.ask(prompt.toString());
        int startIndex = responseText.indexOf("{");
        int endIndex = responseText.lastIndexOf("}");
        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            responseText = responseText.substring(startIndex, endIndex + 1);
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(responseText);
            String briefing = root.path("briefing").asText();
            com.fasterxml.jackson.databind.JsonNode newSched = root.path("newSchedule");
            
            List<TripScheduleItemEntity> allItems = itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule);
            for (TripScheduleItemEntity it : allItems) {
                if (!it.isFixed()) {
                    itemRepository.delete(it);
                }
            }
            itemRepository.flush();
            
            if (newSched != null && newSched.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : newSched) {
                    if ("package_default".equals(node.path("source").asText())) {
                        continue;
                    }
                    String nodeSource = node.path("source").asText("custom");
                    Long aid = node.path("activityId").isNull() ? null : node.path("activityId").asLong();
                    if ("custom".equals(nodeSource)) {
                        aid = null;
                    } else if (aid != null) {
                        Long mappedAid = null;
                        for (TripScheduleItemEntity it : allItems) {
                            if (it.getId().equals(aid) && it.getActivity() != null) {
                                mappedAid = it.getActivity().getId();
                                break;
                            }
                        }
                        aid = mappedAid;
                    }

                    ScheduleItemRequest req = new ScheduleItemRequest(
                            node.path("dayIndex").asInt(),
                            node.path("startMinute").asInt(),
                            node.path("durationMinute").asInt(),
                            aid,
                            node.path("title").asText(),
                            node.path("memo").isNull() ? null : node.path("memo").asText(), node.path("color").isNull() ? null : node.path("color").asText()
                    );
                    plannerService.addItem(scheduleId, user, req);
                }
            }
            
            broadcast(scheduleId);
            return ApiResponse.ok(java.util.Map.of("briefing", briefing));
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.ok(java.util.Map.of("briefing", "응답을 처리하는 중 오류가 발생했습니다: " + e.getMessage() + "\n\n원본:\n" + responseText));
        }
    }

    @PostMapping("/api/planner/{scheduleId}/submit")
    @ResponseBody
    public ApiResponse<Void> submit(@PathVariable Long scheduleId) {
        plannerService.submitForPayment(getSchedule(scheduleId));
        broadcast(scheduleId);
        return ApiResponse.okMessage("계획표가 최종 확정되었습니다.");
    }

    @PostMapping("/api/planner/{scheduleId}/pay")
    @ResponseBody
    public ApiResponse<Void> pay(@PathVariable Long scheduleId, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity payer = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        plannerService.pay(getSchedule(scheduleId), payer);
        return ApiResponse.okMessage("결제가 완료되었습니다.");
    }

    @GetMapping("/planner/{scheduleId}/report")
    public String report(@PathVariable Long scheduleId, Model model, @RequestParam(defaultValue = "public") String mode) {
        TripScheduleEntity schedule = getScheduleWithContext(scheduleId);
        java.util.List<TripScheduleItemEntity> items = itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule);
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("다음 여행 일정의 각 일정 간 이동 방법(").append("public".equals(mode) ? "대중교통" : "자동차").append(" 기준), 구글맵 기준 예상 소요 시간 등을 포함한 상세하고 친절한 여행 플랜 리포트를 작성해 줘.\n");
        prompt.append("가장 중요한 조건: 답변은 절대로 마크다운(```html 등)을 포함하지 말고, 순수한 HTML 태그(<h1>, <table>, <p>, <ul>, <b> 등)로만 이루어진 내용 스니펫만 반환해 줘. <html>이나 <head>, <body> 같은 전체 문서 구조는 제외하고 내용물만 작성해.\n\n");
        
        prompt.append("【일정 데이터】\n");
        for (TripScheduleItemEntity item : items) {
            String startTime = String.format("%02d:%02d", item.getStartMinute() / 60, item.getStartMinute() % 60);
            prompt.append(String.format("- %d일차 %s : %s", item.getDayIndex() + 1, startTime, item.getTitle()));
            if (item.getActivity() != null && item.getActivity().getLatitude() != null) {
                prompt.append(String.format(" (GPS: %f, %f)", item.getActivity().getLatitude(), item.getActivity().getLongitude()));
            }
            prompt.append("\n");
        }
        
        String aiHtml = geminiClient.ask(prompt.toString());
        
        aiHtml = aiHtml.replaceAll("^```(html)?\\s*", "").replaceAll("\\s*```$", "");
        
        model.addAttribute("reportHtml", aiHtml.trim());
        return "planner/report";
    }

    private TripScheduleEntity getSchedule(Long id) {
        return scheduleRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private TripScheduleEntity getScheduleWithContext(Long id) {
        return scheduleRepository.findWithReservationAndTourById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private void broadcast(Long scheduleId) {
        messagingTemplate.convertAndSend("/topic/planner/" + scheduleId,
                plannerService.getItems(getSchedule(scheduleId)));
    }
}
