package com.musicinsights.spotifycatalog.application.common.pagination;

import java.util.List;

/**
 * 커서 기반 페이지 결과 DTO.
 *
 * @param items      현재 페이지 아이템 목록
 * @param hasNext    다음 페이지 존재 여부
 * @param nextCursor 다음 페이지 조회용 커서(없으면 null)
 * @param <T>        아이템 타입
 */
public record PageResult<T>(
        List<T> items,
        boolean hasNext,
        String nextCursor
) {}
