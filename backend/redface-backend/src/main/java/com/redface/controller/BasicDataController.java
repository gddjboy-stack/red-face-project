package com.redface.controller;

import com.redface.api.ApiResponse;
import com.redface.dto.BasicDataRequests;
import com.redface.dto.BasicDataViews;
import com.redface.service.BasicDataService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C19 基础数据管理 Admin API。仅管理 players、teams、rounds、player_round 四类静态基础数据。
 */
@RestController
@RequestMapping("/api/admin")
public class BasicDataController {
    private final BasicDataService basicDataService;

    public BasicDataController(BasicDataService basicDataService) {
        this.basicDataService = basicDataService;
    }

    @GetMapping("/players")
    public ApiResponse<List<BasicDataViews.PlayerView>> listPlayers() {
        return ApiResponse.success(basicDataService.listPlayers());
    }

    @PostMapping("/players")
    public ApiResponse<BasicDataViews.PlayerView> createPlayer(@RequestBody BasicDataRequests.CreatePlayerRequest request) {
        return ApiResponse.success(basicDataService.createPlayer(request));
    }

    @GetMapping("/teams")
    public ApiResponse<List<BasicDataViews.TeamView>> listTeams() {
        return ApiResponse.success(basicDataService.listTeams());
    }

    @PostMapping("/teams")
    public ApiResponse<BasicDataViews.TeamView> createTeam(@RequestBody BasicDataRequests.CreateTeamRequest request) {
        return ApiResponse.success(basicDataService.createTeam(request));
    }

    @GetMapping("/rounds")
    public ApiResponse<List<BasicDataViews.RoundView>> listRounds() {
        return ApiResponse.success(basicDataService.listRounds());
    }

    @PostMapping("/rounds")
    public ApiResponse<BasicDataViews.RoundView> createRound(@RequestBody BasicDataRequests.CreateRoundRequest request) {
        return ApiResponse.success(basicDataService.createRound(request));
    }

    @PutMapping("/rounds/{roundId}/status")
    public ApiResponse<BasicDataViews.RoundView> updateRoundStatus(@PathVariable int roundId,
                                                                   @RequestBody BasicDataRequests.UpdateRoundStatusRequest request) {
        return ApiResponse.success(basicDataService.updateRoundStatus(roundId, request));
    }

    @GetMapping("/player-round")
    public ApiResponse<List<BasicDataViews.PlayerRoundView>> listPlayerRounds(@RequestParam int roundId) {
        return ApiResponse.success(basicDataService.listPlayerRounds(roundId));
    }

    @PostMapping("/player-round")
    public ApiResponse<BasicDataViews.PlayerRoundView> upsertPlayerRound(@RequestBody BasicDataRequests.PlayerRoundRequest request) {
        return ApiResponse.success(basicDataService.upsertPlayerRound(request));
    }
}
