package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.dto.PartyCardView;
import net.datasa.tanoshimi.domain.entity.Gender;
import net.datasa.tanoshimi.domain.entity.GenderRestriction;
import net.datasa.tanoshimi.domain.entity.Nationality;
import net.datasa.tanoshimi.domain.entity.NationalityRestriction;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PartyStatus;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.PartyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PartyService.listBoard - 파티 게시판(둘러보기) 목록 조회.
 *
 * <p>핵심은 "출발일이 지난 파티는 기본 목록에서 뺀다": 기본 조회는 항상 오늘(LocalDate.now())을
 * 기준으로 departureDate >= today 인 recruiting 파티만, past=true 일 때만 지난 파티를 조회한다.
 */
@ExtendWith(MockitoExtension.class)
class PartyServiceTest {

    @Mock
    private PartyRepository partyRepository;

    // PartyService 생성자의 나머지 의존성은 listBoard 가 건드리지 않으므로 목만 채워 넣는다.
    @Mock private net.datasa.tanoshimi.repository.UserRepository userRepository;
    @Mock private net.datasa.tanoshimi.repository.PartyMemberRepository partyMemberRepository;
    @Mock private net.datasa.tanoshimi.repository.ChatRoomRepository chatRoomRepository;
    @Mock private net.datasa.tanoshimi.repository.ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private net.datasa.tanoshimi.repository.TourRepository tourRepository;
    @Mock private net.datasa.tanoshimi.repository.TripScheduleRepository tripScheduleRepository;
    @Mock private NotificationService notificationService;
    @Mock private MannerTempService mannerTempService;
    @Mock private FileStorageService fileStorageService;

    private PartyService partyService() {
        return new PartyService(userRepository, partyRepository, partyMemberRepository, chatRoomRepository,
                chatRoomMemberRepository, tourRepository, tripScheduleRepository, notificationService,
                mannerTempService, fileStorageService);
    }

    private final List<PartyEntity> sample = List.of(mock(PartyEntity.class));

    private UserEntity owner() {
        return UserEntity.createLocal("o@test.com", "owner", "hash", "오너", "01000000000",
                Gender.male, LocalDate.of(1990, 1, 1), Nationality.KR);
    }

    private PartyEntity party(String title, LocalDate departure, int capacity) {
        return PartyEntity.builder()
                .owner(owner()).title(title).description("d").region("오사카")
                .departureDate(departure).durationDays((byte) 1).capacity((byte) capacity)
                .genderRestriction(GenderRestriction.all).nationalityRestriction(NationalityRestriction.all)
                .build();
    }

    @Test
    void 기본_조회는_출발일이_안_지난_모집중_파티만_오늘_기준으로_가져온다() {
        when(partyRepository.findByStatusAndBlindedFalseAndDepartureDateGreaterThanEqualOrderByDepartureDateAsc(
                eq(PartyStatus.recruiting), eq(LocalDate.now()))).thenReturn(sample);

        List<PartyEntity> result = partyService().listBoard(null, null, false);

        assertThat(result).isSameAs(sample);
        verify(partyRepository).findByStatusAndBlindedFalseAndDepartureDateGreaterThanEqualOrderByDepartureDateAsc(
                PartyStatus.recruiting, LocalDate.now());
        verify(partyRepository, never()).findByBlindedFalseAndDepartureDateLessThanOrderByDepartureDateDesc(any());
    }

    @Test
    void 지역이_주어지면_지역_필터_쿼리를_쓰되_여전히_출발일_이후만() {
        when(partyRepository.findByRegionAndStatusAndBlindedFalseAndDepartureDateGreaterThanEqualOrderByDepartureDateAsc(
                eq("오사카"), eq(PartyStatus.recruiting), eq(LocalDate.now()))).thenReturn(sample);

        List<PartyEntity> result = partyService().listBoard("오사카", null, false);

        assertThat(result).isSameAs(sample);
        verify(partyRepository).findByRegionAndStatusAndBlindedFalseAndDepartureDateGreaterThanEqualOrderByDepartureDateAsc(
                "오사카", PartyStatus.recruiting, LocalDate.now());
    }

    @Test
    void 키워드가_주어지면_trim_해서_검색_쿼리를_쓰고_출발일_이후만() {
        when(partyRepository.searchRecruiting(eq(PartyStatus.recruiting), eq("오사카"), eq(LocalDate.now())))
                .thenReturn(sample);

        List<PartyEntity> result = partyService().listBoard(null, "  오사카  ", false);

        assertThat(result).isSameAs(sample);
        verify(partyRepository).searchRecruiting(PartyStatus.recruiting, "오사카", LocalDate.now());
    }

