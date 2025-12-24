package com.musicinsights.spotifycatalog.infrastructure.mapper;

import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.TrackRaw;
import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TrackRawBatchMapper} 단위 테스트.
 *
 * <p>NDJSON 원본 입력({@link TrackRaw})으로부터
 * 정규화된 아티스트/앨범 추출, 관계 테이블 row 생성, 트랙 row 생성 로직을 검증한다.</p>
 */
@DisplayName("배치 mapper 테스트")
class TrackRawBatchMapperTest {

    /** 테스트 대상 매퍼 */
    private final TrackRawBatchMapper mapper = new TrackRawBatchMapper();

    /**
     * extract가 아티스트/앨범을 정규화(trim 등)하고 distinct 처리하여 수집하는지 검증한다.
     *
     * <p>빈 문자열/공백 앨범은 제외되고, 동일 아티스트/앨범은 중복 제거되어야 한다.</p>
     */
    @DisplayName("extract가 아티스트/앨범을 정규화(trim 등)하고 distinct 처리하여 수집하는지 검증")
    @Test
    void extract_collectsDistinctNormalizedArtists_andAlbums() {
        // given
        TrackRaw r1 = raw()
                .artists(" IU , BTS ")
                .album("  AlbumA ")
                .releaseDate("2020-01-01")
                .build();

        TrackRaw r2 = raw()
                .artists("IU") // duplicate
                .album("AlbumA") // duplicate
                .releaseDate("2020-01-01")
                .build();

        TrackRaw r3 = raw()
                .artists("NewJeans")
                .album("  ") // blank -> filtered out
                .releaseDate("")
                .build();

        var out = mapper.extract(List.of(r1, r2, r3));

        // artistNames: IU, BTS, NewJeans (정규화+distinct)
        assertTrue(out.artistNames().contains("IU"));
        assertTrue(out.artistNames().contains("BTS"));
        assertTrue(out.artistNames().contains("NewJeans"));
        assertEquals(3, out.artistNames().size());

        // albums: AlbumA(2020-01-01)만 남아야 함
        assertEquals(1, out.albums().size());
        assertEquals(new AlbumRow("AlbumA", LocalDate.of(2020, 1, 1)), out.albums().get(0));
    }

    /**
     * buildAlbumArtistRows가 앨범ID/아티스트ID가 존재하는 경우에만 매핑 row를 생성하는지 검증한다.
     *
     * <p>artistIdMap 또는 albumIdMap에 없는 값은 결과에서 스킵되어야 한다.</p>
     */
    @DisplayName("buildAlbumArtistRows가 앨범ID/아티스트ID가 존재하는 경우에만 매핑 row를 생성하는지 검증")
    @Test
    void buildAlbumArtistRows_buildsPairs_whenIdsExist_andSkipsMissing() {
        // given batch
        TrackRaw r1 = raw()
                .artists("IU, BTS, Unknown")
                .album("AlbumA")
                .releaseDate("2020-01-01")
                .build();

        List<TrackRaw> batch = List.of(r1);

        Map<String, Long> artistIdMap = Map.of(
                "IU", 10L,
                "BTS", 11L
                // Unknown intentionally missing
        );

        AlbumRow albumKey = new AlbumRow("AlbumA", LocalDate.of(2020, 1, 1));
        Map<AlbumRow, Long> albumIdMap = Map.of(albumKey, 100L);

        // when
        List<AlbumArtistRow> rows = mapper.buildAlbumArtistRows(batch, artistIdMap, albumIdMap);

        // then: IU, BTS만 들어가야 함
        assertEquals(2, rows.size());
        assertTrue(rows.contains(new AlbumArtistRow(100L, 10L)));
        assertTrue(rows.contains(new AlbumArtistRow(100L, 11L)));
    }

