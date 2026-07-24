package com.jixiaotong.performance.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jixiaotong.performance.entity.PerfMaterial;
import java.util.List;

public interface PerfMaterialService extends IService<PerfMaterial> {
    List<PerfMaterial> getMaterialListWithCache();

    void addMaterial(PerfMaterial material);

    void offShelfPastCycleMaterials();
}
