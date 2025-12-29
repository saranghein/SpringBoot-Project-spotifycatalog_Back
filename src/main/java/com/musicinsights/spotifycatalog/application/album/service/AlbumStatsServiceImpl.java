package com.musicinsights.spotifycatalog.application.album.service;

import com.musicinsights.spotifycatalog.application.album.dto.response.*;
import com.musicinsights.spotifycatalog.application.common.pagination.KeysetPageAssembler;
import com.musicinsights.spotifycatalog.application.common.pagination.PageResult;
import com.musicinsights.spotifycatalog.application.album.repository.AlbumStatsRepository;
import com.musicinsights.spotifycatalog.application.common.error.NotFoundException;
import com.musicinsights.spotifycatalog.application.common.pagination.CursorCodec;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 앨범 통계/아티스트 앨범 목록 조회 서비스 구현체.
 *
 * <p>keyset pagination(커서 기반)으로 조회하며, 커서는 {@link CursorCodec}으로 인코딩/디코딩한다.</p>
 */
@Service
public class AlbumStatsServiceImpl implements AlbumStatsService {

    private final AlbumStatsRepository albumStatsRepository;

    public AlbumStatsServiceImpl(AlbumStatsRepository albumStatsRepository) {
        this.albumStatsRepository = albumStatsRepository;
    }

    /**
     * 아티스트별 발매 앨범 수 통계를 조회한다.
     *
     * @param year   연도(없으면 전체)
     * @param cursor 다음 페이지 커서
     * @param size   페이지 크기
     * @return 통계 결과(총 앨범 수 + 페이지 정보)
     */
    public Mono<ArtistAlbumStatsResponse.ArtistAlbumStatsResultResponse> getArtistAlbumStats(
            Integer year,
            String cursor,
            int size
    ){
        ArtistAlbumStatsResponse.ArtistAlbumStatsCursor decoded =
                CursorCodec.decodeOrNull(cursor, ArtistAlbumStatsResponse.ArtistAlbumStatsCursor.class);

        long cursorAlbumCount = (decoded == null) ? Long.MAX_VALUE : decoded.albumCount();
        long cursorArtistId   = (decoded == null) ? 0L : decoded.artistId();

        int fetchSize = KeysetPageAssembler.fetchSize(size);

        Mono<Long> totalAlbumsMono = albumStatsRepository.countAlbums(year);

        Mono<PageResult<ArtistAlbumStatsResponse.ArtistAlbumStatsItemResponse>> pageMono =
                albumStatsRepository.findArtistAlbumCounts(year, cursorAlbumCount, cursorArtistId, fetchSize)
                        .collectList()
                        .map(list -> KeysetPageAssembler.toPage(
                                list,
                                size,
                                last -> CursorCodec.encode(
                                        new ArtistAlbumStatsResponse.ArtistAlbumStatsCursor(last.albumCount(), last.artistId())
                                )
                        ));

        return Mono.zip(totalAlbumsMono, pageMono)
                .map(t -> new ArtistAlbumStatsResponse.ArtistAlbumStatsResultResponse(year, t.getT1(), t.getT2()));

    }

    /**
     * 특정 아티스트의 앨범 목록을 조회한다.
     *
     * <p>year가 없으면 (releaseYear, albumId) 커서, year가 있으면 (albumId) 커서를 사용한다.</p>
     *
     * @param artistId 아티스트 ID
     * @param year     연도(없으면 전체)
     * @param cursor   다음 페이지 커서
     * @param size     페이지 크기
     * @return 아티스트 앨범 목록 결과
     */
    @Override
    public Mono<ArtistAlbumsResponse.ArtistAlbumsResultResponse> getArtistAlbums(
            Long artistId,
            Integer year,
            String cursor,
            int size
    ) {
        ArtistAlbumsResponse.ArtistAlbumsCursor decoded =
                CursorCodec.decodeOrNull(cursor, ArtistAlbumsResponse.ArtistAlbumsCursor.class);

        Integer cursorReleaseYear = (decoded == null) ? null : decoded.releaseYear();
        Long cursorAlbumId = (decoded == null) ? null : decoded.albumId();

        int fetchSize = KeysetPageAssembler.fetchSize(size);

        Mono<Long> totalMono = albumStatsRepository.countArtistAlbums(artistId, year);

        Mono<PageResult<ArtistAlbumsResponse.ArtistAlbumsItemResponse>> pageMono =
                albumStatsRepository.findArtistAlbumsKeyset(artistId, year, cursorReleaseYear, cursorAlbumId, fetchSize)
                        .collectList()
                        .map(list -> KeysetPageAssembler.toPage(
                                list,
                                size,
                                last -> {
                                    ArtistAlbumsResponse.ArtistAlbumsCursor nc = (year == null)
                                            ? new ArtistAlbumsResponse.ArtistAlbumsCursor(last.releaseYear(), last.albumId())
                                            : new ArtistAlbumsResponse.ArtistAlbumsCursor(null, last.albumId());

                                    return CursorCodec.encode(nc);
                                }
                        ));

        return requireArtistExists(artistId)
                .then(Mono.zip(totalMono, pageMono))
                .map(t -> new ArtistAlbumsResponse.ArtistAlbumsResultResponse(artistId, year, t.getT1(), t.getT2()));


    }

    /**
     * artistId가 존재하지 않으면 {@link NotFoundException}을 발생시킨다.
     *
     * @param artistId 아티스트 ID
     * @return 존재 시 완료(빈 Mono), 미존재 시 에러
     */
    private Mono<Void> requireArtistExists(Long artistId) {
        return albumStatsRepository.existsArtist(artistId)
                .flatMap(exists -> exists
                        ? Mono.empty()
                        : Mono.error(new NotFoundException("ARTIST_NOT_FOUND", "artist not found")));
    }

}
