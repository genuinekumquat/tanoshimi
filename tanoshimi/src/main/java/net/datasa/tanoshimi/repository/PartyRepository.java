package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PartyStatus;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartyRepository extends JpaRepository<PartyEntity, Long> {
    /** [⑥ 마이페이지] '파티리더' 칭호용 - 내가 개설한 파티 수(상태 무관). */
    long countByOwner(UserEntity owner);

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

    /**
     * [v16 신규] 파티 완료 자동처리 스케줄러 전용 - 아직 completed 가 아니고
     * 종료일(departureDate + durationDays)이 이미 지난 파티들을 찾는다.
     */
    @Query(value = """
            select * from parties
            where status <> 'completed'
              and DATE_ADD(departure_date, INTERVAL duration_days DAY) < CURDATE()
            """, nativeQuery = true)
    List<PartyEntity> findEndedButNotCompleted();
}