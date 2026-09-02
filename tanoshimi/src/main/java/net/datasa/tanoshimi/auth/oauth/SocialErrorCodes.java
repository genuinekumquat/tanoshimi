package net.datasa.tanoshimi.auth.oauth;

public final class SocialErrorCodes {
    public static final String SIGNUP_REQUIRED = "signup_required";
    public static final String EMAIL_ALREADY_USED = "email_already_used";
    public static final String ACCOUNT_SUSPENDED = "account_suspended";
    /** [social-link 신규] 계정 연동 시도 중 - 그 소셜 identity 가 이미 다른 계정에 연동돼 있음. */
    public static final String LINK_CONFLICT = "link_conflict";
    private SocialErrorCodes() {}
}
