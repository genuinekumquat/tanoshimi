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
import net.datasa.tanoshimi.service.PartyService;
import net.datasa.tanoshimi.service.TripPlannerService;
import net.datasa.tanoshimi.service.TripScheduleVoteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 완성된 계획표 찬반 투표. 투표·집계 모두 그 파티의 파티원만 할 수 있다 - 예전엔 로그인만 하면
 * 아무 계획표에나 투표해서 결과를 바꿀 수 있었다(PlannerController.requireMember 와 같은 기준).
 */
@RestController
@RequestMapping("/api/planner/{scheduleId}/vote")
@RequiredArgsConstructor
public class VoteController {

    private final UserRepository userRepository;
    private final TripPlannerService plannerService;
    private final TripScheduleVoteService voteService;
    private final PartyService partyService;

    @PostMapping
    public ApiResponse<Void> vote(@PathVariable Long scheduleId, @RequestParam VoteType type,
                                  @AuthenticationPrincipal CustomUserDetails principal) {
        TripScheduleEntity schedule = plannerService.getScheduleWithContext(scheduleId);
        UserEntity user = requireMember(schedule, principal);
        voteService.vote(schedule, user, type);
        return ApiResponse.okMessage("투표했습니다.");
    }

    @GetMapping("/tally")
    public ApiResponse<TripScheduleVoteService.Tally> tally(@PathVariable Long scheduleId,
                                                           @AuthenticationPrincipal CustomUserDetails principal) {
        TripScheduleEntity schedule = plannerService.getScheduleWithContext(scheduleId);
        requireMember(schedule, principal);
        return ApiResponse.ok(voteService.tally(schedule));
    }

    private UserEntity requireMember(TripScheduleEntity schedule, CustomUserDetails principal) {
        UserEntity user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (schedule.getParty() != null) {
            partyService.assertMember(schedule.getParty(), user);
        }
        return user;
    }
}
