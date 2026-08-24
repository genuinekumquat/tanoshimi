package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PartyMemberEntity;
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

    /**
     * "내 파티" 목록 화면(mypage/index.html, party/my-parties.html)에서 m.party.title 처럼
     * 연관 엔티티 필드를 바로 찍어 쓰기 때문에, open-in-view:false 인 상태로 렌더링 시점에
     * LazyInitializationException 이 나지 않도록 party 를 미리 JOIN FETCH 해서 가져온다.
     */
    @Query("select pm from PartyMemberEntity pm join fetch pm.party where pm.user = :user order by pm.joinedAt desc")
    List<PartyMemberEntity> findByUserOrderByJoinedAtDesc(@Param("user") UserEntity user);
}
