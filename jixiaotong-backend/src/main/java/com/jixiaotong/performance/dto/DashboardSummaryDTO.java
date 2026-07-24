package com.jixiaotong.performance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardSummaryDTO {
    private Long managerId;
    private String cycleName;
    private Integer totalCount;
    private Integer completedCount;
    private BigDecimal completionRate;
    private BigDecimal averageScore;
    private List<Integer> distData;
}
