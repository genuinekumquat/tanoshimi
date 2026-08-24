package net.datasa.tanoshimi.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.ScheduleItemRequest;
import net.datasa.tanoshimi.domain.dto.ScheduleItemView;
import net.datasa.tanoshimi.domain.entity.*;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.TripScheduleItemRepository;
import net.datasa.tanoshimi.repository.TripScheduleRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.ChatbotActivityService;
import net.datasa.tanoshimi.service.TripPlannerService;
import net.datasa.tanoshimi.service.WeatherAdvisorService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Planner page and REST API.
 * Uses REST API for CRUD operations, and broadcasts mapping via WebSocket.
 */
@Controller
@RequiredArgsConstructor
public class PlannerController {

    private final TripScheduleRepository scheduleRepository;
    private final TripScheduleItemRepository itemRepository;
    private final UserRepository userRepository;
    private final TripPlannerService plannerService;
    private final ChatbotActivityService chatbotActivityService;
    private final WeatherAdvisorService weatherAdvisorService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/planner/{scheduleId}")
    public String planner(@PathVariable Long scheduleId, Model model) {
        TripScheduleEntity schedule = getScheduleWithContext(scheduleId);
        TourEntity tour = schedule.getReservation() != null ? schedule.getReservation().getTour() : null;
        
        model.addAttribute("schedule", schedule);
        model.addAttribute("tour", tour);
        model.addAttribute("items", plannerService.getItems(schedule));
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
                                    @RequestParam int durationMinute, @RequestParam(required=false) Integer dayIndex) {
        plannerService.resizeItem(itemId, startMinute, durationMinute, dayIndex);
        return ApiResponse.okMessage("변경되었습니다.");
    }

    @DeleteMapping("/api/planner/items/{itemId}")
    @ResponseBody
    public ApiResponse<Void> remove(@PathVariable Long itemId) {
        plannerService.removeItem(itemId);
        return ApiResponse.okMessage("삭제되었습니다.");
    }

    /** AI Recommendation API */
    @GetMapping("/api/planner/{scheduleId}/recommend")
    @ResponseBody
    public ApiResponse<List<ActivityEntity>> recommend(@PathVariable Long scheduleId,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String region,
                                                        @RequestParam String date) {
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
