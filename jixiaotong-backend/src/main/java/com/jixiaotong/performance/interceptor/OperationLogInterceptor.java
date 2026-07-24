package com.jixiaotong.performance.interceptor;

import com.jixiaotong.performance.entity.SysUser;
import com.jixiaotong.performance.mapper.SysUserMapper;
import com.jixiaotong.performance.security.UserContext;
import com.jixiaotong.performance.service.SysOperationLogService;
import com.jixiaotong.performance.util.OperationLogDescriptions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class OperationLogInterceptor implements HandlerInterceptor {

    private final SysOperationLogService operationLogService;
    private final SysUserMapper sysUserMapper;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String method = request.getMethod();
        if (!"POST".equalsIgnoreCase(method)
                && !"PUT".equalsIgnoreCase(method)
                && !"DELETE".equalsIgnoreCase(method)) {
            return;
        }

        String uri = request.getRequestURI();
        if (uri == null) {
            return;
        }
        if (uri.startsWith("/api/auth/login") || uri.startsWith("/api/operation-log")) {
            return;
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            return;
        }

        String[] desc = OperationLogDescriptions.resolve(method, uri);
        String status = (ex == null && response.getStatus() < 400) ? "SUCCESS" : "FAIL";

        String username = UserContext.getUsername();
        String role = UserContext.getRole();
        String realName = null;
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null) {
            realName = user.getRealName();
            if (username == null) {
                username = user.getUsername();
            }
            if (role == null) {
                role = user.getRole();
            }
        }

        operationLogService.record(userId, username, realName, role, desc[0], desc[1], status);
    }
}
