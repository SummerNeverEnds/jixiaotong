package com.jixiaotong.performance.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class DashboardStatDTO {
    private Long deptId;
    
    
    private String cycleName;
    
    
    private Integer totalCount;
    
    
    private Integer completedCount;
    
    
    private BigDecimal completionRate;
    
    private BigDecimal averageScore;
    
    private BigDecimal maxScore;
    
    private BigDecimal minScore;
}
