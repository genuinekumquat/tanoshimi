package net.datasa.tanoshimi.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.service.FileStorageService;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.PartyApplyRequest;
import net.datasa.tanoshimi.domain.dto.PartyCreateRequest;
import net.datasa.tanoshimi.domain.entity.ActiveStatus;
import net.datasa.tanoshimi.domain.entity.GenderRestriction;
import net.datasa.tanoshimi.domain.entity.NationalityRestriction;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.PartyRepository;
import net.datasa.tanoshimi.repository.TourRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.PartyApplicationService;
import net.datasa.tanoshimi.service.PartyEligibilityService;
import net.datasa.tanoshimi.service.PartyService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * 파티 모집 게시판 + 상세 + 참가 신청.
 *
 * <p>자격 미달이면 신청 버튼을 프론트에서 비활성화하기 위해, 상세 화면 로드 시점에
 * PartyEligibilityService 결과를 모델에 같이 담아 내려준다(로그인 상태일 때만).
 */
@Controller
@RequiredArgsConstructor
public class PartyController {

    private final PartyRepository partyRepository;
    private final net.datasa.tanoshimi.repository.PartyMemberRepository partyMemberRepository;
    private final UserRepository userRepository;
    private final TourRepository tourRepository;
    private final PartyApplicationService partyApplicationService;
    private final PartyEligibilityService eligibilityService;
    private final PartyService partyService;
    private final FileStorageService fileStorageService;

    @GetMapping("/party-board")
    public String board(@RequestParam(required = false) String region,
                        @RequestParam(required = false) String q,
                        @RequestParam(required = false, defaultValue = "false") boolean past,
                        @RequestParam(required = false) String gender,
                        @RequestParam(required = false) String nationality,
                        @RequestParam(required = false) Integer age,
                        Model model) {
        GenderRestriction genderFilter = (gender == null || gender.isBlank()) ? null : GenderRestriction.valueOf(gender);
        NationalityRestriction nationalityFilter = (nationality == null || nationality.isBlank()) ? null : NationalityRestriction.valueOf(nationality);
        model.addAttribute("parties", partyService.listBoard(region, q, past, genderFilter, nationalityFilter, age));
        model.addAttribute("keyword", q);
        model.addAttribute("region", region);
        model.addAttribute("showingPast", past);
        model.addAttribute("selectedGender", gender);
        model.addAttribute("selectedNationality", nationality);
        model.addAttribute("selectedAge", age);
        return "party/board";
    }

    @GetMapping("/party-board/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        PartyEntity party = partyRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
        if (party.isBlinded()) throw new BusinessException(ErrorCode.PARTY_NOT_FOUND, "블라인드 처리된 파티입니다.");

        model.addAttribute("party", party);

        List<net.datasa.tanoshimi.domain.entity.PartyMemberEntity> members = partyMemberRepository.findByParty(party);
        model.addAttribute("members", members);

        // eligible/ineligibleReasonKey/isOwner 는 비로그인 방문자에게도 항상 안전한 기본값이 들어가야 한다.
        // 예전엔 principal != null 일 때만 넣어서, 비로그인 상태로 이 페이지를 보면 이 값들이
        // 모델에 아예 없어(null) 템플릿의 "!eligible" 연산에서 SpEL 예외가 났었다.
        boolean eligible = false;
        String ineligibleReasonKey = null;
        boolean isOwner = false;

        if (principal != null) {
            UserEntity me = userRepository.findById(principal.getId()).orElse(null);
            if (me != null) {
                var eligibility = eligibilityService.check(party, me);
                eligible = eligibility.eligible();
                ineligibleReasonKey = eligibility.messageKey();
                isOwner = party.getOwner().getId().equals(me.getId());
            }
        }
        model.addAttribute("eligible", eligible);
        model.addAttribute("ineligibleReasonKey", ineligibleReasonKey);
        model.addAttribute("isOwner", isOwner);

        return "party/detail";
    }

