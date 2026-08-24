package net.datasa.tanoshimi.auth;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.UserRepository;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보안 메모: "없는 이메일"과 "비밀번호 틀림"을 구분해서 알려주지 않는다 (계정 열거 공격 방지).
 * 화면에는 항상 "이메일 또는 비밀번호가 올바르지 않습니다" 하나만 보여준다.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalized = email == null ? "" : email.trim().toLowerCase();

        if (loginAttemptService.isLocked(normalized)) {
            throw new LockedException("로그인 시도 횟수를 초과했습니다.");
        }

        UserEntity user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("일치하는 회원 없음"));

        if (user.isSocialAccount()) {
            throw new UsernameNotFoundException("소셜 전용 계정");
        }
        return new CustomUserDetails(user);
    }
}
