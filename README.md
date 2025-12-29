## 📌 과제 수행 개요

본 프로젝트는 대용량 Spotify 데이터를 기반으로  
Reactive 환경에서 효율적인 데이터 적재 및 조회 API를 구현하는 것을 목표로 한 과제입니다.

약 90만 건 규모의 NDJSON 데이터를 메모리 사용을 최소화하여 처리하고,  
연도·아티스트별 앨범 집계 및 노래별 좋아요 기능을  
확장 가능하고 성능 친화적인 구조로 설계하였습니다.

---

## 실행 방법 

### 1. 환경
- Java 21
- MySQL 8.x
- Gradle

### 2. 데이터셋 다운로드

- 🔗 [데이터셋 링크](https://www.kaggle.com/datasets/devdope/900k-spotify?select=900k+Definitive+Spotify+Dataset.json)
- 데이터셋 위치 : 📁src/main/resources/dataset
- 데이터셋을 `900k Definitive Spotify Dataset.json` 파일명으로 해당 위치에 넣습니다.

### 3-1. 실행 (Localhost)

- DB 설정
  - host: localhost
  - port: 3306
  - database: spotifycatalog
  - user: root / root1234

```bash
# 초기 1회 적재 실행
./gradlew bootRun --args='--spring.profiles.active=local,ingest'

# 평소 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```
- DB 접속 URL : jdbc:mysql://localhost:3306


### 3-2. 실행 (Docker)

```bash
./gradlew clean bootJar

# 초기 1회 적재 실행
docker compose -f docker/docker-compose.ingest.yml up --build

# 평소 실행
docker compose -f docker/docker-compose.yml up --build
```
- DB 접속 URL : jdbc:mysql://localhost:3307

### 4. 데이터 적재 완료

- 아래와 같은 로그가 뜨면 파일 데이터가 DB 에 저장 완료 되고
- API 테스트가 가능한 상태입니다.

<img width="723" height="143" alt="image" src="https://github.com/user-attachments/assets/94814429-612e-4d30-9617-569308a48808" />

---
## ⚙️ 기술 스택

<table>
  <tr>
    <th>Category</th>
    <th>Stack</th>
  </tr>

  <tr>
    <td><strong>Language</strong></td>
    <td>
      <img src="https://img.shields.io/badge/Java%2021-6DB33F?style=for-the-badge&logo=openjdk&logoColor=white">
    </td>
  </tr>

  <tr>
    <td><strong>Framework</strong></td>
    <td>
      <img src="https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white">
      <img src="https://img.shields.io/badge/WebFlux-6DB33F?style=for-the-badge&logo=spring&logoColor=white">
    </td>
  </tr>

  <tr>
    <td><strong>Reactive Stack</strong></td>
    <td>
      <img src="https://img.shields.io/badge/R2DBC-2A3F54?style=for-the-badge&logo=r2dbc&logoColor=white">
    </td>
  </tr>

  <tr>
    <td><strong>Blocking Stack</strong></td>
    <td>
      <img src="https://img.shields.io/badge/JDBC-59666C?style=for-the-badge&logo=databricks&logoColor=white">
    </td>
  </tr>

  <tr>
    <td><strong>Database</strong></td>
    <td>
      <img src="https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white">
    </td>
  </tr>

  <tr>
    <td><strong>Migration</strong></td>
    <td>
      <img src="https://img.shields.io/badge/Flyway-CC0200?style=for-the-badge&logo=flyway&logoColor=white">
    </td>
  </tr>
</table>

### 🛠 기술 선택 배경

- **WebFlux + R2DBC**
    - 대용량 데이터 적재 및 다수의 집계 API 요청을 고려하여
      논블로킹 기반의 Reactive 스택을 적용
    - DB 접근 시 Thread blocking을 최소화하여 자원 효율성 확보

- **MySQL + Flyway**
    - 명확한 스키마 관리 및 인덱스 전략 검증을 위해 RDBMS 선택
    - 마이그레이션 이력을 코드로 관리하여 재현성 확보

## 과제 수행 내용

### 🧩 ERD

<img width="813" height="977" alt="erd" src="https://github.com/user-attachments/assets/6502526b-c607-4578-91cb-5e093429f9bb" />

### 🔍 인덱스 전략

본 프로젝트는 **집계/페이징 API**와 **최근 시간 구간 Top N 집계**가 핵심이므로,  
실제 조회 패턴에 맞춰 인덱스를 설계하였습니다.

---

#### 1) 연도 & 아티스트별 앨범 수 집계 / 페이징 최적화

#### 📌 `album.release_year` 생성 컬럼 + 연도 인덱스
- `album.release_year`는 `release_date`에서 연도만 추출한 **STORED generated column**입니다.
- `YEAR(release_date)`와 같은 함수 조건 대신 컬럼 기반 필터링을 사용하여  
  **연도 조건 및 그룹 집계 시 인덱스가 직접 활용**되도록 설계했습니다.
- 사용 인덱스
  - `idx_album_release_year (release_year)`
  - `idx_album_year_id (release_year, id)`  
    → 연도 필터 이후 `album_id` 조인 및 탐색을 빠르게 하기 위한 보조 인덱스

#### 📌 M:N 조인 테이블(`album_artist`) 탐색 방향 인덱스
- `album_artist`는 `(album_id, artist_id)`를 PK로 두어 **정합성 보장 및 album 기준 조인**을 최적화했습니다.
- “가수별 앨범 집계”는 artist → album 방향 탐색이 주가 되므로 보조 인덱스를 추가했습니다.
  - `PRIMARY KEY (album_id, artist_id)`
  - `idx_album_artist_artist (artist_id, album_id)`

#### 📌 집계 결과 조회용 물리 테이블 + 정렬 인덱스
- 연도/아티스트별 앨범 수는 자주 조회되고 정렬·페이징이 필요하므로  
  `artist_album_count_year` 테이블로 **사전 집계(denormalization)** 합니다.
- 조회 패턴은 `release_year` 필터 후 `album_count DESC` 정렬이 핵심이므로 다음 인덱스를 사용합니다.
  - `PRIMARY KEY (release_year, artist_id)`
  - `idx_year_count_artist (release_year, album_count DESC, artist_id ASC)`
    - 연도 조건을 먼저 좁히고
    - 앨범 수 기준 정렬
    - `artist_id`를 tie-breaker로 사용하여 커서 기반 페이징 안정성 확보

---

#### 2) 좋아요 증가 & 최근 N분 Top N 집계 최적화 (Event Log)

좋아요 기능은 단순 카운트 증가가 아닌 **Event Log 기반 모델링**으로 설계했습니다.

#### 📌 최근 시간 범위 Top N 집계 인덱스
- 최근 `windowMinutes` 동안의 이벤트를 먼저 필터링한 뒤 `track_id`로 그룹 집계합니다.
- 시간 범위 조건이 선행될 수 있도록 다음 복합 인덱스를 사용합니다.
  - `idx_like_created_track (created_at, track_id)`

#### 📌 특정 트랙 이벤트 조회/정리 인덱스
- 특정 트랙의 좋아요 이벤트 조회 및 정리(최근 N건 등)를 위해 반대 방향 인덱스를 추가했습니다.
  - `idx_like_track_created (track_id, created_at)`

#### 📌 누적 카운터 캐시 테이블
- 누적 좋아요 수를 빠르게 반환해야 하는 경우를 대비하여  
  `track_like_counter`를 **카운터 캐시 테이블**로 사용합니다.
  - `PRIMARY KEY (track_id)`
- 이벤트 기반 집계와 카운터 캐시를 조합해  
  **집계성 조회**와 **즉시 응답이 필요한 누적값 조회**를 모두 대응합니다.

---

#### 3) 기본 Join / 조회 성능을 위한 인덱스

#### 📌 Track ↔ Album 조인 최적화
- 트랙에서 앨범으로의 조인이 자주 발생하므로 다음 인덱스를 사용합니다.
  - `idx_track_album (album_id)`

#### 📌 검색 / 정렬 대비
- 트랙 제목 기반 조회 및 정렬을 위해
  - `idx_track_title (title)`

#### 📌 데이터 중복 방지
- 동일 트랙이 여러 번 유입되는 상황을 방지하기 위해 해시 기반 유니크 키를 사용합니다.
  - `uk_track_hash (track_hash)`

#### 📌 아티스트 정규화 및 중복 방지
- 정규화된 아티스트 키(`name_key`) 기준으로 중복을 방지하고 조회를 빠르게 합니다.
  - `uk_artist_name_key (name_key)`
  - `idx_artist_name_key (name_key)`

---

### 🔄 Flow

```text
NDJSON (900k)
  ↓
Line-by-line read (Flux)  +  blocking I/O → boundedElastic
  ↓
Parse → TrackRaw DTO
  ↓
Normalize / Key 생성
  ├─ artist: name_key
  ├─ album : album_key (+ release_date → release_year generated)
  ├─ track : track_hash
  ↓
Buffer (CHUNK)
  ↓
Batch upsert/insert (R2DBC)
  1) artist (uk: name_key)
  2) album  (uk: album_key)
  3) track  (uk: track_hash, FK: album_id)
  4) album_artist (PK: album_id, artist_id)
  5) track_artist (PK: track_id, artist_id)
  6) audio_feature (1:1, track_id)
  7) track_lyrics  (1:1, track_id)
  ↓
(후처리) stats rebuild
  └─ artist_album_count_year 집계 갱신 (연도·아티스트별 앨범 수 조회 최적화)


```

- 위 흐름을 통해 파일 전체를 메모리에 적재하지 않고, <br>스트리밍 + 배치 조합으로 대용량 데이터를 안정적으로 처리하도록 설계하였습니다.

---

### 📥 대용량 데이터 처리 전략

과제 요구사항인 “메모리 사용량 최소화”를 충족하기 위해  <br>
데이터 파일을 한 번에 로드하지 않고 스트리밍 방식으로 처리하였습니다.

- NDJSON 파일을 line 단위로 읽어 Flux로 변환
- Blocking I/O는 boundedElastic Scheduler에서 처리
- DB INSERT는 CHUNK 단위 batch 처리로 메모리 사용량 제어

---

### ❤️ 좋아요(Like) 모델링

본 프로젝트의 좋아요 기능은 단순 카운트 증가가 아닌  
**Event Log + Counter Cache** 조합으로 설계했습니다.

- **최근 구간 집계**: Event Log
  - 좋아요 1회 = 1개의 이벤트 row 가 저장
- **누적 값 조회**: Counter Cache
  - 누적 좋아요 수를 빠르게 제공

---

### 🚀 API 설계 및 구현

#### 1️⃣ 연도 & 아티스트별 발매 앨범 수 조회 API

- **설계 특징**
  - OFFSET 기반 페이징 대신 Keyset Pagination(커서 기반) 적용
  - 대용량 데이터 환경에서 페이지가 뒤로 갈수록 성능이 저하되는 문제를 방지
  - 정렬 기준: albumCount DESC, artistId DESC
  - 다음 페이지 조회를 위해 마지막 row 기준으로 cursor 생성
- **커서(cursor) 설명**
  - 커서는 마지막 row의 (albumCount, artistId) 값을 기반으로 인코딩
  - 다음 요청 시 cursor 파라미터로 전달하여 연속적인 페이지 조회 가능
  - 다음 페이지는 (albumCount, artistId)가 마지막 항목보다 **작은 값**만 조회
  
 
<br>

- **Endpoint**
```text
GET /api/album/stats/artist?year={year}&cursor={cursor}&size={size}
```
- **Endpoint Example**
```text
http://localhost:8080/api/album/stats/artist?year=2019&size=20
```


- **Query Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| year | Integer | 선택 | 조회 연도 (미지정 시 전체) |
| cursor | String | 선택 | 커서 기반 페이징을 위한 커서 |
| size | int | 선택 | 페이지 크기 (기본 20, 최대 200) |

<br>

- **Response Example**
```json
{
  "year": 2019,
  "totalAlbums": 13350,
  "page": {
    "items": [
      {
        "artistId": 625,
        "artistName": "Lil Baby",
        "albumCount": 35
      },
      {
        "artistId": 1064,
        "artistName": "Tory Lanez",
        "albumCount": 32
      }
    ],
    "hasNext": true,
    "nextCursor": "eyJhbGJ1bUNvdW50IjoyMCwiYXJ0aXN0SWQiOjUxMzZ9"
  }
}
```

- **Response Field**

| 필드명                     | 타입      | 설명                    |
| ----------------------- | ------- | --------------------- |
| year                    | Integer | 조회 기준 연도 (미지정 시 null) |
| totalAlbums             | Long    | 해당 연도 기준 전체 앨범 수      |
| page.items              | Array   | 아티스트별 앨범 집계 목록        |
| page.items[].artistId   | Long    | 아티스트 ID               |
| page.items[].artistName | String  | 아티스트 이름               |
| page.items[].albumCount | Integer | 해당 아티스트의 발매 앨범 수      |
| page.hasNext            | Boolean | 다음 페이지 존재 여부          |
| page.nextCursor         | String  | 다음 페이지 조회 시 사용할 커서    |

<br>

---
#### 2️⃣ 특정 아티스트 앨범 목록 조회 API
- **설계 특징**
  - 아티스트 단위 앨범 목록을 Keyset Pagination(커서 기반) 으로 조회
  - year 미지정 시 정렬 기준: releaseYear DESC (null last), albumId DESC
  - year 지정 시 정렬 기준: albumId DESC

<br>

- **Endpoint**
```text
GET /api/album/stats/artist/{artistId}?year={year}&cursor={cursor}&size={size}
```

- **Path Variable**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| artistId | Long | 필수 | 아티스트 ID |

- **Query Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| year | Integer | 선택 | 조회 연도 (미지정 시 전체) |
| cursor | String | 선택 | 커서 기반 페이징을 위한 커서 |
| size | int | 선택 | 페이지 크기 (기본 20, 최대 200) |

<br>

- **Response Example**
```json
{
  "artistId": 625,
  "year": 2019,
  "totalAlbums": 35,
  "page": {
    "items": [
      {
        "albumId": 12345,
        "albumName": "My Turn",
        "releaseYear": 2019
      }
    ],
    "hasNext": true,
    "nextCursor": "..."
  }
}
```

- **Response Field**

| 필드명                      | 타입      | 설명                                |
| ------------------------ | ------- | --------------------------------- |
| artistId                 | Long    | 아티스트 ID                           |
| year                     | Integer | 조회 기준 연도 (미지정 시 null)             |
| totalAlbums              | Long    | 해당 조건(artistId + year) 기준 전체 앨범 수 |
| page.items               | Array   | 앨범 목록                             |
| page.items[].albumId     | Long    | 앨범 ID                             |
| page.items[].albumName   | String  | 앨범 이름                             |
| page.items[].releaseYear | Integer | 발매 연도 (없으면 null)                  |
| page.hasNext             | Boolean | 다음 페이지 존재 여부                      |
| page.nextCursor          | String  | 다음 페이지 조회 시 사용할 커서                |

<br>

---
#### 3️⃣ 노래별 좋아요 증가 API
- **설계 설명**
  - 특정 트랙의 좋아요를 1회 증가
  - 좋아요 증가 요청 시 이벤트 로그(track_like_event)에 1 row 기록
  - 증가 이후의 누적 좋아요 수를 응답으로 반환
- **입력 검증** : trackId는 양수만 허용 (@Positive)

<br>

- **Endpoint**
```text
POST /api/track/{trackId}/likes
```
- **Path Variable**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| trackId | long | 필수 | 트랙 ID (양수) |

<br>

- **Response Example**
```json
{
  "trackId": 12345,
  "likeCount": 1024
}
```
- **Response Field**

| 필드명       | 타입   | 설명              |
| --------- | ---- | --------------- |
| trackId   | Long | 좋아요가 증가된 트랙 ID  |
| likeCount | Long | 증가 이후의 누적 좋아요 수 |

<br>

---

#### 4️⃣ 최근 1시간 기준 좋아요 Top 10 조회 API
- **설계 설명**
  - 최근 windowMinutes 동안 발생한 좋아요 이벤트를 집계하여 증가량(incCount) 기준 Top N 트랙을 조회
  - 시간 범위 조건(created_at >= now - windowMinutes)으로 먼저 자른 뒤 track_id로 그룹 집계
  - 조회 결과에는 트랙 식별자뿐 아니라 title, artistNames를 함께 제공하여 클라이언트가 추가 조회 없이 바로 표시 가능
- **입력 검증** : windowMinutes: 1 ~ 1440, limit: 1 ~ 200

<br>

- **Endpoint**

```text
GET /api/track/likes/top?windowMinutes={windowMinutes}&limit={limit}
```

- **Query Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| windowMinutes | int | 선택 | 집계 시간 범위(분, 기본 60, 최대 1440) |
| limit | int | 선택 | 조회 개수(기본 10, 최대 200) |

<br>

- **Response Example**

```json
[
  {
    "trackId": 12345,
    "incCount": 87,
    "title": "Blinding Lights",
    "artistNames": "The Weeknd"
  },
  {
    "trackId": 67890,
    "incCount": 75,
    "title": "SICKO MODE",
    "artistNames": "Travis Scott, Drake"
  }
]
```

- **Response Field**

| 필드명         | 타입     | 설명                                  |
| ----------- | ------ | ----------------------------------- |
| trackId     | Long   | 트랙 ID                               |
| incCount    | Long   | 해당 시간 구간(windowMinutes) 동안의 좋아요 증가량 |
| title       | String | 트랙 제목                               |
| artistNames | String | 아티스트명 (여러 명이면 구분자 포함 문자열)           |

<br>

---

#### ❗ 공통 에러 응답 (GlobalExceptionHandler)

모든 에러 응답은 아래 공통 포맷으로 반환됩니다.

- **ErrorResponse Schema**

| 필드명 | 타입 | 설명 |
|---|---|---|
| timestamp | Instant | 에러 발생 시각 (서버 생성) |
| status | int | HTTP 상태 코드 |
| error | String | HTTP 상태 문자열 (예: `Bad Request`, `Not Found`) |
| message | String | 에러 상세 메시지 |
| path | String | 요청 경로 |
| code | String | 애플리케이션 에러 코드 |

<br>

- **에러 코드 매핑**

| HTTP Status | code | 발생 케이스 | message 예시 |
|---:|---|---|---|
| 400 | VALIDATION_ERROR | 파라미터/바인딩/검증 실패 (`@Min/@Max/@Positive`, DTO bind 등) | `year: must be less than or equal to 2100` |
| 400 | (커스텀) | `BadRequestException` 발생 | (예: 비즈니스 규칙 위반 메시지) |
| 404 | (커스텀) | `NotFoundException` 발생 | `artist not found` |
| 500 | DB_ERROR | DB 접근/SQL 오류 (`DataAccessException`, `BadSqlGrammarException`) | `Database error` |
| 500 | INTERNAL_ERROR | 처리되지 않은 기타 예외 | `Unexpected error` |

---

### 🤝 커밋 규칙

- **[Commit Convention](./.github/COMMIT_CONVENTION.md)**