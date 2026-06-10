package com.idempotent.exception;

public class IdempotentException extends RuntimeException {

    private Integer code;

    public IdempotentException(String message) {
        super(message);
        this.code = 409;
    }

    public IdempotentException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }
}
