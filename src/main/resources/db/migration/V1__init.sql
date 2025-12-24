-- V1__init.sql
-- Spring Boot + R2DBC + MySQL (InnoDB, utf8mb4)
-- 요구사항: (연도 & 가수별 앨범 수 페이징), (좋아요 증가), (최근 1시간 좋아요 증가 Top10)

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1) Core: Artist / Album / Track

CREATE TABLE IF NOT EXISTS artist (
                                      id          BIGINT PRIMARY KEY AUTO_INCREMENT,
                                      name        VARCHAR(255) NOT NULL,
                                      created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

                                      UNIQUE KEY uk_artist_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS album (
                                     id           BIGINT PRIMARY KEY AUTO_INCREMENT,
                                     name         VARCHAR(255) NOT NULL,
                                     release_date DATE NULL,

    -- 연도 필터/그룹 최적화용: release_date에서 연도만 뽑아 저장(생성 컬럼)
                                     release_year SMALLINT
                                         GENERATED ALWAYS AS (YEAR(release_date)) STORED,

                                     created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    -- 같은 이름 앨범이 여러 번 나올 수 있지만, 데이터셋 특성상 (name, release_date)로 중복을 강하게 줄임
                                     UNIQUE KEY uk_album_name_date (name, release_date),

    -- 집계/필터 최적화
                                     KEY idx_album_release_date (release_date),
                                     KEY idx_album_release_year (release_year),

    --  연도 필터 후 album_id 조인까지 같이 빠르게
                                     KEY idx_album_year_id (release_year, id),

                                     KEY idx_album_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- Album <-> Artist (M:N)
CREATE TABLE IF NOT EXISTS album_artist (
                                            album_id  BIGINT NOT NULL,
                                            artist_id BIGINT NOT NULL,

                                            PRIMARY KEY (album_id, artist_id),

    -- "가수별"로 앨범을 모을 때 효율적
                                            KEY idx_album_artist_artist (artist_id, album_id),

                                            CONSTRAINT fk_album_artist_album
                                                FOREIGN KEY (album_id) REFERENCES album(id) ON DELETE CASCADE,
                                            CONSTRAINT fk_album_artist_artist
                                                FOREIGN KEY (artist_id) REFERENCES artist(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Track
CREATE TABLE IF NOT EXISTS track (
                                     id           BIGINT PRIMARY KEY AUTO_INCREMENT,

    -- 중복 방지(원본 row가 중복되거나, 동일 트랙이 여러 번 들어오는 상황 방어)
    -- SHA-256 hex 64 chars
                                     track_hash   CHAR(64) NOT NULL,

                                     title        VARCHAR(255) NOT NULL,

    -- 정규화: "03:47" 같은 문자열 대신 ms 저장
                                     duration_ms  INT NULL,
                                     duration_str VARCHAR(16) NULL,  -- 원본 보존(선택)

                                     genre        VARCHAR(64) NULL,
                                     emotion      VARCHAR(32) NULL,

                                     explicit     BOOLEAN NOT NULL DEFAULT FALSE,
                                     popularity   INT NULL,

    -- Track은 Album에 속함
                                     album_id     BIGINT NULL,

                                     created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

                                     UNIQUE KEY uk_track_hash (track_hash),

    -- join/조회 최적화
                                     KEY idx_track_album (album_id),
                                     KEY idx_track_title (title),

    -- filter
                                     KEY idx_track_popularity (popularity),

                                     CONSTRAINT fk_track_album
                                         FOREIGN KEY (album_id) REFERENCES album(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Track <-> Artist (M:N)
CREATE TABLE IF NOT EXISTS track_artist (
                                            track_id  BIGINT NOT NULL,
                                            artist_id BIGINT NOT NULL,

                                            PRIMARY KEY (track_id, artist_id),

                                            KEY idx_track_artist_artist (artist_id, track_id),

                                            CONSTRAINT fk_ta_track
                                                FOREIGN KEY (track_id) REFERENCES track(id) ON DELETE CASCADE,
                                            CONSTRAINT fk_ta_artist
                                                FOREIGN KEY (artist_id) REFERENCES artist(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) Heavy column separation: Lyrics
-- LONGTEXT는 I/O 비용이 커서 분리
CREATE TABLE IF NOT EXISTS track_lyrics (
                                            track_id BIGINT PRIMARY KEY,
                                            lyrics   LONGTEXT NULL,

                                            CONSTRAINT fk_track_lyrics_track
                                                FOREIGN KEY (track_id) REFERENCES track(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) Audio Features (1:1)

CREATE TABLE IF NOT EXISTS audio_feature (
                                             track_id BIGINT PRIMARY KEY,

                                             tempo              DOUBLE NULL,
                                             loudness           DOUBLE NULL,

    -- 원본이 0~100 정수처럼 들어오므로 INT
                                             energy             INT NULL,
                                             danceability       INT NULL,
                                             positiveness       INT NULL,
                                             speechiness        INT NULL,
                                             liveness           INT NULL,
                                             acousticness       INT NULL,
                                             instrumentalness   INT NULL,

                                             musical_key        VARCHAR(16) NULL,
                                             time_signature     VARCHAR(8)  NULL,

                                             CONSTRAINT fk_af_track
                                                 FOREIGN KEY (track_id) REFERENCES track(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4) Likes: Event-based modeling
-- 좋아요 1회 증가 = 1 row (event log)
-- 최근 1시간 Top10: created_at 범위 집계로 해결
CREATE TABLE IF NOT EXISTS track_like_event (
                                                id         BIGINT PRIMARY KEY AUTO_INCREMENT,
                                                track_id   BIGINT NOT NULL,
                                                created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    -- Top10 집계: 시간 범위로 먼저 자르는 쿼리에 사용
                                                KEY idx_like_created_track (created_at, track_id),

    -- 특정 곡 좋아요 조회/삭제/정리 등을 할 때
                                                KEY idx_like_track_created (track_id, created_at),

                                                CONSTRAINT fk_like_track
                                                    FOREIGN KEY (track_id) REFERENCES track(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 누적 카운터 캐시 테이블
CREATE TABLE IF NOT EXISTS track_like_counter (
                                                  track_id   BIGINT PRIMARY KEY,
                                                  like_count BIGINT NOT NULL DEFAULT 0,
                                                  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                                      ON UPDATE CURRENT_TIMESTAMP(6),

                                                  CONSTRAINT fk_like_counter_track
                                                      FOREIGN KEY (track_id) REFERENCES track(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;
