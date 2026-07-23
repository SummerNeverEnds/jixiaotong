package com.jixiaotong.ai.dto;

import lombok.Data;

@Data
public class AiEvaluateResponse {

    private Integer code;
    private String message;
    private AiEvaluateData data;

    public static AiEvaluateResponse success(AiEvaluateData data) {
        AiEvaluateResponse response = new AiEvaluateResponse();
        response.setCode(200);
        response.setMessage("操作成功");
        response.setData(data);
        return response;
    }

    public static AiEvaluateResponse error(String message) {
        AiEvaluateResponse response = new AiEvaluateResponse();
        response.setCode(500);
        response.setMessage(message);
        return response;
    }

    @Data
    public static class AiEvaluateData {
        private String aiComment;
        private Integer suggestScore;
    }
}
