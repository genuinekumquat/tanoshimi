package net.datasa.tanoshimi.auth.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.LoginAttemptService;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String email = request.getParameter("email");
        String message;
        if (exception instanceof DisabledException) {
            message = "정지된 계정입니다. 고객센터로 문의해 주세요.";
        } else if (exception instanceof LockedException) {
            message = "로그인 실패 횟수를 초과했습니다. " + loginAttemptService.lockMinutes() + "분 후 다시 시도해 주세요.";
        } else {
            loginAttemptService.recordFailure(email);
            message = "이메일 또는 비밀번호가 올바르지 않습니다.";
        }
        setDefaultFailureUrl("/login?error=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
        super.onAuthenticationFailure(request, response, exception);
    }
}
