package com.musicinsights.spotifycatalog.application.common.pagination;

import java.util.List;
import java.util.function.Function;

/**
 * keyset pagination 결과를 {@link PageResult}로 조립하는 유틸리티.
 *
 * <p>size+1(fetchSize)로 조회한 결과를 기준으로 hasNext를 판단하고,
 * hasNext인 경우 nextCursor를 생성한다.</p>
 */
public final class KeysetPageAssembler {

    private KeysetPageAssembler() {}

    /**
     * @param fetched  fetchSize(size+1)로 가져온 결과 list
     * @param size     클라이언트가 요청한 page size
     * @param nextCursorFactory "마지막 아이템"을 받아 nextCursor 문자열을 만들 함수 (hasNext일 때만 호출됨)
     */
    public static <T> PageResult<T> toPage(List<T> fetched, int size, Function<T, String> nextCursorFactory) {
        boolean hasNext = fetched.size() > size;

        List<T> items = hasNext ? fetched.subList(0, size) : fetched;

        String nextCursor = null;
        if (hasNext && !items.isEmpty()) {
            T last = items.get(items.size() - 1);
            nextCursor = nextCursorFactory.apply(last);
        }

        return new PageResult<>(items, hasNext, nextCursor);
    }

    /**
     * keyset pagination에서 다음 페이지 유무 판별을 위해 실제 조회에 사용할 limit 값을 반환한다.
     *
     * @param size 요청 페이지 크기
     * @return size + 1
     */
    public static int fetchSize(int size) {
        return size + 1;
    }
}
