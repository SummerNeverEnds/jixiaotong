package com.jixiaotong.performance.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jixiaotong.performance.entity.PerfReview;
import com.jixiaotong.performance.entity.PerfReviewDetail;
import com.jixiaotong.performance.entity.PerfTemplate;
import com.jixiaotong.performance.entity.SysUser;
import com.jixiaotong.performance.es.entity.PerfReviewEsDoc;
import com.jixiaotong.performance.es.repository.PerfReviewEsRepository;
import com.jixiaotong.performance.event.ReviewGradedEvent;
import com.jixiaotong.performance.mapper.PerfReviewDetailMapper;
import com.jixiaotong.performance.mapper.PerfReviewMapper;
import com.jixiaotong.performance.mapper.PerfTemplateMapper;
import com.jixiaotong.performance.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewGradedListener {

    private final PerfReviewMapper reviewMapper;
    private final PerfTemplateMapper templateMapper;
    private final PerfReviewDetailMapper reviewDetailMapper;
    private final SysUserMapper sysUserMapper;
    private final PerfReviewEsRepository esRepository;

    @Async
    @EventListener
    public void handleReviewGradedEvent(ReviewGradedEvent event) {
        Long reviewId = event.getReviewId();
        log.info("收到归档事件，同步 ES，reviewId: {}", reviewId);

        try {
            PerfReview review = reviewMapper.selectById(reviewId);
            if (review == null || (!"GRADED".equals(review.getStatus()) && !"CHEATED".equals(review.getStatus()))) {
                return;
            }

            PerfTemplate template = templateMapper.selectById(review.getTemplateId());
            String cycleName = template != null ? template.getCycleName() : "UNKNOWN";
            SysUser employee = sysUserMapper.selectById(review.getEmployeeId());

            List<PerfReviewDetail> details = reviewDetailMapper.selectList(
                    new LambdaQueryWrapper<PerfReviewDetail>().eq(PerfReviewDetail::getReviewId, reviewId));

            String employeeAnswers = details.stream()
                    .map(PerfReviewDetail::getEmployeeAnswer)
                    .filter(ans -> ans != null && !ans.trim().isEmpty())
                    .collect(Collectors.joining("。"));

            String aiComments = details.stream()
                    .map(PerfReviewDetail::getAiComment)
                    .filter(ai -> ai != null && !ai.trim().isEmpty())
                    .collect(Collectors.joining("。"));

            PerfReviewEsDoc esDoc = PerfReviewEsDoc.builder()
                    .id(String.valueOf(reviewId))
                    .reviewId(reviewId)
                    .employeeId(review.getEmployeeId())
                    .employeeName(employee == null ? "员工" + review.getEmployeeId() : employee.getRealName())
                    .cycleName(cycleName)
                    .totalScore(review.getTotalScore())
                    .employeeAnswers(employeeAnswers)
                    .aiComments(aiComments)
                    .managerComment(review.getManagerComment())
                    .archiveTime(LocalDateTime.now())
                    .build();

            esRepository.save(esDoc);
            log.info("ES 同步成功, reviewId: {}", reviewId);
        } catch (Exception e) {
            log.error("归档事件同步 ES 失败, reviewId: {}", reviewId, e);
        }
    }
}
