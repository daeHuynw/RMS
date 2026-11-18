package com.rms.common;

import com.rms.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// spring goi @RestControllerAdvice la class chuyen doi exception thanh response.
// Spring chọn handler có kiểu exception GẦN NHẤT với lỗi được ném, nên handler Exception ở cuối
// chỉ nhận những gì các handler phía trên không nhận.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        ResponseEntity<ApiError> response = build(ex.getStatus(), ex.getCode(), ex.getMessage(), request);
        if (ex.getRetryAfterSeconds() == null) {
            return response;
        }
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(response.getBody());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", request);
    }

    /** @Valid trên @RequestBody: mỗi ô sai thành một dòng trong errors. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.add(new ApiError.FieldError(error.getField(), error.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.add(new ApiError.FieldError(error.getObjectName(), error.getDefaultMessage())));
        return validation(errors, request);
    }

    /** Ràng buộc trên @RequestParam / @PathVariable ở controller KHÔNG có @Validated cấp class. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleInvalidParameter(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        List<ApiError.FieldError> errors = new ArrayList<>();
        for (var result : ex.getParameterValidationResults()) {
            String parameter = result.getMethodParameter().getParameterName();
            for (var error : result.getResolvableErrors()) {
                String field = error instanceof org.springframework.validation.FieldError fieldError
                        ? fieldError.getField()
                        : parameter;
                errors.add(new ApiError.FieldError(field, error.getDefaultMessage()));
            }
        }
        return validation(errors, request);
    }

    /** Controller có @Validated cấp class kiểm tham số qua AOP và ném exception này. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiError.FieldError> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.add(new ApiError.FieldError(lastNode(violation.getPropertyPath()), violation.getMessage()));
        }
        return validation(errors, request);
    }

    /** Thiếu body hoặc JSON hỏng / sai kiểu dữ liệu trong JSON. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is missing or is not valid JSON", request);
    }

    /** Ví dụ /orders/abc khi id phải là số. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        var errors = List.of(new ApiError.FieldError(ex.getName(), "has an invalid value"));
        return build(
                HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Parameter '" + ex.getName() + "' has an invalid value",
                errors, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "Uploaded file is too large", request);
    }

    /** Giữ 403 cho lỗi quyền ném từ trong controller/service, để handler chung bên dưới không biến nó thành 500. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", request);
    }

    /** Trùng khoá / vi phạm ràng buộc: 409. Chi tiết SQL chỉ ghi log, không trả cho client. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with existing data", request);
    }

    /** Tránh forward /error (hay bị security đổi thành 401) khi query/DB lỗi. Không lộ tên bảng/cột. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        log.error("Data access failure on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "DATA_ACCESS", "A database error occurred", request);
    }

    /**
     * Lưới cuối. Lỗi chuẩn của Spring MVC (405, 415, thiếu tham số, ...) tự mang mã HTTP qua ErrorResponse:
     * giữ đúng mã đó. Còn lại là lỗi không lường trước: ghi log đầy đủ, trả 500 chung chung.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse response) {
            HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
            if (status != null) {
                String detail = response.getBody().getDetail();
                return build(status, status.name(), detail != null ? detail : status.getReasonPhrase(), request);
            }
        }
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", request);
    }

    private ResponseEntity<ApiError> validation(List<ApiError.FieldError> errors, HttpServletRequest request) {
        errors.sort(Comparator.comparing(ApiError.FieldError::field));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", errors, request);
    }

    /** "topSelling.limit" → "limit". */
    private static String lastNode(Path path) {
        String name = null;
        for (Path.Node node : path) {
            name = node.getName();
        }
        return name != null ? name : path.toString();
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message, HttpServletRequest request) {
        return build(status, code, message, null, request);
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status, String code, String message, List<ApiError.FieldError> errors,
            HttpServletRequest request) {
        ApiError body = new ApiError(Instant.now(), status.value(), code, message, errors, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
