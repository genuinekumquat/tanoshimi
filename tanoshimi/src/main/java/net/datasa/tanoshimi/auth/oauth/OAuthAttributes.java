package net.datasa.tanoshimi.auth.oauth;

import java.util.Map;

/**
 * 구글/네이버가 주는 사용자 정보 형태가 각각 달라서 하나로 맞춰주는 변환기.
 * - 구글: {sub, email, name} 최상위
 * - 네이버: {response:{id, email, name}} 처럼 중첩
 *
 * v16 제안서 기준 소셜 로그인은 Google·Naver만 지원 (LINE 제외 - 아래 주석 처리한 case 참고).
 * 라인(OIDC)은 {sub, email, name} 최상위 형태였고, email 스코프는 LINE 측 별도 심사가 필요해서
 * 심사 전에는 email 이 안 내려올 수 있어 email=null 로 넘기고 화면에서 직접 입력받게 되어 있었다.
 * 팀 논의로 재도입되면 case 주석만 해제하면 된다.
 */
public record OAuthAttributes(String provider, String socialId, String email, String name) {

    @SuppressWarnings("unchecked")
    public static OAuthAttributes of(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "google" -> new OAuthAttributes("google",
                    str(attributes.get("sub")), lower(str(attributes.get("email"))), str(attributes.get("name")));
            // case "line" -> new OAuthAttributes("line",
            //         str(attributes.get("sub")), lower(str(attributes.get("email"))), str(attributes.get("name")));
            case "naver" -> {
                Object raw = attributes.get("response");
                if (!(raw instanceof Map)) throw new IllegalStateException("네이버 응답 형식이 예상과 다릅니다.");
                Map<String, Object> response = (Map<String, Object>) raw;
                yield new OAuthAttributes("naver",
                        str(response.get("id")), lower(str(response.get("email"))), str(response.get("name")));
            }
            default -> throw new IllegalArgumentException("지원하지 않는 소셜 로그인: " + registrationId);
        };
    }

    private static String str(Object v) { return v == null ? null : String.valueOf(v); }
    private static String lower(String v) { return v == null ? null : v.trim().toLowerCase(); }
}
