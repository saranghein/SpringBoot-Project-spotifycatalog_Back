package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.AlbumArtistRow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link AlbumArtistRepo} 통합 테스트 클래스.
 * <p>
 * 실제 DB(R2DBC)와 연결하여 album_artist 저장 동작(중복 무시, null 필터링 등)을 검증한다.
 */
@SpringBootTest
@DisplayName("album_artist repo 테스트")
class AlbumArtistRepoTest {
    @Autowired
    AlbumArtistRepo repo;

    @Autowired
    DatabaseClient db;

    /**
     * 각 테스트 시작 전 테이블을 정리한다.
     * <p>
     * FK 제약이 있으므로 자식 테이블부터 삭제한다.
     */
    @BeforeEach
    void clean() {
        StepVerifier.create(
                db.sql("DELETE FROM album_artist").fetch().rowsUpdated()
                        .then(db.sql("DELETE FROM track_artist").fetch().rowsUpdated()) // 혹시 있으면
                        .then(db.sql("DELETE FROM track").fetch().rowsUpdated())        // 혹시 있으면
                        .then(db.sql("DELETE FROM album").fetch().rowsUpdated())
                        .then(db.sql("DELETE FROM artist").fetch().rowsUpdated())
        ).expectNextCount(1).verifyComplete();
    }

    /**
     * 입력 리스트가 null/empty인 경우:
     * - insertIgnore 결과가 0을 반환한다.
     * - DB에 변경이 없다.
     */
    @Test
    @DisplayName("입력이 null/empty면 0을 반환하고 DB 변경이 없다")
    void insertIgnore_emptyOrNull_returns0() {
        StepVerifier.create(repo.insertIgnore(List.of()))
                .expectNext(0L)
                .verifyComplete();

        StepVerifier.create(countAlbumArtist())
                .expectNext(0L)
                .verifyComplete();

    }

    /**
     * 정상 rows를 insertIgnore하면 album_artist에 행이 삽입되는지 검증한다.
     */
    @Test
    @DisplayName("정상 rows를 insertIgnore하면 album_artist row가 삽입된다")
    void insertIgnore_insertsRows() {
        long artistId1 = seedArtist("kIU", "IU");
        long artistId2 = seedArtist("kBTS", "BTS");
        long albumId1  = seedAlbum("akA", "AlbumA", LocalDate.of(2020, 1, 1));

        List<AlbumArtistRow> rows = List.of(
                new AlbumArtistRow(albumId1, artistId1),
                new AlbumArtistRow(albumId1, artistId2)
        );

        StepVerifier.create(repo.insertIgnore(rows))
                .assertNext(updated -> Assertions.assertTrue(updated >= 2))
                .verifyComplete();

        StepVerifier.create(countAlbumArtist())
                .expectNext(2L)
                .verifyComplete();
    }

    /**
     * 동일 (album_id, artist_id) 페어를 중복 삽입해도 PK 중복이 무시되어
     * row 수가 증가하지 않는지 검증한다.
     */
    @Test
    @DisplayName("동일 (album_id, artist_id) 중복 삽입해도 row 수가 증가하지 않는다")
    void insertIgnore_duplicatePairs_doNotIncreaseCount() {
        long artistId = seedArtist("kIU", "IU");
        long albumId  = seedAlbum("akA", "AlbumA", LocalDate.of(2020, 1, 1));

        List<AlbumArtistRow> rows = List.of(
                new AlbumArtistRow(albumId, artistId),
                new AlbumArtistRow(albumId, artistId) // duplicate
        );

        StepVerifier.create(repo.insertIgnore(rows))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(countAlbumArtist())
                .expectNext(1L) // PK 중복 무시
                .verifyComplete();

        // 한 번 더 넣어도 증가 X
        StepVerifier.create(repo.insertIgnore(rows))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(countAlbumArtist())
                .expectNext(1L)
                .verifyComplete();
    }

    /**
     * null albumId/artistId가 포함된 row는 저장 대상에서 제외되고
     * 유효한 row만 삽입되는지 검증한다.
     */
    @Test
    @DisplayName("null albumId/artistId를 포함한 row는 필터링되어 스킵된다")
    void insertIgnore_filtersNullIds() {
        long artistId = seedArtist("kIU", "IU");
        long albumId  = seedAlbum("akA", "AlbumA", LocalDate.of(2020, 1, 1));

        List<AlbumArtistRow> rows = List.of(
                new AlbumArtistRow(null, artistId),
                new AlbumArtistRow(albumId, null),
                new AlbumArtistRow(albumId, artistId) // only valid
        );

        StepVerifier.create(repo.insertIgnore(rows))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(countAlbumArtist())
                .expectNext(1L)
                .verifyComplete();
    }

    /**
     * album_artist 테이블의 총 row 수를 조회한다.
     *
     * @return album_artist row count
     */
    private Mono<Long> countAlbumArtist() {
        return db.sql("SELECT COUNT(*) AS c FROM album_artist")
                .map((row, meta) -> row.get("c", Long.class))
                .one();
    }

    /**
     * artist 테이블에 1건을 삽입하고 생성된 id를 조회해 반환한다.
     *
     * @param key  artist 식별 키(name_key)
     * @param name artist 이름(name)
     * @return 삽입된 artist id
     */
    private long seedArtist(String key, String name) {
        db.sql("INSERT INTO artist(name, name_key) VALUES (?, ?)")
                .bind(0, name)
                .bind(1, key)
                .fetch()
                .rowsUpdated()
                .block();

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
        // INSERT
        DatabaseClient.GenericExecuteSpec spec =
                db.sql("INSERT INTO album(album_key, name, release_date) VALUES(?, ?, ?)")
                        .bind(0, albumKey)
                        .bind(1, name);

        if (releaseDate == null) {
            spec = spec.bindNull(2, LocalDate.class);
        } else {
            spec = spec.bind(2, releaseDate);
        }

        StepVerifier.create(spec.fetch().rowsUpdated())
                .expectNext(1L)
                .verifyComplete();

        Long id = db.sql("SELECT id FROM album WHERE album_key=?")
                .bind(0, albumKey)
                .map((row, meta) -> row.get("id", Long.class))
                .one()
                .block();

        Assertions.assertNotNull(id);
        return id;
    }

}
