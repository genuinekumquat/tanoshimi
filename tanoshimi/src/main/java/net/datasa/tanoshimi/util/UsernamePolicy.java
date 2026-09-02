package net.datasa.tanoshimi.util;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * [vanity-url 신규] 프로필 URL(/{username})에 쓰이는 아이디 규칙.
 *
 * <p>형식: 소문자 영문으로 시작, 그 뒤로 소문자/숫자/밑줄, 총 3~20자.
 * 대문자는 저장 전에 항상 소문자로 정규화한다(normalize) - "Yuja"와 "yuja"가 서로 다른
 * 계정으로 충돌 없이 공존하면 URL이 헷갈리므로, 대소문자 구분 없이 하나로 취급한다.
 * 숫자로 시작하지 못하게 막은 이유: URL 세그먼트 첫 글자가 숫자면 얼핏 다른 리소스 id처럼
 * 보일 수 있어서(가독성 판단, 기술적 제약은 아님).
 *
 * <p>예약어: 이 앱의 실제 최상위 라우트 세그먼트(모든 컨트롤러의 @…Mapping 첫 세그먼트를 grep해서
 * 확인한 목록) + SecurityConfig 의 정적 리소스 permitAll 루트 + 스프링 시큐리티/웹소켓 내장 경로.
 * 이 목록에 있는 값은 절대 사용자에게 아이디로 내줄 수 없다 - 내주는 순간 그 라우트가
 * 전체 서비스에서 영구히 막힌다("/{username}" 은 정확히 세그먼트 1개짜리 경로에만 매칭되므로,
 * "/board/list" 처럼 세그먼트가 더 있는 경로는 애초에 이 매핑과 충돌하지 않지만, 헷갈림을
 * 막기 위해 그런 경로의 첫 세그먼트도 함께 예약했다).
 */
public final class UsernamePolicy {

    private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9_]{2,19}$");

    /**
     * grep 결과 (2026-09 기준):
     * - 컨트롤러 @…Mapping 최상위 세그먼트: admin, api, board, error, login, messages,
     *   my-parties, mypage, party-board, planner, recommendations, signup, support, users
     * - SecurityConfig 명시적 permitAll 정적 리소스 루트: css, js, images, assets, vendor, model, uploads
     * - 폼로그인/소셜로그인/웹소켓 내장 경로(직접 매핑 코드는 없지만 스프링/이 앱 설정이 예약해둔 경로):
     *   logout, oauth2, ws
     */
    private static final Set<String> RESERVED = Set.of(
            "admin", "api", "board", "error", "login", "logout", "messages",
            "model", "my-parties", "mypage", "oauth2", "party-board", "planner",
            "recommendations", "signup", "support", "users",
            "css", "js", "images", "assets", "vendor", "uploads", "ws"
    );

    private UsernamePolicy() {}

    /** 저장/조회 전 항상 이걸 거친다 - 대소문자 구분 없이 하나로 취급하기 위한 정규화. */
    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase();
    }

    public static boolean isValidFormat(String normalized) {
        return normalized != null && FORMAT.matcher(normalized).matches();
    }

    public static boolean isReserved(String normalized) {
        return normalized != null && RESERVED.contains(normalized);
    }

    /** 형식 + 예약어 둘 다 통과해야 "쓸 수 있는 모양"이다(그 다음에 DB 중복 체크는 호출부 몫). */
    public static boolean isAllowed(String normalized) {
        return isValidFormat(normalized) && !isReserved(normalized);
    }
}
