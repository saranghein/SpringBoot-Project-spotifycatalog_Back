package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.TrackRow;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * {@link TrackRepo} 통합 테스트.
 *
 * <p>track 테이블에 대한 upsert 동작(신규 삽입/동일 hash 갱신/null 허용/청크 분할)과
 * 해시 기반 ID 조회(fetchTrackIdsByHash) 동작을 검증한다.</p>
 */
@DisplayName("track repo 테스트")
@SpringBootTest
class TrackRepoTest {

    @Autowired
    TrackRepo repo;
    @Autowired
    DatabaseClient db;

    /**
     * 각 테스트 실행 전 track 테이블을 비운다.
     */
    @BeforeEach
    void clean() {
        StepVerifier.create(db.sql("DELETE FROM track").fetch().rowsUpdated())
                .expectNextCount(1).verifyComplete();
    }

    /**
     * 신규 track 레코드가 upsert로 삽입되는지 검증한다.
     */
    @DisplayName("신규 track 레코드가 upsert로 삽입되는지 검증")
    @Test
    void upsert_insertsNew() {
        TrackRow r = new TrackRow(
                "h-1",
                "title-1",
                123000,
                "02:03",
                "pop",
                "happy",
                false,
                55,
                null // album_id는 null로 단순화
        );

        StepVerifier.create(repo.upsert(List.of(r)))
                .assertNext(updated -> Assertions.assertTrue(updated >= 1))
                .verifyComplete();

        StepVerifier.create(countTrack())
                .expectNext(1L)
                .verifyComplete();
    }

    /**
     * 동일 track_hash로 upsert할 경우 기존 row를 업데이트하고 row 수는 유지되는지 검증한다.
     *
     * <p>v1 삽입 후 v2로 재-upsert하고, COUNT는 1 유지 + 컬럼 값이 v2로 갱신됐는지 확인한다.</p>
     */
    @DisplayName("동일 track_hash로 upsert할 경우 기존 row를 업데이트하고 row 수는 유지되는지 검증")
    @Test
    void upsert_sameHash_updatesValues_rowCountStays1() {
        TrackRow v1 = new TrackRow(
                "h-1", "title-1",
                1000, "00:01",
                "pop", "happy",
                false, 10,
                null
        );
        TrackRow v2 = new TrackRow(
                "h-1", "title-2",
                2000, "00:02",
                "rock", "sad",
                true, 99,
                null
        );

        StepVerifier.create(repo.upsert(List.of(v1)))
                .expectNextCount(1).verifyComplete();

        StepVerifier.create(repo.upsert(List.of(v2)))
                .expectNextCount(1).verifyComplete();

        // row 수는 1 유지
        StepVerifier.create(countTrack())
                .expectNext(1L)
                .verifyComplete();

        // 값이 v2로 바뀌었는지 (null emit 피하려고 COALESCE 사용)
        StepVerifier.create(fetchTrackSnapshotByHash("h-1"))
                .assertNext(s -> {
                    Assertions.assertEquals("title-2", s.title);
                    Assertions.assertEquals(2000, s.durationMs);
                    Assertions.assertEquals("00:02", s.durationStr);
                    Assertions.assertEquals("rock", s.genre);
                    Assertions.assertEquals("sad", s.emotion);
                    Assertions.assertTrue(s.explicit);
                    Assertions.assertEquals(99, s.popularity);
                })
                .verifyComplete();
    }

    /**
     * title/hash(필수) 외 나머지 컬럼들이 null이어도 upsert가 성공하고,
     * DB에 실제로 NULL로 저장되는지 검증한다.
     */
    @DisplayName("null이어도 upsert가 성공하고, DB에 실제로 NULL로 저장되는지 검증")
    @Test
    void upsert_allowsNullColumns_exceptTitleAndHash() {
        TrackRow r = new TrackRow(
                "h-null",
                "title-null",
                null,
                null,
                null,
                null,
                false,
                null,
                null
        );

        StepVerifier.create(repo.upsert(List.of(r)))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(assertNullColumns("h-null"))
                .verifyComplete();
    }

