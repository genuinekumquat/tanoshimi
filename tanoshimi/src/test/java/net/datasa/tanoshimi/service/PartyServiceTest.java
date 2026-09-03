package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PartyStatus;
import net.datasa.tanoshimi.repository.PartyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
}
