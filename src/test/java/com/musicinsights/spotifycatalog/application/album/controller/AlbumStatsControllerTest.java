package com.musicinsights.spotifycatalog.application.album.controller;

import com.musicinsights.spotifycatalog.application.album.dto.response.ArtistAlbumStatsResponse;
import com.musicinsights.spotifycatalog.application.album.dto.response.ArtistAlbumsResponse;
import com.musicinsights.spotifycatalog.application.common.pagination.PageResult;
import com.musicinsights.spotifycatalog.application.album.service.AlbumStatsService;
import com.musicinsights.spotifycatalog.application.common.error.GlobalExceptionHandler;
import com.musicinsights.spotifycatalog.application.common.error.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AlbumStatsController} WebFlux 슬라이스 테스트.
 *
 * <p>요청 파라미터 전달, 응답 바디 구조, 검증 실패/예외 처리(에러 바디)를 검증한다.</p>
 */
@DisplayName("album stats controller 테스트")
@WebFluxTest(controllers = AlbumStatsController.class)
@Import(GlobalExceptionHandler.class)
class AlbumStatsControllerTest {

    @Autowired
    WebTestClient webTestClient;

    @MockitoBean
    AlbumStatsService service;

    /** 연도 없이 조회 시 200 응답과 페이지 구조를 검증한다. */
    @Test
    @DisplayName("연도 없이 조회 시 200 응답 및 페이지 구조(items/hasNext/nextCursor) 검증")
    void ok_withoutYear_returnsBody() {
        // given
        var page = new PageResult<>(
                List.of(
                        new ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse(1L, "A", 10L),
                        new ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse(2L, "B", 9L)
                ),
                true,
                "next-cursor"
        );

        var body = new ArtistAlbumStatsResponse.ArtistAlbumStatsResultResponse(
                null,
                50L,
                page
        );

        when(service.getArtistAlbumStats(isNull(), isNull(), eq(20)))
                .thenReturn(Mono.just(body));

        // when / then
        webTestClient.get()
                .uri("/api/album/stats/artist")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.year").isEmpty()
                .jsonPath("$.totalAlbums").isEqualTo(50)
                .jsonPath("$.page.items.length()").isEqualTo(2)
                .jsonPath("$.page.items[0].artistId").isEqualTo(1)
                .jsonPath("$.page.items[0].artistName").isEqualTo("A")
                .jsonPath("$.page.items[0].albumCount").isEqualTo(10)
                .jsonPath("$.page.hasNext").isEqualTo(true)
                .jsonPath("$.page.nextCursor").isEqualTo("next-cursor");

        verify(service).getArtistAlbumStats(null, null, 20);
        verifyNoMoreInteractions(service);
    }

    /** 쿼리 파라미터(year/cursor/size)가 그대로 서비스로 전달되는지 검증한다. */
    @Test
    @DisplayName("연도/커서/사이즈가 주어지면 그대로 서비스에 전달되는지 검증")
    void ok_withQueryParams_delegatesToService() {
        // given
        Integer year = 2024;
        String cursor = "abc";
        int size = 5;

        var page = new PageResult<>(
                List.of(new ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse(7L, "X", 3L)),
                false,
                null
        );

        var body = new ArtistAlbumStatsResponse.ArtistAlbumStatsResultResponse(
                year,
                123L,
                page
        );

        when(service.getArtistAlbumStats(eq(year), eq(cursor), eq(size)))
                .thenReturn(Mono.just(body));

        // when / then
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/album/stats/artist")
                        .queryParam("year", year)
                        .queryParam("cursor", cursor)
                        .queryParam("size", size)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.year").isEqualTo(2024)
                .jsonPath("$.totalAlbums").isEqualTo(123)
                .jsonPath("$.page.items.length()").isEqualTo(1)
                .jsonPath("$.page.hasNext").isEqualTo(false);

        verify(service).getArtistAlbumStats(year, cursor, size);
        verifyNoMoreInteractions(service);
    }

    /** year 검증 실패 시 400 및 VALIDATION_ERROR 에러 바디를 검증한다. */
    @Test
    @DisplayName("연도 범위가 벗어나면 400 및 VALIDATION_ERROR 에러 바디 검증")
    void badRequest_whenYearOutOfRange_returnsValidationErrorBody() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/album/stats/artist")
                        .queryParam("year", 1800) // @Min(1900) 위반
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.timestamp").value(v -> assertThat(v).isNotNull())
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.error").isEqualTo("Bad Request")
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.path").isEqualTo("/api/album/stats/artist")
                .jsonPath("$.message").value(v -> assertThat(v.toString()).contains("year"));

