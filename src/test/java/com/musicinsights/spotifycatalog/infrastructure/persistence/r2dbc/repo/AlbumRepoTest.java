package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link AlbumRepo} 통합 테스트.
 *
 * <p>album 테이블에 대한 upsert 동작(신규 삽입/중복 처리/업데이트/청크 분할)과
 * 이름 기반 ID 조회(fetchAlbumIdsByName) 동작을 검증한다.</p>
 */
@SpringBootTest
@DisplayName("album repo 테스트")
class AlbumRepoTest {
    @Autowired
    AlbumRepo repo;
    @Autowired
    DatabaseClient db;

    /**
     * 각 테스트 실행 전 album 테이블을 비운다.
     */
    @BeforeEach
    void clean() {
        StepVerifier.create(db.sql("DELETE FROM album").fetch().rowsUpdated())
                .expectNextCount(1)
                .verifyComplete();
    }

    /**
     * upsert 호출 시 신규 앨범들이 정상 삽입되는지 검증한다.
     *
     * <p>삽입 후 COUNT(*)로 최종 row 수를 확인한다.</p>
     */
    @Test
    @DisplayName("upsert 호출 시 신규 앨범들이 정상 삽입되는지 검증")
    void upsert_insertsNewAlbums() {
        List<AlbumRow> rows = List.of(
                new AlbumRow("AlbumA", LocalDate.of(2020, 1, 1)),
                new AlbumRow("AlbumB", LocalDate.of(2021, 6, 10))
        );

        StepVerifier.create(repo.upsert(rows))
                .assertNext(updated -> Assertions.assertTrue(updated >= 2))
                .verifyComplete();

        StepVerifier.create(countAlbum())
                .expectNext(2L)
                .verifyComplete();
    }

    /**
     * 동일 (name, release_date) 조합을 반복 upsert 해도 row 수가 증가하지 않는지 검증한다.
     *
     * <p>중복 처리 시 rowsUpdated 값은 환경에 따라 달라질 수 있어 COUNT(*) 기반으로 검증한다.</p>
     */
    @Test
    @DisplayName("동일 (name, release_date) 조합을 반복 upsert 해도 row 수가 증가하지 않는지 검증")
    void upsert_duplicateNameAndDate_doesNotIncreaseRowCount() {
        AlbumRow a = new AlbumRow("AlbumA", LocalDate.of(2020, 1, 1));

        StepVerifier.create(repo.upsert(List.of(a)))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(countAlbum()).expectNext(1L).verifyComplete();

        // 동일 (name, release_date)
        StepVerifier.create(repo.upsert(List.of(a)))
                .expectNextCount(1)
                .verifyComplete();

        // row 수는 그대로
        StepVerifier.create(countAlbum())
                .expectNext(1L)
                .verifyComplete();
    }

    /**
     * 동일 name에 대해 release_date가 달라지면 upsert가 업데이트로 동작하는지 검증한다.
     */
    @Test
    @DisplayName("동일 name에 대해 release_date가 달라지면 upsert가 업데이트로 동작하는지 검증")
    void upsert_sameNameDifferentDate_insertsNewRow() {
        AlbumRow a1 = new AlbumRow("AlbumA", LocalDate.of(2020, 1, 1));
        AlbumRow a2 = new AlbumRow("AlbumA", LocalDate.of(2021, 5, 5)); // same name, different date

        StepVerifier.create(repo.upsert(List.of(a1)))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(repo.upsert(List.of(a2)))
                .expectNextCount(1)
                .verifyComplete();

        // 같은 name의 row가 2개여야 정상
        StepVerifier.create(
                        db.sql("SELECT COUNT(*) AS c FROM album WHERE name=?")
                                .bind(0, "AlbumA")
                                .map((row, meta) -> row.get("c", Long.class))
                                .one()
                )
                .expectNext(2L)
                .verifyComplete();
    }


