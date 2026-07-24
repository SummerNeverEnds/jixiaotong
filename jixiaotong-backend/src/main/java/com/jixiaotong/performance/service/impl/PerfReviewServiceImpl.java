package com.jixiaotong.performance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jixiaotong.performance.dto.*;
import com.jixiaotong.performance.entity.*;
import com.jixiaotong.performance.event.ReviewGradedEvent;
import com.jixiaotong.performance.exception.BusinessException;
import com.jixiaotong.performance.exception.ReviewTimeoutException;
import com.jixiaotong.performance.mapper.*;
import com.jixiaotong.performance.service.AiEvaluationService;
import com.jixiaotong.performance.service.PerfEmployeeScoreService;
import com.jixiaotong.performance.service.PerfReviewService;
import com.jixiaotong.performance.service.SysNotificationService;
import com.jixiaotong.performance.security.AuthSupport;
import com.jixiaotong.performance.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerfReviewServiceImpl extends ServiceImpl<PerfReviewMapper, PerfReview> implements PerfReviewService {

    private final PerfTemplateMapper templateMapper;
    private final PerfTemplateIndicatorMapper templateIndicatorMapper;
    private final PerfIndicatorMapper indicatorMapper;
    private final PerfReviewDetailMapper reviewDetailMapper;
    private final PerfReviewAppealMapper appealMapper;
    private final PerfReviewAppealDetailMapper appealDetailMapper;
    private final SysUserMapper sysUserMapper;
    private final AiEvaluationService aiEvaluationService;
    private final PerfEmployeeScoreService perfEmployeeScoreService;
    private final ApplicationEventPublisher eventPublisher;
    private final SysNotificationService sysNotificationService;
    
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String SUBMIT_LOCK_PREFIX = "perf:submit:";
    private static final String DRAFT_KEY_PREFIX = "perf:draft:";
    private static final String CHEAT_KEY_PREFIX = "perf:cheat:";
    private static final int CHEAT_INVALIDATE_THRESHOLD = 2;
    private static final long DURATION_GRACE_SECONDS = 60;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublishResultDTO generateReview(Long templateId, List<Long> employeeIds, Long managerId) {
        if (templateId == null || managerId == null) {
            throw new BusinessException("模板ID和经理ID不能为空");
        }
        if (CollectionUtils.isEmpty(employeeIds)) {
            throw new BusinessException("本部门暂无员工可下发考核");
        }
        log.info("开始为模板 ID: {} 生成考核快照，员工数: {}", templateId, employeeIds.size());
        
        PerfTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException("考核模板不存在");
        }
        if (template.getDeadline() == null) {
            throw new BusinessException("请先设置考核截止日期");
        }
        if (template.getDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException("考核截止日期已过，无法发布");
        }
        
        int totalQuestionCount = getTemplateQuestionCount(template);
        Map<String, Set<String>> usedExamSignatures = new HashMap<>();
        int createdCount = 0;
        List<Long> skippedIds = new ArrayList<>();
        List<String> skippedNames = new ArrayList<>();

        for (Long empId : employeeIds) {
            SysUser employee = sysUserMapper.selectById(empId);
            if (employee == null || !"EMPLOYEE".equals(employee.getRole())) {
                throw new BusinessException("员工ID " + empId + " 不存在或不是员工角色");
            }

            Long existCount = this.baseMapper.selectCount(new LambdaQueryWrapper<PerfReview>()
                    .eq(PerfReview::getEmployeeId, empId)
                    .eq(PerfReview::getTemplateId, templateId));
            if (existCount > 0) {
                log.warn("员工 ID: {} 在模板 ID: {} 下已存在考核快照，跳过", empId, templateId);
                skippedIds.add(empId);
                skippedNames.add(employee.getRealName());
                continue;
            }

            List<PerfTemplateIndicator> employeeIndicators = getEmployeeTemplateIndicators(template, employee, totalQuestionCount, usedExamSignatures);
            if (CollectionUtils.isEmpty(employeeIndicators)) {
                throw new BusinessException("模板未配置考核题目，无法下发");
            }
            List<Long> indicatorIds = employeeIndicators.stream()
                    .map(PerfTemplateIndicator::getIndicatorId)
                    .collect(Collectors.toList());
            Map<Long, PerfIndicator> indicatorMap = indicatorMapper.selectBatchIds(indicatorIds).stream()
                    .collect(Collectors.toMap(PerfIndicator::getId, i -> i));

            PerfReview review = PerfReview.builder()
                    .employeeId(empId)
                    .templateId(templateId)
                    .managerId(managerId)
                    .status("UNSTARTED")
                    .objectiveScore(BigDecimal.ZERO)
                    .subjectiveScore(BigDecimal.ZERO)
                    .totalScore(BigDecimal.ZERO)
                    .appealCount(0)
                    .build();
            this.baseMapper.insert(review);

            List<PerfReviewDetail> details = new ArrayList<>();
            for (PerfTemplateIndicator tplInd : employeeIndicators) {
                PerfIndicator indicatorInfo = indicatorMap.get(tplInd.getIndicatorId());
                if (indicatorInfo == null) {
                    continue;
                }

                PerfReviewDetail detail = PerfReviewDetail.builder()
                        .reviewId(review.getId())
                        .indicatorId(tplInd.getIndicatorId())
                        .status("PENDING")
                        .objectiveScore(BigDecimal.ZERO)
                        .managerScore(BigDecimal.ZERO)
                        .finalScore(BigDecimal.ZERO)
                        .build();
                details.add(detail);
            }
            if (details.isEmpty()) {
                this.removeById(review.getId());
                throw new BusinessException("员工 " + employee.getRealName() + " 组卷失败：题目快照为空");
            }

            details.forEach(reviewDetailMapper::insert);
            createdCount++;
            sysNotificationService.notifyUser(
                    empId,
                    "新考核试卷已下发",
                    "试卷《" + template.getName() + "》已下发，请在截止日期前完成考试。",
                    "EXAM_ASSIGNED",
                    "/employee/review"
            );
        }

        if (createdCount == 0) {
            throw new BusinessException("本部门员工均已下发过该试卷，未新增考核单");
        }
        
        template.setStatus("PUBLISHED");
        templateMapper.updateById(template);
        
        log.info("考核快照生成完毕，模板 ID: {}，新建 {} 份，跳过 {} 份", templateId, createdCount, skippedIds.size());
        return PublishResultDTO.builder()
                .createdCount(createdCount)
                .skippedCount(skippedIds.size())
                .skippedEmployeeIds(skippedIds)
                .skippedNames(skippedNames)
                .build();
    }

    private List<PerfTemplateIndicator> getEmployeeTemplateIndicators(PerfTemplate template,
                                                                      SysUser employee,
                                                                      int totalQuestionCount,
                                                                      Map<String, Set<String>> usedExamSignatures) {
        int objectiveCount = template.getObjectiveCount() == null ? 0 : template.getObjectiveCount();
        int subjectiveCount = template.getSubjectiveCount() == null ? 0 : template.getSubjectiveCount();
        if (objectiveCount + subjectiveCount > 0) {
            String jobLevel = StringUtils.hasText(employee.getJobLevel()) ? employee.getJobLevel() : "P1";
            List<Long> indicatorIds = drawDistinctIndicatorIdsForEmployee(jobLevel, objectiveCount, subjectiveCount, usedExamSignatures);
            ensureTemplateIndicatorRelations(template.getId(), indicatorIds, totalQuestionCount);
            return templateIndicatorMapper.selectList(new LambdaQueryWrapper<PerfTemplateIndicator>()
                    .eq(PerfTemplateIndicator::getTemplateId, template.getId())
                    .in(PerfTemplateIndicator::getIndicatorId, indicatorIds));
        }

        return templateIndicatorMapper.selectList(new LambdaQueryWrapper<PerfTemplateIndicator>()
                .eq(PerfTemplateIndicator::getTemplateId, template.getId()));
    }

    private List<Long> drawDistinctIndicatorIdsForEmployee(String jobLevel,
                                                           int objectiveCount,
                                                           int subjectiveCount,
                                                           Map<String, Set<String>> usedExamSignatures) {
        Set<String> usedSignatures = usedExamSignatures.computeIfAbsent(jobLevel, level -> new HashSet<>());
        List<Long> selectedIds = Collections.emptyList();
        String signature = "";

        for (int attempt = 0; attempt < 30; attempt++) {
            selectedIds = new ArrayList<>();
            selectedIds.addAll(drawIndicatorsByTypeAndLevel("OBJECTIVE", jobLevel, objectiveCount));
            selectedIds.addAll(drawIndicatorsByTypeAndLevel("SUBJECTIVE", jobLevel, subjectiveCount));
            signature = buildExamSignature(selectedIds);
            if (!usedSignatures.contains(signature)) {
                usedSignatures.add(signature);
                return selectedIds;
            }
        }

        usedSignatures.add(signature);
        return selectedIds;
    }

    private String buildExamSignature(List<Long> indicatorIds) {
        return indicatorIds.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private List<Long> drawIndicatorsByTypeAndLevel(String type, String jobLevel, int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        List<PerfIndicator> candidates = indicatorMapper.selectList(new LambdaQueryWrapper<PerfIndicator>()
                .eq(PerfIndicator::getType, type)
                .eq(PerfIndicator::getJobLevel, jobLevel));
        if (candidates.size() < count) {
            throw new BusinessException(jobLevel + " 职级"
                    + ("OBJECTIVE".equals(type) ? "客观题" : "主观题")
                    + "题库数量不足，当前可用 " + candidates.size() + " 道，要求抽取 " + count + " 道");
        }
        Collections.shuffle(candidates);
        return candidates.stream()
                .limit(count)
                .map(PerfIndicator::getId)
                .collect(Collectors.toList());
    }

    private void ensureTemplateIndicatorRelations(Long templateId, List<Long> indicatorIds, int totalQuestionCount) {
        if (CollectionUtils.isEmpty(indicatorIds)) {
            return;
        }
        BigDecimal weight = new BigDecimal("100")
                .divide(new BigDecimal(totalQuestionCount), 2, RoundingMode.HALF_UP);
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

    private int getTemplateQuestionCount(PerfTemplate template) {
        int objectiveCount = template.getObjectiveCount() == null ? 0 : template.getObjectiveCount();
        int subjectiveCount = template.getSubjectiveCount() == null ? 0 : template.getSubjectiveCount();
        int total = objectiveCount + subjectiveCount;
        if (total > 0) {
            return total;
        }
        Long fixedCount = templateIndicatorMapper.selectCount(new LambdaQueryWrapper<PerfTemplateIndicator>()
                .eq(PerfTemplateIndicator::getTemplateId, template.getId()));
        return fixedCount.intValue();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitSelfEval(SelfEvalSubmitDTO submitDTO) {
        if (submitDTO == null || submitDTO.getEmployeeId() == null || submitDTO.getTemplateId() == null || submitDTO.getReviewId() == null) {
            throw new BusinessException("提交参数不完整");
        }
        if (CollectionUtils.isEmpty(submitDTO.getDetailItems())) {
            throw new BusinessException("提交明细不能为空");
        }
        AuthSupport.requireSelf(submitDTO.getEmployeeId());
        Long employeeId = submitDTO.getEmployeeId();
        Long templateId = submitDTO.getTemplateId();
        Long reviewId = submitDTO.getReviewId();

        String lockKey = SUBMIT_LOCK_PREFIX + templateId + ":" + employeeId;
        boolean redisLockEnabled = true;
        Boolean lockAcquired = Boolean.TRUE;
        try {
            lockAcquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", 10, TimeUnit.SECONDS);
        } catch (Exception e) {
            redisLockEnabled = false;
            log.warn("Redis 暂不可用，提交防重锁降级为数据库状态校验: {}", e.getMessage());
        }

        if (Boolean.FALSE.equals(lockAcquired)) {
            throw new BusinessException("系统正在处理您的提交，请勿重复操作！");
        }

        boolean releaseLockAfterCommit = false;
        try {
            PerfReview review = this.getById(reviewId);
            if (review == null || !review.getEmployeeId().equals(employeeId)) {
                throw new BusinessException("非法操作，找不到对应的考核记录");
            }
            if (!review.getTemplateId().equals(templateId)) {
                throw new BusinessException("提交模板与考核单不匹配");
            }
            if (!"IN_PROGRESS".equals(review.getStatus())) {
                throw new BusinessException("请先开始考试，且考试未结束时才允许提交");
            }

            PerfTemplate template = templateMapper.selectById(templateId);
            if (template == null) {
                throw new BusinessException("模板数据异常");
            }

            LocalDateTime now = LocalDateTime.now();
            if (template.getDeadline() != null && now.isAfter(template.getDeadline())) {
                throw new ReviewTimeoutException("已超过考核截止时间：" + template.getDeadline() + "，无法提交");
            }
            if (template.getDurationMinutes() != null && review.getStartTime() != null) {
                LocalDateTime expireAt = review.getStartTime()
                        .plusMinutes(template.getDurationMinutes())
                        .plusSeconds(DURATION_GRACE_SECONDS);
                if (now.isAfter(expireAt)) {
                    throw new ReviewTimeoutException("已超过允许的答题时长，无法提交");
                }
            }

            List<PerfReviewDetail> allDetails = reviewDetailMapper.selectList(
                    new LambdaQueryWrapper<PerfReviewDetail>().eq(PerfReviewDetail::getReviewId, reviewId));
            if (allDetails.isEmpty()) {
                throw new BusinessException("考核明细不存在");
            }
            Set<Long> submittedDetailIds = submitDTO.getDetailItems().stream()
                    .map(SelfEvalSubmitDTO.EvalDetailItem::getReviewDetailId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
            Set<Long> requiredDetailIds = allDetails.stream().map(PerfReviewDetail::getId).collect(Collectors.toSet());
            if (!submittedDetailIds.equals(requiredDetailIds)) {
                throw new BusinessException("提交题目不完整，请刷新后重新作答并提交全部题目");
            }

            List<Long> indicatorIds = submitDTO.getDetailItems().stream()
                    .map(SelfEvalSubmitDTO.EvalDetailItem::getIndicatorId)
                    .collect(Collectors.toList());
            Map<Long, PerfIndicator> indicatorMap = indicatorMapper.selectBatchIds(indicatorIds).stream()
                    .collect(Collectors.toMap(PerfIndicator::getId, i -> i));

            Map<Long, PerfTemplateIndicator> tplIndMap = templateIndicatorMapper.selectList(
                    new LambdaQueryWrapper<PerfTemplateIndicator>()
                            .eq(PerfTemplateIndicator::getTemplateId, templateId)
                            .in(PerfTemplateIndicator::getIndicatorId, indicatorIds)
            ).stream().collect(Collectors.toMap(PerfTemplateIndicator::getIndicatorId, t -> t));

            BigDecimal totalObjectiveScore = BigDecimal.ZERO;
            BigDecimal totalSubjectiveScore = BigDecimal.ZERO;

            for (SelfEvalSubmitDTO.EvalDetailItem item : submitDTO.getDetailItems()) {
                if (item.getReviewDetailId() == null || item.getIndicatorId() == null) {
                    throw new BusinessException("提交明细参数不完整");
                }
                PerfReviewDetail detail = reviewDetailMapper.selectById(item.getReviewDetailId());
                if (detail == null || !reviewId.equals(detail.getReviewId()) || !item.getIndicatorId().equals(detail.getIndicatorId())) {
                    throw new BusinessException("提交明细与考核单不匹配");
                }

                PerfIndicator indicator = indicatorMap.get(item.getIndicatorId());
                PerfTemplateIndicator tplInd = tplIndMap.get(item.getIndicatorId());
                if (indicator == null || tplInd == null) {
                    throw new BusinessException("提交明细包含未配置的题目");
                }

                detail.setEmployeeAnswer(item.getAnswer());

                if ("OBJECTIVE".equals(indicator.getType())) {
                    BigDecimal maxScore = tplInd.getWeightRatio();
                    if (StringUtils.hasText(item.getAnswer()) && item.getAnswer().trim().equalsIgnoreCase(indicator.getStandardAnswer())) {
                        detail.setObjectiveScore(maxScore);
                        detail.setFinalScore(maxScore);
                        totalObjectiveScore = totalObjectiveScore.add(maxScore);
                    } else {
                        detail.setObjectiveScore(BigDecimal.ZERO);
                        detail.setFinalScore(BigDecimal.ZERO);
                    }
                    detail.setStatus("REVIEWED");
                } else {
                    BigDecimal maxScore = tplInd.getWeightRatio() == null ? BigDecimal.ZERO : tplInd.getWeightRatio();
                    BigDecimal subjectiveScore = StringUtils.hasText(item.getAnswer())
                            ? maxScore.multiply(new BigDecimal("0.60")).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    detail.setManagerScore(subjectiveScore);
                    detail.setFinalScore(subjectiveScore);
                    detail.setStatus("REVIEWED");
                    if (!StringUtils.hasText(item.getAnswer())) {
                        detail.setAiComment("未填写作答，已跳过 AI 预审。");
                    }
                    totalSubjectiveScore = totalSubjectiveScore.add(subjectiveScore);
                }

                reviewDetailMapper.updateById(detail);
            }

            review.setObjectiveScore(totalObjectiveScore);
            review.setSubjectiveScore(totalSubjectiveScore);
            review.setTotalScore(totalObjectiveScore.add(totalSubjectiveScore).setScale(2, RoundingMode.HALF_UP));
            review.setStatus("SUBMITTED");
            review.setSubmitTime(now);
            review.setAppealCount(review.getAppealCount() == null ? 0 : review.getAppealCount());
            review.setAppealDeadline(now.plusDays(3));
            this.updateById(review);
            clearDraft(reviewId, employeeId);
            perfEmployeeScoreService.refreshByReviewId(reviewId);
            triggerAiEvaluationAfterCommit(reviewId);
            releaseLockAfterCommit = redisLockEnabled;

            log.info("员工 ID: {} 完成模板 ID: {} 考核提交，客观题得分: {}，主观题得分: {}",
                    employeeId, templateId, totalObjectiveScore, totalSubjectiveScore);

        } finally {
            if (redisLockEnabled) {
                if (releaseLockAfterCommit && TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            releaseSubmitLock(lockKey);
                        }
                    });
                } else {
                    releaseSubmitLock(lockKey);
                }
            }
        }
    }

    private void releaseSubmitLock(String lockKey) {
        try {
            stringRedisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("Redis 暂不可用，跳过提交锁释放: {}", e.getMessage());
        }
    }

    private void triggerAiEvaluationAfterCommit(Long reviewId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    aiEvaluationService.evaluateQualitativeIndicatorsAsync(reviewId);
                }
            });
        } else {
            aiEvaluationService.evaluateQualitativeIndicatorsAsync(reviewId);
        }
    }

    @Override
    public void reEvaluateAi(Long reviewId) {
        if (reviewId == null || this.getById(reviewId) == null) {
            throw new BusinessException("考核单不存在");
        }
        aiEvaluationService.evaluateQualitativeIndicatorsAsync(reviewId, true);
    }

    @Override
    public void saveDraft(SelfEvalSubmitDTO draftDTO) {
        if (draftDTO == null || draftDTO.getReviewId() == null || draftDTO.getEmployeeId() == null) {
            throw new BusinessException("草稿参数不完整");
        }
        AuthSupport.requireSelf(draftDTO.getEmployeeId());
        PerfReview review = this.getById(draftDTO.getReviewId());
        if (review == null || !review.getEmployeeId().equals(draftDTO.getEmployeeId())) {
            throw new BusinessException("考核单不存在");
        }
        if (!"IN_PROGRESS".equals(review.getStatus())) {
            throw new BusinessException("仅考试进行中可暂存草稿");
        }
        try {
            String key = DRAFT_KEY_PREFIX + draftDTO.getReviewId() + ":" + draftDTO.getEmployeeId();
            String json = objectMapper.writeValueAsString(draftDTO);
            int ttlHours = 24;
            PerfTemplate template = templateMapper.selectById(review.getTemplateId());
            if (template != null && template.getDurationMinutes() != null) {
                ttlHours = Math.max(2, template.getDurationMinutes() / 30 + 2);
            }
            stringRedisTemplate.opsForValue().set(key, json, ttlHours, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            throw new BusinessException("草稿序列化失败");
        } catch (Exception e) {
            log.warn("Redis 暂不可用，草稿暂存失败: {}", e.getMessage());
            throw new BusinessException("草稿暂存失败，请稍后重试");
        }
    }

    @Override
    public SelfEvalSubmitDTO getDraft(Long reviewId, Long employeeId) {
        if (reviewId == null || employeeId == null) {
            return null;
        }
        try {
            String key = DRAFT_KEY_PREFIX + reviewId + ":" + employeeId;
            String json = stringRedisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, SelfEvalSubmitDTO.class);
        } catch (Exception e) {
            log.warn("读取草稿失败: {}", e.getMessage());
            return null;
        }
    }

    private void clearDraft(Long reviewId, Long employeeId) {
        try {
            stringRedisTemplate.delete(DRAFT_KEY_PREFIX + reviewId + ":" + employeeId);
            stringRedisTemplate.delete(CHEAT_KEY_PREFIX + reviewId);
        } catch (Exception e) {
            log.warn("清理草稿/作弊计数失败: {}", e.getMessage());
        }
    }

    @Override
    public ReviewVO getCurrentReview(Long employeeId) {
        PerfReview review = this.getOne(new LambdaQueryWrapper<PerfReview>()
                .eq(PerfReview::getEmployeeId, employeeId)
                .orderByDesc(PerfReview::getCreateTime)
                .last("LIMIT 1"));
        return review == null ? null : buildReviewVO(review);
    }

    @Override
    public List<ReviewVO> listEmployeeReviews(Long employeeId) {
        return listEmployeeReviews(employeeId, null, null, null, null);
    }

    @Override
    public List<ReviewVO> listEmployeeReviews(Long employeeId, String templateName, String cycleName,
                                              String sortBy, String sortOrder) {
        archiveExpiredReviews();
        List<ReviewVO> list = this.list(new LambdaQueryWrapper<PerfReview>()
                        .eq(PerfReview::getEmployeeId, employeeId)
                        .orderByDesc(PerfReview::getCreateTime))
                .stream()
                .map(this::buildReviewVO)
                .collect(Collectors.toList());
        if (StringUtils.hasText(templateName)) {
            String keyword = templateName.trim();
            list = list.stream()
                    .filter(item -> item.getTemplateName() != null && item.getTemplateName().contains(keyword))
                    .collect(Collectors.toList());
        }
        if (StringUtils.hasText(cycleName)) {
            String cycle = cycleName.trim();
            list = list.stream()
                    .filter(item -> cycle.equals(item.getCycleName()))
                    .collect(Collectors.toList());
        }
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        if ("cycle".equalsIgnoreCase(sortBy)) {
            Comparator<ReviewVO> comparator = Comparator.comparing(
                    item -> item.getCycleName() == null ? "" : item.getCycleName());
            list.sort(asc ? comparator : comparator.reversed());
        } else if ("score".equalsIgnoreCase(sortBy)) {
            Comparator<ReviewVO> comparator = Comparator.comparing(
                    item -> item.getTotalScore() == null ? BigDecimal.ZERO : item.getTotalScore());
            list.sort(asc ? comparator : comparator.reversed());
        }
        return list;
    }

    @Override
    public EmployeePerformanceDTO getEmployeePerformance(Long employeeId, String cycleName) {
        return perfEmployeeScoreService.getEmployeePerformance(employeeId, cycleName);
    }

    @Override
    public List<EmployeePerformanceDTO> listEmployeePerformanceHistory(Long employeeId) {
        return perfEmployeeScoreService.listEmployeePerformanceHistory(employeeId);
    }

    @Override
    public List<EmployeePerformanceDTO> listCompanyPerformance(String cycleName, String keyword, Long deptId) {
        return perfEmployeeScoreService.listCompanyPerformance(cycleName, keyword, deptId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewVO startReview(Long reviewId, Long employeeId) {
        PerfReview review = this.getById(reviewId);
        if (review == null || !review.getEmployeeId().equals(employeeId)) {
            throw new BusinessException("考核单不存在");
        }
        if ("CHEATED".equals(review.getStatus())) {
            throw new BusinessException("该考核单已被登记为作弊零分");
        }
        if ("SUBMITTED".equals(review.getStatus()) || "APPEALING".equals(review.getStatus()) || "GRADED".equals(review.getStatus())) {
            throw new BusinessException("该考核单已提交或归档，无法重新开始");
        }
        PerfTemplate template = templateMapper.selectById(review.getTemplateId());
        if (template == null) {
            throw new BusinessException("考核模板不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (template.getDeadline() != null && now.isAfter(template.getDeadline())) {
            throw new ReviewTimeoutException("已超过考核截止时间，无法开始考试");
        }
        if ("UNSTARTED".equals(review.getStatus())) {
            review.setStatus("IN_PROGRESS");
            review.setStartTime(now);
            this.updateById(review);
        }
        return buildReviewVO(this.getById(reviewId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String reportCheat(Long reviewId, Long employeeId) {
        PerfReview review = this.getById(reviewId);
        if (review == null || !review.getEmployeeId().equals(employeeId)) {
            return "IGNORED";
        }
        if (!"IN_PROGRESS".equals(review.getStatus())) {
            return "IGNORED";
        }

        long cheatCount = 1L;
        try {
            String key = CHEAT_KEY_PREFIX + reviewId;
            Long incremented = stringRedisTemplate.opsForValue().increment(key);
            if (incremented != null) {
                cheatCount = incremented;
                if (incremented == 1L) {
                    stringRedisTemplate.expire(key, 2, TimeUnit.DAYS);
                }
            }
        } catch (Exception e) {
            log.warn("Redis 暂不可用，切屏仅返回警告: {}", e.getMessage());
            return "WARNED";
        }

        if (cheatCount < CHEAT_INVALIDATE_THRESHOLD) {
            return "WARNED";
        }
        markCheated(reviewId, employeeId);
        return "CHEATED";
    }

    @Override
    public void markCheated(Long reviewId, Long employeeId) {
        PerfReview review = this.getById(reviewId);
        if (review == null || !review.getEmployeeId().equals(employeeId)) {
            return;
        }
        if (!"IN_PROGRESS".equals(review.getStatus())) {
            return;
        }
        review.setStatus("CHEATED");
        review.setObjectiveScore(BigDecimal.ZERO);
        review.setSubjectiveScore(BigDecimal.ZERO);
        review.setTotalScore(BigDecimal.ZERO);
        review.setSubmitTime(LocalDateTime.now());
        review.setManagerComment("系统判定：考试过程中多次切屏或离开页面，按作弊处理，成绩为 0 分。");
        this.updateById(review);
        clearDraft(reviewId, employeeId);

        List<PerfReviewDetail> details = reviewDetailMapper.selectList(new LambdaQueryWrapper<PerfReviewDetail>()
                .eq(PerfReviewDetail::getReviewId, reviewId));
        for (PerfReviewDetail detail : details) {
            detail.setObjectiveScore(BigDecimal.ZERO);
            detail.setManagerScore(BigDecimal.ZERO);
            detail.setFinalScore(BigDecimal.ZERO);
            detail.setStatus("REVIEWED");
            reviewDetailMapper.updateById(detail);
        }
        eventPublisher.publishEvent(new ReviewGradedEvent(this, reviewId));
        perfEmployeeScoreService.refreshByReviewId(reviewId);
    }

    @Override
    public List<ExportReviewDTO> listExportReviews(Long managerId, String cycleName) {
        String targetCycle = StringUtils.hasText(cycleName)
                ? cycleName.trim()
                : perfEmployeeScoreService.currentCycleName();

        LambdaQueryWrapper<PerfReview> wrapper = new LambdaQueryWrapper<>();
        if (managerId != null) {
            wrapper.eq(PerfReview::getManagerId, managerId);
        }
        wrapper.orderByDesc(PerfReview::getSubmitTime).orderByDesc(PerfReview::getCreateTime);
        List<PerfReview> reviews = this.list(wrapper);
        Map<String, String> statusTextMap = Map.of(
                "UNSTARTED", "未开始",
                "IN_PROGRESS", "考试中",
                "SUBMITTED", "申诉期",
                "APPEALING", "申诉中",
                "GRADED", "已归档",
                "CHEATED", "作弊零分"
        );
        return reviews.stream()
                .map(review -> {
                    PerfTemplate template = templateMapper.selectById(review.getTemplateId());
                    if (template == null || !targetCycle.equals(template.getCycleName())) {
                        return null;
                    }
                    SysUser employee = sysUserMapper.selectById(review.getEmployeeId());
                    return ExportReviewDTO.builder()
                            .reviewId(review.getId())
                            .employeeName(employee == null ? "-" : employee.getRealName())
                            .username(employee == null ? "-" : employee.getUsername())
                            .cycleName(template == null ? "-" : template.getCycleName())
                            .templateName(template == null ? "-" : template.getName())
                            .status(review.getStatus())
                            .statusText(statusTextMap.getOrDefault(review.getStatus(), review.getStatus()))
                            .objectiveScore(review.getObjectiveScore())
                            .subjectiveScore(review.getSubjectiveScore())
                            .totalScore(review.getTotalScore())
                            .submitTime(review.getSubmitTime())
                            .managerComment(review.getManagerComment())
                            .build();
                })
                .filter(item -> item != null)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitAppeal(AppealSubmitDTO appealDTO) {
        archiveExpiredReviews();
        if (appealDTO == null || appealDTO.getReviewId() == null || appealDTO.getEmployeeId() == null) {
            throw new BusinessException("申诉参数不完整");
        }
        if (!StringUtils.hasText(appealDTO.getReason())) {
            throw new BusinessException("请填写申诉理由");
        }

        PerfReview review = this.getById(appealDTO.getReviewId());
        if (review == null || !review.getEmployeeId().equals(appealDTO.getEmployeeId())) {
            throw new BusinessException("考核单不存在");
        }
        if (!"SUBMITTED".equals(review.getStatus())) {
            throw new BusinessException("当前状态不允许提交申诉");
        }
        if (review.getAppealDeadline() == null || LocalDateTime.now().isAfter(review.getAppealDeadline())) {
            throw new BusinessException("已超过申诉期限，成绩已归档");
        }
        int appealCount = review.getAppealCount() == null ? 0 : review.getAppealCount();
        if (appealCount >= 2) {
            throw new BusinessException("每张试卷最多只能申诉两次");
        }

        PerfReviewAppeal appeal = PerfReviewAppeal.builder()
                .reviewId(review.getId())
                .employeeId(review.getEmployeeId())
                .appealNo(appealCount + 1)
                .status("PENDING")
                .build();
        appealMapper.insert(appeal);
        appealDetailMapper.insert(PerfReviewAppealDetail.builder()
                .appealId(appeal.getId())
                .reason(appealDTO.getReason())
                .build());

        review.setAppealCount(appealCount + 1);
        review.setStatus("APPEALING");
        this.updateById(review);

        int appealNo = appealCount + 1;
        PerfTemplate template = templateMapper.selectById(review.getTemplateId());
        String templateName = template == null ? "考核试卷" : template.getName();
        String title = "收到员工成绩申诉";
        String content = "有员工对试卷《" + templateName + "》提交了申诉，请及时复核。";
        if (appealNo < 2) {
            if (review.getManagerId() != null) {
                sysNotificationService.notifyUser(
                        review.getManagerId(),
                        title,
                        content,
                        "APPEAL_PENDING",
                        "/manager/dashboard"
                );
            }
        } else {
            Set<Long> previousReviewers = appealMapper.selectList(new LambdaQueryWrapper<PerfReviewAppeal>()
                            .eq(PerfReviewAppeal::getReviewId, review.getId())
                            .eq(PerfReviewAppeal::getStatus, "RESOLVED")
                            .isNotNull(PerfReviewAppeal::getReviewerManagerId))
                    .stream()
                    .map(PerfReviewAppeal::getReviewerManagerId)
                    .collect(Collectors.toSet());
            List<Long> notifyManagerIds = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getRole, "MANAGER")
                            .select(SysUser::getId))
                    .stream()
                    .map(SysUser::getId)
                    .filter(id -> id != null && !previousReviewers.contains(id))
                    .collect(Collectors.toList());
            sysNotificationService.notifyUsers(
                    notifyManagerIds,
                    title,
                    content,
                    "APPEAL_PENDING",
                    "/manager/dashboard"
            );
        }
    }

    @Override
    public List<AppealVO> listPendingAppeals(Long managerId) {
        archiveExpiredReviews();
        return appealMapper.selectList(new LambdaQueryWrapper<PerfReviewAppeal>()
                        .eq(PerfReviewAppeal::getStatus, "PENDING")
                        .orderByAsc(PerfReviewAppeal::getCreateTime))
                .stream()
                .filter(appeal -> canManagerReviewAppeal(appeal, managerId))
                .map(appeal -> buildAppealVO(appeal, true))
                .collect(Collectors.toList());
    }

    @Override
    public AppealVO getAppealDetail(Long appealId, Long managerId) {
        PerfReviewAppeal appeal = appealMapper.selectById(appealId);
        if (appeal == null) {
            throw new BusinessException("申诉记录不存在");
        }
        if (!canManagerReviewAppeal(appeal, managerId)) {
            throw new BusinessException("二次申诉需由不同经理复核");
        }
        return buildAppealVO(appeal, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewAppeal(AppealReviewDTO reviewDTO) {
        if (reviewDTO == null || reviewDTO.getAppealId() == null || reviewDTO.getManagerId() == null) {
            throw new BusinessException("复核参数不完整");
        }
        if (!StringUtils.hasText(reviewDTO.getReviewOpinion())) {
            throw new BusinessException("请填写复核意见");
        }

        PerfReviewAppeal appeal = appealMapper.selectById(reviewDTO.getAppealId());
        if (appeal == null || !"PENDING".equals(appeal.getStatus())) {
            throw new BusinessException("申诉记录不存在或已复核");
        }
        if (!canManagerReviewAppeal(appeal, reviewDTO.getManagerId())) {
            throw new BusinessException("二次申诉需由不同经理复核");
        }

        PerfReview review = this.getById(appeal.getReviewId());
        if (review == null || !"APPEALING".equals(review.getStatus())) {
            throw new BusinessException("考核单状态异常");
        }

        List<PerfReviewDetail> details = reviewDetailMapper.selectList(new LambdaQueryWrapper<PerfReviewDetail>()
                .eq(PerfReviewDetail::getReviewId, review.getId()));
        List<Long> indicatorIds = details.stream().map(PerfReviewDetail::getIndicatorId).collect(Collectors.toList());
        Map<Long, PerfIndicator> indicatorMap = indicatorIds.isEmpty() ? Collections.emptyMap()
                : indicatorMapper.selectBatchIds(indicatorIds).stream()
                .collect(Collectors.toMap(PerfIndicator::getId, i -> i));
        Map<Long, PerfTemplateIndicator> weightMap = indicatorIds.isEmpty() ? Collections.emptyMap()
                : templateIndicatorMapper.selectList(new LambdaQueryWrapper<PerfTemplateIndicator>()
                        .eq(PerfTemplateIndicator::getTemplateId, review.getTemplateId())
                        .in(PerfTemplateIndicator::getIndicatorId, indicatorIds))
                .stream()
                .collect(Collectors.toMap(PerfTemplateIndicator::getIndicatorId, t -> t));
        Map<Long, BigDecimal> scoreMap = reviewDTO.getScores() == null ? Collections.emptyMap()
                : reviewDTO.getScores().stream()
                .filter(item -> item.getReviewDetailId() != null)
                .collect(Collectors.toMap(AppealReviewDTO.ScoreItem::getReviewDetailId,
                        item -> item.getManagerScore() == null ? BigDecimal.ZERO : item.getManagerScore(),
                        (left, right) -> right));

        BigDecimal subjectiveTotal = BigDecimal.ZERO;
        for (PerfReviewDetail detail : details) {
            PerfIndicator indicator = indicatorMap.get(detail.getIndicatorId());
            if (indicator == null || !"SUBJECTIVE".equals(indicator.getType())) {
                continue;
            }
            BigDecimal maxScore = getWeight(weightMap, detail.getIndicatorId());
            BigDecimal score = scoreMap.get(detail.getId());
            if (score == null) {
                throw new BusinessException("请完成所有主观题复核评分");
            }
            score = score.max(BigDecimal.ZERO).min(maxScore).setScale(2, RoundingMode.HALF_UP);
            detail.setManagerScore(score);
            detail.setFinalScore(score);
            detail.setStatus("REVIEWED");
            reviewDetailMapper.updateById(detail);
            subjectiveTotal = subjectiveTotal.add(score);
        }

        BigDecimal objectiveScore = defaultScore(review.getObjectiveScore());
        review.setSubjectiveScore(subjectiveTotal);
        review.setTotalScore(objectiveScore.add(subjectiveTotal).setScale(2, RoundingMode.HALF_UP));
        review.setManagerComment(reviewDTO.getReviewOpinion());

        LocalDateTime now = LocalDateTime.now();
        int appealCount = review.getAppealCount() == null ? 0 : review.getAppealCount();
        if (appealCount < 2 && (review.getAppealDeadline() == null || !now.isBefore(review.getAppealDeadline()))) {
            review.setAppealDeadline(now.plusDays(1));
            review.setStatus("SUBMITTED");
        } else if (appealCount >= 2) {
            review.setStatus("GRADED");
        } else {
            review.setStatus("SUBMITTED");
        }
        this.updateById(review);

        appeal.setStatus("RESOLVED");
        appeal.setReviewerManagerId(reviewDTO.getManagerId());
        appeal.setReviewTime(now);
        appealMapper.updateById(appeal);

        PerfReviewAppealDetail appealDetail = getAppealDetailEntity(appeal.getId());
        if (appealDetail == null) {
            appealDetailMapper.insert(PerfReviewAppealDetail.builder()
                    .appealId(appeal.getId())
                    .reason("")
                    .reviewOpinion(reviewDTO.getReviewOpinion())
                    .build());
        } else {
            appealDetail.setReviewOpinion(reviewDTO.getReviewOpinion());
            appealDetailMapper.updateById(appealDetail);
        }

        if ("GRADED".equals(review.getStatus())) {
            eventPublisher.publishEvent(new ReviewGradedEvent(this, review.getId()));
        } else {
            archiveExpiredReviews();
        }
        perfEmployeeScoreService.refreshByReviewId(review.getId());

        PerfTemplate template = templateMapper.selectById(review.getTemplateId());
        String templateName = template == null ? "考核试卷" : template.getName();
        sysNotificationService.notifyUser(
                review.getEmployeeId(),
                "申诉复核结果已出",
                "试卷《" + templateName + "》已由经理完成申诉复核，当前总分 " + review.getTotalScore() + "。",
                "APPEAL_RESULT",
                "/employee/review"
        );
    }

    @Override
    public List<ReviewVO> listPendingReviews(Long managerId) {
        LambdaQueryWrapper<PerfReview> wrapper = new LambdaQueryWrapper<PerfReview>()
                .eq(PerfReview::getStatus, "SUBMITTED")
                .orderByDesc(PerfReview::getSubmitTime);
        if (managerId != null) {
            wrapper.eq(PerfReview::getManagerId, managerId);
        }
        return this.list(wrapper).stream()
                .map(this::buildReviewVO)
                .collect(Collectors.toList());
    }

    @Override
    public ReviewVO getReviewDetail(Long reviewId) {
        PerfReview review = this.getById(reviewId);
        if (review == null) {
            throw new BusinessException("考核单不存在");
        }
        return buildReviewVO(review);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void gradeReview(ManagerGradeDTO gradeDTO) {
        throw new BusinessException("经理不参与普通评分，请通过员工申诉复核入口处理");
    }

    @Override
    public DashboardSummaryDTO getDashboardSummary(Long managerId, String cycleName) {
        String targetCycle = StringUtils.hasText(cycleName)
                ? cycleName.trim()
                : perfEmployeeScoreService.currentCycleName();

        LambdaQueryWrapper<PerfReview> wrapper = new LambdaQueryWrapper<>();
        if (managerId != null) {
            wrapper.eq(PerfReview::getManagerId, managerId);
        }
        List<PerfReview> reviews = this.list(wrapper).stream()
                .filter(review -> {
                    PerfTemplate template = templateMapper.selectById(review.getTemplateId());
                    return template != null && targetCycle.equals(template.getCycleName());
                })
                .collect(Collectors.toList());

        int totalCount = reviews.size();
        List<PerfReview> graded = reviews.stream()
                .filter(review -> "GRADED".equals(review.getStatus()) || "CHEATED".equals(review.getStatus()))
                .collect(Collectors.toList());
        int completedCount = graded.size();
        BigDecimal completionRate = totalCount == 0 ? BigDecimal.ZERO :
                BigDecimal.valueOf(completedCount * 100.0 / totalCount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal averageScore = graded.isEmpty() ? BigDecimal.ZERO :
                graded.stream()
                        .map(review -> review.getTotalScore() == null ? BigDecimal.ZERO : review.getTotalScore())
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(graded.size()), 2, RoundingMode.HALF_UP);

        List<Integer> distData = new ArrayList<>(List.of(0, 0, 0, 0));
        for (PerfReview review : graded) {
            BigDecimal score = review.getTotalScore() == null ? BigDecimal.ZERO : review.getTotalScore();
            if (score.compareTo(new BigDecimal("90")) >= 0) {
                distData.set(0, distData.get(0) + 1);
            } else if (score.compareTo(new BigDecimal("80")) >= 0) {
                distData.set(1, distData.get(1) + 1);
            } else if (score.compareTo(new BigDecimal("60")) >= 0) {
                distData.set(2, distData.get(2) + 1);
            } else {
                distData.set(3, distData.get(3) + 1);
            }
        }

        return DashboardSummaryDTO.builder()
                .managerId(managerId)
                .cycleName(targetCycle)
                .totalCount(totalCount)
                .completedCount(completedCount)
                .completionRate(completionRate)
                .averageScore(averageScore)
                .distData(distData)
                .build();
    }

    private ReviewVO buildReviewVO(PerfReview review) {
        PerfTemplate template = templateMapper.selectById(review.getTemplateId());
        SysUser employee = sysUserMapper.selectById(review.getEmployeeId());
        SysUser manager = sysUserMapper.selectById(review.getManagerId());
        List<PerfReviewDetail> details = reviewDetailMapper.selectList(new LambdaQueryWrapper<PerfReviewDetail>()
                .eq(PerfReviewDetail::getReviewId, review.getId()));

        List<Long> indicatorIds = details.stream().map(PerfReviewDetail::getIndicatorId).collect(Collectors.toList());
        Map<Long, PerfIndicator> indicatorMap = indicatorIds.isEmpty() ? Collections.emptyMap()
                : indicatorMapper.selectBatchIds(indicatorIds).stream()
                .collect(Collectors.toMap(PerfIndicator::getId, i -> i));
        Map<Long, PerfTemplateIndicator> weightMap = indicatorIds.isEmpty() ? Collections.emptyMap()
                : templateIndicatorMapper.selectList(new LambdaQueryWrapper<PerfTemplateIndicator>()
                        .eq(PerfTemplateIndicator::getTemplateId, review.getTemplateId())
                        .in(PerfTemplateIndicator::getIndicatorId, indicatorIds))
                .stream()
                .collect(Collectors.toMap(PerfTemplateIndicator::getIndicatorId, t -> t));

        boolean revealAnswer = !"UNSTARTED".equals(review.getStatus()) && !"IN_PROGRESS".equals(review.getStatus());

        List<ReviewDetailVO> detailVOs = details.stream()
                .map(detail -> {
                    PerfIndicator indicator = indicatorMap.get(detail.getIndicatorId());
                    return ReviewDetailVO.builder()
                            .reviewDetailId(detail.getId())
                            .indicatorId(detail.getIndicatorId())
                            .indicatorName(indicator == null ? "未知指标" : indicator.getName())
                            .type(indicator == null ? "UNKNOWN" : indicator.getType())
                            .optionsContent(indicator == null ? null : indicator.getOptionsContent())
                            .standardAnswer(revealAnswer && indicator != null ? indicator.getStandardAnswer() : null)
                            .weightRatio(getWeight(weightMap, detail.getIndicatorId()))
                            .answer(detail.getEmployeeAnswer())
                            .employeeAnswer(detail.getEmployeeAnswer())
                            .objectiveScore(defaultScore(detail.getObjectiveScore()))
                            .aiComment(detail.getAiComment())
                            .managerScore(defaultScore(detail.getManagerScore()))
                            .finalScore(defaultScore(detail.getFinalScore()))
                            .status(detail.getStatus())
                            .build();
                })
                .collect(Collectors.toList());

        List<ReviewDetailVO> subjectiveDetails = detailVOs.stream()
                .filter(detail -> "SUBJECTIVE".equals(detail.getType()))
                .collect(Collectors.toList());

        PerfReviewAppeal latestAppeal = appealMapper.selectList(new LambdaQueryWrapper<PerfReviewAppeal>()
                        .eq(PerfReviewAppeal::getReviewId, review.getId())
                        .orderByDesc(PerfReviewAppeal::getCreateTime)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .orElse(null);
        PerfReviewAppealDetail latestAppealDetail = latestAppeal == null ? null : getAppealDetailEntity(latestAppeal.getId());
        int appealCount = review.getAppealCount() == null ? 0 : review.getAppealCount();
        boolean canAppeal = "SUBMITTED".equals(review.getStatus())
                && appealCount < 2
                && review.getAppealDeadline() != null
                && !LocalDateTime.now().isAfter(review.getAppealDeadline());

        Long managerId = review.getManagerId();
        String managerName = manager == null ? "主管" + review.getManagerId() : manager.getRealName();
        if ("EMPLOYEE".equals(UserContext.getRole())
                && (appealCount > 0 || "APPEALING".equals(review.getStatus()) || latestAppeal != null)) {
            managerId = null;
            managerName = "匿名经理";
        }

        return ReviewVO.builder()
                .id(review.getId())
                .reviewId(review.getId())
                .employeeId(review.getEmployeeId())
                .employeeName(employee == null ? "员工" + review.getEmployeeId() : employee.getRealName())
                .templateId(review.getTemplateId())
                .templateName(template == null ? "未知考核" : template.getName())
                .cycleName(template == null ? "" : template.getCycleName())
                .managerId(managerId)
                .managerName(managerName)
                .status(review.getStatus())
                .durationMinutes(template == null || template.getDurationMinutes() == null ? 120 : template.getDurationMinutes())
                .deadline(template == null ? null : template.getDeadline())
                .objectiveScore(defaultScore(review.getObjectiveScore()))
                .subjectiveScore(defaultScore(review.getSubjectiveScore()))
                .totalScore(defaultScore(review.getTotalScore()))
                .startTime(review.getStartTime())
                .submitTime(review.getSubmitTime())
                .appealCount(appealCount)
                .appealDeadline(review.getAppealDeadline())
                .canAppeal(canAppeal)
                .latestAppealStatus(latestAppeal == null ? null : latestAppeal.getStatus())
                .latestAppealReason(latestAppealDetail == null ? null : latestAppealDetail.getReason())
                .latestReviewOpinion(latestAppealDetail == null ? null : latestAppealDetail.getReviewOpinion())
                .managerComment(review.getManagerComment())
                .details(detailVOs)
                .subjectiveDetails(subjectiveDetails)
                .build();
    }

    private AppealVO buildAppealVO(PerfReviewAppeal appeal, boolean anonymous) {
        ReviewVO reviewVO = buildReviewVO(this.getById(appeal.getReviewId()));
        if (anonymous) {
            reviewVO.setEmployeeId(null);
            reviewVO.setEmployeeName("匿名员工");
        }
        PerfReviewAppealDetail detail = getAppealDetailEntity(appeal.getId());
        return AppealVO.builder()
                .appealId(appeal.getId())
                .reviewId(appeal.getReviewId())
                .appealNo(appeal.getAppealNo())
                .reason(detail == null ? null : detail.getReason())
                .status(appeal.getStatus())
                .reviewerManagerId(appeal.getReviewerManagerId())
                .reviewOpinion(detail == null ? null : detail.getReviewOpinion())
                .createTime(appeal.getCreateTime())
                .reviewTime(appeal.getReviewTime())
                .review(reviewVO)
                .build();
    }

    private PerfReviewAppealDetail getAppealDetailEntity(Long appealId) {
        if (appealId == null) {
            return null;
        }
        return appealDetailMapper.selectOne(new LambdaQueryWrapper<PerfReviewAppealDetail>()
                .eq(PerfReviewAppealDetail::getAppealId, appealId)
                .last("LIMIT 1"));
    }

    private boolean canManagerReviewAppeal(PerfReviewAppeal appeal, Long managerId) {
        if (appeal == null || !"PENDING".equals(appeal.getStatus())) {
            return false;
        }
        if (managerId == null || appeal.getAppealNo() == null || appeal.getAppealNo() < 2) {
            return true;
        }
        Long reviewedBefore = appealMapper.selectCount(new LambdaQueryWrapper<PerfReviewAppeal>()
                .eq(PerfReviewAppeal::getReviewId, appeal.getReviewId())
                .eq(PerfReviewAppeal::getReviewerManagerId, managerId)
                .eq(PerfReviewAppeal::getStatus, "RESOLVED"));
        return reviewedBefore == 0;
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void archiveExpiredReviews() {
        LocalDateTime now = LocalDateTime.now();
        List<PerfReview> expiredReviews = this.list(new LambdaQueryWrapper<PerfReview>()
                .eq(PerfReview::getStatus, "SUBMITTED")
                .isNotNull(PerfReview::getAppealDeadline)
                .le(PerfReview::getAppealDeadline, now));
        for (PerfReview review : expiredReviews) {
            review.setStatus("GRADED");
            this.updateById(review);
            eventPublisher.publishEvent(new ReviewGradedEvent(this, review.getId()));
            perfEmployeeScoreService.refreshByReviewId(review.getId());
        }

        List<PerfReview> expiredAppealing = this.list(new LambdaQueryWrapper<PerfReview>()
                .eq(PerfReview::getStatus, "APPEALING")
                .isNotNull(PerfReview::getAppealDeadline)
                .le(PerfReview::getAppealDeadline, now));
        for (PerfReview review : expiredAppealing) {
            List<PerfReviewAppeal> pendingAppeals = appealMapper.selectList(new LambdaQueryWrapper<PerfReviewAppeal>()
                    .eq(PerfReviewAppeal::getReviewId, review.getId())
                    .eq(PerfReviewAppeal::getStatus, "PENDING"));
            for (PerfReviewAppeal appeal : pendingAppeals) {
                appeal.setStatus("RESOLVED");
                appeal.setReviewTime(now);
                appealMapper.updateById(appeal);
                PerfReviewAppealDetail appealDetail = getAppealDetailEntity(appeal.getId());
                if (appealDetail == null) {
                    appealDetailMapper.insert(PerfReviewAppealDetail.builder()
                            .appealId(appeal.getId())
                            .reason("")
                            .reviewOpinion("申诉期限已到，系统自动关闭待复核申诉并归档。")
                            .build());
                } else {
                    appealDetail.setReviewOpinion("申诉期限已到，系统自动关闭待复核申诉并归档。");
                    appealDetailMapper.updateById(appealDetail);
                }
            }
            review.setStatus("GRADED");
            if (!StringUtils.hasText(review.getManagerComment())) {
                review.setManagerComment("申诉期限已到，系统自动归档。");
            }
            this.updateById(review);
            eventPublisher.publishEvent(new ReviewGradedEvent(this, review.getId()));
            perfEmployeeScoreService.refreshByReviewId(review.getId());
        }

    }

    private BigDecimal getWeight(Map<Long, PerfTemplateIndicator> weightMap, Long indicatorId) {
        PerfTemplateIndicator relation = weightMap.get(indicatorId);
        return relation == null || relation.getWeightRatio() == null ? BigDecimal.ZERO : relation.getWeightRatio();
    }

    private BigDecimal defaultScore(BigDecimal score) {
        return score == null ? BigDecimal.ZERO : score;
    }
}
