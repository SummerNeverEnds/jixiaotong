package com.jixiaotong.performance.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jixiaotong.performance.entity.PerfIndicator;
import com.jixiaotong.performance.mapper.PerfIndicatorMapper;
import com.jixiaotong.performance.service.PerfIndicatorService;
import org.springframework.stereotype.Service;

@Service
public class PerfIndicatorServiceImpl extends ServiceImpl<PerfIndicatorMapper, PerfIndicator> implements PerfIndicatorService {
}
