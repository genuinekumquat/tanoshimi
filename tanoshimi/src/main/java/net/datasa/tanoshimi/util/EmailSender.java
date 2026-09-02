package net.datasa.tanoshimi.util;

public interface EmailSender {
    void sendVerificationCode(String email, String code);
}
