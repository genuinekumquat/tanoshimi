# db/ 폴더 안내

로컬 개발 DB를 처음부터 세팅하는 순서와 각 파일이 하는 일을 정리한 문서. (2026-09-01, ⑥ 김민규)

## 처음 로컬 DB 세팅하는 순서

`tanoshimi/tanoshimi` 디렉터리 기준으로, 아래 순서대로 실행한다.

```
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/schema.sql
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/data.sql
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/migration_v16_mypage_titles.sql
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/migration_v16_planner_manner_ai.sql
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/migration_v16_remove_payment_tables.sql
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/migration_v17_titles_catalog.sql
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/migration_v18_titles_distance_tiers.sql
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/migration_v19_my_trips.sql
```

여기까지가 앱을 실행하는 데 필요한 필수 순서다. 매너온도/DB 계정 등 세부 사항은 저장소 루트의
다른 문서 참고 (`application-local.yml.example`, DB 계정은 `scit`).

## (선택) 마이페이지 데모 데이터 채우기

마이페이지 "내 여행"/히트맵/칭호 기능을 눈으로 확인하고 싶으면, 위 필수 순서 다음에
`demo_mypage_seed.sql`을 실행한다:

```
mysql -u scit -p --default-character-set=utf8mb4 < src/main/resources/db/demo_mypage_seed.sql
```

**주의: 이 스크립트는 두 번 실행해야 완전한 효과가 난다** — 1차 실행 후 서버를 켜고
`yuja@test.com`(비번 `Test1234!`)으로 로그인해서 `/mypage`를 한 번 열어봐야(파티→"내 여행"
자동 동기화, 앱 코드가 하는 일이라 SQL만으로는 대신할 수 없다), 그다음 2차 실행에서 파티
여행 29건 전부에 스냅이 채워진다. 자세한 이유와 단계는 파일 상단 주석 참고. 몇 번을 더
실행해도 안전하다(멱등).

## 파일 구성

- **`schema.sql`** — 전체 DDL. 단, 앱이 `spring.jpa.hibernate.ddl-auto: update`로 뜨기 때문에
  서버를 한 번이라도 켜면 엔티티 기준으로 누락된 컬럼이 자동으로 채워진다(예:
  `posts.blinded`) — 이 파일 자체가 지금 실제 DB 스키마의 100% 스냅샷은 아닐 수 있다는 점
  참고. schema.sql만 실행하고 앱을 한 번도 안 켰다면, `data.sql`이나 `demo_mypage_seed.sql`
  실행 전에 서버를 한 번 켰다 꺼서 컬럼을 맞춰두는 게 안전하다.
- **`data.sql`** — 기본 시드(관리자 계정 2개, 데모 유저 7명, 칭호 40종, 기본 파티 4건 등).
- **`migration_v16_mypage_titles.sql`, `migration_v16_planner_manner_ai.sql`,
  `migration_v16_remove_payment_tables.sql`, `migration_v17_titles_catalog.sql`,
  `migration_v18_titles_distance_tiers.sql`, `migration_v19_my_trips.sql`** — 실제 스키마
  변경 이력. **순서대로 실행해야 하고, 합치거나 지우지 않는다** — 지금 스키마가 어떤 과정을
  거쳐 이 모양이 됐는지 보여주는 기록이라 마이그레이션 파일은 정리 대상이 아니다.
- **`demo_mypage_seed.sql`** — 마이페이지(내 여행/히트맵/칭호) 확인용 데모 데이터.
  `yuja@test.com` 계정에 완료 파티 29건 + 지역 태그 스냅 21건 + SOLO 여행 2건(오사카/부산) +
  스냅 3건을 채우고, 파티 29건 전부에 스냅을 연동해서 히트맵/칭호/"내 여행" 목록이 실제로
  채워진 상태를 확인할 수 있게 해준다. **팀원이 새로 로컬 DB를 세팅했을 때도 지금과 같은
  데모 상태를 그대로 받고 싶다면, 위 필수 순서 다음에 이 파일을 두 번 실행하면 된다.**

## 팀원이 지금과 똑같은 데모 상태를 받으려면

위 "처음 로컬 DB 세팅하는 순서" + "(선택) 마이페이지 데모 데이터 채우기"를 그대로 따라가면
된다. 요약하면: `schema.sql` → `data.sql` → `migration_v16~v19` → `demo_mypage_seed.sql`
1차 → 브라우저로 `/mypage` 한 번 열기 → `demo_mypage_seed.sql` 2차. 이게 끝이면 지금
확인 중인 것과 동일한 유자차 데모 상태(히트맵 색칠, 칭호, "직접 등록"/"파티 자동 등록"
배지 포함)를 그대로 받는다.
