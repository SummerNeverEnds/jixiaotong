package com.jixiaotong.performance.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jixiaotong.performance.dto.LoginDTO;
import com.jixiaotong.performance.entity.SysUser;

public interface SysUserService extends IService<SysUser> {

    SysUser login(LoginDTO loginDTO);

    void updateProfile(Long userId, String phone, String realName);

    void changePassword(Long userId, String oldPassword, String newPassword);

    void resetPassword(Long userId);

    void setDisabled(Long userId, boolean disabled);

    java.util.List<SysUser> listDeptEmployees(Long managerId);
}
