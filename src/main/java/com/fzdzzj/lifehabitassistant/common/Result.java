package com.fzdzzj.lifehabitassistant.common;

/** 统一 JSON 响应；文件下载接口不使用此包装。 */
public record Result<T>(int code, String message, T data) {
    public static <T> Result<T> success(T data) {
        return new Result<>(1, "success", data);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.code(), errorCode.message(), null);
    }

    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.code(), message, null);
    }
}
