package com.redface.service;

import org.springframework.stereotype.Service;

/**
 * 场控状态服务。C2 暂不实现 like/comment 的场控归属，C3 将补全该服务。
 */
@Service
public class CollectStateService {

    /**
     * 获取当前场控状态。C2 阶段仅实现 player 直接归属，因此该方法暂不提供业务结果。
     *
     * @return 当前场控状态，C2 阶段返回 null
     */
    public Object getCurrent() {
        return null;
    }
}
