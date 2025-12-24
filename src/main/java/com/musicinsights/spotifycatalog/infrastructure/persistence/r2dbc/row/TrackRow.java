package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row;

/**
 * 트랙의 기본 메타데이터를 표현하는 Row 객체입니다.
 *
 * @param trackHash   트랙을 식별하기 위한 해시 값
 * @param title       트랙 제목
 * @param durationMs  트랙 재생 시간(밀리초)
 * @param durationStr 트랙 재생 시간(문자열 형식)
 * @param genre       트랙 장르
 * @param emotion     트랙의 감정 태그
 * @param explicit    노골적인 콘텐츠 포함 여부
 * @param popularity  트랙 인기도 지표
 * @param albumId     트랙이 속한 앨범의 고유 식별자
 */
public record TrackRow(
        String trackHash,
        String title,
        Integer durationMs,
        String durationStr,
        String genre,
        String emotion,
        Boolean explicit,
        Integer popularity,
        Long albumId
) {}
