package net.datasa.tanoshimi.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 예전엔 @RestControllerAdvice(annotations = RestController.class) 로 스코프가 잡혀 있어서,
 * 화면 렌더링용 @Controller 안에 @ResponseBody API 메서드를 섞어 쓰는 컨트롤러(PostController,
 * PlannerController, PartyController, PartyRoomController, RecommendationController,
 * MyPageController)에서 예외가 나면 이 핸들러를 안 타고 스프링 기본 에러 페이지(스택트레이스
 * 그대로)가 노출됐다. 이제 모든 컨트롤러를 대상으로 하되, 실제로 예외를 던진 핸들러 메서드가
 * API 요청(메서드 자체가 @ResponseBody, 또는 클래스가 @RestController)인 경우에만 JSON으로
 * 응답하고, 화면 렌더링 메서드는 예외를 그대로 다시 던져 기존 동작(스프링/시큐리티 표준 에러
 * 처리)을 그대로 유지하는지를 검증한다.
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

    private HandlerMethod handlerMethodOf(Object bean, String methodName) throws NoSuchMethodException {
        Method method = bean.getClass().getMethod(methodName);
        return new HandlerMethod(bean, method);
    }

    @Test
    void 메서드에_ResponseBody가_있으면_JSON으로_응답한다() throws Exception {
        HandlerMethod apiMethod = handlerMethodOf(new MixedController(), "apiMethod");

        ResponseEntity<?> result = handler.handleBusiness(new BusinessException(ErrorCode.USER_NOT_FOUND), apiMethod);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 클래스가_RestController이면_메서드에_애노테이션이_없어도_JSON으로_응답한다() throws Exception {
        HandlerMethod restMethod = handlerMethodOf(new PureRestController(), "anyMethod");

        ResponseEntity<?> result = handler.handleBusiness(new BusinessException(ErrorCode.PARTY_NOT_FOUND), restMethod);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 화면_렌더링_메서드면_ResponseBody가_없으니_예외를_그대로_다시_던진다() throws Exception {
        // 여기서 다시 던져야 스프링/시큐리티의 표준 에러 처리(/error, /error/403 등)로 흘러가서
        // 화면 컨트롤러 쪽 기존 동작이 이번 변경으로 안 바뀐다.
        HandlerMethod viewMethod = handlerMethodOf(new MixedController(), "viewMethod");
        BusinessException e = new BusinessException(ErrorCode.USER_NOT_FOUND);

        assertThatThrownBy(() -> handler.handleBusiness(e, viewMethod)).isSameAs(e);
    }

    @Test
    void handlerMethod가_null이면_API_요청으로_보지_않고_예외를_다시_던진다() {
        // ExceptionHandlerExceptionResolver 밖에서 이 어드바이스가 호출되는 극단적인 경우를 대비.
        BusinessException e = new BusinessException(ErrorCode.USER_NOT_FOUND);

        assertThatThrownBy(() -> handler.handleBusiness(e, null)).isSameAs(e);
    }

    @Test
    void 알수없는_예외도_API_메서드에서는_JSON으로_응답한다() throws Exception {
        HandlerMethod apiMethod = handlerMethodOf(new MixedController(), "apiMethod");

        ResponseEntity<?> result = handler.handleUnexpected(new RuntimeException("boom"), apiMethod);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void 알수없는_예외는_화면_렌더링_메서드에서는_그대로_다시_던진다() throws Exception {
        HandlerMethod viewMethod = handlerMethodOf(new MixedController(), "viewMethod");
        RuntimeException e = new RuntimeException("boom");

        assertThatThrownBy(() -> handler.handleUnexpected(e, viewMethod)).isSameAs(e);
    }
}
