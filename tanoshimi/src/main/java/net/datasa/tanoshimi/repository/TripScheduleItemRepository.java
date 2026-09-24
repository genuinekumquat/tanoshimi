package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.TripScheduleEntity;
import net.datasa.tanoshimi.domain.entity.TripScheduleItemEntity;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripScheduleItemRepository extends JpaRepository<TripScheduleItemEntity, Long> {
    /**
     * activity 를 함께 가져온다 - 계획표 리포트(PlannerController.report)처럼 트랜잭션 밖에서
     * item.getActivity().getLatitude() 를 읽는 곳이 있어, LAZY 프록시로 두면 open-in-view:false
     * 환경에서 LazyInitializationException(500)이 난다.
     */
    @EntityGraph(attributePaths = {"activity"})
    List<TripScheduleItemEntity> findByScheduleOrderByDayIndexAscStartMinuteAsc(TripScheduleEntity schedule);
    void deleteBySchedule(TripScheduleEntity schedule);
}
