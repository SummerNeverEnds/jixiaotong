package com.jixiaotong.performance.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jixiaotong.performance.entity.PerfTemplate;
import com.jixiaotong.performance.dto.TemplateSaveDTO;

public interface PerfTemplateService extends IService<PerfTemplate> {
    void saveTemplateWithIndicators(TemplateSaveDTO dto);

    void deleteTemplate(Long templateId);
}
