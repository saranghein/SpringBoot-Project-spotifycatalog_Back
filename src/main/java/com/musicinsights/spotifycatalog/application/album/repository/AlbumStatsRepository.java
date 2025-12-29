package com.musicinsights.spotifycatalog.application.album.repository;

import com.musicinsights.spotifycatalog.application.album.dto.response.ArtistAlbumsResponse;
import com.musicinsights.spotifycatalog.application.album.dto.response.ArtistAlbumStatsResponse;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.musicinsights.spotifycatalog.application.album.repository.AlbumStatsSql.*;

@Component
public class AlbumStatsRepository {
    private final DatabaseClient db;
    // release date = null 인거 어떻게 처리할 건지 생각 필요
    public AlbumStatsRepository(DatabaseClient db) {
        this.db = db;
    }

    public Flux<ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse> findArtistAlbumCounts(
            Integer year,
            long cursorAlbumCount,
            long cursorArtistId,
            int limit
    ) {
        DatabaseClient.GenericExecuteSpec spec;

        if (year == null) {
            spec = db.sql(SQL_FIND_ALL)
                    .bind(0, cursorAlbumCount)
                    .bind(1, cursorAlbumCount)
                    .bind(2, cursorArtistId)
                    .bind(3, limit);
        } else {
            spec = db.sql(SQL_FIND_BY_YEAR)
                    .bind(0, year)
                    .bind(1, cursorAlbumCount)
                    .bind(2, cursorAlbumCount)
                    .bind(3, cursorArtistId)
                    .bind(4, limit);
        }

        return spec.map((row, meta) -> new ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse(
                row.get("artistId", Number.class).longValue(),
                row.get("artistName", String.class),
                row.get("albumCount", Number.class).longValue()
        )).all();
    }

    public Mono<Long> countAlbums(Integer year) {
        DatabaseClient.GenericExecuteSpec spec =
                (year == null)
                        ? db.sql(SQL_COUNT_ALBUMS_ALL)
                        : db.sql(SQL_COUNT_ALBUMS_BY_YEAR).bind(0, year);

        return spec.map((row, meta) -> {
            Number n = row.get("total", Number.class);
            return (n == null) ? 0L : n.longValue();
        }).one().defaultIfEmpty(0L);
    }


    public Flux<ArtistAlbumsResponse.ArtistAlbumsItemResponse> findArtistAlbumsKeyset(
            Long artistId,
            Integer year,
            Integer cursorReleaseYear,
            Long cursorAlbumId,
            int limit
    ) {
        DatabaseClient.GenericExecuteSpec spec;

        if (year == null) {
            int cy = (cursorReleaseYear == null) ? Integer.MAX_VALUE : cursorReleaseYear;
            long cid = (cursorAlbumId == null) ? 0L : cursorAlbumId;

            spec = db.sql(AlbumStatsSql.SQL_ARTIST_ALBUMS_ALL)
                    .bind(0, artistId)
                    .bind(1, cy)
                    .bind(2, cy)
                    .bind(3, cid)
                    .bind(4, limit);
        } else {
            long cid = (cursorAlbumId == null) ? 0L : cursorAlbumId;

            spec = db.sql(AlbumStatsSql.SQL_ARTIST_ALBUMS_BY_YEAR)
                    .bind(0, artistId)
                    .bind(1, year)
                    .bind(2, cid)
                    .bind(3, limit);
        }

        return spec.map((row, meta) -> new ArtistAlbumsResponse.ArtistAlbumsItemResponse(
                row.get("albumId", Long.class),
                row.get("albumName", String.class),
                row.get("releaseYear", Integer.class)
        )).all();
    }


    public Mono<Long> countArtistAlbums(Long artistId, Integer year) {
        DatabaseClient.GenericExecuteSpec spec;

        if (year == null) {
            spec = db.sql(SQL_COUNT_ARTIST_ALBUMS_ALL)
                    .bind(0, artistId);
        } else {
            spec = db.sql(SQL_COUNT_ARTIST_ALBUMS_BY_YEAR)
                    .bind(0, artistId)
                    .bind(1, year);
        }

        return spec.map((row, meta) -> {
            Number n = row.get("total", Number.class);
            return (n == null) ? 0L : n.longValue();
        }).one().defaultIfEmpty(0L);
    }

    public Mono<Boolean> existsArtist(Long artistId) {
        return db.sql(SQL_EXISTS_ARTIST)
                .bind(0, artistId)
                .map((row, meta) -> true)
                .one()
                .defaultIfEmpty(false);
    }

}
