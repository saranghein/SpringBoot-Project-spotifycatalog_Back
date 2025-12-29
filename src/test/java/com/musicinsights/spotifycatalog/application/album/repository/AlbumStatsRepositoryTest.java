package com.musicinsights.spotifycatalog.application.album.repository;


import com.musicinsights.spotifycatalog.application.album.dto.response.ArtistAlbumStatsResponse;
import com.musicinsights.spotifycatalog.application.album.dto.response.ArtistAlbumsResponse;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.BiFunction;

import static com.musicinsights.spotifycatalog.application.album.repository.AlbumStatsSql.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AlbumStatsRepository} 단위 테스트.
 * <p>
 * DatabaseClient fluent 체인을 Mockito로 모킹하여,
 * - SQL 분기(year null/비null)
 * - bind 값
 * - row -> DTO 매핑
 * - empty 결과 처리(defaultIfEmpty)
 * 를 검증한다.
 */
@DisplayName("album stats repo 테스트")
class AlbumStatsRepositoryTest {
    private DatabaseClient db;
    private AlbumStatsRepository repo;

    @BeforeEach
    void setUp() {
        this.db = mock(DatabaseClient.class, RETURNS_DEEP_STUBS);
        this.repo = new AlbumStatsRepository(db);
    }

    /** year=null일 때 SQL_FIND_ALL을 사용하고 바인딩/매핑이 올바른지 검증한다. */
    @Test
    @DisplayName("year=null일 때 SQL_FIND_ALL 사용 및 bind/매핑 검증")
    void findArtistAlbumCounts_yearNull_bindsAndMaps() {
        // given
        long cursorAlbumCount = 10L;
        long cursorArtistId = 5L;
        int limit = 3;

        DatabaseClient.GenericExecuteSpec spec = mock(DatabaseClient.GenericExecuteSpec.class, RETURNS_DEEP_STUBS);

        @SuppressWarnings("unchecked")
        RowsFetchSpec<ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse> fetch =
                (RowsFetchSpec<ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse>) mock(RowsFetchSpec.class);

        when(db.sql(SQL_FIND_ALL)).thenReturn(spec);
        when(spec.bind(anyInt(), any())).thenReturn(spec);

        ArgumentCaptor<BiFunction<Row, RowMetadata, ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse>> mapperCaptor =
                ArgumentCaptor.forClass((Class) BiFunction.class);

        when(spec.map(mapperCaptor.capture())).thenReturn(fetch);

        when(fetch.all()).thenAnswer(inv -> {
            Row row = mock(Row.class);
            RowMetadata meta = mock(RowMetadata.class);

            when(row.get(eq("artistId"), eq(Number.class))).thenReturn(99L);
            when(row.get(eq("artistName"), eq(String.class))).thenReturn("IU");
            when(row.get(eq("albumCount"), eq(Number.class))).thenReturn(7L);

            var mapped = mapperCaptor.getValue().apply(row, meta);
            return Flux.just(mapped);
        });

        // when
        Flux<ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse> result =
                repo.findArtistAlbumCounts(null, cursorAlbumCount, cursorArtistId, limit);

        // then
        verify(db).sql(SQL_FIND_ALL);
        verify(spec).bind(0, cursorAlbumCount);
        verify(spec).bind(1, cursorAlbumCount);
        verify(spec).bind(2, cursorArtistId);
        verify(spec).bind(3, limit);

        StepVerifier.create(result)
                .assertNext(item -> {
                    assertThat(item.artistId()).isEqualTo(99L);
                    assertThat(item.artistName()).isEqualTo("IU");
                    assertThat(item.albumCount()).isEqualTo(7L);
                })
                .verifyComplete();
    }

