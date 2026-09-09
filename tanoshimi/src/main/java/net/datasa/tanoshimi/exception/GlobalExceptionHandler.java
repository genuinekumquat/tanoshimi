package net.datasa.tanoshimi.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 예전엔 @RestControllerAdvice(annotations = RestController.class) 로 스코프가 잡혀 있어서
 * 클래스 자체가 @RestController 인 곳에만 적용됐다. 그런데 화면 렌더링용 @Controller 안에
 * @ResponseBody API 메서드를 섞어 쓰는 컨트롤러가 여럿이라(PostController, PlannerController,
 * PartyController, PartyRoomController, RecommendationController, MyPageController) 그
 * 메서드들에서 예외가 나면 이 핸들러를 안 타고 스프링 기본 에러 페이지(스택트레이스 그대로)가
 * 사용자에게 그대로 노출됐다.
 *
 * <p>이제 모든 컨트롤러를 대상으로 하되, "API 요청"일 때만 JSON 으로 응답한다. API 여부는
 * ① 예외를 던진 핸들러 메서드가 @ResponseBody(또는 클래스가 @RestController) 이거나
 * ② 요청 URI 가 {@code /api/} 로 시작하면 API 로 본다. ②가 필요한 이유: 시큐리티/서블릿
 * 필터에서 난 예외는 매칭된 핸들러 메서드가 없어서(BEST_MATCHING_HANDLER_ATTRIBUTE 없음)
 * ①만으로는 판정할 수 없다. 예전엔 {@code @ExceptionHandler} 파라미터로 {@code HandlerMethod}
 * 를 직접 받았는데, 핸들러 메서드가 없는 경우 스프링이 그 파라미터를 resolve 하지 못해
 * {@code @ExceptionHandler} 호출 자체가 IllegalStateException 으로 실패했다(로그인 등
 * 필터 경로에서 재현). 그래서 항상 resolve 되는 {@link HttpServletRequest} 로 바꿨다.
 *
 * <p>화면 렌더링 요청의 예외는 그대로 다시 던져 기존 스프링/시큐리티 표준 에러 처리
 * (/error, /error/403 등)로 흘러가게 둔다 - 화면 쪽 동작은 이번 변경으로 바뀌지 않는다.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e, HttpServletRequest request) throws Exception {
        if (!isApiRequest(request)) throw e;
        log.warn("BusinessException: {}", e.getMessage());
        return ResponseEntity.status(e.getErrorCode().status()).body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) throws Exception {
        if (!isApiRequest(request)) throw e;
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(fe -> fe.getDefaultMessage()).orElse(ErrorCode.INVALID_INPUT.message());
        return ResponseEntity.badRequest().body(ApiResponse.fail(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException e, HttpServletRequest request) throws Exception {
        if (!isApiRequest(request)) throw e;
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.INVALID_INPUT.message()));
    }

    /**
     * 필수 쿼리 파라미터/헤더 누락({@link ServletRequestBindingException}, 하위에
     * MissingServletRequestParameterException 포함)이나 타입 변환 실패
     * ({@link MethodArgumentTypeMismatchException} - 예: enum 파라미터에 잘못된 값)는
     * 잘못된 요청이므로 400 으로 돌려준다. 이걸 안 잡으면 handleUnexpected 로 빠져
     * 500 "일시적인 오류가 발생했습니다" 가 나간다.
     */
    @ExceptionHandler({ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e, HttpServletRequest request) throws Exception {
        if (!isApiRequest(request)) throw e;
        log.warn("bad request: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.INVALID_INPUT.message()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e, HttpServletRequest request) throws Exception {
        if (!isApiRequest(request)) throw e;
        log.error("unexpected error", e);
        return ResponseEntity.internalServerError().body(ApiResponse.fail("일시적인 오류가 발생했습니다."));
    }

    /**
     * ① 매칭된 핸들러 메서드가 @ResponseBody(또는 클래스가 @RestController) 이면 API,
     * ② 그게 없으면(필터 등에서 난 예외) URI 가 "/api/" 로 시작하면 API 로 본다.
     */
    private boolean isApiRequest(HttpServletRequest request) {
        if (request == null) return false;
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod hm) {
            return AnnotatedElementUtils.hasAnnotation(hm.getMethod(), ResponseBody.class)
                    || AnnotatedElementUtils.hasAnnotation(hm.getBeanType(), ResponseBody.class);
        }
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/api/");
    }
}
