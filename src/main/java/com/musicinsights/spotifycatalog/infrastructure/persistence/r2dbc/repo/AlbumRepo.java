package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.IngestSeeds;
import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.NormalizeUtils;
import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.BatchSqlSupport;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * album 테이블에 대한 배치 저장/조회 기능을 제공하는 Repository입니다.
 * <p>
 * 대량 입력 시 쿼리 크기 및 메모리 사용을 줄이기 위해 CHUNK 단위로 분할 처리합니다.
 */
@Component
public class AlbumRepo extends BatchSqlSupport {

    /** 앨범 배치 업서트 시 한 번에 처리할 최대 행 수 */
    private static final int CHUNK = 400;

    /**
     * R2DBC {@link DatabaseClient}를 주입받아 배치 SQL 실행 기반을 초기화합니다.
     *
     * @param db R2DBC DatabaseClient
     */
    public AlbumRepo(DatabaseClient db) {
        super(db);
    }

    /**
     * (album_key, name, release_date) 배치 upsert
     */
    public Mono<Long> upsertByKey(List<IngestSeeds.AlbumSeed> seeds) {
        return chunkedSum(seeds, CHUNK, this::upsertOnceByKey);
    }

    private Mono<Long> upsertOnceByKey(List<IngestSeeds.AlbumSeed> seeds) {
        if (seeds == null || seeds.isEmpty()) return Mono.just(0L);

        List<IngestSeeds.AlbumSeed> safe = seeds.stream()
                .filter(s -> s != null
                        && s.key() != null
                        && s.album() != null
                        && s.album().name() != null)
                .toList();
        if (safe.isEmpty()) return Mono.just(0L);

        StringBuilder sql = new StringBuilder("""
            INSERT INTO album (album_key, name, release_date) VALUES
        """);

        for (int i = 0; i < safe.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("(:k").append(i)
                    .append(", :n").append(i)
                    .append(", :rd").append(i)
                    .append(")");
        }

        // album_key가 UNIQUE이므로 중복이면 "그대로 유지"
        sql.append("""
            ON DUPLICATE KEY UPDATE
              name = name,
              release_date = release_date
        """);

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql.toString());
        for (int i = 0; i < safe.size(); i++) {
            IngestSeeds.AlbumSeed s = safe.get(i);
            spec = spec.bind("k" + i, s.key())
                    .bind("n" + i, NormalizeUtils.norm(s.album().name()));
            spec = bindOrNull(spec, "rd" + i, s.album().releaseDate(), LocalDate.class);
        }

        return spec.fetch().rowsUpdated();
    }

    /**
     * album_key IN (...) 로 id 매핑 조회
     * @return album_key -> album.id
     */
    public Mono<Map<String, Long>> fetchAlbumIdsByKey(List<String> keys) {
        if (keys == null || keys.isEmpty()) return Mono.just(Map.of());

        List<String> uniq = keys.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (uniq.isEmpty()) return Mono.just(Map.of());

        StringBuilder sql = new StringBuilder("""
            SELECT id, album_key
            FROM album
            WHERE album_key IN (
        """);
        for (int i = 0; i < uniq.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append(":k").append(i);
        }
        sql.append(")");

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql.toString());
        for (int i = 0; i < uniq.size(); i++) {
            spec = spec.bind("k" + i, uniq.get(i));
        }

        return spec
                .map((row, meta) -> Map.entry(
                        row.get("album_key", String.class),
                        row.get("id", Long.class)
                ))
                .all()
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }
}
