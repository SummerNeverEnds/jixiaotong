package com.jixiaotong.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "gateway.login-rate-limit")
public class LoginRateLimitProperties {

    private long windowSeconds = 60;

    private long maxRequests = 20;
}
