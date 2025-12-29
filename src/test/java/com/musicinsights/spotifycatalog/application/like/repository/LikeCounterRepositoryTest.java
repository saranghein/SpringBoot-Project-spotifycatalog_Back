package com.musicinsights.spotifycatalog.application.like.repository;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


/**
 * {@link LikeCounterRepository} 단위 테스트.
 *
 * <p>{@link DatabaseClient} 체인을 모킹하여 upsert 증가 및 조회 쿼리 호출,
 * bind 값, row 매핑, empty 처리(defaultIfEmpty)를 검증한다.</p>
 */
@DisplayName("like counter repo 검증 ")
class LikeCounterRepositoryTest {

    private DatabaseClient db;
    private LikeCounterRepository repo;

    @BeforeEach
    void setUp() {
        this.db = mock(DatabaseClient.class, RETURNS_DEEP_STUBS);
        this.repo = new LikeCounterRepository(db);
    }


    /**
     * incrementAndGet이 upsert 쿼리 실행 후 LAST_INSERT_ID 조회로 최종 카운트를 반환하는지 검증한다.
     */
    @Test
    @DisplayName("증가 쿼리(upsert)와 LAST_INSERT_ID 조회 쿼리가 호출되고 최종 카운트를 반환하는지 검증")
    void increment_returnsLastInsertId_andVerifiesSql() {
        // given
        long trackId = 10L;

        DatabaseClient.GenericExecuteSpec upsertSpec =
                mock(DatabaseClient.GenericExecuteSpec.class, RETURNS_DEEP_STUBS);

        DatabaseClient.GenericExecuteSpec selectSpec =
                mock(DatabaseClient.GenericExecuteSpec.class, RETURNS_DEEP_STUBS);

        @SuppressWarnings("unchecked")
        RowsFetchSpec<Long> selectFetch = (RowsFetchSpec<Long>) mock(RowsFetchSpec.class);

        // sql()에 전달된 문자열 캡처
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

        // 첫번째 sql() 호출: upsert, 두번째 sql() 호출: select
        when(db.sql(anyString())).thenReturn(upsertSpec, selectSpec);

        when(upsertSpec.bind(anyInt(), any())).thenReturn(upsertSpec);
        when(upsertSpec.fetch().rowsUpdated()).thenReturn(Mono.just(1L));

        ArgumentCaptor<BiFunction<Row, RowMetadata, Long>> mapperCaptor =
                ArgumentCaptor.forClass((Class) BiFunction.class);

        when(selectSpec.map(mapperCaptor.capture())).thenReturn(selectFetch);

        when(selectFetch.one()).thenAnswer(inv -> {
            Row row = mock(Row.class);
            RowMetadata meta = mock(RowMetadata.class);
            when(row.get(0, Long.class)).thenReturn(5L);
            return Mono.just(mapperCaptor.getValue().apply(row, meta));
        });

        // when
        Mono<Long> mono = repo.incrementAndGet(trackId);

        // then
        StepVerifier.create(mono)
                .expectNext(5L)
                .verifyComplete();

        // bind 및 호출 흐름 검증
        verify(upsertSpec).bind(0, trackId);
        verify(upsertSpec.fetch()).rowsUpdated();
        verify(selectFetch).one();

        // sql 문자열 검증
        verify(db, times(2)).sql(sqlCaptor.capture());
        List<String> sqls = sqlCaptor.getAllValues();

        assertThat(sqls.get(0))
                .contains("INSERT INTO track_like_counter")
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("LAST_INSERT_ID");

        assertThat(sqls.get(1))
                .isEqualTo("SELECT LAST_INSERT_ID()");
    }

    /**
     * findCount가 row가 존재할 때 like_count 값을 매핑하여 반환하는지 검증한다.
     */
    @Test
    @DisplayName("카운트가 존재하면 like_count 값을 반환하는지 검증")
    void findCount_whenExists_returnsValue_andVerifiesSql() {
        // given
        long trackId = 7L;

        DatabaseClient.GenericExecuteSpec spec =
                mock(DatabaseClient.GenericExecuteSpec.class, RETURNS_DEEP_STUBS);

        @SuppressWarnings("unchecked")
        RowsFetchSpec<Long> fetch = (RowsFetchSpec<Long>) mock(RowsFetchSpec.class);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

        when(db.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyInt(), any())).thenReturn(spec);

        ArgumentCaptor<BiFunction<Row, RowMetadata, Long>> mapperCaptor =
                ArgumentCaptor.forClass((Class) BiFunction.class);

        when(spec.map(mapperCaptor.capture())).thenReturn(fetch);

        when(fetch.one()).thenAnswer(inv -> {
            Row row = mock(Row.class);
            RowMetadata meta = mock(RowMetadata.class);

            when(row.get("like_count", Long.class)).thenReturn(12L);

            return Mono.just(mapperCaptor.getValue().apply(row, meta));
        });

        // when
        Mono<Long> mono = repo.findCount(trackId);

        // then
        StepVerifier.create(mono)
                .expectNext(12L)
                .verifyComplete();

        verify(spec).bind(0, trackId);

        verify(db).sql(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("SELECT like_count")
                .contains("FROM track_like_counter")
                .contains("WHERE track_id = ?");
    }

    /**
     * findCount가 조회 결과가 비어있을 때 0을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("카운트 row가 없으면 0을 반환하는지 검증")
    void findCount_whenEmpty_returnsZero() {
        // given
        long trackId = 999L;

        DatabaseClient.GenericExecuteSpec spec =
                mock(DatabaseClient.GenericExecuteSpec.class, RETURNS_DEEP_STUBS);

        @SuppressWarnings("unchecked")
        RowsFetchSpec<Long> fetch = (RowsFetchSpec<Long>) mock(RowsFetchSpec.class);

        when(db.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyInt(), any())).thenReturn(spec);

        when(spec.map(any(BiFunction.class))).thenReturn(fetch);
        when(fetch.one()).thenReturn(Mono.empty());

        // when
        Mono<Long> mono = repo.findCount(trackId);

        // then
        StepVerifier.create(mono)
                .expectNext(0L)
                .verifyComplete();

        verify(spec).bind(0, trackId);
    }
}
