package com.jixiaotong.performance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AppealReviewDTO {
    private Long appealId;
    private Long managerId;
    private String reviewOpinion;
    private List<ScoreItem> scores;

    @Data
    public static class ScoreItem {
        private Long reviewDetailId;
        private BigDecimal managerScore;
    }
}
