package com.jixiaotong.performance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReviewVO {
    private Long id;
    private Long reviewId;
    private Long employeeId;
    private String employeeName;
    private Long templateId;
    private String templateName;
    private String cycleName;
    private Long managerId;
    private String managerName;
    private String status;
    private Integer durationMinutes;
    private LocalDateTime deadline;
    private BigDecimal objectiveScore;
    private BigDecimal subjectiveScore;
    private BigDecimal totalScore;
    private LocalDateTime startTime;
    private LocalDateTime submitTime;
    private Integer appealCount;
    private LocalDateTime appealDeadline;
    private Boolean canAppeal;
    private String latestAppealStatus;
    private String latestAppealReason;
    private String latestReviewOpinion;
    private String managerComment;
    private List<ReviewDetailVO> details;
    private List<ReviewDetailVO> subjectiveDetails;
}