    /**
     * buildTrackRows가 트랙 타이틀 null을 빈 문자열로 처리하고,
     * 앨범ID 매핑 및 해시 생성이 수행되는지 검증한다.
     */
    @DisplayName("buildTrackRows가 트랙 타이틀 null을 빈 문자열로 처리, 앨범ID 매핑 및 해시 생성이 수행되는지 검증")
    @Test
    void buildTrackRows_setsTitleEmptyIfNull_andMapsAlbumId_andProducesHashes() {
        // given
        TrackRaw r1 = raw()
                .song(null) // title null
                .artists("IU")
                .album("AlbumA")
                .releaseDate("2020-01-01")
                .length("03:47")
                .genre("pop")
                .emotion("happy")
                .explicit("FALSE")
                .popularity(55)
                .build();

        AlbumRow albumKey = new AlbumRow("AlbumA", LocalDate.of(2020, 1, 1));
        Map<AlbumRow, Long> albumIdMap = Map.of(albumKey, 100L);

        // when
        TrackRawBatchMapper.TrackBuild out = mapper.buildTrackRows(List.of(r1), albumIdMap);

        // then
        assertEquals(1, out.trackRows().size());
        assertEquals(1, out.hashes().size());

        TrackRow tr = out.trackRows().get(0);
        assertNotNull(tr.trackHash());
        assertFalse(tr.trackHash().isBlank());
        assertEquals("", tr.title());              // null -> ""
        assertEquals(100L, tr.albumId());          // mapped
        assertEquals("03:47", tr.durationStr());   // norm된 원본 보존
    }

    /**
     * buildTrackRelations가 trackIdMap/artistIdMap을 기반으로
     * track_artist, track_lyrics, audio_feature row를 생성하는지 검증한다.
     *
     * <p>아티스트 매핑이 없는 경우(track_artist)는 스킵되고,
     * 가사는 null이면 생성되지 않으며,
     * 오디오 row는 입력 트랙 수만큼 생성되는(코드 정책) 시나리오를 검증한다.</p>
     */
    @DisplayName("buildTrackRelations가 trackIdMap/artistIdMap을 기반으로 row 생성 검증")
    @Test
    void buildTrackRelations_buildsArtistsLyricsAudio_usingTrackIdAndArtistIdMaps() {
        // given: batch 2개
        TrackRaw r1 = raw()
                .artists("IU, BTS")
                .song("Song1")
                .album("AlbumA")
                .releaseDate("2020-01-01")
                .text(" hello lyrics ")
                .tempo(120.0)
                .loudnessDb(-5.0)
                .energy(80)
                .danceability(70)
                .positiveness(60)
                .speechiness(10)
                .liveness(15)
                .acousticness(20)
                .instrumentalness(0)
                .key(" C ")
                .timeSignature(" 4/4 ")
                .build();

        TrackRaw r2 = raw()
                .artists("Unknown") // artistIdMap에 없음
                .song("Song2")
                .album("AlbumA")
                .releaseDate("2020-01-01")
                .text(null) // lyrics 없음
                .tempo(null)
                .loudnessDb(null)
                .build();

        List<TrackRaw> batch = List.of(r1, r2);

        // trackRows는 buildTrackRows 결과와 "인덱스 정렬이 같다"는 전제라,
        // 테스트에서는 최소 필드만 맞춰서 직접 생성
        TrackRow t1 = new TrackRow("h1", "Song1", null, null, null, null, false, null, null);
        TrackRow t2 = new TrackRow("h2", "Song2", null, null, null, null, false, null, null);
        List<TrackRow> trackRows = List.of(t1, t2);

        Map<String, Long> trackIdMap = Map.of(
                "h1", 1000L,
                "h2", 2000L
        );

        Map<String, Long> artistIdMap = Map.of(
                "IU", 10L,
                "BTS", 11L
        );

        // when
        TrackRawBatchMapper.TrackRelations rel =
                mapper.buildTrackRelations(batch, trackRows, trackIdMap, artistIdMap);

        // then: track_artist는 r1의 IU,BTS만 2개
        assertEquals(2, rel.trackArtistRows().size());
        assertTrue(rel.trackArtistRows().contains(new TrackArtistRow(1000L, 10L)));
        assertTrue(rel.trackArtistRows().contains(new TrackArtistRow(1000L, 11L)));

        // lyrics는 r1만 (norm으로 공백 제거되어 저장)
        assertEquals(1, rel.lyricsRows().size());
        assertEquals(new TrackLyricsRow(1000L, "hello lyrics"), rel.lyricsRows().get(0));

        // audio는 r1,r2 모두 add (코드에서 무조건 add)
        assertEquals(2, rel.audioRows().size());

        AudioRow a1 = rel.audioRows().get(0);
        assertEquals(1000L, a1.trackId());
        assertEquals(120.0, a1.tempo());
        assertEquals(-5.0, a1.loudness());
        assertEquals("C", a1.musicalKey());
        assertEquals("4/4", a1.timeSignature());

        AudioRow a2 = rel.audioRows().get(1);
        assertEquals(2000L, a2.trackId());
        assertNull(a2.tempo());
        assertNull(a2.loudness());
    }

