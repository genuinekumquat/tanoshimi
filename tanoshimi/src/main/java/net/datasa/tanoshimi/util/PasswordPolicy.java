package net.datasa.tanoshimi.util;

/** 비밀번호 규칙: 8~20자, 영문/숫자/특수문자 중 2가지 이상. 회원가입/비밀번호 변경에서 공용으로 사용. */
public final class PasswordPolicy {

    private static final String SPECIALS = "!@#$%^&*()_+-=[]{}|;:',.<>/?~`\"\\";

    private PasswordPolicy() {}

    public static boolean isValid(String password) {
        if (password == null || password.length() < 8 || password.length() > 20) return false;
        if (password.chars().anyMatch(Character::isWhitespace)) return false;

        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> SPECIALS.indexOf(c) >= 0);
        int kinds = (hasLetter ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
        return kinds >= 2;
    }
}
