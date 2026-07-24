package com.jixiaotong.performance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jixiaotong.performance.entity.SysOperationLog;
import com.jixiaotong.performance.entity.SysUser;

public interface SysOperationLogService extends IService<SysOperationLog> {

    void record(SysUser user, String module, String action, String status);

    void record(Long userId, String username, String realName, String role,
                String module, String action, String status);

    Page<SysOperationLog> pageLogs(Integer current, Integer size, Long userId, String keyword);
}
