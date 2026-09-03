package net.datasa.tanoshimi.auth.oauth;

import jakarta.servlet.http.HttpSession;
import net.datasa.tanoshimi.domain.entity.Gender;
import net.datasa.tanoshimi.domain.entity.Nationality;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustomOAuth2UserService(google/naver)와 CustomOidcUserService(line)가 공유하는
 * 계정 연결/신규가입 판단 로직의 핵심. "성공"과 "가입유도/이메일중복/정지계정" 을 전부
 * OAuth2AuthenticationException으로 흘려보내는 특이한 제어흐름이라 전부 검증해야 안전하다.
 */
@ExtendWith(MockitoExtension.class)
class SocialLoginResolverTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private HttpSession httpSession;

    private SocialLoginResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SocialLoginResolver(userRepository, httpSession);
    }

    private UserEntity newSocialUser(String provider, String socialId) {
        return UserEntity.createSocial("user@test.com", "unusable-hash", "유자차", "01011112222",
                Gender.female, LocalDate.of(1998, 5, 14), Nationality.KR, provider, socialId);
    }

    @Test
    void 이미_연결된_활성_계정이면_그대로_반환한다() {
        UserEntity user = newSocialUser("google", "google-uid-1");
        when(userRepository.findBySocialProviderAndSocialId("google", "google-uid-1")).thenReturn(Optional.of(user));

        UserEntity result = resolver.resolveOrRequireSignup(new OAuthAttributes("google", "google-uid-1", "user@test.com", "유자차"));

        assertThat(result).isSameAs(user);
    }

    @Test
    void 이미_연결됐지만_정지된_계정이면_ACCOUNT_SUSPENDED_예외() {
        UserEntity user = newSocialUser("google", "google-uid-1");
        user.suspend(LocalDateTime.now().plusDays(7));
        when(userRepository.findBySocialProviderAndSocialId("google", "google-uid-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> resolver.resolveOrRequireSignup(new OAuthAttributes("google", "google-uid-1", "user@test.com", "유자차")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(SocialErrorCodes.ACCOUNT_SUSPENDED);
    }

    @Test
    void 연결안된_소셜이지만_같은_이메일이_이미_가입돼있으면_EMAIL_ALREADY_USED_예외() {
        // 계정 탈취 방지 - 로컬로 먼저 가입한 이메일에 몰래 소셜 계정을 자동 연결시키지 않는다.
        when(userRepository.findBySocialProviderAndSocialId("google", "google-uid-new")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("user@test.com")).thenReturn(true);

        assertThatThrownBy(() -> resolver.resolveOrRequireSignup(new OAuthAttributes("google", "google-uid-new", "user@test.com", "유자차")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(SocialErrorCodes.EMAIL_ALREADY_USED);
    }

    @Test
    void 완전히_새로운_사용자면_세션에_pending정보를_저장하고_SIGNUP_REQUIRED_예외() {
        when(userRepository.findBySocialProviderAndSocialId("google", "google-uid-brand-new")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);

        assertThatThrownBy(() -> resolver.resolveOrRequireSignup(new OAuthAttributes("google", "google-uid-brand-new", "new@test.com", "유자차")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(SocialErrorCodes.SIGNUP_REQUIRED);

        ArgumentCaptor<PendingSocialSignup> captor = ArgumentCaptor.forClass(PendingSocialSignup.class);
        verify(httpSession).setAttribute(eq(PendingSocialSignup.SESSION_KEY), captor.capture());
        assertThat(captor.getValue().provider()).isEqualTo("google");
        assertThat(captor.getValue().socialId()).isEqualTo("google-uid-brand-new");
        assertThat(captor.getValue().email()).isEqualTo("new@test.com");
    }

    @Test
    void 라인처럼_이메일이_없는_신규가입도_email_null인_채로_pending정보를_저장한다() {
        // LINE은 이메일 권한 심사 전이면 email 자체가 안 내려온다 - 그래도 정상적으로
        // 회원가입 유도(signup-social, needEmailInput=true)로 진행돼야 한다.
        when(userRepository.findBySocialProviderAndSocialId("line", "line-uid-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveOrRequireSignup(new OAuthAttributes("line", "line-uid-1", null, "유자차")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(SocialErrorCodes.SIGNUP_REQUIRED);

        ArgumentCaptor<PendingSocialSignup> captor = ArgumentCaptor.forClass(PendingSocialSignup.class);
        verify(httpSession).setAttribute(eq(PendingSocialSignup.SESSION_KEY), captor.capture());
        assertThat(captor.getValue().provider()).isEqualTo("line");
        assertThat(captor.getValue().email()).isNull();
        // email이 null이면 굳이 existsByEmail을 호출할 필요가 없다(&& 단락 평가).
        verify(userRepository, never()).existsByEmail(any());
    }
}
