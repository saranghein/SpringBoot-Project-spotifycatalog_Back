package com.musicinsights.spotifycatalog.infrastructure.input.ndjson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link TrackRaw} JSON 매핑 단위 테스트.
 *
 * <p>NDJSON 한 줄(JSON 객체)이 Jackson {@link ObjectMapper}를 통해
 * {@link TrackRaw} 필드로 올바르게 역직렬화되는지 검증한다.</p>
 */
@DisplayName("track raw json 매핑 테스트")
public class TrackRawJsonMappingTest {

    /**
     * 테스트용 ObjectMapper.
     *
     * <p>데이터셋에 불필요/추가 필드가 있을 수 있으므로 unknown properties는 무시한다.</p>
     */
    private final ObjectMapper om = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


    /**
     * 데이터셋 형식의 JSON 한 줄을 {@link TrackRaw}로 매핑했을 때
     * 주요 필드 값이 기대와 일치하는지 검증한다.
     *
     * @throws Exception JSON 파싱/매핑 실패 시
     */
    @DisplayName("데이터셋 형식의 JSON 한 줄 매핑이 기대와 일치하는지 검증")
    @Test
    void jsonLine_mapsTo_TrackRaw_correctly() throws Exception {
        String json = """
        {
          "Artist(s)":"!!!",
          "song":"Even When the Waters Cold",
          "text":"Friends told her",
          "Length":"03:47",
          "emotion":"sadness",
          "Genre":"hip hop",
          "Album":"Thr!!!er",
          "Release Date":"2013-04-29",
          "Key":"D min",
          "Tempo":0.4378698225,
          "Loudness (db)":0.785065407,
          "Time signature":"4/4",
          "Explicit":"No",
          "Popularity":"40",
          "Energy":"83",
          "Danceability":"71",
          "Positiveness":"87",
          "Speechiness":"4",
          "Liveness":"16",
          "Acousticness":"11",
          "Instrumentalness":"0"
        }
        """;

        TrackRaw r = om.readValue(json, TrackRaw.class);

        assertEquals("!!!", r.artists);
        assertEquals("Even When the Waters Cold", r.song);
        assertEquals("Friends told her", r.text);
        assertEquals("03:47", r.length);
        assertEquals("sadness", r.emotion);
        assertEquals("hip hop", r.genre);
        assertEquals("Thr!!!er", r.album);
        assertEquals("2013-04-29", r.releaseDate);
        assertEquals("D min", r.key);
        assertEquals(0.4378698225, r.tempo);
        assertEquals(0.785065407, r.loudnessDb);
        assertEquals("4/4", r.timeSignature);
        assertEquals("No", r.explicit);
        assertEquals(40, r.popularity);
        assertEquals(83, r.energy);
        assertEquals(71, r.danceability);
        assertEquals(87, r.positiveness);
        assertEquals(4, r.speechiness);
        assertEquals(16, r.liveness);
        assertEquals(11, r.acousticness);
        assertEquals(0, r.instrumentalness);
    }
}
