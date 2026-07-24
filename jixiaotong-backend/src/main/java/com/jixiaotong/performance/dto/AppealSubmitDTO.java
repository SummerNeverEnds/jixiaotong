package com.jixiaotong.performance.dto;

import lombok.Data;

@Data
public class AppealSubmitDTO {
    private Long reviewId;
    private Long employeeId;
    private String reason;
}
