package com.musicinsights.spotifycatalog.infrastructure.input.ndjson;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * NDJSON 파일을 "한 줄씩" 읽기 위한 라인 리더입니다.
 * <p>
 * {@link BufferedReader#lines()}의 lazy 스트림을 이용해 메모리 사용량을 최소화하며,
 * 리소스 생성/사용/해제를 {@link Flux#using}으로 안전하게 관리합니다.
 * <p>
 * 파일 I/O는 blocking 작업이므로 {@link Schedulers#boundedElastic()}에서 실행합니다.
 */
@Component
// “한 줄씩” 읽는 메모리 최적화 장치
public class NdjsonLineReader {

    /**
     * classpath 상의 NDJSON 파일을 한 줄씩 {@link Flux}로 반환합니다.
     *
     * @param classpath classpath 내 파일 경로 (예: {@code data/sample.ndjson})
     * @return 파일의 각 라인을 순차적으로 방출하는 Flux
     *
     * <p><b>{@code BufferedReader#lines()}는 lazy stream이라서 한 줄씩 순차적으로 흘러와</b></p>
     * <ul>
     *     <li>{@code Flux.using(...)}은 리소스(BufferedReader)를</li>
     *     <li>생성 → 사용 → 종료(close)</li>
     *     <li>를 Flux 라이프사이클에 맞춰 안전하게 처리해 줌</li>
     * </ul>
     *
     * <p><b>{@code boundedElastic()}을 쓰는 이유</b></p>
     * <ul>
     *     <li>파일 I/O는 “blocking”</li>
     *     <li>WebFlux/Reactive에서 blocking을 기본 스레드에서 하면 성능/데드락 이슈</li>
     *     <li>blocking 작업은 elastic 전용 스레드풀로 넘겨서 안전하게 함</li>
     * </ul>
     */
    public Flux<String> readLines(String classpath) {
        return Flux.using(
                () -> new BufferedReader(new InputStreamReader(
                        new ClassPathResource(classpath).getInputStream(),
                        StandardCharsets.UTF_8
                )),
                br -> Flux.fromStream(br.lines()),
                br -> {
                    try { br.close(); } catch (Exception ignored) {}
                }
        ).subscribeOn(Schedulers.boundedElastic()); // blocking IO는 elastic으로
    }
}
