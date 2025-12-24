package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.AlbumRow;
import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.BatchSqlSupport;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
     * 앨범 목록을 배치로 upsert 합니다.
     * <p>
     * 동일 키(예: name 유니크/PK 등) 충돌 시 release_date만 갱신합니다.
     *
     * @param rows upsert할 앨범 목록
     * @return 영향을 받은 행 수(배치 합계)
     */
    public Mono<Long> upsert(List<AlbumRow> rows) {
        return chunkedSum(rows, CHUNK, this::upsertOnce);
    }

    /**
     * 주어진 rows를 단일 INSERT ... ON DUPLICATE KEY UPDATE로 실행합니다.
     *
     * @param rows upsert할 앨범 목록(비어있지 않음)
     * @return 영향을 받은 행 수
     */
    private Mono<Long> upsertOnce(List<AlbumRow> rows) {
        if (rows.isEmpty()) return Mono.just(0L);

        StringBuilder sql = new StringBuilder("""
            INSERT INTO album (name, release_date) VALUES
        """);

        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("(:n").append(i).append(", :rd").append(i).append(")");
        }

        sql.append("""
            ON DUPLICATE KEY UPDATE
              release_date = VALUES(release_date)
        """);

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql.toString());
        for (int i = 0; i < rows.size(); i++) {
            AlbumRow r = rows.get(i);
            spec = spec.bind("n" + i, r.name());
            spec = bindOrNull(spec, "rd" + i, r.releaseDate(), LocalDate.class);
        }

        return spec.fetch().rowsUpdated();
    }

    /**
     * (name, release_date) 조합으로 album.id를 조회하여 매핑을 반환합니다.
     * <p>
     * 입력 rows는 중복 제거 후 WHERE (name, release_date) IN (...) 형태로 한 번에 조회합니다.
     *
     * @param rows 조회할 앨범 키 목록
     * @return AlbumRow(키) -> album.id 매핑
     */
    public Mono<Map<AlbumRow, Long>> fetchAlbumIdsByName(List<AlbumRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Mono.just(Map.of());
        }

        // 중복 제거
        List<AlbumRow> uniq = rows.stream().distinct().toList();

        StringBuilder sql = new StringBuilder("""
        SELECT id, name, release_date
        FROM album
        WHERE (name, release_date) IN (
    """);

        for (int i = 0; i < uniq.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("(:n").append(i).append(", :rd").append(i).append(")");
        }
        sql.append(")");

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql.toString());
        for (int i = 0; i < uniq.size(); i++) {
            AlbumRow r = uniq.get(i);
            spec = spec.bind("n" + i, r.name());
            spec = bindOrNull(spec, "rd" + i, r.releaseDate(), LocalDate.class);
        }

        return spec
                .map((row, meta) -> {
                    AlbumRow key = new AlbumRow(
                            row.get("name", String.class),
                            row.get("release_date", LocalDate.class)
                    );
                    Long id = row.get("id", Long.class);
                    return Map.entry(key, id);
                })
                .all()
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

}
