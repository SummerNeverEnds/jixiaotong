package com.jixiaotong.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jixiaotong.performance.entity.SysOperationLog;
import com.jixiaotong.performance.entity.SysUser;
import com.jixiaotong.performance.mapper.SysOperationLogMapper;
import com.jixiaotong.performance.service.SysOperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
public class SysOperationLogServiceImpl extends ServiceImpl<SysOperationLogMapper, SysOperationLog>
        implements SysOperationLogService {

    @Override
    public void record(SysUser user, String module, String action, String status) {
        if (user == null) {
            record(null, null, null, null, module, action, status);
            return;
        }
        record(user.getId(), user.getUsername(), user.getRealName(), user.getRole(),
                module, action, status);
    }

    @Override
    public void record(Long userId, String username, String realName, String role,
                       String module, String action, String status) {
        try {
            this.save(SysOperationLog.builder()
                    .userId(userId)
                    .username(username)
                    .realName(realName)
                    .role(role)
                    .module(module)
                    .action(action)
                    .status(StringUtils.hasText(status) ? status : "SUCCESS")
                    .createTime(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("写入操作日志失败: {}", e.getMessage());
        }
    }

    @Override
    public Page<SysOperationLog> pageLogs(Integer current, Integer size, Long userId, String keyword) {
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
                .orderByDesc(SysOperationLog::getCreateTime)
                .orderByDesc(SysOperationLog::getId);
        if (userId != null) {
            wrapper.eq(SysOperationLog::getUserId, userId);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SysOperationLog::getUsername, kw)
                    .or().like(SysOperationLog::getRealName, kw)
                    .or().like(SysOperationLog::getAction, kw)
                    .or().like(SysOperationLog::getModule, kw));
        }
        long pageNo = current == null || current < 1 ? 1 : current;
        long pageSize = size == null || size < 1 ? 10 : size;
        return this.page(new Page<>(pageNo, pageSize), wrapper);
    }
}
