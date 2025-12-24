package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc;

import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

/**
 * R2DBC 기반 배치 SQL 처리를 위한 공통 유틸/베이스 클래스입니다.
 * <p>
 * 대량 입력을 chunk 단위로 나누어 순차 실행하고, 처리 결과(rowsUpdated)를 합산하는 기능과
 * null-safe 바인딩 편의 메서드를 제공합니다.
 */
public abstract class BatchSqlSupport {

    /** R2DBC SQL 실행을 위한 DatabaseClient */
    protected final DatabaseClient db;

    /**
     * {@link DatabaseClient}를 주입받아 초기화합니다.
     *
     * @param db R2DBC DatabaseClient
     */
    protected BatchSqlSupport(DatabaseClient db) {
        this.db = db;
    }

    /**
     * 주어진 아이템 목록을 chunk 단위로 분할하여 순차(concat) 처리하고,
     * 각 처리 결과를 합산하여 반환합니다.
     * <p>
     * 순차 실행을 통해 DB 부하/락 경합 및 메모리 사용을 제어합니다.
     *
     * @param items  처리할 전체 아이템 목록
     * @param chunk  한 번에 처리할 chunk 크기
     * @param onceFn chunk 단위로 실행할 함수(각 chunk에 대한 rowsUpdated 반환)
     * @param <T>    아이템 타입
     * @return 처리된 rowsUpdated 합계
     */
    protected <T> Mono<Long> chunkedSum(
            List<T> items,
            int chunk,
            Function<List<T>, Mono<Long>> onceFn
    ) {
        if (items == null || items.isEmpty()) return Mono.just(0L);
        return Flux.fromIterable(items)
                .buffer(chunk)
                .concatMap(onceFn)
                .reduce(0L, Long::sum);
    }

    /**
     * 값이 null인 경우 {@code bindNull}, 아니면 {@code bind}를 수행하는 null-safe 바인딩 헬퍼입니다.
     *
     * @param spec 바인딩 대상 {@link org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec}
     * @param name 파라미터 이름
     * @param value 바인딩할 값(Nullable)
     * @param type null 바인딩 시 사용할 타입
     * @param <V> 값 타입
     * @return 바인딩이 적용된 spec
     */
    protected <V> DatabaseClient.GenericExecuteSpec bindOrNull(
            DatabaseClient.GenericExecuteSpec spec, String name, V value, Class<V> type
    ) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }
}
