package com.musicinsights.spotifycatalog.infrastructure.input.ndjson;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link NormalizeUtils} 단위 테스트.
 *
 * <p>입력 문자열 정규화/파싱 유틸의 기대 동작을 검증한다:
 * 아티스트 분리, 날짜 파싱, explicit 파싱, 문자열 정규화(norm),
 * 길이(mm:ss) 파싱, SHA-256 해시 생성, simplify/키 생성(artistKey/albumKey/trackKey).</p>
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

    /**
     * simplify는 null 입력에 대해 null을 반환해야 한다.
     */
    @Test
    @DisplayName("simplify: null 입력이면 null 반환")
    void simplify_null_returnsNull() {
        assertNull(NormalizeUtils.simplify(null));
    }

    /**
     * simplify는 trim/공백 제거, 소문자화, 특수문자 제거를 수행해야 한다.
     */
    @Test
    @DisplayName("simplify: trim/공백 제거 + 소문자화 + 특수문자 제거")
    void simplify_removesSpacesLowercasesAndStripsSymbols() {
        // 공백/탭/특수문자 제거, 소문자화
        assertEquals("helloworld123", NormalizeUtils.simplify("  Hello  World! 123  "));
        assertEquals("abc", NormalizeUtils.simplify(" A\tB\nC "));
    }

    /**
     * simplify는 악센트(결합문자)를 제거해야 한다.
     */
    @Test
    @DisplayName("simplify: 악센트(결합문자) 제거")
    void simplify_removesDiacritics() {
        // é -> e
        assertEquals("beyonce", NormalizeUtils.simplify("Beyoncé"));
        // ö -> o
        assertEquals("zoe", NormalizeUtils.simplify("Zoë"));
    }

    /**
     * simplify는 한글을 유지하고 기호/이모지를 제거해야 한다.
     */
    @Test
    @DisplayName("simplify: 한글은 유지되고, 기호/이모지는 제거된다")
    void simplify_keepsKoreanAndRemovesEmoji() {
        System.out.println("simplify(아이유) = [" + NormalizeUtils.simplify("아이유") + "]");
        System.out.println("simplify regex result for ascii = [" + NormalizeUtils.simplify("BTS") + "]");

        assertEquals("아이유", NormalizeUtils.simplify("아이유"));

        assertEquals("아이유", NormalizeUtils.simplify("아이유✨"));
        assertEquals("방탄소년단", NormalizeUtils.simplify("방탄소년단!!!"));
    }

    /**
     * artistKey는 null/blank 입력에 대해 null을 반환해야 한다.
     */
    @Test
    @DisplayName("artistKey: null/blank면 null")
    void artistKey_nullOrBlank_returnsNull() {
        assertNull(NormalizeUtils.artistKey(null));
        assertNull(NormalizeUtils.artistKey("   "));
    }

    /**
     * artistKey는 의미상 동일한 입력(대소문자/공백/특수문자 차이)을 동일 키로 정규화해야 한다.
     */
    @Test
    @DisplayName("artistKey: 동일 의미(대소문자/공백/특수문자 차이)는 같은 키")
    void artistKey_normalizesToSameKey() {
        assertEquals(
                NormalizeUtils.artistKey("BTS"),
                NormalizeUtils.artistKey("  b t s!! ")
        );
        assertEquals(
                NormalizeUtils.artistKey("IU"),
                NormalizeUtils.artistKey(" I.U ")
        );
    }

    /**
     * albumKey는 albumName이 null/blank인 경우 null을 반환해야 한다.
     */
    @Test
    @DisplayName("albumKey: albumName blank면 null")
    void albumKey_blankName_returnsNull() {
        assertNull(NormalizeUtils.albumKey("   ", LocalDate.of(2020, 1, 1)));
        assertNull(NormalizeUtils.albumKey(null, LocalDate.of(2020, 1, 1)));
    }

    /**
     * albumKey는 같은 앨범명/같은 날짜면 같은 키를 생성하고,
     * 날짜가 다르면 다른 키를 생성해야 한다.
     */
    @Test
    @DisplayName("albumKey: 같은 앨범명/같은 날짜면 같은 키, 날짜가 다르면 다른 키")
    void albumKey_sameNameSameDate_sameKey_differentDate_differentKey() {
        LocalDate d1 = LocalDate.of(2020, 1, 1);
        LocalDate d2 = LocalDate.of(2021, 1, 1);

        String k1 = NormalizeUtils.albumKey(" Album A ", d1);
        String k2 = NormalizeUtils.albumKey("ALBUM-A!!", d1);
        String k3 = NormalizeUtils.albumKey("Album A", d2);

        assertEquals(k1, k2);
        assertNotEquals(k1, k3);
    }

    /**
     * albumKey는 releaseDate가 null인 경우 날짜 토큰에 "null"을 포함해야 한다.
     */
    @Test
    @DisplayName("albumKey: releaseDate null은 'null'로 포함된다")
    void albumKey_nullDate_includesNullToken() {
        String k = NormalizeUtils.albumKey("AlbumA", null);
        assertTrue(k.endsWith("|null"));
    }

    /**
     * trackKey는 아티스트 순서가 달라도 동일 키가 생성되도록(정렬) 처리해야 한다.
     */
    @Test
    @DisplayName("trackKey: 아티스트 순서가 달라도 동일 키(정렬)로 생성된다")
    void trackKey_artistOrderDoesNotMatter() {
        LocalDate d = LocalDate.of(2020, 1, 1);

        String k1 = NormalizeUtils.trackKey(
                "Song A",
                "Album A",
                d,
                List.of("IU", "BTS")
        );

        String k2 = NormalizeUtils.trackKey(
                "song a",
                " album-a!! ",
                d,
                List.of("BTS", "IU")
        );

        assertEquals(k1, k2);
    }

    /**
     * trackKey는 title/album이 null이어도 키를 생성해야 하며,
     * artists 입력에서 null/blank는 제거되어야 한다.
     */
    @Test
    @DisplayName("trackKey: title/album null이면 빈 문자열로 들어가고, artists는 null/blank가 제거된다")
    void trackKey_allowsNullTitleAlbum_andFiltersNullArtists() {
        String k = NormalizeUtils.trackKey(
                null,
                null,
                null,
                Arrays.asList("IU", null, "  ")
        );

        assertNotNull(k);
        assertTrue(k.endsWith("iu")); // IU만 남아야 함
    }

    /**
     * trackKey는 releaseDate가 null인 경우 날짜 파트가 빈 문자열로 생성되어야 한다.
     */
    @Test
    @DisplayName("trackKey: releaseDate null이면 날짜 파트는 빈 문자열")
    void trackKey_nullDate_datePartEmpty() {
        String k = NormalizeUtils.trackKey(
                "Song",
                "Album",
                null,
                List.of("IU")
        );

        // "song|album||iu" 형태를 기대 (날짜 파트가 비어있어서 ||)
        assertTrue(k.contains("||"));
    }
}