# 타노시미 (tanoshimi)

> 한일교류 여행 파티 매칭 + 실시간 협업 일정 계획 + AI 여행 추천 서비스
> 日韓交流旅行のパーティーマッチング + リアルタイム共同プランニング + AI旅行おすすめサービス

**"타노시미(楽しみ)" = 기대·즐거움.** 여행을 *함께 갈 사람 찾기*부터 *일정 짜기*, *다녀와서 기록*까지 한 서비스에서 잇습니다.

---

## 무엇을 해결하나

- 한일 여행에서 **조건(성별·연령·국적·여행 스타일)에 맞는 동행**을 찾을 채널이 없다 → 조건 기반 파티 매칭
- 여러 명이 일정을 짤 때 **카톡 + 엑셀 + 지도**를 오가며 최신본 관리가 안 된다 → 편집권(lock) 기반 실시간 공동 편집 + 스냅샷 롤백
- 활동을 고를 때 **날씨·실내외·동선**을 일일이 따져야 한다 → AI가 보조하는 활동 추천·동선 정렬·현실성 검토

## 핵심 기능

| 모듈 | 내용 |
|---|---|
| **인증·회원** | 이메일 인증 가입, 로그인, 로그인 실패 잠금, 소셜 로그인(Google·Naver) |
| **번개모임(파티)** | 파티 개설·모집·신청·승인·강퇴·완료, 성별/연령/국적 조건 제한 |
| **협업 플래너** | 파티당 계획표 1개, 분 단위 드래그 일정, 편집권 lock, 자동/수동 저장, 스냅샷 롤백, 찬반 투표 |
| **AI 추천·챗봇** | 동행 챗봇, 액티비티 실내외(venue_type) AI 판정+캐싱, 날씨 기반 재추천, 채팅 번역, AI 크레딧 일일 한도 |
| **AI 동선 최적화** | 고정(항공·숙박)/이동 가능(액티비티) 구분, 좌표 기반 최근접 이웃 정렬 |
| **커뮤니티** | 여행 게시판(글·댓글·좋아요), 추천글, 팔로우, 파티 채팅(WebSocket), 1:1 DM, 유저 차단 |
| **마이페이지** | 프로필·자기소개, 참여 파티·완료 여행, 지역 정복 히트맵, 칭호(38종/8카테고리), 매너온도, 프로필 배경 꾸미기 |
| **관리자** | 회원 정지/복구, 파티 강제 종료, 신고 처리, 배너 관리, 관리자 권한 관리 |
| **알림·고객지원** | 인앱 알림(팔로우·댓글·파티 신청/승인/거절/강퇴), 1:1 문의 게시판 |

## 기술 스택

| 계층 | 기술 |
|---|---|
| 언어/런타임 | Java 21 |
| 프레임워크 | Spring Boot 3.5.15 (Web MVC · Data JPA · Security · OAuth2 Client · WebSocket · WebFlux WebClient) |
| 빌드 | Gradle (Groovy DSL) |
| 뷰 | Thymeleaf + `thymeleaf-extras-springsecurity6` (서버 사이드 렌더링) |
| 프론트 | Vanilla JS (`static/js/*.js`), 단일 `app.css`, SockJS + STOMP — 노드 빌드 없음 |
| DB | MySQL 8 (`utf8mb4`), `schema.sql` 단일 원본 + 번호 마이그레이션, Hibernate `ddl-auto: update` 보조 |
| 실시간 | STOMP over WebSocket — 엔드포인트 `/ws`, 브로커 `/topic`, 앱 prefix `/app` |
| 파일/이미지 | Apache Tika(타입 검증), metadata-extractor(EXIF GPS), webp-imageio, AWS S3 / Rekognition SDK |
| 외부 연동 | SMS(알리고) · 날씨(KMA/OpenWeather) · 번역(Papago/DeepL) · AI(Anthropic/Gemini) — **전부 `mock` 기본**, 키는 `application-local.yml` |
| 테스트 | JUnit 5, `spring-security-test`, H2(test runtime) |

## 빠른 시작

> 프로젝트 소스는 저장소의 [`tanoshimi/`](tanoshimi/) 디렉터리에 있습니다. 아래 명령은 그 안에서 실행하세요.

### 1. DB 준비 (MySQL 8)

```bash
cd tanoshimi
mysql -u root -p < src/main/resources/db/schema.sql
mysql -u root -p tanoshimi < src/main/resources/db/data.sql   # 데모 데이터 (모든 계정 비번: Test1234!)
# 이어서 db/ 폴더의 migration_v16_* ~ migration_v20_* 를 파일명 순서대로 실행
```

