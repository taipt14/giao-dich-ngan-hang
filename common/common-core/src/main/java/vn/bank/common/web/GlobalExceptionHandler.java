package vn.bank.common.web;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import vn.bank.common.error.BusinessException;
import vn.bank.common.error.CommonErrorCode;
import vn.bank.common.error.ErrorCode;
import vn.bank.common.error.ErrorDetail;
import vn.bank.common.error.ErrorResponse;
import vn.bank.common.error.TechnicalException;

/**
 * Chuyen exception thanh {@link ErrorResponse}. Dang ky qua {@link ErrorHandlingAutoConfiguration}, service khong
 * can khai bao gi them.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Key MDC ma Micrometer Tracing dung de luu trace id. */
    public static final String TRACE_ID_MDC_KEY = "traceId";

    static final String VALIDATION_FAILED_MESSAGE = "Request validation failed";
    static final String MALFORMED_REQUEST_MESSAGE = "Malformed request";
    static final String INTERNAL_ERROR_MESSAGE = "Internal server error";

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        log.info("Business error {}: {}", ex.getErrorCode().code(), ex.getMessage());
        return toResponse(ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(TechnicalException.class)
    ResponseEntity<ErrorResponse> handleTechnical(TechnicalException ex) {
        log.error("Technical error {}: {}", ex.getErrorCode().code(), ex.getMessage(), ex);
        return toResponse(ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        var details = new ArrayList<ErrorDetail>();
        ex.getBindingResult().getAllErrors().forEach(error -> details.add(toDetail(error, error.getObjectName())));
        return toResponse(CommonErrorCode.VALIDATION_FAILED, VALIDATION_FAILED_MESSAGE, details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        var details = new ArrayList<ErrorDetail>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getAllErrors().forEach(error -> details.add(toDetail(error, error.getObjectName())));
            } else {
                var parameterName = result.getMethodParameter().getParameterName();
                result.getResolvableErrors()
                        .forEach(error -> details.add(new ErrorDetail(parameterName, error.getDefaultMessage())));
            }
        }
        return toResponse(CommonErrorCode.VALIDATION_FAILED, VALIDATION_FAILED_MESSAGE, details);
    }

    // Hai loi nay khong implement ErrorResponse cua Spring nen phai xu ly rieng, neu khong se roi vao 500
    @ExceptionHandler({HttpMessageNotReadableException.class, TypeMismatchException.class})
    ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex) {
        log.warn("Malformed request: {}", ex.getMessage());
        var status = HttpStatus.BAD_REQUEST;
        return toResponse(status.value(), status.name(), MALFORMED_REQUEST_MESSAGE, List.of());
    }

    /**
     * Ranh gioi cuoi: loi cua Spring MVC (thieu header, sai JSON, 404, 405...) giu nguyen status cua no; con lai la
     * loi khong xac dinh, tra 500 va khong lo chi tiet ben trong.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        if (ex instanceof org.springframework.web.ErrorResponse frameworkError) {
            return handleFramework(ex, frameworkError);
        }
        log.error("Unexpected error", ex);
        return toResponse(CommonErrorCode.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE, List.of());
    }

    private ResponseEntity<ErrorResponse> handleFramework(Exception ex, org.springframework.web.ErrorResponse error) {
        var status = error.getStatusCode();
        if (status.is5xxServerError()) {
            log.error("Request failed with status {}", status.value(), ex);
        } else {
            log.warn("Request rejected with status {}: {}", status.value(), ex.getMessage());
        }
        var message = error.getBody().getDetail();
        return toResponse(status.value(), toCode(status), message == null ? toCode(status) : message, List.of());
    }

    private static String toCode(HttpStatusCode status) {
        var known = HttpStatus.resolve(status.value());
        return known != null ? known.name() : "HTTP_" + status.value();
    }

    private static ErrorDetail toDetail(ObjectError error, String objectName) {
        var field = error instanceof FieldError fieldError ? fieldError.getField() : objectName;
        return new ErrorDetail(field, error.getDefaultMessage());
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode code, String message, List<ErrorDetail> details) {
        return toResponse(code.httpStatus(), code.code(), message, details);
    }

    private ResponseEntity<ErrorResponse> toResponse(
            int status, String code, String message, List<ErrorDetail> details) {
        var body = new ErrorResponse(code, message, MDC.get(TRACE_ID_MDC_KEY), clock.instant(), details);
        return ResponseEntity.status(status).body(body);
    }
}
