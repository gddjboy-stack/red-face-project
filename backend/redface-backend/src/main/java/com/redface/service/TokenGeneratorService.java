package com.redface.service;

import com.redface.model.Token;

import java.util.List;

public interface TokenGeneratorService {

    /**
     * 批量生成卡密，并保存到数据库。
     *
     * @param count 要生成的卡密数量
     * @param playerId 绑定的选手ID
     * @param points 核销后增加的人气值
     * @param photoAssetId 绑定的数字写真资产ID
     * @param productSku 产品SKU
     * @return 生成的卡密列表
     */
    List<Token> generateBatch(int count, int playerId, long points, String photoAssetId, String productSku);

    /**
     * 导出指定批次的卡密。
     *
     * @param batchId 批次ID
     * @return 纯文本格式的卡密内容
     */
    String exportBatch(String batchId);
}
