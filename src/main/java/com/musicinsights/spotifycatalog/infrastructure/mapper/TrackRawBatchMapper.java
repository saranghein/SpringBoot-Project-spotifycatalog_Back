package com.musicinsights.spotifycatalog.infrastructure.mapper;

import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.IngestSeeds;
import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.TrackRaw;
import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

import static com.musicinsights.spotifycatalog.infrastructure.input.ndjson.NormalizeUtils.*;

/**
 * {@link TrackRaw} 배치를 DB 입력용 row/seed로 변환한다.
 */
@Component
public class TrackRawBatchMapper {

    /** extract 결과(artist/album seed). */
    public record BatchExtract(
            List<IngestSeeds.ArtistSeed> artists,
            List<IngestSeeds.AlbumSeed> albums
    ) {
        public List<String> artistKeys() { return artists.stream().map(IngestSeeds.ArtistSeed::key).toList(); }
        public List<String> albumKeys()  { return albums.stream().map(IngestSeeds.AlbumSeed::key).toList(); }
        public List<AlbumRow> albumRows(){ return albums.stream().map(a -> a.album()).toList(); }
    }

    /** track 생성 결과(track row + hash). */
    public record TrackBuild(
            List<TrackRow> trackRows,
            List<String> hashes
    ) {}

    /** 관계 row 생성 결과(track_artist/lyrics/audio). */
    public record TrackRelations(
            List<TrackArtistRow> trackArtistRows,
            List<TrackLyricsRow> lyricsRows,
            List<AudioRow> audioRows
    ) {}

    /**
     * 배치에서 artist/album seed를 key 기준으로 중복 제거하여 추출한다.
     *
     * @param batch 입력 배치
     * @return seed 목록
     */
    public BatchExtract extract(List<TrackRaw> batch) {
        Map<String, IngestSeeds.ArtistSeed> artistByKey = new LinkedHashMap<>();
        Map<String, IngestSeeds.AlbumSeed>  albumByKey  = new LinkedHashMap<>();

        for (TrackRaw r : batch) {
            // artists
            for (String raw : splitArtists(r.artists)) {
                String display = norm(raw);
                String key = artistKey(display);
                if (key != null && display != null) {
                    artistByKey.putIfAbsent(key, new IngestSeeds.ArtistSeed(key, display));
                }
            }

            // albums
            LocalDate rd = parseDateOrNull(r.releaseDate);
            String albumName = norm(r.album);
            String ak = albumKey(albumName, rd);
            if (ak != null) {
                albumByKey.putIfAbsent(ak, new IngestSeeds.AlbumSeed(ak, new AlbumRow(albumName, rd)));
            }
        }

        return new BatchExtract(
                new ArrayList<>(artistByKey.values()),
                new ArrayList<>(albumByKey.values())
        );
    }

    /**
     * albumId/artistId 맵을 사용해 album_artist row를 생성한다.
     *
     * @param batch 입력 배치
     * @param artistIdByKey artistKey -> id
     * @param albumIdByKey albumKey -> id
     * @return album_artist row 목록
     */
    public List<AlbumArtistRow> buildAlbumArtistRows(
            List<TrackRaw> batch,
            Map<String, Long> artistIdByKey,
            Map<String, Long> albumIdByKey
    ) {
        List<AlbumArtistRow> rows = new ArrayList<>();

        for (TrackRaw r : batch) {
            String albumName = norm(r.album);
            LocalDate rd = parseDateOrNull(r.releaseDate);

            // Album="" or blank면 album_artist를 만들 수 없어 스킵
            if (albumName == null) continue;

            String ak = albumKey(albumName, rd);
            Long albumId = albumIdByKey.get(ak);

            if (albumId == null) {
                System.err.println("[WARN] albumId not found (album_artist): " + albumName + " / " + r.releaseDate);
                continue;
            }

            for (String rawArtist : splitArtists(r.artists)) {
                String artistK = artistKey(rawArtist);
                Long artistId = (artistK == null) ? null : artistIdByKey.get(artistK);
                if (artistId != null) {
                    rows.add(new AlbumArtistRow(albumId, artistId));
                }
            }
        }
        return rows;
    }

    /**
     * track row와 track_hash 목록을 생성한다.
     *
     * @param batch 입력 배치
     * @param albumIdByKey albumKey -> id
     * @return track row + hash
     */
    public TrackBuild buildTrackRows(
            List<TrackRaw> batch,
            Map<String, Long> albumIdByKey
    ) {
        List<TrackRow> trackRows = new ArrayList<>(batch.size());
        List<String> hashes = new ArrayList<>(batch.size());

        for (TrackRaw r : batch) {
            LocalDate rd = parseDateOrNull(r.releaseDate);

            String albumName = norm(r.album);
            String ak = (albumName == null) ? null : albumKey(albumName, rd);
            Long albumId = (ak == null) ? null : albumIdByKey.get(ak);

            // albumName이 실제로 있는데도 못 찾는 경우만 WARN
            if (albumName != null && albumId == null) {
                System.err.println("[WARN] albumId not found (track.album_id): " + albumName + " / " + r.releaseDate);
            }

            String keyStr = trackKey(r.song, r.album, rd, splitArtists(r.artists));
            String h = sha256Hex(keyStr);
            hashes.add(h);

            String title = norm(r.song);
            if (title == null) title = "";

            trackRows.add(new TrackRow(
                    h,
                    title,
                    parseDurationMsOrNull(r.length),
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
     * trackId/artistId 맵을 사용해 track_artist/lyrics/audio row를 생성한다.
     *
     * @param batch 입력 배치
     * @param trackRows track row(동일 순서)
     * @param trackIdMap track_hash -> track_id
     * @param artistIdByKey artistKey -> id
     * @return 관계 row 묶음
     */
    public TrackRelations buildTrackRelations(
            List<TrackRaw> batch,
            List<TrackRow> trackRows,
            Map<String, Long> trackIdMap,
            Map<String, Long> artistIdByKey
    ) {
        List<TrackArtistRow> taRows = new ArrayList<>();
        List<TrackLyricsRow> lyricRows = new ArrayList<>();
        List<AudioRow> afRows = new ArrayList<>();

        for (int i = 0; i < batch.size(); i++) {
            TrackRaw r = batch.get(i);

            String trackHash = trackRows.get(i).trackHash();
            Long trackId = trackIdMap.get(trackHash);
            if (trackId == null) continue;

            for (String rawArtist : splitArtists(r.artists)) {
                String artistK = artistKey(rawArtist);
                Long aid = (artistK == null) ? null : artistIdByKey.get(artistK);
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
