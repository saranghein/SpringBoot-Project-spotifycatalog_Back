package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row;

/**
 * 트랙의 오디오 특성(Audio Features)을 저장하기 위한 Row 객체입니다.
 *
 * @param trackId             트랙의 고유 식별자
 * @param tempo               곡의 템포(BPM)
 * @param loudness            곡의 평균 음량(dB)
 * @param energy              에너지 레벨
 * @param danceability        춤추기 적합한 정도
 * @param positiveness        곡의 긍정적 분위기 정도
 * @param speechiness         음성 비중
 * @param liveness            라이브 공연 여부 지표
 * @param acousticness        어쿠스틱 성향 지표
 * @param instrumentalness    보컬이 없는 정도
 * @param musicalKey          곡의 조성(Key)
 * @param timeSignature       박자(Time Signature)
 */
public record AudioRow(
        Long trackId,
        Double tempo,
        Double loudness,
        Integer energy,
        Integer danceability,
        Integer positiveness,
        Integer speechiness,
        Integer liveness,
        Integer acousticness,
        Integer instrumentalness,
        String musicalKey,
        String timeSignature
) {}
