package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.ActivityEntity;
import net.datasa.tanoshimi.domain.entity.TripScheduleEntity;
import net.datasa.tanoshimi.domain.entity.TripScheduleItemEntity;
import net.datasa.tanoshimi.repository.TripScheduleItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * RouteOptimizationService.optimizeDay - FR-RTE-01~03 검증.
 * 같은 날(day_index) 안에서 is_fixed=false 항목만 좌표 최근접 이웃 순서로,
 * 원래 시작시각 슬롯(오름차순)에 재배치한다. 고정 항목은 앵커로 미이동,
 * 좌표 없는 항목은 뒤로(원래 순서 유지).
 */
@ExtendWith(MockitoExtension.class)
class RouteOptimizationServiceTest {

    @Mock private TripScheduleItemRepository itemRepository;

    @InjectMocks private RouteOptimizationService service;

    private final TripScheduleEntity schedule = mock(TripScheduleEntity.class);

    private static final byte DAY = 1;
    private static final short DUR = 60;

    private TripScheduleItemEntity coordItem(short start, double lat, double lng) {
        TripScheduleItemEntity it = mock(TripScheduleItemEntity.class);
        when(it.getDayIndex()).thenReturn(DAY);
        when(it.isMovable()).thenReturn(true);
        lenient().when(it.getStartMinute()).thenReturn(start);
        lenient().when(it.getDurationMinute()).thenReturn(DUR);
        ActivityEntity act = mock(ActivityEntity.class);
        lenient().when(it.getActivity()).thenReturn(act);
        lenient().when(act.getLatitude()).thenReturn(BigDecimal.valueOf(lat));
        lenient().when(act.getLongitude()).thenReturn(BigDecimal.valueOf(lng));
        return it;
    }

    private TripScheduleItemEntity noCoordItem(short start) {
        TripScheduleItemEntity it = mock(TripScheduleItemEntity.class);
        when(it.getDayIndex()).thenReturn(DAY);
        when(it.isMovable()).thenReturn(true);
        lenient().when(it.getStartMinute()).thenReturn(start);
        lenient().when(it.getDurationMinute()).thenReturn(DUR);
        lenient().when(it.getActivity()).thenReturn(null);
        return it;
    }

    private TripScheduleItemEntity fixedItem() {
        TripScheduleItemEntity it = mock(TripScheduleItemEntity.class);
        when(it.getDayIndex()).thenReturn(DAY);
        when(it.isMovable()).thenReturn(false);
        return it;
    }

    @Test
    void 이동가능_항목이_2개_미만이면_아무것도_재배치하지_않는다() {
        TripScheduleItemEntity fixed = fixedItem();
        TripScheduleItemEntity onlyMovable = coordItem((short) 600, 0, 0);
        when(itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule))
                .thenReturn(List.of(fixed, onlyMovable));

        service.optimizeDay(schedule, DAY);

        verify(itemRepository, never()).save(any());
        verify(onlyMovable, never()).reschedule(anyShort(), anyShort(), any());
    }

    @Test
    void 고정_항목은_재배치_대상에서_제외된다() {
        TripScheduleItemEntity fixed = fixedItem();
        TripScheduleItemEntity m1 = coordItem((short) 100, 0, 0);
        TripScheduleItemEntity m2 = coordItem((short) 200, 0, 5);
        when(itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule))
                .thenReturn(List.of(fixed, m1, m2));

        service.optimizeDay(schedule, DAY);

        verify(fixed, never()).reschedule(anyShort(), anyShort(), any());
        verify(itemRepository, times(2)).save(any());
    }

    @Test
    void 최근접_이웃_순서로_원래_시작시각_슬롯에_재배치한다() {
        // 좌표: A(0,0) B(0,9) C(0,1) / 시작시각: A100 < B200 < C300
        // A 기준 최근접은 C(거리1) → 그다음 B. 최종 순서 A,C,B 를 [100,200,300] 슬롯에 매핑.
        TripScheduleItemEntity a = coordItem((short) 100, 0, 0);
        TripScheduleItemEntity b = coordItem((short) 200, 0, 9);
        TripScheduleItemEntity c = coordItem((short) 300, 0, 1);
        when(itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule))
                .thenReturn(List.of(a, b, c));

        service.optimizeDay(schedule, DAY);

        verify(a).reschedule((short) 100, DUR, (byte) DAY);
        verify(c).reschedule((short) 200, DUR, (byte) DAY);
        verify(b).reschedule((short) 300, DUR, (byte) DAY);
        verify(itemRepository, times(3)).save(any());
    }

    @Test
    void 좌표_없는_항목은_좌표_있는_항목_뒤로_원래_순서를_유지한다() {
        // X(좌표없음)100, Y(좌표없음)200, A(좌표0,0)300 → 최종 A,X,Y 를 [100,200,300] 슬롯에.
        TripScheduleItemEntity x = noCoordItem((short) 100);
        TripScheduleItemEntity y = noCoordItem((short) 200);
        TripScheduleItemEntity a = coordItem((short) 300, 0, 0);
        when(itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule))
                .thenReturn(List.of(x, y, a));

        service.optimizeDay(schedule, DAY);

        verify(a).reschedule((short) 100, DUR, (byte) DAY);
        verify(x).reschedule((short) 200, DUR, (byte) DAY);
        verify(y).reschedule((short) 300, DUR, (byte) DAY);
    }

    @Test
    void 다른_날짜_항목은_건드리지_않는다() {
        TripScheduleItemEntity today1 = coordItem((short) 100, 0, 0);
        TripScheduleItemEntity today2 = coordItem((short) 200, 0, 1);
        TripScheduleItemEntity otherDay = mock(TripScheduleItemEntity.class);
        when(otherDay.getDayIndex()).thenReturn((byte) 2);
        when(itemRepository.findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule))
                .thenReturn(List.of(today1, today2, otherDay));

        service.optimizeDay(schedule, DAY);

        verify(otherDay, never()).reschedule(anyShort(), anyShort(), any());
        verify(itemRepository, times(2)).save(any());
    }
}
