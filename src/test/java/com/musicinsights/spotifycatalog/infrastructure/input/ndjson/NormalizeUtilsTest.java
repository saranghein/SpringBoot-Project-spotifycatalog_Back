package com.musicinsights.spotifycatalog.infrastructure.input.ndjson;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link NormalizeUtils} 단위 테스트.
 *
 * <p>입력 문자열 정규화/파싱 유틸의 기대 동작을 검증한다:
 * 아티스트 분리, 날짜 파싱, explicit 파싱, 문자열 정규화(norm),
 * 길이(mm:ss) 파싱, SHA-256 해시 생성.</p>
 */
@DisplayName("정규화 util 테스트")
class NormalizeUtilsTest {


    /**
     * splitArtists는 null/blank 입력에 대해 빈 리스트를 반환해야 한다.
     */
    @DisplayName("splitArtists null/blank 입력에 대해 빈 리스트 반환 검증")
    @Test
    void splitArtists_nullOrBlank_returnsEmptyList() {
        assertEquals(List.of(), NormalizeUtils.splitArtists(null));
        assertEquals(List.of(), NormalizeUtils.splitArtists(""));
        assertEquals(List.of(), NormalizeUtils.splitArtists("   "));
    }

    /**
     * splitArtists는 콤마로 분리하고, 각 토큰을 trim하며, 빈 토큰은 제거해야 한다.
     */
    @DisplayName("splitArtists 콤마로 분리, 각 토큰을 trim, 빈 토큰은 제거 검증")
    @Test
    void splitArtists_splitsByComma_trims_andSkipsEmptyTokens() {
        List<String> out = NormalizeUtils.splitArtists(" IU,  BTS , , NewJeans  ,");
        assertEquals(List.of("IU", "BTS", "NewJeans"), out);
    }

    /**
     * splitArtists는 콤마가 없는 경우 단일 아티스트로 처리해야 한다.
     */
    @DisplayName("splitArtists 콤마가 없는 경우 단일 아티스트로 처리 검증")
    @Test
    void splitArtists_noComma_singleArtist() {
        assertEquals(List.of("IU"), NormalizeUtils.splitArtists(" IU "));
    }


    /**
     * parseDateOrNull은 yyyy-MM-dd 형식의 유효한 날짜를 {@link LocalDate}로 파싱해야 한다.
     */
    @DisplayName("parseDateOrNull yyyy-MM-dd 형식의 유효한 날짜를 LocalDate로 파싱 검증")
    @Test
    void parseDateOrNull_validDate_parses() {
        assertEquals(LocalDate.of(2013, 4, 29), NormalizeUtils.parseDateOrNull("2013-04-29"));
        assertEquals(LocalDate.of(2013, 4, 29), NormalizeUtils.parseDateOrNull(" 2013-04-29 "));
    }

    /**
     * parseDateOrNull은 null/blank/잘못된 형식의 입력에 대해 null을 반환해야 한다.
     */
    @DisplayName("parseDateOrNull null/blank/잘못된 형식의 입력에 대해 null을 반환 검증")
    @Test
    void parseDateOrNull_blankOrInvalid_returnsNull() {
        assertNull(NormalizeUtils.parseDateOrNull(null));
        assertNull(NormalizeUtils.parseDateOrNull(""));
        assertNull(NormalizeUtils.parseDateOrNull("  "));
        assertNull(NormalizeUtils.parseDateOrNull("2013/04/29"));
        assertNull(NormalizeUtils.parseDateOrNull("not-a-date"));
        assertNull(NormalizeUtils.parseDateOrNull("2013-13-40"));
    }

    /**
     * parseExplicit은 "Yes"(대소문자 무관)를 true로 해석해야 한다.
     */
    @DisplayName("parseExplicit \"Yes\"(대소문자 무관)를 true로 해석 검증")
    @Test
    void parseExplicit_yesIsTrue_caseInsensitive() {
        assertTrue(NormalizeUtils.parseExplicit("Yes"));
        assertTrue(NormalizeUtils.parseExplicit("YES"));
        assertTrue(NormalizeUtils.parseExplicit("yEs"));
    }

