package net.datasa.tanoshimi.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
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
    
    @Transactional
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
        Long itemId = plannerService.addItem(schedule, user, request);
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
    public ApiResponse<List<ActivityEntity>> recommend(@PathVariable Long scheduleId,
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
        return ApiResponse.ok(chatbotActivityService.recommend(targetRegion, d, keyword, badWeather));
    }

    @PostMapping("/api/planner/{scheduleId}/submit")
    @ResponseBody
    public ApiResponse<Void> submit(@PathVariable Long scheduleId) {
        plannerService.submitForPayment(getSchedule(scheduleId));
        broadcast(scheduleId);
        return ApiResponse.okMessage("제출되었습니다. 결제를 진행해주세요.");
    }

    @PostMapping("/api/planner/{scheduleId}/pay")
    @ResponseBody
    public ApiResponse<Void> pay(@PathVariable Long scheduleId, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity payer = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        plannerService.pay(getSchedule(scheduleId), payer);
        return ApiResponse.okMessage("결제가 완료되었습니다.");
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
