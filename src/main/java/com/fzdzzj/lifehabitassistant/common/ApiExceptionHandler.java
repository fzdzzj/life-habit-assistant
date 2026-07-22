package com.fzdzzj.lifehabitassistant.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Result<Void>> handleApi(ApiException ex) {
        return response(ex.errorCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Result<Void>> handleValidation(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream().findFirst().map(e -> e.getField() + ": " + e.getDefaultMessage()).orElse("参数不合法")
                : validationMessage(ex);
        return response(ErrorCode.VALIDATION_FAILED, message);
    }

    private String validationMessage(Exception ex) {
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            return mismatch.getName() + ": 格式不正确";
        }
        if (ex instanceof MissingServletRequestParameterException missing) {
            return missing.getParameterName() + ": 不得为空";
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "请求体格式不正确";
        }
        return ex.getMessage();
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
