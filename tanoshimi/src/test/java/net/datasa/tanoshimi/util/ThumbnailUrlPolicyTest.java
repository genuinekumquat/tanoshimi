package net.datasa.tanoshimi.util;

import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThumbnailUrlPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/uploads/2b1f0c8e-1234-4abc-9def-000000000000.webp",
            "/uploads/demo_osaka_shinsekai.jpg",
            "https://tanoshimi-bucket.s3.amazonaws.com/a.webp",
            "http://example.com/a.png",
            "ph1"
    })
    void 업로드_경로_http주소_자리표시자는_허용한다(String url) {
        assertThat(ThumbnailUrlPolicy.isAllowed(url)).isTrue();
    }

    @Test
    void 비어_있으면_허용한다() {
        assertThat(ThumbnailUrlPolicy.isAllowed(null)).isTrue();
        assertThat(ThumbnailUrlPolicy.isAllowed("")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JavaScript:alert(document.cookie)",
            " javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "vbscript:msgbox(1)",
            "//evil.example.com/a.png",
            "/uploads/../application.yml",
            "/admin/users",
            "https://example.com/a.png\" onerror=\"alert(1)"
    })
    void 스크립트_주소나_엉뚱한_경로는_거부한다(String url) {
        assertThat(ThumbnailUrlPolicy.isAllowed(url)).isFalse();
    }

    @Test
    void validate_는_거부할_때_INVALID_INPUT() {
        assertThatThrownBy(() -> ThumbnailUrlPolicy.validate("javascript:alert(1)"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
