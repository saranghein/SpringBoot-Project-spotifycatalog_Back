package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * {@link BatchSqlSupport} 유틸 메서드에 대한 단위 테스트.
 *
 * <p>DB에 실제로 접근하지 않고, chunk 분할/합산 로직 및 바인딩 유틸(bindOrNull)을 검증한다.</p>
 */
@DisplayName("batch sql support 테스트")
class BatchSqlSupportTest {

    /**
     * 테스트용 {@link BatchSqlSupport} 구현체.
     *
     * <p>이 테스트에서는 DB를 사용하지 않으므로 {@link DatabaseClient}는 mock으로 주입한다.</p>
     */
    static class TestSupport extends BatchSqlSupport {
        TestSupport(DatabaseClient db) { super(db); }
    }

    /** 테스트 대상 지원 클래스 인스턴스 */
    private final TestSupport support = new TestSupport(Mockito.mock(DatabaseClient.class));

    /**
     * chunkedSum에 null 또는 빈 리스트를 주면 0을 반환하는지 검증한다.
     */
    @DisplayName("chunkedSum에 null 또는 빈 리스트를 주면 0을 반환하는지 검증")
    @Test
    void chunkedSum_nullOrEmpty_returns0() {
        StepVerifier.create(support.chunkedSum(null, 3, xs -> Mono.just(1L)))
                .expectNext(0L)
                .verifyComplete();

        StepVerifier.create(support.chunkedSum(List.of(), 3, xs -> Mono.just(1L)))
                .expectNext(0L)
                .verifyComplete();
    }

    /**
     * chunkedSum이 입력을 CHUNK 단위로 분할하고, 각 chunk 결과를 합산해 반환하는지 검증한다.
     *
     * <p>또한 onceFn이 chunk 순서대로 호출되며, 전달된 chunk 내용이 기대와 동일한지 확인한다.</p>
     */
    @DisplayName("chunkedSum이 입력을 CHUNK 단위로 분할하고, 각 chunk 결과를 합산해 반환하는지 검증")
    @Test
    void chunkedSum_splitsIntoChunks_andSumsResults_inOrder() {
        // items: 1..10, chunk=4 -> [1..4], [5..8], [9..10] => 3번 호출
        List<Integer> items = List.of(1,2,3,4,5,6,7,8,9,10);

        AtomicInteger calls = new AtomicInteger(0);
        List<List<Integer>> received = new ArrayList<>();

        Function<List<Integer>, Mono<Long>> onceFn = chunk -> {
            calls.incrementAndGet();
            received.add(List.copyOf(chunk));
            // 각 chunk의 합을 반환
            long sum = chunk.stream().mapToLong(Integer::longValue).sum();
            return Mono.just(sum);
        };

        StepVerifier.create(support.chunkedSum(items, 4, onceFn))
                .expectNext(55L) // 1~10 합
                .verifyComplete();

        Assertions.assertEquals(3, calls.get());
        Assertions.assertEquals(List.of(1,2,3,4), received.get(0));
        Assertions.assertEquals(List.of(5,6,7,8), received.get(1));
        Assertions.assertEquals(List.of(9,10), received.get(2));
    }


    /**
     * chunkedSum이 concatMap 기반으로 순차 실행(sequential)되는지 검증한다.
     *
     * <p>즉, 앞 chunk 처리 흐름이 이어지는 순서로 onceFn이 호출되는지를 확인한다.</p>
     */
    @DisplayName("chunkedSum이 concatMap 기반으로 순차 실행(sequential)되는지 검증")
    @Test
    void chunkedSum_usesConcatMap_soOnceFnIsSequential() {
        // concatMap이면 1번 chunk가 끝나기 전 2번 chunk가 시작되지 않음
        List<Integer> items = List.of(1,2,3,4,5,6);
        List<Integer> order = new ArrayList<>();

        Function<List<Integer>, Mono<Long>> onceFn = chunk -> {
            // 시작 기록
            order.add(chunk.get(0)); // 각 chunk의 첫 원소를 기록해 순서 확인
            return Mono.just(1L);
        };

        StepVerifier.create(support.chunkedSum(items, 2, onceFn))
                .expectNext(3L) // 3 chunks * 1
                .verifyComplete();

        // chunk=2 -> [1,2], [3,4], [5,6] 순서대로 호출됐는지
        Assertions.assertEquals(List.of(1,3,5), order);
    }

    /**
     * bindOrNull에 non-null 값을 전달하면 bind가 호출되는지 검증한다.
     */
    @DisplayName("bindOrNull에 non-null 값을 전달하면 bind가 호출되는지 검증")
    @Test
    void bindOrNull_valueNotNull_callsBind() {
        @SuppressWarnings("unchecked")
        DatabaseClient.GenericExecuteSpec spec = Mockito.mock(DatabaseClient.GenericExecuteSpec.class);

        // bind는 chaining 되므로 자기 자신 반환하도록
        Mockito.when(spec.bind(Mockito.anyString(), Mockito.any())).thenReturn(spec);

        DatabaseClient.GenericExecuteSpec out = support.bindOrNull(spec, "x", "hello", String.class);

        Assertions.assertSame(spec, out);
        Mockito.verify(spec).bind("x", "hello");
        Mockito.verify(spec, Mockito.never()).bindNull(Mockito.anyString(), Mockito.any());
    }

    /**
     * bindOrNull에 null 값을 전달하면 bindNull이 호출되는지 검증한다.
     */
    @DisplayName("bindOrNull에 null 값을 전달하면 bindNull이 호출되는지 검증")
    @Test
    void bindOrNull_valueNull_callsBindNull() {
        @SuppressWarnings("unchecked")
        DatabaseClient.GenericExecuteSpec spec = Mockito.mock(DatabaseClient.GenericExecuteSpec.class);

        Mockito.when(spec.bindNull(Mockito.anyString(), Mockito.any())).thenReturn(spec);

        DatabaseClient.GenericExecuteSpec out = support.bindOrNull(spec, "x", null, String.class);

        Assertions.assertSame(spec, out);
        Mockito.verify(spec).bindNull("x", String.class);
        Mockito.verify(spec, Mockito.never()).bind(Mockito.anyString(), Mockito.any());
    }
}