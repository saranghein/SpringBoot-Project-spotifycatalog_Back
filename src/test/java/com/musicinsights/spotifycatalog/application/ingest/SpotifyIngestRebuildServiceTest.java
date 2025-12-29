package com.musicinsights.spotifycatalog.application.ingest;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo.ArtistAlbumCountYearRepo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

/**
 * {@link SpotifyIngestRebuildService} 단위 테스트.
 *
 * <p>rebuild가 {@link ArtistAlbumCountYearRepo#rebuild()}를 위임 호출하고,
 * 결과/에러를 그대로 전파하는지 검증한다.</p>
 */
@DisplayName("spotify ingest rebuild 서비스 테스트")
class SpotifyIngestRebuildServiceTest {

    /**
     * rebuild가 repo.rebuild를 1회 호출하고,
     * 반환된 값을 그대로 반환하는지 검증한다.
     */
    @Test
    @DisplayName("rebuild는 repo.rebuild를 호출하고 결과를 그대로 반환한다")
    void rebuild_delegatesToRepo_andReturnsValue() {
        // given
        ArtistAlbumCountYearRepo repo = mock(ArtistAlbumCountYearRepo.class);
        SpotifyIngestRebuildService service = new SpotifyIngestRebuildService(repo);

        when(repo.rebuild()).thenReturn(Mono.just(42L));

        // when / then
        StepVerifier.create(service.rebuild())
                .expectNext(42L)
                .verifyComplete();

        verify(repo, times(1)).rebuild();
        verifyNoMoreInteractions(repo);
    }

    /**
     * repo.rebuild에서 에러가 발생하면
     * service.rebuild도 동일한 에러를 전파하는지 검증한다.
     */
    @Test
    @DisplayName("repo에서 에러가 나면 rebuild는 에러를 전파한다")
    void rebuild_propagatesError() {
        // given
        ArtistAlbumCountYearRepo repo = mock(ArtistAlbumCountYearRepo.class);
        SpotifyIngestRebuildService service = new SpotifyIngestRebuildService(repo);

        when(repo.rebuild()).thenReturn(Mono.error(new RuntimeException("boom")));

        // when / then
        StepVerifier.create(service.rebuild())
                .expectErrorMessage("boom")
                .verify();

        verify(repo, times(1)).rebuild();
        verifyNoMoreInteractions(repo);
    }
}
