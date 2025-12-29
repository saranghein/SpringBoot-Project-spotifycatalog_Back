package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.IngestSeeds;
import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.NormalizeUtils;
import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.AlbumRow;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * {@link AlbumRepo} 통합 테스트.
 *
 * <p>album 테이블에 대해 album_key 기반 업서트(upsertByKey) 및
 * album_key -> id 매핑 조회(fetchAlbumIdsByKey) 동작을 검증한다.</p>
 */
@SpringBootTest
@DisplayName("album repo 테스트")
class AlbumRepoTest {

    @Autowired
    AlbumRepo repo;

    @Autowired
    DatabaseClient db;

    @BeforeEach
    void clean() {
        StepVerifier.create(db.sql("DELETE FROM album").fetch().rowsUpdated())
                .expectNextCount(1)
                .verifyComplete();
    }

    /**
     * 신규 앨범 seed 목록을 upsertByKey로 삽입했을 때
     * 정상적으로 row가 생성되는지 검증한다.
     * <p>
     * release_date가 null인 케이스도 포함한다.
     */
    @Test
    @DisplayName("신규 앨범 seed 목록이 upsertByKey로 정상 삽입되는지 검증")
    void upsertByKey_insertsNewSeeds() {
        var a1 = new AlbumRow("First Album", LocalDate.of(2015, 10, 16));
        var a2 = new AlbumRow("Second Album", LocalDate.of(2020, 9, 25));
        var a3 = new AlbumRow("NoDate Album", null); // release_date null 케이스

        List<IngestSeeds.AlbumSeed> seeds = List.of(
                new IngestSeeds.AlbumSeed(NormalizeUtils.albumKey(a1.name(), a1.releaseDate()), a1),
                new IngestSeeds.AlbumSeed(NormalizeUtils.albumKey(a2.name(), a2.releaseDate()), a2),
                new IngestSeeds.AlbumSeed(NormalizeUtils.albumKey(a3.name(), a3.releaseDate()), a3)
        );

        StepVerifier.create(repo.upsertByKey(seeds))
                .assertNext(updated -> Assertions.assertTrue(updated >= 3))
                .verifyComplete();

        StepVerifier.create(countAlbum())
                .expectNext(3L)
                .verifyComplete();
    }

    /**
     * 동일 album_key를 가진 seed를 반복 업서트해도
     * UNIQUE(album_key) 제약에 의해 row 수가 증가하지 않는지 검증한다.
     */
    @Test
    @DisplayName("동일 album_key를 재업서트해도 row 수가 증가하지 않는지 검증 (UNIQUE(album_key))")
    void upsertByKey_duplicateKeys_areIdempotent_rowCountDoesNotIncrease() {
        var base = new AlbumRow("Same Album", LocalDate.of(2020, 1, 1));
        String k = NormalizeUtils.albumKey(base.name(), base.releaseDate());

        List<IngestSeeds.AlbumSeed> first = List.of(
                new IngestSeeds.AlbumSeed(k, base)
        );

        // display(name)을 일부러 다르게 넣어도, album_key가 동일하면 같은 row로 유지되어야 함
        var variant = new AlbumRow("same  album  ", LocalDate.of(2020, 1, 1));
        List<IngestSeeds.AlbumSeed> second = List.of(
                new IngestSeeds.AlbumSeed(k, variant)
        );

        StepVerifier.create(repo.upsertByKey(first))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(countAlbum())
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(repo.upsertByKey(second))
                .expectNextCount(1)
                .verifyComplete();

        // 핵심: row 수가 증가하지 않아야 함
        StepVerifier.create(countAlbum())
                .expectNext(1L)
                .verifyComplete();
    }

    /**
     * 존재하는 key들에 대해 album_key -> id 매핑을 반환하는지 검증한다.
     * <p>
     * 입력에 null 또는 중복 key가 포함되어도 결과 맵은 유효 key만 포함한다.
     */
    @Test
    @DisplayName("존재하는 key들에 대해 (album_key -> id) 맵을 반환하는지 검증 (null/중복 무시)")
    void fetchAlbumIdsByKey_returnsMap_forExistingKeys_andIgnoresNullAndDuplicates() {
        var a1 = new AlbumRow("A", LocalDate.of(2015, 10, 16));
        var a2 = new AlbumRow("B", null);

        String k1 = NormalizeUtils.albumKey(a1.name(), a1.releaseDate());
        String k2 = NormalizeUtils.albumKey(a2.name(), a2.releaseDate());

        StepVerifier.create(repo.upsertByKey(List.of(
                        new IngestSeeds.AlbumSeed(k1, a1),
                        new IngestSeeds.AlbumSeed(k2, a2)
                )))
                .expectNextCount(1)
                .verifyComplete();

        Mono<Map<String, Long>> mono = repo.fetchAlbumIdsByKey(
                Arrays.asList(k1, null, k2, k1)
        );

        StepVerifier.create(mono)
                .assertNext(map -> {
                    Assertions.assertEquals(2, map.size());
                    Assertions.assertTrue(map.containsKey(k1));
                    Assertions.assertTrue(map.containsKey(k2));
                    Assertions.assertNotNull(map.get(k1));
                    Assertions.assertNotNull(map.get(k2));
                })
                .verifyComplete();
    }

    /**
     * 입력이 null 또는 빈 리스트일 경우 빈 맵을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("입력이 null 또는 빈 리스트일 경우 빈 맵을 반환하는지 검증")
    void fetchAlbumIdsByKey_emptyOrNullInput_returnsEmptyMap() {
        StepVerifier.create(repo.fetchAlbumIdsByKey(List.of()))
                .assertNext(map -> Assertions.assertTrue(map.isEmpty()))
                .verifyComplete();

        StepVerifier.create(repo.fetchAlbumIdsByKey(null))
                .assertNext(map -> Assertions.assertTrue(map.isEmpty()))
                .verifyComplete();
    }


    /**
     * album 테이블의 총 row 수를 조회한다.
     *
     * @return album row count
     */
    private Mono<Long> countAlbum() {
        return db.sql("SELECT COUNT(*) AS c FROM album")
                .map((row, meta) -> row.get("c", Long.class))
                .one();
    }
}
