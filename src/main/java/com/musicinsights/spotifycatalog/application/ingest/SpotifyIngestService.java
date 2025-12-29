package com.musicinsights.spotifycatalog.application.ingest;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.AlbumArtistRow;
import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.TrackRaw;
import com.musicinsights.spotifycatalog.infrastructure.mapper.TrackRawBatchMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * TrackRaw 배치를 관계형 스키마로 변환하여 DB에 적재(ingest)하는 서비스입니다.
 * <p>
 * 한 배치 단위로 다음 순서로 저장합니다:
 * artist → album → album_artist → track → (track_artist, track_lyrics, audio_feature)
 * <p>
 * 전체 배치 작업은 {@link TransactionalOperator}로 하나의 리액티브 트랜잭션으로 감쌉니다.
 */
@Service
public class SpotifyIngestService {

    /** 배치 적재에 필요한 Repo들을 묶은 파사드 */
    private final IngestFacade ingestDb;

    /** 리액티브 트랜잭션 적용을 위한 operator */
    private final TransactionalOperator tx;

    /** TrackRaw → Row 변환을 담당하는 배치 매퍼 */
    private final TrackRawBatchMapper mapper;

    /**
     * 의존성을 주입받아 서비스를 초기화합니다.
     *
     * @param ingestDb Repo 파사드
     * @param tx 리액티브 트랜잭션 오퍼레이터
     * @param mapper 배치 매퍼
     */
    public SpotifyIngestService(
            IngestFacade ingestDb,
            TransactionalOperator tx,
            TrackRawBatchMapper mapper
    ) {
        this.ingestDb = ingestDb;
        this.tx = tx;
        this.mapper = mapper;
    }

    /**
     * TrackRaw 배치(여러 줄)를 DB에 적재합니다.
     * <p>
     * 배치에서 필요한 엔티티를 먼저 추출한 뒤, ID 매핑을 확보하고
     * 조인/관계 테이블을 포함해 순차적으로 저장합니다.
     *
     * @param batch TrackRaw 배치
     * @return 배치 처리 결과(rowsUpdated 등) 값(최종 단계의 결과)
     */
    public Mono<Long> ingestBatch(List<TrackRaw> batch) {
        var ex = mapper.extract(batch);

        Mono<Long> work =
                ingestDb.artist.insertIgnoreByKey(ex.artists())
                        .then(ingestDb.artist.fetchArtistIdsByKey(ex.artistKeys()))
                        .flatMap(artistIdByKey ->
                                ingestDb.album.upsertByKey(ex.albums())
                                        .then(ingestDb.album.fetchAlbumIdsByKey(ex.albumKeys()))
                                        .flatMap(albumIdByKey ->
                                                ingestAlbumArtist(batch, artistIdByKey, albumIdByKey)
                                                        .then(ingestTracksAndRelations(batch, artistIdByKey, albumIdByKey))
                                        )
                        );

        return tx.transactional(work);
    }

    /**
     * album과 artist 간 조인 매핑(album_artist)을 생성하여 저장합니다.
     *
     * @param batch TrackRaw 배치
     * @param artistIdByKey artistName -> artistId 매핑
     * @param albumIdByKey AlbumRow -> albumId 매핑
     * @return 처리 결과(rowsUpdated 등)
     */
    private Mono<Long> ingestAlbumArtist(
            List<TrackRaw> batch,
            Map<String, Long> artistIdByKey,
            Map<String, Long> albumIdByKey
    ) {
        List<AlbumArtistRow> aaRows = mapper.buildAlbumArtistRows(batch, artistIdByKey, albumIdByKey);
        return ingestDb.albumArtist.insertIgnore(aaRows);
    }

    /**
     * 트랙(track)을 저장한 뒤, track_id를 조회하여 관계/부가 데이터(track_artist, lyrics, audio_feature)를 저장합니다.
     *
     * @param batch TrackRaw 배치
     * @param artistIdByKey artistName -> artistId 매핑
     * @param albumIdByKey AlbumRow -> albumId 매핑
     * @return 처리 결과(rowsUpdated 등)
     */
    private Mono<Long> ingestTracksAndRelations(
            List<TrackRaw> batch,
            Map<String, Long> artistIdByKey,
            Map<String, Long> albumIdByKey
    ) {
        var tb = mapper.buildTrackRows(batch, albumIdByKey);

        return ingestDb.track.upsert(tb.trackRows())
                .then(ingestDb.track.fetchTrackIdsByHash(tb.hashes()))
                .flatMap(trackIdMap -> {
                    var rel = mapper.buildTrackRelations(batch, tb.trackRows(), trackIdMap, artistIdByKey);

                    return ingestDb.trackArtist.insertIgnore(rel.trackArtistRows())
                            .then(ingestDb.trackLyrics.upsert(rel.lyricsRows()))
                            .then(ingestDb.audioFeature.upsertAudioFeatures(rel.audioRows()));
                });
    }
}
