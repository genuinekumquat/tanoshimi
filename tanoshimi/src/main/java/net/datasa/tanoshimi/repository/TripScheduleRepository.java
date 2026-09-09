package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.TripScheduleEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripScheduleRepository extends JpaRepository<TripScheduleEntity, Long> {
    Optional<TripScheduleEntity> findByParty(PartyEntity party);

    /**
     * 계획표 화면(PlannerController)·AI 기능이 party/tour 필드를 바로 참조하므로 미리 JOIN FETCH 한다.
     * tour 가 없는 파티도 있어서 LEFT JOIN 을 쓴다.
     */
    @Query("""
            select ts from TripScheduleEntity ts
            left join fetch ts.party p
            left join fetch p.tour
            where ts.id = :id
            """)
    Optional<TripScheduleEntity> findWithContextById(@Param("id") Long id);
}
