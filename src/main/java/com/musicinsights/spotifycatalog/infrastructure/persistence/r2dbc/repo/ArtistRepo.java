package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.IngestSeeds;
import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.NormalizeUtils;
import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.BatchSqlSupport;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * artist 테이블에 대한 배치 저장/조회 기능을 제공하는 Repository입니다.
 * <p>
 * 대량 입력 시 CHUNK 단위로 분할 처리하며, 중복(유니크/PK 충돌) 발생 시 무시하도록 설계되어 있습니다.
 */
@Component
public class ArtistRepo extends BatchSqlSupport {

    /** 아티스트 배치 처리 시 한 번에 처리할 최대 건수 */
    private static final int CHUNK = 500;

    /**
     * R2DBC {@link DatabaseClient}를 주입받아 배치 SQL 실행 기반을 초기화합니다.
     *
     * @param db R2DBC DatabaseClient
     */
    public ArtistRepo(DatabaseClient db) {
        super(db);
    }

    /**
     * 아티스트 이름 목록을 배치로 저장합니다(중복 시 무시).
     *
     * @param artistNames 저장할 아티스트 이름 목록
     * @return 영향을 받은 행 수(배치 합계)
     */
    public Mono<Long> insertIgnore(List<String> artistNames) {
        return chunkedSum(artistNames, CHUNK, this::insertOnce);
    }

    /**
     * 주어진 이름 목록을 단일 INSERT 문으로 실행합니다.
     * <p>
     * 중복 키 충돌 시 UPDATE를 수행하지 않는 방식으로 무시합니다.
     *
     * @param names 저장할 아티스트 이름 목록(비어있지 않음)
     * @return 영향을 받은 행 수
     */
    private Mono<Long> insertOnce(List<String> names) {
        if (names.isEmpty()) return Mono.just(0L);

        StringBuilder sql = new StringBuilder("""
            INSERT INTO artist (name) VALUES
        """);

        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("(:n").append(i).append(")");
        }

        sql.append("""
            ON DUPLICATE KEY UPDATE
              name = name
        """);

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql.toString());
        for (int i = 0; i < names.size(); i++) {
            spec = spec.bind("n" + i, names.get(i));
        }

        return spec.fetch().rowsUpdated();
    }

    /**
     * 아티스트 이름으로 artist.id를 조회하여 매핑을 반환합니다.
     * <p>
     * 입력 names는 null 제거 및 중복 제거 후 WHERE name IN (...) 형태로 한 번에 조회합니다.
     *
     * @param names 조회할 아티스트 이름 목록
     * @return name -> artist.id 매핑
     */
    public Mono<Map<String, Long>> fetchArtistIdsByName(List<String> names) {
        if (names == null || names.isEmpty()) {
            return Mono.just(Map.of());
        }

        List<String> uniq = names.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        StringBuilder sql = new StringBuilder("""
        SELECT id, name
        FROM artist
        WHERE name IN (
    """);

        for (int i = 0; i < uniq.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append(":n").append(i);
        }
        sql.append(")");

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql.toString());
        for (int i = 0; i < uniq.size(); i++) {
            spec = spec.bind("n" + i, uniq.get(i));
        }

        return spec
                .map((row, meta) -> Map.entry(
                        row.get("name", String.class),
                        row.get("id", Long.class)
                ))
                .all()
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    /**
     * (name_key, name) 배치 insert (중복 key는 무시)
     */
    public Mono<Long> insertIgnoreByKey(List<IngestSeeds.ArtistSeed> seeds) {
        return chunkedSum(seeds, CHUNK, this::insertOnceByKey);
    }

    private Mono<Long> insertOnceByKey(List<IngestSeeds.ArtistSeed> seeds) {
        if (seeds == null || seeds.isEmpty()) return Mono.just(0L);

        List<IngestSeeds.ArtistSeed> safe = seeds.stream()
                .filter(s -> s != null && s.key() != null && s.name() != null)
                .toList();
        if (safe.isEmpty()) return Mono.just(0L);

        StringBuilder sql = new StringBuilder("""
            INSERT INTO artist (name_key, name) VALUES
        """);

        for (int i = 0; i < safe.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("(:k").append(i).append(", :n").append(i).append(")");
        }

        sql.append("""
            ON DUPLICATE KEY UPDATE
              name = name
        """);

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql.toString());
        for (int i = 0; i < safe.size(); i++) {
            IngestSeeds.ArtistSeed s = safe.get(i);
            spec = spec.bind("k" + i, s.key())
                    .bind("n" + i, NormalizeUtils.norm(s.name()));
        }

        return spec.fetch().rowsUpdated();
    }

    /**
     * name_key IN (...) 로 id 매핑 조회
     * @return name_key -> artist.id
     */
    public Mono<Map<String, Long>> fetchArtistIdsByKey(List<String> keys) {
        if (keys == null || keys.isEmpty()) return Mono.just(Map.of());

        List<String> uniq = keys.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (uniq.isEmpty()) return Mono.just(Map.of());

        StringBuilder sql = new StringBuilder("""
            SELECT id, name_key
            FROM artist
            WHERE name_key IN (
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
                        row.get("name_key", String.class),
                        row.get("id", Long.class)
                ))
                .all()
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }
}
