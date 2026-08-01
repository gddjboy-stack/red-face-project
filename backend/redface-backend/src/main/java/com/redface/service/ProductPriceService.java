package com.redface.service;

import com.redface.entity.ProductPriceConfig;
import com.redface.mapper.OperationsLogMapper;
import com.redface.mapper.ProductPriceConfigMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 商品原价配置服务（C20-4B）。
 *
 * <p>人气按「原价 × 件数」计算（John 2026-08-01 决策），原价由此表定义而非从订单导出表反推。
 * 官方「订单应付金额」已扣除运费与各类优惠，若据此计人气，包邮商品与用券订单的人气会缩水，
 * 粉丝花了 19.9 却发现只加了 15000 人气，这在直播现场是无法解释的争议。
 *
 * <p><b>改价是高风险操作</b>：改价会改变<i>后续</i>导入的换算依据。已入账的历史订单不会被追溯，
 * 因此同一商品在改价前后导入会得到不同人气值。所以改价一律写操作日志留痕。
 */
@Service
public class ProductPriceService {

    private final ProductPriceConfigMapper priceMapper;
    private final OperationsLogMapper operationsLogMapper;

    public ProductPriceService(ProductPriceConfigMapper priceMapper,
                               OperationsLogMapper operationsLogMapper) {
        this.priceMapper = priceMapper;
        this.operationsLogMapper = operationsLogMapper;
    }

    public List<ProductPriceConfig> list() {
        return priceMapper.findAll();
    }

    /**
     * 新增或改价。
     *
     * @param merchantCode  商家编码，须与选手编号一致
     * @param productName   商品名
     * @param unitPriceYuan 单价（元），如 "19.9"
     * @param status        active/disabled，空则默认 active
     * @param operatorId    操作人
     * @return 保存后的配置
     */
    @Transactional
    public ProductPriceConfig save(String merchantCode, String productName, String unitPriceYuan,
                                   String status, String operatorId) {
        if (!StringUtils.hasText(merchantCode)) {
            throw new IllegalArgumentException("商家编码不能为空");
        }
        if (!StringUtils.hasText(productName)) {
            throw new IllegalArgumentException("商品名不能为空");
        }
        long cent = parseYuanToCent(unitPriceYuan);
        if (cent <= 0) {
            throw new IllegalArgumentException("单价必须大于 0 元");
        }
        String finalStatus = StringUtils.hasText(status) ? status : ProductPriceConfig.STATUS_ACTIVE;
        if (!ProductPriceConfig.STATUS_ACTIVE.equals(finalStatus)
                && !ProductPriceConfig.STATUS_DISABLED.equals(finalStatus)) {
            throw new IllegalArgumentException("status 只能是 active 或 disabled");
        }

        ProductPriceConfig before = priceMapper.findByMerchantCode(merchantCode);
        priceMapper.upsert(merchantCode.trim(), productName.trim(), cent, finalStatus, operatorId);

        // 改价留痕：改价会改变后续导入的换算依据，已入账历史订单不追溯，必须可查
        String detail = before == null
                ? "新增商品原价配置：" + merchantCode + " " + productName + " " + centToYuan(cent) + " 元"
                : "修改商品原价配置：" + merchantCode + " 原价 " + centToYuan(before.getUnitPriceCent())
                        + " 元 → " + centToYuan(cent) + " 元（仅影响此后导入的订单，已入账订单不追溯）";
        operationsLogMapper.insert(operatorId, "product_price_save", merchantCode, detail,
                before == null ? "新增商品原价配置" : "修改商品原价配置");

        return priceMapper.findByMerchantCode(merchantCode);
    }

    /**
     * 元转分。用字符串按小数点切分而非 double 运算——19.9 在双精度下是 19.899999...，
     * 乘 100 取整会得到 1989 分，每张明信片少算 10 个人气值。数量大时累积成可见误差。
     *
     * @param raw 单价字符串，如 "19.9"、"99"、"19.90"
     * @return 分
     */
    public long parseYuanToCent(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("单价不能为空");
        }
        String s = raw.trim().replace(",", "").replace("，", "")
                .replace("¥", "").replace("￥", "").replace("元", "");
        if (!s.matches("\\d+(\\.\\d{1,2})?")) {
            throw new IllegalArgumentException("单价格式不正确，应为最多两位小数的数字，如 19.9：" + raw);
        }
        int dot = s.indexOf('.');
        if (dot < 0) {
            return Long.parseLong(s) * 100L;
        }
        String intPart = s.substring(0, dot);
        String decPart = s.substring(dot + 1);
        if (decPart.length() == 1) {
            decPart = decPart + "0";
        }
        return Long.parseLong(intPart) * 100L + Long.parseLong(decPart);
    }

    /** 分转元，仅用于展示与日志。 */
    public String centToYuan(long cent) {
        return (cent / 100) + "." + String.format("%02d", cent % 100);
    }
}
