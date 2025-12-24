package com.musicinsights.spotifycatalog.application.ingest;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo.*;
import org.springframework.stereotype.Component;

/**
 * ingest 과정에서 사용하는 Repository들을 한 곳에 모아 제공하는 파사드(Facade) 컴포넌트입니다.
 * <p>
 * 서비스 레이어에서 다수의 Repo 의존성을 줄이고, 배치 적재 흐름을 읽기 쉽게 구성하기 위한 용도입니다.
 */
@Component
public class IngestFacade {

    /** artist 테이블 관련 작업 */
    public final ArtistRepo artist;

    /** album 테이블 관련 작업 */
    public final AlbumRepo album;

    /** album_artist 조인 테이블 관련 작업 */
    public final AlbumArtistRepo albumArtist;

    /** track 테이블 관련 작업 */
    public final TrackRepo track;

    /** track_artist 조인 테이블 관련 작업 */
    public final TrackArtistRepo trackArtist;

    /** track_lyrics 테이블 관련 작업 */
    public final TrackLyricsRepo trackLyrics;

    /** audio_feature 테이블 관련 작업 */
    public final AudioRepo audioFeature;

    /**
     * ingest에 필요한 모든 Repository를 주입받아 초기화합니다.
     *
     * @param artist artist repo
     * @param album album repo
     * @param albumArtist album-artist repo
     * @param track track repo
     * @param trackArtist track-artist repo
     * @param trackLyrics track-lyrics repo
     * @param audioFeature audio-feature repo
     */
    public IngestFacade(
            ArtistRepo artist,
            AlbumRepo album,
            AlbumArtistRepo albumArtist,
            TrackRepo track,
            TrackArtistRepo trackArtist,
            TrackLyricsRepo trackLyrics,
            AudioRepo audioFeature
    ) {
        this.artist = artist;
        this.album = album;
        this.albumArtist = albumArtist;

        this.track = track;
        this.trackArtist = trackArtist;

        this.trackLyrics = trackLyrics;
        this.audioFeature = audioFeature;
    }
}
