package net.datasa.tanoshimi.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;

/**
 * 예전엔 @RestControllerAdvice(annotations = RestController.class) 로 스코프가 잡혀 있어서
 * 클래스 자체가 @RestController 인 곳에만 적용됐다. 그런데 화면 렌더링용 @Controller 안에
 * @ResponseBody API 메서드를 섞어 쓰는 컨트롤러가 여럿이라(PostController, PlannerController,
 * PartyController, PartyRoomController, RecommendationController, MyPageController) 그
 * 메서드들에서 예외가 나면 이 핸들러를 안 타고 스프링 기본 에러 페이지(스택트레이스 그대로)가
 * 사용자에게 그대로 노출됐다.
 *
 * 이제 모든 컨트롤러를 대상으로 하되, 실제로 예외를 던진 핸들러 메서드가 @ResponseBody 이거나
 * (또는 그 메서드가 속한 클래스가 @RestController 라서 메타 애노테이션으로 @ResponseBody 를
 * 갖고 있으면) 그때만 JSON 으로 응답한다. 그 외(화면 렌더링 메서드)는 예외를 그대로 다시 던져서
 * 기존과 똑같이 스프링/시큐리티의 표준 에러 처리(/error, /error/403 등)로 흘러가게 둔다 -
 * 화면 컨트롤러 쪽 동작은 이번 변경으로 전혀 바뀌지 않는다.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e, HandlerMethod handlerMethod) throws Exception {
        if (!isApiRequest(handlerMethod)) throw e;
        log.warn("BusinessException: {}", e.getMessage());
        return ResponseEntity.status(e.getErrorCode().status()).body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e, HandlerMethod handlerMethod) throws Exception {
        if (!isApiRequest(handlerMethod)) throw e;
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(fe -> fe.getDefaultMessage()).orElse(ErrorCode.INVALID_INPUT.message());
        return ResponseEntity.badRequest().body(ApiResponse.fail(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException e, HandlerMethod handlerMethod) throws Exception {
        if (!isApiRequest(handlerMethod)) throw e;
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.INVALID_INPUT.message()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e, HandlerMethod handlerMethod) throws Exception {
        if (!isApiRequest(handlerMethod)) throw e;
        log.error("unexpected error", e);
        return ResponseEntity.internalServerError().body(ApiResponse.fail("일시적인 오류가 발생했습니다."));
    }

    /** 메서드 자체가 @ResponseBody 이거나, 클래스가 @RestController(=메타애노테이션으로 @ResponseBody 포함)면 API 요청으로 본다. */
    private boolean isApiRequest(HandlerMethod handlerMethod) {
        if (handlerMethod == null) return false;
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), ResponseBody.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), ResponseBody.class);
    }
}
