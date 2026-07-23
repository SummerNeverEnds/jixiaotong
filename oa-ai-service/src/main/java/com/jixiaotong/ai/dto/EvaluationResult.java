package com.jixiaotong.ai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class EvaluationResult {

    @JsonPropertyDescription("中文评语，客观指出优点与不足，不超过200字")
    private String aiComment;

    @JsonPropertyDescription("建议得分，整数，范围 0 到 100")
    private Integer suggestScore;
}
