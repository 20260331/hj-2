package com.idempotent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "idempotent.receipt")
public class ReceiptProperties {

    private String prefix = "idempotent:receipt:";

    private Long expireDays = 30L;

    private Boolean autoReturnReceiptOnDuplicate = true;

    private String defaultSource = "AUTO";
}
