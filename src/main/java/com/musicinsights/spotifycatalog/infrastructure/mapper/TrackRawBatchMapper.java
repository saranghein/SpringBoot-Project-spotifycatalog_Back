package com.musicinsights.spotifycatalog.infrastructure.mapper;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.*;
import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.TrackRaw;
import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.NormalizeUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.musicinsights.spotifycatalog.infrastructure.input.ndjson.NormalizeUtils.*;

/**
 * NDJSON로 읽어온 {@link TrackRaw} 배치를 DB 저장용 Row 모델로 변환하는 매퍼입니다.
 * <p>
 * 1) 배치에서 공통 엔티티(artist/album)를 추출하고
 * 2) album_artist / track / track_artist / track_lyrics / audio_feature 입력 Row들을 생성합니다.
 */
@Component
public class TrackRawBatchMapper {

    /**
     * 배치에서 선행 저장이 필요한 엔티티(아티스트/앨범) 정보를 추출한 결과입니다.
     *
     * @param artistNames 중복 제거된 아티스트 이름 목록(정규화됨)
     * @param albums      중복 제거된 앨범 키 목록(name + releaseDate)
     */
    public record BatchExtract(
            List<String> artistNames,
            List<AlbumRow> albums
    ) {}

    /**
     * 트랙 upsert에 필요한 Row들과, 이후 track_id 조회를 위한 track_hash 목록을 포함한 결과입니다.
     *
     * @param trackRows upsert할 트랙 Row 목록
     * @param hashes    track_hash 목록(입력 순서와 대응)
     */
    public record TrackBuild(
            List<TrackRow> trackRows,
            List<String> hashes
    ) {}

    /**
     * 트랙 저장 이후 생성되는 관계/부가 테이블 입력 Row들을 포함한 결과입니다.
     *
     * @param trackArtistRows track-artist 매핑 Row 목록
     * @param lyricsRows      트랙 가사 Row 목록
     * @param audioRows       오디오 특성 Row 목록
     */
    public record TrackRelations(
            List<TrackArtistRow> trackArtistRows,
            List<TrackLyricsRow> lyricsRows,
            List<AudioRow> audioRows
    ) {}

    /**
     * 배치에서 아티스트 이름과 앨범 키를 추출합니다.
     * <p>
     * - 아티스트: split → 정규화 → null 제거 → distinct
     * - 앨범: (name, releaseDate)로 구성 → 유효 name만 필터 → distinct
     *
     * @param batch TrackRaw 배치
     * @return 추출 결과(artistNames, albums)
     */
    public BatchExtract extract(List<TrackRaw> batch) {
        List<String> artistNames = batch.stream()
                .flatMap(r -> splitArtists(r.artists).stream())
                .map(NormalizeUtils::norm)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<AlbumRow> albums = batch.stream()
                .map(r -> new AlbumRow(
                        norm(r.album),
                        parseDateOrNull(r.releaseDate)
                ))
                .filter(a -> a.name() != null && !a.name().isBlank())
                .distinct()
                .toList();

        return new BatchExtract(artistNames, albums);
    }

    /**
     * 배치 데이터를 기반으로 album_artist 조인 테이블 입력 Row를 생성합니다.
     * <p>
     * albumIdMap/artistIdMap 조회에 실패하는 경우는 경고 로그를 남기고 스킵합니다.
     *
     * @param batch       TrackRaw 배치
     * @param artistIdMap artistName -> artistId 매핑
     * @param albumIdMap  AlbumRow(name, releaseDate) -> albumId 매핑
     * @return album_artist 입력 Row 목록
     */
    public List<AlbumArtistRow> buildAlbumArtistRows(
            List<TrackRaw> batch,
            Map<String, Long> artistIdMap,
            Map<AlbumRow, Long> albumIdMap
    ) {
        List<AlbumArtistRow> aaRows = new ArrayList<>();

        for (TrackRaw r : batch) {
            LocalDate rd = parseDateOrNull(r.releaseDate);
            String albumName = norm(r.album);
            if (albumName == null) continue;

            AlbumRow key = new AlbumRow(albumName, rd);
            Long albumId = albumIdMap.get(key);
            if (albumId == null) {
                System.err.println("[WARN] albumId not found (album_artist): " + key);
                continue;
            }

            for (String an : splitArtists(r.artists)) {
                String artistName = norm(an);
                if (artistName == null) continue;

                Long artistId = artistIdMap.get(artistName);
                if (artistId != null) {
                    aaRows.add(new AlbumArtistRow(albumId, artistId));
                }
            }
        }
        return aaRows;
    }

