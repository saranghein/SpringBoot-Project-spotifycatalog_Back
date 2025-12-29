package com.musicinsights.spotifycatalog.infrastructure.input.ndjson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * ingest 과정에서 반복적으로 사용하는 "정규화/파싱" 유틸리티입니다.
 * <p>
 * - 문자열 정규화(trim, 빈 값 처리)
 * - 날짜/길이 등 타입 파싱
 * - 아티스트 문자열 분리
 * - 트랙 식별용 해시 생성
 */
public class NormalizeUtils {

    /**
     * "A, B, C" 형태의 아티스트 문자열을 쉼표 기준으로 분리하여 리스트로 반환합니다.
     * <p>
     * 공백을 제거하고 빈 항목은 제외합니다.
     *
     * @param raw 원본 아티스트 문자열
     * @return 분리된 아티스트 이름 목록(입력이 비어있으면 빈 리스트)
     */
    public static List<String> splitArtists(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        // 데이터 특성상 보통 쉼표로 구분된 문자열
        String[] parts = raw.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String s = p.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    /**
     * ISO-8601 날짜 문자열(yyyy-MM-dd)을 {@link LocalDate}로 파싱합니다.
     * <p>
     * 빈 값이거나 파싱에 실패하면 null을 반환합니다.
     *
     * @param s 날짜 문자열 (예: "2013-04-29")
     * @return 파싱된 LocalDate 또는 null
     */
    public static LocalDate parseDateOrNull(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            return LocalDate.parse(s.trim()); // yyyy-MM-dd
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * explicit 여부 문자열을 boolean으로 변환합니다.
     * <p>
     * 데이터셋에서 "Yes"면 true로 처리합니다(대소문자 무시).
     *
     * @param s explicit 값 문자열
     * @return explicit이면 true, 아니면 false
     */
    public static boolean parseExplicit(String s) {
        return s != null && s.equalsIgnoreCase("Yes");
    }

    /**
     * 문자열을 정규화합니다.
     * <p>
     * trim 후 빈 문자열이면 null을 반환합니다.
     *
     * @param s 원본 문자열
     * @return 정규화된 문자열 또는 null
     */
    public static String norm(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 비교용 문자열 정규화 */
    static String simplify(String input) {
        if (input == null) return null;

        String result = Normalizer.normalize(input, Normalizer.Form.NFKC);

        // 소문자화 후 NFD로 분해 → 결합문자 제거
        result = Normalizer.normalize(result.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        // NFD로 인해 분해된 한글 자모를 다시 완성형으로 합치기
        result = Normalizer.normalize(result, Normalizer.Form.NFC);

        return result
                .replaceAll("[\\s\\p{Z}]", "")
                .replaceAll("[^a-z0-9\\p{IsHangul}]", "");
    }

    /** Artist 동일성 키 */
    public static String artistKey(String artistName) {
        return simplify(norm(artistName));
    }

    /** Album 동일성 키 */
    public static String albumKey(String albumName, LocalDate releaseDate) {
        String nk = simplify(norm(albumName));
        if (nk == null) return null;
        return nk + "|" + (releaseDate == null ? "null" : releaseDate.toString());
    }

    /** Track 자연키 문자열(해시 입력) */
    public static String trackKey(String title, String album, LocalDate releaseDate, List<String> artistsRaw) {
        String t = simplify(norm(title));
        String a = simplify(norm(album));
        String d = (releaseDate == null) ? "" : releaseDate.toString();

        String artistsKey = artistsRaw.stream()
                .map(NormalizeUtils::artistKey)
                .filter(Objects::nonNull)
                .sorted() // 순서 불변
                .collect(Collectors.joining(","));

        return String.join("|",
                t == null ? "" : t,
                a == null ? "" : a,
                d,
                artistsKey
        );
    }
    /**
     * "mm:ss" 형태의 길이 문자열을 밀리초(ms)로 변환합니다.
     * <p>
     * 파싱 실패/형식 불일치/비정상 값이면 null을 반환합니다.
     *
     * @param durationStr 길이 문자열 (예: "03:47")
     * @return 밀리초 값 또는 null
     */
    public static Integer parseDurationMsOrNull(String durationStr) {
        String s = norm(durationStr);
        if (s == null) return null;
        try {
            // "mm:ss" 가정 (데이터셋 Length)
            String[] parts = s.split(":");
            if (parts.length != 2) return null;

            int mm = Integer.parseInt(parts[0].trim());
            int ss = Integer.parseInt(parts[1].trim());
            if (mm < 0 || ss < 0 || ss >= 60) return null;

            return (mm * 60 + ss) * 1000;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 입력 문자열을 SHA-256으로 해시한 16진수 문자열을 반환합니다.
     * <p>
     * track에 자연키(유일키)가 없을 때 (song + album + releaseDate + artists) 등의 조합으로
     * 키를 만들고 이를 해시하여 track_hash로 사용하기 위한 목적입니다.
     *
     * @param input 해시할 원본 문자열
     * @return SHA-256 해시(hex)
     * @throws IllegalStateException 해시 알고리즘 사용에 실패한 경우
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