    /**
     * 입력 건수가 CHUNK 크기를 초과해도 분할 실행되어 정상 upsert 되는지 검증한다.
     *
     * <p>CHUNK=300 기준으로 301건을 넣어 두 번 이상 쿼리가 수행되는 시나리오를 만든다.</p>
     */
    @DisplayName("입력 건수가 CHUNK 크기를 초과해도 분할 실행되어 정상 upsert 되는지 검증")
    @Test
    void upsert_overChunkSize_stillWorks() {
        int n = 301; // CHUNK(300) + 1
        List<TrackRow> rows = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            rows.add(new TrackRow(
                    "h-" + i,
                    "t-" + i,
                    1000 + i,
                    null,
                    null,
                    null,
                    false,
                    i,
                    null
            ));
        }

        StepVerifier.create(repo.upsert(rows))
                .assertNext(updated -> Assertions.assertTrue(updated >= 301))
                .verifyComplete();

        StepVerifier.create(countTrack())
                .expectNext(301L)
                .verifyComplete();
    }

    /**
     * 존재하는 track_hash들에 대해 (hash -> id) 맵을 반환하는지 검증한다.
     *
     * <p>입력에 포함된 null 및 중복 값이 무시되는지 함께 확인한다.</p>
     */
    @DisplayName("존재하는 track_hash들에 대해 (hash -> id) 맵을 반환하는지 검증")
    @Test
    void fetchTrackIdsByHash_returnsMap_forExistingHashes_andIgnoresNullAndDuplicates() {
        TrackRow a = new TrackRow("h-1", "t-1", null, null, null, null, false, null, null);
        TrackRow b = new TrackRow("h-2", "t-2", null, null, null, null, false, null, null);

        StepVerifier.create(repo.upsert(List.of(a, b)))
                .expectNextCount(1).verifyComplete();

        Mono<Map<String, Long>> mono = repo.fetchTrackIdsByHash(
                Arrays.asList("h-1", null, "h-2", "h-1")
        );

        StepVerifier.create(mono)
                .assertNext(map -> {
                    Assertions.assertEquals(2, map.size());
                    Assertions.assertNotNull(map.get("h-1"));
                    Assertions.assertNotNull(map.get("h-2"));
                })
                .verifyComplete();
    }

    /**
     * 입력이 null 또는 빈 리스트인 경우 빈 맵을 반환하는지 검증한다.
     */
    @DisplayName("입력이 null 또는 빈 리스트인 경우 빈 맵을 반환하는지 검증")
    @Test
    void fetchTrackIdsByHash_emptyOrNull_returnsEmptyMap() {
        StepVerifier.create(repo.fetchTrackIdsByHash(List.of()))
                .assertNext(Map::isEmpty)
                .verifyComplete();

        StepVerifier.create(repo.fetchTrackIdsByHash(null))
                .assertNext(Map::isEmpty)
                .verifyComplete();
    }

    /**
     * DB에 존재하지 않는 hash만 조회하는 경우 빈 맵을 반환하는지 검증한다.
     */
    @DisplayName("DB에 존재하지 않는 hash만 조회하는 경우 빈 맵을 반환하는지 검증")
    @Test
    void fetchTrackIdsByHash_nonExisting_returnsEmptyMap() {
        StepVerifier.create(repo.fetchTrackIdsByHash(List.of("nope")))
                .assertNext(Map::isEmpty)
                .verifyComplete();
    }

    /**
     * track 테이블의 총 행 수를 반환한다.
     *
     * @return track 전체 건수
     */
    private Mono<Long> countTrack() {
        return db.sql("SELECT COUNT(*) AS c FROM track")
                .map((row, meta) -> row.get("c", Long.class))
                .one();
    }

    /**
     * 특정 hash의 track 주요 컬럼을 스냅샷 형태로 조회한다.
     *
     * <p>리액터 스트림에서 null emit을 피하기 위해 COALESCE로 기본값을 부여한다.</p>
     *
     * @param hash 조회할 track_hash
     * @return 조회된 스냅샷
     */
    private Mono<TrackSnapshot> fetchTrackSnapshotByHash(String hash) {
        return db.sql("""
                SELECT
                  title,
                  COALESCE(duration_ms, -1) AS duration_ms,
                  COALESCE(duration_str, '') AS duration_str,
                  COALESCE(genre, '') AS genre,
                  COALESCE(emotion, '') AS emotion,
                  explicit,
                  COALESCE(popularity, -1) AS popularity
                FROM track
                WHERE track_hash=?
                """)
                .bind(0, hash)
                .map((row, meta) -> new TrackSnapshot(
                        row.get("title", String.class),
                        row.get("duration_ms", Integer.class),
                        row.get("duration_str", String.class),
                        row.get("genre", String.class),
                        row.get("emotion", String.class),
                        Boolean.TRUE.equals(row.get("explicit", Boolean.class)),
                        row.get("popularity", Integer.class)
                ))
                .one();
    }

    /**
     * 특정 hash 레코드의 주요 컬럼들이 실제로 NULL로 저장되었는지 검증한다.
     *
     * <p>NULL 여부를 (col IS NULL) boolean 표현식으로 조회하면 0/1로 내려와
     * reactive 스트림에 null이 흘러가지 않아 안전하게 검증할 수 있다.</p>
     *
     * @param hash 검증할 track_hash
     * @return 검증 완료 시 완료 신호(Mono<Void>)
     */
    private Mono<Void> assertNullColumns(String hash) {
        return db.sql("""
                SELECT
                  (duration_ms IS NULL) AS dm_null,
                  (duration_str IS NULL) AS ds_null,
                  (genre IS NULL) AS g_null,
                  (emotion IS NULL) AS e_null,
                  (popularity IS NULL) AS p_null,
                  (album_id IS NULL) AS aid_null,
                  explicit AS ex_val
                  
                FROM track
                WHERE track_hash=?
                """)
                .bind(0, hash)
                .map((row, meta) -> {
                    Assertions.assertTrue(intIsOne(row, "dm_null"));
                    Assertions.assertTrue(intIsOne(row, "ds_null"));
                    Assertions.assertTrue(intIsOne(row, "g_null"));
                    Assertions.assertTrue(intIsOne(row, "e_null"));
                    Assertions.assertTrue(intIsOne(row, "p_null"));
                    Assertions.assertTrue(intIsOne(row, "aid_null"));

                    Boolean ex = row.get("ex_val", Boolean.class);
                    Assertions.assertNotNull(ex);
                    return 1;
                })
                .one()
                .then();
    }

    /**
     * MySQL의 boolean 표현식 결과(0/1)를 Integer 또는 Long로 받아 true/false로 변환한다.
     *
     * @param row 조회 결과 row
     * @param col 컬럼 별칭
     * @return 값이 1이면 true
     */
    private boolean intIsOne(io.r2dbc.spi.Row row, String col) {
        Integer v = row.get(col, Integer.class);
        if (v != null) return v == 1;
        Long l = row.get(col, Long.class);
        return l != null && l == 1L;
    }

    /**
     * 테스트 검증을 위한 track 조회 스냅샷 DTO.
     *
     * <p>COALESCE로 null을 제거한 값들을 담아 비교에 사용한다.</p>
     */
    private static class TrackSnapshot {
        final String title;
        final Integer durationMs;
        final String durationStr;
        final String genre;
        final String emotion;
        final boolean explicit;
        final Integer popularity;

        /**
         * 생성자.
         *
         * @param title       track title
         * @param durationMs  duration_ms (COALESCE 적용)
         * @param durationStr duration_str (COALESCE 적용)
         * @param genre       genre (COALESCE 적용)
         * @param emotion     emotion (COALESCE 적용)
         * @param explicit    explicit flag
         * @param popularity  popularity (COALESCE 적용)
         */
        private TrackSnapshot(String title, Integer durationMs, String durationStr,
                              String genre, String emotion, boolean explicit, Integer popularity) {
            this.title = title;
            this.durationMs = durationMs;
            this.durationStr = durationStr;
            this.genre = genre;
            this.emotion = emotion;
            this.explicit = explicit;
            this.popularity = popularity;
        }
    }
}