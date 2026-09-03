package net.datasa.tanoshimi.service;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.PostRequest;
import net.datasa.tanoshimi.domain.entity.*;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.*;
import org.springframework.data.domain.Page;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final PartyRepository partyRepository;
    private final MyTripRepository myTripRepository;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;
    private final BlockService blockService;
    private final RegionCatalog regionCatalog;

    // ---------------------------------------------------------------- 단건/댓글 조회 (컨트롤러가 Repository 를 직접 부르지 않도록 여기로 모음)

    /** 게시글 1건 (작성자 fetch). 없으면 INVALID_INPUT. */
    @Transactional(readOnly = true)
    public PostEntity getWithUser(Long id) {
        return postRepository.findWithUserById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
    }

    /** 게시글 1건. 없으면 INVALID_INPUT. */
    @Transactional(readOnly = true)
    public PostEntity getPost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
    }

    /**
     * TNSM-96: 글의 댓글 중 viewer 와 차단 관계인 작성자의 댓글을 걸러낸 목록.
     * viewer 가 null(비로그인)이면 전부 보여준다.
     */
    @Transactional(readOnly = true)
    public List<PostCommentEntity> visibleComments(PostEntity post, UserEntity viewer) {
        List<PostCommentEntity> comments = postCommentRepository.findByPostOrderByCreatedAtAsc(post);
        if (viewer == null) return comments;
        var blockedIds = blockService.relatedBlockedUserIds(viewer);
        if (blockedIds.isEmpty()) return comments;
        return comments.stream().filter(c -> !blockedIds.contains(c.getUser().getId())).toList();
    }

    /** 파티 전용 사진첩(파티방 화면) - 그 파티에 묶인 글 중 썸네일 있는 것만 최신순. */
    @Transactional(readOnly = true)
    public List<PostEntity> partyPhotos(PartyEntity party) {
        return postRepository.findByPartyOrderByCreatedAtDesc(party).stream()
                .filter(p -> p.getThumbnailUrl() != null && !p.getThumbnailUrl().isBlank())
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PostEntity> boardList(String region, Pageable pageable) {
        return (region == null || region.isBlank())
                ? postRepository.findByBlindedFalseOrderByCreatedAtDesc(pageable)
                : postRepository.findByBlindedFalseAndRegionOrderByCreatedAtDesc(region, pageable);
    }

    /** TNSM-96: 로그인 사용자 기준으로 차단 관계인 글쓴이의 글을 제외하고 조회. */
    @Transactional(readOnly = true)
    public Page<PostEntity> boardList(String region, Pageable pageable, UserEntity viewer) {
        if (viewer == null) return boardList(region, pageable);
        var blockedIds = blockService.relatedBlockedUserIds(viewer);
        if (blockedIds.isEmpty()) return boardList(region, pageable);
        return (region == null || region.isBlank())
                ? postRepository.findByBlindedFalseAndUserIdNotInOrderByCreatedAtDesc(blockedIds, pageable)
                : postRepository.findByBlindedFalseAndRegionAndUserIdNotInOrderByCreatedAtDesc(region, blockedIds, pageable);
    }
    
    @Transactional(readOnly = true)
    public Page<PostEntity> myPosts(UserEntity user, Pageable pageable) {
        return postRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }

    /**
     * [⑥ 마이페이지] 지도 스냅용 - 지역 태그가 붙은 내 글만.
     *
     * <p>마이페이지 지도에서 지역에 마우스를 올리면 그 지역에서 찍은 사진이 뜨는데,
     * 그 재료다. 피드(12개)보다 넓게 보되 무한정 넘기지는 않도록 limit 을 둔다.
     */
    @Transactional(readOnly = true)
    public List<PostEntity> regionTaggedPosts(UserEntity user, int limit) {
        return postRepository.findByUserOrderByCreatedAtDesc(user, PageRequest.of(0, limit))
                .getContent().stream()
                .filter(post -> post.getRegion() != null && !post.getRegion().isBlank())
                .toList();
    }

    /** regionSnaps 가 훑어볼 최근 글 수. 한 사람이 이보다 많은 스냅을 올릴 규모가 아니다. */
    private static final int REGION_SNAPS_SCAN_LIMIT = 500;

    /**
     * [⑥ 마이페이지, v21 신규] 지도에서 지역을 클릭했을 때 여는 "그 지역 스냅 모아보기"
     * 페이지(/mypage/snaps)의 재료 - 그 지역 태그가 붙은 내 글만.
     *
     * <p>지도 쪽 지역명("울릉")과 글에 적힌 지역명("독도", "경상북도" 등)이 항상 같지는
     * 않아서, 비교는 {@link RegionCatalog} 로 정규화한 뒤에 한다.
     *
     * <p><b>권역을 클릭한 경우엔 그 아래 지역까지 모아준다</b> - "전남"을 눌렀는데 "여수"로
     * 태그된 스냅이 안 보이면 이상하기 때문이다(지도에서 상위 지역에 마우스를 올렸을 때
     * 하위 스냅이 같이 뜨는 것과 같은 규칙 - mypage-heatmap.js 의 snapsFor 참고).
     * 반대로 "여수"처럼 말단 지역을 눌렀을 때는 정확히 그 지역만 본다.
     *
     * <p><b>[v21 변경]</b> 예전엔 이 클래스 안에 별칭표를 따로 들고 있었는데,
     * region-tree.json 을 단일 출처로 삼는 {@link RegionCatalog} 로 옮겼다.
     */
    @Transactional(readOnly = true)
    public List<PostEntity> regionSnaps(UserEntity user, String region) {
        String target = regionCatalog.normalize(region);
        if (target == null) return List.of();
        // 권역 이름은 자기 자신을 가리킨다(RegionCatalog.areaOf) - 그걸로 권역/말단을 구분한다.
        boolean targetIsArea = target.equals(regionCatalog.areaOf(target));

        return postRepository.findByUserOrderByCreatedAtDesc(user, PageRequest.of(0, REGION_SNAPS_SCAN_LIMIT))
                .getContent().stream()
                .filter(post -> post.getRegion() != null && !post.getRegion().isBlank())
                .filter(post -> {
                    String name = regionCatalog.normalize(post.getRegion());
                    if (target.equals(name)) return true;
                    return targetIsArea && target.equals(regionCatalog.areaOf(name));
                })
                .toList();
    }

    @Transactional
    public Long write(UserEntity author, PostRequest req) {
        PartyEntity party = req.partyId() == null ? null : partyRepository.findById(req.partyId()).orElse(null);

        // [v19 신규] "내 여행"을 선택했으면 그 여행에 스냅을 묶는다 - 소유자 본인 여행만
        // 선택 가능(findByIdAndUser). 지역을 직접 안 적었으면 선택한 여행의 여행지로 채운다
        // (post-write.js 가 이미 자동으로 채워 보내지만, API를 직접 호출하는 경우까지 대비해
        // 서버에서도 한 번 더 보정).
        MyTripEntity trip = null;
        String region = req.region();
        if (req.tripId() != null) {
            trip = myTripRepository.findByIdAndUser(req.tripId(), author)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "선택한 여행을 찾을 수 없습니다."));
            if (region == null || region.isBlank()) {
                region = trip.getDestination();
            }
        }

        PostEntity post = PostEntity.builder()
                .user(author).party(party).trip(trip)
                .title(req.title()).content(req.content())
                .region(region).thumbnailUrl(req.thumbnailUrl())
                .build();
        Long id = postRepository.save(post).getId();
        fileStorageService.markActive(req.thumbnailUrl());
        return id;
    }
    
    @Transactional
    public void toggleLike(PostEntity post, UserEntity user) {
        if (postLikeRepository.existsByPostAndUser(post, user)) {
            postLikeRepository.deleteByPostAndUser(post, user);
            post.decreaseLike();
        } else {
            postLikeRepository.save(new net.datasa.tanoshimi.domain.entity.PostLikeEntity(post, user));
            post.increaseLike();
        }
        postRepository.save(post);
    }
    
    @Transactional
    public void comment(PostEntity post, UserEntity user, String content, Long parentId) {
        PostCommentEntity comment = new PostCommentEntity(post, user, content);
        if (parentId != null) {
            PostCommentEntity parent = postCommentRepository.findById(parentId).orElse(null);
            if (parent != null) {
                comment.setParent(parent);
            }
        }
        postCommentRepository.save(comment);
        
        if (!post.getUser().getId().equals(user.getId())) {
            notificationService.notify(post.getUser(), "new_comment",
                    "게시글에 댓글이 달렸어요",
                    user.getName() + "님: " + (content.length() > 40 ? content.substring(0, 40) + "..." : content),
                    "/board/" + post.getId());
        }
    }
    
    // 호환성을 위한 오버로딩
    @Transactional
    public void comment(PostEntity post, UserEntity user, String content) {
        comment(post, user, content, null);
    }
    
    @Transactional
    public void delete(PostEntity post, UserEntity requester) {
        if (!post.getUser().getId().equals(requester.getId()) && !requester.isAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        postLikeRepository.deleteByPost(post);
        postCommentRepository.deleteByPost(post);
        postRepository.delete(post);
    }

    @Transactional
    public void deleteComment(Long commentId, UserEntity requester) {
        net.datasa.tanoshimi.domain.entity.PostCommentEntity comment = postCommentRepository.findById(commentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        if (!comment.getUser().getId().equals(requester.getId()) && !requester.isAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        postCommentRepository.delete(comment);
    }
}