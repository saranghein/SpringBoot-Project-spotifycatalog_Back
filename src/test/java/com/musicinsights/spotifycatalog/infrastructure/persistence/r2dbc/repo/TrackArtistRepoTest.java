package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.NormalizeUtils;
import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.TrackArtistRow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link TrackArtistRepo} 통합 테스트.
 *
 * <p>track_artist 매핑 테이블에 대한 insertIgnore 동작(신규 삽입/PK 중복 무시/청크 분할)을
 * 실제 R2DBC 쿼리 실행으로 검증한다.</p>
 */
@SpringBootTest
@DisplayName("track artist repo 테스트")
class TrackArtistRepoTest {

    @Autowired
    TrackArtistRepo repo;

    @Autowired
    DatabaseClient db;

    /**
     * 각 테스트 실행 전 관련 테이블을 초기화한다.
     *
     * <p>FK 제약을 고려하여 자식(track_artist) → audio_feature → track → artist 순서로 삭제한다.</p>
     */
    @BeforeEach
    void clean() {
        StepVerifier.create(db.sql("DELETE FROM track_artist").fetch().rowsUpdated())
                .expectNextCount(1).verifyComplete();

        StepVerifier.create(db.sql("DELETE FROM audio_feature").fetch().rowsUpdated())
                .expectNextCount(1).verifyComplete();

        StepVerifier.create(db.sql("DELETE FROM track").fetch().rowsUpdated())
                .expectNextCount(1).verifyComplete();

        StepVerifier.create(db.sql("DELETE FROM artist").fetch().rowsUpdated())
                .expectNextCount(1).verifyComplete();
    }

    /**
     * 신규 track-artist 매핑이 insertIgnore로 정상 삽입되는지 검증한다.
     */
    @DisplayName("신규 track-artist 매핑이 insertIgnore로 정상 삽입되는지 검증")
    @Test
    void insertIgnore_insertsNewRows() {
        seedTrack(1L);
        seedTrack(2L);

        long iuId = seedArtist("IU");
        long btsId = seedArtist("BTS");

        List<TrackArtistRow> rows = List.of(
                new TrackArtistRow(1L, iuId),
                new TrackArtistRow(1L, btsId),
                new TrackArtistRow(2L, iuId)
        );

        StepVerifier.create(repo.insertIgnore(rows))
                .assertNext(updated -> Assertions.assertTrue(updated >= 3))
                .verifyComplete();

        StepVerifier.create(countTrackArtist())
                .expectNext(3L)
                .verifyComplete();
    }


    /**
     * 동일 PK(중복 매핑)를 다시 insertIgnore로 넣어도 PK 중복이 무시되어
     * row 수가 증가하지 않는지 검증한다.
     */
    @DisplayName("동일 PK(중복 매핑)를 다시 insertIgnore로 넣었을 때 row 수가 증가하지 않는지 검증")
    @Test
    void insertIgnore_duplicatePk_isIgnored_rowCountDoesNotIncrease() {
        seedTrack(1L);

        long iuId = seedArtist("IU");
        long btsId = seedArtist("BTS");

        List<TrackArtistRow> rows = List.of(
                new TrackArtistRow(1L, iuId),
                new TrackArtistRow(1L, btsId)
        );

        StepVerifier.create(repo.insertIgnore(rows)).expectNextCount(1).verifyComplete();
        StepVerifier.create(countTrackArtist()).expectNext(2L).verifyComplete();

        // 같은 PK들 재삽입
        StepVerifier.create(repo.insertIgnore(rows)).expectNextCount(1).verifyComplete();

        // row 수 그대로
        StepVerifier.create(countTrackArtist())
                .expectNext(2L)
                .verifyComplete();
    }

    /**
     * 입력 건수가 CHUNK 크기를 초과해도 내부적으로 분할 실행되어
     * 정상적으로 삽입되는지 검증한다.
     */
    @DisplayName("입력 건수가 CHUNK 크기를 초과해도 분할 실행되어 정상 삽입되는지 검증")
    @Test
    void insertIgnore_overChunkSize_stillWorks() {
        int n = 801; // CHUNK(800) + 1

        seedTrack(1L);

        // artist n명 생성 + id 수집
        List<Long> artistIds = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            artistIds.add(seedArtist("a-" + i));
        }

        List<TrackArtistRow> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new TrackArtistRow(1L, artistIds.get(i)));
        }

        StepVerifier.create(repo.insertIgnore(rows))
                .assertNext(updated -> Assertions.assertTrue(updated >= 801))
                .verifyComplete();

        StepVerifier.create(countTrackArtist())
                .expectNext(801L)
                .verifyComplete();
    }

    /**
     * 테스트용 track 레코드를 1건 삽입한다.
     *
     * <p>track DDL 기준 필수 컬럼(track_hash, title)은 NOT NULL 이므로 함께 세팅한다.</p>
     *
     * @param id track PK(id)
     */
    private void seedTrack(long id) {
        String hash = String.format("%064x", id); // 64 chars, UNIQUE

        StepVerifier.create(
                db.sql("INSERT INTO track(id, track_hash, title) VALUES(?, ?, ?)")
                        .bind(0, id)
                        .bind(1, hash)
                        .bind(2, "t-" + id)
                        .fetch()
                        .rowsUpdated()
        ).expectNext(1L).verifyComplete();
    }

    /**
     * 테스트용 artist 레코드를 1건 삽입하고 생성된 id를 반환한다.
     *
     * <p>artist.name_key는 NOT NULL이므로 함께 삽입한다.</p>
     *
     * @param name artist 이름
     * @return 삽입된 artist id
     */
    private long seedArtist(String name) {
        String key = NormalizeUtils.artistKey(name);

        // insert
        StepVerifier.create(
                db.sql("INSERT INTO artist(name, name_key) VALUES(?, ?)")
                        .bind(0, name)
                        .bind(1, key)
                        .fetch()
                        .rowsUpdated()
        ).expectNext(1L).verifyComplete();

        // select id
        Long id = db.sql("SELECT id FROM artist WHERE name_key=?")
                .bind(0, key)
                .map((row, meta) -> row.get("id", Long.class))
                .one()
                .block();

        Assertions.assertNotNull(id);
        return id;
    }

    /**
     * track_artist 테이블의 총 row 수를 조회한다.
     *
     * @return track_artist row count
     */
    private reactor.core.publisher.Mono<Long> countTrackArtist() {
        return db.sql("SELECT COUNT(*) AS c FROM track_artist")
                .map((row, meta) -> row.get("c", Long.class))
                .one();
    }
}
