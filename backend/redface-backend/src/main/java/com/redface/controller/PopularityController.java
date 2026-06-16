package com.redface.controller;

import com.redface.api.ApiResponse;
import com.redface.dto.PopularityBoardResponse;
import com.redface.query.PopularityBoardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-2 人气看板接口。
 */
@RestController
@RequestMapping("/api/popularity")
public class PopularityController {
    private final PopularityBoardService popularityBoardService;

    public PopularityController(PopularityBoardService popularityBoardService) {
        this.popularityBoardService = popularityBoardService;
    }

    @GetMapping("/board")
    public ApiResponse<PopularityBoardResponse> board(@RequestParam(defaultValue = "player") String tab,
                                                       @RequestParam int roundId) {
        return ApiResponse.success(popularityBoardService.getBoard(tab, roundId));
    }
}
