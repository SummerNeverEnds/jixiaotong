package com.jixiaotong.performance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jixiaotong.performance.common.PageResult;
import com.jixiaotong.performance.common.Result;
import com.jixiaotong.performance.entity.PerfTemplate;
import com.jixiaotong.performance.entity.SysUser;
import com.jixiaotong.performance.exception.BusinessException;
import com.jixiaotong.performance.security.AuthSupport;
import com.jixiaotong.performance.service.PerfReviewService;
import com.jixiaotong.performance.service.PerfTemplateService;
import com.jixiaotong.performance.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/template")
@RequiredArgsConstructor
public class PerfTemplateController {

    private final PerfTemplateService templateService;
    private final PerfReviewService perfReviewService;
    private final SysUserService sysUserService;

    @GetMapping("/list")
    public Result<List<PerfTemplate>> list() {
        AuthSupport.requireRole("MANAGER", "ADMIN");
        return Result.success(templateService.list(new LambdaQueryWrapper<PerfTemplate>().orderByDesc(PerfTemplate::getCreateTime)));
    }

    @GetMapping("/page")
    public Result<PageResult<PerfTemplate>> page(@RequestParam(defaultValue = "1") Integer current,
                                                 @RequestParam(defaultValue = "10") Integer size,
                                                 @RequestParam(required = false) String name,
                                                 @RequestParam(required = false) Long managerId) {
        AuthSupport.requireRole("MANAGER", "ADMIN");
        Long scopeManagerId = AuthSupport.resolveManagerScope(managerId);
        LambdaQueryWrapper<PerfTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(name)) {
            wrapper.like(PerfTemplate::getName, name);
        }
        if (scopeManagerId != null) {
            wrapper.eq(PerfTemplate::getManagerId, scopeManagerId);
        }
        wrapper.orderByDesc(PerfTemplate::getCreateTime);
        Page<PerfTemplate> page = templateService.page(new Page<>(current, size), wrapper);
        return Result.success(PageResult.from(page));
    }

    @PostMapping("/save")
    public Result<Void> save(@RequestBody com.jixiaotong.performance.dto.TemplateSaveDTO dto) {
        AuthSupport.requireRole("MANAGER");
        dto.setManagerId(AuthSupport.requireLoginUserId());
        templateService.saveTemplateWithIndicators(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AuthSupport.requireRole("MANAGER", "ADMIN");
        templateService.deleteTemplate(id);
        return Result.success();
    }

    @PostMapping("/publish")
    public Result<com.jixiaotong.performance.dto.PublishResultDTO> publish(@RequestBody Map<String, Object> params) {
        if (params.get("templateId") == null) {
            throw new BusinessException("模板ID不能为空");
        }
        Long templateId = Long.valueOf(params.get("templateId").toString());
        Long requestedManagerId = params.get("managerId") == null
                ? null
                : Long.valueOf(params.get("managerId").toString());
        Long managerId = AuthSupport.requireActingManagerId(requestedManagerId);

        List<SysUser> employees = sysUserService.listDeptEmployees(managerId);
        if (employees.isEmpty()) {
            throw new BusinessException("本部门暂无员工可下发考核");
        }
        List<Long> employeeIds = employees.stream().map(SysUser::getId).toList();
        return Result.success(perfReviewService.generateReview(templateId, employeeIds, managerId));
    }
}
