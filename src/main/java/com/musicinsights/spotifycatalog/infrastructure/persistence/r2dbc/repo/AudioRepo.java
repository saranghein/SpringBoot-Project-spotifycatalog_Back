package com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.repo;

import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.BatchSqlSupport;
import com.musicinsights.spotifycatalog.infrastructure.persistence.r2dbc.row.AudioRow;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * audio_feature 테이블에 대한 배치 upsert 기능을 제공하는 Repository입니다.
 * <p>
 * 트랙별 오디오 특성(tempo, loudness 등)을 대량 입력할 때 CHUNK 단위로 분할하여 처리합니다.
 */
@Component
public class AudioRepo extends BatchSqlSupport {

    /** 오디오 특성 배치 업서트 시 한 번에 처리할 최대 행 수 */
    private static final int CHUNK = 400;

    /**
     * R2DBC {@link DatabaseClient}를 주입받아 배치 SQL 실행 기반을 초기화합니다.
     *
     * @param db R2DBC DatabaseClient
     */
    public AudioRepo(DatabaseClient db) {
        super(db);
    }

    /**
     * 오디오 특성 목록을 배치로 upsert 합니다.
     * <p>
     * track_id 기준 중복 키가 발생하면 각 오디오 특성 컬럼을 최신 값으로 갱신합니다.
     *
     * @param rows upsert할 오디오 특성 목록
     * @return 영향을 받은 행 수(배치 합계)
     */
    public Mono<Long> upsertAudioFeatures(List<AudioRow> rows) {
        return chunkedSum(rows, CHUNK, this::upsertOnce);
    }

    /**
     * 주어진 rows를 단일 INSERT ... ON DUPLICATE KEY UPDATE로 실행합니다.
     *
     * @param rows upsert할 오디오 특성 목록(비어있지 않음)
     * @return 영향을 받은 행 수
     */
    private Mono<Long> upsertOnce(List<AudioRow> rows) {
        if (rows.isEmpty()) return Mono.just(0L);

        StringBuilder sql = new StringBuilder("""
            INSERT INTO audio_feature(
              track_id, tempo, loudness, energy, danceability, positiveness,
              speechiness, liveness, acousticness, instrumentalness, musical_key, time_signature
            ) VALUES
        """);

        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("(:tid").append(i)
                    .append(", :te").append(i)
                    .append(", :lo").append(i)
                    .append(", :en").append(i)
                    .append(", :da").append(i)
                    .append(", :po").append(i)
                    .append(", :sp").append(i)
                    .append(", :li").append(i)
                    .append(", :ac").append(i)
                    .append(", :in").append(i)
                    .append(", :mk").append(i)
                    .append(", :ts").append(i)
                    .append(")");
        }

        sql.append("""
            ON DUPLICATE KEY UPDATE
              tempo = VALUES(tempo),
              loudness = VALUES(loudness),
              energy = VALUES(energy),
              danceability = VALUES(danceability),
              positiveness = VALUES(positiveness),
              speechiness = VALUES(speechiness),
              liveness = VALUES(liveness),
              acousticness = VALUES(acousticness),
              instrumentalness = VALUES(instrumentalness),
              musical_key = VALUES(musical_key),
              time_signature = VALUES(time_signature)
        """);

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql.toString());
        for (int i = 0; i < rows.size(); i++) {
            AudioRow r = rows.get(i);

            spec = spec.bind("tid" + i, r.trackId());

            spec = bindOrNull(spec, "te" + i, r.tempo(), Double.class);
            spec = bindOrNull(spec, "lo" + i, r.loudness(), Double.class);
            spec = bindOrNull(spec, "en" + i, r.energy(), Integer.class);
            spec = bindOrNull(spec, "da" + i, r.danceability(), Integer.class);
            spec = bindOrNull(spec, "po" + i, r.positiveness(), Integer.class);
            spec = bindOrNull(spec, "sp" + i, r.speechiness(), Integer.class);
            spec = bindOrNull(spec, "li" + i, r.liveness(), Integer.class);
            spec = bindOrNull(spec, "ac" + i, r.acousticness(), Integer.class);
            spec = bindOrNull(spec, "in" + i, r.instrumentalness(), Integer.class);
            spec = bindOrNull(spec, "mk" + i, r.musicalKey(), String.class);
            spec = bindOrNull(spec, "ts" + i, r.timeSignature(), String.class);
        }

        return spec.fetch().rowsUpdated();
    }
}
