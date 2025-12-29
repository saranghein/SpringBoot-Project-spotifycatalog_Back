package com.musicinsights.spotifycatalog.bootstrap;

import com.musicinsights.spotifycatalog.application.ingest.SpotifyIngestRebuildService;
import com.musicinsights.spotifycatalog.application.ingest.SpotifyIngestService;
import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.NdjsonLineReader;
import com.musicinsights.spotifycatalog.infrastructure.input.ndjson.TrackRaw;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link SpotifyNdjsonIngestRunner} 단위 테스트.
 *
 * <p>NDJSON 파일을 line 단위로 읽고({@link NdjsonLineReader}),
 * 각 line을 {@link TrackRaw}로 파싱({@link ObjectMapper#readValue(String, Class)})한 뒤,
 * 800개 단위로 buffer 하여 {@link SpotifyIngestService#ingestBatch(List)}를 호출하는 흐름을 검증한다.</p>
 *
 * <p>JSON 파싱 실패 또는 ingest 실패 시 예외 전파 동작도 함께 검증한다.</p>
 */
@DisplayName("배치 테스트")
class SpotifyNdjsonIngestRunnerTest {

    private static final String PATH = "dataset/900k Definitive Spotify Dataset.json";

    /**
     * blank line을 제외한 비-blank line을 파싱한 뒤,
     * 최초 800개만 batch로 ingest되는지 검증한다.
     */
    @DisplayName("800개만 ingest되는지 검증")
    @Test
    void run_readsLines_parses_buffers800_andCallsIngestBatch() throws Exception {
        // given
        NdjsonLineReader lineReader = mock(NdjsonLineReader.class);
        ObjectMapper om = mock(ObjectMapper.class);
        SpotifyIngestService ingestService = mock(SpotifyIngestService.class);
        SpotifyIngestRebuildService rebuildService = mock(SpotifyIngestRebuildService.class);

        SpotifyNdjsonIngestRunner runner =
                new SpotifyNdjsonIngestRunner(lineReader, om, ingestService, rebuildService);

        List<String> lines = Flux.range(0, 801)
                .map(i -> i == 0 ? "   " : "{\"song\":\"s" + i + "\"}") // 0번은 blank
                .collectList()
                .block();

        when(lineReader.readLines(PATH)).thenReturn(Flux.fromIterable(lines));

        when(om.readValue(anyString(), eq(TrackRaw.class)))
                .thenAnswer(inv -> {
                    String json = inv.getArgument(0, String.class);
                    TrackRaw r = new TrackRaw();
                    r.song = json;
                    return r;
                });

        when(ingestService.ingestBatch(anyList())).thenReturn(Mono.just(1L));

        when(rebuildService.rebuild()).thenReturn(Mono.just(1L));

        // when
        runner.run();

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TrackRaw>> captor = ArgumentCaptor.forClass(List.class);
        verify(ingestService, times(1)).ingestBatch(captor.capture());

        List<TrackRaw> batch = captor.getValue();
        assertEquals(800, batch.size());

        verify(om, times(800)).readValue(anyString(), eq(TrackRaw.class));
        verify(rebuildService, times(1)).rebuild();
    }

    /**
     * 비-blank line이 800개를 초과하면 여러 batch로 분할되어
     * ingestBatch가 반복 호출되는지 검증한다.
     */
    @DisplayName("여러 배치로 나뉘어 ingestBatch가 호출되는지 검증")
    @Test
    void run_twoBatches_whenMoreThan800_nonBlankLines() throws Exception {
        // given
        NdjsonLineReader lineReader = mock(NdjsonLineReader.class);
        ObjectMapper om = mock(ObjectMapper.class);
        SpotifyIngestService ingestService = mock(SpotifyIngestService.class);
        SpotifyIngestRebuildService rebuildService = mock(SpotifyIngestRebuildService.class);

        SpotifyNdjsonIngestRunner runner =
                new SpotifyNdjsonIngestRunner(lineReader, om, ingestService, rebuildService);

        List<String> lines = Flux.range(1, 1601)
                .map(i -> "{\"song\":\"s" + i + "\"}")
                .collectList()
                .block();

        when(lineReader.readLines(PATH)).thenReturn(Flux.fromIterable(lines));
        when(om.readValue(anyString(), eq(TrackRaw.class))).thenAnswer(inv -> new TrackRaw());
        when(ingestService.ingestBatch(anyList())).thenReturn(Mono.just(1L));

        when(rebuildService.rebuild()).thenReturn(Mono.just(1L));

        // when
        runner.run();

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TrackRaw>> captor = ArgumentCaptor.forClass(List.class);
        verify(ingestService, times(3)).ingestBatch(captor.capture());

        List<List<TrackRaw>> allBatches = captor.getAllValues();
        assertEquals(800, allBatches.get(0).size());
        assertEquals(800, allBatches.get(1).size());
        assertEquals(1, allBatches.get(2).size());

        verify(rebuildService, times(1)).rebuild();
    }

    /**
     * JSON 파싱 단계에서 예외가 발생하면
     * Runner가 {@link IllegalStateException}으로 감싸서 던지는지 검증한다.
     */
    @DisplayName("예외 발생 시 Runner가 IllegalStateException 던지는지 검증")
    @Test
    void run_throwsIllegalStateException_whenJsonParseFails() throws Exception {
        // given
        NdjsonLineReader lineReader = mock(NdjsonLineReader.class);
        ObjectMapper om = mock(ObjectMapper.class);
        SpotifyIngestService ingestService = mock(SpotifyIngestService.class);
        SpotifyIngestRebuildService rebuildService = mock(SpotifyIngestRebuildService.class);

        SpotifyNdjsonIngestRunner runner =
                new SpotifyNdjsonIngestRunner(lineReader, om, ingestService, rebuildService);

        when(lineReader.readLines(PATH)).thenReturn(Flux.just("{bad-json}"));
        when(om.readValue(anyString(), eq(TrackRaw.class))).thenThrow(new RuntimeException("boom"));

        // when / then
        IllegalStateException ex = assertThrows(IllegalStateException.class, runner::run);
        assertTrue(ex.getMessage().contains("JSON parse error"));

        verify(ingestService, never()).ingestBatch(anyList());
        verify(rebuildService, never()).rebuild();
    }

    /**
     * ingestBatch 단계에서 예외가 발생하면
     * Runner가 해당 예외를 그대로 전파하고, rebuild는 호출되지 않는지 검증한다.
     */
    @DisplayName("ingestBatch 단계에서 에러가 발생하면 Runner가 해당 예외를 그대로 전파하는지 검증")
    @Test
    void run_propagatesError_whenIngestFails() throws Exception {
        // given
        NdjsonLineReader lineReader = mock(NdjsonLineReader.class);
        ObjectMapper om = mock(ObjectMapper.class);
        SpotifyIngestService ingestService = mock(SpotifyIngestService.class);
        SpotifyIngestRebuildService rebuildService = mock(SpotifyIngestRebuildService.class);

        SpotifyNdjsonIngestRunner runner =
                new SpotifyNdjsonIngestRunner(lineReader, om, ingestService, rebuildService);

        when(lineReader.readLines(PATH)).thenReturn(Flux.just("{\"song\":\"s1\"}"));
        when(om.readValue(anyString(), eq(TrackRaw.class))).thenReturn(new TrackRaw());
        when(ingestService.ingestBatch(anyList())).thenReturn(Mono.error(new RuntimeException("db down")));

        when(rebuildService.rebuild()).thenReturn(Mono.just(1L));

        // when / then
        RuntimeException ex = assertThrows(RuntimeException.class, runner::run);
        assertTrue(ex.getMessage().contains("db down"));

        verify(rebuildService, never()).rebuild();
    }
}
