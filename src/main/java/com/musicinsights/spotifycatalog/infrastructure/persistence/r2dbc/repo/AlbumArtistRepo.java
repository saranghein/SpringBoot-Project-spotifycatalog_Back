package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.AlbumArtistRow;
import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.BatchSqlSupport;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * album_artist(앨범-아티스트 조인 테이블) 배치 저장을 담당하는 Repository입니다.
 * <p>
 * 대량 입력 시 메모리/쿼리 크기 부담을 줄이기 위해 CHUNK 단위로 분할 처리합니다.
 * 중복(PK 충돌) 발생 시에는 무시(INSERT IGNORE 유사)하도록 설계되어 있습니다.
 */
@Component
public class AlbumArtistRepo extends BatchSqlSupport {

    /** 조인 매핑 데이터는 많이 생성되므로 배치 크기를 비교적 크게 설정합니다. */
    private static final int CHUNK = 800; // mapping은 가볍고 많이 생겨서 조금 크게

    /**
     * R2DBC {@link DatabaseClient}를 주입받아 배치 SQL 실행을 위한 기반을 초기화합니다.
     *
     * @param db R2DBC DatabaseClient
     */
    public AlbumArtistRepo(DatabaseClient db) {
        super(db);
    }

    /**
     * album-artist 매핑을 배치로 저장합니다.
     * <p>
     * PK 중복이 발생하면 해당 행은 업데이트 없이 무시됩니다.
     *
     * @param rows 저장할 매핑 목록
     * @return 영향을 받은 행 수(배치 합계)
     */
    public Mono<Long> insertIgnore(List<AlbumArtistRow> rows) {
        return chunkedSum(rows, CHUNK, this::insertOnce);
    }

    /**
     * 주어진 rows를 단일 INSERT 문으로 실행합니다.
     *
     * @param rows 저장할 매핑 목록(비어있지 않음)
     * @return 영향을 받은 행 수
     */
    private Mono<Long> insertOnce(List<AlbumArtistRow> rows) {
        if (rows.isEmpty()) return Mono.just(0L);

        StringBuilder sql = new StringBuilder("""
            INSERT INTO album_artist (album_id, artist_id) VALUES
        """);

        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("(:aid").append(i).append(", :rid").append(i).append(")");
        }

        // PK 중복이면 아무것도 안 하게
        sql.append("""
            ON DUPLICATE KEY UPDATE
              album_id = album_id
        """);

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql.toString());
        for (int i = 0; i < rows.size(); i++) {
            AlbumArtistRow r = rows.get(i);
            spec = spec.bind("aid" + i, r.albumId())
                    .bind("rid" + i, r.artistId());
        }

        return spec.fetch().rowsUpdated();
    }
}
