package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row;
/**
 * 앨범과 아티스트 간의 다대다(M:N) 관계를 나타내는 조인 테이블용 Row 객체입니다.
 *
 * @param albumId  앨범의 고유 식별자
 * @param artistId 아티스트의 고유 식별자
 */
public record AlbumArtistRow(Long albumId, Long artistId) {}

