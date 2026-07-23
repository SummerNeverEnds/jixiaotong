package com.jixiaotong.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret = "jixiaotong-performance-jwt-secret-key-2026-change-me";

    private long expireHours = 24;
}
