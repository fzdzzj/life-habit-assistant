package com.fzdzzj.lifehabitassistant.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(40000, HttpStatus.BAD_REQUEST, "参数不合法"),
    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "请先登录"),
    FORBIDDEN(40300, HttpStatus.FORBIDDEN, "无权访问"),
    RESOURCE_NOT_FOUND(40400, HttpStatus.NOT_FOUND, "资源不存在"),
    RESOURCE_CONFLICT(40900, HttpStatus.CONFLICT, "资源冲突"),
    INTERNAL_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final int code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(int code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public int code() { return code; }
    public HttpStatus status() { return status; }
    public String message() { return message; }
}
