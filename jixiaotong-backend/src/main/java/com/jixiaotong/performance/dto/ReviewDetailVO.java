package com.jixiaotong.performance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ReviewDetailVO {
    private Long reviewDetailId;
    private Long indicatorId;
    private String indicatorName;
    private String type;
    private String optionsContent;
    private String standardAnswer;
    private BigDecimal weightRatio;
    private String answer;
    private String employeeAnswer;
    private BigDecimal objectiveScore;
    private String aiComment;
    private BigDecimal managerScore;
    private BigDecimal finalScore;
    private String status;
}