    @Test
    void past_true_면_출발일이_지난_파티를_상태무관으로_가져온다() {
        when(partyRepository.findByBlindedFalseAndDepartureDateLessThanOrderByDepartureDateDesc(eq(LocalDate.now())))
                .thenReturn(sample);

        List<PartyEntity> result = partyService().listBoard(null, null, true);

        assertThat(result).isSameAs(sample);
        verify(partyRepository).findByBlindedFalseAndDepartureDateLessThanOrderByDepartureDateDesc(LocalDate.now());
    }

    @Test
    void past_true_면_지역_키워드가_있어도_무시하고_지난_파티_쿼리만_탄다() {
        when(partyRepository.findByBlindedFalseAndDepartureDateLessThanOrderByDepartureDateDesc(any()))
                .thenReturn(sample);

        partyService().listBoard("오사카", "키워드", true);

        verify(partyRepository).findByBlindedFalseAndDepartureDateLessThanOrderByDepartureDateDesc(LocalDate.now());
        verify(partyRepository, never()).searchRecruiting(any(), any(), any());
        verify(partyRepository, never())
                .findByRegionAndStatusAndBlindedFalseAndDepartureDateGreaterThanEqualOrderByDepartureDateAsc(any(), any(), any());
    }

    // ---------------------------------------------------------------- getVisibleParty

    @Test
    void getVisibleParty_는_블라인드_파티면_PARTY_NOT_FOUND() {
        PartyEntity blinded = party("가려진 파티", LocalDate.now().plusDays(3), 4);
        blinded.blind();
        when(partyRepository.findById(1L)).thenReturn(java.util.Optional.of(blinded));

        assertThatThrownBy(() -> partyService().getVisibleParty(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARTY_NOT_FOUND);
    }

    @Test
    void getVisibleParty_는_블라인드가_아니면_그대로_반환() {
        PartyEntity normal = party("정상 파티", LocalDate.now().plusDays(3), 4);
        when(partyRepository.findById(1L)).thenReturn(java.util.Optional.of(normal));

        assertThat(partyService().getVisibleParty(1L)).isSameAs(normal);
    }

    @Test
    void getParty_는_없으면_PARTY_NOT_FOUND() {
        when(partyRepository.findById(9L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> partyService().getParty(9L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARTY_NOT_FOUND);
    }

    // ---------------------------------------------------------------- otherMembers

    @Test
    void otherMembers_는_지정한_유저를_뺀_나머지_파티원_유저를_돌려준다() {
        PartyEntity p = mock(PartyEntity.class);
        UserEntity u1 = mock(UserEntity.class);
        UserEntity me = mock(UserEntity.class);
        UserEntity u3 = mock(UserEntity.class);
        when(u1.getId()).thenReturn(1L);
        when(me.getId()).thenReturn(2L);
        when(u3.getId()).thenReturn(3L);
        net.datasa.tanoshimi.domain.entity.PartyMemberEntity m1 = mock(net.datasa.tanoshimi.domain.entity.PartyMemberEntity.class);
        net.datasa.tanoshimi.domain.entity.PartyMemberEntity m2 = mock(net.datasa.tanoshimi.domain.entity.PartyMemberEntity.class);
        net.datasa.tanoshimi.domain.entity.PartyMemberEntity m3 = mock(net.datasa.tanoshimi.domain.entity.PartyMemberEntity.class);
        when(m1.getUser()).thenReturn(u1);
        when(m2.getUser()).thenReturn(me);
        when(m3.getUser()).thenReturn(u3);
        when(partyMemberRepository.findByParty(p)).thenReturn(List.of(m1, m2, m3));

        assertThat(partyService().otherMembers(p, 2L)).containsExactly(u1, u3);
    }

    // ---------------------------------------------------------------- urgentPartyCards

    @Test
    void urgentPartyCards_는_잔여석_적은순_그다음_출발일_빠른순으로_정렬한다() {
        PartyEntity a = party("A", LocalDate.now().plusDays(10), 4); // 잔여 1
        PartyEntity b = party("B", LocalDate.now().plusDays(5), 5);  // 잔여 4
        PartyEntity c = party("C", LocalDate.now().plusDays(3), 4);  // 잔여 1
        when(partyRepository.findByStatusAndBlindedFalseOrderByDepartureDateAsc(PartyStatus.recruiting))
                .thenReturn(List.of(a, b, c));
        when(partyMemberRepository.countByParty(a)).thenReturn(3L);
        when(partyMemberRepository.countByParty(b)).thenReturn(1L);
        when(partyMemberRepository.countByParty(c)).thenReturn(3L);

        List<PartyCardView> cards = partyService().urgentPartyCards();

        // 잔여 1인 A·C 가 먼저, 그 안에서는 출발일 빠른 C 가 A 보다 앞. 잔여 4인 B 는 맨 뒤.
        assertThat(cards).extracting(PartyCardView::title).containsExactly("C", "A", "B");
        assertThat(cards).extracting(PartyCardView::remaining).containsExactly(1, 1, 4);
    }
}
