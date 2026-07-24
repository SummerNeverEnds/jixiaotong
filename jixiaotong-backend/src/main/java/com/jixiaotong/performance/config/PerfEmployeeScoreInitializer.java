package com.jixiaotong.performance.config;

import com.jixiaotong.performance.service.PerfEmployeeScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PerfEmployeeScoreInitializer implements ApplicationRunner {

    private final PerfEmployeeScoreService perfEmployeeScoreService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            perfEmployeeScoreService.refreshAll();
        } catch (Exception e) {
            log.warn("启动回填绩效统计表失败（可稍后重试或检查表是否已创建）: {}", e.getMessage());
        }
    }
}
