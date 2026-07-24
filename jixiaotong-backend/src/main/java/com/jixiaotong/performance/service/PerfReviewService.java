package com.jixiaotong.performance.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jixiaotong.performance.dto.*;
import com.jixiaotong.performance.entity.PerfReview;

import java.util.List;

public interface PerfReviewService extends IService<PerfReview> {

    PublishResultDTO generateReview(Long templateId, List<Long> employeeIds, Long managerId);

    void submitSelfEval(SelfEvalSubmitDTO submitDTO);

    void reEvaluateAi(Long reviewId);

    ReviewVO getCurrentReview(Long employeeId);

    List<ReviewVO> listEmployeeReviews(Long employeeId);

    List<ReviewVO> listEmployeeReviews(Long employeeId, String templateName, String cycleName, String sortBy, String sortOrder);

    EmployeePerformanceDTO getEmployeePerformance(Long employeeId, String cycleName);

    List<EmployeePerformanceDTO> listEmployeePerformanceHistory(Long employeeId);

    List<EmployeePerformanceDTO> listCompanyPerformance(String cycleName, String keyword, Long deptId);

    ReviewVO startReview(Long reviewId, Long employeeId);

    
    String reportCheat(Long reviewId, Long employeeId);

    void markCheated(Long reviewId, Long employeeId);

    void saveDraft(SelfEvalSubmitDTO draftDTO);

    SelfEvalSubmitDTO getDraft(Long reviewId, Long employeeId);

    void submitAppeal(AppealSubmitDTO appealDTO);

    List<ExportReviewDTO> listExportReviews(Long managerId, String cycleName);

    List<AppealVO> listPendingAppeals(Long managerId);

    AppealVO getAppealDetail(Long appealId, Long managerId);

    void reviewAppeal(AppealReviewDTO reviewDTO);

    List<ReviewVO> listPendingReviews(Long managerId);

    ReviewVO getReviewDetail(Long reviewId);

    void gradeReview(ManagerGradeDTO gradeDTO);

    DashboardSummaryDTO getDashboardSummary(Long managerId, String cycleName);
}
