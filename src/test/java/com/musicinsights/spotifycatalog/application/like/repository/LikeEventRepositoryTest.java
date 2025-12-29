package com.musicinsights.spotifycatalog.application.like.repository;

import com.musicinsights.spotifycatalog.application.like.dto.response.TopLikeResponse;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link LikeEventRepository} 단위 테스트.
 *
 * <p>DatabaseClient 체인을 모킹하여 이벤트 삽입 및 Top 증가량 조회 시
 * SQL 선택, bind 값(from/limit), row 매핑 동작을 검증한다.</p>
 */
@DisplayName("like event repo 테스트")
class LikeEventRepositoryTest {

    private DatabaseClient db;
    private LikeEventRepository repo;

    @BeforeEach
    void setUp() {
        this.db = mock(DatabaseClient.class, RETURNS_DEEP_STUBS);
        this.repo = new LikeEventRepository(db);
    }

    /**
     * insertEvent가 trackId를 바인딩하여 insert 쿼리를 실행하고 rowsUpdated 결과를 반환하는지 검증한다.
     */
    @Test
    @DisplayName("이벤트 삽입 시 trackId 바인딩 및 rowsUpdated 결과 반환 검증")
    void insertEvent_bindsAndReturnsRowsUpdated() {
        // given
        long trackId = 10L;

        DatabaseClient.GenericExecuteSpec spec =
                mock(DatabaseClient.GenericExecuteSpec.class, RETURNS_DEEP_STUBS);

        when(db.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyInt(), any())).thenReturn(spec);
        when(spec.fetch().rowsUpdated()).thenReturn(Mono.just(1L));

        // when
        Mono<Long> mono = repo.insertEvent(trackId);

        // then
        StepVerifier.create(mono)
                .expectNext(1L)
                .verifyComplete();

        verify(spec).bind(0, trackId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(db).sql(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("INSERT INTO track_like_event")
                .contains("VALUES");
    }

    /**
     * findTopIncreased가 from(LocalDateTime)/limit를 바인딩하고,
     * row를 {@link TopLikeResponse}로 매핑하여 Flux로 반환하는지 검증한다.
     */
    @Test
    @DisplayName("Top 증가량 조회 시 시간/limit 바인딩 및 결과 매핑 검증")
    void findTopIncreased_bindsFromAndLimit_andMapsRows() {
        // given
        int windowMinutes = 30;
        int limit = 2;

        DatabaseClient.GenericExecuteSpec spec =
                mock(DatabaseClient.GenericExecuteSpec.class, RETURNS_DEEP_STUBS);

        @SuppressWarnings("unchecked")
        RowsFetchSpec<TopLikeResponse> fetch =
                (RowsFetchSpec<TopLikeResponse>) mock(RowsFetchSpec.class);

        when(db.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyInt(), any())).thenReturn(spec);

        ArgumentCaptor<BiFunction<Row, RowMetadata, TopLikeResponse>> mapperCaptor =
                ArgumentCaptor.forClass((Class) BiFunction.class);

        when(spec.map(mapperCaptor.capture())).thenReturn(fetch);

        when(fetch.all()).thenAnswer(inv -> {
            Row row1 = mock(Row.class);
            RowMetadata meta = mock(RowMetadata.class);
            when(row1.get("track_id", Long.class)).thenReturn(1L);
            when(row1.get("inc_count", Long.class)).thenReturn(5L);

            Row row2 = mock(Row.class);
            when(row2.get("track_id", Long.class)).thenReturn(2L);
            when(row2.get("inc_count", Long.class)).thenReturn(3L);

            TopLikeResponse r1 = mapperCaptor.getValue().apply(row1, meta);
            TopLikeResponse r2 = mapperCaptor.getValue().apply(row2, meta);

            return Flux.just(r1, r2);
        });

        // bind(0, from)에서 from 값 캡처해서 범위 검증
        ArgumentCaptor<Object> bind0Captor = ArgumentCaptor.forClass(Object.class);

        // bind(0, ...)은 LocalDateTime이 들어가야 함
        when(spec.bind(eq(0), bind0Captor.capture())).thenReturn(spec);

        // when
        Flux<TopLikeResponse> flux = repo.findTopIncreased(windowMinutes, limit);

        // then (매핑 결과)
        StepVerifier.create(flux)
                .assertNext(r -> {
                    assertThat(r.trackId()).isEqualTo(1L);
                    assertThat(r.incCount()).isEqualTo(5L);
                })
                .assertNext(r -> {
                    assertThat(r.trackId()).isEqualTo(2L);
                    assertThat(r.incCount()).isEqualTo(3L);
                })
                .verifyComplete();

        // then (bind 검증)
        verify(spec).bind(eq(1), eq(limit));

        Object v = bind0Captor.getValue();
        assertThat(v).isInstanceOf(LocalDateTime.class);

        LocalDateTime actualFrom = (LocalDateTime) v;

        // now - windowMinutes 근처인지 (±3초 허용)
        LocalDateTime expected = LocalDateTime.now().minusMinutes(windowMinutes);
        Duration diff = Duration.between(actualFrom, expected).abs();
        assertThat(diff).isLessThan(Duration.ofSeconds(3));

        // SQL 키워드 포함 검증
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(db).sql(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("FROM track_like_event")
                .contains("WHERE created_at >= ?")
                .contains("LIMIT ?");
    }
}
