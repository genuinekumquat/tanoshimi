package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.TripScheduleEntity;
import net.datasa.tanoshimi.domain.entity.TripScheduleSnapshotEntity;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripScheduleSnapshotRepository extends JpaRepository<TripScheduleSnapshotEntity, Long> {
    /** 롤백 화면에서 "누가 언제 저장했는지" 목록을 바로 찍어 써야 하므로 createdBy 를 미리 JOIN FETCH 한다. */
    @EntityGraph(attributePaths = {"createdBy"})
    List<TripScheduleSnapshotEntity> findByScheduleOrderByCreatedAtDesc(TripScheduleEntity schedule);
}
