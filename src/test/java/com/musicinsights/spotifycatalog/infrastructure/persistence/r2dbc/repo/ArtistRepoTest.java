package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

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
 * <p>artist 테이블에 대해 insertIgnore(중복 무시) 및
 * 이름 기반 ID 조회(fetchArtistIdsByName) 동작을 검증한다.</p>
 */
@SpringBootTest
@DisplayName("artist repo 테스트")
class ArtistRepoTest {
    @Autowired
    ArtistRepo repo;
    @Autowired
    DatabaseClient db;

    /**
     * 각 테스트 실행 전 artist 테이블을 비운다.
     */
    @BeforeEach
    void clean() {
        StepVerifier.create(db.sql("DELETE FROM artist").fetch().rowsUpdated())
                .expectNextCount(1)
                .verifyComplete();
    }

    /**
     * 신규 아티스트 이름 목록이 insertIgnore로 정상 삽입되는지 검증한다.
     *
     * <p>삽입 이후 COUNT(*)로 최종 row 수를 확인한다.</p>
     */
    @Test
    @DisplayName("신규 아티스트 이름 목록이 insertIgnore로 정상 삽입되는지 검증")
    void insertIgnore_insertsNewNames() {
        List<String> names = List.of("IU", "NewJeans", "BTS");

        StepVerifier.create(repo.insertIgnore(names))
                .assertNext(updated -> Assertions.assertTrue(updated >= 3))
                .verifyComplete();

        StepVerifier.create(countArtist())
                .expectNext(3L)
                .verifyComplete();
    }

    /**
     * 동일 이름을 재삽입해도(UNIQUE(name) 충돌) row 수가 증가하지 않는지 검증한다.
     *
     * <p>rowsUpdated 값은 환경에 따라 달라질 수 있어 COUNT(*) 기반으로 검증한다.</p>
     */
    @Test
    @DisplayName("동일 이름을 재삽입해도(UNIQUE(name) 충돌) row 수가 증가하지 않는지 검증")
    void insertIgnore_duplicateNames_areIgnored_rowCountDoesNotIncrease() {
        List<String> names = List.of("IU", "NewJeans", "BTS");

        StepVerifier.create(repo.insertIgnore(names)).expectNextCount(1).verifyComplete();
        StepVerifier.create(countArtist()).expectNext(3L).verifyComplete();

        // 동일 입력 재삽입 (unique(name) 기준 중복 무시 기대)
        StepVerifier.create(repo.insertIgnore(names))
                .expectNextCount(1)
                .verifyComplete();

        //  "COUNT가 늘지 않는다"를 핵심으로 검증
        StepVerifier.create(countArtist())
                .expectNext(3L)
                .verifyComplete();
    }

    /**
     * 존재하는 이름들에 대해 (name -> id) 맵을 반환하는지 검증한다.
     *
     * <p>입력에 포함된 null 및 중복 값이 무시되는지 함께 확인한다.</p>
     */
    @Test
    @DisplayName("존재하는 이름들에 대해 (name -> id) 맵을 반환하는지 검증")
    void fetchArtistIdsByName_returnsMap_forExistingNames_andIgnoresNullAndDuplicates() {
        // given: DB에 3명 넣기
        StepVerifier.create(repo.insertIgnore(List.of("IU", "NewJeans", "BTS")))
                .expectNextCount(1)
                .verifyComplete();

        // when: 중복 + null 섞어서 조회
        Mono<Map<String, Long>> mono = repo.fetchArtistIdsByName(
                Arrays.asList("IU", null, "BTS", "IU")
        );


        // then
        StepVerifier.create(mono)
                .assertNext(map -> {
                    Assertions.assertEquals(2, map.size());
                    Assertions.assertTrue(map.containsKey("IU"));
                    Assertions.assertTrue(map.containsKey("BTS"));

                    // id는 auto_increment라 값 고정은 못하지만 null은 아니어야 함
                    Assertions.assertNotNull(map.get("IU"));
                    Assertions.assertNotNull(map.get("BTS"));
                })
                .verifyComplete();
    }


    /**
     * 입력이 null 또는 빈 리스트일 경우 빈 맵을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("입력이 null 또는 빈 리스트일 경우 빈 맵을 반환하는지 검증")
    void fetchArtistIdsByName_emptyOrNullInput_returnsEmptyMap() {
        StepVerifier.create(repo.fetchArtistIdsByName(List.of()))
                .assertNext(map -> Assertions.assertTrue(map.isEmpty()))
                .verifyComplete();

        StepVerifier.create(repo.fetchArtistIdsByName(null))
                .assertNext(map -> Assertions.assertTrue(map.isEmpty()))
                .verifyComplete();
    }

    /**
     * artist 테이블의 총 행 수를 반환한다.
     *
     * @return artist 전체 건수
     */
    private Mono<Long> countArtist() {
        return db.sql("SELECT COUNT(*) AS c FROM artist")
                .map((row, meta) -> row.get("c", Long.class))
                .one();
    }
}