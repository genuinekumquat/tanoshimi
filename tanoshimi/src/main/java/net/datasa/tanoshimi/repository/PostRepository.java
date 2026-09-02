package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.MyTripEntity;
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

    /** TNSM-96: 나와 차단 관계인 유저의 글은 목록에서 제외. blockedUserIds 가 비어있지 않을 때만 사용. */
    @EntityGraph(attributePaths = {"user"})
    @Query("select p from PostEntity p where p.blinded = false and p.user.id not in :blockedUserIds order by p.createdAt desc")
    Page<PostEntity> findByBlindedFalseAndUserIdNotInOrderByCreatedAtDesc(@Param("blockedUserIds") List<Long> blockedUserIds, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    @Query("select p from PostEntity p where p.blinded = false and p.region = :region and p.user.id not in :blockedUserIds order by p.createdAt desc")
    Page<PostEntity> findByBlindedFalseAndRegionAndUserIdNotInOrderByCreatedAtDesc(@Param("region") String region, @Param("blockedUserIds") List<Long> blockedUserIds, Pageable pageable);

    /** 마이페이지 진입 경로: 같은 테이블을 user_id 로만 필터링 */
    Page<PostEntity> findByUserOrderByCreatedAtDesc(UserEntity user, Pageable pageable);

    /**
     * 개별 여행 인정(⑥, TNSM 미부여): 파티 없이 혼자 다녀온 여행을 스냅의 지역 태그로 인정하기
     * 위한 조회. party IS NULL 인 글만 대상으로 한다 - party 가 있는 글은 그 파티가 완료됐을 때
     * TravelHeatmapService/TitleService 가 파티 기준으로 이미 세고 있으므로, 여기서 또 세면
     * 같은 여행이 두 번 잡힌다(중복 집계 방지가 이 조건의 핵심).
     * region 정제(공백/블랭크 제외)와 날짜별 중복 제거는 호출부에서 한다.
     */
    List<PostEntity> findByUserAndPartyIsNullAndBlindedFalse(UserEntity user);

    /** 파티 전용 게시판(사진첩) 진입 경로 - party_id 로 필터링, 이것도 같은 posts 테이블을 공유한다. */
    @EntityGraph(attributePaths = {"user"})
    List<PostEntity> findByPartyOrderByCreatedAtDesc(PartyEntity party);

    /**
     * [v19-4 신규] "이 여행에 스냅이 최소 1장 있는가" - MyTripService.isCountable 이 파티
     * 여행을 실제로 집계할지 판단할 때 쓴다(요청: 파티를 완료 처리만 해두고 실제로 안 갔어도
     * 여행 기록이 쌓이는 걸 막기 위해, 파티 여행도 연결된 스냅이 있어야 카운트되게 한다).
     * 블라인드된 글은 증거로 보지 않는다(findByUserAndPartyIsNullAndBlindedFalse 와 같은 기준).
     */
    boolean existsByTripAndBlindedFalse(MyTripEntity trip);

    /**
     * [v20-7 신규] "내 여행" 관리 화면의 "📷 스냅 보기" 버튼 - 이 여행에 연결된 스냅 전부를
     * 최신순으로. isCountable 판정 기준(existsByTripAndBlindedFalse)과 같은 조건(블라인드
     * 제외)을 쓴다 - 카운트 근거로 안 쳐주는 글을 "연결된 스냅"이라며 보여주면 혼란스럽다.
     */
    List<PostEntity> findByTripAndBlindedFalseOrderByCreatedAtDesc(MyTripEntity trip);

    /**
     * 게시글 상세(board/detail.html)에서 post.user.name 을 바로 찍어 쓰기 때문에,
     * open-in-view:false 상태에서 렌더링 시점에 LazyInitializationException 이 나지 않도록
     * user 를 미리 JOIN FETCH 해서 가져온다.
     */
    @Query("select p from PostEntity p join fetch p.user where p.id = :id")
    Optional<PostEntity> findWithUserById(@Param("id") Long id);
}
