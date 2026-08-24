package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.ReservationEntity;
import net.datasa.tanoshimi.domain.entity.TripScheduleEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripScheduleRepository extends JpaRepository<TripScheduleEntity, Long> {
    Optional<TripScheduleEntity> findByReservation(ReservationEntity reservation);
    Optional<TripScheduleEntity> findByParty(PartyEntity party);

    /**
     * 계획표 화면(PlannerController)에서 reservation/tour 필드를 바로 참조하므로 미리 JOIN FETCH 한다.
     * reservation 이 아직 없을 수 있어서(패키지 예약 전) LEFT JOIN 을 쓴다 - INNER JOIN 이면
     * 예약 전 계획표는 아예 조회가 안 된다.
     */
    @Query("""
            select ts from TripScheduleEntity ts
            left join fetch ts.reservation r
            left join fetch r.tour
            left join fetch ts.party p
            where ts.id = :id
            """)
    Optional<TripScheduleEntity> findWithReservationAndTourById(@Param("id") Long id);
}
