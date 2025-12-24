package com.musicinsights.spotifycatalog.application.ingest;

import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.TrackRaw;
import com.musicinsights.spotifycatalog.infrastructure.mapper.TrackRawBatchMapper;
import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo.*;
import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/**
 * {@link SpotifyIngestService} 단위 테스트.
 *
 * <p>ingestBatch가 배치 처리 오케스트레이션(추출 → upsert/insertIgnore → id 조회 → 관계 row 생성)을
 * 올바른 순서로 수행하며, 전체 흐름이 트랜잭션({@link TransactionalOperator})으로 감싸지는지 검증한다.</p>
 *
 * <p>또한 중간 단계에서 에러가 발생할 경우 에러가 전파되고,
 * 이후 단계가 호출되지 않는지(중단)도 확인한다.</p>
 */
@DisplayName("배치 오케스트레이션 테스트")
class SpotifyIngestServiceTest {
    /**
     * ingestBatch가 전체 ingest 흐름을 순서대로 호출하고,
     * transactional로 래핑되어 실행되는지 검증한다.
     */
    @DisplayName("전체 ingest 순서대로 호출, transactional로 래핑되어 실행되는지 검증")
    @Test
    void ingestBatch_orchestratesAllSteps_andWrapsInTransaction() {
        // given
        ArtistRepo artistRepo = mock(ArtistRepo.class);
        AlbumRepo albumRepo = mock(AlbumRepo.class);
        AlbumArtistRepo albumArtistRepo = mock(AlbumArtistRepo.class);
        TrackRepo trackRepo = mock(TrackRepo.class);
        TrackArtistRepo trackArtistRepo = mock(TrackArtistRepo.class);
        TrackLyricsRepo trackLyricsRepo = mock(TrackLyricsRepo.class);
        AudioRepo audioRepo = mock(AudioRepo.class);

        IngestFacade ingestDb = new IngestFacade(
                artistRepo, albumRepo, albumArtistRepo,
                trackRepo, trackArtistRepo,
                trackLyricsRepo, audioRepo
        );

        TransactionalOperator tx = mock(TransactionalOperator.class);
        TrackRawBatchMapper mapper = mock(TrackRawBatchMapper.class);

        SpotifyIngestService service = new SpotifyIngestService(ingestDb, tx, mapper);

        List<TrackRaw> batch = List.of(new TrackRaw(), new TrackRaw());

        // mapper.extract 결과
        List<String> artistNames = List.of("IU", "BTS");
        List<AlbumRow> albums = List.of(new AlbumRow("AlbumA", LocalDate.of(2020, 1, 1)));
        TrackRawBatchMapper.BatchExtract ex = new TrackRawBatchMapper.BatchExtract(artistNames, albums);
        when(mapper.extract(batch)).thenReturn(ex);

        // id maps
        Map<String, Long> artistIdMap = Map.of("IU", 10L, "BTS", 11L);
        Map<AlbumRow, Long> albumIdMap = Map.of(albums.get(0), 100L);

        // album_artist rows
        List<AlbumArtistRow> aaRows = List.of(new AlbumArtistRow(100L, 10L));
        when(mapper.buildAlbumArtistRows(batch, artistIdMap, albumIdMap)).thenReturn(aaRows);

        // track build
        List<TrackRow> trackRows = List.of(
                new TrackRow("h1", "t1", null, null, null, null, false, null, null),
                new TrackRow("h2", "t2", null, null, null, null, false, null, null)
        );
        List<String> hashes = List.of("h1", "h2");
        TrackRawBatchMapper.TrackBuild tb = new TrackRawBatchMapper.TrackBuild(trackRows, hashes);
        when(mapper.buildTrackRows(batch, albumIdMap)).thenReturn(tb);

        Map<String, Long> trackIdMap = Map.of("h1", 1000L, "h2", 2000L);

        // relations
        List<TrackArtistRow> taRows = List.of(new TrackArtistRow(1000L, 10L));
        List<TrackLyricsRow> lyricRows = List.of(new TrackLyricsRow(1000L, "ly"));
        List<AudioRow> audioRows = List.of(
                new AudioRow(1000L, null, null, null, null, null, null, null, null, null, null, null)
        );
        TrackRawBatchMapper.TrackRelations rel = new TrackRawBatchMapper.TrackRelations(taRows, lyricRows, audioRows);
        when(mapper.buildTrackRelations(batch, trackRows, trackIdMap, artistIdMap)).thenReturn(rel);

        // repo stubs (rowsUpdated)
        when(artistRepo.insertIgnore(artistNames)).thenReturn(Mono.just(2L));
        when(artistRepo.fetchArtistIdsByName(artistNames)).thenReturn(Mono.just(artistIdMap));

        when(albumRepo.upsert(albums)).thenReturn(Mono.just(1L));
        when(albumRepo.fetchAlbumIdsByName(albums)).thenReturn(Mono.just(albumIdMap));

        when(albumArtistRepo.insertIgnore(aaRows)).thenReturn(Mono.just(1L));

        when(trackRepo.upsert(trackRows)).thenReturn(Mono.just(2L));
        when(trackRepo.fetchTrackIdsByHash(hashes)).thenReturn(Mono.just(trackIdMap));

        when(trackArtistRepo.insertIgnore(taRows)).thenReturn(Mono.just(1L));
        when(trackLyricsRepo.upsert(lyricRows)).thenReturn(Mono.just(1L));
        when(audioRepo.upsertAudioFeatures(audioRows)).thenReturn(Mono.just(1L));

        // transactional passthrough (실제 트랜잭션 대신 인자로 받은 Mono를 그대로 반환)
        when(tx.transactional(any(Mono.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // when / then
        StepVerifier.create(service.ingestBatch(batch))
                .assertNext(v -> assertNotNull(v)) // 마지막 단계의 Long이 나오면 OK
                .verifyComplete();

        // tx 적용 확인
        verify(tx, times(1)).transactional(any(Mono.class));

        // 호출 순서 검증
        InOrder inOrder = inOrder(
                mapper,
                artistRepo,
                albumRepo,
                albumArtistRepo,
                trackRepo,
                trackArtistRepo,
                trackLyricsRepo,
                audioRepo
        );

        inOrder.verify(mapper).extract(batch);

        inOrder.verify(artistRepo).insertIgnore(artistNames);
        inOrder.verify(artistRepo).fetchArtistIdsByName(artistNames);

        inOrder.verify(albumRepo).upsert(albums);
        inOrder.verify(albumRepo).fetchAlbumIdsByName(albums);

        inOrder.verify(mapper).buildAlbumArtistRows(batch, artistIdMap, albumIdMap);
        inOrder.verify(albumArtistRepo).insertIgnore(aaRows);

        inOrder.verify(mapper).buildTrackRows(batch, albumIdMap);
        inOrder.verify(trackRepo).upsert(trackRows);
        inOrder.verify(trackRepo).fetchTrackIdsByHash(hashes);

        inOrder.verify(mapper).buildTrackRelations(batch, trackRows, trackIdMap, artistIdMap);

        inOrder.verify(trackArtistRepo).insertIgnore(taRows);
        inOrder.verify(trackLyricsRepo).upsert(lyricRows);
        inOrder.verify(audioRepo).upsertAudioFeatures(audioRows);
    }

    /**
     * 중간 단계(artistRepo.fetchArtistIdsByName)에서 에러가 발생하면
     * 에러가 전파되고 이후 단계가 호출되지 않는지 검증한다.
     */
    @DisplayName("중간 단계에서 에러가 발생하면 이후 단계가 호출되지 않는지 검증")
    @Test
    void ingestBatch_propagatesError_whenArtistFetchFails_andStopsDownstream() {
        // given
        ArtistRepo artistRepo = mock(ArtistRepo.class);
        AlbumRepo albumRepo = mock(AlbumRepo.class);
        AlbumArtistRepo albumArtistRepo = mock(AlbumArtistRepo.class);
        TrackRepo trackRepo = mock(TrackRepo.class);
        TrackArtistRepo trackArtistRepo = mock(TrackArtistRepo.class);
        TrackLyricsRepo trackLyricsRepo = mock(TrackLyricsRepo.class);
        AudioRepo audioRepo = mock(AudioRepo.class);

        IngestFacade ingestDb = new IngestFacade(
                artistRepo, albumRepo, albumArtistRepo,
                trackRepo, trackArtistRepo,
                trackLyricsRepo, audioRepo
        );

        TransactionalOperator tx = mock(TransactionalOperator.class);
        TrackRawBatchMapper mapper = mock(TrackRawBatchMapper.class);

        SpotifyIngestService service = new SpotifyIngestService(ingestDb, tx, mapper);

        List<TrackRaw> batch = List.of(new TrackRaw());

        List<String> artistNames = List.of("IU");
        List<AlbumRow> albums = List.of();

        when(mapper.extract(batch)).thenReturn(new TrackRawBatchMapper.BatchExtract(artistNames, albums));

        when(artistRepo.insertIgnore(artistNames)).thenReturn(Mono.just(1L));
        when(artistRepo.fetchArtistIdsByName(artistNames)).thenReturn(Mono.error(new RuntimeException("fail")));

        when(tx.transactional(any(Mono.class))).thenAnswer(inv -> inv.getArgument(0));

        // when / then
        StepVerifier.create(service.ingestBatch(batch))
                .expectErrorMessage("fail")
                .verify();

        // downstream은 호출되면 안 됨
        verifyNoInteractions(albumRepo, albumArtistRepo, trackRepo, trackArtistRepo, trackLyricsRepo, audioRepo);
    }
}