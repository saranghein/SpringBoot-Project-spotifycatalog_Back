package com.musicinsights.spotifycatalog.infrastructure.input.ndjson;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.AlbumRow;

/**
 * Ingest 과정에서 사용하는 seed 모델 모음.
 *
 * <p>key 기준으로 중복 제거된 최소 단위 데이터를 표현한다.</p>
 */
public final class IngestSeeds {
    private IngestSeeds() {}


    /**
     * 아티스트 seed.
     *
     * @param key  정규화된 아티스트 키
     * @param name 표시용 아티스트 이름
     */
    public record ArtistSeed(String key, String name) {}

    /**
     * 앨범 seed.
     *
     * @param key   정규화된 앨범 키
     * @param album 앨범 row 데이터
     */
    public record AlbumSeed(String key, AlbumRow album) {}
}
