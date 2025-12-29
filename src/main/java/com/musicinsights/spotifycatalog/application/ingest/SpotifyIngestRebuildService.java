package com.musicinsights.spotifycatalog.application.ingest;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo.ArtistAlbumCountYearRepo;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * ingest 이후 집계(통계) 테이블을 재생성하는 서비스.
 */
@Service
public class SpotifyIngestRebuildService {
    public final ArtistAlbumCountYearRepo artistAlbumCountYearRepo;

    public SpotifyIngestRebuildService(ArtistAlbumCountYearRepo artistAlbumCountYearRepo) {
        this.artistAlbumCountYearRepo = artistAlbumCountYearRepo;
    }

    /**
     * 집계 테이블을 rebuild 한다.
     *
     * @return 처리된(갱신/삽입된) 행 수
     */
    public Mono<Long> rebuild() {
        return artistAlbumCountYearRepo.rebuild();
    }
}
