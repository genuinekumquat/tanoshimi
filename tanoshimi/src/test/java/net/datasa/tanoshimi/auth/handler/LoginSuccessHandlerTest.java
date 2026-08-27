package net.datasa.tanoshimi.auth.handler;

import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.auth.LoginAttemptService;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginSuccessHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private Authentication authentication;

    @Test
    void 로그인_성공하면_실패_카운트를_초기화한다() throws Exception {
        LoginSuccessHandler handler = new LoginSuccessHandler(userRepository, loginAttemptService);
        UserEntity user = mock(UserEntity.class);
        CustomUserDetails principal = new CustomUserDetails(user);

        when(authentication.getPrincipal()).thenReturn(principal);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        // 이 리셋 호출이 빠지면, 로그인에 성공한 뒤에도 이전 실패 횟수가 남아 있다가
        // 몇 번만 더 틀려도 다시 잠기는 버그가 재발한다.
        verify(loginAttemptService).reset(principal.getUsername());
    }
}
