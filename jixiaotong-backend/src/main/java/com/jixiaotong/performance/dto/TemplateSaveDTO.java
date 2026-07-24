package com.jixiaotong.performance.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TemplateSaveDTO {
    private Long id;
    private String name;
    private String cycleName;
    private Integer durationMinutes;
    private LocalDateTime deadline;
    private Long managerId;
    private String status;

    private Integer objectiveCount;
    private Integer subjectiveCount;
    
    private List<Long> indicatorIds;
}
