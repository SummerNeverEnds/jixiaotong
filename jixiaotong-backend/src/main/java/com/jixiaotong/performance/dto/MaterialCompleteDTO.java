package com.jixiaotong.performance.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialCompleteDTO {
    private Long employeeId;
    private Long materialId;
    
    private BigDecimal watchProgress;
    
    private Integer staySeconds;
}
