package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.TrackArtistRow;
import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.BatchSqlSupport;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * track_artist(트랙-아티스트 조인 테이블) 배치 저장을 담당하는 Repository입니다.
 * <p>
 * 매핑 데이터가 대량으로 생성될 수 있어 CHUNK 단위로 분할 처리하며,
 * PK 중복 발생 시에는 무시(INSERT IGNORE 유사)하도록 구현되어 있습니다.
 */
@Component
public class TrackArtistRepo extends BatchSqlSupport {

    /** 트랙-아티스트 매핑 배치 처리 시 한 번에 처리할 최대 행 수 */
    private static final int CHUNK = 800;

    /**
     * R2DBC {@link DatabaseClient}를 주입받아 배치 SQL 실행 기반을 초기화합니다.
     *
     * @param db R2DBC DatabaseClient
     */
    public TrackArtistRepo(DatabaseClient db) {
        super(db);
    }

    /**
     * 트랙-아티스트 매핑을 배치로 저장합니다.
     * <p>
     * PK 중복이 발생하면 해당 행은 업데이트 없이 무시됩니다.
     *
     * @param rows 저장할 매핑 목록
     * @return 영향을 받은 행 수(배치 합계)
     */
    public Mono<Long> insertIgnore(List<TrackArtistRow> rows) {
        return chunkedSum(rows, CHUNK, this::insertOnce);
    }

    /**
     * 주어진 rows를 단일 INSERT 문으로 실행합니다.
     *
     * @param rows 저장할 매핑 목록(비어있지 않음)
     * @return 영향을 받은 행 수
     */
    private Mono<Long> insertOnce(List<TrackArtistRow> rows) {
        if (rows.isEmpty()) return Mono.just(0L);

        StringBuilder sql = new StringBuilder("""
            INSERT INTO track_artist (track_id, artist_id) VALUES
        """);

        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("(:t").append(i).append(", :a").append(i).append(")");
        }

        sql.append("""
            ON DUPLICATE KEY UPDATE
              track_id = track_id
        """);

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql.toString());
        for (int i = 0; i < rows.size(); i++) {
            TrackArtistRow r = rows.get(i);
            spec = spec.bind("t" + i, r.trackId())
                    .bind("a" + i, r.artistId());
        }

        return spec.fetch().rowsUpdated();
    }
}
