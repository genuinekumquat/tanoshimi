package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.TripScheduleEntity;
import net.datasa.tanoshimi.domain.entity.TripScheduleVoteEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripScheduleVoteRepository extends JpaRepository<TripScheduleVoteEntity, Long> {
    List<TripScheduleVoteEntity> findBySchedule(TripScheduleEntity schedule);
    Optional<TripScheduleVoteEntity> findByScheduleAndUser(TripScheduleEntity schedule, UserEntity user);
}