    @GetMapping("/party-board/create")
    public String createForm(Model model) {
        model.addAttribute("tours", tourRepository.findByStatusOrderByIdDesc(ActiveStatus.active));
        return "party/create";
    }

    @PostMapping("/api/parties")
    @ResponseBody
    public ApiResponse<Long> create(@Valid @RequestBody PartyCreateRequest request,
                                    @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity owner = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Long partyId = partyService.createParty(owner, request);
        return ApiResponse.ok("파티가 만들어졌습니다.", partyId);
    }

    @PutMapping("/api/parties/{id}")
    @ResponseBody
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody PartyCreateRequest request,
                                    @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity owner = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        partyService.updateParty(id, owner, request);
        return ApiResponse.okMessage("파티 정보가 수정되었습니다.");
    }

    @DeleteMapping("/api/parties/{id}")
    @ResponseBody
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity owner = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        partyService.deleteParty(id, owner);
        return ApiResponse.okMessage("파티가 삭제되었습니다.");
    }

    @PostMapping("/api/parties/{id}/close")
    @ResponseBody
    public ApiResponse<Void> close(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity owner = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        partyService.closeParty(id, owner);
        return ApiResponse.okMessage("파티 모집이 마감되었습니다.");
    }

    /** 파티장이 아닌 멤버가 파티를 나간다. */
    @PostMapping("/api/parties/{id}/leave")
    @ResponseBody
    public ApiResponse<Void> leave(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        partyService.leaveParty(id, me);
        return ApiResponse.okMessage("파티에서 나왔습니다.");
    }

    /** 파티장이 특정 멤버를 강퇴한다. */
    @PostMapping("/api/parties/{id}/members/{userId}/kick")
    @ResponseBody
    public ApiResponse<Void> kick(@PathVariable Long id, @PathVariable Long userId,
                                  @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity owner = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        partyService.kickMember(id, owner, userId);
        return ApiResponse.okMessage("강퇴했습니다.");
    }

    @PostMapping("/api/parties/{id}/apply")
    @ResponseBody
    public ApiResponse<Long> apply(@PathVariable Long id, @Valid @RequestBody PartyApplyRequest request,
                                   @AuthenticationPrincipal CustomUserDetails principal) {
        PartyEntity party = partyRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
        if (party.isBlinded()) throw new BusinessException(ErrorCode.PARTY_NOT_FOUND, "블라인드 처리된 파티입니다.");
        UserEntity applicant = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Long applicationId = partyApplicationService.apply(party, applicant, request.message());
        return ApiResponse.ok("참가 신청이 접수되었습니다. 방장 승인을 기다려 주세요.", applicationId);
    }

    @PostMapping("/api/parties/{id}/apply/cancel")
    @ResponseBody
    public ApiResponse<Void> cancelApply(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        PartyEntity party = partyRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
        if (party.isBlinded()) throw new BusinessException(ErrorCode.PARTY_NOT_FOUND, "블라인드 처리된 파티입니다.");
        UserEntity applicant = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        partyApplicationService.cancel(party, applicant);
        return ApiResponse.okMessage("참가 신청이 취소되었습니다.");
    }

    @PostMapping("/api/parties/{id}/thumbnail")
    @ResponseBody
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<String> updateThumbnail(@PathVariable Long id, @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        PartyEntity party = partyRepository.findById(id).orElseThrow(() -> new net.datasa.tanoshimi.exception.BusinessException(net.datasa.tanoshimi.exception.ErrorCode.PARTY_NOT_FOUND));
        if (!party.getOwner().getId().equals(principal.getId())) {
            throw new net.datasa.tanoshimi.exception.BusinessException(net.datasa.tanoshimi.exception.ErrorCode.ACCESS_DENIED, "파티장만 변경할 수 있습니다.");
        }
        String url = fileStorageService.saveImage(file);
        party.changeThumbnail(url);
        fileStorageService.markActive(url);
        return ApiResponse.ok("썸네일이 변경되었습니다.", url);
    }
}
