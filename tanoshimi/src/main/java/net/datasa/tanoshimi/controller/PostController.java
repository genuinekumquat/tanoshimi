package net.datasa.tanoshimi.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.PostRequest;
import net.datasa.tanoshimi.domain.dto.CommentRequest;
import net.datasa.tanoshimi.domain.entity.PostEntity;
import net.datasa.tanoshimi.domain.entity.PostCommentEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.PostCommentRepository;
import net.datasa.tanoshimi.repository.PostRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.BlockService;
import net.datasa.tanoshimi.service.PostService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.time.format.DateTimeFormatter;

@Controller
@RequiredArgsConstructor
public class PostController {

    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final UserRepository userRepository;
    private final PostService postService;
    private final BlockService blockService;

    @GetMapping("/board")
    public String board(@RequestParam(required = false) String region,
                        @RequestParam(defaultValue = "0") int page,
                        @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        UserEntity viewer = principal == null ? null : userRepository.findById(principal.getId()).orElse(null);
        model.addAttribute("posts", postService.boardList(region, PageRequest.of(page, 12), viewer));
        return "board/list";
    }
    @GetMapping("/board/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        PostEntity post = postRepository.findWithUserById(id).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        if (principal != null) {
            UserEntity viewer = userRepository.findById(principal.getId()).orElse(null);
            // TNSM-96: 차단 관계면 존재 자체를 감춘다(글이 없는 것과 동일하게 처리).
            if (viewer != null && blockService.isBlockedEitherWay(viewer, post.getUser())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "차단 관계로 볼 수 없는 게시글입니다.");
            }
        }
        model.addAttribute("post", post);
        model.addAttribute("comments", visibleComments(post, principal));
        model.addAttribute("isOwner", principal != null && post.getUser().getId().equals(principal.getId()));
        return "board/detail";
    }

    /** TNSM-96: 로그인 사용자와 차단 관계인 작성자의 댓글을 걸러낸다. */
    private List<PostCommentEntity> visibleComments(PostEntity post, CustomUserDetails principal) {
        List<PostCommentEntity> comments = postCommentRepository.findByPostOrderByCreatedAtAsc(post);
        if (principal == null) return comments;
        UserEntity viewer = userRepository.findById(principal.getId()).orElse(null);
        if (viewer == null) return comments;
        var blockedIds = blockService.relatedBlockedUserIds(viewer);
        if (blockedIds.isEmpty()) return comments;
        return comments.stream().filter(c -> !blockedIds.contains(c.getUser().getId())).toList();
    }

    @GetMapping("/api/posts/{id}/comments")
    @ResponseBody
    public ApiResponse<List<Map<String, Object>>> getComments(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        PostEntity post = postRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        List<PostCommentEntity> comments = visibleComments(post, principal);
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<Map<String, Object>> res = comments.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("parentId", c.getParent() != null ? c.getParent().getId() : null);
            map.put("content", c.getContent());
            map.put("authorId", c.getUser().getId());
            map.put("authorName", c.getUser().getName());
            map.put("authorImage", c.getUser().getProfileImageUrl() != null ? c.getUser().getProfileImageUrl() : "");
            map.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().format(df) : "");
            return map;
        }).collect(Collectors.toList());
        return ApiResponse.ok(res);
    }

    @PostMapping("/api/posts")
    @ResponseBody
    public ApiResponse<Long> write(@Valid @RequestBody PostRequest request, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity author = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return ApiResponse.ok(postService.write(author, request));
    }
    @PostMapping("/api/posts/{id}/like")
    @ResponseBody
    public ApiResponse<Void> like(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        PostEntity post = postRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        UserEntity user = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        postService.toggleLike(post, user);
        return ApiResponse.okMessage("완료");
    }

    @PostMapping("/api/posts/{id}/comments")
    @ResponseBody
    public ApiResponse<Void> comment(@PathVariable Long id, @RequestBody CommentRequest req,
                                     @AuthenticationPrincipal CustomUserDetails principal) {
        PostEntity post = postRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        UserEntity user = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        postService.comment(post, user, req.content(), req.parentId());
        return ApiResponse.okMessage("등록되었습니다.");
    }

    @DeleteMapping("/api/posts/{id}")
    @ResponseBody
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        PostEntity post = postRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        UserEntity user = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        postService.delete(post, user);
        return ApiResponse.okMessage("삭제완료");
    }

    @DeleteMapping("/api/posts/comments/{id}")
    @ResponseBody
    public ApiResponse<Void> deleteComment(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity user = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        postService.deleteComment(id, user);
        return ApiResponse.okMessage("삭제완료");
    }
}