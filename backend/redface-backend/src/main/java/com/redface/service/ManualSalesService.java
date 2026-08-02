package com.redface.service;

import com.redface.config.ManualSalesProperties;
import com.redface.dto.AdminRequests;
import com.redface.dto.ManualSalesEntryResult;
import com.redface.dto.ManualSalesSummaryItem;
import com.redface.dto.PopularityChangeRequest;
import com.redface.entity.ProductPriceConfig;
import com.redface.mapper.BasicDataMapper;
import com.redface.mapper.ManualSalesLedgerMapper;
import com.redface.mapper.OperationsLogMapper;
import com.redface.mapper.ProductPriceConfigMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * C20-6: 后台手工销量录入服务。
 *
 * <p><b>为什么存在这个功能</b>：C20-4C（订单表批量导入）已完成但暂不启用，
 * 因为 {@code players.display_code} 在生产环境没有任何写入入口
 * （见 collaboration/已知缺陷_display_code无写入入口_V1.0.md）。8/9 首场改由运营
 * 在后台按「选手 + 商品 + 件数」手工录入，选手从下拉框选取，完全绕开 display_code。
 *
 * <p><b>与订单导入的风险方向相反，必须明说</b>：订单导入的主要风险是「无意识丢失」
 * （某些订单没被计入而无人知晓），所以 C20-4C 做了硬阻断。手工录入的主要风险是
 * 「无意识多算」——运营多打一个零、或以为没提交成功而重复录入。多算比少算更难被发现，
 * 因为选手人气变高不会有人来投诉。因此本服务的防线全部朝向「拦住异常放大」：
 * 软重复提示、单笔异常量提示、禁止冲销到负数总量。
 *
 * <p><b>换算口径与订单导入完全一致</b>：人气值 = 单价（分）× 件数 × 10，
 * 复用 {@link OrderSheetParser#computePopularity(long, int)}，不另写一份公式。
 * 若两处口径出现分歧，同一笔销量在两条链路会算出不同人气，而账面无法解释差异。
 */
@Service
public class ManualSalesService {

    /** 幂等键前缀，与群投票的 gv_ 同风格，便于在账本里一眼区分来源。 */
    private static final String IDEM_PREFIX = "ms_";

    private final ManualSalesLedgerMapper ledgerMapper;
    private final ProductPriceConfigMapper priceMapper;
    private final BasicDataMapper basicDataMapper;
    private final OperationsLogMapper operationsLogMapper;
    private final PopularityService popularityService;
    private final OrderSheetParser parser;
    private final ManualSalesProperties props;

    public ManualSalesService(ManualSalesLedgerMapper ledgerMapper,
                              ProductPriceConfigMapper priceMapper,
                              BasicDataMapper basicDataMapper,
                              OperationsLogMapper operationsLogMapper,
                              PopularityService popularityService,
                              OrderSheetParser parser,
                              ManualSalesProperties props) {
        this.ledgerMapper = ledgerMapper;
        this.priceMapper = priceMapper;
        this.basicDataMapper = basicDataMapper;
        this.operationsLogMapper = operationsLogMapper;
        this.popularityService = popularityService;
        this.parser = parser;
        this.props = props;
    }

    /**
     * 录入一笔手工销量。正数累加，负数冲销。
     *
     * @param req 录入请求
     * @return 三种终态之一：已入账 / 幂等拦截 / 需二次确认
     */
    @Transactional
    public ManualSalesEntryResult record(AdminRequests.ManualSalesEntryRequest req) {
        validate(req);

        int roundId = req.getRoundId();
        int playerId = req.getPlayerId();
        String merchantCode = req.getMerchantCode().trim();
        int quantity = req.getQuantity();

        // 选手必须存在。下拉框理论上不会给出不存在的选手，但接口可被直接调用，
        // 且「往不存在的选手身上记人气」不会报错、只会在账本里留下孤儿记录。
        var player = basicDataMapper.findPlayerById(playerId);
        if (player == null) {
            throw new IllegalArgumentException("选手不存在：playerId=" + playerId);
        }

        ProductPriceConfig price = priceMapper.findByMerchantCode(merchantCode);
        if (price == null) {
            throw new IllegalArgumentException("商品编码「" + merchantCode
                    + "」未配置原价，无法换算人气值。请先在商品原价配置中录入");
        }
        if (ProductPriceConfig.STATUS_DISABLED.equals(price.getStatus())) {
            throw new IllegalArgumentException("商品编码「" + merchantCode + "」的价格配置已停用，不可录入");
        }

        // 换算：复用订单导入的同一公式，负数件数自然得到负数人气。
        long popularityValue = parser.computePopularity(price.getUnitPriceCent(), quantity);

        // 冲销不得把累计件数冲成负数。负销量没有业务含义，
        // 出现这种情况通常意味着运营选错了冲销对象（比如把 A 商品的退货记到了 B 商品上）。
        if (quantity < 0) {
            int existing = ledgerMapper.sumQuantity(roundId, playerId, merchantCode);
            if (existing + quantity < 0) {
                throw new IllegalArgumentException("冲销件数超出已录入总量：本轮该选手该商品累计 "
                        + existing + " 件，本次冲销 " + (-quantity)
                        + " 件将导致负数总量。请核对冲销对象是否选错");
            }
        }

        String idempotencyKey = IDEM_PREFIX + req.getIdempotencyKey().trim();

        // 幂等：先查后插。不在 @Transactional 内捕获 DuplicateKeyException，
        // 否则事务会被标记回滚（沿用 C20-3-FIX 群投票录入的既有策略）。
        if (ledgerMapper.countByIdempotencyKey(idempotencyKey) > 0) {
            return ManualSalesEntryResult.duplicated();
        }

        // 需二次确认的两类情形。注意：此时尚未入账，返回后必须由运营带 confirmed=true 重提。
        if (!Boolean.TRUE.equals(req.getConfirmed())) {
            String confirmReason = detectSuspicious(roundId, playerId, merchantCode,
                    quantity, popularityValue);
            if (confirmReason != null) {
                return ManualSalesEntryResult.needsConfirm(confirmReason);
            }
        }

        ledgerMapper.insert(roundId, playerId, merchantCode, price.getProductName(), quantity,
                price.getUnitPriceCent(), popularityValue, idempotencyKey,
                req.getOperatorId(), req.getReason());

        // 留痕先于人气入账。若日志写失败，整笔事务回滚，宁可不入账——
        // 绝不允许出现「人气已变、没人知道是谁录的」。
        operationsLogMapper.insert(req.getOperatorId(),
                quantity >= 0 ? "manual_sales_entry" : "manual_sales_reversal",
                "player:" + playerId,
                "{\"roundId\":" + roundId
                        + ",\"playerId\":" + playerId
                        + ",\"merchantCode\":\"" + safe(merchantCode) + "\""
                        + ",\"quantity\":" + quantity
                        + ",\"unitPriceCent\":" + price.getUnitPriceCent()
                        + ",\"popularityValue\":" + popularityValue
                        + ",\"confirmed\":" + Boolean.TRUE.equals(req.getConfirmed())
                        + ",\"idempotencyKey\":\"" + safe(idempotencyKey) + "\"}",
                req.getReason());

        // 人气入账走全系统唯一入口。source 用 manual：引擎对 manual 不再换算，
        // rawValue 即人气值，与订单导入传 order 的处理方式一致。
        PopularityChangeRequest pop = new PopularityChangeRequest();
        pop.setTargetType("player");
        pop.setTargetId(playerId);
        pop.setSource("manual");
        pop.setRawValue(popularityValue);
        pop.setRoundId(roundId);
        pop.setIdempotencyKey(idempotencyKey);
        pop.setOperatorId(req.getOperatorId());
        pop.setReason("手工销量录入：" + merchantCode + " × " + quantity + " 件。" + req.getReason());
        pop.setOccurredAt(LocalDateTime.now());
        popularityService.applyChange(pop);

        ManualSalesEntryResult result = new ManualSalesEntryResult();
        result.setStatus(ManualSalesEntryResult.STATUS_RECORDED);
        result.setPopularityValue(popularityValue);
        result.setUnitPriceCent(price.getUnitPriceCent());
        result.setQuantity(quantity);
        result.setProductName(price.getProductName());
        result.setPlayerId(playerId);
        result.setPlayerName(player.getName());
        result.setTotalQuantityAfter(ledgerMapper.sumQuantity(roundId, playerId, merchantCode));
        return result;
    }

    /**
     * 检测需要运营二次确认的可疑录入。返回 null 表示无异常。
     *
     * <p>两类检测都是<b>提示而非阻止</b>：手工录入的合法情形太多，硬阻断会误伤。
     * 但静默接受同样不可接受——多算的人气不会有人来投诉，错误会一直留在结算里。
     */
    private String detectSuspicious(int roundId, int playerId, String merchantCode,
                                    int quantity, long popularityValue) {
        // 情形一：软重复。既有幂等键由前端生成，只能防「同一次点击的重复提交」；
        // 「运营以为没成功、手动又点一次」会得到新的幂等键而正常入账，幂等机制对此无效。
        // 同一选手同一商品同一件数在窗口期内出现两次，是这种情形的典型指纹。
        int windowSeconds = props.getRecentWindowSeconds();
        LocalDateTime since = LocalDateTime.now().minusSeconds(windowSeconds);
        int recent = ledgerMapper.countRecentSame(roundId, playerId, merchantCode, quantity, since);
        if (recent > 0) {
            return windowSeconds + " 秒内已录入过完全相同的一笔（" + merchantCode
                    + " × " + quantity + " 件，共 " + recent + " 笔）。若这确实是新增的一笔销量，"
                    + "请确认后重新提交；若是误操作，请勿确认";
        }

        // 情形二：单笔件数异常。防的是「多打一个零」——这是手工录入最典型的错误，
        // 且一旦发生，选手人气会被放大十倍而账面看不出任何异常。
        //
        // Claude 裁定 A1：校验对象从「折算人气 vs 本轮最高人气的倍数」改为「绝对件数」。
        // 两个理由：其一，旧方案会在轮次初期稳定误报（一笔 30 件×199 元 = 597,000 人气，
        // 而当时最高选手可能只有几千），而频繁误报会让运营习惯性点确认，防线自动失效；
        // 其二，错误发生在件数上（运营把 30 敲成 300），校验对象必须与出错对象一致，
        // 否则提示文案无法指向运营该复核的地方。
        //
        // 取绝对值则同时覆盖正负：冲销 -300 件同样是多打一个零，且冲销方向是扣减人气，
        // 错了更不容易被发现（选手不会来投诉自己人气过高，也不一定盯着自己得分）。
        int threshold = props.getAbnormalQuantityThreshold();
        if (Math.abs(quantity) > threshold) {
            return "本笔件数 " + quantity + "（绝对值 " + Math.abs(quantity)
                    + "）超过单笔阈值 " + threshold + " 件，折算人气 " + popularityValue
                    + "。请核对件数是否多打了一位数字，确认无误后重新提交";
        }
        return null;
    }

    /**
     * 本轮手工销量汇总，两级展开：外层按选手给出人气合计，内层按商品给出件数与人气。
     *
     * <p>为什么必须两级：商家编码规则为「每位选手每款商品一个独立编码」，
     * 跨商品把件数相加得不到有业务含义的数字（明信片 30 件 + 写真 5 件 = 35 件，
     * 这个 35 无法用来判断任何事）。而运营核对时既需要「林一本场一共多少人气」，
     * 也需要「明信片卖了多少件」来验证单价配置是否正确，两者缺一不可。
     */
    public ManualSalesSummary summarize(int roundId) {
        if (roundId <= 0) {
            throw new IllegalArgumentException("roundId必须为正数");
        }
        List<ManualSalesSummaryItem> items = ledgerMapper.summarize(roundId);

        Map<Integer, PlayerGroup> grouped = new LinkedHashMap<>();
        for (ManualSalesSummaryItem item : items) {
            PlayerGroup g = grouped.computeIfAbsent(item.getPlayerId(), k -> {
                PlayerGroup pg = new PlayerGroup();
                pg.playerId = item.getPlayerId();
                pg.playerName = item.getPlayerName();
                pg.playerNumber = item.getPlayerNumber();
                return pg;
            });
            g.products.add(item);
            g.totalPopularity += item.getTotalPopularity();
            g.entryCount += item.getEntryCount();
            if (item.isPriceInconsistent()) {
                g.hasPriceInconsistency = true;
            }
        }

        ManualSalesSummary summary = new ManualSalesSummary();
        summary.roundId = roundId;
        summary.players = new ArrayList<>(grouped.values());
        summary.grandTotalPopularity = summary.players.stream()
                .mapToLong(p -> p.totalPopularity).sum();
        for (PlayerGroup g : summary.players) {
            if (g.hasPriceInconsistency) {
                summary.warnings.add("选手「" + g.playerName
                        + "」存在同一商品多笔录入单价不一致的情况，说明期间改过价格。"
                        + "此时无法用「件数 × 单价」反推人气合计，核对时请以账本明细为准");
            }
        }
        return summary;
    }

    private void validate(AdminRequests.ManualSalesEntryRequest req) {
        if (!StringUtils.hasText(req.getOperatorId())) {
            throw new IllegalArgumentException("operatorId不能为空");
        }
        if (!StringUtils.hasText(req.getReason())) {
            throw new IllegalArgumentException("reason不能为空（每一笔人气变更都必须有人具名负责）");
        }
        if (!StringUtils.hasText(req.getIdempotencyKey())) {
            throw new IllegalArgumentException("idempotencyKey不能为空（防连点，由前端生成）");
        }
        if (!StringUtils.hasText(req.getMerchantCode())) {
            throw new IllegalArgumentException("merchantCode不能为空");
        }
        if (req.getRoundId() == null || req.getRoundId() <= 0) {
            throw new IllegalArgumentException("roundId必须为正数");
        }
        if (req.getPlayerId() == null || req.getPlayerId() <= 0) {
            throw new IllegalArgumentException("playerId必须为正数");
        }
        if (req.getQuantity() == null || req.getQuantity() == 0) {
            throw new IllegalArgumentException("quantity不能为0（正数累加，负数冲销）");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 本轮手工销量汇总响应。 */
    public static class ManualSalesSummary {
        private int roundId;
        private List<PlayerGroup> players = new ArrayList<>();
        private long grandTotalPopularity;
        private final List<String> warnings = new ArrayList<>();

        public int getRoundId() { return roundId; }
        public List<PlayerGroup> getPlayers() { return players; }
        public long getGrandTotalPopularity() { return grandTotalPopularity; }
        public List<String> getWarnings() { return warnings; }
    }

    /** 汇总中的选手分组：外层人气合计 + 内层各商品明细。 */
    public static class PlayerGroup {
        private Integer playerId;
        private String playerName;
        private Integer playerNumber;
        private final List<ManualSalesSummaryItem> products = new ArrayList<>();
        private long totalPopularity;
        private long entryCount;
        private boolean hasPriceInconsistency;

        public Integer getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public Integer getPlayerNumber() { return playerNumber; }
        public List<ManualSalesSummaryItem> getProducts() { return products; }
        public long getTotalPopularity() { return totalPopularity; }
        public long getEntryCount() { return entryCount; }
        public boolean isHasPriceInconsistency() { return hasPriceInconsistency; }
    }
}
