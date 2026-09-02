package net.datasa.tanoshimi.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "log", matchIfMissing = true)
public class LogEmailSender implements EmailSender {
    @Override
    public void sendVerificationCode(String email, String code) {
        log.info("[개발용 이메일] 수신주소={} 인증번호={}", email, code);
    }
}
