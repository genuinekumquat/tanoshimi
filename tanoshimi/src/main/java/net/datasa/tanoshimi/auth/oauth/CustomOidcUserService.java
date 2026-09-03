package net.datasa.tanoshimi.auth.oauth;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomOidcUser;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * line처럼 scope에 openid가 있는 소셜 로그인 전용 - Spring Security가 이런 요청을 OIDC로 인식해서
 * SecurityConfig의 userInfoEndpoint().userService()가 아니라 oidcUserService()로 이 서비스를 탄다.
 * (google/naver는 openid 스코프가 없어 CustomOAuth2UserService가 그대로 처리된다.)
 */
@Service
@RequiredArgsConstructor
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    // SecurityConfig가 빈으로 등록해준 것을 주입받는다(테스트에서 mock 대체 가능하도록 - CustomOAuth2UserService와 동일한 이유).
    private final OidcUserService delegate;
    private final SocialLoginResolver socialLoginResolver;

    @Override
    @Transactional(readOnly = true)
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthAttributes attrs = OAuthAttributes.of(registrationId, oidcUser.getAttributes());

        UserEntity user = socialLoginResolver.resolveOrRequireSignup(attrs);
        return new CustomOidcUser(user, oidcUser.getAttributes(), oidcUser.getIdToken(), oidcUser.getUserInfo());
    }
}
