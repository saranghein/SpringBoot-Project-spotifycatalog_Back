package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row;

import java.time.LocalDate;
/**
 * 앨범의 기본 정보를 표현하는 Row 객체입니다.
 *
 * @param name        앨범명
 * @param releaseDate 앨범 발매일
 */
public record AlbumRow(String name,
                       LocalDate releaseDate) {}