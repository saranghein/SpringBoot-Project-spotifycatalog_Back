package com.musicinsights.spotifycatalog.application.album.dto.response;

import com.musicinsights.spotifycatalog.application.common.pagination.PageResult;


/**
 * 아티스트별 앨범 통계 조회 API 응답 DTO 모음.
 *
 * <p>커서 기반 페이징을 위해 커서 DTO({@link ArtistAlbumStatsCursor})를 포함한다.</p>
 */
public class ArtistAlbumStatsResponse {

    /**
     * keyset pagination 커서.
     *
     * <p>정렬 키(albumCount DESC, artistId ASC)에 대응하는 커서 값이다.</p>
     */
    public static record ArtistAlbumStatsCursor(
            long albumCount,
            long artistId
    ) {}

    /**
     * 아티스트별 앨범 통계 조회 결과.
     *
     * @param year        필터 연도(없으면 null)
     * @param totalAlbums 전체 앨범 수(필터 기준)
     * @param page        페이지 결과(items/hasNext/nextCursor)
     */
    public static record ArtistAlbumStatsResultResponse(
            Integer year,
            long totalAlbums,
            PageResult<ArtistAlbumStatsItemResponse> page
    ) {}

    public static record ArtistAlbumStatsItemResponse(
            long artistId,
            String artistName,
            long albumCount
    ) {}
}
