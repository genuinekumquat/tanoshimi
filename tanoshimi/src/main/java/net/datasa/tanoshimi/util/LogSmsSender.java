package net.datasa.tanoshimi.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "log", matchIfMissing = true)
public class LogSmsSender implements SmsSender {
    @Override
    public void sendVerificationCode(String phone, String code) {
        log.info("[개발용 SMS] 수신번호={} 인증번호={}", phone, code);
    }
}
