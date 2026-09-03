package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.ApplicantSummaryDTO;
import net.datasa.tanoshimi.domain.entity.*;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.ChatService;
import net.datasa.tanoshimi.service.PartyApplicationService;
import net.datasa.tanoshimi.service.PartyService;
import net.datasa.tanoshimi.service.PostService;
import net.datasa.tanoshimi.service.ReservationService;
import net.datasa.tanoshimi.service.TripScheduleVoteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * 파티원만 볼 수 있는 파티 전용 페이지.
 *
 * <p>접근 제어: SecurityConfig 는 "로그인만" 요구하고, 여기서 party_members 존재 여부로
 * 실제 멤버십을 한 번 더 확인한다(URL 을 안다고 아무나 들어올 수 없게 하는 최종 방어선).
 * 채팅/계획표/투표/파티 전용 게시판(사진첩)이 전부 이 화면 하나에 모여 있다.
 *
 * <p>계획표는 이제 예약(패키지 결제) 여부와 무관하게 항상 존재한다(파티 생성 시 자동 생성 -
 * PartyService.createParty). ensureSchedule() 로 옛날 데이터(이 변경 이전에 만들어진 파티)도
 * 자연스럽게 맞춰준다.
 */
@Controller
@RequiredArgsConstructor
public class PartyRoomController {

    private final UserRepository userRepository;
    private final ChatService chatService;
    private final PartyApplicationService partyApplicationService;
    private final PartyService partyService;
    private final PostService postService;
    private final ReservationService reservationService;
    private final TripScheduleVoteService voteService;

    @GetMapping("/party-board/{id}/room")
    public String room(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        PartyEntity party = partyService.getParty(id);
        UserEntity me = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        partyService.assertMember(party, me);

        model.addAttribute("party", party);
        model.addAttribute("isOwner", party.getOwner().getId().equals(me.getId()));
        model.addAttribute("members", partyService.members(party));

        partyService.chatRoomOf(party).ifPresent(room -> {
            model.addAttribute("roomId", room.getId());
            model.addAttribute("chatHistory", chatService.history(room));
        });

        // 계획표는 예약 여부와 무관하게 항상 존재한다(없으면 즉시 생성 - 옛날 데이터 호환)
        var schedule = partyService.ensureSchedule(party);
        model.addAttribute("scheduleId", schedule.getId());
        model.addAttribute("voteTally", voteService.tally(schedule));

        // 패키지 예약 여부는 별개로 보여준다 - 예약 전이라도 계획표는 이미 위에서 항상 있다
        reservationService.forParty(party).ifPresent(reservation -> model.addAttribute("reservation", reservation));

        // 파티 전용 게시판(사진첩)
        model.addAttribute("partyPosts", postService.partyPhotos(party));


        if (party.getOwner().getId().equals(me.getId())) {
            model.addAttribute("pendingApplicants", partyApplicationService.listPendingApplicants(party));
        }
        return "party/room";
    }

    @PostMapping("/api/parties/{partyId}/applications/{applicationId}/approve")
    @ResponseBody
    public ApiResponse<Void> approve(@PathVariable Long partyId, @PathVariable Long applicationId,
                                     @AuthenticationPrincipal CustomUserDetails principal) {
        PartyEntity party = partyService.getParty(partyId);
        assertOwner(party, principal.getId());
        partyApplicationService.approve(applicationId);
        return ApiResponse.okMessage("승인했습니다.");
    }

    @PostMapping("/api/parties/{partyId}/applications/{applicationId}/reject")
    @ResponseBody
    public ApiResponse<Void> reject(@PathVariable Long partyId, @PathVariable Long applicationId,
                                    @AuthenticationPrincipal CustomUserDetails principal) {
        PartyEntity party = partyService.getParty(partyId);
        assertOwner(party, principal.getId());
        partyApplicationService.reject(applicationId);
        return ApiResponse.okMessage("거절했습니다.");
    }

    private void assertOwner(PartyEntity party, Long userId) {
        if (!party.getOwner().getId().equals(userId)) throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
}
