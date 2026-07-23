package com.jixiaotong.gateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenStore {

    private static final String TOKEN_KEY_PREFIX = "auth:token:";

    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    public Mono<Boolean> matches(Long userId, String token) {
        if (userId == null || token == null) {
            return Mono.just(false);
        }
        return reactiveStringRedisTemplate.opsForValue()
                .get(TOKEN_KEY_PREFIX + userId)
                .map(token::equals)
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.error("Redis 读取登录 Token 失败: {}", e.getMessage());
                    return Mono.just(false);
                });
    }
}
