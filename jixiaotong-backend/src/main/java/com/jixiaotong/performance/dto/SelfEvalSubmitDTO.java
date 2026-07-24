package com.jixiaotong.performance.dto;

import lombok.Data;
import java.util.List;

@Data
public class SelfEvalSubmitDTO {
    
    private Long employeeId;
    
    private Long templateId;
    
    private Long reviewId;
    
    
    private List<EvalDetailItem> detailItems;

    @Data
    public static class EvalDetailItem {
        private Long indicatorId;
        private Long reviewDetailId;
        private String answer;
    }
}
