package com.jixiaotong.ai.service;

import com.jixiaotong.ai.dto.AiEvaluateRequest;
import com.jixiaotong.ai.dto.AiEvaluateResponse;

public interface QualitativeEvaluateService {

    AiEvaluateResponse.AiEvaluateData evaluate(AiEvaluateRequest request);
}
