package com.jixiaotong.performance.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AppealVO {
    private Long appealId;
    private Long reviewId;
    private Integer appealNo;
    private String reason;
    private String status;
    private Long reviewerManagerId;
    private String reviewOpinion;
    private LocalDateTime createTime;
    private LocalDateTime reviewTime;
    private ReviewVO review;
}
