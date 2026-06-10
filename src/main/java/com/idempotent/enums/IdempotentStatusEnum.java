package com.idempotent.enums;

public enum IdempotentStatusEnum {

    NOT_FOUND(-1, "未找到记录"),

    PROCESSING(0, "处理中"),

    COMPLETED(1, "已完成"),

    TOKEN_EXPIRED(2, "令牌已失效"),

    FAILED(3, "处理失败");

    private final Integer code;

    private final String desc;

    IdempotentStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static IdempotentStatusEnum getByCode(Integer code) {
        if (code == null) {
            return NOT_FOUND;
        }
        for (IdempotentStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return NOT_FOUND;
    }
}
