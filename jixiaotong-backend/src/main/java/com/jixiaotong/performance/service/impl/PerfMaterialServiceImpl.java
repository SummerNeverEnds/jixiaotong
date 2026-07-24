package com.jixiaotong.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jixiaotong.performance.entity.PerfMaterial;
import com.jixiaotong.performance.entity.SysUser;
import com.jixiaotong.performance.exception.BusinessException;
import com.jixiaotong.performance.mapper.PerfMaterialMapper;
import com.jixiaotong.performance.mapper.SysUserMapper;
import com.jixiaotong.performance.service.PerfEmployeeScoreService;
import com.jixiaotong.performance.service.PerfMaterialService;
import com.jixiaotong.performance.service.SysNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerfMaterialServiceImpl extends ServiceImpl<PerfMaterialMapper, PerfMaterial> implements PerfMaterialService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final PerfEmployeeScoreService perfEmployeeScoreService;
    private final SysNotificationService sysNotificationService;
    private final SysUserMapper sysUserMapper;
    private static final String MATERIAL_CACHE_KEY = "perf:material:list:v3";
    private static final long MATERIAL_CACHE_HOURS = 2;
    public static final String STATUS_ON_SHELF = "ON_SHELF";
    public static final String STATUS_OFF_SHELF = "OFF_SHELF";

    @Override
    public List<PerfMaterial> getMaterialListWithCache() {
        offShelfPastCycleMaterials();
        try {
            Object cached = redisTemplate.opsForValue().get(MATERIAL_CACHE_KEY);
            List<PerfMaterial> fromCache = convertCachedMaterials(cached);
            if (fromCache != null) {
                return fromCache;
            }
        } catch (Exception e) {
            log.warn("Redis 暂不可用，资料列表降级查库: {}", e.getMessage());
        }

        List<PerfMaterial> materials = this.list(new LambdaQueryWrapper<PerfMaterial>()
                .orderByDesc(PerfMaterial::getCreateTime));
        try {
            redisTemplate.opsForValue().set(MATERIAL_CACHE_KEY, materials, MATERIAL_CACHE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis 写入资料缓存失败: {}", e.getMessage());
        }
        return materials;
    }

    private List<PerfMaterial> convertCachedMaterials(Object cached) {
        if (!(cached instanceof List<?> list)) {
            return null;
        }
        List<PerfMaterial> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof PerfMaterial material) {
                result.add(material);
            } else {
                result.add(objectMapper.convertValue(item, PerfMaterial.class));
            }
        }
        return result;
    }

    @Override
    public void addMaterial(PerfMaterial material) {
        if (material.getStatus() == null || material.getStatus().isBlank()) {
            material.setStatus(STATUS_ON_SHELF);
        }
        this.save(material);
        deleteMaterialCache();
        String cycle = perfEmployeeScoreService.cycleNameOf(material.getCreateTime());
        perfEmployeeScoreService.refreshCycle(cycle);
        List<Long> employeeIds = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getRole, "EMPLOYEE")
                        .select(SysUser::getId))
                .stream()
                .map(SysUser::getId)
                .collect(Collectors.toList());
        sysNotificationService.notifyUsers(
                employeeIds,
                "新学习资料已发布",
                "本周期学习资料《" + material.getTitle() + "》已上架，请及时学习。",
                "MATERIAL_PUBLISHED",
                "/employee/study"
        );
    }

    @Override
    public boolean updateById(PerfMaterial material) {
        if (material.getId() == null) {
            throw new BusinessException("资料ID不能为空");
        }
        boolean updated = this.update(new LambdaUpdateWrapper<PerfMaterial>()
                .eq(PerfMaterial::getId, material.getId())
                .set(PerfMaterial::getTitle, material.getTitle())
                .set(PerfMaterial::getType, material.getType())
                .set(PerfMaterial::getUrl, material.getUrl())
                .set(PerfMaterial::getDescription, material.getDescription())
                .set(PerfMaterial::getDeadline, material.getDeadline())
                .set(material.getStatus() != null, PerfMaterial::getStatus, material.getStatus())
                .set(material.getCreatorId() != null, PerfMaterial::getCreatorId, material.getCreatorId()));
        deleteMaterialCache();
        return updated;
    }

    @Override
    public boolean removeById(java.io.Serializable id) {
        PerfMaterial existing = id == null ? null : this.getById(id);
        boolean removed = super.removeById(id);
        deleteMaterialCache();
        if (removed && existing != null) {
            perfEmployeeScoreService.refreshCycle(perfEmployeeScoreService.cycleNameOf(existing.getCreateTime()));
        }
        return removed;
    }

    @Override
    @Scheduled(cron = "0 10 0 * * ?")
    public void offShelfPastCycleMaterials() {
        String currentCycle = perfEmployeeScoreService.currentCycleName();
        List<PerfMaterial> onShelf = this.list(new LambdaQueryWrapper<PerfMaterial>()
                .and(w -> w.eq(PerfMaterial::getStatus, STATUS_ON_SHELF)
                        .or()
                        .isNull(PerfMaterial::getStatus)
                        .or()
                        .eq(PerfMaterial::getStatus, "")));
        List<Long> toOff = onShelf.stream()
                .filter(m -> !currentCycle.equals(perfEmployeeScoreService.cycleNameOf(m.getCreateTime())))
                .map(PerfMaterial::getId)
                .collect(Collectors.toList());
        if (toOff.isEmpty()) {
            return;
        }
        this.update(new LambdaUpdateWrapper<PerfMaterial>()
                .in(PerfMaterial::getId, toOff)
                .set(PerfMaterial::getStatus, STATUS_OFF_SHELF));
        deleteMaterialCache();
        log.info("已自动下架过期周期资料 {} 条", toOff.size());
    }

    private void deleteMaterialCache() {
        try {
            redisTemplate.delete(MATERIAL_CACHE_KEY);
        } catch (Exception e) {
            log.warn("Redis 暂不可用，跳过学习资料缓存淘汰: {}", e.getMessage());
        }
    }
}
