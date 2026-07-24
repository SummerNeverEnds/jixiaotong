package com.jixiaotong.performance.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEvaluateRequest {
    private Long indicatorId;
    private String indicatorName;
    private String employeeAnswer;
}
