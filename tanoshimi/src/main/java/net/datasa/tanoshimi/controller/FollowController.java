package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.FollowService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/follow")
@RequiredArgsConstructor
public class FollowController {

    private final UserRepository userRepository;
    private final FollowService followService;

    @PostMapping("/{targetId}")
    public ApiResponse<Void> follow(@PathVariable Long targetId, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        followService.follow(me, targetId);
        return ApiResponse.okMessage("팔로우했습니다.");
    }

    @DeleteMapping("/{targetId}")
    public ApiResponse<Void> unfollow(@PathVariable Long targetId, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        followService.unfollow(me, targetId);
        return ApiResponse.okMessage("언팔로우했습니다.");
    }
}
