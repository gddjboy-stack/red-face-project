package com.redface.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redface.dto.OrderImportPreview;
import com.redface.dto.OrderRowParseResult;
import com.redface.dto.PopularityChangeRequest;
import com.redface.dto.PopularityChangeResult;
import com.redface.entity.ProductPriceConfig;
import com.redface.mapper.OrderSalesLedgerMapper;
import com.redface.mapper.ProductPriceConfigMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单表批量导入（C20-4B）。
 *
 * <p>两阶段：先 {@link #preview} 解析并汇总供运营核对，再 {@link #confirm} 落库入账。
 * 两个阶段共用同一套解析与判定逻辑，只在「是否写库」上分叉。
 *
 * <p>幂等策略是<b>两层</b>的：
 * <ol>
 *   <li>order_sales_ledger.sub_order_no 唯一约束——防同一子订单重复入账</li>
 *   <li>popularity_ledger.idempotency_key = "order:{subOrderNo}"——防绕过本服务的重复写入</li>
 * </ol>
 * 两层都必须有：只靠第一层，若将来新增别的订单入账路径就会绕开；只靠第二层，
 * 订单明细表会积累重复行导致对账数字虚高。
 */
@Service
public class OrderImportService {

    private static final DateTimeFormatter BATCH_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderSheetParser parser;
    private final OrderSalesLedgerMapper ledgerMapper;
    private final ProductPriceConfigMapper priceMapper;
    private final PopularityService popularityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * 自身代理。Spring 的 @Transactional 通过代理生效，类内部直接调用 this.persistRow()
     * 不经过代理，REQUIRES_NEW 会静默失效——单行失败会污染整批事务而不是只回滚该行。
     * 这类问题编译与单测都不报错，只在真实回滚场景暴露，故必须显式绕回代理。
     */
    private final ObjectProvider<OrderImportService> selfProvider;

    /** 预览缓存：token → 已解析行。确认时必须凭 token 取回，避免重复解析导致结果漂移 */
    private final Map<String, PreviewCache> previewCache = new LinkedHashMap<>();

    public OrderImportService(OrderSheetParser parser,
                              OrderSalesLedgerMapper ledgerMapper,
                              ProductPriceConfigMapper priceMapper,
                              PopularityService popularityService,
                              ObjectProvider<OrderImportService> selfProvider) {
        this.selfProvider = selfProvider;
        this.parser = parser;
        this.ledgerMapper = ledgerMapper;
        this.priceMapper = priceMapper;
        this.popularityService = popularityService;
    }

    /**
     * 解析并预览，不写库。
     *
     * @param rows    含表头的全部行，第 0 行为表头
     * @param roundId 入账轮次
     * @return 预览结果，含 previewToken
     */
    public OrderImportPreview preview(List<List<String>> rows, Integer roundId) {
        OrderImportPreview preview = new OrderImportPreview();
        if (rows == null || rows.size() < 2) {
            preview.getBlockingErrors().add("文件内容为空或只有表头，无可导入数据");
            return preview;
        }
        List<String> headerRow = rows.get(0);
        Map<String, Integer> columnMap;
        try {
            columnMap = parser.buildColumnMap(headerRow);
        } catch (IllegalArgumentException e) {
            preview.getBlockingErrors().add(e.getMessage());
            return preview;
        }

        Map<String, Long> byPlayer = new LinkedHashMap<>();
        List<OrderRowParseResult> parsed = new ArrayList<>();
        // 文件内自查重：同一文件里出现重复子订单号，说明导出时勾选了重复维度，须提示而非静默去重
        Map<String, Integer> seenSubOrder = new HashMap<>();

        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isEmptyRow(row)) {
                continue;
            }
            OrderRowParseResult r = parser.parseRow(i + 1, row, columnMap, headerRow);

            // 归属与单价：解析器不查库，在此注入
            attributeAndPrice(r, preview);

            // 文件内重复
            if (r.getSubOrderNo() != null && !r.getSubOrderNo().isEmpty()) {
                Integer firstRow = seenSubOrder.putIfAbsent(r.getSubOrderNo(), r.getRowNumber());
                if (firstRow != null) {
                    r.setValidity(OrderRowParseResult.VALIDITY_INVALID);
                    r.setInvalidReason("子订单号与第 " + firstRow + " 行重复，本行不计入");
                    r.setPopularityValue(0L);
                }
            }
            // 已入账查重
            if (OrderRowParseResult.VALIDITY_VALID.equals(r.getValidity())
                    && ledgerMapper.countBySubOrderNo(r.getSubOrderNo()) > 0) {
                preview.setDuplicateRows(preview.getDuplicateRows() + 1);
                r.setValidity(OrderRowParseResult.VALIDITY_INVALID);
                r.setInvalidReason("该子订单已于此前批次入账，本次跳过");
                r.setPopularityValue(0L);
            }

            String warn = parser.crossCheckActualSales(row, columnMap, r);
            if (warn != null) {
                preview.getWarnings().add(warn);
            }

            switch (r.getValidity()) {
                case OrderRowParseResult.VALIDITY_VALID -> {
                    preview.setValidRows(preview.getValidRows() + 1);
                    preview.setTotalPopularity(preview.getTotalPopularity() + r.getPopularityValue());
                    byPlayer.merge(r.getMerchantCode(), r.getPopularityValue(), Long::sum);
                    if (r.isInAftersale()) {
                        preview.setAftersaleRows(preview.getAftersaleRows() + 1);
                        preview.setAftersaleExposure(preview.getAftersaleExposure() + r.getPopularityValue());
                    }
                }
                case OrderRowParseResult.VALIDITY_UNATTRIBUTED ->
                        preview.setUnattributedRows(preview.getUnattributedRows() + 1);
                default -> preview.setInvalidRows(preview.getInvalidRows() + 1);
            }
            parsed.add(r);
        }

        preview.setTotalRows(parsed.size());
        preview.setByPlayer(byPlayer);
        preview.setRows(parsed);

        String token = "pv-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        preview.setPreviewToken(token);
        synchronized (previewCache) {
            // 只保留最近若干次预览，避免长时间运行内存增长
            if (previewCache.size() > 20) {
                previewCache.remove(previewCache.keySet().iterator().next());
            }
            previewCache.put(token, new PreviewCache(parsed, roundId, LocalDateTime.now()));
        }
        return preview;
    }

    /**
     * 凭预览令牌确认入账。
     *
     * @param previewToken 预览令牌
     * @param operatorId   操作人
     * @return 入账结果汇总
     */
    public Map<String, Object> confirm(String previewToken, String operatorId) {
        PreviewCache cache;
        synchronized (previewCache) {
            cache = previewCache.remove(previewToken);
        }
        if (cache == null) {
            throw new IllegalArgumentException(
                    "预览令牌无效或已使用。请重新上传文件预览后再确认，切勿凭旧预览入账");
        }
        String batchId = "ob-" + LocalDateTime.now().format(BATCH_TS) + "-"
                + UUID.randomUUID().toString().substring(0, 6);

        int inserted = 0;
        int skipped = 0;
        int failed = 0;
        long popularitySum = 0L;
        List<String> failures = new ArrayList<>();

        for (OrderRowParseResult r : cache.rows()) {
            try {
                boolean ok = selfProvider.getObject()
                        .persistRow(r, cache.roundId(), batchId, operatorId);
                if (ok) {
                    inserted++;
                    if (OrderRowParseResult.VALIDITY_VALID.equals(r.getValidity())) {
                        popularitySum += r.getPopularityValue();
                    }
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                // 单行失败不能中断整批：直播现场没有时间排查后重新导入全量文件。
                // 失败行明确列出，运营可单独补录。
                failed++;
                failures.add("第 " + r.getRowNumber() + " 行（子订单 " + r.getSubOrderNo() + "）："
                        + e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("importBatchId", batchId);
        result.put("insertedRows", inserted);
        result.put("skippedRows", skipped);
        result.put("failedRows", failed);
        result.put("popularityApplied", popularitySum);
        result.put("failures", failures);
        result.put("message", "已导入 " + inserted + " 行，跳过 " + skipped + " 行，失败 " + failed
                + " 行，合计计入人气值 " + popularitySum);
        return result;
    }

    /**
     * 落库单行。明细与人气入账在同一事务内，避免出现「明细已记但人气未加」的半成品状态。
     *
     * @return true 表示已写入，false 表示因幂等跳过
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean persistRow(OrderRowParseResult r, Integer roundId,
                                 String batchId, String operatorId) {
        String rawJson;
        try {
            rawJson = objectMapper.writeValueAsString(r.getRawRow() == null ? Map.of() : r.getRawRow());
        } catch (Exception e) {
            rawJson = "{}";
        }
        try {
            ledgerMapper.insert(r.getSubOrderNo(), r.getMainOrderNo(), r.getMerchantCode(),
                    r.getPlayerId(), r.getQuantity(), r.getUnitPriceCent(),
                    OrderRowParseResult.VALIDITY_VALID.equals(r.getValidity()) ? r.getPopularityValue() : 0L,
                    r.getOrderStatus(), r.getAftersaleStatus(), r.getValidity(), r.getInvalidReason(),
                    r.isInAftersale() ? 1 : 0, r.getPaidAt(), r.getPayableAmountCent(),
                    roundId, batchId, operatorId, rawJson);
        } catch (DuplicateKeyException e) {
            return false;
        }

        if (!OrderRowParseResult.VALIDITY_VALID.equals(r.getValidity()) || r.getPopularityValue() <= 0) {
            return true;
        }
        PopularityChangeRequest req = new PopularityChangeRequest();
        req.setTargetType("player");
        req.setTargetId(r.getPlayerId());
        req.setSource("order");
        req.setRawValue(r.getPopularityValue());
        req.setRoundId(roundId);
        req.setIdempotencyKey("order:" + r.getSubOrderNo());
        req.setOperatorId(operatorId);
        req.setReason("周边订单导入，批次 " + batchId);
        req.setOccurredAt(r.getPaidAt());
        try {
            req.setMetadata(objectMapper.writeValueAsString(Map.of(
                    "subOrderNo", r.getSubOrderNo(),
                    "merchantCode", String.valueOf(r.getMerchantCode()),
                    "quantity", String.valueOf(r.getQuantity()),
                    "unitPriceCent", String.valueOf(r.getUnitPriceCent()),
                    "importBatchId", batchId)));
        } catch (Exception ignored) {
            // metadata 仅供追溯，序列化失败不应阻断入账
        }
        PopularityChangeResult applied = popularityService.applyChange(req);
        return applied != null;
    }

    /** 归属选手并取原价，计算人气值。归属失败或未配置单价均判为 unattributed。 */
    private void attributeAndPrice(OrderRowParseResult r, OrderImportPreview preview) {
        if (!OrderRowParseResult.VALIDITY_VALID.equals(r.getValidity())) {
            return;
        }
        String code = r.getMerchantCode();
        if (code == null || code.isEmpty()) {
            r.setValidity(OrderRowParseResult.VALIDITY_UNATTRIBUTED);
            r.setInvalidReason("商家编码为空，无法归属选手");
            return;
        }
        Integer playerId = ledgerMapper.findPlayerIdByDisplayCode(code);
        if (playerId == null) {
            r.setValidity(OrderRowParseResult.VALIDITY_UNATTRIBUTED);
            r.setInvalidReason("商家编码「" + code + "」未匹配到任何选手编号，请核对选手编号配置");
            return;
        }
        ProductPriceConfig price = priceMapper.findByMerchantCode(code);
        if (price == null) {
            r.setValidity(OrderRowParseResult.VALIDITY_UNATTRIBUTED);
            r.setInvalidReason("商家编码「" + code + "」未配置商品原价，无法换算人气值");
            preview.getWarnings().add("商家编码「" + code
                    + "」缺少原价配置，相关订单未计入。请先在商品价格配置中补录后重新导入");
            return;
        }
        if (ProductPriceConfig.STATUS_DISABLED.equals(price.getStatus())) {
            r.setValidity(OrderRowParseResult.VALIDITY_UNATTRIBUTED);
            r.setInvalidReason("商家编码「" + code + "」的价格配置已停用");
            return;
        }
        r.setPlayerId(playerId);
        r.setUnitPriceCent(price.getUnitPriceCent());
        r.setPopularityValue(parser.computePopularity(price.getUnitPriceCent(), r.getQuantity()));
    }

    private boolean isEmptyRow(List<String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (String c : row) {
            if (c != null && !c.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private record PreviewCache(List<OrderRowParseResult> rows, Integer roundId, LocalDateTime createdAt) {
    }
}
