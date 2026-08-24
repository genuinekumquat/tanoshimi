package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.TripScheduleEntity;
import net.datasa.tanoshimi.domain.entity.TripScheduleItemEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripScheduleItemRepository extends JpaRepository<TripScheduleItemEntity, Long> {
    List<TripScheduleItemEntity> findByScheduleOrderByDayIndexAscStartMinuteAsc(TripScheduleEntity schedule);
    void deleteBySchedule(TripScheduleEntity schedule);
}
