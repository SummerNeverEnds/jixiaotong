package com.jixiaotong.performance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class WorkScoreUploadDTO {
    private String cycleName;
    private List<Item> items;

    @Data
    public static class Item {
        private String username;
        private BigDecimal workScore;
    }
}
