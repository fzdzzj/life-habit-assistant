package com.fzdzzj.lifehabitassistant.common;

public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.RESOURCE_CONFLICT, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(ErrorCode.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(ErrorCode.TOO_MANY_REQUESTS, message);
    }
}
