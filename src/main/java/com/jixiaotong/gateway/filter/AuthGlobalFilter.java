package com.jixiaotong.gateway.filter;

import com.jixiaotong.gateway.security.JwtUtil;
import com.jixiaotong.gateway.security.TokenStore;
import com.jixiaotong.gateway.support.JsonErrorWriter;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;
    private final JsonErrorWriter jsonErrorWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //
        ServerHttpRequest request = exchange.getRequest();
        //放行
        if (HttpMethod.OPTIONS.equals(request.getMethod()) || isLoginPath(request)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return jsonErrorWriter.write(exchange, HttpStatus.UNAUTHORIZED, 401, "未登录或 Token 缺失，请先登录");
        }

        String token = authHeader.substring(7).trim();
        try {
            var claims = jwtUtil.parseClaims(token);
            Long userId = Long.valueOf(claims.getSubject());
            String role = claims.get("role") == null ? null : String.valueOf(claims.get("role"));
            String username = claims.get("username") == null ? null : String.valueOf(claims.get("username"));

            return tokenStore.matches(userId, token)
                    .flatMap(matched -> {
                        if (!Boolean.TRUE.equals(matched)) {
                            return jsonErrorWriter.write(exchange, HttpStatus.UNAUTHORIZED, 401, "登录已失效，请重新登录");
                        }
                        ServerHttpRequest mutated = request.mutate()
                                .headers(headers -> {
                                    headers.remove(HEADER_USER_ID);
                                    headers.remove(HEADER_USERNAME);
                                    headers.remove(HEADER_USER_ROLE);
                                    headers.set(HEADER_USER_ID, String.valueOf(userId));
                                    headers.set(HEADER_USERNAME, username == null ? "" : username);
                                    headers.set(HEADER_USER_ROLE, role == null ? "" : role);
                                })
                                .build();
                        return chain.filter(exchange.mutate().request(mutated).build());
                    });
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 校验失败: {}", e.getMessage());
            return jsonErrorWriter.write(exchange, HttpStatus.UNAUTHORIZED, 401, "Token 无效或已过期，请重新登录");
        }
    }

    private boolean isLoginPath(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        return HttpMethod.POST.equals(request.getMethod()) && "/api/auth/login".equals(path);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
