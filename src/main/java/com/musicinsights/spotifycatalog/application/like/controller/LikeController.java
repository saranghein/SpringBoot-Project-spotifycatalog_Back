package com.musicinsights.spotifycatalog.application.like.controller;

import com.musicinsights.spotifycatalog.application.like.dto.response.LikeResponse;
import com.musicinsights.spotifycatalog.application.like.dto.response.TopLikeResponse;
import com.musicinsights.spotifycatalog.application.like.service.LikeService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 트랙 좋아요 API 컨트롤러.
 *
 * <p>좋아요 증가 및 최근 구간(window) 내 증가량 기준 Top N 조회를 제공한다.</p>
 */
@RestController
@RequestMapping("/api/track")
@Validated
public class LikeController {
    private final LikeService service;

    public LikeController(LikeService service) {
        this.service = service;
    }

    /**
     * 특정 트랙의 좋아요를 1 증가시킨다.
     *
     * @param trackId 트랙 ID(양수)
     * @return 증가 후 좋아요 수 응답
     */
    @PostMapping("/{trackId}/likes")
    public Mono<LikeResponse> like(@PathVariable @Positive long trackId) {
        return service.like(trackId);
    }

    /**
     * 최근 windowMinutes 구간에서 좋아요 증가량 기준 Top N 트랙을 조회한다.
     *
     * @param windowMinutes 집계 시간(분)
     * @param limit         조회 개수
     * @return Top N 응답 스트림
     */
    @GetMapping("/likes/top")
    public Flux<TopLikeResponse> top(
            @RequestParam(defaultValue = "60") @Min(1) @Max(1440) int windowMinutes,
            @RequestParam(defaultValue = "10") @Min(1) @Max(200) int limit
    ) {
        return service.topIncreased(windowMinutes, limit);
    }

}
