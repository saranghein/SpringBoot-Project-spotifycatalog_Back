package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * R2DBC(WebFlux) 환경에서 Reactive 트랜잭션을 사용하기 위한 설정 클래스입니다.
 * <p>
 * {@link ReactiveTransactionManager}와 {@link TransactionalOperator}를 Bean으로 등록하여,
 * 서비스 레이어에서 트랜잭션 경계를 선언적으로 적용할 수 있게 합니다.
 */
@Configuration
public class R2dbcTxConfig {

    /**
     * R2DBC용 트랜잭션 매니저를 생성합니다.
     *
     * @param cf R2DBC {@link ConnectionFactory}
     * @return Reactive 트랜잭션 매니저
     */
    @Bean
    public ReactiveTransactionManager reactiveTransactionManager(ConnectionFactory cf) {
        return new R2dbcTransactionManager(cf);
    }

    /**
     * Reactive 트랜잭션을 코드로 적용하기 위한 {@link TransactionalOperator}를 생성합니다.
     *
     * @param tm Reactive 트랜잭션 매니저
     * @return 트랜잭션 적용용 operator
     */
    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager tm) {
        return TransactionalOperator.create(tm); // TransactionalOperator.create(tm) 등록
    }
}
