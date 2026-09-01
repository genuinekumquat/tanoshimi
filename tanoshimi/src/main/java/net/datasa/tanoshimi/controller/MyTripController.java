package net.datasa.tanoshimi.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.MyTripRequest;
import net.datasa.tanoshimi.domain.dto.MyTripView;
import net.datasa.tanoshimi.domain.dto.PostSnapView;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.MyTripService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * "내 여행" CRUD API - 마이페이지 편집 화면용. 담당: 김민규(⑥). v19 신규.
 * 실제 로직/권한 검사는 전부 MyTripService 에 있다(파티 자동 등록 건은 수정·삭제 거부).
 */
@RestController
@RequestMapping("/api/my-trips")
@RequiredArgsConstructor
public class MyTripController {

    private final MyTripService myTripService;
    private final UserRepository userRepository;

    @PostMapping
    public ApiResponse<MyTripView> create(@Valid @RequestBody MyTripRequest req,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = me(principal);
        return ApiResponse.ok("여행을 등록했어요.", MyTripView.of(myTripService.create(me, req)));
    }

    @PutMapping("/{id}")
    public ApiResponse<MyTripView> update(@PathVariable Long id, @Valid @RequestBody MyTripRequest req,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = me(principal);
        return ApiResponse.ok("여행을 수정했어요.", MyTripView.of(myTripService.update(me, id, req)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = me(principal);
        myTripService.delete(me, id);
        return ApiResponse.okMessage("여행을 삭제했어요.");
    }

    /**
     * [v20-7 신규] "📷 스냅 보기" - 이 여행에 연결된 스냅 목록(읽기 전용). 파티 자동 등록
     * 여행도 포함해서 부른다 - MyTripService.snapsForTrip 참고.
     */
    @GetMapping("/{id}/snaps")
    public ApiResponse<List<PostSnapView>> snaps(@PathVariable Long id,
                                                 @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = me(principal);
        return ApiResponse.ok(myTripService.snapsForTrip(me, id));
    }

    private UserEntity me(CustomUserDetails principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
