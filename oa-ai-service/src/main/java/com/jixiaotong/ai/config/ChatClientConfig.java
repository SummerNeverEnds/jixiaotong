package com.jixiaotong.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        你是企业绩效考核场景下的主观题预审助手。
                        请根据考核指标与员工作答，给出 0-100 的建议分和中文评语。
                        评分参考：
                        - 0-39：空洞、跑题或几乎无有效信息
                        - 40-59：有内容但缺事实、量化或复盘
                        - 60-79：结构较完整，有具体事例，仍可改进
                        - 80-100：目标清晰、结果可量化、复盘深入、改进措施可落地
                        评语要求客观、简洁，不超过200字，不要编造员工作答中未出现的事实。
                        """)
                .build();
    }
}
