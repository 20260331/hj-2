package com.idempotent.dto;

import com.idempotent.enums.IdempotentStatusEnum;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class IdempotentHistoryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String businessKey;

    private Long total;

    private List<IdempotentRecord> records;

    public static IdempotentHistoryDTO of(String businessKey, Long total, List<IdempotentRecord> records) {
        IdempotentHistoryDTO dto = new IdempotentHistoryDTO();
        dto.setBusinessKey(businessKey);
        dto.setTotal(total);
        dto.setRecords(records);
        return dto;
    }
}
