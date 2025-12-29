package com.musicinsights.spotifycatalog.infrastructure.mapper;

import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.IngestSeeds;
import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.NormalizeUtils;
import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.TrackRaw;
import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TrackRawBatchMapper} 단위 테스트 (key/seed 기반).
 *
 * <p>{@link TrackRaw} 입력으로부터
 * seed(artist/album) 추출, 관계 row 생성(album_artist/track_artist),
 * 트랙 row 생성(track), 부가 row 생성(lyrics/audio) 로직을 검증한다.</p>
 */
@DisplayName("배치 mapper 테스트 (key/seed 기반)")
class TrackRawBatchMapperTest {

    private final TrackRawBatchMapper mapper = new TrackRawBatchMapper();

    /**
     * extract가 artist/album 값을 정규화하고,
     * key 기준으로 중복 제거된 seed 목록을 수집하는지 검증한다.
     * <p>
     * album이 blank인 경우 album seed는 생성되지 않는다.
     */
    @DisplayName("extract가 아티스트/앨범을 정규화하고 key 기준 distinct seed로 수집하는지 검증")
    @Test
    void extract_collectsDistinctSeeds_byKey_andSkipsBlankAlbum() {
        // given
        TrackRaw r1 = raw()
                .artists(" IU , BTS ")
                .album("  AlbumA ")
                .releaseDate("2020-01-01")
                .build();

        TrackRaw r2 = raw()
                .artists("IU")     // duplicate
                .album("AlbumA")   // duplicate
                .releaseDate("2020-01-01")
                .build();

        TrackRaw r3 = raw()
                .artists("NewJeans")
                .album("  ")       // blank -> filtered out
                .releaseDate("")   // invalid -> null
                .build();

        var out = mapper.extract(List.of(r1, r2, r3));

        // artists: IU, BTS, NewJeans
        assertEquals(3, out.artists().size());
        assertTrue(out.artists().stream().anyMatch(s -> s.name().equals("IU")));
        assertTrue(out.artists().stream().anyMatch(s -> s.name().equals("BTS")));
        assertTrue(out.artists().stream().anyMatch(s -> s.name().equals("NewJeans")));

        // artist key도 채워져 있어야 함
        out.artists().forEach(s -> assertNotNull(s.key()));

        // albums: AlbumA(2020-01-01)만 남아야 함
        assertEquals(1, out.albums().size());
        IngestSeeds.AlbumSeed a = out.albums().get(0);
        assertEquals("AlbumA", a.album().name());
        assertEquals(LocalDate.of(2020, 1, 1), a.album().releaseDate());
        assertNotNull(a.key());
    }

    /**
     * buildAlbumArtistRows가 albumKey/artistKey로 ID 맵을 조회하여
     * album_artist 매핑 row를 생성하는지 검증한다.
     * <p>
     * artistIdByKey 또는 albumIdByKey에 존재하지 않는 key는 스킵된다.
     */
    @DisplayName("buildAlbumArtistRows가 albumKey/artistKey로 ID 조회해서 매핑 row를 생성(없는 것은 스킵)")
    @Test
    void buildAlbumArtistRows_buildsPairs_whenIdsExist_andSkipsMissing() {
        // given batch
        TrackRaw r1 = raw()
                .artists("IU, BTS, Unknown")
                .album("AlbumA")
                .releaseDate("2020-01-01")
                .build();

        List<TrackRaw> batch = List.of(r1);

        String kIU = NormalizeUtils.artistKey("IU");
        String kBTS = NormalizeUtils.artistKey("BTS");

        Map<String, Long> artistIdByKey = Map.of(
                kIU, 10L,
                kBTS, 11L
        );

        String ak = NormalizeUtils.albumKey("AlbumA", LocalDate.of(2020, 1, 1));
        Map<String, Long> albumIdByKey = Map.of(
                ak, 100L
        );

        // when
        List<AlbumArtistRow> rows = mapper.buildAlbumArtistRows(batch, artistIdByKey, albumIdByKey);

        // then: IU, BTS만 들어가야 함
        assertEquals(2, rows.size());
        assertTrue(rows.contains(new AlbumArtistRow(100L, 10L)));
        assertTrue(rows.contains(new AlbumArtistRow(100L, 11L)));
    }

