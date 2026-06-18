package com.redface.dto;

/**
 * 卡密核销结果对象，表达成功、重复核销、失败和防爆破锁定等状态。
 */
public class RedeemResult {
    private final boolean success;
    private final String code;
    private final String message;
    private final String tokenId;
    private final Integer playerId;
    private final long points;
    private final String photoAssetId;
    private final UserMembershipSummary membership;
    private final long remainingSeconds;

    private RedeemResult(boolean success,
                         String code,
                         String message,
                         String tokenId,
                         Integer playerId,
                         long points,
                         String photoAssetId,
                         UserMembershipSummary membership,
                         long remainingSeconds) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.tokenId = tokenId;
        this.playerId = playerId;
        this.points = points;
        this.photoAssetId = photoAssetId;
        this.membership = membership;
        this.remainingSeconds = remainingSeconds;
    }

    /**
     * 构造核销成功结果。
     *
     * @param tokenId      卡密
     * @param playerId     绑定选手 ID
     * @param points       入账人气值
     * @param photoAssetId 写真资产 ID
     * @return 成功结果
     */
    public static RedeemResult success(String tokenId, Integer playerId, long points, String photoAssetId, UserMembershipSummary membership) {
        return new RedeemResult(true, "success", "核销成功", tokenId, playerId, points, photoAssetId, membership, 0L);
    }

    /**
     * 构造重复核销结果。
     *
     * @return 重复核销结果
     */
    public static RedeemResult alreadyUsed() {
        return new RedeemResult(false, "already_used", "卡密已被核销", null, null, 0L, null, null, 0L);
    }

    /**
     * 构造失败结果。
     *
     * @param code    错误码
     * @param message 错误说明
     * @return 失败结果
     */
    public static RedeemResult fail(String code, String message) {
        return new RedeemResult(false, code, message, null, null, 0L, null, null, 0L);
    }

    /**
     * 构造防爆破锁定结果。
     *
     * @param remainingSeconds 剩余锁定秒数
     * @return 锁定结果
     */
    public static RedeemResult locked(long remainingSeconds) {
        return new RedeemResult(false, "locked", "连续错误次数过多，请稍后再试", null, null, 0L, null, null, remainingSeconds);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getTokenId() {
        return tokenId;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public long getPoints() {
        return points;
    }

    public String getPhotoAssetId() {
        return photoAssetId;
    }

    public UserMembershipSummary getMembership() {
        return membership;
    }

    public long getRemainingSeconds() {
        return remainingSeconds;
    }
}