    /** year!=null일 때 SQL_FIND_BY_YEAR를 사용하고 바인딩/매핑이 올바른지 검증한다. */
    @Test
    @DisplayName("year!=null일 때 SQL_FIND_BY_YEAR 사용 및 bind/매핑 검증")
    void findArtistAlbumCounts_yearProvided_bindsAndMaps() {
        // given
        int year = 2020;
        long cursorAlbumCount = 100L;
        long cursorArtistId = 22L;
        int limit = 2;

        DatabaseClient.GenericExecuteSpec spec = mock(DatabaseClient.GenericExecuteSpec.class, RETURNS_DEEP_STUBS);

        @SuppressWarnings("unchecked")
        RowsFetchSpec<ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse> fetch =
                (RowsFetchSpec<ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse>) mock(RowsFetchSpec.class);

        when(db.sql(SQL_FIND_BY_YEAR)).thenReturn(spec);
        when(spec.bind(anyInt(), any())).thenReturn(spec);

        ArgumentCaptor<BiFunction<Row, RowMetadata, ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse>> mapperCaptor =
                ArgumentCaptor.forClass((Class) BiFunction.class);

        when(spec.map(mapperCaptor.capture())).thenReturn(fetch);

        when(fetch.all()).thenAnswer(inv -> {
            Row row = mock(Row.class);
            RowMetadata meta = mock(RowMetadata.class);

            when(row.get(eq("artistId"), eq(Number.class))).thenReturn(1L);
            when(row.get(eq("artistName"), eq(String.class))).thenReturn("TAEYEON");
            when(row.get(eq("albumCount"), eq(Number.class))).thenReturn(3L);

            var mapped = mapperCaptor.getValue().apply(row, meta);
            return Flux.just(mapped);
        });

        // when
        Flux<ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse> result =
                repo.findArtistAlbumCounts(year, cursorAlbumCount, cursorArtistId, limit);

        // then
        verify(db).sql(SQL_FIND_BY_YEAR);
        verify(spec).bind(0, year);
        verify(spec).bind(1, cursorAlbumCount);
        verify(spec).bind(2, cursorAlbumCount);
        verify(spec).bind(3, cursorArtistId);
        verify(spec).bind(4, limit);

        StepVerifier.create(result)
                .assertNext(item -> {
                    assertThat(item.artistId()).isEqualTo(1L);
                    assertThat(item.artistName()).isEqualTo("TAEYEON");
                    assertThat(item.albumCount()).isEqualTo(3L);
                })
                .verifyComplete();
    }

    /** year=null일 때 전체 앨범 수 집계 SQL을 사용하고 total 매핑이 올바른지 검증한다. */
    @Test
    @DisplayName("year=null일 때 SQL_COUNT_ALBUMS_ALL 사용 및 total 매핑 검증")
    void countAlbums_yearNull_returnsTotal() {
        // given
        DatabaseClient.GenericExecuteSpec spec = mock(DatabaseClient.GenericExecuteSpec.class, RETURNS_DEEP_STUBS);

        @SuppressWarnings("unchecked")
        RowsFetchSpec<Long> fetch = (RowsFetchSpec<Long>) mock(RowsFetchSpec.class);

        when(db.sql(SQL_COUNT_ALBUMS_ALL)).thenReturn(spec);

        ArgumentCaptor<BiFunction<Row, RowMetadata, Long>> mapperCaptor =
                ArgumentCaptor.forClass((Class) BiFunction.class);

        when(spec.map(mapperCaptor.capture())).thenReturn(fetch);

        when(fetch.one()).thenAnswer(inv -> {
            Row row = mock(Row.class);
            RowMetadata meta = mock(RowMetadata.class);
            when(row.get(eq("total"), eq(Number.class))).thenReturn(123L);

            Long mapped = mapperCaptor.getValue().apply(row, meta);
            return Mono.just(mapped);
        });

        // when
        Mono<Long> result = repo.countAlbums(null);

        // then
        verify(db).sql(SQL_COUNT_ALBUMS_ALL);

        StepVerifier.create(result)
                .expectNext(123L)
                .verifyComplete();
    }

    /** year!=null일 때 연도별 집계 SQL을 사용하고 empty면 0을 반환하는지 검증한다. */
    @Test
    @DisplayName("year!=null일 때 SQL_COUNT_ALBUMS_BY_YEAR 사용, bind(year) 및 empty면 0 반환 검증")
    void countAlbums_yearProvided_bindsYear_andDefaultIfEmptyIs0() {
        // given
        int year = 1999;

        DatabaseClient.GenericExecuteSpec spec = mock(DatabaseClient.GenericExecuteSpec.class, RETURNS_DEEP_STUBS);

        @SuppressWarnings("unchecked")
        RowsFetchSpec<Long> fetch = (RowsFetchSpec<Long>) mock(RowsFetchSpec.class);

        when(db.sql(SQL_COUNT_ALBUMS_BY_YEAR)).thenReturn(spec);
        when(spec.bind(anyInt(), any())).thenReturn(spec);

        when(spec.map((BiFunction<Row, RowMetadata, Long>) any()))
                .thenReturn(fetch);
        when(fetch.one()).thenReturn(Mono.empty()); // empty

        // when
        Mono<Long> result = repo.countAlbums(year);

        // then
        verify(db).sql(SQL_COUNT_ALBUMS_BY_YEAR);
        verify(spec).bind(0, year);

        StepVerifier.create(result)
                .expectNext(0L)
                .verifyComplete();
    }

