package net.datasa.tanoshimi.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GlobalExceptionHandler 의 "API 요청일 때만 JSON 응답" 판정을 검증한다.
 * 판정 근거는 ① 매칭된 핸들러 메서드가 @ResponseBody / @RestController 인지,
 * ② (핸들러 메서드가 없으면) URI 가 "/api/" 로 시작하는지.
 * 화면 렌더링 요청의 예외는 그대로 다시 던져 기존 스프링/시큐리티 표준 에러 처리로 흘려보낸다.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Controller
    static class MixedController {
        public String viewMethod() { return "some/view"; }

        @ResponseBody
        public String apiMethod() { return "ok"; }
    }

    @RestController
    static class PureRestController {
        public String anyMethod() { return "ok"; }
    }

    /** 매칭된 핸들러 메서드를 담은 요청. */
    private MockHttpServletRequest requestFor(Object bean, String methodName) throws NoSuchMethodException {
        Method method = bean.getClass().getMethod(methodName);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, new HandlerMethod(bean, method));
        return request;
    }

    /** 매칭된 핸들러 메서드가 없는 요청(시큐리티/서블릿 필터에서 난 예외 상황). */
    private MockHttpServletRequest requestForUri(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    @Test
    void 메서드에_ResponseBody가_있으면_JSON으로_응답한다() throws Exception {
        var request = requestFor(new MixedController(), "apiMethod");

        ResponseEntity<?> result = handler.handleBusiness(new BusinessException(ErrorCode.USER_NOT_FOUND), request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 클래스가_RestController이면_메서드에_애노테이션이_없어도_JSON으로_응답한다() throws Exception {
        var request = requestFor(new PureRestController(), "anyMethod");

        ResponseEntity<?> result = handler.handleBusiness(new BusinessException(ErrorCode.PARTY_NOT_FOUND), request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 화면_렌더링_메서드면_ResponseBody가_없으니_예외를_그대로_다시_던진다() throws Exception {
        var request = requestFor(new MixedController(), "viewMethod");
        BusinessException e = new BusinessException(ErrorCode.USER_NOT_FOUND);

        assertThatThrownBy(() -> handler.handleBusiness(e, request)).isSameAs(e);
    }

    @Test
    void 핸들러_메서드가_없어도_URI가_api로_시작하면_JSON으로_응답한다() throws Exception {
        // 시큐리티 필터 등 컨트롤러 밖에서 난 예외 - HandlerMethod 없음.
        var request = requestForUri("/api/planner/3/vote");

        ResponseEntity<?> result = handler.handleUnexpected(new RuntimeException("boom"), request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void 핸들러_메서드가_없고_URI도_api가_아니면_예외를_그대로_다시_던진다() {
        var request = requestForUri("/login");
        RuntimeException e = new RuntimeException("boom");

        assertThatThrownBy(() -> handler.handleUnexpected(e, request)).isSameAs(e);
    }

    @Test
    void request가_null이면_API로_보지_않고_예외를_다시_던진다() {
        BusinessException e = new BusinessException(ErrorCode.USER_NOT_FOUND);

        assertThatThrownBy(() -> handler.handleBusiness(e, null)).isSameAs(e);
    }

    @Test
    void 알수없는_예외도_API_메서드에서는_JSON으로_응답한다() throws Exception {
        var request = requestFor(new MixedController(), "apiMethod");

        ResponseEntity<?> result = handler.handleUnexpected(new RuntimeException("boom"), request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void 알수없는_예외는_화면_렌더링_메서드에서는_그대로_다시_던진다() throws Exception {
        var request = requestFor(new MixedController(), "viewMethod");
        RuntimeException e = new RuntimeException("boom");

        assertThatThrownBy(() -> handler.handleUnexpected(e, request)).isSameAs(e);
    }

    @Test
    void 필수_파라미터_누락은_API_요청에서_400으로_응답한다() throws Exception {
        var request = requestFor(new MixedController(), "apiMethod");
        var e = new MissingServletRequestParameterException("type", "VoteType");

        ResponseEntity<?> result = handler.handleBadRequest(e, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 필수_파라미터_누락도_화면_렌더링_요청에서는_그대로_다시_던진다() throws Exception {
        var request = requestFor(new MixedController(), "viewMethod");
        var e = new MissingServletRequestParameterException("type", "VoteType");

        assertThatThrownBy(() -> handler.handleBadRequest(e, request)).isSameAs(e);
    }
}
