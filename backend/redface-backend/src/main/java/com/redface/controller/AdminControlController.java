package com.redface.controller;

import com.redface.api.ApiResponse;
import com.redface.dto.AdminOperationResult;
import com.redface.dto.AdminRequests;
import com.redface.service.CoefficientService;
import com.redface.dto.DistributionResult;
import com.redface.dto.GroupVoteSummaryResponse;
import com.redface.dto.LiveHomeResponse;
import com.redface.dto.PopularityBoardResponse;
import com.redface.dto.PopularityChangeResult;
import com.redface.dto.SimResult;
import com.redface.entity.CollectState;
import com.redface.entity.LiveMetricWatermark;
import com.redface.service.AdminControlService;
import com.redface.service.LiveMetricEntryService;
import com.redface.service.LiveWatermarkService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C10 场控后台 Admin API。Controller 只做 HTTP 适配，业务逻辑全部委托 Service。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminControlController {
    private final AdminControlService adminControlService;
    private final CoefficientService coefficientService;
    private final LiveMetricEntryService liveMetricEntryService;
    private final LiveWatermarkService liveWatermarkService;

    public AdminControlController(AdminControlService adminControlService,
                                  CoefficientService coefficientService,
                                  LiveMetricEntryService liveMetricEntryService,
                                  LiveWatermarkService liveWatermarkService) {
        this.adminControlService = adminControlService;
        this.coefficientService = coefficientService;
        this.liveMetricEntryService = liveMetricEntryService;
        this.liveWatermarkService = liveWatermarkService;
    }

    @GetMapping("/live/home")
    public ApiResponse<LiveHomeResponse> getLiveHome() {
        return ApiResponse.success(adminControlService.getLiveHome());
    }

    @GetMapping("/board")
    public ApiResponse<PopularityBoardResponse> getBoard(@RequestParam(defaultValue = "player") String tab,
                                                         @RequestParam int roundId) {
        return ApiResponse.success(adminControlService.getBoard(tab, roundId));
    }

    @GetMapping("/collect-state")
    public ApiResponse<CollectState> getCollectState() {
        return ApiResponse.success(adminControlService.getCollectState());
    }

    @PostMapping("/collect-state")
    public ApiResponse<AdminOperationResult<Void>> setCollectTarget(@RequestBody AdminRequests.CollectStateRequest request) {
        return ApiResponse.success(adminControlService.setCollectTarget(request));
    }

    @PostMapping("/live/simulate")
    public ApiResponse<AdminOperationResult<SimResult>> simulateInject(@RequestBody AdminRequests.SimulateInjectRequest request) {
        return ApiResponse.success(adminControlService.simulateInject(request));
    }

    @PostMapping("/adjust-coefficient")
    public ApiResponse<Void> manualBonus(@RequestBody AdminRequests.ManualBonusRequest request) {
        if ("player".equals(request.getTargetType())) {
            coefficientService.manualAdjustPlayer(request.getTargetId(), request.getRoundId(), request.getDelta(), request.getIdempotencyKey(), request.getOperatorId(), request.getReason());
        } else if ("team".equals(request.getTargetType())) {
            coefficientService.manualAdjustTeam(request.getTargetId(), request.getRoundId(), request.getDelta(), request.getIdempotencyKey(), request.getOperatorId(), request.getReason());
        } else {
            return ApiResponse.error(400, "不支持的 targetType", null);
        }
        return ApiResponse.success(null);
    }

    @PostMapping("/popularity/manual-adjust")
    public ApiResponse<AdminOperationResult<PopularityChangeResult>> manualAdjust(@RequestBody AdminRequests.ManualAdjustRequest request) {
        return ApiResponse.success(adminControlService.manualAdjust(request));
    }

    @PostMapping("/team-distribution")
    public ApiResponse<AdminOperationResult<DistributionResult>> distributeTeam(@RequestBody AdminRequests.TeamDistributionRequest request) {
        return ApiResponse.success(adminControlService.distributeTeam(request));
    }

    /**
     * C20-3 群投票结果录入（增量累加，负数冲销，幂等防连点）。
     */
    @PostMapping("/group-vote/entry")
    public ApiResponse<AdminOperationResult<AdminControlService.GroupVoteEntryOutcome>> recordGroupVote(
            @RequestBody AdminRequests.GroupVoteEntryRequest request) {
        return ApiResponse.success(adminControlService.recordGroupVote(request));
    }

    /**
     * C20-3 查询指定轮次各选手群投票累计票数。
     */
    @GetMapping("/group-vote/summary")
    public ApiResponse<GroupVoteSummaryResponse> getGroupVoteSummary(@RequestParam int roundId) {
        return ApiResponse.success(adminControlService.getGroupVoteSummary(roundId));
    }

    /**
     * C20-4A 查询三条数据来源的水位线现状，供后台展示「上次录入总数」以便运营核对。
     */
    @GetMapping("/live/watermarks")
    public ApiResponse<List<LiveMetricWatermark>> listWatermarks() {
        return ApiResponse.success(liveWatermarkService.listAll());
    }

    /**
     * C20-4A 下发校准操作的官方文案（按钮名、二次确认语、成功提示）。
     *
     * <p><b>为何把文案做成接口而不交给前端硬编码</b>：这几段文字是防误操作措施，
     * 不是普通界面文案。若交由各端自行书写，很可能有人为了按钮宽度好看而改回
     * 「清零」二字，风险静默复活且无人察觉；集中下发则使其成为可测试的服务端约束。
     */
    @GetMapping("/live/watermarks/calibrate-copy")
    public ApiResponse<Map<String, String>> getCalibrationCopy() {
        return ApiResponse.success(Map.of(
                "actionLabel", LiveWatermarkService.CALIBRATION_ACTION_LABEL,
                "confirmMessage", LiveWatermarkService.CALIBRATION_CONFIRM_MESSAGE,
                "successMessage", LiveWatermarkService.CALIBRATION_SUCCESS_MESSAGE,
                "revokeSuccessMessage", LiveWatermarkService.REVOKE_SUCCESS_MESSAGE));
    }

    /**
     * C20-4A 预演一次录入，返回将要入账的增量或「需先校准」信号。不写入任何数据。
     */
    @GetMapping("/live/metric-entry/preview")
    public ApiResponse<LiveWatermarkService.EntryPreview> previewMetricEntry(
            @RequestParam String metricType,
            @RequestParam long currentTotal) {
        return ApiResponse.success(liveMetricEntryService.preview(metricType, currentTotal));
    }

    /**
     * C20-4A 按「中控台当前累计总数」录入互动数据。系统减去水位线得到增量后入账。
     *
     * <p>当前总数小于水位线时不写入任何数据，返回 40910 要求前端确认是否为新场次开播。
     */
    @PostMapping("/live/metric-entry")
    public ApiResponse<LiveMetricEntryService.EntryOutcome> submitMetricEntry(
            @RequestBody AdminRequests.LiveMetricEntryRequest request) {
        if (request.getCurrentTotal() == null) {
            throw new IllegalArgumentException("currentTotal不能为空");
        }
        return ApiResponse.success(liveMetricEntryService.submit(
                request.getMetricType(), request.getCurrentTotal(), request.getOperatorId(),
                request.getIdempotencyKey(), request.getReason()));
    }

    /**
     * C20-4A 校准全部来源水位线，用于新一场直播开播。
     *
     * <p>本操作只重置中控台读数基准，<b>不会改变任何选手的人气值</b>。前端二次确认
     * 文案必须写明这一点，否则运营可能把它理解为「分数清零」，在发现分数没变时
     * 误判系统故障并手动调分「修正」，造成系统层面无法拦截的数据污染。
     */
    @PostMapping("/live/watermarks/calibrate")
    public ApiResponse<LiveWatermarkService.CalibrationResult> calibrateWatermarks(
            @RequestBody AdminRequests.WatermarkCalibrateRequest request) {
        return ApiResponse.success(
                liveWatermarkService.calibrate(request.getOperatorId(), request.getReason()));
    }

    /**
     * C20-4A 撤销最近一次校准。用于误点校准的挽回——误点的后果是「多加」而非倒扣，
     * 负值兜底对该方向完全无效，因此必须提供撤销。仅在校准后尚未录入时可用。
     */
    @PostMapping("/live/watermarks/revoke-calibration")
    public ApiResponse<LiveWatermarkService.RevokeResult> revokeCalibration(
            @RequestBody AdminRequests.WatermarkCalibrateRequest request) {
        return ApiResponse.success(
                liveWatermarkService.revokeCalibration(request.getOperatorId(), request.getReason()));
    }
}
