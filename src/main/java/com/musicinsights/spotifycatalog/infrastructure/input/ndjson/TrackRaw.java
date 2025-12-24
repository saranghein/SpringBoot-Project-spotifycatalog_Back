package com.musicinsights.spotifycatalog.infrastructure.input.ndjson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Kaggle NDJSON 데이터의 "한 줄(= 한 트랙 레코드)"을 매핑하기 위한 원본 DTO입니다.
 * <p>
 * 컬럼명이 제각각이므로 {@link JsonProperty}로 원본 필드명을 그대로 매핑하며,
 * 스키마 변경/추가 컬럼에 대비해 {@link JsonIgnoreProperties#ignoreUnknown()}를 사용합니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrackRaw { // 원본 JSON 한 줄”을 담는 DTO

    /**
     * 아티스트 문자열(쉼표로 구분될 수 있음).
     * <p>예: "A, B, C"</p>
     */
    @JsonProperty("Artist(s)") // Kaggle 데이터의 컬럼명 그대로 받기
    public String artists; // "A, B, C" 가능

    /** 곡 제목 */
    @JsonProperty("song")
    public String song;

    /** 가사 텍스트 */
    @JsonProperty("text")
    public String text;

    /** 곡 길이 문자열(mm:ss) */
    @JsonProperty("Length")
    public String length; // "03:47"

    /** 감정 태그 */
    @JsonProperty("emotion")
    public String emotion;

    /** 장르 */
    @JsonProperty("Genre")
    public String genre;

    /** 앨범명 */
    @JsonProperty("Album")
    public String album;

    /** 발매일(문자열, 예: "2013-04-29") */
    @JsonProperty("Release Date")
    public String releaseDate; // "2013-04-29"

    /** 곡의 조성(Key) */
    @JsonProperty("Key")
    public String key;

    /** 템포(BPM) */
    @JsonProperty("Tempo")
    public Double tempo;

    /** 평균 음량(dB) */
    @JsonProperty("Loudness (db)")
    public Double loudnessDb;

    /** 박자(Time Signature) */
    @JsonProperty("Time signature")
    public String timeSignature;

    /** Explicit 여부("Yes"/"No") */
    @JsonProperty("Explicit")
    public String explicit; // "Yes"/"No"

    /** 인기도 지표 */
    @JsonProperty("Popularity")
    public Integer popularity;

    /** 에너지 레벨 */
    @JsonProperty("Energy")
    public Integer energy;

    /** Danceability 지표 */
    @JsonProperty("Danceability")
    public Integer danceability;

    /** Positiveness 지표 */
    @JsonProperty("Positiveness")
    public Integer positiveness;

    /** Speechiness 지표 */
    @JsonProperty("Speechiness")
    public Integer speechiness;

    /** Liveness 지표 */
    @JsonProperty("Liveness")
    public Integer liveness;

    /** Acousticness 지표 */
    @JsonProperty("Acousticness")
    public Integer acousticness;

    /** Instrumentalness 지표 */
    @JsonProperty("Instrumentalness")
    public Integer instrumentalness;

}
