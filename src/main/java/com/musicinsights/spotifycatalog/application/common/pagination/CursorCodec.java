package com.musicinsights.spotifycatalog.application.common.pagination;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;


/**
 * 커서 기반 페이징을 위한 인코딩/디코딩 유틸리티.
 *
 * <p>커서 객체를 JSON으로 직렬화한 뒤 Base64(URL-safe)로 변환한다.</p>
 */
public final class CursorCodec {

    /** 커서 직렬화/역직렬화용 ObjectMapper */
    private static final ObjectMapper MAPPER=new ObjectMapper();

    private CursorCodec() {}

    /**
     * 커서 객체를 Base64 문자열로 인코딩한다.
     *
     * @param cursor 커서 객체
     * @return 인코딩된 커서 문자열
     * @throws IllegalArgumentException 인코딩 실패 시
     */
    public static String encode(Object cursor) {
        try {
            String json = MAPPER.writeValueAsString(cursor);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to encode cursor", e);
        }
    }

    /**
     * Base64 문자열을 커서 객체로 디코딩한다.
     *
     * @param encoded 인코딩된 커서 문자열
     * @param type    커서 타입
     * @param <T>     커서 타입
     * @return 디코딩된 커서 객체
     * @throws IllegalArgumentException 디코딩 실패 시
     */
    public static <T> T decode(String encoded, Class<T> type) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            String json = new String(decoded, StandardCharsets.UTF_8);
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor", e);
        }
    }

    public static <T> T decodeOrNull(String cursor, Class<T> type) {
        if (cursor == null || cursor.isBlank()) return null;
        return CursorCodec.decode(cursor, type);
    }
}
