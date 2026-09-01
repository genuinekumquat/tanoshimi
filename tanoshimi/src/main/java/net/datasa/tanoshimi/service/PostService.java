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
     * 지역명 정규화표. static/js/mypage-heatmap.js 의 NAME_ALIASES 와 같은 내용이다.
     * 여기에 없는 이름은 공백만 정리해서 그대로 쓴다(매칭이 안 되면 빈 목록이 될 뿐).
     *
     * <p><b>알려진 중복:</b> 이 표가 프론트(mypage-heatmap.js)와 여기 두 곳에 있다.
     * 인수인계 문서의 "지역 매핑 서버 일원화"(Phase 2)에 이미 올라와 있는 숙제이고,
     * 이번에 새로 생긴 게 아니라 그 목록에 한 줄 더해진 것이다.
     */
    private static final java.util.Map<String, String> REGION_ALIASES = java.util.Map.ofEntries(
            java.util.Map.entry("서울특별시", "서울"), java.util.Map.entry("경기도", "경기"),
            java.util.Map.entry("인천광역시", "인천"),
            java.util.Map.entry("강원특별자치도", "강원"), java.util.Map.entry("강원도", "강원"),
            java.util.Map.entry("충청북도", "충북"), java.util.Map.entry("충청남도", "충남"),
            java.util.Map.entry("세종특별자치시", "세종"),
            java.util.Map.entry("전라북도", "전북"), java.util.Map.entry("전북특별자치도", "전북"),
            java.util.Map.entry("전라남도", "전남"),
            java.util.Map.entry("경상북도", "경북"), java.util.Map.entry("경상남도", "경남"),
            java.util.Map.entry("대구광역시", "대구"), java.util.Map.entry("부산광역시", "부산"),
            java.util.Map.entry("광주광역시", "광주"), java.util.Map.entry("대전광역시", "대전"),
            java.util.Map.entry("울산광역시", "울산"),
            java.util.Map.entry("제주특별자치도", "제주"), java.util.Map.entry("제주도", "제주"),
            // 독도는 행정구역상 울릉군 소속이라 지도에서도 울릉에 병합돼 있다(v19-2).
            java.util.Map.entry("독도", "울릉"));

    public static String normalizeRegion(String raw) {
        String name = raw == null ? "" : raw.trim();
        return REGION_ALIASES.getOrDefault(name, name);
    }

    /**
     * [⑥ 마이페이지, v21 신규] 지도에서 지역을 클릭했을 때 여는 "그 지역 스냅 모아보기"
     * 페이지(/mypage/snaps)의 재료 - 그 지역 태그가 붙은 내 글만.
     *
     * <p>지도 쪽 지역명("울릉")과 글에 적힌 지역명("독도", "경상북도" 등)이 항상 같지는
     * 않아서, 양쪽 다 {@link #normalizeRegion} 으로 한 번 정규화한 뒤 비교한다.
     */
    @Transactional(readOnly = true)
    public List<PostEntity> regionSnaps(UserEntity user, String region) {
        if (region == null || region.isBlank()) return List.of();
        String target = normalizeRegion(region);
        return postRepository.findByUserOrderByCreatedAtDesc(user, PageRequest.of(0, REGION_SNAPS_SCAN_LIMIT))
                .getContent().stream()
                .filter(post -> post.getRegion() != null && !post.getRegion().isBlank())
                .filter(post -> normalizeRegion(post.getRegion()).equals(target))
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