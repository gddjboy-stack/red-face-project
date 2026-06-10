package com.redface.service;

import com.redface.config.AppConstants;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * 卡密核销防爆破计数器。彩排阶段使用内存 Map 实现。
 */
@Component
public class FailureCounter {

    private final ConcurrentMap<String, FailureState> states = new ConcurrentHashMap<>();
    private final Clock clock;

    public FailureCounter() {
        this(Clock.systemUTC());
    }

    FailureCounter(Clock clock) {
        this.clock = clock;
    }

    /**
     * 判断用户是否处于锁定状态。
     *
     * @param userId 用户 ID
     * @return 是否锁定
     */
    public boolean isLocked(String userId) {
        FailureState state = states.get(userId);
        if (state == null) {
            return false;
        }
        if (state.lockedUntilEpochSecond == 0L) {
            return false;
        }
        if (state.lockedUntilEpochSecond <= now()) {
            states.remove(userId, state);
            return false;
        }
        return true;
    }

    /**
     * 查询用户剩余锁定秒数。
     *
     * @param userId 用户 ID
     * @return 剩余锁定秒数
     */
    public long remainingSeconds(String userId) {
        FailureState state = states.get(userId);
        if (state == null) {
            return 0L;
        }
        return Math.max(0L, state.lockedUntilEpochSecond - now());
    }

    /**
     * 记录一次失败。连续失败达到阈值后进入锁定状态。
     *
     * @param userId 用户 ID
     */
    public void recordFailure(String userId) {
        states.compute(userId, (key, oldState) -> {
            long now = now();
            if (oldState != null && oldState.lockedUntilEpochSecond > now) {
                return oldState;
            }
            int failures = oldState == null ? 1 : oldState.failureCount + 1;
            long lockedUntil = failures >= AppConstants.TOKEN_MAX_FAILURES ? now + AppConstants.TOKEN_LOCK_SECONDS : 0L;
            return new FailureState(failures, lockedUntil);
        });
    }

    /**
     * 清除用户失败记录。核销成功后调用。
     *
     * @param userId 用户 ID
     */
    public void clear(String userId) {
        states.remove(userId);
    }

    private long now() {
        return clock.instant().getEpochSecond();
    }

    private record FailureState(int failureCount, long lockedUntilEpochSecond) {
    }
}
