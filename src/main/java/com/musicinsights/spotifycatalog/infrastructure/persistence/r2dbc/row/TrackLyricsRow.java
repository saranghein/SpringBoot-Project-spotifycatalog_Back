package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row;

/**
 * 트랙의 가사 정보를 저장하기 위한 Row 객체입니다.
 *
 * @param trackId 트랙의 고유 식별자
 * @param lyrics  트랙의 가사 내용
 */
public record TrackLyricsRow(Long trackId, String lyrics) {}
