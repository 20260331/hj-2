package com.idempotent.dto;

import com.idempotent.enums.IdempotentStatusEnum;
import com.idempotent.enums.IdempotentTypeEnum;
import lombok.Data;

import java.io.Serializable;

@Data
public class IdempotentRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;

    private String businessKey;

    private IdempotentTypeEnum type;

    private IdempotentStatusEnum status;

    private String className;

    private String methodName;

    private String requestParam;

    private Long createTime;

    private Long processStartTime;

    private Long processEndTime;

    private Long expireTime;

    private String errorMsg;

    private String responseContent;

    private String receiptSource;

    private Long receiptTime;

    private Integer responseCode;

    private String responseMessage;
}
