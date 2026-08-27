package net.datasa.tanoshimi.auth;

import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void 잠긴_계정은_저장소_조회_전에_예외를_던진다() {
        when(loginAttemptService.isLocked("user@test.com")).thenReturn(true);

        assertThatThrownBy(() -> service.loadUserByUsername("user@test.com"))
                .isInstanceOf(LockedException.class);

        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void 존재하지_않는_이메일은_예외를_던진다() {
        when(loginAttemptService.isLocked(any())).thenReturn(false);
        when(userRepository.findByEmail("nouser@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nouser@test.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void 소셜_전용_계정은_비밀번호_로그인을_거부한다() {
        UserEntity socialUser = mock(UserEntity.class);
        when(socialUser.isSocialAccount()).thenReturn(true);
        when(loginAttemptService.isLocked(any())).thenReturn(false);
        when(userRepository.findByEmail("social@test.com")).thenReturn(Optional.of(socialUser));

        assertThatThrownBy(() -> service.loadUserByUsername("social@test.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void 이메일의_대소문자와_공백을_정규화해서_조회한다() {
        UserEntity user = mock(UserEntity.class);
        when(user.isSocialAccount()).thenReturn(false);
        when(loginAttemptService.isLocked("user@test.com")).thenReturn(false);
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("  User@Test.com ");

        assertThat(result).isNotNull();
        verify(userRepository).findByEmail("user@test.com");
    }
}
