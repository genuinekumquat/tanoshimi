package net.datasa.tanoshimi.util;

public interface SmsSender {
    void sendVerificationCode(String phone, String code);
}
