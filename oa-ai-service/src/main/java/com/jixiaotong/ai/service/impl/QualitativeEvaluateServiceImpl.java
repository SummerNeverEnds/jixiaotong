package com.jixiaotong.ai.service.impl;

import com.jixiaotong.ai.dto.AiEvaluateRequest;
import com.jixiaotong.ai.dto.AiEvaluateResponse;
import com.jixiaotong.ai.dto.EvaluationResult;
import com.jixiaotong.ai.service.QualitativeEvaluateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualitativeEvaluateServiceImpl implements QualitativeEvaluateService {

    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+(\\.\\d+)?%?");
    private static final Pattern KEYWORD_PATTERN = Pattern.compile(
            "目标|完成|达成|改进|复盘|风险|协作|指标|上线|交付|客户|优化|提升|问题|方案|结果");

    private final ChatClient chatClient;

    @Value("${ai.evaluate.fallback-enabled:true}")
    private boolean fallbackEnabled;

    @Value("${ai.evaluate.comment-max-length:480}")
    private int commentMaxLength;

    @Override
    public AiEvaluateResponse.AiEvaluateData evaluate(AiEvaluateRequest request) {
        try {
            EvaluationResult result = chatClient.prompt()
                    .user(u -> u.text("""
                                    请对以下主观题作答进行预审评分，只返回结构化结果。
                                    考核指标：{indicatorName}
                                    员工作答：
                                    {employeeAnswer}
                                    """)
                            .param("indicatorName", request.getIndicatorName())
                            .param("employeeAnswer", request.getEmployeeAnswer()))
                    .call()
                    .entity(EvaluationResult.class);

            return toData(result);
        } catch (Exception ex) {
            log.warn("调用 Ollama/Spring AI 失败, indicatorId={}, reason={}",
                    request.getIndicatorId(), ex.getMessage());
            if (!fallbackEnabled) {
                throw ex;
            }
            return heuristicEvaluate(request);
        }
    }

    private AiEvaluateResponse.AiEvaluateData heuristicEvaluate(AiEvaluateRequest request) {
        String answer = request.getEmployeeAnswer() == null ? "" : request.getEmployeeAnswer().trim();
        int length = answer.length();
        int score = 45;

        if (length >= 30) {
            score += 10;
        }
        if (length >= 80) {
            score += 10;
        }
        if (length >= 160) {
            score += 8;
        }
        if (DIGIT_PATTERN.matcher(answer).find()) {
            score += 8;
        }
        long keywordHits = KEYWORD_PATTERN.matcher(answer).results().count();
        score += (int) Math.min(12, keywordHits * 3);

        score = Math.max(40, Math.min(85, score));

        String comment = String.format(
                "【规则降级预审】针对「%s」：作答长度约 %d 字。%s建议分 %d。Ollama 未就绪时使用本地启发式评分，请经理结合实际表现复核。",
                abbreviate(request.getIndicatorName(), 40),
                length,
                buildHeuristicHint(length, DIGIT_PATTERN.matcher(answer).find(), keywordHits),
                score);

        AiEvaluateResponse.AiEvaluateData data = new AiEvaluateResponse.AiEvaluateData();
        data.setSuggestScore(score);
        data.setAiComment(truncate(comment, commentMaxLength));
        log.info("启用启发式降级评分, indicatorId={}, suggestScore={}", request.getIndicatorId(), score);
        return data;
    }

    private String buildHeuristicHint(int length, boolean hasDigit, long keywordHits) {
        StringBuilder sb = new StringBuilder();
        if (length < 50) {
            sb.append("内容偏短，建议补充具体事例与结果；");
        } else {
            sb.append("内容具备一定完整性；");
        }
        if (!hasDigit) {
            sb.append("缺少量化数据；");
        } else {
            sb.append("含有量化信息；");
        }
        if (keywordHits < 2) {
            sb.append("绩效关键词覆盖不足。");
        } else {
            sb.append("围绕目标/结果/改进有所展开。");
        }
        return sb.toString();
    }

    private AiEvaluateResponse.AiEvaluateData toData(EvaluationResult result) {
        AiEvaluateResponse.AiEvaluateData data = new AiEvaluateResponse.AiEvaluateData();
        int score = result == null || result.getSuggestScore() == null
                ? 60
                : Math.max(0, Math.min(100, result.getSuggestScore()));
        String comment = result == null || !StringUtils.hasText(result.getAiComment())
                ? "AI 已完成预审，建议结合实际表现复核。"
                : result.getAiComment().trim();
        data.setSuggestScore(score);
        data.setAiComment(truncate(comment, commentMaxLength));
        return data;
    }

    private String truncate(String text, int maxLen) {
        if (!StringUtils.hasText(text) || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen - 1) + "…";
    }

    private String abbreviate(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 1) + "…";
    }
}
