package com.musicinsights.spotifycatalog.application.like.dto.response;

/**
 * 좋아요 증가/조회 응답 DTO.
 *
 * @param trackId   트랙 ID
 * @param likeCount 현재 좋아요 수
 */
public record LikeResponse(long trackId, long likeCount) {}
