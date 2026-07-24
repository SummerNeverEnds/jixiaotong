package com.jixiaotong.performance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jixiaotong.performance.common.PageResult;
import com.jixiaotong.performance.common.Result;
import com.jixiaotong.performance.entity.SysOperationLog;
import com.jixiaotong.performance.security.AuthSupport;
import com.jixiaotong.performance.service.SysOperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operation-log")
@RequiredArgsConstructor
public class SysOperationLogController {

    private final SysOperationLogService operationLogService;

    @GetMapping("/page")
    public Result<PageResult<SysOperationLog>> page(@RequestParam(defaultValue = "1") Integer current,
                                                    @RequestParam(defaultValue = "10") Integer size,
                                                    @RequestParam(required = false) Long userId,
                                                    @RequestParam(required = false) String keyword) {
        AuthSupport.requireRole("ADMIN");
        Page<SysOperationLog> page = operationLogService.pageLogs(current, size, userId, keyword);
        return Result.success(PageResult.from(page));
    }
}