    /**
     * TrackRaw 테스트 데이터 생성을 위한 빌더 엔트리.
    *
     * @return 테스트용 TrackRawBuilder
     */
    private static TrackRawBuilder raw() {
        return new TrackRawBuilder();
    }

    /**
     * 테스트 편의용 {@link TrackRaw} 빌더.
     *
     */
    private static class TrackRawBuilder {
        String artists;
        String album;
        String releaseDate;
        String song;
        String length;
        String genre;
        String emotion;
        String explicit;
        Integer popularity;
        String text;

        Double tempo;
        Double loudnessDb;
        Integer energy;
        Integer danceability;
        Integer positiveness;
        Integer speechiness;
        Integer liveness;
        Integer acousticness;
        Integer instrumentalness;
        String key;
        String timeSignature;

        TrackRawBuilder artists(String v) { this.artists = v; return this; }
        TrackRawBuilder album(String v) { this.album = v; return this; }
        TrackRawBuilder releaseDate(String v) { this.releaseDate = v; return this; }
        TrackRawBuilder song(String v) { this.song = v; return this; }
        TrackRawBuilder length(String v) { this.length = v; return this; }
        TrackRawBuilder genre(String v) { this.genre = v; return this; }
        TrackRawBuilder emotion(String v) { this.emotion = v; return this; }
        TrackRawBuilder explicit(String v) { this.explicit = v; return this; }
        TrackRawBuilder popularity(Integer v) { this.popularity = v; return this; }
        TrackRawBuilder text(String v) { this.text = v; return this; }

        TrackRawBuilder tempo(Double v) { this.tempo = v; return this; }
        TrackRawBuilder loudnessDb(Double v) { this.loudnessDb = v; return this; }
        TrackRawBuilder energy(Integer v) { this.energy = v; return this; }
        TrackRawBuilder danceability(Integer v) { this.danceability = v; return this; }
        TrackRawBuilder positiveness(Integer v) { this.positiveness = v; return this; }
        TrackRawBuilder speechiness(Integer v) { this.speechiness = v; return this; }
        TrackRawBuilder liveness(Integer v) { this.liveness = v; return this; }
        TrackRawBuilder acousticness(Integer v) { this.acousticness = v; return this; }
        TrackRawBuilder instrumentalness(Integer v) { this.instrumentalness = v; return this; }
        TrackRawBuilder key(String v) { this.key = v; return this; }
        TrackRawBuilder timeSignature(String v) { this.timeSignature = v; return this; }

        TrackRaw build() {
            TrackRaw r = new TrackRaw();
            r.artists = this.artists;
            r.album = this.album;
            r.releaseDate = this.releaseDate;
            r.song = this.song;
            r.length = this.length;
            r.genre = this.genre;
            r.emotion = this.emotion;
            r.explicit = this.explicit;
            r.popularity = this.popularity;
            r.text = this.text;

            r.tempo = this.tempo;
            r.loudnessDb = this.loudnessDb;
            r.energy = this.energy;
            r.danceability = this.danceability;
            r.positiveness = this.positiveness;
            r.speechiness = this.speechiness;
            r.liveness = this.liveness;
            r.acousticness = this.acousticness;
            r.instrumentalness = this.instrumentalness;
            r.key = this.key;
            r.timeSignature = this.timeSignature;

            return r;
        }
    }
}