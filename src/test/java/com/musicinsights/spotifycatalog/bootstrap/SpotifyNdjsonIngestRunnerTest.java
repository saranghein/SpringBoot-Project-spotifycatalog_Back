package com.musicinsights.spotifycatalog.bootstrap;

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
 * <p>NDJSON 파일을 읽고(JSON line 단위), {@link TrackRaw}로 파싱한 뒤,
 * 800개 단위로 buffer 하여 {@link SpotifyIngestService#ingestBatch(List)}를 호출하는 흐름을 검증한다.</p>
 *
 * <p>또한 JSON 파싱 실패/ingest 실패 시 예외 전파 동작을 함께 확인한다.</p>
 */
@DisplayName("배치 테스트")
class SpotifyNdjsonIngestRunnerTest {

    /**
     * 라인 읽기 → 공백 라인 필터링 → JSON 파싱 → 800개 버퍼링 → ingestBatch 1회 호출 흐름을 검증한다.
     *
     * <p>801줄 중 1줄을 blank로 만들어 필터링되도록 하고,
     * 결과적으로 800개만 ingest되는지 확인한다.</p>
     */
    @DisplayName("800개만 ingest되는지 검증")
    @Test
    void run_readsLines_parses_buffers800_andCallsIngestBatch() throws Exception {
        // given
        NdjsonLineReader lineReader = mock(NdjsonLineReader.class);
        ObjectMapper om = mock(ObjectMapper.class);
        SpotifyIngestService ingestService = mock(SpotifyIngestService.class);

        SpotifyNdjsonIngestRunner runner = new SpotifyNdjsonIngestRunner(lineReader, om, ingestService);

        // 801줄 중 1줄은 blank로 걸러짐 -> 실제 800개만 ingest
        List<String> lines = Flux.range(0, 801)
                .map(i -> i == 0 ? "   " : "{\"song\":\"s" + i + "\"}") // 0번은 blank
                .collectList()
                .block();

        when(lineReader.readLines("dataset/900kdefinitivespotifydataset.json"))
                .thenReturn(Flux.fromIterable(lines));

        // parse: 각 json line -> TrackRaw로 매핑
        // TrackRaw는 public 필드라 테스트에서 간단히 직접 생성해서 반환
        when(om.readValue(anyString(), eq(TrackRaw.class)))
                .thenAnswer(inv -> {
                    String json = inv.getArgument(0, String.class);
                    TrackRaw r = new TrackRaw();
                    r.song = json; // 구분용으로 그냥 넣음
                    return r;
                });

        // ingestBatch는 batch size만큼 처리했다고 가정하고 1L 반환
        when(ingestService.ingestBatch(anyList()))
                .thenReturn(Mono.just(1L));

        // when
        runner.run();

        // then: ingestBatch가 딱 1번 호출되어야 함 (800개)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TrackRaw>> captor = ArgumentCaptor.forClass(List.class);
        verify(ingestService, times(1)).ingestBatch(captor.capture());

        List<TrackRaw> batch = captor.getValue();
        assertEquals(800, batch.size());

        // blank 줄은 filter로 제거되므로 ObjectMapper.readValue 호출 대상이 아님
        verify(om, times(800)).readValue(anyString(), eq(TrackRaw.class));
    }

    /**
     * 800개를 초과하는 non-blank 라인이 들어올 경우 여러 배치로 나뉘어 ingestBatch가 호출되는지 검증한다.
     *
     * <p>1601줄이면 800 + 800 + 1로 총 3번 ingestBatch가 호출되어야 한다.</p>
     */
    @DisplayName("여러 배치로 나뉘어 ingestBatch가 호출되는지 검증")
    @Test
    void run_twoBatches_whenMoreThan800_nonBlankLines() throws Exception {
        // given
        NdjsonLineReader lineReader = mock(NdjsonLineReader.class);
        ObjectMapper om = mock(ObjectMapper.class);
        SpotifyIngestService ingestService = mock(SpotifyIngestService.class);

        SpotifyNdjsonIngestRunner runner = new SpotifyNdjsonIngestRunner(lineReader, om, ingestService);

        // 1601줄(모두 non-blank) -> 800 + 800 + 1 => ingestBatch 3번
        List<String> lines = Flux.range(1, 1601)
                .map(i -> "{\"song\":\"s" + i + "\"}")
                .collectList()
                .block();

        when(lineReader.readLines("dataset/900kdefinitivespotifydataset.json"))
                .thenReturn(Flux.fromIterable(lines));

        when(om.readValue(anyString(), eq(TrackRaw.class)))
                .thenAnswer(inv -> new TrackRaw());

        when(ingestService.ingestBatch(anyList()))
                .thenReturn(Mono.just(1L));

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
    }

    /**
     * JSON 파싱(ObjectMapper.readValue)에서 예외가 발생하면
     * Runner가 {@link IllegalStateException}으로 래핑해 던지는지 검증한다.
     *
     * <p>파싱이 실패한 경우 ingest는 호출되면 안 된다.</p>
     */
    @DisplayName("예외 발생 시 Runner가 IllegalStateException 던지는지 검증")
    @Test
    void run_throwsIllegalStateException_whenJsonParseFails() throws Exception {
        // given
        NdjsonLineReader lineReader = mock(NdjsonLineReader.class);
        ObjectMapper om = mock(ObjectMapper.class);
        SpotifyIngestService ingestService = mock(SpotifyIngestService.class);

        SpotifyNdjsonIngestRunner runner = new SpotifyNdjsonIngestRunner(lineReader, om, ingestService);

        when(lineReader.readLines("dataset/900kdefinitivespotifydataset.json"))
                .thenReturn(Flux.just("{bad-json}"));

        when(om.readValue(anyString(), eq(TrackRaw.class)))
                .thenThrow(new RuntimeException("boom"));

        // when / then
        IllegalStateException ex = assertThrows(IllegalStateException.class, runner::run);
        assertTrue(ex.getMessage().contains("JSON parse error"));

        // ingest는 호출되면 안 됨
        verifyNoInteractions(ingestService);
    }


    /**
     * ingestBatch 단계에서 에러가 발생하면 Runner가 해당 예외를 그대로 전파하는지 검증한다.
     *
     * <p>Runner 내부에서 block()을 사용한다면 예외가 호출자에게 동기적으로 전파된다.</p>
     */
    @DisplayName("ingestBatch 단계에서 에러가 발생하면 Runner가 해당 예외를 그대로 전파하는지 검증")
    @Test
    void run_propagatesError_whenIngestFails() throws Exception {
        // given
        NdjsonLineReader lineReader = mock(NdjsonLineReader.class);
        ObjectMapper om = mock(ObjectMapper.class);
        SpotifyIngestService ingestService = mock(SpotifyIngestService.class);

        SpotifyNdjsonIngestRunner runner = new SpotifyNdjsonIngestRunner(lineReader, om, ingestService);

        when(lineReader.readLines("dataset/900kdefinitivespotifydataset.json"))
                .thenReturn(Flux.just("{\"song\":\"s1\"}"));

        when(om.readValue(anyString(), eq(TrackRaw.class)))
                .thenReturn(new TrackRaw());

        when(ingestService.ingestBatch(anyList()))
                .thenReturn(Mono.error(new RuntimeException("db down")));

        // when / then: block() 때문에 예외가 밖으로 튀어나옴
        RuntimeException ex = assertThrows(RuntimeException.class, runner::run);
        assertTrue(ex.getMessage().contains("db down"));
    }
}