package com.idempotent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "idempotent.token")
public class IdempotentProperties {

    private String prefix = "idempotent:token:";

    private Long expireTime = 600L;

    private String headerName = "Idempotent-Token";
}
