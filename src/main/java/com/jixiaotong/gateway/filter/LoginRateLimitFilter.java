package com.jixiaotong.gateway.filter;

import com.jixiaotong.gateway.config.LoginRateLimitProperties;
import com.jixiaotong.gateway.support.JsonErrorWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginRateLimitFilter implements GlobalFilter, Ordered {

    private static final String KEY_PREFIX = "gateway:login:rl:";

    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;
    private final LoginRateLimitProperties properties;
    private final JsonErrorWriter jsonErrorWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!HttpMethod.POST.equals(request.getMethod())
                || !"/api/auth/login".equals(request.getURI().getPath())) {
            return chain.filter(exchange);
        }

        String ip = resolveClientIp(request);
        String key = KEY_PREFIX + ip;
        Duration window = Duration.ofSeconds(Math.max(1, properties.getWindowSeconds()));
        long max = Math.max(1, properties.getMaxRequests());

        return reactiveStringRedisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    Mono<Boolean> expireMono = count != null && count == 1
                            ? reactiveStringRedisTemplate.expire(key, window)
                            : Mono.just(true);
                    return expireMono.thenReturn(count == null ? 1L : count);
                })
                .flatMap(count -> {
                    if (count > max) {
                        log.warn("登录限流触发: ip={}, count={}, max={}", ip, count, max);
                        return jsonErrorWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, 429,
                                "登录请求过于频繁，请稍后再试");
                    }
                    return chain.filter(exchange);
                })
                .onErrorResume(e -> {
                    log.error("登录限流 Redis 异常，放行请求: {}", e.getMessage());
                    return chain.filter(exchange);
                });
    }

    private String resolveClientIp(ServerHttpRequest request) {
        List<String> forwarded = request.getHeaders().get("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            String first = forwarded.get(0);
            if (first != null && !first.isBlank()) {
                return first.split(",")[0].trim();
            }
        }
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
