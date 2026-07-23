package com.jixiaotong.ai.controller;

import com.jixiaotong.ai.dto.AiEvaluateRequest;
import com.jixiaotong.ai.dto.AiEvaluateResponse;
import com.jixiaotong.ai.service.QualitativeEvaluateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiEvaluateController {

    private final QualitativeEvaluateService qualitativeEvaluateService;

    @PostMapping("/evaluate/qualitative")
    public AiEvaluateResponse evaluateQualitative(@Valid @RequestBody AiEvaluateRequest request) {
        log.info("收到主观题预审请求, indicatorId={}, name={}",
                request.getIndicatorId(), request.getIndicatorName());
        try {
            AiEvaluateResponse.AiEvaluateData data = qualitativeEvaluateService.evaluate(request);
            return AiEvaluateResponse.success(data);
        } catch (Exception ex) {
            log.error("主观题预审失败, indicatorId={}", request.getIndicatorId(), ex);
            return AiEvaluateResponse.error("AI 预审失败: " + ex.getMessage());
        }
    }
}
