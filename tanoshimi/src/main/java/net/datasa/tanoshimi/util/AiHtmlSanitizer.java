package net.datasa.tanoshimi.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * AI 가 만든 HTML 을 화면에 그대로(th:utext) 넣기 전에 정화한다.
 *
 * <p>AI 입력에는 파티원이 쓴 일정 제목 같은 사용자 문자열이 들어가므로, "이 HTML 을 그대로 넣어줘"
 * 같은 문구로 &lt;script&gt;나 onerror 속성을 출력하게 만들 수 있다(프롬프트 인젝션 → 저장형 XSS).
 * 그래서 문서 서식용 태그(제목·문단·목록·표·링크·이미지 등)만 남기고 스크립트·스타일·이벤트
 * 속성·javascript: 링크는 전부 제거한다.
 */
public final class AiHtmlSanitizer {

    private static final Safelist SAFELIST = Safelist.relaxed();

    private AiHtmlSanitizer() {
    }

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.clean(html, SAFELIST);
    }
}
