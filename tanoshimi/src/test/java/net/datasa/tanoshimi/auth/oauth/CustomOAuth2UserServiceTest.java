package net.datasa.tanoshimi.auth.oauth;

import jakarta.servlet.http.HttpSession;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.entity.Gender;
import net.datasa.tanoshimi.domain.entity.Nationality;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CustomOAuth2UserService 는 "성공 로그인"과 "추가정보 필요/이메일 중복/정지 계정" 을 전부
 * OAuth2AuthenticationException 으로 흘려보내는 특이한 제어 흐름을 쓴다(Spring Security 의
 * oauth2Login().failureHandler() 가 실제로는 "신규가입 유도" 같은 정상 분기까지 처리하게 됨).
 * 그래서 이 서비스는 "예외가 안 나는 성공 케이스"와 "에러 코드별로 다른 예외가 나는 케이스" 를
 * 전부 커버해야 실제로 안전하다.
 *
 * DefaultOAuth2UserService.loadUser() 는 실제 HTTP 호출을 하므로, delegate 를 생성자 주입으로
 * 바꿔서(이번 변경) mock 으로 대체했다 - 예전에는 필드에서 직접 new 해서 테스트 자체가 불가능했다.
 */
@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private DefaultOAuth2UserService delegate;
    @Mock
    private SocialLoginResolver socialLoginResolver;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HttpSession httpSession;

    private CustomOAuth2UserService service;

    @BeforeEach
    void setUp() {
        service = new CustomOAuth2UserService(delegate, socialLoginResolver, userRepository, httpSession);
    }

    private OAuth2UserRequest googleRequest(Map<String, Object> attributes) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "dummy-token", Instant.now(), Instant.now().plusSeconds(3600));
        OAuth2User rawUser = new DefaultOAuth2User(
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),
                attributes, "sub");
        when(delegate.loadUser(any())).thenReturn(rawUser);
        return new OAuth2UserRequest(registration, accessToken);
    }

    private UserEntity newSocialUser(String provider, String socialId) {
        return UserEntity.createSocial("user@test.com", "yuzacha", "unusable-hash", "유자차", "01011112222",
                Gender.female, LocalDate.of(1998, 5, 14), Nationality.KR, provider, socialId);
    }

    @Test
    void 연동_세션이_없으면_resolver에게_위임해서_성공하면_CustomUserDetails를_반환한다() {
        // 계정연결/신규가입 판단 로직은 SocialLoginResolver로 옮겨갔다(google/naver/line 공용) -
        // 이 서비스는 링크 세션이 없는 이상 resolver 결과를 그대로 감싸기만 하면 된다.
        OAuth2UserRequest request = googleRequest(Map.of("sub", "google-uid-1", "email", "user@test.com", "name", "유자차"));
        UserEntity user = newSocialUser("google", "google-uid-1");
        when(httpSession.getAttribute(CustomOAuth2UserService.LINK_TARGET_SESSION_KEY)).thenReturn(null);
        when(socialLoginResolver.resolveOrRequireSignup(any())).thenReturn(user);

        OAuth2User result = service.loadUser(request);

        assertThat(result).isInstanceOf(CustomUserDetails.class);
        assertThat(((CustomUserDetails) result).getEmail()).isEqualTo("user@test.com");
    }

    @Test
    void 연동_세션이_없으면_resolver가_던지는_예외를_그대로_전파한다() {
        // ACCOUNT_SUSPENDED / EMAIL_ALREADY_USED / SIGNUP_REQUIRED 판단은 이제 전부
        // SocialLoginResolver 책임이다(SocialLoginResolverTest 참고 - 아직 없다면 추가 필요).
        OAuth2UserRequest request = googleRequest(Map.of("sub", "google-uid-1", "email", "user@test.com", "name", "유자차"));
        when(httpSession.getAttribute(CustomOAuth2UserService.LINK_TARGET_SESSION_KEY)).thenReturn(null);
        when(socialLoginResolver.resolveOrRequireSignup(any()))
                .thenThrow(new OAuth2AuthenticationException(
                        new org.springframework.security.oauth2.core.OAuth2Error(SocialErrorCodes.ACCOUNT_SUSPENDED), "정지 계정"));

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(SocialErrorCodes.ACCOUNT_SUSPENDED);
    }

    @Test
    void 연동_세션이_있으면_resolver를_거치지_않고_handleAccountLink로_간다() {
        // [social-link] 로그인된 로컬 계정에 소셜 계정을 연동하는 플로우 - 이미 인증된 세션의
        // 사용자 id로만 동작하고, 일반 로그인/가입 경로(resolver)는 타지 않는다.
        OAuth2UserRequest request = googleRequest(Map.of("sub", "google-uid-9", "email", "linked@test.com", "name", "유자차"));
        UserEntity target = newSocialUser("google", null);
        when(httpSession.getAttribute(CustomOAuth2UserService.LINK_TARGET_SESSION_KEY)).thenReturn(42L);
        when(userRepository.findById(42L)).thenReturn(Optional.of(target));
        when(userRepository.findBySocialProviderAndSocialId("google", "google-uid-9")).thenReturn(Optional.empty());

        OAuth2User result = service.loadUser(request);

        assertThat(result).isInstanceOf(CustomUserDetails.class);
        org.mockito.Mockito.verify(socialLoginResolver, org.mockito.Mockito.never()).resolveOrRequireSignup(any());
        org.mockito.Mockito.verify(httpSession).removeAttribute(CustomOAuth2UserService.LINK_TARGET_SESSION_KEY);
        org.mockito.Mockito.verify(httpSession).setAttribute(CustomOAuth2UserService.LINK_SUCCESS_SESSION_KEY, Boolean.TRUE);
    }

    @Test
    void 연동_대상_소셜계정이_이미_다른_계정에_연동돼있으면_LINK_CONFLICT_예외() {
        // target/other 둘 다 실제로 저장한 적 없는 순수 인메모리 엔티티라 id가 null인 채로
        // 남는다 - "이미 다른 계정에 연결돼 있다"를 제대로 흉내내려면 서로 다른 id를 명시적으로
        // 심어줘야 한다(안 그러면 둘 다 null이라 "같은 계정"으로 오판하거나 NPE가 난다).
        OAuth2UserRequest request = googleRequest(Map.of("sub", "google-uid-9", "email", "linked@test.com", "name", "유자차"));
        UserEntity target = newSocialUser("google", null);
        ReflectionTestUtils.setField(target, "id", 42L);
        UserEntity other = newSocialUser("google", "google-uid-9");
        ReflectionTestUtils.setField(other, "id", 99L);
        when(httpSession.getAttribute(CustomOAuth2UserService.LINK_TARGET_SESSION_KEY)).thenReturn(42L);
        when(userRepository.findById(42L)).thenReturn(Optional.of(target));
        when(userRepository.findBySocialProviderAndSocialId("google", "google-uid-9")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(SocialErrorCodes.LINK_CONFLICT);
    }

    @Test
    void 네이버_중첩응답도_동일한_흐름으로_동작한다() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("naver")
                .clientId("client-id")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
                .tokenUri("https://nid.naver.com/oauth2.0/token")
                .userInfoUri("https://openapi.naver.com/v1/nid/me")
                .userNameAttributeName("response")
                .clientName("Naver")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "dummy-token", Instant.now(), Instant.now().plusSeconds(3600));
        Map<String, Object> attributes = Map.of(
                "resultcode", "00",
                "response", Map.of("id", "naver-uid-1", "email", "user@test.com", "name", "유자차"));
        OAuth2User rawUser = new DefaultOAuth2User(
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),
                attributes, "response");
        when(delegate.loadUser(any())).thenReturn(rawUser);
        OAuth2UserRequest request = new OAuth2UserRequest(registration, accessToken);

        UserEntity user = newSocialUser("naver", "naver-uid-1");
        when(httpSession.getAttribute(CustomOAuth2UserService.LINK_TARGET_SESSION_KEY)).thenReturn(null);
        when(socialLoginResolver.resolveOrRequireSignup(any())).thenReturn(user);

        OAuth2User result = service.loadUser(request);

        assertThat(result).isInstanceOf(CustomUserDetails.class);
    }

    // NOTE: line은 scope에 openid가 있어서 Spring Security가 OIDC로 취급 - 실제로는 이 클래스가
    // 아니라 CustomOidcUserService.loadUser()를 탄다(SecurityConfig.oidcUserService() 참고).
    // 그래서 "line 레지스트레이션을 CustomOAuth2UserService에 흘려보내는" 테스트는 이제 실제
    // 운영 경로를 반영하지 못한다 - CustomOidcUserServiceTest 쪽으로 옮기는 걸 권장.
}
