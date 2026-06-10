package com.idempotent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Data
public class ReceiptListDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String businessKey;

    private Long total;

    private List<ReceiptDTO> records;

    public static ReceiptListDTO of(String businessKey, Long total, List<ReceiptDTO> records) {
        ReceiptListDTO dto = new ReceiptListDTO();
        dto.setBusinessKey(businessKey);
        dto.setTotal(total != null ? total : 0L);
        dto.setRecords(records != null ? records : Collections.emptyList());
        return dto;
    }
}
