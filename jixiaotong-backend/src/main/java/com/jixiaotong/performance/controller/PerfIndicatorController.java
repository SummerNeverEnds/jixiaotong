package com.jixiaotong.performance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jixiaotong.performance.common.Result;
import com.jixiaotong.performance.entity.PerfIndicator;
import com.jixiaotong.performance.exception.BusinessException;
import com.jixiaotong.performance.security.AuthSupport;
import com.jixiaotong.performance.security.UserContext;
import com.jixiaotong.performance.service.PerfIndicatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/indicator")
@RequiredArgsConstructor
public class PerfIndicatorController {

    private final PerfIndicatorService indicatorService;
    private final ObjectMapper objectMapper;

    private LambdaQueryWrapper<PerfIndicator> buildQueryWrapper(String name, String type, String jobLevel) {
        LambdaQueryWrapper<PerfIndicator> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(name)) {
            wrapper.like(PerfIndicator::getName, name);
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(PerfIndicator::getType, type);
        }
        if (StringUtils.hasText(jobLevel)) {
            wrapper.eq(PerfIndicator::getJobLevel, jobLevel);
        }
        wrapper.orderByDesc(PerfIndicator::getCreateTime);
        return wrapper;
    }

    @GetMapping("/list")
    public Result<List<PerfIndicator>> list(@RequestParam(required = false) String name,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String jobLevel) {
        AuthSupport.requireRole("ADMIN", "MANAGER");
        return Result.success(indicatorService.list(buildQueryWrapper(name, type, jobLevel)));
    }

    @GetMapping("/page")
    public Result<Page<PerfIndicator>> page(@RequestParam(defaultValue = "1") Integer current,
                                            @RequestParam(defaultValue = "10") Integer size,
                                            @RequestParam(required = false) String name,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String jobLevel) {
        AuthSupport.requireRole("ADMIN");
        Page<PerfIndicator> page = indicatorService.page(
                new Page<>(current, size),
                buildQueryWrapper(name, type, jobLevel));
        return Result.success(page);
    }

    @PostMapping("/save")
    public Result<Void> save(@RequestBody PerfIndicator indicator) {
        AuthSupport.requireRole("ADMIN");
        if (!StringUtils.hasText(indicator.getName())) {
            throw new BusinessException("题目名称不能为空");
        }
        if (!StringUtils.hasText(indicator.getType())) {
            throw new BusinessException("题型不能为空");
        }
        if (!StringUtils.hasText(indicator.getJobLevel())) {
            indicator.setJobLevel("P1");
        }
        if ("OBJECTIVE".equals(indicator.getType())) {
            if (!StringUtils.hasText(indicator.getOptionsContent()) || !StringUtils.hasText(indicator.getStandardAnswer())) {
                throw new BusinessException("客观题必须填写选项和标准答案");
            }
            validateObjectiveAnswer(indicator.getOptionsContent(), indicator.getStandardAnswer());
            indicator.setStandardAnswer(indicator.getStandardAnswer().trim());
        } else {
            indicator.setOptionsContent(null);
            indicator.setStandardAnswer(null);
        }
        if (indicator.getCreatorId() == null) {
            indicator.setCreatorId(UserContext.getUserId());
        }
        indicatorService.saveOrUpdate(indicator);
        return Result.success();
    }

    private void validateObjectiveAnswer(String optionsContent, String standardAnswer) {
        Map<String, Object> options;
        try {
            options = objectMapper.readValue(optionsContent, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BusinessException("客观题选项必须是合法 JSON，如 {\"A\":\"选项一\",\"B\":\"选项二\"}");
        }
        if (options == null || options.isEmpty()) {
            throw new BusinessException("客观题至少需要一个选项");
        }
        String answer = standardAnswer.trim();
        if (!options.containsKey(answer)) {
            throw new BusinessException("标准答案必须是选项中的键（如 A/B/C），不能填写选项外的值");
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AuthSupport.requireRole("ADMIN");
        indicatorService.removeById(id);
        return Result.success();
    }
}
