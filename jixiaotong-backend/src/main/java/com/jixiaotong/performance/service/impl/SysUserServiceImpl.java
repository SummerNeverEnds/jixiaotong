package com.jixiaotong.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jixiaotong.performance.dto.LoginDTO;
import com.jixiaotong.performance.entity.SysUser;
import com.jixiaotong.performance.exception.BusinessException;
import com.jixiaotong.performance.mapper.SysUserMapper;
import com.jixiaotong.performance.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
    private static final int MAX_LOGIN_FAIL_COUNT = 5;
    private static final int LOCK_HOURS = 1;
    private static final String DEFAULT_PASSWORD = "123456";

    private final PasswordEncoder passwordEncoder;

    @Override
    public SysUser login(LoginDTO loginDTO) {
        if (!StringUtils.hasText(loginDTO.getUsername()) || !StringUtils.hasText(loginDTO.getPassword())) {
            throw new BusinessException("工号和密码不能为空");
        }

        SysUser user = this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, loginDTO.getUsername()));

        if (user == null) {
            throw new BusinessException("工号不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.getLockUntil() != null && user.getLockUntil().getYear() >= 2099) {
            throw new BusinessException("账号已被管理员禁用，请联系管理员");
        }
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(now)) {
            throw new BusinessException("密码连续错误 5 次，账号已锁定，请于 "
                    + user.getLockUntil().toString().replace('T', ' ').substring(0, 16)
                    + " 后再尝试登录");
        }
        if (user.getLockUntil() != null) {
            clearLoginLock(user.getId());
            user.setLoginFailCount(0);
            user.setLockUntil(null);
        }

        if (!matchesPassword(loginDTO.getPassword(), user.getPassword())) {
            int failCount = (user.getLoginFailCount() == null ? 0 : user.getLoginFailCount()) + 1;
            SysUser updateUser = new SysUser();
            updateUser.setId(user.getId());
            updateUser.setLoginFailCount(failCount);
            if (failCount >= MAX_LOGIN_FAIL_COUNT) {
                LocalDateTime lockUntil = now.plusHours(LOCK_HOURS);
                updateUser.setLockUntil(lockUntil);
                this.updateById(updateUser);
                throw new BusinessException("密码连续错误 5 次，账号已锁定 1 小时");
            }
            this.updateById(updateUser);
            throw new BusinessException("密码错误，还可尝试 " + (MAX_LOGIN_FAIL_COUNT - failCount) + " 次");
        }

        if (!isBcryptHash(user.getPassword())) {
            this.update(new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, user.getId())
                    .set(SysUser::getPassword, passwordEncoder.encode(loginDTO.getPassword())));
        }

        if ((user.getLoginFailCount() != null && user.getLoginFailCount() > 0) || user.getLockUntil() != null) {
            clearLoginLock(user.getId());
        }

        user.setPassword(null);
        user.setLoginFailCount(0);
        user.setLockUntil(null);
        return user;
    }

    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (!StringUtils.hasText(storedPassword)) {
            return false;
        }
        if (isBcryptHash(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return storedPassword.equals(rawPassword);
    }

    private boolean isBcryptHash(String value) {
        return value != null && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }

    private void clearLoginLock(Long userId) {
        this.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getLoginFailCount, 0)
                .set(SysUser::getLockUntil, null));
    }

    @Override
    public void updateProfile(Long userId, String phone, String realName) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setPhone(phone);
        user.setRealName(realName);
        this.updateById(user);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        if (!StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
            throw new BusinessException("原密码和新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new BusinessException("新密码长度不能少于 6 位");
        }

        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!matchesPassword(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }

        SysUser updateUser = new SysUser();
        updateUser.setId(userId);
        updateUser.setPassword(passwordEncoder.encode(newPassword));
        this.updateById(updateUser);
    }

    @Override
    public void resetPassword(Long userId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        this.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPassword, passwordEncoder.encode(DEFAULT_PASSWORD))
                .set(SysUser::getLoginFailCount, 0)
                .set(SysUser::getLockUntil, null));
    }

    @Override
    public void setDisabled(Long userId, boolean disabled) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if ("ADMIN".equals(user.getRole()) && disabled) {
            throw new BusinessException("不能禁用管理员账号");
        }
        if (disabled) {
            this.update(new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, userId)
                    .set(SysUser::getLockUntil, LocalDateTime.of(2099, 1, 1, 0, 0)));
        } else {
            clearLoginLock(userId);
        }
    }

    @Override
    public java.util.List<SysUser> listDeptEmployees(Long managerId) {
        if (managerId == null) {
            throw new BusinessException("经理ID不能为空");
        }
        SysUser manager = this.getById(managerId);
        if (manager == null || !"MANAGER".equals(manager.getRole())) {
            throw new BusinessException("仅部门经理可查询本部门员工");
        }
        if (manager.getDeptId() == null) {
            throw new BusinessException("经理未绑定部门，无法查询员工");
        }
        java.util.List<SysUser> employees = this.list(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeptId, manager.getDeptId())
                .eq(SysUser::getRole, "EMPLOYEE")
                .orderByAsc(SysUser::getUsername));
        employees.forEach(u -> u.setPassword(null));
        return employees;
    }
}
