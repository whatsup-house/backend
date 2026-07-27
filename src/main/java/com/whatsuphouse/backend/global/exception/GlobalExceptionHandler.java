package com.whatsuphouse.backend.global.exception;

import com.whatsuphouse.backend.global.common.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        // 존재하지 않는 경로 요청 — 로그 불필요 (클라이언트 실수)
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResult.fail("RESOURCE_NOT_FOUND", "존재하지 않는 API입니다."));
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResult<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("[CustomException] {} - {}", errorCode.name(), errorCode.getMessage());
        // code는 ErrorCode enum name (안정적 식별자), message는 ko 폴백. (KAN-265)
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResult.fail(errorCode.name(), errorCode.getMessage(), e.getParams()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("[HttpMessageNotReadableException] {}", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResult.fail("INVALID_REQUEST_BODY", "요청 값이 올바르지 않습니다."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        log.warn("[ValidationException] {}", message);
        return ResponseEntity
                .badRequest()
                .body(ApiResult.fail("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleException(Exception e) {
        log.error("[UnhandledException] {} : {}", e.getClass().getSimpleName(), e.getMessage(), e);
        return ResponseEntity
                .internalServerError()
                .body(ApiResult.fail("INTERNAL_ERROR", "서버 오류가 발생했습니다."));
    }
}
