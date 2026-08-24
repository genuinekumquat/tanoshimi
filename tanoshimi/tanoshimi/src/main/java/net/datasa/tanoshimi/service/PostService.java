package net.datasa.tanoshimi.service;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.PostRequest;
import net.datasa.tanoshimi.domain.entity.*;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.*;
import org.springframework.data.domain.Page;
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
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public Page<PostEntity> boardList(String region, Pageable pageable) {
        return (region == null || region.isBlank())
                ? postRepository.findByBlindedFalseOrderByCreatedAtDesc(pageable)
                : postRepository.findByBlindedFalseAndRegionOrderByCreatedAtDesc(region, pageable);
    }
    
    @Transactional(readOnly = true)
    public Page<PostEntity> myPosts(UserEntity user, Pageable pageable) {
        return postRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }
    
    @Transactional
    public Long write(UserEntity author, PostRequest req) {
        PartyEntity party = req.partyId() == null ? null : partyRepository.findById(req.partyId()).orElse(null);
        PostEntity post = PostEntity.builder()
                .user(author).party(party)
                .title(req.title()).content(req.content())
                .region(req.region()).thumbnailUrl(req.thumbnailUrl())
                .build();
        return postRepository.save(post).getId();
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