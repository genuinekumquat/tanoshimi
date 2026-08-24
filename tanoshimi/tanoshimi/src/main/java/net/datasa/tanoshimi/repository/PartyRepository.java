package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PartyStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartyRepository extends JpaRepository<PartyEntity, Long> {
    List<PartyEntity> findByStatusAndBlindedFalseOrderByDepartureDateAsc(PartyStatus status);
    org.springframework.data.domain.Page<PartyEntity> findByTitleContainingIgnoreCase(String title, org.springframework.data.domain.Pageable pageable);
    List<PartyEntity> findByRegionAndStatusAndBlindedFalse(String region, PartyStatus status);

    /** 메인 페이지 검색창 - 제목/지역에 키워드가 포함된 모집중 파티. */
    @Query("""
            select p from PartyEntity p
            where p.status = :status and p.blinded = false
              and (p.title like concat('%', :keyword, '%') or p.region like concat('%', :keyword, '%'))
            order by p.departureDate asc
            """)
    List<PartyEntity> searchRecruiting(@Param("status") PartyStatus status, @Param("keyword") String keyword);
}