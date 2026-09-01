package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PartyMemberEntity;
import net.datasa.tanoshimi.domain.entity.PartyStatus;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartyMemberRepository extends JpaRepository<PartyMemberEntity, Long> {
    boolean existsByPartyAndUser(PartyEntity party, UserEntity user);
    Optional<PartyMemberEntity> findByPartyAndUser(PartyEntity party, UserEntity user);
    @EntityGraph(attributePaths = {"user"})
    List<PartyMemberEntity> findByParty(PartyEntity party);
    long countByParty(PartyEntity party);

    /** [⑥ 마이페이지] '프로참석러' 칭호용 - 내가 속한 파티 수(개설한 것 포함). */
    long countByUser(UserEntity user);

    /**
     * "내 파티" 목록 화면(mypage/index.html, party/my-parties.html)에서 m.party.title 처럼
     * 연관 엔티티 필드를 바로 찍어 쓰기 때문에, open-in-view:false 인 상태로 렌더링 시점에
     * LazyInitializationException 이 나지 않도록 party 를 미리 JOIN FETCH 해서 가져온다.
     */
    @Query("select pm from PartyMemberEntity pm join fetch pm.party where pm.user = :user order by pm.joinedAt desc")
    List<PartyMemberEntity> findByUserOrderByJoinedAtDesc(@Param("user") UserEntity user);

    /**
     * [⑥ 마이페이지] 히트맵·칭호 집계용 - 내가 참여했고 이미 완료된 파티들.
     *
     * <p>v16 이전엔 예약(reservations) 내역으로 방문 횟수를 셌지만, 예약·결제 기능이
     * 삭제되면서 완료된 파티가 유일한 "다녀온 여행"의 근거가 됐다.
     *
     * <p>pm.party 를 거치지 않고 party 자체를 select 하므로 별도 fetch join 없이도
     * region·durationDays 를 바로 읽을 수 있다(open-in-view:false 환경 대응).
     */
    @Query("select p from PartyMemberEntity pm join pm.party p where pm.user = :user and p.status = :status")
    List<PartyEntity> findPartiesByUserAndStatus(@Param("user") UserEntity user, @Param("status") PartyStatus status);
}
