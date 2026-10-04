package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.TripScheduleEntity;
import net.datasa.tanoshimi.domain.entity.TripScheduleVoteEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripScheduleVoteRepository extends JpaRepository<TripScheduleVoteEntity, Long> {
    List<TripScheduleVoteEntity> findBySchedule(TripScheduleEntity schedule);
    Optional<TripScheduleVoteEntity> findByScheduleAndUser(TripScheduleEntity schedule, UserEntity user);
    
    
    /** [TNSM-72] 파티(계획표) 삭제 시 먼저 지워야 하는 자식 테이블. */
    void deleteBySchedule(TripScheduleEntity schedule);
}
