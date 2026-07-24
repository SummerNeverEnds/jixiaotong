package com.jixiaotong.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jixiaotong.performance.entity.SysNotification;
import com.jixiaotong.performance.exception.BusinessException;
import com.jixiaotong.performance.mapper.SysNotificationMapper;
import com.jixiaotong.performance.service.SysNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class SysNotificationServiceImpl extends ServiceImpl<SysNotificationMapper, SysNotification>
        implements SysNotificationService {

    @Override
    public void notifyUser(Long userId, String title, String content, String type, String link) {
        if (userId == null || !StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            return;
        }
        this.save(SysNotification.builder()
                .userId(userId)
                .title(title.trim())
                .content(content.trim())
                .type(StringUtils.hasText(type) ? type.trim() : "SYSTEM")
                .link(link)
                .isRead(false)
                .createTime(LocalDateTime.now())
                .build());
    }

    @Override
    public void notifyUsers(Collection<Long> userIds, String title, String content, String type, String link) {
        if (CollectionUtils.isEmpty(userIds)) {
            return;
        }
        List<SysNotification> list = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            list.add(SysNotification.builder()
                    .userId(userId)
                    .title(title.trim())
                    .content(content.trim())
                    .type(StringUtils.hasText(type) ? type.trim() : "SYSTEM")
                    .link(link)
                    .isRead(false)
                    .createTime(now)
                    .build());
        }
        if (!list.isEmpty()) {
            this.saveBatch(list);
        }
    }

    @Override
    public List<SysNotification> listByUser(Long userId) {
        return this.list(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .orderByDesc(SysNotification::getCreateTime));
    }

    @Override
    public long countUnread(Long userId) {
        return this.count(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getIsRead, false));
    }

    @Override
    public void markRead(Long id, Long userId) {
        SysNotification notification = this.getById(id);
        if (notification == null || !notification.getUserId().equals(userId)) {
            throw new BusinessException("通知不存在");
        }
        if (Boolean.TRUE.equals(notification.getIsRead())) {
            return;
        }
        this.update(new LambdaUpdateWrapper<SysNotification>()
                .eq(SysNotification::getId, id)
                .eq(SysNotification::getUserId, userId)
                .set(SysNotification::getIsRead, true));
    }

    @Override
    public void markAllRead(Long userId) {
        this.update(new LambdaUpdateWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getIsRead, false)
                .set(SysNotification::getIsRead, true));
    }
}