    /**
     * parseExplicit은 "Yes"가 아니거나 null/blank이면 false를 반환해야 한다.
     */
    @DisplayName("parseExplicit \"Yes\"가 아니거나 null/blank이면 false 반환 검증")
    @Test
    void parseExplicit_nonYesOrNull_isFalse() {
        assertFalse(NormalizeUtils.parseExplicit(null));
        assertFalse(NormalizeUtils.parseExplicit(""));
        assertFalse(NormalizeUtils.parseExplicit("No"));
        assertFalse(NormalizeUtils.parseExplicit("true"));
        assertFalse(NormalizeUtils.parseExplicit("Y"));
    }

    /**
     * norm은 null 입력에 대해 null을 반환해야 한다.
     */
    @DisplayName("norm null 입력에 대해 null을 반환 검증")
    @Test
    void norm_null_returnsNull() {
        assertNull(NormalizeUtils.norm(null));
    }

    /**
     * norm은 문자열을 trim하고, 결과가 빈 문자열이면 null을 반환해야 한다.
     */
    @DisplayName("norm 문자열을 trim하고, 결과가 빈 문자열이면 null을 반환 검증")
    @Test
    void norm_trims_andEmptyBecomesNull() {
        assertEquals("abc", NormalizeUtils.norm(" abc "));
        assertNull(NormalizeUtils.norm(""));
        assertNull(NormalizeUtils.norm("   "));
    }

    /**
     * parseDurationMsOrNull은 mm:ss 형식의 입력을 밀리초로 변환해야 한다.
     */
    @DisplayName("parseDurationMsOrNull mm:ss 형식의 입력을 밀리초로 변환 검증")
    @Test
    void parseDurationMsOrNull_validMmSs_returnsMs() {
        // 03:47 -> (3*60+47)*1000 = 227000
        assertEquals(227000, NormalizeUtils.parseDurationMsOrNull("03:47"));
        assertEquals(227000, NormalizeUtils.parseDurationMsOrNull(" 3:47 ")); // trimming works
        assertEquals(0, NormalizeUtils.parseDurationMsOrNull("00:00"));
    }

    /**
     * parseDurationMsOrNull은 형식이 잘못된 입력에 대해 null을 반환해야 한다.
     */
    @DisplayName("parseDurationMsOrNull 형식이 잘못된 입력에 대해 null을 반환 검증")
    @Test
    void parseDurationMsOrNull_invalid_returnsNull() {
        assertNull(NormalizeUtils.parseDurationMsOrNull(null));
        assertNull(NormalizeUtils.parseDurationMsOrNull(""));
        assertNull(NormalizeUtils.parseDurationMsOrNull("  "));
        assertNull(NormalizeUtils.parseDurationMsOrNull("3"));
        assertNull(NormalizeUtils.parseDurationMsOrNull("03:"));
        assertNull(NormalizeUtils.parseDurationMsOrNull(":47"));
        assertNull(NormalizeUtils.parseDurationMsOrNull("aa:bb"));
        assertNull(NormalizeUtils.parseDurationMsOrNull("03:60"));
        assertNull(NormalizeUtils.parseDurationMsOrNull("-1:10"));
        assertNull(NormalizeUtils.parseDurationMsOrNull("01:-5"));
        assertNull(NormalizeUtils.parseDurationMsOrNull("01:02:03"));
    }

    /**
     * sha256Hex는 동일 입력에 대해 동일 해시를 생성하고(결정성),
     * 결과는 64자리 소문자 hex 문자열이어야 한다.
     */
    @DisplayName("sha256Hex 동일 입력 동일 해시 생성, 64자리 소문자 hex 문자열인지 검증")
    @Test
    void sha256Hex_isDeterministic_and64HexChars() {
        String h1 = NormalizeUtils.sha256Hex("abc");
        String h2 = NormalizeUtils.sha256Hex("abc");
        String h3 = NormalizeUtils.sha256Hex("abcd");

        assertEquals(h1, h2);
        assertNotEquals(h1, h3);

        assertNotNull(h1);
        assertEquals(64, h1.length());
        assertTrue(h1.matches("[0-9a-f]{64}"));
    }

    /**
     * sha256Hex가 UTF-8 문자열도 안정적으로 처리하는지 검증한다.
     */
    @DisplayName("sha256Hex UTF-8도 안정적으로 처리하는지 검증")
    @Test
    void sha256Hex_supportsUtf8() {
        String h = NormalizeUtils.sha256Hex("아이유");
        assertEquals(64, h.length());
        assertTrue(h.matches("[0-9a-f]{64}"));
    }
}