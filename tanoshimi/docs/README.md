# 타노시미 투어 — 개발 문서

한일교류 여행 파티 매칭 서비스. 이 폴더는 개발자용 산출물 문서를 모아둔 곳입니다.

| 문서 | 내용 |
|---|---|
| [01. 프로젝트 기획서](./01-프로젝트-기획서.md) | 서비스 개요, 목표, 대상 사용자, 핵심 기능, 기술 스택, 팀 역할, 일정 |
| [02. 요구사항 정의서](./02-요구사항-정의서.md) | 기능 요구사항(모듈별), 비기능 요구사항, 권한/제약, 외부 연동 |
| [03. 화면 설계서 (UI)](./03-화면설계서.md) | 화면 목록, 라우팅, 화면별 구성요소·동작, 프론트엔드 구조 |
| [04. ERD & 테이블 설계서](./04-ERD-테이블설계서.md) | 엔티티 관계도, 테이블 정의, 주요 컬럼·제약·인덱스 |
| [05. 시연 가이드](./05-시연-가이드.md) | 데모 계정·비밀번호, 시연 PC 준비, 동시 접속(채팅) 방법, 시연 직전 체크리스트 |

## 문서 작성 기준

- 2026-10-05 시점의 `main`(e5b2eae, PR #81까지) 소스 기준. 진행 중 기능은 각 문서에 표기.
- 스키마 원본: [`src/main/resources/db/schema.sql`](../src/main/resources/db/schema.sql) + `migration_v16_*` ~ `migration_v24_*`.
- DB 세팅 순서 원본: [`src/main/resources/db/README.md`](../src/main/resources/db/README.md).
- `*.docx` / `*.xlsx` 는 2026-09-09 제출본이라 md 문서보다 오래됐다. 최신 내용은 md 기준.
- 라우팅 원본: `src/main/java/net/datasa/tanoshimi/controller/`.
- 이 문서와 코드가 다르면 **코드가 정답**입니다. 문서 갱신 PR 환영.

## 빠른 시작

```bash
# 1. MySQL 8 준비 후 스키마 + 데모 데이터 + 마이그레이션 (순서는 db/README.md 기준)
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/schema.sql
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/data.sql   # 데모 데이터(계정 비번: Test1234!)
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/migration_v16_mypage_titles.sql
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/migration_v16_planner_manner_ai.sql
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/migration_v17_titles_catalog.sql
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/migration_v18_email_verification.sql
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/migration_v18_titles_distance_tiers.sql
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/migration_v19_my_trips.sql
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/migration_v19_temporary_password.sql
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/migration_v20_usernames.sql
#   v21(결제·예약 제거)·v22(자동 로그인)·v23(칭호 장착)·v24(추천 좋아요)는 갱신된 schema.sql 에
#   이미 들어 있어 새 DB 는 불필요. 예전 DB 에만 한 번 실행.
#   시연용 데모 데이터(유자차 마이페이지·마감 임박 파티·관광지 사진)는 05-시연-가이드.md 2-1 참고.

# 2. 개인 비밀값 파일 생성 (git 미추적)
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
#   -> DB 계정, (선택) OAuth/외부 API 키 입력

# 3. 실행
./gradlew bootRun     # http://localhost:8080  (프로파일: local)
```

## 로컬 개발 참고

### 이메일 인증 (회원가입 본인인증 · 비밀번호 재발급)

회원가입 본인인증은 v18부터 **휴대폰 SMS가 아니라 이메일 인증번호**다(알리고 SMS는 사업자등록번호
없이 실사용이 어려워 전환. `phone_verifications`/`PhoneVerificationService`는 남아 있지만 미사용).
비밀번호 찾기(`/find-password`)는 이메일로 임시 비밀번호를 보내고, 그걸로 로그인하면 비밀번호
변경 모달이 강제로 뜬다(`users.must_change_password`, v19).

로컬 기본값은 **실제 메일을 보내지 않습니다.** `app.email.provider` 기본값이 `log`라
`LogEmailSender`가 동작하고, 인증번호는 **앱 콘솔 로그**에 찍힙니다.

```
[개발용 이메일] 수신주소=you@example.com 인증번호=123456
```

회원가입/비밀번호 재발급 테스트는 이 로그의 번호를 입력하면 됩니다.

실제 메일 발송을 쓰려면 `application-local.yml`에 아래를 추가:

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: 발신용_메일주소
    password: 앱_비밀번호        # 계정 비밀번호 아님. Gmail은 2단계 인증 후 앱 비밀번호 발급
app:
  email:
    provider: smtp               # log -> smtp
    smtp:
      from: 발신용_메일주소
```

`provider: smtp`인데 `spring.mail.host`가 비어 있으면 발송이 실패합니다(`EMAIL_SEND_FAILED`).

### 소셜 로그인 (Google / Naver / LINE)

세 provider 모두 실제로 동작한다(LINE은 OIDC로 재도입, 서명 검증 버그 수정 완료).
`application.yml`에 세 provider가 모두 등록돼 있어, `application-local.yml`의
`spring.security.oauth2.client.registration` 아래에 **세 개 모두 `client-id`/`client-secret`이
채워져 있어야 앱이 부팅됩니다.** 값이 비면 기동 시
`Failed to bind properties under 'spring.security.oauth2.client.registration.<provider>'`로 실패.

당장 실행만 필요하면 미사용 provider는 더미값이라도 넣으면 됩니다(해당 버튼만 눌렀을 때 에러).
등록된 리다이렉트 주소가 `localhost` 기준이라, 다른 PC에서 IP로 접속하면 소셜 로그인은 실패할 수 있습니다.

```yaml
          line:
            client-id: dummy-line-client-id
            client-secret: dummy-line-client-secret
```
