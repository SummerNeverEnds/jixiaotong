package com.jixiaotong.performance.controller;

import com.jixiaotong.performance.common.Result;
import com.jixiaotong.performance.dto.ChangePasswordDTO;
import com.jixiaotong.performance.dto.LoginDTO;
import com.jixiaotong.performance.dto.LoginVO;
import com.jixiaotong.performance.entity.SysUser;
import com.jixiaotong.performance.security.AuthSupport;
import com.jixiaotong.performance.security.JwtUtil;
import com.jixiaotong.performance.security.TokenStore;
import com.jixiaotong.performance.security.UserContext;
import com.jixiaotong.performance.service.SysOperationLogService;
import com.jixiaotong.performance.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService sysUserService;
    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;
    private final SysOperationLogService operationLogService;

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO loginDTO) {
        SysUser user = sysUserService.login(loginDTO);
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        long expireSeconds = jwtUtil.getExpireSeconds();
        tokenStore.remove(user.getId());
        tokenStore.save(user.getId(), token, expireSeconds);
        operationLogService.record(user, "认证", "用户登录", "SUCCESS");
        return Result.success(LoginVO.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(expireSeconds)
                .user(user)
                .build());
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        Long userId = UserContext.getUserId();
        if (userId != null) {
            tokenStore.remove(userId);
        }
        return Result.success();
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody ChangePasswordDTO dto) {
        Long currentUserId = AuthSupport.requireLoginUserId();
        Long targetUserId = dto.getUserId() != null ? dto.getUserId() : currentUserId;
        AuthSupport.requireSelfOrAdmin(targetUserId);
        sysUserService.changePassword(targetUserId, dto.getOldPassword(), dto.getNewPassword());
        tokenStore.remove(targetUserId);
        return Result.success();
    }
}
