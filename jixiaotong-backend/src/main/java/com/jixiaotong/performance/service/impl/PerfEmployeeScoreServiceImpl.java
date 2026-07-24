package com.jixiaotong.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jixiaotong.performance.dto.EmployeePerformanceDTO;
import com.jixiaotong.performance.dto.WorkScoreUploadDTO;
import com.jixiaotong.performance.entity.*;
import com.jixiaotong.performance.exception.BusinessException;
import com.jixiaotong.performance.mapper.*;
import com.jixiaotong.performance.service.PerfEmployeeScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerfEmployeeScoreServiceImpl extends ServiceImpl<PerfEmployeeScoreMapper, PerfEmployeeScore>
        implements PerfEmployeeScoreService {

    private final PerfMaterialMapper materialMapper;
    private final PerfMaterialStudyMapper materialStudyMapper;
    private final PerfTemplateMapper templateMapper;
    private final PerfReviewMapper reviewMapper;
    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;

    @Override
    public void refresh(Long employeeId, String cycleName) {
        if (employeeId == null || !StringUtils.hasText(cycleName)) {
            return;
        }
        String cycle = cycleName.trim();
        EmployeePerformanceDTO computed = compute(employeeId, cycle);
        PerfEmployeeScore existing = this.getOne(new LambdaQueryWrapper<PerfEmployeeScore>()
                .eq(PerfEmployeeScore::getEmployeeId, employeeId)
                .eq(PerfEmployeeScore::getCycleName, cycle)
                .last("LIMIT 1"));
        if (existing == null) {
            this.save(PerfEmployeeScore.builder()
                    .employeeId(employeeId)
                    .cycleName(cycle)
                    .learningScore(computed.getLearningScore())
                    .examScore(computed.getExamScore())
                    .workScore(computed.getWorkScore())
                    .performanceScore(computed.getPerformanceScore())
                    .materialTotal(computed.getMaterialTotal())
                    .materialCompleted(computed.getMaterialCompleted())
                    .examCount(computed.getExamCount())
                    .build());
        } else {
            existing.setLearningScore(computed.getLearningScore());
            existing.setExamScore(computed.getExamScore());
            existing.setWorkScore(computed.getWorkScore());
            existing.setPerformanceScore(computed.getPerformanceScore());
            existing.setMaterialTotal(computed.getMaterialTotal());
            existing.setMaterialCompleted(computed.getMaterialCompleted());
            existing.setExamCount(computed.getExamCount());
            this.updateById(existing);
        }
    }

    @Override
    public void refreshByReviewId(Long reviewId) {
        if (reviewId == null) {
            return;
        }
        PerfReview review = reviewMapper.selectById(reviewId);
        if (review == null || review.getEmployeeId() == null) {
            return;
        }
        PerfTemplate template = templateMapper.selectById(review.getTemplateId());
        if (template == null || !StringUtils.hasText(template.getCycleName())) {
            return;
        }
        refresh(review.getEmployeeId(), template.getCycleName());
    }

    @Override
    public void refreshCycle(String cycleName) {
        if (!StringUtils.hasText(cycleName)) {
            return;
        }
        String cycle = cycleName.trim();
        List<SysUser> employees = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "EMPLOYEE"));
        for (SysUser employee : employees) {
            refresh(employee.getId(), cycle);
        }
        log.info("已刷新周期 {} 的员工绩效统计，共 {} 人", cycle, employees.size());
    }

    @Override
    public String cycleNameOf(LocalDateTime time) {
        LocalDateTime t = time == null ? LocalDateTime.now() : time;
        int quarter = (t.getMonthValue() - 1) / 3 + 1;
        return t.getYear() + "-Q" + quarter;
    }

    @Override
    public String currentCycleName() {
        return cycleNameOf(LocalDateTime.now());
    }

    @Override
    public void refreshAll() {
        Set<String> cycles = new LinkedHashSet<>();
        cycles.add(currentCycleName());
        templateMapper.selectList(new LambdaQueryWrapper<PerfTemplate>()
                        .select(PerfTemplate::getCycleName)
                        .isNotNull(PerfTemplate::getCycleName))
                .stream()
                .map(PerfTemplate::getCycleName)
                .filter(StringUtils::hasText)
                .forEach(cycles::add);
        materialMapper.selectList(new LambdaQueryWrapper<PerfMaterial>().select(PerfMaterial::getCreateTime))
                .stream()
                .map(PerfMaterial::getCreateTime)
                .map(this::cycleNameOf)
                .forEach(cycles::add);

        List<SysUser> employees = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "EMPLOYEE"));
        for (String cycle : cycles) {
            for (SysUser employee : employees) {
                refresh(employee.getId(), cycle);
            }
        }
        log.info("员工绩效统计全量回填完成，周期数={}, 员工数={}", cycles.size(), employees.size());
    }

    @Override
    public EmployeePerformanceDTO getEmployeePerformance(Long employeeId, String cycleName) {
        String targetCycle = StringUtils.hasText(cycleName) ? cycleName.trim() : currentCycleName();
        PerfEmployeeScore score = this.getOne(new LambdaQueryWrapper<PerfEmployeeScore>()
                .eq(PerfEmployeeScore::getEmployeeId, employeeId)
                .eq(PerfEmployeeScore::getCycleName, targetCycle)
                .last("LIMIT 1"));
        if (score == null) {
            refresh(employeeId, targetCycle);
            score = this.getOne(new LambdaQueryWrapper<PerfEmployeeScore>()
                    .eq(PerfEmployeeScore::getEmployeeId, employeeId)
                    .eq(PerfEmployeeScore::getCycleName, targetCycle)
                    .last("LIMIT 1"));
        }
        return toDto(score, sysUserMapper.selectById(employeeId), null);
    }

    @Override
    public List<EmployeePerformanceDTO> listEmployeePerformanceHistory(Long employeeId) {
        Set<String> cycles = new LinkedHashSet<>();
        cycles.add(currentCycleName());
        List<PerfReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<PerfReview>()
                .eq(PerfReview::getEmployeeId, employeeId)
                .orderByDesc(PerfReview::getCreateTime));
        for (PerfReview review : reviews) {
            PerfTemplate template = templateMapper.selectById(review.getTemplateId());
            if (template != null && StringUtils.hasText(template.getCycleName())) {
                cycles.add(template.getCycleName());
            }
        }
        for (String cycle : cycles) {
            Long count = this.count(new LambdaQueryWrapper<PerfEmployeeScore>()
                    .eq(PerfEmployeeScore::getEmployeeId, employeeId)
                    .eq(PerfEmployeeScore::getCycleName, cycle));
            if (count == 0) {
                refresh(employeeId, cycle);
            }
        }
        SysUser employee = sysUserMapper.selectById(employeeId);
        Map<Long, String> deptNameMap = deptNameMap();
        return cycles.stream()
                .map(cycle -> this.getOne(new LambdaQueryWrapper<PerfEmployeeScore>()
                        .eq(PerfEmployeeScore::getEmployeeId, employeeId)
                        .eq(PerfEmployeeScore::getCycleName, cycle)
                        .last("LIMIT 1")))
                .filter(score -> score != null)
                .map(score -> toDto(score, employee, deptNameMap))
                .collect(Collectors.toList());
    }

    @Override
    public List<EmployeePerformanceDTO> listCompanyPerformance(String cycleName, String keyword, Long deptId) {
        String targetCycle = StringUtils.hasText(cycleName) ? cycleName.trim() : currentCycleName();
        Long scoreCount = this.count(new LambdaQueryWrapper<PerfEmployeeScore>()
                .eq(PerfEmployeeScore::getCycleName, targetCycle));
        if (scoreCount == 0) {
            refreshCycle(targetCycle);
        }

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "EMPLOYEE")
                .orderByAsc(SysUser::getDeptId)
                .orderByAsc(SysUser::getUsername);
        if (deptId != null) {
            wrapper.eq(SysUser::getDeptId, deptId);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SysUser::getRealName, kw).or().like(SysUser::getUsername, kw));
        }
        List<SysUser> employees = sysUserMapper.selectList(wrapper);
        Map<Long, String> deptNameMap = deptNameMap();
        Map<Long, PerfEmployeeScore> scoreMap = this.list(new LambdaQueryWrapper<PerfEmployeeScore>()
                        .eq(PerfEmployeeScore::getCycleName, targetCycle)
                        .in(!employees.isEmpty(), PerfEmployeeScore::getEmployeeId,
                                employees.stream().map(SysUser::getId).collect(Collectors.toList())))
                .stream()
                .collect(Collectors.toMap(PerfEmployeeScore::getEmployeeId, s -> s, (a, b) -> a));

        return employees.stream()
                .map(emp -> {
                    PerfEmployeeScore score = scoreMap.get(emp.getId());
                    if (score == null) {
                        refresh(emp.getId(), targetCycle);
                        score = this.getOne(new LambdaQueryWrapper<PerfEmployeeScore>()
                                .eq(PerfEmployeeScore::getEmployeeId, emp.getId())
                                .eq(PerfEmployeeScore::getCycleName, targetCycle)
                                .last("LIMIT 1"));
                    }
                    return toDto(score, emp, deptNameMap);
                })
                .collect(Collectors.toList());
    }

    private EmployeePerformanceDTO compute(Long employeeId, String cycleName) {
        LocalDateTime[] range = quarterRange(cycleName);
        LocalDateTime start = range[0];
        LocalDateTime end = range[1];

        List<PerfMaterial> materials = materialMapper.selectList(new LambdaQueryWrapper<PerfMaterial>()
                .ge(PerfMaterial::getCreateTime, start)
                .le(PerfMaterial::getCreateTime, end));
        int materialTotal = materials.size();
        int materialCompleted = 0;
        if (!materials.isEmpty()) {
            List<Long> materialIds = materials.stream().map(PerfMaterial::getId).collect(Collectors.toList());
            Long completedCount = materialStudyMapper.selectCount(new LambdaQueryWrapper<PerfMaterialStudy>()
                    .eq(PerfMaterialStudy::getEmployeeId, employeeId)
                    .in(PerfMaterialStudy::getMaterialId, materialIds)
                    .ge(PerfMaterialStudy::getCompleteTime, start)
                    .le(PerfMaterialStudy::getCompleteTime, end));
            materialCompleted = completedCount.intValue();
        }
        BigDecimal learningScore = materialTotal == 0 ? new BigDecimal("100.00")
                : BigDecimal.valueOf(materialCompleted)
                .multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(materialTotal), 2, RoundingMode.HALF_UP);

        List<PerfTemplate> templates = templateMapper.selectList(new LambdaQueryWrapper<PerfTemplate>()
                .eq(PerfTemplate::getCycleName, cycleName));
        List<Long> templateIds = templates.stream().map(PerfTemplate::getId).collect(Collectors.toList());
        List<PerfReview> scoredReviews = templateIds.isEmpty() ? Collections.emptyList()
                : reviewMapper.selectList(new LambdaQueryWrapper<PerfReview>()
                .eq(PerfReview::getEmployeeId, employeeId)
                .in(PerfReview::getTemplateId, templateIds)
                .in(PerfReview::getStatus, List.of("SUBMITTED", "APPEALING", "GRADED", "CHEATED")));
        int examCount = scoredReviews.size();
        BigDecimal examScore = examCount == 0 ? BigDecimal.ZERO
                : scoredReviews.stream()
                .map(review -> review.getTotalScore() == null ? BigDecimal.ZERO : review.getTotalScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(examCount), 2, RoundingMode.HALF_UP);

        PerfEmployeeScore existing = this.getOne(new LambdaQueryWrapper<PerfEmployeeScore>()
                .eq(PerfEmployeeScore::getEmployeeId, employeeId)
                .eq(PerfEmployeeScore::getCycleName, cycleName)
                .last("LIMIT 1"));
        BigDecimal workScore = existing == null || existing.getWorkScore() == null
                ? BigDecimal.ZERO
                : existing.getWorkScore();

        BigDecimal performanceScore = learningScore.multiply(new BigDecimal("0.30"))
                .add(examScore.multiply(new BigDecimal("0.40")))
                .add(workScore.multiply(new BigDecimal("0.30")))
                .setScale(2, RoundingMode.HALF_UP);

        return EmployeePerformanceDTO.builder()
                .employeeId(employeeId)
                .cycleName(cycleName)
                .learningScore(learningScore)
                .examScore(examScore)
                .workScore(workScore)
                .performanceScore(performanceScore)
                .materialTotal(materialTotal)
                .materialCompleted(materialCompleted)
                .examCount(examCount)
                .build();
    }

    private EmployeePerformanceDTO toDto(PerfEmployeeScore score, SysUser employee, Map<Long, String> deptNameMap) {
        if (score == null) {
            return EmployeePerformanceDTO.builder()
                    .employeeId(employee == null ? null : employee.getId())
                    .username(employee == null ? null : employee.getUsername())
                    .employeeName(employee == null ? null : employee.getRealName())
                    .deptId(employee == null ? null : employee.getDeptId())
                    .jobLevel(employee == null ? null : employee.getJobLevel())
                    .learningScore(BigDecimal.ZERO)
                    .examScore(BigDecimal.ZERO)
                    .workScore(BigDecimal.ZERO)
                    .performanceScore(BigDecimal.ZERO)
                    .materialTotal(0)
                    .materialCompleted(0)
                    .examCount(0)
                    .build();
        }
        Map<Long, String> names = deptNameMap == null ? deptNameMap() : deptNameMap;
        Long deptId = employee == null ? null : employee.getDeptId();
        return EmployeePerformanceDTO.builder()
                .employeeId(score.getEmployeeId())
                .username(employee == null ? null : employee.getUsername())
                .employeeName(employee == null ? "员工" + score.getEmployeeId() : employee.getRealName())
                .deptId(deptId)
                .deptName(deptId == null ? "-" : names.getOrDefault(deptId, "部门" + deptId))
                .jobLevel(employee == null ? null : employee.getJobLevel())
                .cycleName(score.getCycleName())
                .learningScore(score.getLearningScore())
                .examScore(score.getExamScore())
                .workScore(score.getWorkScore() == null ? BigDecimal.ZERO : score.getWorkScore())
                .performanceScore(score.getPerformanceScore())
                .materialTotal(score.getMaterialTotal())
                .materialCompleted(score.getMaterialCompleted())
                .examCount(score.getExamCount())
                .build();
    }

    private Map<Long, String> deptNameMap() {
        return sysDeptMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getName, (a, b) -> a));
    }

    @Override
    public int importWorkScores(String cycleName, List<WorkScoreUploadDTO.Item> items) {
        String cycle = StringUtils.hasText(cycleName) ? cycleName.trim() : currentCycleName();
        if (items == null || items.isEmpty()) {
            throw new BusinessException("工作实绩表数据为空");
        }
        int updated = 0;
        for (WorkScoreUploadDTO.Item item : items) {
            if (item == null || !StringUtils.hasText(item.getUsername()) || item.getWorkScore() == null) {
                continue;
            }
            BigDecimal score = item.getWorkScore().max(BigDecimal.ZERO).min(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            SysUser employee = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, item.getUsername().trim())
                    .eq(SysUser::getRole, "EMPLOYEE")
                    .last("LIMIT 1"));
            if (employee == null) {
                continue;
            }
            PerfEmployeeScore existing = this.getOne(new LambdaQueryWrapper<PerfEmployeeScore>()
                    .eq(PerfEmployeeScore::getEmployeeId, employee.getId())
                    .eq(PerfEmployeeScore::getCycleName, cycle)
                    .last("LIMIT 1"));
            if (existing == null) {
                this.save(PerfEmployeeScore.builder()
                        .employeeId(employee.getId())
                        .cycleName(cycle)
                        .learningScore(BigDecimal.ZERO)
                        .examScore(BigDecimal.ZERO)
                        .workScore(score)
                        .performanceScore(BigDecimal.ZERO)
                        .materialTotal(0)
                        .materialCompleted(0)
                        .examCount(0)
                        .build());
            } else {
                existing.setWorkScore(score);
                this.updateById(existing);
            }
            refresh(employee.getId(), cycle);
            updated++;
        }
        if (updated == 0) {
            throw new BusinessException("未匹配到有效员工工号，请检查实绩表");
        }
        return updated;
    }

    private LocalDateTime[] quarterRange(String cycleName) {
        try {
            String[] parts = cycleName.split("-Q");
            int year = Integer.parseInt(parts[0]);
            int quarter = Integer.parseInt(parts[1]);
            Month startMonth = Month.of((quarter - 1) * 3 + 1);
            Month endMonth = Month.of(quarter * 3);
            LocalDateTime start = LocalDateTime.of(year, startMonth, 1, 0, 0, 0);
            LocalDateTime end = LocalDateTime.of(year, endMonth, endMonth.length(year % 4 == 0), 23, 59, 59);
            return new LocalDateTime[]{start, end};
        } catch (Exception e) {
            LocalDateTime now = LocalDateTime.now();
            return new LocalDateTime[]{now.with(LocalTime.MIN), now.with(LocalTime.MAX)};
        }
    }
}
