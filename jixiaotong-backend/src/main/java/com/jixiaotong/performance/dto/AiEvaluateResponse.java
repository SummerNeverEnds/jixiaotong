package com.jixiaotong.performance.dto;

import lombok.Data;

@Data
public class AiEvaluateResponse {
    private Integer code;
    private String message;
    private AiEvaluateData data;

    @Data
    public static class AiEvaluateData {
        private String aiComment;
        private Integer suggestScore;
    }
}
