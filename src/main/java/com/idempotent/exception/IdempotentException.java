package com.idempotent.exception;

import com.idempotent.dto.IdempotentRecord;

public class IdempotentException extends RuntimeException {

    private Integer code;

    private IdempotentRecord receiptRecord;

    public IdempotentException(String message) {
        super(message);
        this.code = 409;
    }

    public IdempotentException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public IdempotentException(Integer code, String message, IdempotentRecord receiptRecord) {
        super(message);
        this.code = code;
        this.receiptRecord = receiptRecord;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public IdempotentRecord getReceiptRecord() {
        return receiptRecord;
    }

    public void setReceiptRecord(IdempotentRecord receiptRecord) {
        this.receiptRecord = receiptRecord;
    }
}
