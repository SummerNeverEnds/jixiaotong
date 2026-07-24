package com.jixiaotong.performance.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jixiaotong.performance.dto.EmployeePerformanceDTO;
import com.jixiaotong.performance.dto.WorkScoreUploadDTO;
import com.jixiaotong.performance.entity.PerfEmployeeScore;

import java.time.LocalDateTime;
import java.util.List;

public interface PerfEmployeeScoreService extends IService<PerfEmployeeScore> {

    void refresh(Long employeeId, String cycleName);

    void refreshByReviewId(Long reviewId);

    void refreshCycle(String cycleName);

    String cycleNameOf(LocalDateTime time);

    String currentCycleName();

    void refreshAll();

    EmployeePerformanceDTO getEmployeePerformance(Long employeeId, String cycleName);

    List<EmployeePerformanceDTO> listEmployeePerformanceHistory(Long employeeId);

    List<EmployeePerformanceDTO> listCompanyPerformance(String cycleName, String keyword, Long deptId);

    int importWorkScores(String cycleName, List<WorkScoreUploadDTO.Item> items);
}
