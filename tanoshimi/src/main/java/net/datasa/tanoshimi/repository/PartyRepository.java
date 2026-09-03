package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PartyStatus;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import java.time.LocalDate;
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

    /**
     * 파티 게시판(둘러보기) 기본 목록 - status=recruiting 이면서 아직 출발일이 안 지난 파티만.
     * 출발일이 지난 파티는 뱃지를 뭘로 바꾸든 더 이상 신청할 수 없는 죽은 글이라 기본 조회에서 뺀다
     * (지난 파티는 findByBlindedFalseAndDepartureDateLessThanOrderByDepartureDateDesc 로 별도 조회).
     * from 에는 보통 오늘 날짜를 넣는다.
     */
    List<PartyEntity> findByStatusAndBlindedFalseAndDepartureDateGreaterThanEqualOrderByDepartureDateAsc(
            PartyStatus status, LocalDate from);

    /** 위와 동일하되 지역 필터가 걸린 버전. */
    List<PartyEntity> findByRegionAndStatusAndBlindedFalseAndDepartureDateGreaterThanEqualOrderByDepartureDateAsc(
            String region, PartyStatus status, LocalDate from);

    /** 파티 게시판 "지난 모임 보기" 탭 - 출발일이 이미 지난 파티(상태 무관), 최근 출발순. */
    List<PartyEntity> findByBlindedFalseAndDepartureDateLessThanOrderByDepartureDateDesc(LocalDate before);

    /**
     * 파티 게시판 검색창 - 제목/지역에 키워드가 포함된 모집중 파티. 게시판 기본 목록과 같은 기준으로
     * 아직 출발일이 안 지난(p.departureDate &gt;= :from) 파티만 노출한다.
     */
    @Query("""
            select p from PartyEntity p
            where p.status = :status and p.blinded = false
              and p.departureDate >= :from
              and (p.title like concat('%', :keyword, '%') or p.region like concat('%', :keyword, '%'))
            order by p.departureDate asc
            """)
    List<PartyEntity> searchRecruiting(@Param("status") PartyStatus status, @Param("keyword") String keyword,
                                       @Param("from") LocalDate from);

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