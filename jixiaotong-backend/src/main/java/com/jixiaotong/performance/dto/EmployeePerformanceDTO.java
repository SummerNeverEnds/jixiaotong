package com.jixiaotong.performance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class EmployeePerformanceDTO {
    private Long employeeId;
    private String username;
    private String employeeName;
    private Long deptId;
    private String deptName;
    private String jobLevel;
    private String cycleName;
    private BigDecimal learningScore;
    private BigDecimal examScore;
    private BigDecimal workScore;
    private BigDecimal performanceScore;
    private Integer materialTotal;
    private Integer materialCompleted;
    private Integer examCount;
}
