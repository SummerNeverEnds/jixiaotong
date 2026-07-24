package com.jixiaotong.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jixiaotong.performance.dto.AiEvaluateRequest;
import com.jixiaotong.performance.dto.AiEvaluateResponse;
import com.jixiaotong.performance.entity.PerfIndicator;
import com.jixiaotong.performance.entity.PerfReview;
import com.jixiaotong.performance.entity.PerfReviewDetail;
import com.jixiaotong.performance.entity.PerfTemplateIndicator;
import com.jixiaotong.performance.feign.OaAiServiceClient;
import com.jixiaotong.performance.mapper.PerfIndicatorMapper;
import com.jixiaotong.performance.mapper.PerfReviewDetailMapper;
import com.jixiaotong.performance.mapper.PerfReviewMapper;
import com.jixiaotong.performance.mapper.PerfTemplateIndicatorMapper;
import com.jixiaotong.performance.service.AiEvaluationService;
import com.jixiaotong.performance.service.PerfEmployeeScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiEvaluationServiceImpl implements AiEvaluationService {

    private static final String FALLBACK_COMMENT =
            "AI 服务暂不可用，系统已保留预评分，请结合实际表现通过申诉复核调整。";

    private final PerfReviewDetailMapper reviewDetailMapper;
    private final PerfIndicatorMapper indicatorMapper;
    private final PerfReviewMapper reviewMapper;
    private final PerfTemplateIndicatorMapper templateIndicatorMapper;
    private final OaAiServiceClient oaAiServiceClient;
    private final PerfEmployeeScoreService perfEmployeeScoreService;

    @Async("aiExecutor")
    @Override
    public void evaluateQualitativeIndicatorsAsync(Long reviewId) {
        evaluateQualitativeIndicatorsAsync(reviewId, false);
    }

    @Async("aiExecutor")
    @Override
    public void evaluateQualitativeIndicatorsAsync(Long reviewId, boolean force) {
        log.info("开始异步 AI 预审, reviewId: {}, force: {}", reviewId, force);

        PerfReview review = reviewMapper.selectById(reviewId);
        if (review == null) {
            return;
        }
        boolean allowScoreWrite = "SUBMITTED".equals(review.getStatus());

        List<PerfReviewDetail> details = reviewDetailMapper.selectList(
                new LambdaQueryWrapper<PerfReviewDetail>().eq(PerfReviewDetail::getReviewId, reviewId));
        if (details.isEmpty()) {
            return;
        }

        List<Long> indicatorIds = details.stream().map(PerfReviewDetail::getIndicatorId).collect(Collectors.toList());
        Map<Long, PerfIndicator> indicatorMap = indicatorMapper.selectBatchIds(indicatorIds).stream()
                .filter(i -> "SUBJECTIVE".equals(i.getType()))
                .collect(Collectors.toMap(PerfIndicator::getId, i -> i));
        Map<Long, BigDecimal> weightMap = templateIndicatorMapper.selectList(
                        new LambdaQueryWrapper<PerfTemplateIndicator>()
                                .eq(PerfTemplateIndicator::getTemplateId, review.getTemplateId())
                                .in(PerfTemplateIndicator::getIndicatorId, indicatorIds))
                .stream()
                .collect(Collectors.toMap(PerfTemplateIndicator::getIndicatorId,
                        t -> t.getWeightRatio() == null ? BigDecimal.ZERO : t.getWeightRatio(),
                        (a, b) -> a));

        boolean scoreChanged = false;
        for (PerfReviewDetail detail : details) {
            PerfIndicator indicator = indicatorMap.get(detail.getIndicatorId());
            if (indicator == null) {
                continue;
            }
            if (!StringUtils.hasText(detail.getEmployeeAnswer())) {
                if (!StringUtils.hasText(detail.getAiComment())) {
                    detail.setAiComment("未填写作答，已跳过 AI 预审。");
                    reviewDetailMapper.updateById(detail);
                }
                continue;
            }
            if (!force
                    && StringUtils.hasText(detail.getAiComment())
                    && !detail.getAiComment().contains("AI 服务暂不可用")) {
                continue;
            }

            BigDecimal maxScore = weightMap.getOrDefault(detail.getIndicatorId(), BigDecimal.ZERO);
            try {
                AiEvaluateResponse response = oaAiServiceClient.evaluateQualitative(AiEvaluateRequest.builder()
                        .indicatorId(indicator.getId())
                        .indicatorName(indicator.getName())
                        .employeeAnswer(detail.getEmployeeAnswer())
                        .build());

                if (response == null || response.getCode() == null || response.getCode() != 200
                        || response.getData() == null) {
                    applyFallbackComment(detail);
                    continue;
                }

                detail.setAiComment(truncateComment(response.getData().getAiComment()));
                Integer suggest = response.getData().getSuggestScore();
                if (allowScoreWrite && suggest != null && maxScore.compareTo(BigDecimal.ZERO) > 0) {
                    int clamped = Math.max(0, Math.min(100, suggest));
                    BigDecimal aiScore = maxScore.multiply(BigDecimal.valueOf(clamped))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    detail.setManagerScore(aiScore);
                    detail.setFinalScore(aiScore);
                    scoreChanged = true;
                }
                reviewDetailMapper.updateById(detail);
                log.info("AI 预审完成, reviewId: {}, indicatorId: {}, suggestScore: {}",
                        reviewId, indicator.getId(), suggest);
            } catch (Exception e) {
                applyFallbackComment(detail);
                log.warn("调用 oa-ai-service 失败, reviewId: {}, indicatorId: {}, reason: {}",
                        reviewId, indicator.getId(), e.getMessage());
            }
        }

        if (scoreChanged && allowScoreWrite) {
            recalculateSubjectiveTotal(reviewId, review, indicatorMap);
        }
    }

    private void applyFallbackComment(PerfReviewDetail detail) {
        detail.setAiComment(FALLBACK_COMMENT);
        reviewDetailMapper.updateById(detail);
    }

    private String truncateComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            return "AI 已完成预审，建议结合实际表现复核。";
        }
        String trimmed = comment.trim();
        return trimmed.length() <= 480 ? trimmed : trimmed.substring(0, 479) + "…";
    }

    private void recalculateSubjectiveTotal(Long reviewId,
                                           PerfReview review,
                                           Map<Long, PerfIndicator> indicatorMap) {
        List<PerfReviewDetail> latest = reviewDetailMapper.selectList(
                new LambdaQueryWrapper<PerfReviewDetail>().eq(PerfReviewDetail::getReviewId, reviewId));
        BigDecimal subjectiveTotal = BigDecimal.ZERO;
        for (PerfReviewDetail detail : latest) {
            PerfIndicator indicator = indicatorMap.get(detail.getIndicatorId());
            if (indicator == null) {
                continue;
            }
            subjectiveTotal = subjectiveTotal.add(
                    detail.getFinalScore() == null ? BigDecimal.ZERO : detail.getFinalScore());
        }
        BigDecimal objective = review.getObjectiveScore() == null ? BigDecimal.ZERO : review.getObjectiveScore();
        review.setSubjectiveScore(subjectiveTotal.setScale(2, RoundingMode.HALF_UP));
        review.setTotalScore(objective.add(subjectiveTotal).setScale(2, RoundingMode.HALF_UP));
        if ("SUBMITTED".equals(review.getStatus())) {
            reviewMapper.updateById(review);
            perfEmployeeScoreService.refreshByReviewId(reviewId);
            log.info("AI 评分回写完成, reviewId: {}, subjective={}, total={}",
                    reviewId, review.getSubjectiveScore(), review.getTotalScore());
        }
    }
}
