package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.TrackLyricsRow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link TrackLyricsRepo} 통합 테스트.
 *
 * <p>track_lyrics 테이블에 대한 upsert 동작(신규 삽입/가사 갱신/null 허용/청크 분할)을
 * 실제 R2DBC 쿼리 실행으로 검증한다.</p>
 */
@SpringBootTest
@DisplayName("track lyrics repo 테스트")
class TrackLyricsRepoTest {

    @Autowired
    TrackLyricsRepo repo;
    @Autowired
    DatabaseClient db;

    /**
     * 각 테스트 실행 전 DB 상태를 초기화한다.
     *
     * <p>FK 제약을 고려하여 자식 테이블(track_lyrics) → 부모 테이블(track) 순서로 삭제한다.</p>
     */
    @BeforeEach
    void clean() {
        // FK 때문에 자식 먼저
        StepVerifier.create(db.sql("DELETE FROM track_lyrics").fetch().rowsUpdated())
                .expectNextCount(1).verifyComplete();

        StepVerifier.create(db.sql("DELETE FROM track").fetch().rowsUpdated())
                .expectNextCount(1).verifyComplete();
    }

    /**
     * 신규 track에 대해 가사 레코드가 삽입되는지 검증한다.
     *
     * <p>삽입 후 COUNT(*)와 단건 조회 값을 비교하여 저장 결과를 확인한다.</p>
     */
    @DisplayName("신규 track에 대해 가사 레코드가 삽입되는지 검증")
    @Test
    void upsert_insertsNew() {
        seedTrack(1L);

        TrackLyricsRow r = new TrackLyricsRow(1L, "hello lyrics");

        StepVerifier.create(repo.upsert(List.of(r)))
                .assertNext(updated -> Assertions.assertTrue(updated >= 1))
                .verifyComplete();

        StepVerifier.create(countLyrics())
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(fetchLyricsByTrackId(1L))
                .expectNext("hello lyrics")
                .verifyComplete();
    }

    /**
     * 동일 track_id로 다시 upsert할 경우 lyrics 값이 갱신되는지 검증한다.
     *
     * <p>row 수는 유지되며(1개), lyrics만 최신 값으로 변경되어야 한다.</p>
     */
    @DisplayName("동일 track_id로 다시 upsert할 경우 lyrics 값이 갱신되는지 검증")
    @Test
    void upsert_sameTrackId_updatesLyrics() {
        seedTrack(1L);

        TrackLyricsRow v1 = new TrackLyricsRow(1L, "old lyrics");
        TrackLyricsRow v2 = new TrackLyricsRow(1L, "new lyrics");

        StepVerifier.create(repo.upsert(List.of(v1)))
                .expectNextCount(1).verifyComplete();

        StepVerifier.create(repo.upsert(List.of(v2)))
                .expectNextCount(1).verifyComplete();

        StepVerifier.create(fetchLyricsByTrackId(1L))
                .expectNext("new lyrics")
                .verifyComplete();

        StepVerifier.create(countLyrics())
                .expectNext(1L) // row는 1개 유지
                .verifyComplete();
    }

    /**
     * lyrics 컬럼이 null을 허용할 때, null 값이 정상 저장되는지 검증한다.
     */
    @DisplayName("lyrics 컬럼이 null을 허용할 때, null 값이 정상 저장되는지 검증")
    @Test
    void upsert_allowsNullLyrics() {
        seedTrack(1L);

        TrackLyricsRow r = new TrackLyricsRow(1L, null);

        StepVerifier.create(repo.upsert(List.of(r)))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(isLyricsNull(1L))
                .assertNext(isNull -> Assertions.assertTrue(isNull))
                .verifyComplete();
    }

    /**
     * 특정 track_id의 lyrics가 NULL인지 여부를 반환한다.
     *
     * <p>MySQL에서 boolean 표현식 결과가 0/1(Integer/Long)로 내려올 수 있어 이를 흡수한다.</p>
     *
     * @param trackId 조회할 track id
     * @return lyrics가 NULL이면 true
     */
    private Mono<Boolean> isLyricsNull(long trackId) {
        return db.sql("SELECT (lyrics IS NULL) AS is_null FROM track_lyrics WHERE track_id=?")
                .bind(0, trackId)
                // MySQL은 보통 0/1을 Integer/Long로 줌
                .map((row, meta) -> {
                    Integer v = row.get("is_null", Integer.class);
                    if (v == null) {
                        Long l = row.get("is_null", Long.class);
                        return l != null && l == 1L;
                    }
                    return v == 1;
                })
                .one();
    }

    /**
     * 입력 건수가 CHUNK 크기를 초과해도 분할 실행되어 정상 upsert 되는지 검증한다.
     *
     * <p>CHUNK=200 기준으로 201건을 넣어 두 번 이상 쿼리가 수행되는 시나리오를 만든다.</p>
     */
    @DisplayName("입력 건수가 CHUNK 크기를 초과해도 분할 실행되어 정상 upsert 되는지 검증")
    @Test
    void upsert_overChunkSize_stillWorks() {
        int n = 201; // CHUNK(200) + 1

        for (int i = 0; i < n; i++) seedTrack(1000L + i);

        List<TrackLyricsRow> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long tid = 1000L + i;
            rows.add(new TrackLyricsRow(tid, "ly-" + tid));
        }

        StepVerifier.create(repo.upsert(rows))
                .assertNext(updated -> Assertions.assertTrue(updated >= 201))
                .verifyComplete();

        StepVerifier.create(countLyrics())
                .expectNext(201L)
                .verifyComplete();
    }

    /**
     * 테스트용 track 레코드를 1건 삽입한다.
     *
     * <p>track DDL 기준 필수 컬럼(track_hash, title)은 NOT NULL 이므로 함께 세팅한다.</p>
     *
     * @param id track PK
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
                )
                .expectNext(1L)
                .verifyComplete();
    }


    /**
     * track_lyrics 테이블의 총 행 수를 반환한다.
     *
     * @return track_lyrics 전체 건수
     */
    private Mono<Long> countLyrics() {
        return db.sql("SELECT COUNT(*) AS c FROM track_lyrics")
                .map((row, meta) -> row.get("c", Long.class))
                .one();
    }

    /**
     * track_id 기준으로 lyrics 문자열을 단건 조회한다.
     *
     * @param trackId 조회할 track id
     * @return lyrics 값(없으면 empty가 아닌 Mono error/empty는 쿼리 결과에 따름)
     */
    private Mono<String> fetchLyricsByTrackId(long trackId) {
        return db.sql("SELECT lyrics FROM track_lyrics WHERE track_id=?")
                .bind(0, trackId)
                .map((row, meta) -> row.get("lyrics", String.class))
                .one();
    }


}