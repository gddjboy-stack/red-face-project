package com.redface.controller;

import com.redface.api.ApiResponse;
import com.redface.dto.PlayerDetailResponse;
import com.redface.dto.PlayerListResponse;
import com.redface.query.PlayerQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C15 用户端选手列表/详情只读接口。严禁返回卧底身份字段。
 */
@RestController
@RequestMapping("/api/players")
public class PlayerController {
    private static final int PLAYER_NOT_FOUND_CODE = 40410;

    private final PlayerQueryService playerQueryService;

    public PlayerController(PlayerQueryService playerQueryService) {
        this.playerQueryService = playerQueryService;
    }

    @GetMapping
    public ApiResponse<PlayerListResponse> list(@RequestParam(required = false) Integer roundId) {
        return ApiResponse.success(playerQueryService.listPlayers(roundId));
    }

    @GetMapping("/{playerId}")
    public ResponseEntity<ApiResponse<?>> detail(@PathVariable int playerId,
                                                 @RequestParam(required = false) Integer roundId) {
        try {
            PlayerDetailResponse response = playerQueryService.getPlayerDetail(playerId, roundId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (PlayerQueryService.PlayerNotFoundException e) {
            return ResponseEntity.ok(ApiResponse.error(PLAYER_NOT_FOUND_CODE, e.getMessage(), null));
        }
    }
}
