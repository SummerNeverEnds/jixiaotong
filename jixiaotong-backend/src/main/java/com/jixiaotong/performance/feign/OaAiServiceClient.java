package com.jixiaotong.performance.feign;

import com.jixiaotong.performance.dto.AiEvaluateRequest;
import com.jixiaotong.performance.dto.AiEvaluateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "oa-ai-service", path = "/api/ai", url = "${oa-ai-service.url:}")
public interface OaAiServiceClient {

    @PostMapping("/evaluate/qualitative")
    AiEvaluateResponse evaluateQualitative(@RequestBody AiEvaluateRequest request);
}
