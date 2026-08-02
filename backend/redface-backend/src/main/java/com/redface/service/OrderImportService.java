package com.redface.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redface.dto.OrderImportPreview;
import com.redface.dto.OrderRowParseResult;
import com.redface.dto.PlayerOrderSummary;
import com.redface.dto.PopularityChangeRequest;
import com.redface.dto.PopularityChangeResult;
import com.redface.entity.ProductPriceConfig;
import com.redface.exception.OrderImportBlockedException;
import com.redface.mapper.OperationsLogMapper;
import com.redface.mapper.OrderSalesLedgerMapper;
import com.redface.mapper.ProductPriceConfigMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *
 * <p>C20-4C 引入三层防护，各自防的是不同失效模式：
 * <ol>
 *   <li><b>硬阻断</b>（{@link #confirm} 入口检查）——防「运营没注意到有行未归属就点了确认」，
 *       即无意识丢失。它不禁止有意识排除。</li>
 *   <li><b>显式覆盖 + 留痕</b>（{@link #confirmWithOverride}）——把有意识排除变成一次
 *       有署名、有原因、可追溯的动作，赛后选手质疑时可举证。</li>
 *   <li><b>按选手汇总核对</b>（{@code byPlayerDetail}）——即使前两层都被绕过，
 *       件数与人气值并列展示仍能让编号配错、单价配错暴露为不合理数字。</li>
 * </ol>
 */
@Service
public class OrderImportService {

    private static final DateTimeFormatter BATCH_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 覆盖放行的审计动作类型。测试与赛后审计均按此常量检索 */
    public static final String ACTION_IMPORT_OVERRIDE = "order_import_override";

    private final OrderSheetParser parser;
    private final OrderSalesLedgerMapper ledgerMapper;
    private final ProductPriceConfigMapper priceMapper;
    private final PopularityService popularityService;
    private final OperationsLogMapper operationsLogMapper;
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
                              OperationsLogMapper operationsLogMapper,
                              ObjectProvider<OrderImportService> selfProvider) {
        this.selfProvider = selfProvider;
        this.parser = parser;
        this.ledgerMapper = ledgerMapper;
        this.priceMapper = priceMapper;
        this.popularityService = popularityService;
        this.operationsLogMapper = operationsLogMapper;
    }

    /**
     * 解析并预览，不写库。
     *
     * @param rows    含表头的全部行，第 0 行为表头
     * @param roundId 入账轮次
     * @return 预览结果，含 previewToken
     */
    public OrderImportPreview preview(List<List<String>> rows, Integer roundId) {
        OrderImportPreview preview = parseInternal(rows, roundId);
        if (!preview.getBlockingErrors().isEmpty()) {
            return preview;
        }
        String token = "pv-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        preview.setPreviewToken(token);
        synchronized (previewCache) {
            // 只保留最近若干次预览，避免长时间运行内存增长
            if (previewCache.size() > 20) {
                previewCache.remove(previewCache.keySet().iterator().next());
            }
            previewCache.put(token, new PreviewCache(
                    preview.getRows(), roundId, LocalDateTime.now(),
                    new ArrayList<>(preview.getUnattributedSubOrderNos())));
        }
        return preview;
    }

    /**
     * 前置检查（C20-4C）：只校验，<b>不生成预览令牌、不写入 previewCache、不写库</b>。
     *
     * <p>用途是赛前空跑：拿一份历史或测试订单表跑一遍，确认单价配置齐全、选手编号能全部归属，
     * 而不在系统里留下任何痕迹。若直接复用 {@link #preview}，会占用预览缓存槽位并产生一个
     * 可被误用的有效令牌——现场紧张时「点错了那个还亮着的确认按钮」是真实风险。
     *
     * <p>本方法<b>照样执行已入账查重</b>并在 duplicateRows 中如实反映。空跑结果因此可能
     * 与当天导入结果不同（当天已有前批次入账），这是如实呈现而非隐藏：若空跑时跳过查重，
     * 运营会误以为「这些行都会计入」。
     *
     * @param rows    含表头的全部行
     * @param roundId 轮次（仅用于沿用同一解析路径，不写库）
     * @return 校验结果，previewToken 恒为 null
     */
    public OrderImportPreview preflight(List<List<String>> rows, Integer roundId) {
        OrderImportPreview preview = parseInternal(rows, roundId);
        preview.setPreviewToken(null);
        return preview;
    }

    /**
     * 解析与汇总的共用实现。{@link #preview} 与 {@link #preflight} 必须走同一条路径，
     * 否则空跑通过而正式导入被拦，这类不一致在赛前无法被发现。
     */
    private OrderImportPreview parseInternal(List<List<String>> rows, Integer roundId) {
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
        Map<String, PlayerOrderSummary> byPlayerDetail = new LinkedHashMap<>();
        List<OrderRowParseResult> parsed = new ArrayList<>();
        List<String> unattributedSubOrderNos = new ArrayList<>();
        // 文件内自查重：同一文件里出现重复子订单号，说明导出时勾选了重复维度，须提示而非静默去重
        Map<String, Integer> seenSubOrder = new HashMap<>();
        Set<String> unknownStatuses = new LinkedHashSet<>();

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
                    preview.setTotalQuantity(preview.getTotalQuantity() + r.getQuantity());
                    byPlayer.merge(r.getMerchantCode(), r.getPopularityValue(), Long::sum);
                    byPlayerDetail
                            .computeIfAbsent(r.getMerchantCode(), PlayerOrderSummary::new)
                            .accumulate(r);
                    if (r.isInAftersale()) {
                        preview.setAftersaleRows(preview.getAftersaleRows() + 1);
                        preview.setAftersaleExposure(preview.getAftersaleExposure() + r.getPopularityValue());
                    }
                }
                case OrderRowParseResult.VALIDITY_UNATTRIBUTED -> {
                    preview.setUnattributedRows(preview.getUnattributedRows() + 1);
                    if (r.getSubOrderNo() != null && !r.getSubOrderNo().isEmpty()) {
                        unattributedSubOrderNos.add(r.getSubOrderNo());
                    }
                }
                default -> {
                    preview.setInvalidRows(preview.getInvalidRows() + 1);
                    if (r.isUnknownOrderStatus()) {
                        preview.setUnknownStatusRows(preview.getUnknownStatusRows() + 1);
                        unknownStatuses.add(String.valueOf(r.getOrderStatus()));
                    }
                }
            }
            parsed.add(r);
        }

        if (!unknownStatuses.isEmpty()) {
            // 未知状态不在硬阻断范围内（卡片限定阻断未归属行），故必须以告警显性暴露：
            // 平台改了状态名称时，这些订单会静默少算，而阻断不会触发。
            preview.getWarnings().add("有 " + preview.getUnknownStatusRows()
                    + " 行的订单状态不在已知枚举内（" + String.join("、", unknownStatuses)
                    + "），已按不计入处理。若平台新增了状态名称，需人工核对后决定是否补录");
        }

        preview.setTotalRows(parsed.size());
        preview.setByPlayer(byPlayer);
        preview.setByPlayerDetail(new ArrayList<>(byPlayerDetail.values()));
        preview.setUnattributedSubOrderNos(unattributedSubOrderNos);
        preview.setRows(parsed);
        if (preview.getUnattributedRows() > 0) {
            preview.setBlockedByUnattributed(true);
            preview.setBlockReason("有 " + preview.getUnattributedRows()
                    + " 行无法归属到选手（缺少商品原价配置、商家编码未匹配到选手，或价格配置已停用）。"
                    + "这些订单不会计入任何选手的人气值。请先补齐配置后重新预览；"
                    + "若确认这些订单确实无需计入，须在下一步逐笔勾选并填写原因。");
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
        PreviewCache peek;
        synchronized (previewCache) {
            peek = previewCache.get(previewToken);
        }
        if (peek != null && !peek.unattributedSubOrderNos().isEmpty()) {
            throw new OrderImportBlockedException(
                    "本次预览有 " + peek.unattributedSubOrderNos().size()
                            + " 行无法归属到选手，已阻止入账。请先补齐商品原价配置或选手编号后重新预览；"
                            + "若确认这些订单确实无需计入人气，请改用「确认并排除未归属订单」并填写原因。",
                    peek.unattributedSubOrderNos());
        }
        return doConfirm(previewToken, operatorId, null, null);
    }

    /**
     * 显式覆盖确认（C20-4C）：运营逐笔确认「这些未归属订单确实无需计入人气」后放行。
     *
     * <p>三条硬性约束，缺一不可：
     * <ol>
     *   <li>必须逐笔提交子订单号，且须与本次预览的未归属行<b>完全一致</b>。
     *       允许提交子集会导致「勾了一部分就整批放行」，等于阻断失效。</li>
     *   <li>必须填写原因。无原因的放行在赛后等于没有记录——「谁放的、为什么」是
     *       选手质疑时唯一能自证的东西。</li>
     *   <li>必须写 operations_log，且先于入账。不允许「入账成功但没留痕」。</li>
     * </ol>
     *
     * <p>被排除的行仍然落库（validity 保持 unattributed、popularity_value = 0），
     * 因为赛后要能回答「这笔为什么没算」，删掉记录就永远答不上。
     *
     * @param previewToken        预览令牌
     * @param operatorId          操作人
     * @param overrideSubOrderNos 运营逐笔勾选确认排除的子订单号
     * @param overrideReason      排除原因（必填）
     * @return 入账结果汇总，含 overriddenRows
     */
    public Map<String, Object> confirmWithOverride(String previewToken, String operatorId,
                                                  List<String> overrideSubOrderNos,
                                                  String overrideReason) {
        if (overrideReason == null || overrideReason.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "排除未归属订单必须填写原因。无原因的放行在赛后无法解释，等同于没有记录");
        }
        PreviewCache peek;
        synchronized (previewCache) {
            peek = previewCache.get(previewToken);
        }
        if (peek == null) {
            throw new IllegalArgumentException(
                    "预览令牌无效或已使用。请重新上传文件预览后再确认，切勿凭旧预览入账");
        }
        List<String> expected = peek.unattributedSubOrderNos();
        if (expected.isEmpty()) {
            throw new IllegalArgumentException(
                    "本次预览没有未归属订单，无需使用排除入口。请使用普通确认入账");
        }
        Set<String> submitted = new HashSet<>(
                overrideSubOrderNos == null ? List.<String>of() : overrideSubOrderNos);
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(submitted);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("还有 " + missing.size()
                    + " 笔未归属订单未勾选确认：" + String.join("、", limit(missing, 10))
                    + "。必须逐笔确认，勾选部分不予放行——否则未勾选的那几笔会被无声排除");
        }
        Set<String> unexpected = new LinkedHashSet<>(submitted);
        expected.forEach(unexpected::remove);
        if (!unexpected.isEmpty()) {
            throw new IllegalArgumentException("提交的子订单号中有 " + unexpected.size()
                    + " 笔并不属于本次预览的未归属订单：" + String.join("、", limit(unexpected, 10))
                    + "。请重新预览后再确认，避免凭旧页面提交");
        }
        return doConfirm(previewToken, operatorId, expected, overrideReason.trim());
    }

    /** 实际入账。overrideSubOrderNos 非 null 表示本次为覆盖放行，须先写审计日志。 */
    private Map<String, Object> doConfirm(String previewToken, String operatorId,
                                          List<String> overrideSubOrderNos, String overrideReason) {
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

        if (overrideSubOrderNos != null) {
            // 留痕先于入账：若日志写入失败则整次覆盖中止，宁可不入账也不允许出现
            // 「人气已加、无人知道谁放行的」这种赛后无法解释的状态。
            writeOverrideLog(batchId, operatorId, overrideSubOrderNos, overrideReason);
        }

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
        result.put("overriddenRows", overrideSubOrderNos == null ? 0 : overrideSubOrderNos.size());
        result.put("failures", failures);
        String msg = "已导入 " + inserted + " 行，跳过 " + skipped + " 行，失败 " + failed
                + " 行，合计计入人气值 " + popularitySum;
        if (overrideSubOrderNos != null) {
            msg += "；已按运营确认排除 " + overrideSubOrderNos.size() + " 笔未归属订单（已留痕）";
        }
        result.put("message", msg);
        return result;
    }

    /** 写覆盖放行审计日志。日志内容须能独立还原「谁、何时、放行了哪些子订单、原因」。 */
    private void writeOverrideLog(String batchId, String operatorId,
                                  List<String> subOrderNos, String reason) {
        String detail;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("importBatchId", batchId);
            payload.put("overriddenCount", subOrderNos.size());
            payload.put("subOrderNos", subOrderNos);
            detail = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            // 序列化失败不能降级为空日志：detail 是赛后举证的唯一载体。
            // 退化为可读文本，保证「放行了哪几笔」这一关键信息不丢。
            detail = "importBatchId=" + batchId + "; subOrderNos=" + String.join(",", subOrderNos);
        }
        operationsLogMapper.insert(operatorId, ACTION_IMPORT_OVERRIDE, batchId, detail, reason);
    }

    /** 错误提示里只列前 N 笔，避免上千笔时报错信息长到无法阅读。 */
    private List<String> limit(Set<String> values, int max) {
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (out.size() >= max) {
                out.add("…");
                break;
            }
            out.add(v);
        }
        return out;
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
        // 选手姓名仅用于按选手汇总核对视图，不参与任何计算；查不到时留空不阻断
        r.setPlayerName(ledgerMapper.findPlayerNameByDisplayCode(code));
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

    /**
     * 预览缓存条目。
     *
     * @param unattributedSubOrderNos 未归属子订单号。缓存在此而非确认时重新统计，
     *        因为重新统计会再次查库，而库状态可能已变（例如运营中途补了单价配置），
     *        导致「预览时被阻断、确认时不阻断」这种前后不一致。
     */
    private record PreviewCache(List<OrderRowParseResult> rows, Integer roundId,
                                LocalDateTime createdAt, List<String> unattributedSubOrderNos) {
    }
}
