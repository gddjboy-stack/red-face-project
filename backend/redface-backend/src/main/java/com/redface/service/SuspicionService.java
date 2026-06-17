package com.redface.service;

import com.redface.dto.SuspicionCandidateView;
import com.redface.dto.SuspicionStatusResponse;
import com.redface.dto.SuspicionSubmitRequest;
import com.redface.dto.SuspicionSubmitResponse;
import com.redface.entity.CollectState;
import com.redface.mapper.C9QueryMapper;
import com.redface.mapper.SuspicionMapper;
import com.redface.query.RoundSummary;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * C13 真相识破服务。用户侧状态与候选分布不暴露真实卧底身份。
 */
@Service
public class SuspicionService {
    public static final String CODE_NOT_OPEN = "not_open";
    public static final String CODE_INVALID_CANDIDATE = "invalid_candidate";
    public static final String CODE_ALREADY_SUBMITTED = "already_submitted";
    public static final String CODE_ROUND_MISMATCH = "round_mismatch";

    private static final String MODE_SPY = "spy";
    private static final String SUBMITTED_MESSAGE = "本轮判断已提交，请等待直播间揭晓。";

    private final SuspicionMapper suspicionMapper;
    private final C9QueryMapper c9QueryMapper;
    private final CollectStateService collectStateService;

    public SuspicionService(SuspicionMapper suspicionMapper,
                            C9QueryMapper c9QueryMapper,
                            CollectStateService collectStateService) {
        this.suspicionMapper = suspicionMapper;
        this.c9QueryMapper = c9QueryMapper;
        this.collectStateService = collectStateService;
    }

    public SuspicionStatusResponse getStatus(String userId, Integer requestedRoundId) {
        validateUser(userId);
        RoundSummary activeRound = c9QueryMapper.findLatestActiveRound();
        SuspicionStatusResponse response = new SuspicionStatusResponse();
        response.setUpdatedAt(nowEpochSeconds());
        if (activeRound == null) {
            response.setOpen(false);
            response.setSubmitted(false);
            return response;
        }

        int roundId = requestedRoundId == null ? activeRound.getRoundId() : requestedRoundId;
        response.setRoundId(roundId);
        response.setRoundName(activeRound.getRoundId() == roundId ? activeRound.getRoundName() : null);
        boolean open = activeRound.getRoundId() == roundId && isSpyChannelOpen(activeRound.getRoundId());
        response.setOpen(open);

        List<SuspicionCandidateView> candidates = suspicionMapper.findCandidatesWithCounts(roundId);
        fillRatios(candidates, suspicionMapper.countByRound(roundId));
        response.setCandidates(candidates);

        Integer submittedPlayerId = suspicionMapper.findSubmittedPlayerId(userId, roundId);
        response.setSubmitted(submittedPlayerId != null);
        response.setSubmittedPlayerId(submittedPlayerId);
        return response;
    }

    public SuspicionStatusResponse getAdminStatus(Integer requestedRoundId) {
        RoundSummary activeRound = c9QueryMapper.findLatestActiveRound();
        SuspicionStatusResponse response = new SuspicionStatusResponse();
        response.setUpdatedAt(nowEpochSeconds());
        if (activeRound == null) {
            response.setOpen(false);
            response.setSubmitted(false);
            return response;
        }

        int roundId = requestedRoundId == null ? activeRound.getRoundId() : requestedRoundId;
        response.setRoundId(roundId);
        response.setRoundName(activeRound.getRoundId() == roundId ? activeRound.getRoundName() : null);
        response.setOpen(activeRound.getRoundId() == roundId && isSpyChannelOpen(activeRound.getRoundId()));
        List<SuspicionCandidateView> candidates = suspicionMapper.findCandidatesWithCounts(roundId);
        fillRatios(candidates, suspicionMapper.countByRound(roundId));
        response.setCandidates(candidates);
        response.setSubmitted(false);
        response.setSubmittedPlayerId(null);
        return response;
    }

    @Transactional
    public SuspicionSubmitResponse submit(String userId, SuspicionSubmitRequest request) {
        validateUser(userId);
        if (request == null || request.getRoundId() == null || request.getRoundId() <= 0
                || request.getSuspectPlayerId() == null || request.getSuspectPlayerId() <= 0) {
            throw new SuspicionException(CODE_INVALID_CANDIDATE, "该选手暂不可选择，请刷新后重试。");
        }

        RoundSummary activeRound = c9QueryMapper.findLatestActiveRound();
        if (activeRound == null || !isSpyChannelOpen(activeRound.getRoundId())) {
            throw new SuspicionException(CODE_NOT_OPEN, "该环节暂未开启。");
        }
        if (!activeRound.getRoundId().equals(request.getRoundId())) {
            throw new SuspicionException(CODE_ROUND_MISMATCH, "环节状态已更新，请刷新页面。");
        }
        if (!suspicionMapper.existsCandidate(request.getRoundId(), request.getSuspectPlayerId())) {
            throw new SuspicionException(CODE_INVALID_CANDIDATE, "该选手暂不可选择，请刷新后重试。");
        }
        Integer existing = suspicionMapper.findSubmittedPlayerId(userId, request.getRoundId());
        if (existing != null) {
            throw new SuspicionException(CODE_ALREADY_SUBMITTED, SUBMITTED_MESSAGE);
        }

        int inserted = suspicionMapper.insertSubmission(userId, request.getRoundId(), request.getSuspectPlayerId());
        if (inserted != 1) {
            throw new SuspicionException(CODE_INVALID_CANDIDATE, "该选手暂不可选择，请刷新后重试。");
        }

        SuspicionSubmitResponse response = new SuspicionSubmitResponse();
        response.setRoundId(request.getRoundId());
        response.setSubmitted(true);
        response.setSubmittedPlayerId(request.getSuspectPlayerId());
        response.setMessage(SUBMITTED_MESSAGE);
        return response;
    }

    public boolean isSpyChannelOpen(int activeRoundId) {
        CollectState state = collectStateService.getCurrent();
        if (state == null || !MODE_SPY.equals(state.getMode())) {
            return false;
        }
        return state.getRoundId() == null || state.getRoundId().equals(activeRoundId);
    }

    private void fillRatios(List<SuspicionCandidateView> candidates, long totalCount) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (SuspicionCandidateView candidate : candidates) {
            candidate.setRatio(totalCount <= 0 ? 0.0 : ((double) candidate.getCount()) / totalCount);
        }
    }

    private long nowEpochSeconds() {
        return LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    private void validateUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new SuspicionException(CODE_NOT_OPEN, "未登录");
        }
    }
}
