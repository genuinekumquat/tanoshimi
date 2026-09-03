package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.*;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.ChatRoomMemberRepository;
import net.datasa.tanoshimi.repository.ChatRoomRepository;
import net.datasa.tanoshimi.repository.PartyApplicationRepository;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PartyApplicationService.apply - 신청 가드.
 *
 * <p>화면에서 버튼을 감추는 것과 무관하게 /api/parties/{id}/apply 를 직접 호출하면
 * 모집종료·완료·출발일 지난 파티에도 신청이 들어가던 문제를 막는 가드가 apply() 맨 앞에 있다.
 * 이 가드는 자격 검증(PartyEligibilityService)보다 먼저 도므로, 자격 검증이 관리자에게
 * 열어주던 우회 경로도 함께 닫힌다.
 */
@ExtendWith(MockitoExtension.class)
class PartyApplicationServiceTest {

    @Mock private PartyApplicationRepository applicationRepository;
    @Mock private PartyMemberRepository partyMemberRepository;
    @Mock private PartyEligibilityService eligibilityService;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private NotificationService notificationService;
    @Mock private BlockService blockService;

    @InjectMocks
    private PartyApplicationService partyApplicationService;

    private UserEntity user(String email) {
        return UserEntity.createLocal(email, email.substring(0, email.indexOf('@')), "hash", "이름", "01012345678",
                Gender.male, LocalDate.of(1995, 1, 1), Nationality.KR);
    }

    private PartyEntity party(LocalDate departureDate) {
        return PartyEntity.builder()
                .owner(user("owner@test.com"))
                .title("오사카 같이 가요").description("설명").region("오사카")
                .departureDate(departureDate).durationDays((byte) 2).capacity((byte) 4)
                .genderRestriction(GenderRestriction.all)
                .nationalityRestriction(NationalityRestriction.all)
                .build();
    }

    private void assertRejectedAsClosed(PartyEntity party) {
        assertThatThrownBy(() -> partyApplicationService.apply(party, user("applicant@test.com"), "안녕하세요"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARTY_RECRUITMENT_CLOSED);

        // 가드가 맨 앞이라 그 뒤 로직(차단검사/자격검사/저장/알림)은 아예 타지 않아야 한다.
        verifyNoInteractions(blockService, eligibilityService, applicationRepository, notificationService);
    }

    @Test
    void 출발일이_이미_지난_파티는_recruiting_이어도_신청_거부() {
        assertRejectedAsClosed(party(LocalDate.now().minusDays(1)));
    }

    @Test
    void 출발일이_오늘인_파티도_신청_거부() {
        assertRejectedAsClosed(party(LocalDate.now()));
    }

    @Test
    void 방장이_마감한_closed_파티는_출발일이_남았어도_신청_거부() {
        PartyEntity party = party(LocalDate.now().plusDays(7));
        party.close();
        assertRejectedAsClosed(party);
    }

    @Test
    void 완료_처리된_completed_파티도_신청_거부() {
        PartyEntity party = party(LocalDate.now().plusDays(7));
        party.markCompleted();
        assertRejectedAsClosed(party);
    }

    @Test
    void 아직_출발_안_한_모집중_파티는_가드를_통과해_다음_단계로_진행된다() {
        PartyEntity party = party(LocalDate.now().plusDays(7));
        UserEntity applicant = user("applicant@test.com");
        // 가드를 통과했다는 것을 증명하기 위해, 바로 다음 단계인 차단검사에서 막히도록 스텁한다.
        when(blockService.isBlockedEitherWay(applicant, party.getOwner())).thenReturn(true);

        assertThatThrownBy(() -> partyApplicationService.apply(party, applicant, "안녕하세요"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_APPLY_BLOCKED_PARTY);

        verify(blockService).isBlockedEitherWay(applicant, party.getOwner());
        verify(applicationRepository, never()).save(any());
    }
}