    /**
     * buildAlbumArtistRows는 album 값이 blank인 경우
     * 조인 row 생성을 수행하지 않고 빈 결과를 반환하는지 검증한다.
     */
    @DisplayName("buildAlbumArtistRows는 Album이 빈 값이면 조용히 스킵한다")
    @Test
    void buildAlbumArtistRows_skips_whenAlbumBlank() {
        TrackRaw r = raw()
                .artists("IU, BTS")
                .album("")
                .releaseDate(null)
                .build();

        String kIU = NormalizeUtils.artistKey("IU");
        String kBTS = NormalizeUtils.artistKey("BTS");

        Map<String, Long> artistIdByKey = Map.of(kIU, 10L, kBTS, 11L);
        Map<String, Long> albumIdByKey = Map.of();

        List<AlbumArtistRow> rows = mapper.buildAlbumArtistRows(List.of(r), artistIdByKey, albumIdByKey);
        assertTrue(rows.isEmpty());
    }

    /**
     * buildTrackRows가 다음을 수행하는지 검증한다.
     * <ul>
     *   <li>title(song)이 null이면 빈 문자열로 치환한다.</li>
     *   <li>albumKey를 통해 albumId를 매핑한다.</li>
     *   <li>track_hash를 생성하고 hashes 목록을 반환한다.</li>
     * </ul>
     */
    @DisplayName("buildTrackRows가 title null을 \"\"로 처리, albumKey로 albumId 매핑 및 해시 생성 수행")
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
                .explicit("Yes")  // parseExplicit는 Yes만 true
                .popularity(55)
                .build();

        String ak = NormalizeUtils.albumKey("AlbumA", LocalDate.of(2020, 1, 1));
        Map<String, Long> albumIdByKey = Map.of(ak, 100L);

        // when
        TrackRawBatchMapper.TrackBuild out = mapper.buildTrackRows(List.of(r1), albumIdByKey);

        // then
        assertEquals(1, out.trackRows().size());
        assertEquals(1, out.hashes().size());

        TrackRow tr = out.trackRows().get(0);
        assertNotNull(tr.trackHash());
        assertFalse(tr.trackHash().isBlank());
        assertEquals("", tr.title());              // null -> ""
        assertEquals(100L, tr.albumId());          // mapped
        assertEquals("03:47", tr.durationStr());   // 원본 보존
        assertTrue(tr.explicit());                 // Yes -> true
    }

    /**
     * buildTrackRelations가 trackIdMap/artistIdByKey를 기반으로
     * 관계 테이블 및 부가 테이블 row를 생성하는지 검증한다.
     * <ul>
     *   <li>track_artist: 존재하는 artistId만 생성</li>
     *   <li>track_lyrics: text가 유효할 때만 생성(정규화 포함)</li>
     *   <li>audio_feature: 각 track에 대해 row 생성(필드 null 허용)</li>
     * </ul>
     */
    @DisplayName("buildTrackRelations가 trackIdMap/artistIdByKey 기반으로 row 생성 검증")
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
                .artists("Unknown") // artistIdByKey에 없음
                .song("Song2")
                .album("AlbumA")
                .releaseDate("2020-01-01")
                .text(null) // lyrics 없음
                .tempo(null)
                .loudnessDb(null)
                .build();

        List<TrackRaw> batch = List.of(r1, r2);

        // trackRows는 인덱스 정렬이 같다는 전제, 최소만 맞춰서 직접 생성
        TrackRow t1 = new TrackRow("h1", "Song1", null, null, null, null, false, null, null);
        TrackRow t2 = new TrackRow("h2", "Song2", null, null, null, null, false, null, null);
        List<TrackRow> trackRows = List.of(t1, t2);

        Map<String, Long> trackIdMap = Map.of(
                "h1", 1000L,
                "h2", 2000L
        );

        String kIU = NormalizeUtils.artistKey("IU");
        String kBTS = NormalizeUtils.artistKey("BTS");
        Map<String, Long> artistIdByKey = Map.of(
                kIU, 10L,
                kBTS, 11L
        );

        // when
        TrackRawBatchMapper.TrackRelations rel =
                mapper.buildTrackRelations(batch, trackRows, trackIdMap, artistIdByKey);

        // then: track_artist는 r1의 IU,BTS만 2개
        assertEquals(2, rel.trackArtistRows().size());
        assertTrue(rel.trackArtistRows().contains(new TrackArtistRow(1000L, 10L)));
        assertTrue(rel.trackArtistRows().contains(new TrackArtistRow(1000L, 11L)));

        // lyrics는 r1만 (norm으로 trim)
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
     * {@link TrackRaw} 빌더를 생성한다.
     *
     * @return 테스트용 {@link TrackRawBuilder}
     */
    private static TrackRawBuilder raw() {
        return new TrackRawBuilder();
    }

    /**
     * 테스트 코드에서 {@link TrackRaw}를 간단히 생성하기 위한 빌더.
     * <p>
     * NDJSON 역직렬화 형태(필드 직접 할당)를 가정한다.
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