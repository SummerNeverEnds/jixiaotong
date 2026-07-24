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
@TableName("perf_review")
public class PerfReview implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long employeeId;
    
    private Long templateId;
    
    
    private String status;
    
    private BigDecimal objectiveScore;
    
    private BigDecimal subjectiveScore;
    
    
    private BigDecimal totalScore;
    
    private LocalDateTime startTime;

    private LocalDateTime submitTime;
    
    private Long managerId;
    
    private String managerComment;

    
    private Integer appealCount;

    
    private LocalDateTime appealDeadline;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Boolean isDeleted;
}
