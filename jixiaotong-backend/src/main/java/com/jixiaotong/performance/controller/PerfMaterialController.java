package com.jixiaotong.performance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jixiaotong.performance.common.PageResult;
import com.jixiaotong.performance.common.Result;
import com.jixiaotong.performance.dto.MaterialCompleteDTO;
import com.jixiaotong.performance.entity.PerfMaterial;
import com.jixiaotong.performance.entity.PerfMaterialStudy;
import com.jixiaotong.performance.exception.BusinessException;
import com.jixiaotong.performance.mapper.PerfMaterialStudyMapper;
import com.jixiaotong.performance.security.AuthSupport;
import com.jixiaotong.performance.security.UserContext;
import com.jixiaotong.performance.service.PerfEmployeeScoreService;
import com.jixiaotong.performance.service.PerfMaterialService;
import com.jixiaotong.performance.service.impl.PerfMaterialServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/material")
@RequiredArgsConstructor
public class PerfMaterialController {

    private final PerfMaterialService perfMaterialService;
    private final PerfMaterialStudyMapper materialStudyMapper;
    private final PerfEmployeeScoreService perfEmployeeScoreService;

    @GetMapping("/list")
    public Result<List<PerfMaterial>> getList(@RequestParam(required = false) Long employeeId) {
        Long uid = resolveEmployeeId(employeeId);
        return Result.success(loadMaterials(uid));
    }

    @GetMapping("/page")
    public Result<PageResult<PerfMaterial>> page(@RequestParam(defaultValue = "1") Integer current,
                                                 @RequestParam(defaultValue = "10") Integer size,
                                                 @RequestParam(required = false) Long employeeId,
                                                 @RequestParam(required = false) String title,
                                                 @RequestParam(required = false) String type) {
        Long uid = resolveEmployeeId(employeeId);
        List<PerfMaterial> materials = loadMaterials(uid);
        if (StringUtils.hasText(title)) {
            materials = materials.stream()
                    .filter(m -> m.getTitle() != null && m.getTitle().contains(title.trim()))
                    .toList();
        }
        if (StringUtils.hasText(type)) {
            materials = materials.stream()
                    .filter(m -> type.equals(m.getType()))
                    .toList();
        }
        return Result.success(PageResult.of(materials, current, size));
    }

    private List<PerfMaterial> loadMaterials(Long employeeId) {
        boolean employeeView = "EMPLOYEE".equals(UserContext.getRole());
        String currentCycle = perfEmployeeScoreService.currentCycleName();
        List<PerfMaterial> materials = perfMaterialService.getMaterialListWithCache().stream()
                .filter(material -> {
                    if (!employeeView) {
                        return true;
                    }
                    String cycle = perfEmployeeScoreService.cycleNameOf(material.getCreateTime());
                    String status = material.getStatus();
                    boolean onShelf = status == null || status.isBlank()
                            || PerfMaterialServiceImpl.STATUS_ON_SHELF.equals(status);
                    return currentCycle.equals(cycle) && onShelf;
                })
                .map(material -> {
                    PerfMaterial copy = new PerfMaterial();
                    BeanUtils.copyProperties(material, copy);
                    if (copy.getStatus() == null || copy.getStatus().isBlank()) {
                        copy.setStatus(PerfMaterialServiceImpl.STATUS_ON_SHELF);
                    }
                    copy.setCompleted(false);
                    return copy;
                })
                .toList();
        if (employeeId != null) {
            Set<Long> completedIds = materialStudyMapper.selectList(new LambdaQueryWrapper<PerfMaterialStudy>()
                            .eq(PerfMaterialStudy::getEmployeeId, employeeId))
                    .stream()
                    .map(PerfMaterialStudy::getMaterialId)
                    .collect(Collectors.toSet());
            materials.forEach(material -> material.setCompleted(completedIds.contains(material.getId())));
        }
        return materials;
    }

