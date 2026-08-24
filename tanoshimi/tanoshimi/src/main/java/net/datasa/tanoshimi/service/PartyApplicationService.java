package net.datasa.tanoshimi.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.ApplicantSummaryDTO;
import net.datasa.tanoshimi.domain.entity.*;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.ChatRoomMemberRepository;
import net.datasa.tanoshimi.repository.ChatRoomRepository;
import net.datasa.tanoshimi.repository.PartyApplicationRepository;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파티 참가 신청 -> 방장 승인 -> party_members 등록 흐름.
 * 자격 미달이면 신청 자체를 서버가 막는다(PartyEligibilityService 2차 검증 - 프론트 버튼
 * 비활성화는 1차 방어일 뿐, 여기가 진짜 방어선).
 */
@Service
@RequiredArgsConstructor
public class PartyApplicationService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final PartyApplicationRepository applicationRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PartyEligibilityService eligibilityService;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final NotificationService notificationService;

    @Transactional
    public Long apply(PartyEntity party, UserEntity applicant, String message) {
        var eligibility = eligibilityService.check(party, applicant);
        if (!eligibility.eligible()) {
            ErrorCode code = switch (eligibility.messageKey()) {
                case "party.apply.disabled.gender" -> ErrorCode.PARTY_GENDER_RESTRICTED;
                case "party.apply.disabled.age" -> ErrorCode.PARTY_AGE_RESTRICTED;
                default -> ErrorCode.PARTY_NATIONALITY_RESTRICTED;
            };
            throw new BusinessException(code);
        }
        if (partyMemberRepository.existsByPartyAndUser(party, applicant)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 파티원입니다.");
        }
        if (partyMemberRepository.countByParty(party) >= party.getCapacity()) {
            throw new BusinessException(ErrorCode.PARTY_FULL);
        }

        var existing = applicationRepository.findByPartyAndApplicant(party, applicant);
        if (existing.isPresent() && existing.get().getStatus() == ApplicationStatus.pending) {
            return existing.get().getId();
        }

        PartyApplicationEntity application = new PartyApplicationEntity(party, applicant, message);
        Long applicationId = applicationRepository.save(application).getId();

        notificationService.notify(party.getOwner(), "party_application",
                "새 참가 신청이 있어요",
                applicant.getName() + "님이 '" + party.getTitle() + "'에 참가 신청했습니다.",
                "/party-board/" + party.getId() + "/room");

        return applicationId;
    }

    /** 방장이 보는 신청자 목록 - 닉네임/매너온도/국적만. */
    @Transactional(readOnly = true)
    public List<ApplicantSummaryDTO> listPendingApplicants(PartyEntity party) {
        return applicationRepository.findByPartyAndStatus(party, ApplicationStatus.pending).stream()
                .map(a -> new ApplicantSummaryDTO(
                        a.getId(),
                        a.getApplicant().getId(),
                        a.getApplicant().getName(),
                        a.getApplicant().getMannerTemp().doubleValue(),
                        a.getApplicant().getNationality().name(),
                        a.getMessage(),
                        a.getAppliedAt().format(DATE_FMT)))
                .toList();
    }

    /**
     * 승인 - party_members 뿐 아니라 파티 채팅방(chat_room_members)에도 반드시 같이 넣어야 한다.
     * 이걸 빼먹으면 승인된 사람이 파티방 화면은 보이는데 채팅은 조용히 실패한다
     * (ChatService.assertMember 가 party_members 가 아니라 chat_room_members 를 기준으로 막기 때문).
     */
    /**
     * applicationId 로 받아서 이 메서드 안에서 직접 조회한다(컨트롤러가 미리 조회해 넘기지 않는다) -
     * party/applicant 필드를 다루는데 이게 LAZY 라서, 컨트롤러에서 조회한 detached 엔티티를
     * 그대로 받으면 LazyInitializationException 이 날 수 있다. JOIN FETCH 로 한 번에 가져온다.
     */
    @Transactional
    public void approve(Long applicationId) {
        PartyApplicationEntity application = applicationRepository.findWithPartyAndApplicantById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        PartyEntity party = application.getParty();
        if (partyMemberRepository.countByParty(party) >= party.getCapacity()) {
            throw new BusinessException(ErrorCode.PARTY_FULL);
        }
        UserEntity applicant = application.getApplicant();
        application.approve();
        applicationRepository.save(application);
        partyMemberRepository.save(new PartyMemberEntity(party, applicant, PartyMemberRole.member));

        Optional<ChatRoomEntity> room = chatRoomRepository.findByParty(party);
        room.ifPresent(r -> {
            if (!chatRoomMemberRepository.existsByRoomAndUser(r, applicant)) {
                chatRoomMemberRepository.save(new ChatRoomMemberEntity(r, applicant));
            }
        });

        if (partyMemberRepository.countByParty(party) >= party.getCapacity()) {
            party.markFull();
        }

        notificationService.notify(applicant, "party_approved",
                "파티 참가가 승인됐어요",
                "'" + party.getTitle() + "' 파티에 합류했습니다. 채팅방에서 인사해 보세요!",
                "/party-board/" + party.getId() + "/room");
    }

    @Transactional
    public void reject(Long applicationId) {
        PartyApplicationEntity application = applicationRepository.findWithPartyAndApplicantById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        application.reject();
        applicationRepository.save(application);
        notificationService.notify(application.getApplicant(), "party_rejected",
                "파티 참가 신청 결과 안내",
                "'" + application.getParty().getTitle() + "' 참가 신청이 아쉽게도 거절되었습니다.",
                "/party-board/" + application.getParty().getId());
    }

    @Transactional
    public void cancel(PartyEntity party, UserEntity applicant) {
        var existing = applicationRepository.findByPartyAndApplicant(party, applicant)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "신청 내역이 없습니다."));
        if (existing.getStatus() != ApplicationStatus.pending) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "대기 중인 신청만 취소할 수 있습니다.");
        }
        applicationRepository.delete(existing);
    }
}
