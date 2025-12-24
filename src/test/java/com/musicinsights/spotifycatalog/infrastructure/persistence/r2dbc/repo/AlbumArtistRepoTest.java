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
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AlbumArtistRepo}에 대한 통합 테스트.
 *
 * <p>MySQL(R2DBC)과 실제 쿼리 실행을 통해 album-artist 매핑 테이블에 대한
 * insertIgnore 동작(신규/중복/청크 분할)을 검증한다.</p>
 */
@SpringBootTest
@DisplayName("album_artist repo 테스트")
class AlbumArtistRepoTest {
    @Autowired
    AlbumArtistRepo repo;
    @Autowired
    DatabaseClient db;

    /**
     * 각 테스트 실행 전 DB 상태를 초기화한다.
     *
     * <p>FK 제약을 고려하여 자식 테이블(album_artist) → 부모 테이블(album, artist) 순서로 삭제한다.</p>
     */
    @BeforeEach
    void clean() {
        // FK 때문에 자식 -> 부모 순서로 정리
        StepVerifier.create(db.sql("DELETE FROM album_artist").fetch().rowsUpdated())
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(db.sql("DELETE FROM album").fetch().rowsUpdated())
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(db.sql("DELETE FROM artist").fetch().rowsUpdated())
                .expectNextCount(1)
                .verifyComplete();
    }

    /**
     * 신규 매핑 데이터를 insertIgnore로 넣었을 때 실제로 행이 삽입되는지 검증한다.
     *
     * <p>부모(artist, album)를 먼저 시딩하여 FK를 만족시키고,
     * insert 후 COUNT(*)로 최종 삽입 건수를 확인한다.</p>
     */
    @Test
    @DisplayName("신규 매핑 데이터를 insertIgnore로 넣었을 때 행 삽입 테스트")
    void insertIgnore_insertsNewRows() {
        // given: FK 만족시키기 위해 부모 먼저 insert
        seedArtist(1001L, "artist-a");
        seedArtist(1002L, "artist-b");
        seedAlbum(2001L, "album-1", LocalDate.of(2020, 1, 1));
        seedAlbum(2002L, "album-2", LocalDate.of(2021, 6, 10));

        List<AlbumArtistRow> rows = List.of(
                new AlbumArtistRow(2001L, 1001L),
                new AlbumArtistRow(2001L, 1002L),
                new AlbumArtistRow(2002L, 1001L)
        );

        // when
        StepVerifier.create(repo.insertIgnore(rows))
                .assertNext(updated -> Assertions.assertTrue(updated >= 3)) // 신규 insert는 보통 3
                .verifyComplete();

        // then: 실제 row 수로 검증
        StepVerifier.create(countAlbumArtist())
                .expectNext(3L)
                .verifyComplete();
    }

    /**
     * 동일 PK(중복 매핑)를 다시 insertIgnore로 넣었을 때 행 수가 증가하지 않는지 검증한다.
     *
     * <p>이미 존재하는 (album_id, artist_id) 조합을 재삽입해도 최종 COUNT는 동일해야 한다.
     * (rowsUpdated 값은 DB/드라이버 설정에 따라 0 또는 1로 달라질 수 있어 COUNT로 검증한다.)</p>
     */
    @Test
    @DisplayName("동일 PK를 다시 insertIgnore로 넣었을 때 행 수가 증가하지 않는지 검증")
    void insertIgnore_duplicatePk_isIgnored_rowCountDoesNotIncrease() {
        seedArtist(1001L, "artist-a");
        seedArtist(1002L, "artist-b");
        seedAlbum(2001L, "album-1", LocalDate.of(2020, 1, 1));

        List<AlbumArtistRow> rows = List.of(
                new AlbumArtistRow(2001L, 1001L),
                new AlbumArtistRow(2001L, 1002L)
        );

        StepVerifier.create(repo.insertIgnore(rows)).expectNextCount(1).verifyComplete();
        StepVerifier.create(countAlbumArtist()).expectNext(2L).verifyComplete();

        // 중복 입력(동일 PK)
        StepVerifier.create(repo.insertIgnore(rows))
                .expectNextCount(1)
                .verifyComplete();

        // ON DUPLICATE KEY UPDATE album_id=album_id 이라서 rowsUpdated는 환경 따라 0/1로 달라질 수 있음

        StepVerifier.create(countAlbumArtist())
                .expectNext(2L)
                .verifyComplete();
    }

    /**
     * 입력이 CHUNK 크기를 초과할 때 chunkedSum이 여러 번 나눠 실행되어도 정상 삽입되는지 검증한다.
     *
     * <p>CHUNK=800 기준으로 801건을 넣어 두 번 이상 쿼리가 수행되는 시나리오를 만든다.</p>
     */
    @Test
    @DisplayName("입력이 CHUNK 크기를 초과할 때 chunkedSum이 여러 번 나눠 실행되어도 정상 삽입되는지 검증")
    void insertIgnore_overChunkSize_stillWorks() {
        // CHUNK=800 + 1을 넣어 chunkedSum이 실제로 쪼개서 수행되는지 검증
        seedAlbum(2001L, "album-1", LocalDate.of(2020, 1, 1));

        int n = 801;
        List<AlbumArtistRow> rows = new ArrayList<>(n);

        // artist를 801명 만들어 FK 만족
        for (int i = 0; i < n; i++) {
            long artistId = 1000L + i;
            seedArtist(artistId, "artist-" + artistId);
            rows.add(new AlbumArtistRow(2001L, artistId));
        }

        StepVerifier.create(repo.insertIgnore(rows))
                .assertNext(updated -> Assertions.assertTrue(updated >= 801))
                .verifyComplete();

        StepVerifier.create(countAlbumArtist())
                .expectNext(801L)
                .verifyComplete();
    }

    // helpers
    /**
     * 테스트에 필요한 artist 레코드를 1건 삽입한다.
     *
     * @param id   artist PK
     * @param name artist name
     */
    private void seedArtist(long id, String name) {
        StepVerifier.create(
                        db.sql("INSERT INTO artist(id, name) VALUES(?, ?)")
                                .bind(0, id)
                                .bind(1, name)
                                .fetch()
                                .rowsUpdated()
                )
                .expectNext(1L)
                .verifyComplete();
    }

    /**
     * 테스트에 필요한 album 레코드를 1건 삽입한다.
     *
     * @param id          album PK
     * @param name        album name
     * @param releaseDate release date
     */
    private void seedAlbum(long id, String name, LocalDate releaseDate) {
        StepVerifier.create(
                        db.sql("INSERT INTO album(id, name, release_date) VALUES(?, ?, ?)")
                                .bind(0, id)
                                .bind(1, name)
                                .bind(2, releaseDate)
                                .fetch()
                                .rowsUpdated()
                )
                .expectNext(1L)
                .verifyComplete();
    }

    /**
     * album_artist 테이블의 총 행 수를 반환한다.
     *
     * @return album_artist 전체 건수
     */
    private Mono<Long> countAlbumArtist() {
        return db.sql("SELECT COUNT(*) AS c FROM album_artist")
                .map((row, meta) -> row.get("c", Long.class))
                .one();
    }
}