    @PostMapping("/complete")
    public Result<Void> completeMaterial(@RequestBody MaterialCompleteDTO dto) {
        if (dto == null || dto.getMaterialId() == null) {
            throw new BusinessException("资料ID不能为空");
        }
        Long employeeId = AuthSupport.requireLoginUserId();
        if (dto.getEmployeeId() != null) {
            AuthSupport.requireSelf(dto.getEmployeeId());
        }
        dto.setEmployeeId(employeeId);

        PerfMaterial material = perfMaterialService.getById(dto.getMaterialId());
        if (material == null) {
            throw new BusinessException("学习资料不存在");
        }
        if (PerfMaterialServiceImpl.STATUS_OFF_SHELF.equals(material.getStatus())) {
            throw new BusinessException("该资料已下架，无法完成学习");
        }
        String currentCycle = perfEmployeeScoreService.currentCycleName();
        if (!currentCycle.equals(perfEmployeeScoreService.cycleNameOf(material.getCreateTime()))) {
            throw new BusinessException("仅可完成本周期学习资料");
        }
        if (material.getDeadline() != null && LocalDateTime.now().isAfter(material.getDeadline())) {
            throw new BusinessException("该资料学习截止日期已过，无法完成学习");
        }

        BigDecimal watchProgress = dto.getWatchProgress() == null ? BigDecimal.ZERO : dto.getWatchProgress();
        int staySeconds = dto.getStaySeconds() == null ? 0 : Math.max(dto.getStaySeconds(), 0);
        if ("VIDEO".equalsIgnoreCase(material.getType())) {
            if (watchProgress.compareTo(new BigDecimal("95")) < 0) {
                throw new BusinessException("视频观看进度需达到 95% 以上才能完成学习");
            }
        } else if (staySeconds < 60) {
            throw new BusinessException("文档/链接类资料需停留满 1 分钟才能完成学习");
        }

        Long count = materialStudyMapper.selectCount(new LambdaQueryWrapper<PerfMaterialStudy>()
                .eq(PerfMaterialStudy::getEmployeeId, employeeId)
                .eq(PerfMaterialStudy::getMaterialId, dto.getMaterialId()));
        if (count == 0) {
            materialStudyMapper.insert(PerfMaterialStudy.builder()
                    .employeeId(employeeId)
                    .materialId(dto.getMaterialId())
                    .watchProgress(watchProgress)
                    .staySeconds(staySeconds)
                    .completeTime(LocalDateTime.now())
                    .build());
            String cycle = perfEmployeeScoreService.cycleNameOf(material.getCreateTime());
            perfEmployeeScoreService.refresh(employeeId, cycle);
        }
        return Result.success();
    }

    @PostMapping("/add")
    public Result<Void> addMaterial(@RequestBody PerfMaterial material) {
        AuthSupport.requireRole("MANAGER", "ADMIN");
        validateMaterial(material);
        material.setCreatorId(UserContext.getUserId());
        material.setStatus(PerfMaterialServiceImpl.STATUS_ON_SHELF);
        perfMaterialService.addMaterial(material);
        return Result.success();
    }

    private Long resolveEmployeeId(Long employeeId) {
        Long loginId = AuthSupport.requireLoginUserId();
        if (employeeId == null) {
            return "EMPLOYEE".equals(UserContext.getRole()) ? loginId : null;
        }
        if ("EMPLOYEE".equals(UserContext.getRole())) {
            AuthSupport.requireSelf(employeeId);
        }
        return employeeId;
    }

    @PutMapping("/update")
    public Result<Void> updateMaterial(@RequestBody PerfMaterial material) {
        AuthSupport.requireRole("MANAGER", "ADMIN");
        validateMaterial(material);
        perfMaterialService.updateById(material);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteMaterial(@PathVariable Long id) {
        AuthSupport.requireRole("MANAGER", "ADMIN");
        perfMaterialService.removeById(id);
        return Result.success();
    }

    private void validateMaterial(PerfMaterial material) {
        if (!StringUtils.hasText(material.getTitle())) {
            throw new BusinessException("资料标题不能为空");
        }
        if (!StringUtils.hasText(material.getType())) {
            throw new BusinessException("资料类型不能为空");
        }
        if (!StringUtils.hasText(material.getUrl())) {
            throw new BusinessException("资料链接不能为空");
        }
        if (material.getDeadline() == null) {
            throw new BusinessException("学习截止日期不能为空");
        }
        if (material.getDeadline().toLocalDate().isBefore(LocalDate.now())) {
            throw new BusinessException("学习截止日期不能早于今天");
        }
    }
}
