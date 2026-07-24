package com.jixiaotong.performance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("perf_employee_score")
public class PerfEmployeeScore implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long employeeId;

    
    private String cycleName;

    private BigDecimal learningScore;

    private BigDecimal examScore;

    private BigDecimal workScore;

    
    private BigDecimal performanceScore;

    private Integer materialTotal;

    private Integer materialCompleted;

    private Integer examCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
