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
    @Mock private net.datasa.tanoshimi.repository.ActivityRepository activityRepository;
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

    @Test
    void replaceNonFixedItems_는_편집권이_없으면_아무것도_지우지_않는다() {
        TripScheduleEntity schedule = mock(TripScheduleEntity.class);
        net.datasa.tanoshimi.domain.entity.UserEntity user = mock(net.datasa.tanoshimi.domain.entity.UserEntity.class);
        when(user.getId()).thenReturn(7L);
        doThrow(new net.datasa.tanoshimi.exception.BusinessException(net.datasa.tanoshimi.exception.ErrorCode.LOCK_NOT_HELD))
                .when(lockService).assertCanEdit(schedule, 7L);

        org.junit.jupiter.api.Assertions.assertThrows(net.datasa.tanoshimi.exception.BusinessException.class,
                () -> plannerService.replaceNonFixedItems(schedule, user, List.of()));

        verify(itemRepository, never()).delete(any());
    }

    @Test
    void extractPlaceName_은_활동_표현을_떼고_장소명만_남긴다() {
        org.junit.jupiter.api.Assertions.assertEquals("쿠로몬 시장", TripPlannerService.extractPlaceName("쿠로몬 시장 먹거리 투어"));
        org.junit.jupiter.api.Assertions.assertEquals("오사카역", TripPlannerService.extractPlaceName("오사카 스테이션 시티 가을 축제 미식 탐방"));
        org.junit.jupiter.api.Assertions.assertEquals("도톤보리", TripPlannerService.extractPlaceName("도톤보리 야경 산책"));
        org.junit.jupiter.api.Assertions.assertEquals("우메다역", TripPlannerService.extractPlaceName("우메다 카페 휴식 및 산책 (추천 활동: 우메다 蔦屋書店 카페)"));
        org.junit.jupiter.api.Assertions.assertEquals("난바역", TripPlannerService.extractPlaceName("난바 쇼핑"));
        org.junit.jupiter.api.Assertions.assertEquals("스미요시타이샤", TripPlannerService.extractPlaceName("스미요시타이샤 하츠모데"));
        org.junit.jupiter.api.Assertions.assertEquals("투어", TripPlannerService.extractPlaceName("투어"));
    }

    @Test
    void lodgingName_은_일정에_적힌_호텔_이름을_찾는다() {
        org.junit.jupiter.api.Assertions.assertEquals("도미인 난바", TripPlannerService.lodgingName("호텔 출발 (도미인 난바)"));
        org.junit.jupiter.api.Assertions.assertEquals("도미인 난바 호텔", TripPlannerService.lodgingName("도미인 난바 호텔 출발"));
        org.junit.jupiter.api.Assertions.assertEquals("도미인 난바 호텔", TripPlannerService.lodgingName("도미인 난바 호텔 체크인"));
        org.junit.jupiter.api.Assertions.assertNull(TripPlannerService.lodgingName("호텔 출발"));
        org.junit.jupiter.api.Assertions.assertNull(TripPlannerService.lodgingName("숙소 체크인"));
        org.junit.jupiter.api.Assertions.assertNull(TripPlannerService.lodgingName("쿠로몬 시장 먹거리 투어"));
    }
}
