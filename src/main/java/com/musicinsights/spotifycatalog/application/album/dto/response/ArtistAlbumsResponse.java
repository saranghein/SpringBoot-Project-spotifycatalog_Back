package com.musicinsights.spotifycatalog.application.album.dto.response;

import com.musicinsights.spotifycatalog.application.common.pagination.PageResult;

/**
 * 특정 아티스트의 앨범 목록 조회 API 응답 DTO 모음.
 */
public class ArtistAlbumsResponse {

    /**
     * keyset pagination 커서.
     *
     * <p>year가 없을 때는 (releaseYear, albumId), year가 있을 때는 (albumId) 형태로 사용한다.</p>
     */
    public static record ArtistAlbumsCursor(
            Integer releaseYear,
            Long albumId
    ) {}

    /**
     * 앨범 목록 아이템.
     *
     * @param albumId     앨범 ID
     * @param albumName   앨범 이름
     * @param releaseYear 발매 연도(없으면 null)
     */
    public static record ArtistAlbumsItemResponse(
            Long albumId,
            String albumName,
            Integer releaseYear
    ){}

    /**
     * 아티스트 앨범 목록 조회 결과.
     *
     * @param artistId    아티스트 ID
     * @param year        필터 연도(없으면 null)
     * @param totalAlbums 전체 앨범 수(필터 기준)
     * @param page        페이지 결과(items/hasNext/nextCursor)
     */
    public static record ArtistAlbumsResultResponse(
            long artistId,
            Integer year,
            long totalAlbums,
            PageResult<ArtistAlbumsItemResponse> page
    ) {}
}
