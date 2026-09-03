package net.datasa.tanoshimi.auth;

import java.util.Map;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * line처럼 openid 스코프를 쓰는 OIDC 로그인의 principal.
 * CustomUserDetails를 상속해서 LoginSuccessHandler 등 기존의 (CustomUserDetails) 캐스팅이
 * 그대로 통하게 하면서, OidcAuthorizationCodeAuthenticationProvider가 요구하는 OidcUser 계약도 만족시킨다.
 */
public class CustomOidcUser extends CustomUserDetails implements OidcUser {

    private final OidcIdToken idToken;
    private final OidcUserInfo userInfo;

    public CustomOidcUser(UserEntity user, Map<String, Object> attributes, OidcIdToken idToken, OidcUserInfo userInfo) {
        super(user, attributes);
        this.idToken = idToken;
        this.userInfo = userInfo;
    }

    @Override public Map<String, Object> getClaims() { return idToken.getClaims(); }
    @Override public OidcUserInfo getUserInfo() { return userInfo; }
    @Override public OidcIdToken getIdToken() { return idToken; }
}
