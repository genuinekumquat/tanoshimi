package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.NotificationSettingsUpdateRequest;
import net.datasa.tanoshimi.domain.dto.PasswordVerifyRequest;
import net.datasa.tanoshimi.domain.dto.PrivacyUpdateRequest;
import net.datasa.tanoshimi.domain.dto.ProfileUpdateRequest;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.AccountSettingsService;
import net.datasa.tanoshimi.service.UserNotificationSettingsService;
import net.datasa.tanoshimi.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * [account-settings 신규] 마이페이지 &gt; 계정 관리(/mypage/account) 화면의 저장용 JSON API.
 *
 * <p>MyPageController 가 이미 공개 프로필/내 여행/스냅/이미지업로드 등 여러 책임을 지고
 * 270줄 넘게 커져 있어(GET /mypage/account 페이지 로딩만 그쪽에 남기고) 이 화면의
 * 뮤테이션(POST/PUT) 엔드포인트는 새 컨트롤러로 분리했다 - MyPageController 의 기존
 * /api/mypage/profile-image, /api/mypage/intro 와 URL 프리픽스(/api/mypage/...)는
 * 그대로 맞춘다.
 */
@RestController
@RequestMapping("/api/mypage/account")
@RequiredArgsConstructor
public class AccountSettingsController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final AccountSettingsService accountSettingsService;
    private final UserNotificationSettingsService userNotificationSettingsService;

    private UserEntity currentUser(CustomUserDetails principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 비밀번호 확인 단계(화면에서 "수정" 버튼을 누르면 먼저 이걸 호출해 입력 필드를 연다).
     * 소셜 전용 계정은 확인할 진짜 비밀번호가 없으므로 항상 true - 화면도 이 경우 비밀번호
     * 입력란 자체를 보여주지 않는다(AccountSettingsService.updateProfile 주석 참고).
     */
    @PostMapping("/verify-password")
    @ResponseBody
    public ApiResponse<Boolean> verifyPassword(@RequestBody PasswordVerifyRequest req,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = currentUser(principal);
        if (me.isSocialAccount()) {
            return ApiResponse.ok(true);
        }
        boolean ok = userService.verifyPassword(me, req.password());
        return ok ? ApiResponse.ok(true) : ApiResponse.fail("비밀번호가 일치하지 않습니다.");
    }

    /**
     * 회원정보 수정. 클라이언트에서 이미 verify-password 를 거쳤더라도, 민감한 변경이라
     * 이 요청 자체에도 비밀번호를 실어 보내게 하고 서버가 다시 검증한다(AccountSettingsService
     * 참고) - 별도 "확인 단계" 호출을 신뢰하고 이 요청은 검증을 생략하지 않는다.
     */
    @PutMapping("/profile")
    @ResponseBody
    @Transactional
    public ApiResponse<?> updateProfile(@RequestBody ProfileUpdateRequest req,
                                        @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = currentUser(principal);
        accountSettingsService.updateProfile(me, req);
        return ApiResponse.okMessage("회원정보를 수정했습니다.");
    }

    @PutMapping("/notifications")
    @ResponseBody
    @Transactional
    public ApiResponse<?> updateNotifications(@RequestBody NotificationSettingsUpdateRequest req,
                                              @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = currentUser(principal);
        userNotificationSettingsService.update(me, req.pushEnabled(), req.emailEnabled(), req.focusModeEnabled(),
                req.notifyNewFollower(), req.notifyNewComment(), req.notifyPartyApplication(),
                req.notifyPartyApproved(), req.notifyPartyRejected(), req.notifyPartyKicked(), req.notifyTripReminder());
        return ApiResponse.okMessage("알림 설정을 저장했습니다.");
    }

    @PutMapping("/privacy")
    @ResponseBody
    @Transactional
    public ApiResponse<?> updatePrivacy(@RequestBody PrivacyUpdateRequest req,
                                        @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity me = currentUser(principal);
        accountSettingsService.changeVisibility(me, req.isPrivate());
        return ApiResponse.okMessage(req.isPrivate() ? "계정을 비공개로 전환했습니다." : "계정을 공개로 전환했습니다.");
    }
}

