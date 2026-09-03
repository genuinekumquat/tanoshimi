package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.TripScheduleEntity;
import net.datasa.tanoshimi.domain.entity.TripScheduleItemEntity;
import net.datasa.tanoshimi.repository.TripScheduleItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * TripPlannerService - 컨트롤러(PlannerController.aiValidate)에서 옮겨온
 * "고정 아닌 항목 전부 삭제" 로직만 검증. 단순 조회 passthrough 는 생략.
 */
@ExtendWith(MockitoExtension.class)
class TripPlannerServiceTest {

    @Mock private TripScheduleItemRepository itemRepository;

    // 나머지 생성자 의존성은 이 테스트가 건드리지 않으므로 목만 채운다.
    @Mock private net.datasa.tanoshimi.repository.TripScheduleRepository scheduleRepository;
    @Mock private net.datasa.tanoshimi.repository.TripSchedulePaymentRepository paymentRepository;
    @Mock private net.datasa.tanoshimi.repository.ActivityRepository activityRepository;
    @Mock private net.datasa.tanoshimi.repository.PartyMemberRepository partyMemberRepository;
    @Mock private net.datasa.tanoshimi.repository.UserRepository userRepository;
    @Mock private TripPlannerLockService lockService;

    @InjectMocks
    private TripPlannerService plannerService;

    private TripScheduleItemEntity item(boolean fixed) {
        TripScheduleItemEntity it = mock(TripScheduleItemEntity.class);
        when(it.isFixed()).thenReturn(fixed);
        return it;
    }

    @Test
    void clearNonFixedItems_는_고정_아닌_항목만_지우고_flush_한다() {
        TripScheduleEntity schedule = mock(TripScheduleEntity.class);
        TripScheduleItemEntity fixed = item(true);
        TripScheduleItemEntity free1 = item(false);
        TripScheduleItemEntity free2 = item(false);
        when(itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule))
                .thenReturn(List.of(fixed, free1, free2));

        plannerService.clearNonFixedItems(schedule);

        verify(itemRepository).delete(free1);
        verify(itemRepository).delete(free2);
        verify(itemRepository, never()).delete(fixed);
        verify(itemRepository).flush();
    }
}
