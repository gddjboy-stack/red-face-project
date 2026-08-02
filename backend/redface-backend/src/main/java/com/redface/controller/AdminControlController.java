package com.redface.controller;

import com.redface.api.ApiResponse;
import com.redface.dto.AdminOperationResult;
import com.redface.dto.AdminRequests;
import com.redface.service.CoefficientService;
import com.redface.dto.DistributionResult;
import com.redface.dto.GroupVoteSummaryResponse;
import com.redface.dto.LiveHomeResponse;
import com.redface.dto.ManualSalesEntryResult;
import com.redface.dto.OrderImportPreview;
import com.redface.dto.PopularityBoardResponse;
import com.redface.dto.PopularityChangeResult;
import com.redface.dto.SimResult;
import com.redface.entity.CollectState;
import com.redface.entity.LiveMetricWatermark;
import com.redface.entity.ProductPriceConfig;
import com.redface.service.AdminControlService;
import com.redface.service.LiveMetricEntryService;
import com.redface.service.LiveWatermarkService;
import com.redface.service.ManualSalesService;
import com.redface.service.OrderImportService;
import com.redface.service.ProductPriceService;
import com.redface.util.SheetReader;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    private final OrderImportService orderImportService;
    private final ProductPriceService productPriceService;
    private final ManualSalesService manualSalesService;

    public AdminControlController(AdminControlService adminControlService,
                                  CoefficientService coefficientService,
                                  LiveMetricEntryService liveMetricEntryService,
                                  LiveWatermarkService liveWatermarkService,
                                  OrderImportService orderImportService,
                                  ProductPriceService productPriceService,
                                  ManualSalesService manualSalesService) {
        this.adminControlService = adminControlService;
        this.coefficientService = coefficientService;
        this.liveMetricEntryService = liveMetricEntryService;
        this.liveWatermarkService = liveWatermarkService;
        this.orderImportService = orderImportService;
        this.productPriceService = productPriceService;
        this.manualSalesService = manualSalesService;
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

    /**
     * C20-4B 上传订单表并预览，<b>不写库</b>。返回每位选手人气增量、
     * 有效/无效/未归属/售后中分布，以及阻塞错误与警告，供运营核对后再确认入账。
     */
    @PostMapping("/orders/preview")
    public ApiResponse<OrderImportPreview> previewOrderImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Integer roundId) {
        return ApiResponse.success(orderImportService.preview(readSheet(file), roundId));
    }

    /**
     * C20-4C 导入前置检查：只校验，<b>不生成预览令牌、不落库</b>，供赛前空跑。
     *
     * <p>与 {@code /orders/preview} 的区别仅在于不产生令牌：空跑后不会遗留一个
     * 可被误点的有效确认入口。解析与归属判定走同一代码路径，
     * 以保证「空跑通过但正式导入被拦」不会发生。
     */
    @PostMapping("/orders/preflight")
    public ApiResponse<OrderImportPreview> preflightOrderImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Integer roundId) {
        return ApiResponse.success(orderImportService.preflight(readSheet(file), roundId));
    }

    /**
     * C20-4B 凭预览令牌确认入账。令牌一次性消费，既防重复点击确认，
     * 也防「看的是 A 文件、导的是 B 文件」。
     *
     * <p>C20-4C：预览中存在未归属行时本端点返回 409/40920 硬阻断，
     * 且不消费令牌——运营补齐配置后仍可重新预览，或转而走排除入口。
     */
    @PostMapping("/orders/confirm")
    public ApiResponse<Map<String, Object>> confirmOrderImport(
            @RequestBody AdminRequests.OrderImportConfirmRequest request) {
        return ApiResponse.success(
                orderImportService.confirm(request.getPreviewToken(), request.getOperatorId()));
    }

    /**
     * C20-4C 确认入账并显式排除未归属订单。
     *
     * <p>运营需逐笔勾选子订单号并填写原因，服务层会校验勾选集合与预览未归属行
     * 完全一致，并在入账<b>之前</b>写 operations_log。前端不得默认全选。
     */
    @PostMapping("/orders/confirm-override")
    public ApiResponse<Map<String, Object>> confirmOrderImportWithOverride(
            @RequestBody AdminRequests.OrderImportOverrideRequest request) {
        return ApiResponse.success(orderImportService.confirmWithOverride(
                request.getPreviewToken(), request.getOperatorId(),
                request.getOverrideSubOrderNos(), request.getOverrideReason()));
    }

    /** C20-4B 查询商品原价配置。 */
    @GetMapping("/orders/prices")
    public ApiResponse<List<ProductPriceConfig>> listProductPrices() {
        return ApiResponse.success(productPriceService.list());
    }

    /**
     * C20-4B 新增或修改商品原价。改价只影响<b>此后导入</b>的订单，
     * 已入账订单不追溯，因此每次改价均写操作日志留痕。
     */
    @PostMapping("/orders/prices")
    public ApiResponse<ProductPriceConfig> saveProductPrice(
            @RequestBody AdminRequests.ProductPriceRequest request) {
        return ApiResponse.success(productPriceService.save(
                request.getMerchantCode(), request.getProductName(), request.getUnitPriceYuan(),
                request.getStatus(), request.getOperatorId()));
    }

    /**
     * C20-6 后台手工销量录入。正数累加，负数冲销。
     *
     * <p>返回体的 {@code status} 有三种取值，前端必须分开处理：
     * {@code recorded}（已入账）、{@code duplicated}（幂等拦截，早已入账）、
     * {@code needs_confirm}（<b>尚未入账</b>，需运营看清提示后带 confirmed=true 重提）。
     * 若前端把后两者都当成「完成」展示，duplicated 会误导运营重复录入，
     * needs_confirm 则会让本该入账的销量凭空消失。
     */
    @PostMapping("/sales/manual-entry")
    public ApiResponse<ManualSalesEntryResult> recordManualSales(
            @RequestBody AdminRequests.ManualSalesEntryRequest request) {
        return ApiResponse.success(manualSalesService.record(request));
    }

    /**
     * C20-6 本轮手工销量汇总（两级展开：外层按选手人气合计，内层按商品件数与人气）。
     */
    @GetMapping("/sales/manual-summary")
    public ApiResponse<ManualSalesService.ManualSalesSummary> getManualSalesSummary(
            @RequestParam int roundId) {
        return ApiResponse.success(manualSalesService.summarize(roundId));
    }

    /**
     * 读取上传的 CSV/Excel 为二维字符串。全部单元格按文本读取，避免子订单号
     * 这类长数字被当成数值转为科学计数法而丢掉末位。
     */
    private List<List<String>> readSheet(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的订单表文件");
        }
        String name = file.getOriginalFilename() == null
                ? "" : file.getOriginalFilename().toLowerCase();
        try {
            if (name.endsWith(".csv") || name.endsWith(".txt")) {
                return SheetReader.readCsv(file.getInputStream());
            }
            return SheetReader.readExcel(file.getInputStream());
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("文件读取失败：" + e.getMessage());
        }
    }
}
