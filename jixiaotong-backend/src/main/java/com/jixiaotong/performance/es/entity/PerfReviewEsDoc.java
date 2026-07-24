package com.jixiaotong.performance.es.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@Document(indexName = "perf_review_archive")
public class PerfReviewEsDoc {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long reviewId;

    @Field(type = FieldType.Long)
    private Long employeeId;

    @Field(type = FieldType.Keyword)
    private String employeeName;

    @Field(type = FieldType.Keyword)
    private String cycleName;

    @Field(type = FieldType.Double)
    private BigDecimal totalScore;

    
    @Field(type = FieldType.Text)
    private String employeeAnswers;

    @Field(type = FieldType.Text)
    private String aiComments;

    
    @Field(type = FieldType.Text)
    private String managerComment;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime archiveTime;
}