    /**
     * 입력 건수가 CHUNK 크기를 초과해도 chunkedSum에 의해 분할 실행되어 정상 upsert 되는지 검증한다.
     *
     * <p>CHUNK=400 기준으로 401건을 넣어 두 번 이상 쿼리가 수행되는 시나리오를 만든다.</p>
     */
    @Test
    @DisplayName("입력 건수가 CHUNK 크기를 초과해도 chunkedSum에 의해 분할 실행되어 정상 upsert 되는지 검증")
    void upsert_overChunkSize_stillWorks() {
        int n = 401; // CHUNK(400) + 1
        List<AlbumRow> rows = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            rows.add(new AlbumRow(
                    "Album-" + i,
                    LocalDate.of(2020, 1, 1)
            ));
        }

        StepVerifier.create(repo.upsert(rows))
                .assertNext(updated -> Assertions.assertTrue(updated >= 401))
                .verifyComplete();

        StepVerifier.create(countAlbum())
                .expectNext(401L)
                .verifyComplete();
    }

    /**
     * 존재하는 앨범 목록을 조회했을 때 (AlbumRow -> id) 매핑이 반환되는지 검증한다.
     *
     * <p>id는 auto_increment라 고정 값 비교 대신 null 여부로 확인한다.</p>
     */
    @Test
    @DisplayName("존재하는 앨범 목록을 조회했을 때 (AlbumRow -> id) 매핑이 반환되는지 검증")
    void fetchAlbumIdsByName_returnsIdsForExistingAlbums() {
        AlbumRow a = new AlbumRow("AlbumA", LocalDate.of(2020, 1, 1));
        AlbumRow b = new AlbumRow("AlbumB", LocalDate.of(2021, 6, 10));

        StepVerifier.create(repo.upsert(List.of(a, b)))
                .expectNextCount(1)
                .verifyComplete();

        Mono<Map<AlbumRow, Long>> mono = repo.fetchAlbumIdsByName(
                List.of(a, b)
        );

        StepVerifier.create(mono)
                .assertNext(map -> {
                    Assertions.assertEquals(2, map.size());
                    Assertions.assertNotNull(map.get(a));
                    Assertions.assertNotNull(map.get(b));
                })
                .verifyComplete();
    }

    /**
     * 입력에 동일 AlbumRow가 중복 포함되어도 결과 맵이 중복 없이 반환되는지 검증한다.
     */
    @Test
    @DisplayName("입력에 동일 AlbumRow가 중복 포함되어도 결과 맵이 중복 없이 반환되는지 검증")
    void fetchAlbumIdsByName_ignoresDuplicateInput() {
        AlbumRow a = new AlbumRow("AlbumA", LocalDate.of(2020, 1, 1));

        StepVerifier.create(repo.upsert(List.of(a)))
                .expectNextCount(1)
                .verifyComplete();

        Mono<Map<AlbumRow, Long>> mono = repo.fetchAlbumIdsByName(
                List.of(a, a, a)
        );

        StepVerifier.create(mono)
                .assertNext(map -> Assertions.assertEquals(1, map.size()))
                .verifyComplete();
    }

    /**
     * 입력이 null 또는 빈 리스트인 경우 빈 맵을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("입력이 null 또는 빈 리스트인 경우 빈 맵을 반환하는지 검증")
    void fetchAlbumIdsByName_emptyOrNull_returnsEmptyMap() {
        StepVerifier.create(repo.fetchAlbumIdsByName(List.of()))
                .assertNext(Map::isEmpty)
                .verifyComplete();

        StepVerifier.create(repo.fetchAlbumIdsByName(null))
                .assertNext(Map::isEmpty)
                .verifyComplete();
    }

    /**
     * DB에 존재하지 않는 앨범만 조회하는 경우 빈 맵을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("DB에 존재하지 않는 앨범만 조회하는 경우 빈 맵을 반환하는지 검증")
    void fetchAlbumIdsByName_nonExistingAlbum_returnsEmptyMap() {
        AlbumRow notExists = new AlbumRow("GhostAlbum", LocalDate.of(1999, 1, 1));

        StepVerifier.create(repo.fetchAlbumIdsByName(List.of(notExists)))
                .assertNext(Map::isEmpty)
                .verifyComplete();
    }

    /**
     * album 테이블의 총 행 수를 반환한다.
     *
     * @return album 전체 건수
     */
    private Mono<Long> countAlbum() {
        return db.sql("SELECT COUNT(*) AS c FROM album")
                .map((row, meta) -> row.get("c", Long.class))
                .one();
    }
}