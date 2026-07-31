package com.redface.web;

import com.redface.config.DisplayProperties;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * C20-5 展示令牌换票防爆破计数器（边界 D-1）。
 *
 * <p>展示令牌是人工在大屏页手输的短口令，若不限流则可被枚举。
 * 本计数器只作用于换取 Cookie 的端点，不影响换票成功后的轮询。
 *
 * <p>实现方式与 {@link com.redface.service.FailureCounter} 一致，采用内存 Map；
 * 大屏为单机现场使用，无需分布式一致性。
 */
@Component
public class DisplayLoginGuard {

    private final ConcurrentMap<String, FailureState> states = new ConcurrentHashMap<>();
    private final DisplayProperties properties;
    private final Clock clock;

    @Autowired
    public DisplayLoginGuard(DisplayProperties properties) {
        this(properties, Clock.systemUTC());
    }

    DisplayLoginGuard(DisplayProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 判断来源是否处于锁定状态。
     *
     * @param clientKey 来源标识（通常为客户端 IP）
     * @return 是否锁定
     */
    public boolean isLocked(String clientKey) {
        FailureState state = states.get(clientKey);
        if (state == null || state.lockedUntilEpochSecond == 0L) {
            return false;
        }
        if (state.lockedUntilEpochSecond <= now()) {
            states.remove(clientKey, state);
            return false;
        }
        return true;
    }

    /**
     * 查询来源剩余锁定秒数。
     *
     * @param clientKey 来源标识
     * @return 剩余锁定秒数
     */
    public long remainingSeconds(String clientKey) {
        FailureState state = states.get(clientKey);
        if (state == null) {
            return 0L;
        }
        return Math.max(0L, state.lockedUntilEpochSecond - now());
    }

    /**
     * 记录一次换票失败。达到阈值后进入锁定状态。
     *
     * @param clientKey 来源标识
     */
    public void recordFailure(String clientKey) {
        states.compute(clientKey, (key, oldState) -> {
            long now = now();
            if (oldState != null && oldState.lockedUntilEpochSecond > now) {
                return oldState;
            }
            int failures = oldState == null ? 1 : oldState.failureCount + 1;
            long lockedUntil = failures >= properties.getMaxLoginFailures()
                    ? now + properties.getLoginLockSeconds()
                    : 0L;
            return new FailureState(failures, lockedUntil);
        });
    }

    /**
     * 换票成功后清除失败记录。
     *
     * @param clientKey 来源标识
     */
    public void clear(String clientKey) {
        states.remove(clientKey);
    }

    private long now() {
        return clock.instant().getEpochSecond();
    }

    private record FailureState(int failureCount, long lockedUntilEpochSecond) {
    }
}
