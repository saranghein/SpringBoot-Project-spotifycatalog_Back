package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.AudioRow;
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
 * {@link AudioRepo} 통합 테스트.
 *
 * <p>audio_feature 테이블에 대한 upsert 동작(신규 삽입/갱신/null 허용/청크 분할)을
 * 실제 R2DBC 쿼리 실행으로 검증한다.</p>
 */
@SpringBootTest
@DisplayName("audio repo 테스트")
class AudioRepoTest {

    @Autowired AudioRepo repo;
    @Autowired DatabaseClient db;

    /**
     * 각 테스트 실행 전 DB 상태를 초기화한다.
     *
     * <p>FK 제약을 고려하여 자식 테이블(audio_feature) → 부모 테이블(track) 순서로 삭제한다.</p>
     */
    @BeforeEach
    void clean() {
        // FK 때문에 자식 먼저
        StepVerifier.create(db.sql("DELETE FROM audio_feature").fetch().rowsUpdated())
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(db.sql("DELETE FROM track").fetch().rowsUpdated())
                .expectNextCount(1)
                .verifyComplete();
    }

    /**
     * 신규 track에 대해 audio_feature가 삽입되는지 검증한다.
     *
     * <p>삽입 후 COUNT(*)와 단건 조회 값을 비교하여 저장된 컬럼 값이 기대값과 일치하는지 확인한다.</p>
     */
    @Test
    @DisplayName("신규 track에 대해 audio_feature가 삽입되는지 검증")
    void upsertAudioFeatures_insertsNew() {
        seedTrack(1L);

        AudioRow r = new AudioRow(
                1L,
                120.5,
                -5.2,
                80,
                70,
                60,
                10,
                15,
                20,
                0,
                "C",
                "4/4"
        );

        StepVerifier.create(repo.upsertAudioFeatures(List.of(r)))
                .assertNext(updated -> Assertions.assertTrue(updated >= 1))
                .verifyComplete();

        StepVerifier.create(countAudio())
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(fetchAudioByTrackId(1L))
                .assertNext(saved -> {
                    Assertions.assertEquals(120.5, saved.tempo());
                    Assertions.assertEquals(-5.2, saved.loudness());
                    Assertions.assertEquals(80, saved.energy());
                    Assertions.assertEquals(70, saved.danceability());
                    Assertions.assertEquals(60, saved.positiveness());
                    Assertions.assertEquals(10, saved.speechiness());
                    Assertions.assertEquals(15, saved.liveness());
                    Assertions.assertEquals(20, saved.acousticness());
                    Assertions.assertEquals(0, saved.instrumentalness());
                    Assertions.assertEquals("C", saved.musicalKey());
                    Assertions.assertEquals("4/4", saved.timeSignature());
                })
                .verifyComplete();
    }
    /**
     * 동일 track_id로 다시 upsert할 경우 기존 레코드가 갱신되는지 검증한다.
     *
     * <p>v1 저장 후 v2로 재-upsert하고, 조회 결과가 v2 값으로 바뀌었는지 확인한다.</p>
     */
    @DisplayName("동일 track_id로 다시 upsert할 경우 기존 레코드가 갱신되는지 검증")
    @Test
    void upsertAudioFeatures_sameTrackId_updatesValues() {
        seedTrack(1L);

        AudioRow v1 = new AudioRow(
                1L,
                100.0, -10.0,
                10, 20, 30,
                40, 50, 60,
                70, "A", "3/4"
        );

        AudioRow v2 = new AudioRow(
                1L,
                150.0, -3.0,
                90, 80, 70,
                60, 50, 40,
                30, "G#", "4/4"
        );

        StepVerifier.create(repo.upsertAudioFeatures(List.of(v1)))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(repo.upsertAudioFeatures(List.of(v2)))
                .expectNextCount(1)
                .verifyComplete();

        // 실제 값이 v2로 갱신됐는지 확인
        StepVerifier.create(fetchAudioByTrackId(1L))
                .assertNext(saved -> {
                    Assertions.assertEquals(150.0, saved.tempo());
                    Assertions.assertEquals(-3.0, saved.loudness());
                    Assertions.assertEquals(90, saved.energy());
                    Assertions.assertEquals(80, saved.danceability());
                    Assertions.assertEquals(70, saved.positiveness());
                    Assertions.assertEquals(60, saved.speechiness());
                    Assertions.assertEquals(50, saved.liveness());
                    Assertions.assertEquals(40, saved.acousticness());
                    Assertions.assertEquals(30, saved.instrumentalness());
                    Assertions.assertEquals("G#", saved.musicalKey());
                    Assertions.assertEquals("4/4", saved.timeSignature());
                })
                .verifyComplete();
    }

