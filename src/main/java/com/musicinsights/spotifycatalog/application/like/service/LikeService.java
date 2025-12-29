package com.musicinsights.spotifycatalog.application.like.service;

import com.musicinsights.spotifycatalog.application.like.dto.response.LikeResponse;
import com.musicinsights.spotifycatalog.application.like.dto.response.TopLikeResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
/**
 * 좋아요 서비스.
 *
 * <p>이벤트 로그(track_like_event) 기록과 누적 카운터(track_like_counter) 갱신을 조합한다.</p>
 */
public interface LikeService {

    /**
     * 좋아요를 1 증가시킨다.
     *
     * <p>이벤트 1건 기록 후 누적 카운터를 증가시키는 과정을 하나의 트랜잭션으로 수행한다.</p>
     *
     * @param trackId 트랙 ID
     * @return 증가 후 누적 좋아요 결과
     */
    Mono<LikeResponse> like(long trackId);

    /**
     * 특정 트랙의 누적 좋아요 수를 조회한다.
     *
     * @param trackId 트랙 ID
     * @return 누적 좋아요 수
     */
    Mono<Long> getCount(long trackId);

    /**
     * 최근 windowMinutes 구간에서 좋아요 증가량 Top N을 조회한다.
     *
     * @param windowMinutes 집계 시간(분)
     * @param limit         조회 개수
     * @return 증가량 집계 결과
     */
    Flux<TopLikeResponse> topIncreased(int windowMinutes, int limit);
}
