package net.datasa.tanoshimi.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiHtmlSanitizerTest {

    @Test
    void 리포트_서식_태그는_남긴다() {
        String html = "<h2>1일차</h2><p><b>도톤보리</b></p><table><tr><td>30분</td></tr></table><ul><li>지하철</li></ul>";

        String result = AiHtmlSanitizer.sanitize(html);

        assertThat(result).contains("<h2>1일차</h2>", "<b>도톤보리</b>", "<td>30분</td>", "<li>지하철</li>");
    }

    @Test
    void script_태그와_이벤트_속성은_제거한다() {
        String html = "<p>안녕</p><script>alert(1)</script><img src=\"https://example.com/a.png\" onerror=\"alert(2)\">";

        String result = AiHtmlSanitizer.sanitize(html);

        assertThat(result).contains("<p>안녕</p>").doesNotContain("<script", "alert", "onerror");
    }

    @Test
    void javascript_링크는_제거한다() {
        String result = AiHtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">클릭</a>");

        assertThat(result).doesNotContain("javascript:").contains("클릭");
    }

    @Test
    void 빈_입력은_빈_문자열() {
        assertThat(AiHtmlSanitizer.sanitize(null)).isEmpty();
        assertThat(AiHtmlSanitizer.sanitize("  ")).isEmpty();
    }
}
