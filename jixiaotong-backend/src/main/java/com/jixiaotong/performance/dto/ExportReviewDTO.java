package com.jixiaotong.performance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ExportReviewDTO {
    private Long reviewId;
    private String employeeName;
    private String username;
    private String cycleName;
    private String templateName;
    private String status;
    private String statusText;
    private BigDecimal objectiveScore;
    private BigDecimal subjectiveScore;
    private BigDecimal totalScore;
    private LocalDateTime submitTime;
    private String managerComment;
}
