package com.musicinsights.spotifycatalog.application.like.service;

import com.musicinsights.spotifycatalog.application.like.dto.response.LikeResponse;
import com.musicinsights.spotifycatalog.application.like.dto.response.TopLikeResponse;
import com.musicinsights.spotifycatalog.application.like.dto.response.LikeIncResponse;
import com.musicinsights.spotifycatalog.application.like.repository.LikeCounterRepository;
import com.musicinsights.spotifycatalog.application.like.repository.LikeEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


/**
 * {@link LikeService} 단위 테스트.
 *
 * <p>의존 저장소/트랜잭션 오퍼레이터는 Mockito로 mocking하고,
 * Reactor {@link StepVerifier}로 반환 Publisher의 값/종료/에러를 검증한다.</p>
 */
@DisplayName("like service 테스트")
class LikeServiceTest {

    private LikeCounterRepository counterRepo;
    private LikeEventRepository eventRepo;
    private TransactionalOperator tx;

    private LikeService service;

    @BeforeEach
    void setUp() {
        this.counterRepo = mock(LikeCounterRepository.class);
        this.eventRepo = mock(LikeEventRepository.class);
        this.tx = mock(TransactionalOperator.class);

        this.service = new LikeService(counterRepo, eventRepo, tx);
    }

    /**
     * 좋아요 요청 시 트랜잭션 내부에서
     * (1) 이벤트 기록 후 (2) 카운터 증가가 순서대로 수행되고,
     * 결과가 {@link LikeResponse}로 매핑되어 반환되는지 검증한다.
     */
    @Test
    @DisplayName("좋아요 요청 시 트랜잭션 내에서 이벤트 기록 후 LikeResponse로 반환되는지 검증")
    void like_runsInTransaction_andReturnsLikeResponse() {
        // given
        long trackId = 10L;

        when(eventRepo.insertEvent(trackId)).thenReturn(Mono.just(1L));
        when(counterRepo.incrementAndGet(trackId)).thenReturn(Mono.just(5L));

        when(tx.transactional(any(Mono.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // when
        Mono<LikeResponse> mono = service.like(trackId);

        // then
        StepVerifier.create(mono)
                .assertNext(r -> {
                    assertThat(r.trackId()).isEqualTo(10L);
                    assertThat(r.likeCount()).isEqualTo(5L);
                })
                .verifyComplete();

        verify(tx).transactional(any(Mono.class));

        InOrder inOrder = inOrder(eventRepo, counterRepo);
        inOrder.verify(eventRepo).insertEvent(trackId);
        inOrder.verify(counterRepo).incrementAndGet(trackId);

        verifyNoMoreInteractions(eventRepo, counterRepo);
    }

    /**
     * 특정 트랙의 현재 좋아요 카운트 조회가
     * {@link LikeCounterRepository}에 위임되는지 검증한다.
     */
    @Test
    @DisplayName("카운트 조회는 카운터 저장소에 위임되는지 검증")
    void getCount_delegatesToCounterRepository() {
        // given
        long trackId = 7L;
        when(counterRepo.findCount(trackId)).thenReturn(Mono.just(12L));

        // when
        Mono<Long> mono = service.getCount(trackId);

        // then
        StepVerifier.create(mono)
                .expectNext(12L)
                .verifyComplete();

        verify(counterRepo).findCount(trackId);
        verifyNoMoreInteractions(counterRepo);
        verifyNoInteractions(eventRepo, tx);
    }

    /**
     * 최근 windowMinutes 동안의 좋아요 증가 Top 목록 조회 결과가
     * {@link LikeIncResponse} -> {@link TopLikeResponse}로 올바르게 매핑되는지 검증한다.
     */
    @Test
    @DisplayName("Top 조회 요청 시 이벤트 저장소 결과가 TopLikeResponse로 매핑되어 반환되는지 검증")
    void topIncreased_mapsToTopLikeResponse() {
        // given
        int windowMinutes = 60;
        int limit = 2;

        when(eventRepo.findTopIncreased(windowMinutes, limit))
                .thenReturn(Flux.just(
                        new LikeIncResponse(1L, 10L, "t1", "a1"),
                        new LikeIncResponse(2L, 7L, "t2", "a2")
                ));

        // when
        Flux<TopLikeResponse> flux = service.topIncreased(windowMinutes, limit);

        // then
        StepVerifier.create(flux)
                .assertNext(r -> {
                    assertThat(r.trackId()).isEqualTo(1L);
                    assertThat(r.incCount()).isEqualTo(10L);
                    assertThat(r.title()).isEqualTo("t1");
                    assertThat(r.artistNames()).isEqualTo("a1");
                })
                .assertNext(r -> {
                    assertThat(r.trackId()).isEqualTo(2L);
                    assertThat(r.incCount()).isEqualTo(7L);
                    assertThat(r.title()).isEqualTo("t2");
                    assertThat(r.artistNames()).isEqualTo("a2");
                })
                .verifyComplete();

        verify(eventRepo).findTopIncreased(windowMinutes, limit);
        verifyNoMoreInteractions(eventRepo);
        verifyNoInteractions(counterRepo, tx);
    }

    /**
     * 이벤트 기록 단계에서 에러가 발생하면,
     * 카운터 증가가 호출되지 않고 동일 에러로 종료되는지 검증한다.
     */
    @Test
    @DisplayName("이벤트 기록이 실패하면 카운터 증가가 호출되지 않고 에러로 종료되는지 검증")
    void like_whenInsertEventFails_doesNotIncrement() {
        // given
        long trackId = 10L;
        RuntimeException boom = new RuntimeException("boom");

        when(eventRepo.insertEvent(trackId)).thenReturn(Mono.error(boom));
        when(tx.transactional(any(Mono.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // when
        Mono<LikeResponse> mono = service.like(trackId);

        // then
        StepVerifier.create(mono)
                .expectErrorSatisfies(e -> assertThat(e).isSameAs(boom))
                .verify();

        verify(eventRepo).insertEvent(trackId);
        verify(tx).transactional(any(Mono.class));

        verifyNoInteractions(counterRepo);
    }
}
