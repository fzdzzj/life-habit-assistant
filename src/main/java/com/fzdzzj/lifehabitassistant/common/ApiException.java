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

    public static ApiException unauthorized(String message) {
        return new ApiException(ErrorCode.UNAUTHORIZED, message);
    }
}
