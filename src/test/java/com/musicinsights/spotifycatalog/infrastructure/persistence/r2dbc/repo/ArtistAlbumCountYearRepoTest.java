package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ArtistAlbumCountYearRepo} 통합 테스트.
 *
 * <p>artist_album_count_year 집계 테이블에 대해 rebuild 동작을 검증한다.</p>
 */
@SpringBootTest
@DisplayName("artist_album_count_year repo 테스트")
class ArtistAlbumCountYearRepoTest {

    @Autowired
    ArtistAlbumCountYearRepo repo;

    @Autowired
    DatabaseClient db;

    @BeforeEach
    void clean() {
        StepVerifier.create(db.sql("DELETE FROM artist_album_count_year").fetch().rowsUpdated())
                .expectNextCount(1).verifyComplete();

        StepVerifier.create(db.sql("DELETE FROM album_artist").fetch().rowsUpdated())
                .expectNextCount(1).verifyComplete();

        StepVerifier.create(db.sql("DELETE FROM album").fetch().rowsUpdated())
                .expectNextCount(1).verifyComplete();

        StepVerifier.create(db.sql("DELETE FROM artist").fetch().rowsUpdated())
                .expectNextCount(1).verifyComplete();
    }

    /**
     * rebuild 실행 시:
     * <ul>
     *   <li>집계 테이블을 비운 뒤(TRUNCATE/DELETE) 다시 집계를 생성한다.</li>
     *   <li>집계 기준은 album_artist 조인 + album.release_year(=release_date의 year)이다.</li>
     *   <li>release_year가 null인 앨범은 집계에서 제외된다.</li>
     * </ul>
     */
    @Test
    @DisplayName("rebuild는 TRUNCATE 후 album_artist + album.release_year 기준으로 집계를 생성한다")
    void rebuild_createsAggregates_andExcludesNullReleaseYear() {
        // given: artist 2명
        long a1 = seedArtist("IU");
        long a2 = seedArtist("BTS");

        // album 3개: 2020 2개 + release_date null 1개
        long al2020a = seedAlbum("ak-2020-a", "Album-2020-A", LocalDate.of(2020, 1, 1));
        long al2020b = seedAlbum("ak-2020-b", "Album-2020-B", LocalDate.of(2020, 6, 1));
        long alNull  = seedAlbum("ak-null",   "Album-NULL",   null);

        // album_artist 매핑
        seedAlbumArtist(al2020a, a1);
        seedAlbumArtist(al2020b, a1);  // IU는 2020년에 앨범 2개
        seedAlbumArtist(al2020a, a2);  // BTS는 2020년에 앨범 1개
        seedAlbumArtist(alNull,  a1);  // release_year null -> 집계 제외되어야 함

        // when
        StepVerifier.create(repo.rebuild())
                .assertNext(updated -> assertTrue(updated >= 2)) // row 수(INSERT된 집계 행 수)
                .verifyComplete();

        // then: 집계 테이블에 (2020, IU)=2, (2020, BTS)=1 총 2행
        StepVerifier.create(countAggRows())
                .expectNext(2L)
                .verifyComplete();

        StepVerifier.create(getAlbumCount(2020, a1))
                .expectNext(2L)
                .verifyComplete();

        StepVerifier.create(getAlbumCount(2020, a2))
                .expectNext(1L)
                .verifyComplete();

        // release_year null은 없어야 함 (아예 집계 행이 생성되지 않음)
        StepVerifier.create(countNullYearAgg())
                .expectNext(0L)
                .verifyComplete();
    }

    /**
     * rebuild 실행 시 기존 집계 데이터가 있어도 제거 후 새 집계로 대체되는지 검증한다.
     */
    @Test
    @DisplayName("rebuild는 기존 집계 데이터가 있어도 TRUNCATE로 제거 후 새 집계로 대체한다")
    void rebuild_truncatesOldData_andReplaces() {
        // given: FK 만족하는 artist 먼저 심기
        long dummyArtistId = seedArtist("DUMMY");

        // 집계 테이블에 '유효한' 더미 데이터 1행 미리 넣기 (FK OK)
        StepVerifier.create(
                db.sql("INSERT INTO artist_album_count_year(release_year, artist_id, album_count) VALUES(?, ?, ?)")
                        .bind(0, 1999)
                        .bind(1, dummyArtistId)
                        .bind(2, 777L)
                        .fetch()
                        .rowsUpdated()
        ).expectNext(1L).verifyComplete();

        StepVerifier.create(countAggRows())
                .expectNext(1L)
                .verifyComplete();

        // 실제 데이터(간단히 1명 1앨범 1매핑)
        long artistId = seedArtist("IU");
        long albumId  = seedAlbum("ak-2020", "Album2020", LocalDate.of(2020, 1, 1));
        seedAlbumArtist(albumId, artistId);

        // when
        StepVerifier.create(repo.rebuild())
                .assertNext(updated -> assertTrue(updated >= 1))
                .verifyComplete();

        // then: 1999 더미 행은 사라지고, 2020 집계만 남아야 함
        StepVerifier.create(countAggRows())
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(getAlbumCount(1999, dummyArtistId))
                .expectNext(0L)
                .verifyComplete();

        StepVerifier.create(getAlbumCount(2020, artistId))
                .expectNext(1L)
                .verifyComplete();
    }

