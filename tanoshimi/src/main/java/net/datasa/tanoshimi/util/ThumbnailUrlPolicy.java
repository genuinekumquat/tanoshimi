package net.datasa.tanoshimi.util;

import java.util.regex.Pattern;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;

/**
 * 게시글·파티 썸네일 주소 검증.
 *
 * <p>썸네일 주소는 화면에서 이미지 src 뿐 아니라 다운로드 링크(href)에도 그대로 들어간다(파티방 사진 보기).
 * 예전엔 클라이언트가 보낸 문자열을 검증 없이 저장해서 {@code javascript:...} 를 넣으면 다른 파티원이
 * 다운로드를 누를 때 스크립트가 실행됐다(CodeQL js/xss-through-dom #5·#6). 그래서 실제로 쓰는 형태만 허용한다:
 * <ul>
 *   <li>업로드 API 가 돌려주는 같은 출처 경로 {@code /uploads/파일명}</li>
 *   <li>S3 등 외부 저장소 주소 {@code http(s)://...}</li>
 *   <li>시드 데이터의 자리표시자 {@code ph1}~ (기존 글을 수정할 때 그대로 다시 보내므로 허용)</li>
 * </ul>
 */
public final class ThumbnailUrlPolicy {

    private static final int MAX_LENGTH = 500;
    private static final Pattern UPLOAD_PATH = Pattern.compile("^/uploads/[A-Za-z0-9._-]+$");
    private static final Pattern HTTP_URL = Pattern.compile("^https?://[^\\s\"'<>\\\\]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACEHOLDER = Pattern.compile("^ph[0-9]{1,2}$");

    private ThumbnailUrlPolicy() {
    }

    public static boolean isAllowed(String url) {
        if (url == null || url.isBlank()) {
            return true; // 썸네일 없음
        }
        if (url.length() > MAX_LENGTH || url.contains("..")) {
            return false;
        }
        return UPLOAD_PATH.matcher(url).matches()
                || HTTP_URL.matcher(url).matches()
                || PLACEHOLDER.matcher(url).matches();
    }

    /** 허용되지 않는 주소면 INVALID_INPUT. */
    public static void validate(String url) {
        if (!isAllowed(url)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "썸네일 이미지 주소가 올바르지 않습니다.");
        }
    }
}
