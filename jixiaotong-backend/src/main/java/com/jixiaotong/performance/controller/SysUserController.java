package com.jixiaotong.performance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jixiaotong.performance.common.Result;
import com.jixiaotong.performance.entity.SysUser;
import com.jixiaotong.performance.exception.BusinessException;
import com.jixiaotong.performance.security.AuthSupport;
import com.jixiaotong.performance.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/page")
    public Result<Page<SysUser>> page(@RequestParam(defaultValue = "1") Integer current,
                                      @RequestParam(defaultValue = "10") Integer size,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Long deptId) {
        AuthSupport.requireRole("ADMIN");
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getRealName, keyword).or().like(SysUser::getUsername, keyword));
        }
        if (deptId != null) {
            wrapper.eq(SysUser::getDeptId, deptId);
        }
        Page<SysUser> page = sysUserService.page(new Page<>(current, size), wrapper);
        page.getRecords().forEach(u -> u.setPassword(null));
        return Result.success(page);
    }

    @PostMapping("/add")
    public Result<Void> add(@RequestBody SysUser user) {
        AuthSupport.requireRole("ADMIN");
        if (!StringUtils.hasText(user.getUsername())) {
            throw new BusinessException("工号不能为空");
        }
        if (!StringUtils.hasText(user.getRealName())) {
            throw new BusinessException("姓名不能为空");
        }
        String username = user.getUsername().trim();
        user.setUsername(username);
        user.setRealName(user.getRealName().trim());
        long exists = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (exists > 0) {
            throw new BusinessException("工号已存在，请更换后再添加");
        }
        if (!StringUtils.hasText(user.getPassword())) {
            user.setPassword("123456");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if ("EMPLOYEE".equals(user.getRole()) && !StringUtils.hasText(user.getJobLevel())) {
            user.setJobLevel("P1");
        }
        if (("EMPLOYEE".equals(user.getRole()) || "MANAGER".equals(user.getRole())) && user.getDeptId() == null) {
            throw new BusinessException("员工和经理必须选择所属部门");
        }
        sysUserService.save(user);
        return Result.success();
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody SysUser user) {
        AuthSupport.requireRole("ADMIN");
        SysUser existing = sysUserService.getById(user.getId());
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        if (!StringUtils.hasText(user.getRealName())) {
            throw new BusinessException("姓名不能为空");
        }
        if (("EMPLOYEE".equals(user.getRole()) || "MANAGER".equals(user.getRole())) && user.getDeptId() == null) {
            throw new BusinessException("员工和经理必须选择所属部门");
        }
        SysUser patch = new SysUser();
        patch.setId(user.getId());
        patch.setRealName(user.getRealName().trim());
        patch.setRole(user.getRole());
        patch.setDeptId(user.getDeptId());
        patch.setJobLevel("EMPLOYEE".equals(user.getRole()) ? user.getJobLevel() : null);
        patch.setPhone(user.getPhone());
        sysUserService.updateById(patch);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AuthSupport.requireRole("ADMIN");
        sysUserService.removeById(id);
        return Result.success();
    }

    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        AuthSupport.requireRole("ADMIN");
        sysUserService.resetPassword(id);
        return Result.success();
    }

    @PostMapping("/{id}/disable")
    public Result<Void> setDisabled(@PathVariable Long id, @RequestParam boolean disabled) {
        AuthSupport.requireRole("ADMIN");
        sysUserService.setDisabled(id, disabled);
        return Result.success();
    }

    @GetMapping("/dept-employees")
    public Result<java.util.List<SysUser>> listDeptEmployees(@RequestParam Long managerId) {
        Long actingManagerId = AuthSupport.requireActingManagerId(managerId);
        return Result.success(sysUserService.listDeptEmployees(actingManagerId));
    }
}
