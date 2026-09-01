# 타노시미 투어 — 개발 문서

한일교류 여행 파티 매칭 서비스. 이 폴더는 개발자용 산출물 문서를 모아둔 곳입니다.

| 문서 | 내용 |
|---|---|
| [01. 프로젝트 기획서](./01-프로젝트-기획서.md) | 서비스 개요, 목표, 대상 사용자, 핵심 기능, 기술 스택, 팀 역할, 일정 |
| [02. 요구사항 정의서](./02-요구사항-정의서.md) | 기능 요구사항(모듈별), 비기능 요구사항, 권한/제약, 외부 연동 |
| [03. 화면 설계서 (UI)](./03-화면설계서.md) | 화면 목록, 라우팅, 화면별 구성요소·동작, 프론트엔드 구조 |
| [04. ERD & 테이블 설계서](./04-ERD-테이블설계서.md) | 엔티티 관계도, 테이블 정의, 주요 컬럼·제약·인덱스 |

## 문서 작성 기준

- 2026-09-01 시점의 `main`(3d63e58) 소스 기준. 진행 중 기능은 각 문서에 표기.
- 스키마 원본: [`src/main/resources/db/schema.sql`](../src/main/resources/db/schema.sql) + `migration_v16_*`, `migration_v17_*`.
- 라우팅 원본: `src/main/java/net/datasa/tanoshimi/controller/`.
- 이 문서와 코드가 다르면 **코드가 정답**입니다. 문서 갱신 PR 환영.

## 빠른 시작

```bash
# 1. MySQL 8 준비 후 스키마 실행
mysql -u root -p < src/main/resources/db/schema.sql
mysql -u root -p tanoshimi < src/main/resources/db/migration_v16_planner_manner_ai.sql
mysql -u root -p tanoshimi < src/main/resources/db/migration_v16_mypage_titles.sql
mysql -u root -p tanoshimi < src/main/resources/db/migration_v17_titles_catalog.sql
mysql -u root -p tanoshimi < src/main/resources/db/data.sql            # 데모 데이터(계정 비번: Test1234!)

# 2. 개인 비밀값 파일 생성 (git 미추적)
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
#   -> DB 계정, (선택) OAuth/외부 API 키 입력

# 3. 실행
./gradlew bootRun     # http://localhost:8080  (프로파일: local)
```
