package com.idempotent.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotBlank(message = "商品名称不能为空")
    private String productName;

    private BigDecimal amount;

    private Integer quantity;

    private String userId;
}