    /** 아티스트 존재 여부 조회가 row 존재 시 true, 없으면 false를 반환하는지 검증한다. */
    @Test
    @DisplayName("row가 존재하면 true, 없으면 false 반환 검증")
    void existsArtist_returnsTrueWhenRowExists_otherwiseFalse() {
        // true 케이스
        {
            DatabaseClient.GenericExecuteSpec spec = mock(DatabaseClient.GenericExecuteSpec.class, RETURNS_DEEP_STUBS);

            @SuppressWarnings("unchecked")
            RowsFetchSpec<Boolean> fetch = (RowsFetchSpec<Boolean>) mock(RowsFetchSpec.class);

            when(db.sql(SQL_EXISTS_ARTIST)).thenReturn(spec);
            when(spec.bind(anyInt(), any())).thenReturn(spec);

            ArgumentCaptor<BiFunction<Row, RowMetadata, Boolean>> mapperCaptor =
                    ArgumentCaptor.forClass((Class) BiFunction.class);

            when(spec.map(mapperCaptor.capture())).thenReturn(fetch);

            when(fetch.one()).thenAnswer(inv -> {
                Row row = mock(Row.class);
                RowMetadata meta = mock(RowMetadata.class);
                Boolean mapped = mapperCaptor.getValue().apply(row, meta); // mapper는 무조건 true 반환
                return Mono.just(mapped);
            });

            StepVerifier.create(repo.existsArtist(10L))
                    .expectNext(true)
                    .verifyComplete();
        }

        // false 케이스
        {
            DatabaseClient.GenericExecuteSpec spec = mock(DatabaseClient.GenericExecuteSpec.class, RETURNS_DEEP_STUBS);

            @SuppressWarnings("unchecked")
            RowsFetchSpec<Boolean> fetch = (RowsFetchSpec<Boolean>) mock(RowsFetchSpec.class);

            when(db.sql(SQL_EXISTS_ARTIST)).thenReturn(spec);
            when(spec.bind(anyInt(), any())).thenReturn(spec);
            ArgumentCaptor<BiFunction<Row, RowMetadata, Boolean>> mapperCaptor =
                    ArgumentCaptor.forClass((Class) BiFunction.class);

            when(spec.map(mapperCaptor.capture())).thenReturn(fetch);

            when(fetch.one()).thenReturn(Mono.empty());

            StepVerifier.create(repo.existsArtist(999L))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    /** year=null일 때 커서 기본값을 적용하고 SQL/바인딩/매핑이 올바른지 검증한다. */
    @Test
    @DisplayName("year=null일 때 커서 기본값(MAX_VALUE/0) 적용 및 bind/매핑 검증")
    void findArtistAlbumsKeyset_yearNull_bindsCursorDefaults_andMaps() {
        // given
        long artistId = 1L;
        Integer year = null;
        Integer cursorReleaseYear = null; // default: Integer.MAX_VALUE
        Long cursorAlbumId = null;        // default: 0
        int limit = 2;

        DatabaseClient.GenericExecuteSpec spec = mock(DatabaseClient.GenericExecuteSpec.class, RETURNS_DEEP_STUBS);

        @SuppressWarnings("unchecked")
        RowsFetchSpec<ArtistAlbumsResponse.ArtistAlbumsItemResponse> fetch =
                (RowsFetchSpec<ArtistAlbumsResponse.ArtistAlbumsItemResponse>) mock(RowsFetchSpec.class);

        when(db.sql(AlbumStatsSql.SQL_ARTIST_ALBUMS_ALL)).thenReturn(spec);
        when(spec.bind(anyInt(), any())).thenReturn(spec);

        ArgumentCaptor<BiFunction<Row, RowMetadata, ArtistAlbumsResponse.ArtistAlbumsItemResponse>> mapperCaptor =
                ArgumentCaptor.forClass((Class) BiFunction.class);

        when(spec.map(mapperCaptor.capture())).thenReturn(fetch);

        when(fetch.all()).thenAnswer(inv -> {
            Row row = mock(Row.class);
            RowMetadata meta = mock(RowMetadata.class);

            when(row.get(eq("albumId"), eq(Long.class))).thenReturn(100L);
            when(row.get(eq("albumName"), eq(String.class))).thenReturn("My Album");
            when(row.get(eq("releaseYear"), eq(Integer.class))).thenReturn(2024);

            var mapped = mapperCaptor.getValue().apply(row, meta);
            return Flux.just(mapped);
        });

        // when
        Flux<ArtistAlbumsResponse.ArtistAlbumsItemResponse> result =
                repo.findArtistAlbumsKeyset(artistId, year, cursorReleaseYear, cursorAlbumId, limit);

        // then
        verify(db).sql(AlbumStatsSql.SQL_ARTIST_ALBUMS_ALL);
        verify(spec).bind(0, artistId);
        verify(spec).bind(1, Integer.MAX_VALUE);
        verify(spec).bind(2, Integer.MAX_VALUE);
        verify(spec).bind(3, 0L);
        verify(spec).bind(4, limit);

        StepVerifier.create(result)
                .assertNext(item -> {
                    assertThat(item.albumId()).isEqualTo(100L);
                    assertThat(item.albumName()).isEqualTo("My Album");
                    assertThat(item.releaseYear()).isEqualTo(2024);
                })
                .verifyComplete();
    }
}