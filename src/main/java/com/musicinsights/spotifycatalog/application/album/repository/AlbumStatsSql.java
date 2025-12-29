package com.musicinsights.spotifycatalog.application.album.repository;

/**
 * 앨범 통계/목록 조회에 사용되는 SQL 상수 모음.
 *
 * <p>Repository에서 year 유무에 따라 서로 다른 쿼리를 선택하며,
 * 커서 기반(keyset) 페이징을 위해 비교 조건과 정렬 기준을 포함한다.</p>
 */
final class AlbumStatsSql {
    private AlbumStatsSql() {}

    /** 전체 기준: 아티스트별 발매 앨범 수(집계) + 커서 페이징 조회 */
    static final String SQL_FIND_ALL = """
        SELECT
          t.artist_id AS artistId,
          ar.name     AS artistName,
          t.albumCount
        FROM (
          SELECT aa.artist_id, COUNT(*) AS albumCount
          FROM album_artist aa
          JOIN album al ON al.id = aa.album_id
          WHERE al.release_year IS NOT NULL
          GROUP BY aa.artist_id
        ) t
        JOIN artist ar ON ar.id = t.artist_id
        WHERE (
           t.albumCount < ?
           OR (t.albumCount = ? AND t.artist_id > ?)
        )
        ORDER BY t.albumCount DESC, t.artist_id ASC
        LIMIT ?;
    """;

    /** 연도 기준: artist_album_count_year 집계 테이블 기반 조회 + 커서 페이징 */
    static final String SQL_FIND_BY_YEAR = """
        SELECT
          c.artist_id   AS artistId,
          ar.name       AS artistName,
          c.album_count AS albumCount
        FROM artist_album_count_year c
        JOIN artist ar ON ar.id = c.artist_id
        WHERE c.release_year = ?
          AND (
             c.album_count < ?
             OR (c.album_count = ? AND c.artist_id > ?)
          )
        ORDER BY c.album_count DESC, c.artist_id ASC
        LIMIT ?;
    """;

    /** 전체 기준: 특정 아티스트의 앨범 목록(연도 DESC, id ASC) + (releaseYear, albumId) 커서 페이징 */
    static final String SQL_ARTIST_ALBUMS_ALL = """
        SELECT
          al.id           AS albumId,
          al.name         AS albumName,
          al.release_year AS releaseYear
        FROM album_artist aa
        JOIN album al ON al.id = aa.album_id
        WHERE aa.artist_id = ?
          AND (
                al.release_year < ?
             OR (al.release_year = ? AND al.id > ?)
          )
        ORDER BY al.release_year DESC, al.id ASC
        LIMIT ?;
    """;

    /** 연도 기준: 특정 아티스트의 특정 연도 앨범 목록(id ASC) + albumId 커서 페이징 */
    static final String SQL_ARTIST_ALBUMS_BY_YEAR = """
        SELECT
          al.id           AS albumId,
          al.name         AS albumName,
          al.release_year AS releaseYear
        FROM album_artist aa
        JOIN album al ON al.id = aa.album_id
        WHERE aa.artist_id = ?
          AND al.release_year = ?
          AND al.id > ?
        ORDER BY al.id ASC
        LIMIT ?;
    """;

    /** 전체 기준: 특정 아티스트의 총 앨범 수 */
    static final String SQL_COUNT_ARTIST_ALBUMS_ALL = """
        SELECT COUNT(*) AS total
        FROM album_artist aa
        JOIN album al ON al.id = aa.album_id
        WHERE aa.artist_id = ?
    """;

    /** 연도 기준: 특정 아티스트의 특정 연도 앨범 수 */
    static final String SQL_COUNT_ARTIST_ALBUMS_BY_YEAR = """
        SELECT COUNT(*) AS total
        FROM album_artist aa
        JOIN album al ON al.id = aa.album_id
        WHERE aa.artist_id = ?
          AND al.release_year = ?
    """;

    /** 아티스트 존재 여부 확인(1행이라도 있으면 존재) */
    public static final String SQL_EXISTS_ARTIST = """
        SELECT 1 AS ok
        FROM artist
        WHERE id = ?
        LIMIT 1
    """;

    /** 전체 앨범 수 */
    static final String SQL_COUNT_ALBUMS_ALL = """
        SELECT COUNT(*) AS total
        FROM album
    """;

    /** 연도별 앨범 수 */
    static final String SQL_COUNT_ALBUMS_BY_YEAR = """
        SELECT COUNT(*) AS total
        FROM album
        WHERE release_year = ?
    """;

}