    /**
     * artist_album_count_year 테이블의 총 row 수를 조회한다.
     *
     * @return 집계 테이블 row count
     */
    private Mono<Long> countAggRows() {
        return db.sql("SELECT COUNT(*) AS c FROM artist_album_count_year")
                .map((row, meta) -> row.get("c", Long.class))
                .one();
    }

    /**
     * release_year가 null인 집계 행 개수를 조회한다.
     *
     * @return release_year is null row count
     */
    private Mono<Long> countNullYearAgg() {
        return db.sql("SELECT COUNT(*) AS c FROM artist_album_count_year WHERE release_year IS NULL")
                .map((row, meta) -> row.get("c", Long.class))
                .one();
    }

    /**
     * 특정 연도/아티스트에 대한 집계값(album_count)을 조회한다.
     * <p>
     * 해당 row가 없으면 0을 반환한다.
     *
     * @param year     release_year
     * @param artistId artist_id
     * @return album_count (없으면 0)
     */
    private Mono<Long> getAlbumCount(int year, long artistId) {
        // row 없으면 0
        return db.sql("""
                SELECT COALESCE(MAX(album_count), 0) AS c
                FROM artist_album_count_year
                WHERE release_year = ? AND artist_id = ?
                """)
                .bind(0, year)
                .bind(1, artistId)
                .map((row, meta) -> row.get("c", Long.class))
                .one();
    }

    /**
     * artist 테이블에 1건을 삽입하고 생성된 id를 조회해 반환한다.
     *
     * @param name artist 이름
     * @return 삽입된 artist id
     */
    private long seedArtist(String name) {
        String key = com.musicinsights.spotifycatalog.infrastructure.input.ndjson.NormalizeUtils.artistKey(name);

        StepVerifier.create(
                db.sql("INSERT INTO artist(name, name_key) VALUES(?, ?)")
                        .bind(0, name)
                        .bind(1, key)
                        .fetch()
                        .rowsUpdated()
        ).expectNext(1L).verifyComplete();

        Long id = db.sql("SELECT id FROM artist WHERE name_key=?")
                .bind(0, key)
                .map((row, meta) -> row.get("id", Long.class))
                .one()
                .block();

        assertNotNull(id);
        return id;
    }

    /**
     * album 테이블에 1건을 삽입하고 생성된 id를 조회해 반환한다.
     * <p>
     * releaseDate가 null이면 bindNull로 처리한다.
     *
     * @param albumKey    앨범 식별 키(album_key)
     * @param name        앨범 이름(name)
     * @param releaseDate 발매일(release_date), null 가능
     * @return 삽입된 album id
     */
    private long seedAlbum(String albumKey, String name, LocalDate releaseDate) {
        DatabaseClient.GenericExecuteSpec spec =
                db.sql("INSERT INTO album(album_key, name, release_date) VALUES(?, ?, ?)")
                        .bind(0, albumKey)
                        .bind(1, name);

        if (releaseDate == null) spec = spec.bindNull(2, LocalDate.class);
        else spec = spec.bind(2, releaseDate);

        StepVerifier.create(spec.fetch().rowsUpdated())
                .expectNext(1L)
                .verifyComplete();

        Long id = db.sql("SELECT id FROM album WHERE album_key=?")
                .bind(0, albumKey)
                .map((row, meta) -> row.get("id", Long.class))
                .one()
                .block();

        assertNotNull(id);
        return id;
    }

    /**
     * album_artist 조인 테이블에 (album_id, artist_id) 1건을 삽입한다.
     *
     * @param albumId  album.id
     * @param artistId artist.id
     */
    private void seedAlbumArtist(long albumId, long artistId) {
        StepVerifier.create(
                db.sql("INSERT INTO album_artist(album_id, artist_id) VALUES(?, ?)")
                        .bind(0, albumId)
                        .bind(1, artistId)
                        .fetch()
                        .rowsUpdated()
        ).expectNext(1L).verifyComplete();
    }
}
