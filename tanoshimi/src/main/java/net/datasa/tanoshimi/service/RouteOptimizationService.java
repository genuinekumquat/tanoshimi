package net.datasa.tanoshimi.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.TripScheduleEntity;
import net.datasa.tanoshimi.domain.entity.TripScheduleItemEntity;
import net.datasa.tanoshimi.repository.TripScheduleItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [v16 신규] "동선 최적화하기" 버튼 - 같은 day_index(같은 날) 안에서 is_fixed=false 인
 * 이동가능 일정들만 좌표 기준으로 순서를 재배치한다. is_fixed=true 항목(항공·숙박 등)은
 * 앵커로 취급해 위치를 건드리지 않는다(필드제약조건 확정 사항).
 *
 * <p>실제 경로계산 API(Google Directions vs OSRM)는 제공사 미확정이라, 지금은 좌표 간
 * 직선거리 기준 최근접 이웃(nearest-neighbor) 방식으로 방문 순서만 정렬하고, 원래 시간대
 * 슬롯(첫 이동가능 항목의 시작 시각부터 지속시간만큼)에 순서대로 다시 배치한다.
 * 실제 API가 확정되면 정렬 로직만 교체하면 되고, 앵커 처리·저장 로직은 그대로 유지된다.
 */
@Service
@RequiredArgsConstructor
public class RouteOptimizationService {

    private final TripScheduleItemRepository itemRepository;

    @Transactional
    public void optimizeDay(TripScheduleEntity schedule, byte dayIndex) {
        List<TripScheduleItemEntity> dayItems = itemRepository
                .findByScheduleOrderByDayIndexAscStartMinuteAsc(schedule).stream()
                .filter(i -> i.getDayIndex() == dayIndex)
                .toList();

        List<TripScheduleItemEntity> movable = new ArrayList<>(dayItems.stream()
                .filter(TripScheduleItemEntity::isMovable)
                .toList());
        if (movable.size() < 2) {
            return; // 재배치할 게 없으면 그대로 둔다
        }

        // 좌표가 없는 항목(직접입력 등)은 원래 순서를 유지하도록 뒤로 보낸다.
        List<TripScheduleItemEntity> ordered = nearestNeighborOrder(movable);

        // 원래 이동가능 항목들이 차지하던 시간 슬롯(시작시각 오름차순)에 새 순서로 다시 배정한다.
        List<Short> slots = movable.stream()
                .map(TripScheduleItemEntity::getStartMinute)
                .sorted()
                .toList();
        for (int i = 0; i < ordered.size(); i++) {
            TripScheduleItemEntity item = ordered.get(i);
            short newStart = slots.get(i);
            item.reschedule(newStart, item.getDurationMinute(), dayIndex);
            itemRepository.save(item);
        }
    }

    /** 좌표가 있는 항목들만 최근접 이웃 방식으로 정렬하고, 좌표 없는 항목은 뒤에 원래 순서대로 붙인다. */
    private List<TripScheduleItemEntity> nearestNeighborOrder(List<TripScheduleItemEntity> items) {
        List<TripScheduleItemEntity> withCoords = new ArrayList<>(items.stream()
                .filter(i -> i.getActivity() != null && i.getActivity().getLatitude() != null)
                .toList());
        List<TripScheduleItemEntity> withoutCoords = items.stream()
                .filter(i -> i.getActivity() == null || i.getActivity().getLatitude() == null)
                .sorted(Comparator.comparing(TripScheduleItemEntity::getStartMinute))
                .toList();

        List<TripScheduleItemEntity> result = new ArrayList<>();
        if (!withCoords.isEmpty()) {
            TripScheduleItemEntity current = withCoords.remove(0);
            result.add(current);
            while (!withCoords.isEmpty()) {
                TripScheduleItemEntity nearest = null;
                double bestDist = Double.MAX_VALUE;
                for (TripScheduleItemEntity candidate : withCoords) {
                    double dist = distance(current, candidate);
                    if (dist < bestDist) {
                        bestDist = dist;
                        nearest = candidate;
                    }
                }
                withCoords.remove(nearest);
                result.add(nearest);
                current = nearest;
            }
        }
        result.addAll(withoutCoords);
        return result;
    }

    private double distance(TripScheduleItemEntity a, TripScheduleItemEntity b) {
        double lat1 = a.getActivity().getLatitude().doubleValue();
        double lng1 = a.getActivity().getLongitude().doubleValue();
        double lat2 = b.getActivity().getLatitude().doubleValue();
        double lng2 = b.getActivity().getLongitude().doubleValue();
        return Math.hypot(lat1 - lat2, lng1 - lng2);
    }
}
