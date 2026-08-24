package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.TripScheduleEntity;
import net.datasa.tanoshimi.domain.entity.TripSchedulePaymentEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripSchedulePaymentRepository extends JpaRepository<TripSchedulePaymentEntity, Long> {
    List<TripSchedulePaymentEntity> findBySchedule(TripScheduleEntity schedule);
    Optional<TripSchedulePaymentEntity> findByScheduleAndUser(TripScheduleEntity schedule, UserEntity user);
}
