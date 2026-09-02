package net.datasa.tanoshimi.auth.oauth;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 구글/네이버/라인이 내려주는 사용자 정보 형태가 서로 달라서(구글·라인은 최상위, 네이버는
 * response로 한 번 감싸서 옴) 이 변환기가 그 차이를 정확히 흡수하는지가 핵심이다.
 */
class OAuthAttributesTest {

    @Test
    void 구글_속성은_최상위에서_바로_꺼낸다() {
        Map<String, Object> attributes = Map.of(
                "sub", "google-uid-1",
                "email", "  User@Test.com ",
                "name", "유자차");

        OAuthAttributes result = OAuthAttributes.of("google", attributes);

        assertThat(result.provider()).isEqualTo("google");
        assertThat(result.socialId()).isEqualTo("google-uid-1");
        assertThat(result.email()).isEqualTo("user@test.com"); // trim + lowercase
        assertThat(result.name()).isEqualTo("유자차");
    }

    @Test
    void 네이버_속성은_response_안에_중첩되어_있다() {
        Map<String, Object> attributes = Map.of(
                "resultcode", "00",
                "message", "success",
                "response", Map.of(
                        "id", "naver-uid-1",
                        "email", "USER@TEST.COM",
                        "name", "유자차"));

        OAuthAttributes result = OAuthAttributes.of("naver", attributes);

        assertThat(result.provider()).isEqualTo("naver");
        assertThat(result.socialId()).isEqualTo("naver-uid-1");
        assertThat(result.email()).isEqualTo("user@test.com");
        assertThat(result.name()).isEqualTo("유자차");
    }

    @Test
    void 네이버_응답에_response_키가_없으면_예외() {
        Map<String, Object> attributes = Map.of("resultcode", "00");

        assertThatThrownBy(() -> OAuthAttributes.of("naver", attributes))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 네이버_응답의_response가_Map이_아니면_예외() {
        Map<String, Object> attributes = Map.of("response", "이상한_문자열_형태");

        assertThatThrownBy(() -> OAuthAttributes.of("naver", attributes))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 지원하지_않는_provider는_예외() {
        Map<String, Object> attributes = Map.of("sub", "x");

        assertThatThrownBy(() -> OAuthAttributes.of("kakao", attributes))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 라인_속성은_구글처럼_최상위에서_바로_꺼낸다() {
        Map<String, Object> attributes = Map.of(
                "sub", "line-uid-1",
                "email", "  User@Test.com ",
                "name", "유자차");

        OAuthAttributes result = OAuthAttributes.of("line", attributes);

        assertThat(result.provider()).isEqualTo("line");
        assertThat(result.socialId()).isEqualTo("line-uid-1");
        assertThat(result.email()).isEqualTo("user@test.com");
        assertThat(result.name()).isEqualTo("유자차");
    }

    @Test
    void 라인은_이메일_심사_전이면_email이_없어도_예외없이_null로_넘어간다() {
        // LINE은 이메일 권한 심사를 통과하지 못한 채널이면 email 스코프를 요청해도 아예
        // 내려주지 않는다 - 이 경우도 예외 없이 null 로 넘어가야 화면에서 직접 입력받을 수 있다.
        Map<String, Object> attributes = Map.of("sub", "line-uid-2", "name", "유자차");

        OAuthAttributes result = OAuthAttributes.of("line", attributes);

        assertThat(result.email()).isNull();
    }

    @Test
    void 이메일이_없어도_예외없이_null로_넘어간다() {
        // 이메일 스코프 미동의 등으로 email 자체가 안 내려올 수 있다.
        Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("sub", "google-uid-2");
        attributes.put("name", "유자차");

        OAuthAttributes result = OAuthAttributes.of("google", attributes);

        assertThat(result.email()).isNull();
    }
}
