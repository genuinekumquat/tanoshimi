package net.datasa.tanoshimi.auth;

import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 폼 로그인에서 원래 비밀번호와, 비밀번호 재발급으로 대기 중인 임시 비밀번호를 둘 다 인정한다.
 * 원래 비밀번호가 맞으면 평소처럼 통과 - 재발급을 요청해놓고도 원래 비밀번호로 계속 로그인할 수
 * 있어야 하기 때문이다(UserService.issueTemporaryPassword 참고). 원래 비밀번호가 틀렸을 때만
 * 대기 중인 임시 비밀번호와 대조하고, 그걸로 로그인에 성공하는 순간 UserEntity.
 * promoteTemporaryPassword() 로 실제 password 에 반영 + 강제 변경 플래그를 켠다.
 */
@Component
public class TempPasswordAuthenticationProvider extends DaoAuthenticationProvider {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TempPasswordAuthenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
                                              UserRepository userRepository) {
        setUserDetailsService(userDetailsService);
        setPasswordEncoder(passwordEncoder);
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected void additionalAuthenticationChecks(UserDetails userDetails, UsernamePasswordAuthenticationToken authentication)
            throws AuthenticationException {
        if (authentication.getCredentials() == null) {
            throw new BadCredentialsException("Bad credentials");
        }
        String rawPassword = authentication.getCredentials().toString();

        if (passwordEncoder.matches(rawPassword, userDetails.getPassword())) {
            return;
        }

        CustomUserDetails details = (CustomUserDetails) userDetails;
        UserEntity user = userRepository.findById(details.getId())
                .orElseThrow(() -> new AuthenticationServiceException("사용자를 찾을 수 없습니다."));

        if (user.hasValidPendingTempPassword() && passwordEncoder.matches(rawPassword, user.getPendingTempPasswordHash())) {
            user.promoteTemporaryPassword();
            userRepository.save(user);
            return;
        }

        throw new BadCredentialsException("Bad credentials");
    }

    @Override
    protected Authentication createSuccessAuthentication(Object principal, Authentication authentication, UserDetails user) {
        // additionalAuthenticationChecks 에서 임시 비밀번호를 승격시켰을 수 있으니, 로그인 직후
        // 강제 변경 모달이 바로 뜨도록 최신 상태로 CustomUserDetails 를 다시 만들어 반영한다.
        CustomUserDetails details = (CustomUserDetails) user;
        UserEntity fresh = userRepository.findById(details.getId())
                .orElseThrow(() -> new AuthenticationServiceException("사용자를 찾을 수 없습니다."));
        CustomUserDetails refreshed = new CustomUserDetails(fresh, details.getAttributes());
        return super.createSuccessAuthentication(refreshed, authentication, refreshed);
    }
}
