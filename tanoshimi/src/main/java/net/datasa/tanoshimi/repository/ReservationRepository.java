package net.datasa.tanoshimi.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.ReservationEntity;
import net.datasa.tanoshimi.domain.entity.ReservationStatus;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {
    Optional<ReservationEntity> findByParty(PartyEntity party);
    List<ReservationEntity> findByDepartureDateAndStatus(LocalDate departureDate, ReservationStatus status);
    
    @EntityGraph(attributePaths = {"tour"})
    List<ReservationEntity> findByBookedBy(UserEntity bookedBy);
}
