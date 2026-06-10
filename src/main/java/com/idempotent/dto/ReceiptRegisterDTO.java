package com.idempotent.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
public class ReceiptRegisterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "业务号不能为空")
    private String businessKey;

    private String token;

    private Integer status;

    private Integer responseCode;

    private String responseMessage;

    private String responseContent;

    private String errorMsg;

    private String receiptSource = "MANUAL";
}
