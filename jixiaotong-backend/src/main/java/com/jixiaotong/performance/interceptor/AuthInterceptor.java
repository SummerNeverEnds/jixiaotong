package com.jixiaotong.performance.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jixiaotong.performance.common.Result;
import com.jixiaotong.performance.security.JwtUtil;
import com.jixiaotong.performance.security.TokenStore;
import com.jixiaotong.performance.security.UserContext;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "未登录或 Token 缺失，请先登录");
            return false;
        }

        String token = authHeader.substring(7).trim();
        try {
            var claims = jwtUtil.parseClaims(token);
            Long userId = Long.valueOf(claims.getSubject());
            String role = claims.get("role") == null ? null : String.valueOf(claims.get("role"));
            String username = claims.get("username") == null ? null : String.valueOf(claims.get("username"));

            if (!tokenStore.matches(userId, token)) {
                if (tokenStore.isKickedByOtherLogin(userId, token)) {
                    writeUnauthorized(response, "账号已在其他地方登录，您已被踢出");
                } else {
                    writeUnauthorized(response, "登录已失效，请重新登录");
                }
                return false;
            }

            UserContext.set(userId, username, role);
            request.setAttribute("currentUserId", userId);
            request.setAttribute("currentUserRole", role);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 校验失败: {}", e.getMessage());
            writeUnauthorized(response, "Token 无效或已过期，请重新登录");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(401, message)));
    }
}
