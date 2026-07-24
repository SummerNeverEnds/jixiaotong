package com.jixiaotong.performance.controller;

import com.jixiaotong.performance.common.PageResult;
import com.jixiaotong.performance.common.Result;
import com.jixiaotong.performance.entity.SysNotification;
import com.jixiaotong.performance.security.AuthSupport;
import com.jixiaotong.performance.service.SysNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class SysNotificationController {

    private final SysNotificationService sysNotificationService;

    @GetMapping("/page")
    public Result<PageResult<SysNotification>> page(@RequestParam(defaultValue = "1") Integer current,
                                                    @RequestParam(defaultValue = "20") Integer size) {
        Long userId = AuthSupport.requireLoginUserId();
        List<SysNotification> list = sysNotificationService.listByUser(userId);
        return Result.success(PageResult.of(list, current, size));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        Long userId = AuthSupport.requireLoginUserId();
        Map<String, Long> data = new HashMap<>();
        data.put("count", sysNotificationService.countUnread(userId));
        return Result.success(data);
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        Long userId = AuthSupport.requireLoginUserId();
        sysNotificationService.markRead(id, userId);
        return Result.success();
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        Long userId = AuthSupport.requireLoginUserId();
        sysNotificationService.markAllRead(userId);
        return Result.success();
    }
}