    /**
     * audio_feature 컬럼들이 null을 허용할 때, null 값이 그대로 저장/조회되는지 검증한다.
     */
    @DisplayName("audio_feature 컬럼들이 null을 허용할 때, null 값이 그대로 저장/조회되는지 검증")
    @Test
    void upsertAudioFeatures_allowsNullColumns() {
        seedTrack(1L);

        AudioRow r = new AudioRow(
                1L,
                null, null,
                null, null, null,
                null, null, null,
                null, null, null
        );

        StepVerifier.create(repo.upsertAudioFeatures(List.of(r)))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(fetchAudioByTrackId(1L))
                .assertNext(saved -> {
                    Assertions.assertNull(saved.tempo());
                    Assertions.assertNull(saved.loudness());
                    Assertions.assertNull(saved.energy());
                    Assertions.assertNull(saved.danceability());
                    Assertions.assertNull(saved.positiveness());
                    Assertions.assertNull(saved.speechiness());
                    Assertions.assertNull(saved.liveness());
                    Assertions.assertNull(saved.acousticness());
                    Assertions.assertNull(saved.instrumentalness());
                    Assertions.assertNull(saved.musicalKey());
                    Assertions.assertNull(saved.timeSignature());
                })
                .verifyComplete();
    }

    /**
     * 입력 건수가 CHUNK 크기를 초과해도 분할 실행되어 정상 upsert 되는지 검증한다.
     *
     * <p>CHUNK=400 기준으로 401건을 넣어 두 번 이상 쿼리가 수행되는 시나리오를 만든다.</p>
     */
    @DisplayName("입력 건수가 CHUNK 크기를 초과해도 분할 실행되어 정상 upsert 되는지 검증")
    @Test
    void upsertAudioFeatures_overChunkSize_stillWorks() {
        int n = 401; // CHUNK(400) + 1

        for (int i = 0; i < n; i++) seedTrack(1000L + i);

        List<AudioRow> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long tid = 1000L + i;
            rows.add(new AudioRow(
                    tid,
                    120.0, -5.0,
                    50, 50, 50,
                    10, 10, 10,
                    0, "C", "4/4"
            ));
        }

        StepVerifier.create(repo.upsertAudioFeatures(rows))
                .assertNext(updated -> Assertions.assertTrue(updated >= 401))
                .verifyComplete();

        StepVerifier.create(countAudio())
                .expectNext(401L)
                .verifyComplete();
    }

    /**
     * 테스트용 track 레코드를 1건 삽입한다.
     *
     * <p>track DDL 기준 필수 컬럼(track_hash, title)은 NOT NULL 이므로 함께 세팅한다.
     * 나머지 컬럼은 default 값이 있는 경우 생략한다.</p>
     *
     * @param id track PK
     */
    private void seedTrack(long id) {
        String hash = String.format("%064x", id); // 64자리 hex (유니크 보장)

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
     * audio_feature 테이블의 총 행 수를 반환한다.
     *
     * @return audio_feature 전체 건수
     */
    private Mono<Long> countAudio() {
        return db.sql("SELECT COUNT(*) AS c FROM audio_feature")
                .map((row, meta) -> row.get("c", Long.class))
                .one();
    }

    /**
     * track_id 기준으로 audio_feature 레코드를 단건 조회한다.
     *
     * @param trackId 조회할 track id
     * @return 조회된 {@link AudioRow}
     */
    private Mono<AudioRow> fetchAudioByTrackId(long trackId) {
        return db.sql("""
                SELECT track_id, tempo, loudness, energy, danceability, positiveness,
                       speechiness, liveness, acousticness, instrumentalness, musical_key, time_signature
                FROM audio_feature
                WHERE track_id=?
                """)
                .bind(0, trackId)
                .map((row, meta) -> new AudioRow(
                        row.get("track_id", Long.class),
                        row.get("tempo", Double.class),
                        row.get("loudness", Double.class),
                        row.get("energy", Integer.class),
                        row.get("danceability", Integer.class),
                        row.get("positiveness", Integer.class),
                        row.get("speechiness", Integer.class),
                        row.get("liveness", Integer.class),
                        row.get("acousticness", Integer.class),
                        row.get("instrumentalness", Integer.class),
                        row.get("musical_key", String.class),
                        row.get("time_signature", String.class)
                ))
                .one();
    }
}