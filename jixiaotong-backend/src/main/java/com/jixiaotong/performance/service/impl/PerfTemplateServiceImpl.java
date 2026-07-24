package com.jixiaotong.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jixiaotong.performance.dto.TemplateSaveDTO;
import com.jixiaotong.performance.entity.PerfIndicator;
import com.jixiaotong.performance.entity.PerfReview;
import com.jixiaotong.performance.entity.PerfReviewAppeal;
import com.jixiaotong.performance.entity.PerfReviewAppealDetail;
import com.jixiaotong.performance.entity.PerfReviewDetail;
import com.jixiaotong.performance.entity.PerfTemplate;
import com.jixiaotong.performance.entity.PerfTemplateIndicator;
import com.jixiaotong.performance.exception.BusinessException;
import com.jixiaotong.performance.mapper.PerfIndicatorMapper;
import com.jixiaotong.performance.mapper.PerfReviewAppealDetailMapper;
import com.jixiaotong.performance.mapper.PerfReviewAppealMapper;
import com.jixiaotong.performance.mapper.PerfReviewDetailMapper;
import com.jixiaotong.performance.mapper.PerfReviewMapper;
import com.jixiaotong.performance.mapper.PerfTemplateIndicatorMapper;
import com.jixiaotong.performance.mapper.PerfTemplateMapper;
import com.jixiaotong.performance.service.PerfTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PerfTemplateServiceImpl extends ServiceImpl<PerfTemplateMapper, PerfTemplate> implements PerfTemplateService {

    private final PerfTemplateIndicatorMapper templateIndicatorMapper;
    private final PerfIndicatorMapper indicatorMapper;
    private final PerfReviewMapper reviewMapper;
    private final PerfReviewDetailMapper reviewDetailMapper;
    private final PerfReviewAppealMapper appealMapper;
    private final PerfReviewAppealDetailMapper appealDetailMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTemplateWithIndicators(TemplateSaveDTO dto) {
        if (dto == null) {
            throw new BusinessException("试卷配置不能为空");
        }
        if (!StringUtils.hasText(dto.getName())) {
            throw new BusinessException("试卷名称不能为空");
        }
        if (dto.getManagerId() == null) {
            throw new BusinessException("创建经理不能为空");
        }
        if (dto.getDeadline() == null) {
            throw new BusinessException("考核截止日期不能为空");
        }
        if (dto.getDeadline().toLocalDate().isBefore(LocalDate.now())) {
            throw new BusinessException("考核截止日期不能早于今天");
        }
        int objectiveCount = safeCount(dto.getObjectiveCount());
        int subjectiveCount = safeCount(dto.getSubjectiveCount());
        List<Long> explicitIndicatorIds = dto.getIndicatorIds() == null ? Collections.emptyList()
                : dto.getIndicatorIds().stream().distinct().collect(Collectors.toList());
        if (objectiveCount + subjectiveCount == 0 && explicitIndicatorIds.isEmpty()) {
            throw new BusinessException("请至少抽取一道考核题目");
        }

        String cycleName = currentCycleName();
        PerfTemplate template = PerfTemplate.builder()
                .id(dto.getId())
                .name(dto.getName())
                .cycleName(cycleName)
                .durationMinutes(dto.getDurationMinutes())
                .deadline(dto.getDeadline())
                .managerId(dto.getManagerId())
                .objectiveCount(objectiveCount)
                .subjectiveCount(subjectiveCount)
                .status(dto.getStatus() == null ? "UNPUBLISHED" : dto.getStatus())
                .build();
        this.saveOrUpdate(template);

        Long templateId = template.getId();

        if (explicitIndicatorIds.isEmpty()) {
            return;
        }

        Long reviewCount = reviewMapper.selectCount(new LambdaQueryWrapper<PerfReview>()
                .eq(PerfReview::getTemplateId, templateId));
        if (reviewCount != null && reviewCount > 0) {
            throw new BusinessException("该试卷已下发考核，不能修改绑定题目");
        }

        templateIndicatorMapper.delete(
                new LambdaQueryWrapper<PerfTemplateIndicator>()
                        .eq(PerfTemplateIndicator::getTemplateId, templateId)
        );
        insertTemplateIndicators(templateId, explicitIndicatorIds, objectiveCount + subjectiveCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long templateId) {
        List<PerfReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<PerfReview>()
                .eq(PerfReview::getTemplateId, templateId));
        List<Long> reviewIds = reviews.stream()
                .map(PerfReview::getId)
                .collect(Collectors.toList());
        if (!reviewIds.isEmpty()) {
            reviewDetailMapper.delete(new LambdaQueryWrapper<PerfReviewDetail>()
                    .in(PerfReviewDetail::getReviewId, reviewIds));
            List<Long> appealIds = appealMapper.selectList(new LambdaQueryWrapper<PerfReviewAppeal>()
                            .in(PerfReviewAppeal::getReviewId, reviewIds))
                    .stream()
                    .map(PerfReviewAppeal::getId)
                    .collect(Collectors.toList());
            if (!appealIds.isEmpty()) {
                appealDetailMapper.delete(new LambdaQueryWrapper<PerfReviewAppealDetail>()
                        .in(PerfReviewAppealDetail::getAppealId, appealIds));
            }
            appealMapper.delete(new LambdaQueryWrapper<PerfReviewAppeal>()
                    .in(PerfReviewAppeal::getReviewId, reviewIds));
            reviewMapper.deleteBatchIds(reviewIds);
        }
        templateIndicatorMapper.delete(new LambdaQueryWrapper<PerfTemplateIndicator>()
                .eq(PerfTemplateIndicator::getTemplateId, templateId));
        this.removeById(templateId);
    }

    public void insertTemplateIndicators(Long templateId, List<Long> indicatorIds, int totalQuestionCount) {
        if (indicatorIds == null || indicatorIds.isEmpty()) {
            return;
        }
        int denominator = totalQuestionCount > 0 ? totalQuestionCount : indicatorIds.size();
        BigDecimal weight = new BigDecimal("100")
                .divide(new BigDecimal(denominator), 2, RoundingMode.HALF_UP);

        for (Long indicatorId : indicatorIds) {
            Long exists = templateIndicatorMapper.selectCount(new LambdaQueryWrapper<PerfTemplateIndicator>()
                    .eq(PerfTemplateIndicator::getTemplateId, templateId)
                    .eq(PerfTemplateIndicator::getIndicatorId, indicatorId));
            if (exists > 0) {
                continue;
            }
            PerfTemplateIndicator relation = PerfTemplateIndicator.builder()
                    .templateId(templateId)
                    .indicatorId(indicatorId)
                    .weightRatio(weight)
                    .build();
            templateIndicatorMapper.insert(relation);
        }
    }

    public List<Long> drawIndicatorsByTypeAndLevel(String type, String jobLevel, Integer count) {
        int drawCount = count == null ? 0 : count;
        if (drawCount < 0) {
            throw new BusinessException("抽题数量不能为负数");
        }
        if (drawCount == 0) {
            return Collections.emptyList();
        }

        List<PerfIndicator> candidates = indicatorMapper.selectList(new LambdaQueryWrapper<PerfIndicator>()
                .eq(PerfIndicator::getType, type)
                .eq(PerfIndicator::getJobLevel, jobLevel));
        if (candidates.size() < drawCount) {
            throw new BusinessException(jobLevel + " 职级"
                    + ("OBJECTIVE".equals(type) ? "客观题" : "主观题")
                    + "题库数量不足，当前可用 " + candidates.size() + " 道，要求抽取 " + drawCount + " 道");
        }
        Collections.shuffle(candidates);
        return candidates.stream()
                .limit(drawCount)
                .map(PerfIndicator::getId)
                .collect(Collectors.toList());
    }

    private int safeCount(Integer count) {
        int value = count == null ? 0 : count;
        if (value < 0) {
            throw new BusinessException("抽题数量不能为负数");
        }
        return value;
    }

    private String currentCycleName() {
        LocalDateTime now = LocalDateTime.now();
        int quarter = (now.getMonthValue() - 1) / 3 + 1;
        return now.getYear() + "-Q" + quarter;
    }
}
