package com.musicinsights.spotifycatalog.application.like.controller;

import com.musicinsights.spotifycatalog.application.like.dto.response.LikeResponse;
import com.musicinsights.spotifycatalog.application.like.dto.response.TopLikeResponse;
import com.musicinsights.spotifycatalog.application.like.service.LikeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.when;


/**
 * {@link LikeController} WebFlux 슬라이스 테스트.
 *
 * <p>요청 파라미터/PathVariable 검증, 서비스 호출 위임, 응답 바디 매핑을 검증한다.</p>
 */
@WebFluxTest(controllers = LikeController.class)
@DisplayName("like controller 테스트")
class LikeControllerTest {

    @Autowired
    WebTestClient webTestClient;

    @MockitoBean
    LikeService likeService;

    /**
     * 좋아요 증가 API가 서비스에 trackId를 전달하고 응답을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("좋아요 증가 요청 시 trackId가 서비스로 전달되고 증가 결과가 반환되는지 검증")
    void like_ok() {
        // given
        long trackId = 10L;
        when(likeService.like(trackId))
                .thenReturn(Mono.just(new LikeResponse(trackId, 5L)));

        // when / then
        webTestClient.post()
                .uri("/api/track/{trackId}/likes", trackId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.trackId").isEqualTo(10)
                .jsonPath("$.likeCount").isEqualTo(5);

        Mockito.verify(likeService).like(trackId);
    }

    /**
     * 좋아요 증가 API에서 trackId 검증 실패 시 400을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("좋아요 증가 요청 시 trackId가 양수가 아니면 400으로 거절되는지 검증")
    void like_validationFail_trackIdMustBePositive() {
        webTestClient.post()
                .uri("/api/track/{trackId}/likes", 0)
                .exchange()
                .expectStatus().isBadRequest();

        Mockito.verifyNoInteractions(likeService);
    }

    /**
     * Top 조회 API가 쿼리 파라미터를 서비스에 전달하고 응답을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("Top 조회 요청 시 windowMinutes/limit 쿼리 파라미터가 서비스로 그대로 전달되고 응답이 반환되는지 검증")
    void top_ok() {
        // given
        int windowMinutes = 60;
        int limit = 2;

        when(likeService.topIncreased(windowMinutes, limit))
                .thenReturn(Flux.just(
                        new TopLikeResponse(1L, 10L, "Song A", "BTS, IU"),
                        new TopLikeResponse(2L, 7L, "Song B", "BTS")
                ));

        // when / then
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/track/likes/top")
                        .queryParam("windowMinutes", windowMinutes)
                        .queryParam("limit", limit)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].trackId").isEqualTo(1)
                .jsonPath("$[0].incCount").isEqualTo(10)
                .jsonPath("$[0].title").isEqualTo("Song A")
                .jsonPath("$[0].artistNames").isEqualTo("BTS, IU")
                .jsonPath("$[1].trackId").isEqualTo(2)
                .jsonPath("$[1].incCount").isEqualTo(7)
                .jsonPath("$[1].title").isEqualTo("Song B")
                .jsonPath("$[1].artistNames").isEqualTo("BTS");

        Mockito.verify(likeService).topIncreased(windowMinutes, limit);
    }

    /**
     * Top 조회 API에서 windowMinutes 검증 실패 시 400을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("Top 조회 요청 시 windowMinutes가 최소값 미만이면 400으로 거절되는지 검증")
    void top_validationFail_windowMinutesTooSmall() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/track/likes/top")
                        .queryParam("windowMinutes", 0)
                        .queryParam("limit", 10)
                        .build())
                .exchange()
                .expectStatus().isBadRequest();

        Mockito.verifyNoInteractions(likeService);
    }

    /**
     * Top 조회 API에서 limit 검증 실패 시 400을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("Top 조회 요청 시 limit이 최대값을 초과하면 400으로 거절되는지 검증")
    void top_validationFail_limitTooLarge() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/track/likes/top")
                        .queryParam("windowMinutes", 60)
                        .queryParam("limit", 201)
                        .build())
                .exchange()
                .expectStatus().isBadRequest();

        Mockito.verifyNoInteractions(likeService);
    }
}
