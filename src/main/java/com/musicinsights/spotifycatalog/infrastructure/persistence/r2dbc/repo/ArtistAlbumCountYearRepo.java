package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.BatchSqlSupport;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ArtistAlbumCountYearRepo extends BatchSqlSupport {


    /**
     * {@link DatabaseClient}를 주입받아 초기화합니다.
     *
     * @param db R2DBC DatabaseClient
     */
    protected ArtistAlbumCountYearRepo(DatabaseClient db) {
        super(db);
    }

    public Mono<Long> rebuild() {

        return db.sql("TRUNCATE TABLE artist_album_count_year")
                .fetch().rowsUpdated()
                .then(
                        db.sql("""
                            INSERT INTO artist_album_count_year(release_year, artist_id, album_count)
                            SELECT
                              al.release_year,
                              aa.artist_id,
                              COUNT(*) AS album_count
                            FROM album_artist aa
                            JOIN album al ON al.id = aa.album_id
                            WHERE al.release_year IS NOT NULL
                            GROUP BY al.release_year, aa.artist_id
                        """)
                                .fetch().rowsUpdated()

                );
    }
}
