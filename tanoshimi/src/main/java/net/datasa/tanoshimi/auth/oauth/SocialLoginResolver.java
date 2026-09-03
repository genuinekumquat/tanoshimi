package net.datasa.tanoshimi.auth.oauth;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;

/**
 * CustomOAuth2UserService(google/naver)와 CustomOidcUserService(line)가 공유하는
 * 계정 연결/신규가입 판단 로직 - 두 곳에 따로 두면 정지 계정/이메일 중복 체크가 갈라질 위험이 있어 여기로 뺐다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class SocialLoginResolver {

    private final UserRepository userRepository;
    private final HttpSession httpSession;

    UserEntity resolveOrRequireSignup(OAuthAttributes attrs) {
        Optional<UserEntity> linked = userRepository.findBySocialProviderAndSocialId(attrs.provider(), attrs.socialId());
        if (linked.isPresent()) {
            UserEntity user = linked.get();
            if (!user.isActive()) {
                throw new OAuth2AuthenticationException(new OAuth2Error(SocialErrorCodes.ACCOUNT_SUSPENDED), "정지 계정");
            }
            return user;
        }

        if (attrs.email() != null && userRepository.existsByEmail(attrs.email())) {
            throw new OAuth2AuthenticationException(new OAuth2Error(SocialErrorCodes.EMAIL_ALREADY_USED), "이메일 중복");
        }

        httpSession.setAttribute(PendingSocialSignup.SESSION_KEY,
                new PendingSocialSignup(attrs.provider(), attrs.socialId(), attrs.email(), attrs.name()));
        log.info("신규 소셜 사용자 -> 추가정보 입력 유도 provider={}", attrs.provider());

        throw new OAuth2AuthenticationException(new OAuth2Error(SocialErrorCodes.SIGNUP_REQUIRED), "추가정보 입력 필요");
    }
}
