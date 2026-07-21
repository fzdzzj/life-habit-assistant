package com.fzdzzj.lifehabitassistant.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Result<Void>> handleApi(ApiException ex) {
        return response(ex.errorCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    ResponseEntity<Result<Void>> handleValidation(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream().findFirst().map(e -> e.getField() + ": " + e.getDefaultMessage()).orElse("参数不合法")
                : ex.getMessage();
        return response(ErrorCode.VALIDATION_FAILED, message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Result<Void>> handleDenied(AccessDeniedException ex) {
        return response(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.message());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Result<Void>> handleUnexpected(Exception ex) {
        return response(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message());
    }

    private ResponseEntity<Result<Void>> response(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.status()).body(Result.error(errorCode, message));
    }
}
