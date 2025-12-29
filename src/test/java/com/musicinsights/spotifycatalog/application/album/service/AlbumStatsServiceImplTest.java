package com.musicinsights.spotifycatalog.application.album.service;

import com.musicinsights.spotifycatalog.application.album.dto.response.ArtistAlbumStatsResponse;
import com.musicinsights.spotifycatalog.application.album.dto.response.ArtistAlbumsResponse;
import com.musicinsights.spotifycatalog.application.common.pagination.KeysetPageAssembler;
import com.musicinsights.spotifycatalog.application.album.repository.AlbumStatsRepository;
import com.musicinsights.spotifycatalog.application.common.error.NotFoundException;
import com.musicinsights.spotifycatalog.application.common.pagination.CursorCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * {@link AlbumStatsServiceImpl} 단위 테스트.
 */
@DisplayName("album stats service 테스트")
class AlbumStatsServiceImplTest {

    private AlbumStatsRepository repo;
    private AlbumStatsServiceImpl service;

    @BeforeEach
    void setUp() {
        this.repo = mock(AlbumStatsRepository.class);
        this.service = new AlbumStatsServiceImpl(repo);
    }

    /** cursor가 없을 때 기본 커서(Long.MAX_VALUE/0)로 keyset 조회하는지 검증한다. */
    @Test
    @DisplayName("커서가 없을 때 기본 커서로 조회하고 결과를 합쳐 반환 검증")
    void albumStats_cursorNull_usesDefaultCursor_andCombines() {
        // given
        Integer year = null;
        String cursor = null;
        int size = 2;

        int fetchSize = KeysetPageAssembler.fetchSize(size);

        when(repo.countAlbums(year)).thenReturn(Mono.just(50L));
        when(repo.findArtistAlbumCounts(year, Long.MAX_VALUE, 0L, fetchSize))
                .thenReturn(Flux.just(
                        new ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse(1L, "A", 10L),
                        new ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse(2L, "B", 9L)
                ));

        // when
        Mono<ArtistAlbumStatsResponse.ArtistAlbumStatsResultResponse> mono =
                service.getArtistAlbumStats(year, cursor, size);

        // then
        StepVerifier.create(mono)
                .assertNext(res -> {
                    assertThat(res).isNotNull();
                    assertThat(res.year()).isNull();
                    assertThat(res.totalAlbums()).isEqualTo(50L);
                    assertThat(res.page()).isNotNull();
                })
                .verifyComplete();

        verify(repo).countAlbums(year);
        verify(repo).findArtistAlbumCounts(year, Long.MAX_VALUE, 0L, fetchSize);
        verifyNoMoreInteractions(repo);
    }

    /** cursor가 있을 때 디코딩한 값이 repo 호출 파라미터로 전달되는지 검증한다. */
    @Test
    @DisplayName("커서가 있을 때 디코딩 값으로 keyset 조회 파라미터가 전달되는지 검증")
    void albumStats_cursorProvided_decodesAndPassesToRepo() {
        // given
        Integer year = 2024;
        int size = 3;
        int fetchSize = KeysetPageAssembler.fetchSize(size);

        var decoded = new ArtistAlbumStatsResponse.ArtistAlbumStatsCursor(10L, 7L);
        String cursor = CursorCodec.encode(decoded);

        when(repo.countAlbums(year)).thenReturn(Mono.just(123L));
        when(repo.findArtistAlbumCounts(year, 10L, 7L, fetchSize))
                .thenReturn(Flux.just(
                        new ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse(7L, "X", 10L)
                ));

        // when
        Mono<ArtistAlbumStatsResponse.ArtistAlbumStatsResultResponse> mono =
                service.getArtistAlbumStats(year, cursor, size);

        // then
        StepVerifier.create(mono)
                .assertNext(res -> {
                    assertThat(res.year()).isEqualTo(2024);
                    assertThat(res.totalAlbums()).isEqualTo(123L);
                    assertThat(res.page()).isNotNull();
                })
                .verifyComplete();

        verify(repo).countAlbums(year);
        verify(repo).findArtistAlbumCounts(year, 10L, 7L, fetchSize);
        verifyNoMoreInteractions(repo);
    }

    /** 존재하지 않는 artistId 요청 시 {@link NotFoundException}이 발생하는지 검증한다. */
    @Test
    @DisplayName("아티스트가 존재하지 않으면 NotFoundException 발생 검증")
    void artistAlbums_artistMissing_throwsNotFound() {
        // given
        Long artistId = 999L;
        Integer year = null;
        String cursor = null;
        int size = 2;

        // requireArtistExists에서 false
        when(repo.existsArtist(artistId)).thenReturn(Mono.just(false));

        when(repo.countArtistAlbums(eq(artistId), eq(year))).thenReturn(Mono.just(0L));
        when(repo.findArtistAlbumsKeyset(eq(artistId), eq(year), any(), any(), anyInt()))
                .thenReturn(Flux.empty());

        // when
        Mono<ArtistAlbumsResponse.ArtistAlbumsResultResponse> mono =
                service.getArtistAlbums(artistId, year, cursor, size);

        // then
        StepVerifier.create(mono)
                .expectErrorSatisfies(err -> {
                    NotFoundException e = (NotFoundException) err;
                    assertThat(err.getMessage()).isEqualTo("ARTIST_NOT_FOUND");
                })
                .verify();


        verify(repo).existsArtist(artistId);
    }

    /** year 조건이 없을 때 cursor의 releaseYear/albumId가 그대로 전달되는지 검증한다. */
    @Test
    @DisplayName("연도 조건이 없을 때 커서의 releaseYear/albumId가 그대로 전달되는지 검증")
    void artistAlbums_yearNull_passesReleaseYearAndAlbumIdCursor() {
        // given
        Long artistId = 1L;
        Integer year = null;
        int size = 2;
        int fetchSize = KeysetPageAssembler.fetchSize(size);

        var decoded = new ArtistAlbumsResponse.ArtistAlbumsCursor(2024, 100L);
        String cursor = CursorCodec.encode(decoded);

        when(repo.existsArtist(artistId)).thenReturn(Mono.just(true));
        when(repo.countArtistAlbums(artistId, year)).thenReturn(Mono.just(3L));
        when(repo.findArtistAlbumsKeyset(artistId, year, 2024, 100L, fetchSize))
                .thenReturn(Flux.just(
                        new ArtistAlbumsResponse.ArtistAlbumsItemResponse(200L, "Album", 2020)
                ));

        // when
        Mono<ArtistAlbumsResponse.ArtistAlbumsResultResponse> mono =
                service.getArtistAlbums(artistId, year, cursor, size);

        // then
        StepVerifier.create(mono)
                .assertNext(res -> {
                    assertThat(res.artistId()).isEqualTo(1L);
                    assertThat(res.year()).isNull();
                    assertThat(res.totalAlbums()).isEqualTo(3L);
                    assertThat(res.page()).isNotNull();
                })
                .verifyComplete();

        verify(repo).existsArtist(artistId);
        verify(repo).countArtistAlbums(artistId, year);
        verify(repo).findArtistAlbumsKeyset(artistId, year, 2024, 100L, fetchSize);
        verifyNoMoreInteractions(repo);
    }
}
