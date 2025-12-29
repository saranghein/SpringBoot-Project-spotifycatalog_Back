package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.IngestSeeds;
import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.NormalizeUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * {@link ArtistRepo} 통합 테스트.
 *
 * <p>artist 테이블에 대해 name_key 기반 중복 무시 삽입(insertIgnoreByKey)과
 * key -> id 매핑 조회(fetchArtistIdsByKey) 동작을 검증한다.</p>
 */
@SpringBootTest
@DisplayName("artist repo 테스트")
class ArtistRepoTest {

    @Autowired
    ArtistRepo repo;

    @Autowired
    DatabaseClient db;

    @BeforeEach
    void clean() {
        StepVerifier.create(db.sql("DELETE FROM artist").fetch().rowsUpdated())
                .expectNextCount(1)
                .verifyComplete();
    }

    /**
     * 신규 아티스트 seed 목록을 insertIgnoreByKey로 삽입했을 때
     * 정상적으로 row가 생성되는지 검증한다.
     */
    @Test
    @DisplayName("신규 아티스트 seed 목록이 insertIgnoreByKey로 정상 삽입되는지 검증")
    void insertIgnoreByKey_insertsNewSeeds() {
        List<IngestSeeds.ArtistSeed> seeds = List.of(
                new IngestSeeds.ArtistSeed(NormalizeUtils.artistKey("IU"), "IU"),
                new IngestSeeds.ArtistSeed(NormalizeUtils.artistKey("NewJeans"), "NewJeans"),
                new IngestSeeds.ArtistSeed(NormalizeUtils.artistKey("BTS"), "BTS")
        );

        StepVerifier.create(repo.insertIgnoreByKey(seeds))
                .assertNext(updated -> Assertions.assertTrue(updated >= 3))
                .verifyComplete();

        StepVerifier.create(countArtist())
                .expectNext(3L)
                .verifyComplete();
    }


    /**
     * 동일 name_key로 반복 삽입해도 UNIQUE(name_key) 제약에 의해
     * row 수가 증가하지 않는지 검증한다.
     * <p>
     * display name(name)을 다르게 넣어도 동일 key면 같은 아티스트로 처리된다.
     */
    @Test
    @DisplayName("동일 key를 재삽입해도 row 수가 증가하지 않는지 검증 (UNIQUE(name_key))")
    void insertIgnoreByKey_duplicateKeys_areIgnored_rowCountDoesNotIncrease() {
        String k1 = NormalizeUtils.artistKey("IU");
        String k2 = NormalizeUtils.artistKey("BTS");

        // 같은 key로 display name만 살짝 다르게 넣어도 "같은 아티스트"로 처리되어야 함
        List<IngestSeeds.ArtistSeed> first = List.of(
                new IngestSeeds.ArtistSeed(k1, "IU"),
                new IngestSeeds.ArtistSeed(k2, "BTS")
        );

        List<IngestSeeds.ArtistSeed> second = List.of(
                new IngestSeeds.ArtistSeed(k1, "iu"),   // display 다름
                new IngestSeeds.ArtistSeed(k2, "BTS ")  // display 다름
        );

        StepVerifier.create(repo.insertIgnoreByKey(first)).expectNextCount(1).verifyComplete();
        StepVerifier.create(countArtist()).expectNext(2L).verifyComplete();

        StepVerifier.create(repo.insertIgnoreByKey(second)).expectNextCount(1).verifyComplete();

        // 핵심: row 수가 증가하지 않아야 함
        StepVerifier.create(countArtist())
                .expectNext(2L)
                .verifyComplete();
    }

    /**
     * 존재하는 key들에 대해 key -> id 매핑을 반환하는지 검증한다.
     * <p>
     * 입력에 null 또는 중복 key가 포함되어도 결과 맵은 유효 key만 포함한다.
     */
    @Test
    @DisplayName("존재하는 key들에 대해 (key -> id) 맵을 반환하는지 검증 (null/중복 무시)")
    void fetchArtistIdsByKey_returnsMap_forExistingKeys_andIgnoresNullAndDuplicates() {
        String kIU = NormalizeUtils.artistKey("IU");
        String kBts = NormalizeUtils.artistKey("BTS");
        String kNj = NormalizeUtils.artistKey("NewJeans");

        StepVerifier.create(repo.insertIgnoreByKey(List.of(
                        new IngestSeeds.ArtistSeed(kIU, "IU"),
                        new IngestSeeds.ArtistSeed(kNj, "NewJeans"),
                        new IngestSeeds.ArtistSeed(kBts, "BTS")
                )))
                .expectNextCount(1)
                .verifyComplete();

        Mono<Map<String, Long>> mono = repo.fetchArtistIdsByKey(
                Arrays.asList(kIU, null, kBts, kIU)
        );

        StepVerifier.create(mono)
                .assertNext(map -> {
                    Assertions.assertEquals(2, map.size());
                    Assertions.assertTrue(map.containsKey(kIU));
                    Assertions.assertTrue(map.containsKey(kBts));
                    Assertions.assertNotNull(map.get(kIU));
                    Assertions.assertNotNull(map.get(kBts));
                })
                .verifyComplete();
    }

    /**
     * 입력이 null 또는 빈 리스트일 경우 빈 맵을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("입력이 null 또는 빈 리스트일 경우 빈 맵을 반환하는지 검증")
    void fetchArtistIdsByKey_emptyOrNullInput_returnsEmptyMap() {
        StepVerifier.create(repo.fetchArtistIdsByKey(List.of()))
                .assertNext(map -> Assertions.assertTrue(map.isEmpty()))
                .verifyComplete();

        StepVerifier.create(repo.fetchArtistIdsByKey(null))
                .assertNext(map -> Assertions.assertTrue(map.isEmpty()))
                .verifyComplete();
    }

    /**
     * artist 테이블의 총 row 수를 조회한다.
     *
     * @return artist row count
     */
    private Mono<Long> countArtist() {
        return db.sql("SELECT COUNT(*) AS c FROM artist")
                .map((row, meta) -> row.get("c", Long.class))
                .one();
    }
}
