package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.TrackLyricsRow;
import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.BatchSqlSupport;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * track_lyrics 테이블에 대한 배치 upsert 기능을 제공하는 Repository입니다.
 * <p>
 * 가사(lyrics)는 LONGTEXT로 크기가 클 수 있어, SQL 길이/메모리 부담을 줄이기 위해
 * 비교적 작은 CHUNK 단위로 분할 처리합니다.
 */
@Component
public class TrackLyricsRepo extends BatchSqlSupport {

    /** LONGTEXT 저장 특성을 고려한 배치 크기 */
    private static final int CHUNK = 200; // LONGTEXT라 chunk를 작게 (SQL 길이/메모리 부담 감소)

    /**
     * R2DBC {@link DatabaseClient}를 주입받아 배치 SQL 실행 기반을 초기화합니다.
     *
     * @param db R2DBC DatabaseClient
     */
    public TrackLyricsRepo(DatabaseClient db) {
        super(db);
    }

    /**
     * 트랙 가사 목록을 배치로 upsert 합니다.
     * <p>
     * track_id 기준 중복 키가 발생하면 lyrics 컬럼을 최신 값으로 갱신합니다.
     *
     * @param rows upsert할 트랙 가사 목록
     * @return 영향을 받은 행 수(배치 합계)
     */
    public Mono<Long> upsert(List<TrackLyricsRow> rows) {
        return chunkedSum(rows, CHUNK, this::upsertOnce);
    }

    /**
     * 주어진 rows를 단일 INSERT ... ON DUPLICATE KEY UPDATE로 실행합니다.
     *
     * @param rows upsert할 트랙 가사 목록(비어있지 않음)
     * @return 영향을 받은 행 수
     */
    private Mono<Long> upsertOnce(List<TrackLyricsRow> rows) {
        if (rows.isEmpty()) return Mono.just(0L);

        StringBuilder sql = new StringBuilder("""
            INSERT INTO track_lyrics (track_id, lyrics) VALUES
        """);

        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("(:tid").append(i).append(", :ly").append(i).append(")");
        }

        sql.append("""
            ON DUPLICATE KEY UPDATE
              lyrics = VALUES(lyrics)
        """);

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql.toString());
        for (int i = 0; i < rows.size(); i++) {
            TrackLyricsRow r = rows.get(i);
            spec = spec.bind("tid" + i, r.trackId());
            spec = bindOrNull(spec, "ly" + i, r.lyrics(), String.class);
        }

        return spec.fetch().rowsUpdated();
    }
}
