package com.musicinsights.spotifycatalog.application.album.controller;

import com.musicinsights.spotifycatalog.application.album.dto.response.*;
import com.musicinsights.spotifycatalog.application.album.service.AlbumStatsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * 앨범 통계/아티스트 앨범 목록 조회 REST 컨트롤러.
 *
 * <p>쿼리 파라미터 검증을 위해 {@link Validated}를 사용하며,
 * 서비스로 요청을 위임한다.</p>
 */
@RestController
@RequestMapping("/api/album")
@Validated
public class AlbumStatsController {
    private final AlbumStatsService service;

    public AlbumStatsController(AlbumStatsService service) {
        this.service = service;
    }

    /**
     * 아티스트별 발매 앨범 수 통계를 조회한다.
     *
     * @param year   조회 연도(없으면 전체)
     * @param cursor keyset pagination 커서
     * @param size   페이지 크기(기본 20)
     * @return 통계 결과
     */
    @GetMapping("/stats/artist")
    public Mono<ArtistAlbumStatsResponse.ArtistAlbumStatsResultResponse> getArtistAlbumStats(
            @RequestParam(required = false) @Min(1900) @Max(2100) Integer year,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size
    ) {
        return service.getArtistAlbumStats(year, cursor, size);
    }


    /**
     * 특정 아티스트의 앨범 목록을 조회한다.
     *
     * @param artistId 아티스트 ID
     * @param year     조회 연도(없으면 전체)
     * @param cursor   keyset pagination 커서
     * @param size     페이지 크기(기본 20)
     * @return 아티스트 앨범 목록 결과
     */
    @GetMapping("/stats/artist/{artistId}")
    public Mono<ArtistAlbumsResponse.ArtistAlbumsResultResponse> getArtistAlbums(
            @PathVariable @Positive Long artistId,
            @RequestParam(required = false) @Min(1900) @Max(2100) Integer year,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size
    ) {
        return service.getArtistAlbums(artistId, year, cursor, size);
    }


}
