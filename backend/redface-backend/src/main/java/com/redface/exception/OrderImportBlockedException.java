package com.redface.exception;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单导入被硬阻断（C20-4C）。
 *
 * <p>与普通参数错误区分成独立异常类型，原因是二者的正确处置方式不同：参数错误应当修正请求重试，
 * 而本异常要求运营<b>先补配置或显式确认排除</b>，是一次需要人介入决策的中止，不能被前端笼统地
 * 当成「网络错误，再点一次」处理。
 */
public class OrderImportBlockedException extends RuntimeException {

    /** 触发阻断的未归属子订单号，供前端直接列出让运营逐笔判断 */
    private final List<String> unattributedSubOrderNos;

    public OrderImportBlockedException(String message, List<String> unattributedSubOrderNos) {
        super(message);
        this.unattributedSubOrderNos = unattributedSubOrderNos == null
                ? new ArrayList<>() : new ArrayList<>(unattributedSubOrderNos);
    }

    public List<String> getUnattributedSubOrderNos() {
        return unattributedSubOrderNos;
    }
}
