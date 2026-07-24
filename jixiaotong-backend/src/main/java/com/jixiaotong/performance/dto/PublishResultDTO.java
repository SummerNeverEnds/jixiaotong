package com.jixiaotong.performance.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class PublishResultDTO {
    private int createdCount;
    private int skippedCount;
    @Builder.Default
    private List<Long> skippedEmployeeIds = new ArrayList<>();
    @Builder.Default
    private List<String> skippedNames = new ArrayList<>();
}
