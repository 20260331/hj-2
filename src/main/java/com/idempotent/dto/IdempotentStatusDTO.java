package com.idempotent.dto;

import com.idempotent.enums.IdempotentStatusEnum;
import lombok.Data;

import java.io.Serializable;

@Data
public class IdempotentStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;

    private String businessKey;

    private IdempotentStatusEnum status;

    private String statusDesc;

    private Long expireSeconds;

    private IdempotentRecord record;

    public static IdempotentStatusDTO of(String token, String businessKey, IdempotentStatusEnum status, Long expireSeconds, IdempotentRecord record) {
        IdempotentStatusDTO dto = new IdempotentStatusDTO();
        dto.setToken(token);
        dto.setBusinessKey(businessKey);
        dto.setStatus(status);
        dto.setStatusDesc(status.getDesc());
        dto.setExpireSeconds(expireSeconds);
        dto.setRecord(record);
        return dto;
    }
}
