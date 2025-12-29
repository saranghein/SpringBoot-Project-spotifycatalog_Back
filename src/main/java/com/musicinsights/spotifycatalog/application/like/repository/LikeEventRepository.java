package com.musicinsights.spotifycatalog.application.like.repository;

import com.musicinsights.spotifycatalog.application.like.dto.response.TopLikeResponse;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;


/**
 * 좋아요 이벤트(1 like = 1 row) 저장소.
 *
 * <p>track_like_event 테이블에 이벤트를 기록하고,
 * 최근 구간(window) 내 증가량 Top N을 집계한다.</p>
 */
@Component
public class LikeEventRepository {
    private final DatabaseClient db;

    public LikeEventRepository(DatabaseClient db) {
        this.db = db;
    }

    /**
     * 좋아요 이벤트 1건을 기록한다.
     *
     * @param trackId 트랙 ID
     * @return 반영된 행 수
     */
    public Mono<Long> insertEvent(long trackId) {
        String sql = """
            INSERT INTO track_like_event(track_id)
            VALUES (?)
        """;

        return db.sql(sql)
                .bind(0, trackId)
                .fetch()
                .rowsUpdated()
                ;
    }

    /**
     * 최근 windowMinutes 동안 좋아요 증가량 Top N을 조회한다.
     *
     * @param windowMinutes 집계 시간(분)
     * @param limit         조회 개수
     * @return 증가량 집계 결과
     */
    public Flux<TopLikeResponse> findTopIncreased(int windowMinutes, int limit) {
        String sql = """
        SELECT e.track_id,
               t.title AS track_title,
               e.inc_count,
               GROUP_CONCAT(DISTINCT a.name ORDER BY a.name SEPARATOR ', ') AS artist_names
        FROM (
            SELECT track_id, COUNT(*) AS inc_count
            FROM track_like_event
            WHERE created_at >= ?
            GROUP BY track_id
        ) e
        JOIN track t ON t.id = e.track_id
        LEFT JOIN track_artist ta ON ta.track_id = t.id
        LEFT JOIN artist a ON a.id = ta.artist_id
        GROUP BY e.track_id, t.title, e.inc_count
        ORDER BY e.inc_count DESC
        LIMIT ?
    """;

        LocalDateTime from = LocalDateTime.now().minusMinutes(windowMinutes);

        return db.sql(sql)
                .bind(0, from)
                .bind(1, limit)
                .map((row, meta) -> new TopLikeResponse(
                        row.get("track_id", Long.class),
                        row.get("inc_count", Long.class),
                        row.get("track_title", String.class),
                        row.get("artist_names", String.class)
                ))
                .all();
    }


}
