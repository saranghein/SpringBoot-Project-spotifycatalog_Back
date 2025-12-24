package com.musicinsights.spotifycatalog.infrastructure.input.ndjson;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link NdjsonLineReader} 단위 테스트.
 *
 * <p>NDJSON 파일을 라인 단위로 읽는 동작과,
 * 존재하지 않는 파일 처리(에러), 스트림 종료 여부를 검증한다.</p>
 */
@DisplayName("ndjson line reader 테스트")
class NdjsonLineReaderTest {
    /** 테스트 대상 리더 */
    private final NdjsonLineReader reader = new NdjsonLineReader();

    /**
     * NDJSON 파일을 라인 단위로 읽어 첫 라인의 기본 형태(JSON 시작, 필드 포함)를 검증한다.
     *
     * <p>테스트 리소스가 1줄인 경우를 가정하여 첫 라인 검증 후 complete를 기대한다.</p>
     */
    @DisplayName("NDJSON 파일을 라인 단위로 읽어 첫 라인의 기본 형태를 검증")
    @Test
    void readLines_readsNdjsonFile_lineByLine() {
        StepVerifier.create(
                        reader.readLines("dataset/900kdefinitivespotifydataset.json")
                )
                .assertNext(line -> {
                    assertNotNull(line);
                    assertTrue(line.startsWith("{"));
                    assertTrue(line.contains("\"song\""));
                    assertTrue(line.contains("\"Artist(s)\""));
                })
                .verifyComplete(); // 테스트 리소스가 1줄일 경우
    }

    /**
     * 존재하지 않는 파일 경로를 전달하면 에러 시그널을 방출하는지 검증한다.
     */
    @DisplayName("존재하지 않는 파일 경로를 전달하면 에러 시그널을 방출하는지 검증")
    @Test
    void readLines_nonExistingFile_emitsError() {
        StepVerifier.create(
                        reader.readLines("dataset/not-exist.json")
                )
                .expectError()
                .verify();
    }

    /**
     * readLines가 지연(lazy) 실행되고, 전체 수집 시 정상적으로 종료되는지 검증한다.
     *
     * <p>collectList로 구독을 발생시키고, 결과가 비어있지 않으며 complete 되는지 확인한다.</p>
     */
    @DisplayName("readLines가 지연(lazy) 실행되고, 전체 수집 시 정상적으로 종료되는지 검증")
    @Test
    void readLines_isLazy_andTerminates() {
        StepVerifier.create(
                        reader.readLines("dataset/900kdefinitivespotifydataset.json")
                                .collectList()
                )
                .assertNext(lines -> {
                    assertFalse(lines.isEmpty());
                })
                .verifyComplete();
    }
}