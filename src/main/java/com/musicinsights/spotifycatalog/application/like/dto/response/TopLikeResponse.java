package com.musicinsights.spotifycatalog.application.like.dto.response;

/**
 * 좋아요 증가량 Top 조회 응답 DTO.
 *
 * @param trackId  트랙 ID
 * @param incCount 지정 구간 내 좋아요 증가량
 */
public record TopLikeResponse(Long trackId,
                              Long incCount,
                              String title,
                              String artistNames
) {}
