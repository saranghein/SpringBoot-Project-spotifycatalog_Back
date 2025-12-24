package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.TrackRow;
import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.BatchSqlSupport;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * track 테이블에 대한 배치 upsert 및 식별자 조회 기능을 제공하는 Repository입니다.
 * <p>
 * track_hash를 기준으로 트랙을 식별하며, 대량 입력 시 CHUNK 단위로 분할 처리합니다.
 */
@Component
public class TrackRepo extends BatchSqlSupport {

    /** 트랙 배치 upsert 시 한 번에 처리할 최대 행 수 */
    private static final int CHUNK = 300;

    /**
     * R2DBC {@link DatabaseClient}를 주입받아 배치 SQL 실행 기반을 초기화합니다.
     *
     * @param db R2DBC DatabaseClient
     */
    public TrackRepo(DatabaseClient db) {
        super(db);
    }

    /**
     * 트랙 목록을 배치로 upsert 합니다.
     * <p>
     * track_hash 기준 중복 키가 발생하면 메타데이터(제목/길이/장르 등)를 최신 값으로 갱신합니다.
     *
     * @param rows upsert할 트랙 목록
     * @return 영향을 받은 행 수(배치 합계)
     */
    public Mono<Long> upsert(List<TrackRow> rows) {
        return chunkedSum(rows, CHUNK, this::upsertOnce);
    }

    /**
     * 주어진 rows를 단일 INSERT ... ON DUPLICATE KEY UPDATE로 실행합니다.
     *
     * @param rows upsert할 트랙 목록(비어있지 않음)
     * @return 영향을 받은 행 수
     */
    private Mono<Long> upsertOnce(List<TrackRow> rows) {
        if (rows.isEmpty()) return Mono.just(0L);

        StringBuilder sql = new StringBuilder("""
            INSERT INTO track (
              track_hash, title, duration_ms, duration_str,
              genre, emotion, explicit, popularity, album_id
            ) VALUES
        """);

        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("""
              (:h%d, :t%d, :dm%d, :ds%d,
               :g%d, :e%d, :ex%d, :p%d, :aid%d)
            """.formatted(i, i, i, i, i, i, i, i, i));
        }

        sql.append("""
            ON DUPLICATE KEY UPDATE
              title = VALUES(title),
              duration_ms = VALUES(duration_ms),
              duration_str = VALUES(duration_str),
              genre = VALUES(genre),
              emotion = VALUES(emotion),
              explicit = VALUES(explicit),
              popularity = VALUES(popularity),
              album_id = VALUES(album_id)
        """);

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql.toString());
        for (int i = 0; i < rows.size(); i++) {
            TrackRow r = rows.get(i);
            spec = spec.bind("h" + i, r.trackHash())
                    .bind("t" + i, r.title());

            spec = bindOrNull(spec, "dm" + i, r.durationMs(), Integer.class);
            spec = bindOrNull(spec, "ds" + i, r.durationStr(), String.class);
            spec = bindOrNull(spec, "g" + i, r.genre(), String.class);
            spec = bindOrNull(spec, "e" + i, r.emotion(), String.class);
            spec = bindOrNull(spec, "ex" + i, r.explicit(), Boolean.class);
            spec = bindOrNull(spec, "p" + i, r.popularity(), Integer.class);
            spec = bindOrNull(spec, "aid" + i, r.albumId(), Long.class);
        }

        return spec.fetch().rowsUpdated();
    }

    /**
     * track_hash 목록으로 track.id를 조회하여 매핑을 반환합니다.
     * <p>
     * 입력 해시 목록은 null 제거 및 중복 제거 후 WHERE track_hash IN (...) 형태로 조회합니다.
     *
     * @param hashes 조회할 track_hash 목록
     * @return track_hash -> track.id 매핑
     */
    public Mono<Map<String, Long>> fetchTrackIdsByHash(List<String> hashes) {
        if (hashes == null || hashes.isEmpty()) {
            return Mono.just(Map.of());
        }

        // 중복 제거 + null 제거
        List<String> uniq = hashes.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 동적 IN 바인딩
        StringBuilder sql = new StringBuilder("""
        SELECT id, track_hash
        FROM track
        WHERE track_hash IN (
    """);

        for (int i = 0; i < uniq.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append(":h").append(i);
        }
        sql.append(")");

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql.toString());
        for (int i = 0; i < uniq.size(); i++) {
            spec = spec.bind("h" + i, uniq.get(i));
        }

        return spec
                .map((row, meta) -> Map.entry(
                        row.get("track_hash", String.class),
                        row.get("id", Long.class)
                ))
                .all()
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }
}
