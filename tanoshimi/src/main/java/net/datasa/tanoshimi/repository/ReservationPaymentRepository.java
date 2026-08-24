package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.ReservationEntity;
import net.datasa.tanoshimi.domain.entity.ReservationPaymentEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationPaymentRepository extends JpaRepository<ReservationPaymentEntity, Long> {
    List<ReservationPaymentEntity> findByReservation(ReservationEntity reservation);
    Optional<ReservationPaymentEntity> findByReservationAndUser(ReservationEntity reservation, UserEntity user);
    long countByReservationAndStatus(ReservationEntity reservation, net.datasa.tanoshimi.domain.entity.PaymentStatus status);
}
