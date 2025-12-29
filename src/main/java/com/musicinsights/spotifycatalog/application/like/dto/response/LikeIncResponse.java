package com.musicinsights.spotifycatalog.application.like.dto.response;

/**
 * 좋아요 집계 결과 row.
 *
 * @param trackId  트랙 ID
 * @param incCount 집계 구간 내 좋아요 증가량
 */
public record LikeIncResponse(
        Long trackId,
        Long incCount,
        String title,
        String artistNames
) {}
