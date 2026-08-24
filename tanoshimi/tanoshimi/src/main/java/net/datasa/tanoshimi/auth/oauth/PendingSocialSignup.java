package net.datasa.tanoshimi.auth.oauth;

import java.io.Serializable;

/**
 * 소셜 로그인은 됐지만 아직 회원이 아닌 상태의 임시 정보.
 * users 테이블 필수 컬럼(phone, gender, birth_date, nationality)이 소셜에서 제공되지 않으므로
 * 추가정보 입력이 끝날 때까지 세션에만 보관한다.
 */
public record PendingSocialSignup(
        String provider,     // google / naver / line
        String socialId,
        String email,        // null 이면(라인 이메일 스코프 미승인 등) 화면에서 직접 입력받아야 함
        String name
) implements Serializable {
    public static final String SESSION_KEY = "PENDING_SOCIAL_SIGNUP";
}
