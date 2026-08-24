package net.datasa.tanoshimi.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.auth.SessionLoginHelper;
import net.datasa.tanoshimi.auth.oauth.PendingSocialSignup;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.SignupRequest;
import net.datasa.tanoshimi.domain.dto.SocialSignupRequest;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SignupApiController {

    private final UserService userService;
    private final SessionLoginHelper sessionLoginHelper;
    private final HttpSession httpSession;

    @GetMapping("/email-check")
    public ApiResponse<Boolean> checkEmail(@RequestParam String email) {
        boolean available = userService.isEmailAvailable(email);
        return ApiResponse.ok(available ? "사용할 수 있는 이메일입니다." : "이미 사용 중인 이메일입니다.", available);
    }

    @PostMapping("/signup")
    public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
        userService.signup(request);
        return ApiResponse.okMessage("회원가입이 완료되었습니다.");
    }

    @PostMapping("/signup/social")
    public ApiResponse<Void> signupSocial(@Valid @RequestBody SocialSignupRequest request,
                                          HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        Object attribute = httpSession.getAttribute(PendingSocialSignup.SESSION_KEY);
        if (!(attribute instanceof PendingSocialSignup pending)) {
            throw new BusinessException(ErrorCode.SOCIAL_SESSION_EXPIRED);
        }
        UserEntity user = userService.signupSocial(pending, request);
        httpSession.removeAttribute(PendingSocialSignup.SESSION_KEY);
        sessionLoginHelper.login(new CustomUserDetails(user), servletRequest, servletResponse);
        return ApiResponse.okMessage("가입이 완료되었습니다.");
    }
}
