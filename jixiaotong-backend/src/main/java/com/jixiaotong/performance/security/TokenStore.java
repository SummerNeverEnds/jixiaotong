package com.jixiaotong.performance.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenStore {

    private static final String TOKEN_KEY_PREFIX = "auth:token:";

    private final StringRedisTemplate stringRedisTemplate;

    public void save(Long userId, String token, long expireSeconds) {
        try {
            stringRedisTemplate.opsForValue().set(tokenKey(userId), token, expireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Redis 写入登录 Token 失败: {}", e.getMessage());
            throw new IllegalStateException("Redis 不可用，无法完成登录鉴权，请先启动 Redis");
        }
    }

    public String get(Long userId) {
        try {
            return stringRedisTemplate.opsForValue().get(tokenKey(userId));
        } catch (Exception e) {
            log.error("Redis 读取登录 Token 失败: {}", e.getMessage());
            return null;
        }
    }

    public boolean matches(Long userId, String token) {
        String cached = get(userId);
        return token != null && token.equals(cached);
    }

    public boolean isKickedByOtherLogin(Long userId, String token) {
        String cached = get(userId);
        return StringUtils.hasText(cached) && token != null && !token.equals(cached);
    }

    public void remove(Long userId) {
        try {
            stringRedisTemplate.delete(tokenKey(userId));
        } catch (Exception e) {
            log.warn("Redis 删除登录 Token 失败: {}", e.getMessage());
        }
    }

    private String tokenKey(Long userId) {
        return TOKEN_KEY_PREFIX + userId;
    }
}
