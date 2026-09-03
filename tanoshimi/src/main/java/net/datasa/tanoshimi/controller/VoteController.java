package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.entity.TripScheduleEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.domain.entity.VoteType;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.TripPlannerService;
import net.datasa.tanoshimi.service.TripScheduleVoteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 완성된 계획표 찬반 투표. */
@RestController
@RequestMapping("/api/planner/{scheduleId}/vote")
@RequiredArgsConstructor
public class VoteController {

    private final UserRepository userRepository;
    private final TripPlannerService plannerService;
    private final TripScheduleVoteService voteService;

    @PostMapping
    public ApiResponse<Void> vote(@PathVariable Long scheduleId, @RequestParam VoteType type,
                                  @AuthenticationPrincipal CustomUserDetails principal) {
        TripScheduleEntity schedule = plannerService.getSchedule(scheduleId);
        UserEntity user = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        voteService.vote(schedule, user, type);
        return ApiResponse.okMessage("투표했습니다.");
    }

    @GetMapping("/tally")
    public ApiResponse<TripScheduleVoteService.Tally> tally(@PathVariable Long scheduleId) {
        return ApiResponse.ok(voteService.tally(plannerService.getSchedule(scheduleId)));
    }
}
