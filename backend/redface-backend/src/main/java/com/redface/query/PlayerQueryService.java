package com.redface.query;

import com.redface.dto.PlayerDetailResponse;
import com.redface.dto.PlayerListItem;
import com.redface.dto.PlayerListResponse;
import com.redface.dto.PlayerPhotoItem;
import com.redface.mapper.PlayerQueryMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * C15 用户端选手列表/详情只读查询服务。
 */
@Service
public class PlayerQueryService {
    private final PlayerQueryMapper playerQueryMapper;

    public PlayerQueryService(PlayerQueryMapper playerQueryMapper) {
        this.playerQueryMapper = playerQueryMapper;
    }

    public PlayerListResponse listPlayers(Integer requestedRoundId) {
        RoundContext round = resolveRound(requestedRoundId);
        List<PlayerListItem> items = playerQueryMapper.findPlayers(round.roundId());
        PlayerListResponse response = new PlayerListResponse();
        response.setRoundId(round.roundId());
        response.setRoundName(round.roundName());
        response.setItems(items);
        return response;
    }

    public PlayerDetailResponse getPlayerDetail(int playerId, Integer requestedRoundId) {
        RoundContext round = resolveRound(requestedRoundId);
        PlayerDetailResponse detail = playerQueryMapper.findPlayerDetail(playerId, round.roundId());
        if (detail == null) {
            throw new PlayerNotFoundException(playerId);
        }
        detail.setRoundId(round.roundId());
        detail.setRoundName(round.roundName());
        List<PlayerPhotoItem> photos = playerQueryMapper.findPhotosByPlayer(playerId);
        detail.setPhotos(photos);
        detail.setSupportHint("增加人气值请在直播间进行。");
        return detail;
    }

    private RoundContext resolveRound(Integer requestedRoundId) {
        if (requestedRoundId != null) {
            return new RoundContext(requestedRoundId, playerQueryMapper.findRoundName(requestedRoundId));
        }
        RoundSummary latest = playerQueryMapper.findLatestActiveRound();
        if (latest == null) {
            return new RoundContext(null, null);
        }
        return new RoundContext(latest.getRoundId(), latest.getRoundName());
    }

    private record RoundContext(Integer roundId, String roundName) {
    }

    public static class PlayerNotFoundException extends RuntimeException {
        private final int playerId;

        public PlayerNotFoundException(int playerId) {
            super("选手不存在或不可用");
            this.playerId = playerId;
        }

        public int getPlayerId() {
            return playerId;
        }
    }
}
