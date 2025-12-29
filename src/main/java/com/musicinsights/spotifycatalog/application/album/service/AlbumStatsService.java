package com.musicinsights.spotifycatalog.application.album.service;

import com.musicinsights.spotifycatalog.application.album.dto.response.*;
import reactor.core.publisher.Mono;

/**
 * 앨범 통계/아티스트 앨범 목록 조회 서비스
 */
public interface AlbumStatsService {

    /**
     * 아티스트별 발매 앨범 수 통계를 조회한다.
     *
     * @param year   연도(없으면 전체)
     * @param cursor 다음 페이지 커서
     * @param size   페이지 크기
     * @return 통계 결과(총 앨범 수 + 페이지 정보)
     */
    Mono<ArtistAlbumStatsResponse.ArtistAlbumStatsResultResponse> getArtistAlbumStats(
            Integer year,
            String cursor,
            int size
    );

    /**
     * 특정 아티스트의 앨범 목록을 조회한다.
     *
     * @param artistId 아티스트 ID
     * @param year     연도(없으면 전체)
     * @param cursor   다음 페이지 커서
     * @param size     페이지 크기
     * @return 아티스트 앨범 목록 결과
     */
    Mono<ArtistAlbumsResponse.ArtistAlbumsResultResponse> getArtistAlbums(
            Long artistId,
            Integer year,
            String cursor,
            int size
    );
}
