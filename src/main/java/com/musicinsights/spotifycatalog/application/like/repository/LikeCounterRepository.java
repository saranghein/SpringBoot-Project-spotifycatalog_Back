package com.musicinsights.spotifycatalog.application.like.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 트랙 누적 좋아요 카운터 저장소.
 *
 * <p>track_like_counter 테이블을 대상으로 좋아요 증가 및 조회 기능을 제공한다.</p>
 */
@Component
public class LikeCounterRepository {
    private final DatabaseClient db;

    public LikeCounterRepository(DatabaseClient db) {
        this.db = db;
    }

    /**
     * 누적 좋아요 수를 1 증가시키고 증가된 값을 반환한다.
     *
     * <p>UPSERT로 동시성 상황에서도 원자적으로 증가시키며,
     * LAST_INSERT_ID를 이용해 증가된 값을 조회한다.</p>
     *
     * @param trackId 트랙 ID
     * @return 증가된 좋아요 수
     */
    public Mono<Long> incrementAndGet(long trackId) {
        String upsertSql = """
            INSERT INTO track_like_counter (track_id, like_count)
            VALUES (?, LAST_INSERT_ID(1))
            ON DUPLICATE KEY UPDATE
              like_count = LAST_INSERT_ID(like_count + 1)
        """;

        return db.sql(upsertSql)
                .bind(0, trackId)
                .fetch()
                .rowsUpdated()
                .then(
                        db.sql("SELECT LAST_INSERT_ID()")
                                .map((row, meta) -> row.get(0, Long.class))
                                .one()
                );
    }

    /**
     * 특정 트랙의 누적 좋아요 수를 조회한다.
     *
     * <p>row가 없으면 0을 반환한다.</p>
     *
     * @param trackId 트랙 ID
     * @return 누적 좋아요 수(없으면 0)
     */
    public Mono<Long> findCount(long trackId) {
        String sql = """
            SELECT like_count
            FROM track_like_counter
            WHERE track_id = ?
        """;

        return db.sql(sql)
                .bind(0, trackId)
                .map((row, meta) -> row.get("like_count", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }
}
