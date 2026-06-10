package com.redface.config;

/**
 * 全局业务常量。任何换算比例、阈值必须从这里取,禁止裸数字。
 */
public final class AppConstants {
    private AppConstants() {}

    // ===== 人气值换算 =====
    /** 1抖币 = 100人气值 (1元=10抖币=1000人气值) */
    public static final long POPULARITY_PER_DOUBI = 100L;
    /** 1次点赞 = 1人气值 */
    public static final long POPULARITY_PER_LIKE = 1L;
    /** 1条留言 = 100人气值 */
    public static final long POPULARITY_PER_COMMENT = 100L;

    // ===== 加成系数(×100整数存储) =====
    /** 初始系数 1.0 */
    public static final int COEFFICIENT_BASE = 100;
    /** 任务完成/失败 ±0.1 */
    public static final int COEFFICIENT_TASK_DELTA = 10;
    /** 队员PK获胜 +0.05 */
    public static final int COEFFICIENT_PK_WIN = 5;

    // ===== 百分比基数 =====
    /** 百分比计算基数: 100=100% */
    public static final int PERCENT_BASE = 100;

    // ===== 衰减规则 =====
    /** 衰减阈值倍数×100: 150=上轮的1.5倍 */
    public static final int DECAY_THRESHOLD_RATIO = 150;
    /** 超出部分计分比例×100: 10=按0.1折算 */
    public static final int DECAY_RATE = 10;

    // ===== 卡密 =====
    /** 防爆破:连续错误次数上限 */
    public static final int TOKEN_MAX_FAILURES = 5;
    /** 锁定时长(秒) */
    public static final int TOKEN_LOCK_SECONDS = 600;
    /** 卡密前缀 */
    public static final String TOKEN_PREFIX = "RFZJ";
    /** 卡密字符集(排除0/1/I/L/O) */
    public static final String TOKEN_CHARSET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    /** 卡密随机段长度 */
    public static final int TOKEN_SECTION_LENGTH = 4;
    /** 卡密随机段数量 */
    public static final int TOKEN_SECTION_COUNT = 3;
    /** 卡密总长度 (前缀-段1-段2-段3) */
    public static final int TOKEN_TOTAL_LENGTH = 19; // RFZJ-XXXX-XXXX-XXXX = 4 + 3*4 + 3 (hyphens) = 19
}
