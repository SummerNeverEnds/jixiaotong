package com.jixiaotong.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEvaluateRequest {

    private Long indicatorId;

    @NotBlank(message = "指标名称不能为空")
    private String indicatorName;

    @NotBlank(message = "员工作答不能为空")
    private String employeeAnswer;
}
