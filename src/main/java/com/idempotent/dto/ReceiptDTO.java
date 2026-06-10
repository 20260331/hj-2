package com.idempotent.dto;

import com.idempotent.enums.IdempotentStatusEnum;
import lombok.Data;

import java.io.Serializable;

@Data
public class ReceiptDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;

    private String businessKey;

    private IdempotentStatusEnum status;

    private String statusDesc;

    private Integer responseCode;

    private String responseMessage;

    private String responseContent;

    private String errorMsg;

    private String className;

    private String methodName;

    private String requestParam;

    private String receiptSource;

    private Long createTime;

    private Long processStartTime;

    private Long processEndTime;

    private Long receiptTime;

    private Long expireSeconds;

    public static ReceiptDTO fromRecord(IdempotentRecord record, Long expireSeconds) {
        ReceiptDTO dto = new ReceiptDTO();
        if (record != null) {
            dto.setToken(record.getToken());
            dto.setBusinessKey(record.getBusinessKey());
            dto.setStatus(record.getStatus());
            dto.setStatusDesc(record.getStatus() != null ? record.getStatus().getDesc() : null);
            dto.setResponseCode(record.getResponseCode());
            dto.setResponseMessage(record.getResponseMessage());
            dto.setResponseContent(record.getResponseContent());
            dto.setErrorMsg(record.getErrorMsg());
            dto.setClassName(record.getClassName());
            dto.setMethodName(record.getMethodName());
            dto.setRequestParam(record.getRequestParam());
            dto.setReceiptSource(record.getReceiptSource());
            dto.setCreateTime(record.getCreateTime());
            dto.setProcessStartTime(record.getProcessStartTime());
            dto.setProcessEndTime(record.getProcessEndTime());
            dto.setReceiptTime(record.getReceiptTime());
        }
        dto.setExpireSeconds(expireSeconds != null ? expireSeconds : 0L);
        return dto;
    }
}
