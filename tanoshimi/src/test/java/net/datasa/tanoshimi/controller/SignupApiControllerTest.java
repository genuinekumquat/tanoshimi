package net.datasa.tanoshimi.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.auth.SessionLoginHelper;
import net.datasa.tanoshimi.auth.oauth.PendingSocialSignup;
import net.datasa.tanoshimi.domain.dto.SocialSignupRequest;
import net.datasa.tanoshimi.domain.entity.Gender;
import net.datasa.tanoshimi.domain.entity.Nationality;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 소셜 가입 완료 API는 세션의 PendingSocialSignup 이 진짜 있는지가 관문이다 - CustomOAuth2UserService가
 * 세션에 넣어둔 값을 그대로 신뢰하는 구조라, 세션이 없거나 만료된 상태로 이 API를 직접 호출하는
 * 경우(예: 브라우저 뒤로가기 후 재제출, 세션 타임아웃)를 반드시 막아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class SignupApiControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private SessionLoginHelper sessionLoginHelper;
    @Mock
    private HttpSession httpSession;
    @Mock
    private HttpServletRequest servletRequest;
    @Mock
    private HttpServletResponse servletResponse;

    private SignupApiController controller;

    @BeforeEach
    void setUp() {
        controller = new SignupApiController(userService, sessionLoginHelper, httpSession);
    }

    private SocialSignupRequest validRequest() {
        return new SocialSignupRequest("유자차", "yuzacha", null, "01011112222", "female",
                LocalDate.of(1998, 5, 14), "KR", true);
    }

    @Test
    void 세션에_pending_정보가_없으면_SOCIAL_SESSION_EXPIRED_예외를_던지고_회원가입을_시도하지_않는다() {
        when(httpSession.getAttribute(PendingSocialSignup.SESSION_KEY)).thenReturn(null);

        assertThatThrownBy(() -> controller.signupSocial(validRequest(), servletRequest, servletResponse))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_SESSION_EXPIRED);

        verify(userService, never()).signupSocial(any(), any());
        verify(sessionLoginHelper, never()).login(any(), any(), any());
    }

    @Test
    void 세션에_엉뚱한_타입의_값이_있어도_SOCIAL_SESSION_EXPIRED_예외() {
        // 방어적 캐스팅 확인 - 세션 키 충돌 등으로 다른 타입 객체가 들어있는 극단적인 경우.
        when(httpSession.getAttribute(PendingSocialSignup.SESSION_KEY)).thenReturn("이상한 값");

        assertThatThrownBy(() -> controller.signupSocial(validRequest(), servletRequest, servletResponse))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_SESSION_EXPIRED);
    }

    @Test
    void 정상_pending_세션이면_가입_후_세션값을_지우고_자동로그인시킨다() {
        PendingSocialSignup pending = new PendingSocialSignup("google", "social-id-1", "user@test.com", "유자차");
        when(httpSession.getAttribute(PendingSocialSignup.SESSION_KEY)).thenReturn(pending);

        UserEntity savedUser = UserEntity.createSocial("user@test.com", "yuzacha", "unusable-hash", "유자차", "01011112222",
                Gender.female, LocalDate.of(1998, 5, 14), Nationality.KR, "google", "social-id-1");
        when(userService.signupSocial(pending, validRequest())).thenReturn(savedUser);

        controller.signupSocial(validRequest(), servletRequest, servletResponse);

        verify(httpSession).removeAttribute(PendingSocialSignup.SESSION_KEY);

        ArgumentCaptor<CustomUserDetails> captor = ArgumentCaptor.forClass(CustomUserDetails.class);
        verify(sessionLoginHelper).login(captor.capture(), org.mockito.ArgumentMatchers.eq(servletRequest), org.mockito.ArgumentMatchers.eq(servletResponse));
        assertThat(captor.getValue().getEmail()).isEqualTo("user@test.com");
    }

    @Test
    void 가입_도중_예외가_나면_세션값을_지우거나_자동로그인시키지_않는다() {
        PendingSocialSignup pending = new PendingSocialSignup("google", "social-id-2", "dup@test.com", "유자차");
        when(httpSession.getAttribute(PendingSocialSignup.SESSION_KEY)).thenReturn(pending);
        when(userService.signupSocial(pending, validRequest()))
                .thenThrow(new BusinessException(ErrorCode.DUPLICATE_PHONE));

        assertThatThrownBy(() -> controller.signupSocial(validRequest(), servletRequest, servletResponse))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_PHONE);

        // 세션의 pending 정보는 재시도할 수 있게 남아있어야 한다 - 실패했는데 지워버리면 처음부터 다시 소셜 로그인해야 한다.
        verify(httpSession, never()).removeAttribute(PendingSocialSignup.SESSION_KEY);
        verify(sessionLoginHelper, never()).login(any(), any(), any());
    }

    // ------------------------------------------------------------ findPassword

    @Test
    void findPassword_이메일을_대소문자_공백_정규화해서_서비스에_넘긴다() {
        controller.findPassword(new SignupApiController.FindPasswordRequest("  User@Test.com "));

        verify(userService).issueTemporaryPassword("user@test.com");
    }
}
