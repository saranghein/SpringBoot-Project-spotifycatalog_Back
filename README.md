# spotify api

## 실행 방법
### 1. docker 실행

### 2. local 실행 

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


## 🗃️ 데이터셋

🔗 [데이터셋 링크](https://www.kaggle.com/datasets/devdope/900k-spotify?select=900k+Definitive+Spotify+Dataset.json)
- 데이터셋 위치 : 📁src/main/resources/dataset

## 🧩 ERD

<img width="1001" height="1010" alt="erd" src="https://github.com/user-attachments/assets/e522adbc-5283-45da-87b6-b26e64487193" />

### 🔍 인덱스 전략

#### 1) 연도 & 가수별 앨범 수 집계 / 페이징 최적화
- **`album.release_year` (generated column) + `idx_album_release_year(release_year)`**
    - `YEAR(release_date)`와 같은 함수 조건 대신 `release_year` 컬럼을 사용하여  
      연도 필터·그룹 시 **인덱스가 직접 활용**되도록 설계
- **`album_artist`**
    - `PRIMARY KEY (album_id, artist_id)`  
      → 앨범–가수 M:N 관계 정합성 보장 및 album 기준 조인 최적화
    - `idx_album_artist_artist(artist_id, album_id)`  
      → “가수별 앨범 집계” 시 artist → album 방향 탐색 최적화
- (옵션) **`idx_album_year_id(release_year, id)`**
    - 연도 필터 이후 `album_id` 조인을 빠르게 하기 위한 보조 인덱스

#### 2) 좋아요 증가 & 최근 1시간 Top10 최적화 (Event Log)
- 좋아요 1회 = 1 row (`track_like_event`)로 모델링하여  
  “최근 1시간 Top10”을 **시간 범위 집계 쿼리**로 단순화
- `idx_like_created_track(created_at, track_id)`
    - 최근 1시간 범위로 먼저 자른 뒤 `track_id` 기준 그룹 집계에 유리
- `idx_like_track_created(track_id, created_at)`
    - 특정 곡의 좋아요 이벤트 조회·정리(최근 N건 등) 최적화
- (선택) `track_like_counter(track_id PK)`
    - 곡 상세 조회 시 누적 like_count가 자주 필요할 경우를 대비한 **카운터 캐시**

#### 3) 기본 Join / 조회 성능을 위한 인덱스
- `track.idx_track_album(album_id)`  
  → track ↔ album 조인 최적화
- `track.idx_track_title(title)`  
  → 트랙 제목 검색/정렬 대비
- `album.idx_album_release_date(release_date)`  
  → 날짜 범위 조회 대비

## 🔄 Flow

```text
NDJSON 파일
    ↓
Files.lines()  (스트리밍)
    ↓
TrackRaw DTO
    ↓
정규화 변환
     ├─ track
     ├─ artist
     └─ track_artist
    ↓
  buffer
    ↓
R2DBC batch insert

```

## 🤝 커밋 규칙

- **[Commit Convention](./.github/COMMIT_CONVENTION.md)**