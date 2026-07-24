package com.jixiaotong.performance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jixiaotong.performance.common.PageResult;
import com.jixiaotong.performance.common.Result;
import com.jixiaotong.performance.entity.PerfReview;
import com.jixiaotong.performance.entity.PerfReviewDetail;
import com.jixiaotong.performance.entity.PerfTemplate;
import com.jixiaotong.performance.entity.SysUser;
import com.jixiaotong.performance.es.entity.PerfReviewEsDoc;
import com.jixiaotong.performance.es.repository.PerfReviewEsRepository;
import com.jixiaotong.performance.mapper.PerfReviewDetailMapper;
import com.jixiaotong.performance.mapper.PerfReviewMapper;
import com.jixiaotong.performance.mapper.PerfTemplateMapper;
import com.jixiaotong.performance.mapper.SysUserMapper;
import com.jixiaotong.performance.security.AuthSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class EsSearchController {

    private final PerfReviewEsRepository esRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final PerfReviewMapper reviewMapper;
    private final PerfReviewDetailMapper detailMapper;
    private final PerfTemplateMapper templateMapper;
    private final SysUserMapper userMapper;

    @GetMapping("/archive")
    public Result<PageResult<PerfReviewEsDoc>> searchArchive(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minScore,
            @RequestParam(required = false) BigDecimal maxScore,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        AuthSupport.requireRole("MANAGER", "ADMIN");

        String kw = keyword == null ? "" : keyword.trim();
        validateScoreRange(minScore, maxScore);

        List<PerfReviewEsDoc> mysqlList = buildArchiveDocs(kw.isEmpty() ? null : kw, minScore, maxScore);
        boolean hasFilter = StringUtils.hasText(kw) || minScore != null || maxScore != null;
        if (!hasFilter) {
            return Result.success(PageResult.of(mysqlList, current, size));
        }

        try {
            if (esRepository.count() == 0) {
                syncArchiveToEs();
            }
            List<PerfReviewEsDoc> esList = searchEs(kw, minScore, maxScore);
            if (!esList.isEmpty()) {
                return Result.success(PageResult.of(esList, current, size));
            }
        } catch (Exception e) {
            log.warn("Elasticsearch 检索失败，降级为 MySQL: {}", e.getMessage());
        }
        return Result.success(PageResult.of(mysqlList, current, size));
    }

    private void validateScoreRange(BigDecimal minScore, BigDecimal maxScore) {
        if (minScore != null && maxScore != null && minScore.compareTo(maxScore) > 0) {
            throw new com.jixiaotong.performance.exception.BusinessException("最低分不能大于最高分");
        }
    }

    private List<PerfReviewEsDoc> searchEs(String keyword, BigDecimal minScore, BigDecimal maxScore) {
        Criteria criteria = null;

        if (StringUtils.hasText(keyword)) {
            criteria = new Criteria("managerComment").matches(keyword)
                    .or(new Criteria("employeeAnswers").matches(keyword))
                    .or(new Criteria("aiComments").matches(keyword))
                    .or(new Criteria("employeeName").contains(keyword))
                    .or(new Criteria("cycleName").contains(keyword))
                    .or(new Criteria("employeeName").is(keyword))
                    .or(new Criteria("cycleName").is(keyword));
        }

        Criteria scoreCriteria = buildScoreCriteria(minScore, maxScore);
        if (criteria == null) {
            criteria = scoreCriteria;
        } else if (scoreCriteria != null) {
            criteria = criteria.and(scoreCriteria);
        }

        if (criteria == null) {
            return List.of();
        }

        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setMaxResults(1000);
        return elasticsearchOperations.search(query, PerfReviewEsDoc.class)
                .stream()
                .map(SearchHit::getContent)
                .toList();
    }

    private Criteria buildScoreCriteria(BigDecimal minScore, BigDecimal maxScore) {
        if (minScore == null && maxScore == null) {
            return null;
        }
        if (minScore != null && maxScore != null) {
            return new Criteria("totalScore").between(minScore.doubleValue(), maxScore.doubleValue());
        }
        if (minScore != null) {
            return new Criteria("totalScore").greaterThanEqual(minScore.doubleValue());
        }
        return new Criteria("totalScore").lessThanEqual(maxScore.doubleValue());
    }

    @PostMapping("/sync-archive")
    public Result<Integer> syncArchiveToEs() {
        AuthSupport.requireRole("MANAGER", "ADMIN");
        List<PerfReviewEsDoc> docs = buildArchiveDocs(null, null, null);
        if (docs.isEmpty()) {
            return Result.success(0);
        }
        esRepository.saveAll(docs);
        return Result.success(docs.size());
    }

    private List<PerfReviewEsDoc> buildArchiveDocs(String keyword, BigDecimal minScore, BigDecimal maxScore) {
        List<PerfReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<PerfReview>()
                .in(PerfReview::getStatus, List.of("GRADED", "CHEATED"))
                .orderByDesc(PerfReview::getUpdateTime));
        List<PerfReviewEsDoc> docs = new ArrayList<>();
        for (PerfReview review : reviews) {
            if (!matchesScore(review.getTotalScore(), minScore, maxScore)) {
                continue;
            }

            List<PerfReviewDetail> details = detailMapper.selectList(new LambdaQueryWrapper<PerfReviewDetail>()
                    .eq(PerfReviewDetail::getReviewId, review.getId()));
            String employeeAnswers = details.stream()
                    .map(PerfReviewDetail::getEmployeeAnswer)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining("。"));
            String aiComments = details.stream()
                    .map(PerfReviewDetail::getAiComment)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining("。"));
            String managerComment = review.getManagerComment() == null ? "" : review.getManagerComment();
            PerfTemplate template = templateMapper.selectById(review.getTemplateId());
            SysUser employee = userMapper.selectById(review.getEmployeeId());
            String employeeName = employee == null ? "员工" + review.getEmployeeId() : employee.getRealName();
            String cycleName = template == null ? "" : template.getCycleName();

            if (!matchesKeyword(keyword, employeeName, cycleName, employeeAnswers, aiComments, managerComment)) {
                continue;
            }

            docs.add(PerfReviewEsDoc.builder()
                    .id(String.valueOf(review.getId()))
                    .reviewId(review.getId())
                    .employeeId(review.getEmployeeId())
                    .employeeName(employeeName)
                    .cycleName(cycleName)
                    .totalScore(review.getTotalScore())
                    .employeeAnswers(employeeAnswers)
                    .aiComments(aiComments)
                    .managerComment(managerComment)
                    .archiveTime(review.getUpdateTime() == null ? LocalDateTime.now() : review.getUpdateTime())
                    .build());
        }
        return docs;
    }

    private boolean matchesScore(BigDecimal totalScore, BigDecimal minScore, BigDecimal maxScore) {
        if (minScore == null && maxScore == null) {
            return true;
        }
        BigDecimal score = totalScore == null ? BigDecimal.ZERO : totalScore;
        if (minScore != null && score.compareTo(minScore) < 0) {
            return false;
        }
        if (maxScore != null && score.compareTo(maxScore) > 0) {
            return false;
        }
        return true;
    }

    private boolean matchesKeyword(String keyword, String... fields) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        for (String field : fields) {
            if (field != null && field.toLowerCase(Locale.ROOT).contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
