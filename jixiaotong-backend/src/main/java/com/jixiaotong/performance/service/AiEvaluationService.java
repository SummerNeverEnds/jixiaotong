package com.jixiaotong.performance.service;

public interface AiEvaluationService {
    void evaluateQualitativeIndicatorsAsync(Long reviewId);

    
    void evaluateQualitativeIndicatorsAsync(Long reviewId, boolean force);
}
