package com.jixiaotong.performance.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jixiaotong.performance.entity.SysNotification;

import java.util.Collection;
import java.util.List;

public interface SysNotificationService extends IService<SysNotification> {

    void notifyUser(Long userId, String title, String content, String type, String link);

    void notifyUsers(Collection<Long> userIds, String title, String content, String type, String link);

    List<SysNotification> listByUser(Long userId);

    long countUnread(Long userId);

    void markRead(Long id, Long userId);

    void markAllRead(Long userId);
}
