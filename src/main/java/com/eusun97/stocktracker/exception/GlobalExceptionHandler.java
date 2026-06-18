package com.eusun97.stocktracker.exception;

import com.eusun97.stocktracker.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("business error code={} message={}", code.getCode(), e.getMessage());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));
        log.warn("validation error: {}", message);
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.getCode(), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        log.warn("constraint violation: {}", message);
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.getCode(), message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("malformed json: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.getCode(),
                        "요청 본문 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("type mismatch: param={}, value={}", e.getName(), e.getValue());
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.getCode(),
                        e.getName() + " 파라미터 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("illegal argument: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("unexpected error", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(),
                        ErrorCode.INTERNAL_ERROR.getMessage()));
    }

    private String formatFieldError(FieldError e) {
        return e.getField() + ": " + e.getDefaultMessage();
    }
}
