package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PostEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<PostEntity, Long> {
    /**
     * 게시판 진입 경로: 전체 글 최신순 (region 필터는 서비스에서 Specification 대신 간단히 처리).
     * board/list.html 이 목록에서도 post.user.name 을 찍어 쓰기 때문에(작성자 표시),
     * @EntityGraph 로 user 를 미리 함께 가져온다 - Page 쿼리는 count 쿼리가 별도로 생기는 구조라
     * JOIN FETCH 를 직접 쓰면 페이징이 깨지므로 @EntityGraph 를 쓴다.
     */
    @EntityGraph(attributePaths = {"user"})
    Page<PostEntity> findByBlindedFalseOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    Page<PostEntity> findByBlindedFalseAndRegionOrderByCreatedAtDesc(String region, Pageable pageable);

    /** 마이페이지 진입 경로: 같은 테이블을 user_id 로만 필터링 */
    Page<PostEntity> findByUserOrderByCreatedAtDesc(UserEntity user, Pageable pageable);

    /** 파티 전용 게시판(사진첩) 진입 경로 - party_id 로 필터링, 이것도 같은 posts 테이블을 공유한다. */
    @EntityGraph(attributePaths = {"user"})
    List<PostEntity> findByPartyOrderByCreatedAtDesc(PartyEntity party);

    /**
     * 게시글 상세(board/detail.html)에서 post.user.name 을 바로 찍어 쓰기 때문에,
     * open-in-view:false 상태에서 렌더링 시점에 LazyInitializationException 이 나지 않도록
     * user 를 미리 JOIN FETCH 해서 가져온다.
     */
    @Query("select p from PostEntity p join fetch p.user where p.id = :id")
    Optional<PostEntity> findWithUserById(@Param("id") Long id);
}
