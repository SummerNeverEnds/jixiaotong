package com.jixiaotong.performance.security;

import com.jixiaotong.performance.exception.BusinessException;
import com.jixiaotong.performance.exception.UnauthorizedException;

public final class AuthSupport {

    private AuthSupport() {
    }

    public static Long requireLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("未登录或登录已失效");
        }
        return userId;
    }

    public static void requireSelf(Long claimedUserId) {
        Long loginUserId = requireLoginUserId();
        if (claimedUserId == null || !loginUserId.equals(claimedUserId)) {
            throw new BusinessException("无权操作他人数据");
        }
    }

    public static void requireSelfOrAdmin(Long claimedUserId) {
        Long loginUserId = requireLoginUserId();
        if ("ADMIN".equals(UserContext.getRole())) {
            return;
        }
        if (claimedUserId == null || !loginUserId.equals(claimedUserId)) {
            throw new BusinessException("无权操作他人数据");
        }
    }

    public static void requireRole(String... roles) {
        requireLoginUserId();
        String current = UserContext.getRole();
        if (current == null) {
            throw new BusinessException("无权访问");
        }
        for (String role : roles) {
            if (current.equals(role)) {
                return;
            }
        }
        throw new BusinessException("无权访问");
    }

    public static Long resolveManagerScope(Long requestedManagerId) {
        requireRole("MANAGER", "ADMIN");
        if ("ADMIN".equals(UserContext.getRole())) {
            return requestedManagerId;
        }
        return requireLoginUserId();
    }

    public static Long requireActingManagerId(Long requestedManagerId) {
        requireRole("MANAGER", "ADMIN");
        Long loginId = requireLoginUserId();
        if ("MANAGER".equals(UserContext.getRole())) {
            if (requestedManagerId != null && !loginId.equals(requestedManagerId)) {
                throw new BusinessException("无权以其他经理身份操作");
            }
            return loginId;
        }
        if (requestedManagerId == null) {
            throw new BusinessException("管理员操作时必须指定经理ID");
        }
        return requestedManagerId;
    }
}
