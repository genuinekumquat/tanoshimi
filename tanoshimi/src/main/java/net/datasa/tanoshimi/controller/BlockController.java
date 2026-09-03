package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.BlockService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/block")
@RequiredArgsConstructor
public class BlockController {

    private final UserRepository userRepository;
    private final BlockService blockService;

    @PostMapping("/{targetId}")
    public ApiResponse<Void> block(@PathVariable Long targetId, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        blockService.block(me, targetId);
        return ApiResponse.okMessage("차단했습니다.");
    }

    @DeleteMapping("/{targetId}")
    public ApiResponse<Void> unblock(@PathVariable Long targetId, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        blockService.unblock(me, targetId);
        return ApiResponse.okMessage("차단을 해제했습니다.");
    }

    @GetMapping("/{targetId}/status")
    public ApiResponse<Boolean> status(@PathVariable Long targetId, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return ApiResponse.ok(blockService.isBlockedByMe(me, targetId));
    }
}