        verifyNoInteractions(service);
    }

    /** size 검증 실패 시 400 및 VALIDATION_ERROR 에러 바디를 검증한다. */
    @Test
    @DisplayName("사이즈 범위가 벗어나면 400 및 VALIDATION_ERROR 에러 바디 검증")
    void badRequest_whenSizeOutOfRange_returnsValidationErrorBody() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/album/stats/artist")
                        .queryParam("size", 0) // @Min(1) 위반
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.timestamp").value(v -> assertThat(v).isNotNull())
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.error").isEqualTo("Bad Request")
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.path").isEqualTo("/api/album/stats/artist")
                .jsonPath("$.message").value(v -> assertThat(v.toString()).contains("size"));

        verifyNoInteractions(service);
    }

    /** 아티스트 앨범 조회 API의 200 응답과 페이지 구조를 검증한다. */
    @Test
    @DisplayName("아티스트 앨범 조회 시 200 응답 및 페이지 구조(items/hasNext/nextCursor) 검증")
    void ok_artistAlbums_returnsBody() {
        // given
        long artistId = 10L;

        var page = new PageResult<>(
                List.of(new ArtistAlbumsResponse.ArtistAlbumsItemResponse(100L, "Album", 2020)),
                true,
                "next"
        );

        var body = new ArtistAlbumsResponse.ArtistAlbumsResultResponse(
                artistId,
                null,
                3L,
                page
        );

        when(service.getArtistAlbums(eq(artistId), isNull(), isNull(), eq(20)))
                .thenReturn(Mono.just(body));

        // when / then
        webTestClient.get()
                .uri("/api/album/stats/artist/{artistId}", artistId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.artistId").isEqualTo(10)
                .jsonPath("$.year").isEmpty()
                .jsonPath("$.totalAlbums").isEqualTo(3)
                .jsonPath("$.page.items.length()").isEqualTo(1)
                .jsonPath("$.page.items[0].albumId").isEqualTo(100)
                .jsonPath("$.page.items[0].albumName").isEqualTo("Album")
                .jsonPath("$.page.items[0].releaseYear").isEqualTo(2020)
                .jsonPath("$.page.hasNext").isEqualTo(true)
                .jsonPath("$.page.nextCursor").isEqualTo("next");

        verify(service).getArtistAlbums(artistId, null, null, 20);
        verifyNoMoreInteractions(service);
    }

    /** 서비스가 NotFoundException을 반환하면 404 및 에러 바디를 검증한다. */
    @Test
    @DisplayName("아티스트가 없으면 404 및 ARTIST_NOT_FOUND 에러 바디 검증")
    void notFound_whenServiceThrows_returnsNotFoundErrorBody() {
        // given
        long artistId = 999L;

        when(service.getArtistAlbums(eq(artistId), any(), any(), anyInt()))
                .thenReturn(Mono.error(new NotFoundException("ARTIST_NOT_FOUND", "artist not found")));

        // when / then
        webTestClient.get()
                .uri("/api/album/stats/artist/{artistId}", artistId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error").isEqualTo("Not Found")
                .jsonPath("$.path").isEqualTo("/api/album/stats/artist/999")
                .jsonPath("$.message").isEqualTo("ARTIST_NOT_FOUND")
                .jsonPath("$.code").isEqualTo("artist not found");

        verify(service).getArtistAlbums(eq(artistId), isNull(), isNull(), eq(20));
        verifyNoMoreInteractions(service);
    }

    /** path variable(artistId) 검증 실패 시 400 및 VALIDATION_ERROR 에러 바디를 검증한다. */
    @Test
    @DisplayName("아티스트 ID가 양수가 아니면 400 및 VALIDATION_ERROR 에러 바디 검증")
    void badRequest_whenArtistIdNotPositive_returnsValidationErrorBody() {
        webTestClient.get()
                .uri("/api/album/stats/artist/{artistId}", 0) // @Positive 위반
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.timestamp").value(v -> assertThat(v).isNotNull())
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.error").isEqualTo("Bad Request")
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.path").isEqualTo("/api/album/stats/artist/0")
                .jsonPath("$.message").exists();

        verifyNoInteractions(service);
    }
}
