package com.musicinsights.spotifycatalog.bootstrap;

import com.musicinsights.spotifycatalog.application.ingest.SpotifyIngestRebuildService;
import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.NdjsonLineReader;
import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.TrackRaw;
import com.musicinsights.spotifycatalog.application.ingest.SpotifyIngestService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * NDJSON 데이터셋을 배치로 DB에 적재하는 {@link CommandLineRunner}.
 *
 * <p>Profile이 {@code ingest}일 때만 활성화된다.</p>
 * <p>흐름: 라인 읽기 → JSON 파싱 → 800개 버퍼링 → 배치 ingest → 집계 rebuild</p>
 */
@Component
@Profile("ingest")
public class SpotifyNdjsonIngestRunner implements CommandLineRunner {

    /** classpath 데이터셋을 한 줄씩 읽는 리더 */
    private final NdjsonLineReader lineReader;

    /** 라인(JSON) → {@link TrackRaw} 변환용 ObjectMapper */
    private final ObjectMapper mapper;

    /** 배치 단위로 DB 적재를 수행하는 서비스 */
    private final SpotifyIngestService ingestService;

    /** 적재 후 통계/집계 rebuild 서비스 */
    private final SpotifyIngestRebuildService ingestRebuildService;

    /**
     * 의존성을 주입받아 러너를 초기화합니다.
     *
     * @param lineReader NDJSON 라인 리더
     * @param mapper JSON 파서(ObjectMapper)
     * @param ingestService 배치 적재 서비스
     * @param spotifyIngestRebuildService 통계 rebuild 서비스
     */
    public SpotifyNdjsonIngestRunner(
            NdjsonLineReader lineReader,
            ObjectMapper mapper,
            SpotifyIngestService ingestService,
            SpotifyIngestRebuildService spotifyIngestRebuildService
    ) {
        this.lineReader = lineReader;
        this.mapper = mapper;
        this.ingestService = ingestService;
        this.ingestRebuildService=spotifyIngestRebuildService;
    }

    /**
     * 애플리케이션 시작 시 실행되는 엔트리 포인트입니다.
     * <p>
     * 지정된 데이터셋 파일을 읽어 800줄 단위로 묶어 순차적으로 ingest 하며,
     * 전체 처리가 끝날 때까지 {@code block()}으로 대기합니다.
     *
     * @param args 커맨드라인 인자
     */
    @Override
    public void run(String... args) {
        String path = "dataset/900k Definitive Spotify Dataset.json";

        lineReader.readLines(path)
                .filter(line -> line != null && !line.isBlank())
                .map(this::parse)
                .buffer(800)
                .concatMap(ingestService::ingestBatch)
                .doOnNext(n -> System.out.println("Batch done. affected=" + n))
                .doOnError(e -> System.err.println("Ingest failed: " + e.getMessage()))
                .then(Mono.defer(() ->
                        ingestRebuildService.rebuild()
                                .doOnSuccess(n -> System.out.println("Stats rebuild done. affected=" + n))
                ))
                .then()
                .block();
    }

    /**
     * NDJSON의 한 줄(JSON 문자열)을 {@link TrackRaw}로 파싱합니다.
     *
     * @param line JSON 한 줄 문자열
     * @return 파싱된 TrackRaw
     * @throws IllegalStateException JSON 파싱 실패 시
     */
    private TrackRaw parse(String line) {
        try {
            return mapper.readValue(line, TrackRaw.class);
        } catch (Exception e) {
            throw new IllegalStateException("JSON parse error", e);
        }
    }
}
