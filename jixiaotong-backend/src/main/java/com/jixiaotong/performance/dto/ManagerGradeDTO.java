package com.jixiaotong.performance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ManagerGradeDTO {
    private Long reviewId;
    private String managerComment;
    private List<ScoreItem> scores;

    @Data
    public static class ScoreItem {
        private Long reviewDetailId;
        private BigDecimal managerScore;
    }
}
