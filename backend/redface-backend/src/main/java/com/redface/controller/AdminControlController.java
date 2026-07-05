package com.redface.controller;

import com.redface.api.ApiResponse;
import com.redface.dto.AdminOperationResult;
import com.redface.dto.AdminRequests;
import com.redface.service.CoefficientService;
import com.redface.dto.DistributionResult;
import com.redface.dto.LiveHomeResponse;
import com.redface.dto.PopularityBoardResponse;
import com.redface.dto.PopularityChangeResult;
import com.redface.dto.SimResult;
import com.redface.entity.CollectState;
import com.redface.service.AdminControlService;
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

    public AdminControlController(AdminControlService adminControlService, CoefficientService coefficientService) {
        this.adminControlService = adminControlService;
        this.coefficientService = coefficientService;
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
}
