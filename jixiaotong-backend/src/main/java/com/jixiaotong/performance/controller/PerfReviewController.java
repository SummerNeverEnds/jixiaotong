package com.jixiaotong.performance.controller;

import com.jixiaotong.performance.common.PageResult;
import com.jixiaotong.performance.common.Result;
import com.jixiaotong.performance.dto.AppealReviewDTO;
import com.jixiaotong.performance.dto.AppealSubmitDTO;
import com.jixiaotong.performance.dto.AppealVO;
import com.jixiaotong.performance.dto.DashboardStatDTO;
import com.jixiaotong.performance.dto.DashboardSummaryDTO;
import com.jixiaotong.performance.dto.EmployeePerformanceDTO;
import com.jixiaotong.performance.dto.ExportReviewDTO;
import com.jixiaotong.performance.dto.ManagerGradeDTO;
import com.jixiaotong.performance.dto.PublishResultDTO;
import com.jixiaotong.performance.dto.ReviewVO;
import com.jixiaotong.performance.dto.SelfEvalSubmitDTO;
import com.jixiaotong.performance.dto.WorkScoreUploadDTO;
import com.jixiaotong.performance.mapper.DashboardMapper;
import com.jixiaotong.performance.security.AuthSupport;
import com.jixiaotong.performance.service.PerfEmployeeScoreService;
import com.jixiaotong.performance.service.PerfReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class PerfReviewController {

    private final PerfReviewService perfReviewService;
    private final PerfEmployeeScoreService perfEmployeeScoreService;
    private final DashboardMapper dashboardMapper;

    @PostMapping("/generate")
    public Result<PublishResultDTO> generateReview(@RequestParam Long templateId,
                                                   @RequestBody List<Long> employeeIds) {
        AuthSupport.requireRole("MANAGER");
        Long managerId = AuthSupport.requireLoginUserId();
        return Result.success(perfReviewService.generateReview(templateId, employeeIds, managerId));
    }

    @PostMapping("/submit")
    public Result<Void> submitSelfEval(@RequestBody SelfEvalSubmitDTO submitDTO) {
        AuthSupport.requireRole("EMPLOYEE");
        AuthSupport.requireSelf(submitDTO.getEmployeeId());
        perfReviewService.submitSelfEval(submitDTO);
        return Result.success();
    }

    @PostMapping("/{reviewId}/ai-evaluate")
    public Result<Void> reEvaluateAi(@PathVariable Long reviewId) {
        AuthSupport.requireRole("MANAGER", "ADMIN");
        perfReviewService.reEvaluateAi(reviewId);
        return Result.success();
    }

    @PostMapping("/draft")
    public Result<Void> saveDraft(@RequestBody SelfEvalSubmitDTO draftDTO) {
        AuthSupport.requireRole("EMPLOYEE");
        AuthSupport.requireSelf(draftDTO.getEmployeeId());
        perfReviewService.saveDraft(draftDTO);
        return Result.success();
    }

    @GetMapping("/draft")
    public Result<SelfEvalSubmitDTO> getDraft(@RequestParam Long reviewId, @RequestParam Long employeeId) {
        AuthSupport.requireRole("EMPLOYEE");
        AuthSupport.requireSelf(employeeId);
        return Result.success(perfReviewService.getDraft(reviewId, employeeId));
    }

    @GetMapping("/current")
    public Result<ReviewVO> getCurrentReview(@RequestParam Long employeeId) {
        AuthSupport.requireSelfOrAdmin(employeeId);
        return Result.success(perfReviewService.getCurrentReview(employeeId));
    }

    @GetMapping("/my")
    public Result<List<ReviewVO>> getMyReviews(@RequestParam Long employeeId,
                                               @RequestParam(required = false) String templateName,
                                               @RequestParam(required = false) String cycleName,
                                               @RequestParam(required = false) String sortBy,
                                               @RequestParam(required = false) String sortOrder) {
        AuthSupport.requireSelfOrAdmin(employeeId);
        return Result.success(perfReviewService.listEmployeeReviews(employeeId, templateName, cycleName, sortBy, sortOrder));
    }

    @GetMapping("/my/page")
    public Result<PageResult<ReviewVO>> getMyReviewsPage(@RequestParam Long employeeId,
                                                         @RequestParam(defaultValue = "1") Integer current,
                                                         @RequestParam(defaultValue = "10") Integer size,
                                                         @RequestParam(required = false) String templateName,
                                                         @RequestParam(required = false) String cycleName,
                                                         @RequestParam(required = false) String sortBy,
                                                         @RequestParam(required = false) String sortOrder) {
        AuthSupport.requireSelfOrAdmin(employeeId);
        return Result.success(PageResult.of(
                perfReviewService.listEmployeeReviews(employeeId, templateName, cycleName, sortBy, sortOrder),
                current, size));
    }

    @GetMapping("/performance")
    public Result<EmployeePerformanceDTO> getEmployeePerformance(@RequestParam Long employeeId,
                                                                 @RequestParam(required = false) String cycleName) {
        AuthSupport.requireSelfOrAdmin(employeeId);
        return Result.success(perfReviewService.getEmployeePerformance(employeeId, cycleName));
    }

    @GetMapping("/performance/history")
    public Result<List<EmployeePerformanceDTO>> getEmployeePerformanceHistory(@RequestParam Long employeeId) {
        AuthSupport.requireSelfOrAdmin(employeeId);
        return Result.success(perfReviewService.listEmployeePerformanceHistory(employeeId));
    }

    @GetMapping("/performance/history/page")
    public Result<PageResult<EmployeePerformanceDTO>> getEmployeePerformanceHistoryPage(
            @RequestParam Long employeeId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        AuthSupport.requireSelfOrAdmin(employeeId);
        return Result.success(PageResult.of(
                perfReviewService.listEmployeePerformanceHistory(employeeId), current, size));
    }

    @GetMapping("/performance/company/page")
    public Result<PageResult<EmployeePerformanceDTO>> companyPerformancePage(
            @RequestParam(required = false) String cycleName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        AuthSupport.requireRole("ADMIN");
        return Result.success(PageResult.of(
                perfReviewService.listCompanyPerformance(cycleName, keyword, deptId), current, size));
    }

    @GetMapping("/performance/company/export")
    public Result<List<EmployeePerformanceDTO>> companyPerformanceExport(
            @RequestParam(required = false) String cycleName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId) {
        AuthSupport.requireRole("ADMIN");
        return Result.success(perfReviewService.listCompanyPerformance(cycleName, keyword, deptId));
    }

    @PostMapping("/{reviewId}/start")
    public Result<ReviewVO> startReview(@PathVariable Long reviewId, @RequestParam Long employeeId) {
        AuthSupport.requireRole("EMPLOYEE");
        AuthSupport.requireSelf(employeeId);
        return Result.success(perfReviewService.startReview(reviewId, employeeId));
    }

    @PostMapping("/{reviewId}/cheat")
    public Result<String> reportCheat(@PathVariable Long reviewId, @RequestParam Long employeeId) {
        AuthSupport.requireRole("EMPLOYEE");
        AuthSupport.requireSelf(employeeId);
        return Result.success(perfReviewService.reportCheat(reviewId, employeeId));
    }

    @PostMapping("/appeal")
    public Result<Void> submitAppeal(@RequestBody AppealSubmitDTO appealDTO) {
        AuthSupport.requireRole("EMPLOYEE");
        AuthSupport.requireSelf(appealDTO.getEmployeeId());
        perfReviewService.submitAppeal(appealDTO);
        return Result.success();
    }

    @GetMapping("/appeals/pending")
    public Result<List<AppealVO>> listPendingAppeals(@RequestParam(required = false) Long managerId) {
        Long scope = AuthSupport.resolveManagerScope(managerId);
        return Result.success(perfReviewService.listPendingAppeals(scope));
    }

    @GetMapping("/appeals/pending/page")
    public Result<PageResult<AppealVO>> listPendingAppealsPage(@RequestParam(required = false) Long managerId,
                                                               @RequestParam(defaultValue = "1") Integer current,
                                                               @RequestParam(defaultValue = "10") Integer size) {
        Long scope = AuthSupport.resolveManagerScope(managerId);
        return Result.success(PageResult.of(perfReviewService.listPendingAppeals(scope), current, size));
    }

    @GetMapping("/appeals/{appealId}")
    public Result<AppealVO> getAppealDetail(@PathVariable Long appealId,
                                            @RequestParam(required = false) Long managerId) {
        Long scope = AuthSupport.resolveManagerScope(managerId);
        return Result.success(perfReviewService.getAppealDetail(appealId, scope));
    }

    @PostMapping("/appeals/review")
    public Result<Void> reviewAppeal(@RequestBody AppealReviewDTO reviewDTO) {
        Long managerId = AuthSupport.requireActingManagerId(reviewDTO.getManagerId());
        reviewDTO.setManagerId(managerId);
        perfReviewService.reviewAppeal(reviewDTO);
        return Result.success();
    }

    @GetMapping("/pending")
    public Result<List<ReviewVO>> getPendingReviews(@RequestParam(required = false) Long managerId) {
        Long scope = AuthSupport.resolveManagerScope(managerId);
        return Result.success(perfReviewService.listPendingReviews(scope));
    }

    @GetMapping("/{reviewId}")
    public Result<ReviewVO> getReviewDetail(@PathVariable Long reviewId) {
        AuthSupport.requireLoginUserId();
        return Result.success(perfReviewService.getReviewDetail(reviewId));
    }

    @PostMapping("/grade")
    public Result<Void> gradeReview(@RequestBody ManagerGradeDTO gradeDTO) {
        AuthSupport.requireRole("MANAGER", "ADMIN");
        perfReviewService.gradeReview(gradeDTO);
        return Result.success();
    }

    @GetMapping("/dashboard")
    public Result<List<DashboardStatDTO>> getDashboardStats(@RequestParam(required = false) String cycleName) {
        AuthSupport.requireRole("MANAGER", "ADMIN");
        List<DashboardStatDTO> stats = dashboardMapper.getDashboardStats(cycleName);
        return Result.success(stats);
    }

    @GetMapping("/dashboard/summary")
    public Result<DashboardSummaryDTO> getDashboardSummary(@RequestParam(required = false) Long managerId,
                                                           @RequestParam(required = false) String cycleName) {
        Long scope = AuthSupport.resolveManagerScope(managerId);
        return Result.success(perfReviewService.getDashboardSummary(scope, cycleName));
    }

    @GetMapping("/export")
    public Result<List<ExportReviewDTO>> exportReviews(@RequestParam(required = false) Long managerId,
                                                       @RequestParam(required = false) String cycleName) {
        Long scope = AuthSupport.resolveManagerScope(managerId);
        return Result.success(perfReviewService.listExportReviews(scope, cycleName));
    }

    @PostMapping("/work-score/upload")
    public Result<Map<String, Object>> uploadWorkScore(@RequestBody WorkScoreUploadDTO dto) {
        AuthSupport.requireRole("MANAGER", "ADMIN");
        String cycle = dto == null ? null : dto.getCycleName();
        int updated = perfEmployeeScoreService.importWorkScores(cycle, dto == null ? null : dto.getItems());
        Map<String, Object> data = new HashMap<>();
        data.put("updated", updated);
        data.put("cycleName", cycle == null || cycle.isBlank()
                ? perfEmployeeScoreService.currentCycleName()
                : cycle.trim());
        return Result.success(data);
    }
}