    /**
     * 배치 데이터를 기반으로 track 테이블 입력 Row와 track_hash 목록을 생성합니다.
     * <p>
     * track_hash는 (title|album|releaseDate|artistsNorm) 기반으로 SHA-256을 생성하여
     * 동일 트랙 식별에 사용합니다.
     *
     * @param batch      TrackRaw 배치
     * @param albumIdMap AlbumRow(name, releaseDate) -> albumId 매핑
     * @return TrackRow 목록 및 해시 목록
     */
    public TrackBuild buildTrackRows(List<TrackRaw> batch, Map<AlbumRow, Long> albumIdMap) {
        List<TrackRow> trackRows = new ArrayList<>(batch.size());
        List<String> hashes = new ArrayList<>(batch.size());

        for (TrackRaw r : batch) {
            LocalDate rd = parseDateOrNull(r.releaseDate);

            String albumName = norm(r.album);
            Long albumId = null;
            if (albumName != null) {
                AlbumRow key = new AlbumRow(albumName, rd);
                albumId = albumIdMap.get(key);

                if (albumId == null) System.err.println("[WARN] albumId not found (track.album_id): " + key);
            }

            String title = norm(r.song);
            if (title == null) title = ""; // title NOT NULL 대비

            String artistsNorm = splitArtists(r.artists).stream()
                    .map(NormalizeUtils::norm)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(","));

            String key = title
                    + "|" + (albumName == null ? "" : albumName)
                    + "|" + (rd == null ? "" : rd)
                    + "|" + artistsNorm;

            String h = sha256Hex(key);
            hashes.add(h);

            Integer durationMs = parseDurationMsOrNull(r.length);

            trackRows.add(new TrackRow(
                    h,
                    title,
                    durationMs,
                    norm(r.length),
                    norm(r.genre),
                    norm(r.emotion),
                    parseExplicit(r.explicit),
                    r.popularity,
                    albumId
            ));
        }

        return new TrackBuild(trackRows, hashes);
    }

    /**
     * track 저장 이후 확보한 track_id를 기반으로, 관계/부가 테이블 입력 Row를 생성합니다.
     * <p>
     * 배치의 i번째 원소는 trackRows의 i번째와 대응된다는 전제 하에 track_hash를 찾아
     * trackIdMap으로 id를 조회합니다.
     *
     * @param batch      TrackRaw 배치
     * @param trackRows  buildTrackRows로 생성된 트랙 Row(입력 순서 대응)
     * @param trackIdMap track_hash -> trackId 매핑
     * @param artistIdMap artistName -> artistId 매핑
     * @return track_artist / track_lyrics / audio_feature 입력 Row 묶음
     */
    public TrackRelations buildTrackRelations(
            List<TrackRaw> batch,
            List<TrackRow> trackRows,
            Map<String, Long> trackIdMap,
            Map<String, Long> artistIdMap
    ) {
        List<TrackArtistRow> taRows = new ArrayList<>();
        List<TrackLyricsRow> lyricRows = new ArrayList<>();
        List<AudioRow> afRows = new ArrayList<>();

        for (int i = 0; i < batch.size(); i++) {
            TrackRaw r = batch.get(i);

            String trackHash = trackRows.get(i).trackHash();
            Long trackId = trackIdMap.get(trackHash);
            if (trackId == null) continue;

            for (String an : splitArtists(r.artists)) {
                String artistName = norm(an);
                if (artistName == null) continue;

                Long aid = artistIdMap.get(artistName);
                if (aid != null) taRows.add(new TrackArtistRow(trackId, aid));
            }

            String lyrics = norm(r.text);
            if (lyrics != null) lyricRows.add(new TrackLyricsRow(trackId, lyrics));

            afRows.add(new AudioRow(
                    trackId,
                    r.tempo,
                    r.loudnessDb,
                    r.energy,
                    r.danceability,
                    r.positiveness,
                    r.speechiness,
                    r.liveness,
                    r.acousticness,
                    r.instrumentalness,
                    norm(r.key),
                    norm(r.timeSignature)
            ));
        }

        return new TrackRelations(taRows, lyricRows, afRows);
    }
}