> **정확한 마이그레이션 순서와 데모 데이터(히트맵·칭호) 세팅은 [`tanoshimi/src/main/resources/db/README.md`](tanoshimi/src/main/resources/db/README.md)를 따르세요.** 앱은 `ddl-auto: update`로 뜨므로 `schema.sql` 실행 후 서버를 한 번 켰다 끄면 누락 컬럼이 자동 보정됩니다.

### 2. 개인 비밀값 파일 생성 (git 미추적)

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# -> DB 계정, (선택) OAuth / 외부 API 키 입력
```

- 외부 연동은 모두 `mock`이 기본이라 **키 없이도 개발·시연 가능**합니다.
- 이메일 인증은 로컬 기본값이 `provider: log` — 인증번호가 **앱 콘솔 로그**에 찍힙니다.
- 소셜 로그인은 `application.yml`에 Google·Naver·LINE 3개가 등록돼 있어, `application-local.yml`에 셋 다 `client-id`/`client-secret`이 채워져야 앱이 부팅됩니다(미사용 provider는 더미값 허용).

### 3. 실행

```bash
./gradlew bootRun     # http://localhost:8080  (프로파일: local)
```

### 테스트

```bash
./gradlew test
```

## 프로젝트 구조

```
tanoshimi/                      # 저장소 루트
├── README.md                   # (이 파일)
├── docs/                       # 개발 산출물 문서
└── tanoshimi/                  # Spring Boot 애플리케이션
    ├── build.gradle
    └── src/
        ├── main/
        │   ├── java/net/datasa/tanoshimi/
        │   │   ├── controller/         # View + @RestController (27개)
        │   │   ├── service/            # 도메인 서비스
        │   │   ├── repository/         # Spring Data JPA
        │   │   ├── domain/{entity,dto} # 엔티티 / DTO
        │   │   ├── scheduler/          # FileCleanup(04:00), PartyCompletion(00:30)
        │   │   ├── config/ · auth/ · exception/
        │   └── resources/
        │       ├── templates/          # Thymeleaf
        │       ├── static/{css,js,vendor}
        │       ├── db/                  # schema.sql + migration_v16~ + data.sql
        │       ├── application.yml
        │       └── messages_ko/ja.properties
        └── test/
```

## 문서

| 문서 | 내용 |
|---|---|
| [01. 프로젝트 기획서](docs/01-프로젝트-기획서.md) | 서비스 개요, 목표, 대상 사용자, 핵심 기능, 기술 스택, 팀 역할, 일정 |
| [02. 요구사항 정의서](docs/02-요구사항-정의서.md) | 모듈별 기능 요구사항(FR), 비기능 요구사항(NFR), 에러 코드 |
| [03. 화면 설계서 (UI)](docs/03-화면설계서.md) | 화면 목록, 라우팅, 화면별 구성요소·동작, 프론트엔드 구조 |
| [04. ERD & 테이블 설계서](docs/04-ERD-테이블설계서.md) | 엔티티 관계도, 테이블 정의, 주요 컬럼·제약·인덱스 |
| [db/ 폴더 안내](tanoshimi/src/main/resources/db/README.md) | 로컬 DB 세팅 순서, 마이그레이션·시드 파일 설명 |

> 문서와 코드가 다르면 **코드가 정답**입니다.

## 팀

| 역할 | 담당 |
|---|---|
| 팀장 / 통합·QA | 아키텍처, 브랜치 전략, 통합·QA, AI 동선 최적화, 매너온도, 신고/관리자 일부 |
| 인증 | 회원가입·로그인·소셜·SMS/이메일 인증, 공통 예외처리 |
| 플래너 | 협업 계획표(편집권·자동저장·스냅샷·투표), AI 리포트, AI 컴패니언 캐릭터 |
| AI 추천·챗봇 | venue_type 판정, 날씨 재추천, 활동 이력 추천, AI 크레딧 |
| 커뮤니티 | 게시판·댓글·팔로우·채팅·DM·유저 차단 |
| 마이페이지 | 프로필·히트맵·칭호·계정 관리·지역 매핑, 내 여행(my_trips) |

## 개발 규칙

- **브랜치**: 기능별 `feature/*` 브랜치에서 작업 → PR로 `main` 병합 (로컬에서 `main` 직접 push 금지).
- **스키마**: `schema.sql` 단일 원본 + 번호 붙은 마이그레이션 파일. 머지 전 컴파일 확인.
- **비밀값**: DB 계정·OAuth secret·외부 API 키는 `application-local.yml`(git 미추적) 또는 환경변수로만 주입.
- **업로드 파일**: `${user.home}/tanoshimi-uploads` (프로젝트 폴더 밖).
