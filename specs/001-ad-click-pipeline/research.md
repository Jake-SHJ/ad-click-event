# Research: 광고 클릭 수집·집계·준실시간 서빙 파이프라인

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-11-15

사용자 지시: 대부분 추천안대로 하되, 메시지 큐는 Kafka, 저장소는 PostgreSQL, 테스트는 Kotlin에 맞게.
라이선스는 오픈소스를 우선한다. 정확한 패치 버전은 구현 시작 시점의 최신 안정판으로 고정하고
`gradle/libs.versions.toml`과 `docker-compose.yml`에 기록한다.

## 1. 전체 처리 구조: 실시간 경로 + 확정 경로

- **Decision**: 두 갈래로 나눈다.
  - **실시간 경로**: Kafka의 클릭 스트림을 1분 텀블링 윈도우(짧은 유예 15초)로 집계해 광고주 준실시간 조회에
    쓴다. 이때의 판정은 **잠정 판정**이며 저장하지 않는다.
  - **확정 경로**: PostgreSQL에 저장된 원본 사실(노출·클릭)을 긴 유예(24시간)가 지난 뒤 SQL로 판정해
    `click_verdict`에 한 번만 기록하고, 일별 확정 리포트를 만든다.
- **Rationale**: ERD의 불변식 "판정은 (click_id, rule_version)당 하나"를 지키려면, 늦게 도착한 노출이나
  앞선 클릭 때문에 결과가 바뀔 수 있는 동안에는 판정을 기록하면 안 된다. 책의 구조(스트림으로 빠르게 집계하고,
  워터마크를 넘긴 이벤트는 배치로 다시 대조)와 같고, 잠정치 → 확정치 → 개정본 흐름과 그대로 맞는다.
- **Alternatives considered**:
  - 스트림에서 판정을 바로 기록: 늦은 이벤트로 판정이 틀려도 고칠 수 없어 불변식과 충돌한다.
  - 확정 경로만 운영: 단순하지만 준실시간 서빙(SC-009)을 만족하지 못한다.

## 2. 언어·런타임·빌드

- **Decision**: Kotlin 2.x, JDK 21 (LTS), Gradle Kotlin DSL 멀티 모듈 + version catalog.
- **Rationale**: 헌법의 "백엔드는 Kotlin만". JDK 21은 Kafka·Spring·Gatling이 모두 안정적으로 지원하는 LTS이며
  가상 스레드로 수집 서버의 블로킹 I/O(Kafka ack 대기)를 단순하게 처리할 수 있다.
- **Alternatives considered**: JDK 25(LTS) — 더 최신이지만 일부 도구 지원이 아직 덜 쌓였다. 도구가 모두
  지원하면 PATCH 수준으로 올린다.

## 3. 웹 프레임워크 (수집·조회 API)

- **Decision**: Spring Boot (현행 메이저), Spring MVC + 가상 스레드.
- **Rationale**: Kafka(Spring Kafka), 스케줄 작업, Actuator·Micrometer 지표, Testcontainers 연동이 한
  생태계에 있다. 서비스 여러 개를 같은 방식으로 만들 수 있다.
- **Alternatives considered**: Ktor — 더 가볍고 Kotlin 친화적이지만 Kafka Streams·스케줄·지표 연동을 직접
  조립해야 해 코드가 늘어난다.

## 4. 메시지 큐

- **Decision**: Apache Kafka (KRaft 모드, ZooKeeper 없음), 로컬은 단일 브로커.
- **Rationale**: 사용자 지정. 책과 같다. 멱등 프로듀서(`enable.idempotence=true`, `acks=all`)로 수집 단계의
  중복·유실을 줄이고, 컨슈머는 저장 후 오프셋을 커밋해 최소 한 번 전달을 보장한다.
- **Topics**: `impressions.v1`, `clicks.v1`, `minute-ad-aggregates.v1`, `events.quarantine.v1`
  (키: 노출은 `impression_id`, 클릭은 `impression_id`가 있으면 그것, 없으면 `click_id`).
  노출과 클릭을 같은 키로 파티셔닝해 실시간 경로의 조인이 같은 파티션에서 일어나게 한다.
- **Alternatives considered**: Redpanda — 로컬에서 더 가볍지만 사용자가 Kafka를 지정했고, 라이선스가
  BSL(소스 공개, 오픈소스 아님)이다.

## 5. 저장소

- **Decision**: PostgreSQL 하나로 원본 사실, 확정 판정, 일별 리포트, 1분 집계를 모두 저장한다.
- **Rationale**: 사용자 지정. 헌법 VIII(단순성) — 저장소 종류를 하나로 줄인다. 노트북 기준 초당 1만 건은
  배치 INSERT(한 번에 수백~수천 행)와 `ON CONFLICT (click_id) DO NOTHING`으로 감당 가능하다.
  PK 충돌을 이용하면 저장 단계 중복 제거(FR-004)가 DB 제약으로 보장된다.
- **파티셔닝**: 처음에는 하지 않는다. 30분 부하 테스트 = 약 1,800만 행으로 단일 테이블로 충분하다.
  데이터가 커지면 날짜 파티셔닝을 도입한다(그때는 PK에 날짜가 들어가야 하므로 UUIDv7에서 날짜를 파생).
- **ClickHouse 참고**: ClickHouse도 Apache 2.0 오픈소스다. 분 단위 집계·상위 N 조회에는 더 강하지만,
  저장소를 하나 더 두는 복잡도에 비해 이 규모에서는 이득이 작아 쓰지 않는다.
- **Alternatives considered**: Cassandra(책) — 쓰기 처리량은 높지만 로컬 운영이 무겁고 집계 SQL이 없다.

## 6. 실시간 집계 엔진

- **Decision**: Kafka Streams (Spring Boot 앱 안에서 실행), `exactly_once_v2`.
- **Rationale**: 별도 클러스터가 필요 없어 로컬 운영이 가볍고 Kotlin으로 바로 작성한다. 이벤트 시각 기반
  텀블링 윈도우와 유예(grace)를 기본 제공한다. Kafka 안에서는 정확히 한 번 처리가 되고, PostgreSQL로
  내보낼 때는 윈도우의 **누적 값을 upsert**하므로 같은 결과를 여러 번 써도 결과가 같다(멱등).
- **처리 방식**:
  1. `clicks.v1`을 `click_id` 기준으로 중복 제거(24시간 보존 상태 저장소).
  2. `impressions.v1`을 상태 저장소로 두고 클릭과 조인해 잠정 판정(노출 없음은 "판정 대기"로 분류,
     오래된 노출·중복 클릭·비인간은 규칙대로).
  3. 광고별 1분 윈도우로 유효·무효(사유별)·판정 대기 수를 집계해 `minute-ad-aggregates.v1`로 내보낸다.
  4. 싱크 컨슈머가 PostgreSQL `minute_ad_aggregate`에 upsert.
- **Alternatives considered**: Apache Flink(책) — 기능은 가장 강력하지만 JobManager·TaskManager를 따로
  띄워야 하고 Kotlin 작성이 덜 자연스럽다. 이 규모에서는 이득보다 운영 부담이 크다.

## 7. 이벤트 스키마 형식과 레지스트리

- **Decision**: Apache Avro + Apicurio Registry(Apache 2.0).
- **Rationale**: "이벤트 스키마"는 Kafka에 오가는 메시지의 필드·타입 약속이다. Kafka 생태계에서는 Avro +
  스키마 레지스트리 조합이 가장 흔하다. 레지스트리가 새 스키마 버전을 등록할 때 하위 호환성을 자동 검사해
  헌법 III(스키마 계약)을 강제하고, 메시지에는 스키마 ID만 붙어 크기가 작다. Apicurio는 Confluent 레지스트리와
  호환되는 API를 제공하는 오픈소스다. 스키마 파일(`.avsc`)은 저장소에 두고 CI에서 호환성을 검사한다.
- **Alternatives considered**:
  - Confluent Schema Registry — 가장 널리 쓰이지만 Confluent Community License(소스 공개, OSI 오픈소스 아님).
  - Protobuf — 성능·도구는 좋지만 Kafka 스키마 진화 사례는 Avro가 더 많다.
  - JSON Schema — 읽기 쉽지만 메시지가 크고 호환성 규칙이 느슨하다.

## 8. 지표·관측

- **Decision**: Micrometer + Prometheus(Apache 2.0, CNCF 졸업 프로젝트). 로그는 JSON 구조화 로그에 `event_id` 포함.
- **Rationale**: Spring Boot Actuator가 Micrometer 지표를 Prometheus 형식으로 바로 내보낸다. 헌법 VI의
  단계별 지표(수신·저장·재시도·격리·중복 제거·지연)를 카운터·히스토그램으로 정의한다.
- **Alternatives considered**: OpenTelemetry Collector 경유 — 확장성은 좋지만 지금은 한 단계를 더 둘 이유가 없다.
  Grafana — 시각화는 직접 만든 웹 화면이 맡으므로 넣지 않는다.

## 9. 시각화 웹 화면

- **Decision**: React + TypeScript + Vite. 실시간 갱신은 SSE(Server-Sent Events).
- **Rationale**: Vite는 현재 React 단일 페이지 앱의 표준 빌드 도구다(React 공식 문서가 Create React App을
  더 이상 권장하지 않고, 새 앱에는 Vite 같은 빌드 도구나 프레임워크를 권한다). 개발 서버가 빠르고 설정이 적다.
  이 화면은 서버 렌더링·라우팅이 필요 없는 로컬 데모라 Next.js 같은 풀스택 프레임워크는 과하다.
  데이터가 서버 → 화면 한 방향이라 WebSocket보다 SSE가 단순하다.
- **화면 구성**: 흐름 다이어그램(SVG) + 단계별 지표 패널 + 대조식 + 시나리오 버튼 + 단계 설명.
  수치는 조회 API의 `/demo/stream`(SSE)만 읽는다(헌법: 웹 화면은 백엔드 API·지표만 읽고 도메인 규칙을 재구현하지 않음).
- **Alternatives considered**: Next.js — SSR·라우팅이 필요 없음. Svelte·Vue — 가능하지만 React 생태계(차트·애니메이션)가 넓다.

## 10. 테스트 (Kotlin에 맞게)

- **Decision**:
  - **Kotest**: Kotlin 전용 테스트 프레임워크. 읽기 쉬운 스펙 스타일과 속성 기반 테스트(무작위 중복·순서 섞기)를 제공.
  - **MockK**: Kotlin 전용 목 라이브러리(코루틴·final 클래스 지원).
  - **Testcontainers**: 실제 Kafka·PostgreSQL·Apicurio를 테스트에서 띄워 통합 테스트.
  - **Kafka Streams TopologyTestDriver**: 브로커 없이 윈도우·유예·중복 제거를 시간 조작으로 검증.
  - **Gatling (Kotlin DSL)**: 부하 테스트(SC-002, SC-009). Kotlin으로 작성해 헌법과 맞는다.
  - 웹 화면: Vitest + Testing Library(TypeScript).
- **Rationale**: 헌법 V(테스트 우선)의 필수 테스트 — 장애 주입 시 유실 없음, 중복 전송 시 한 번만 반영,
  스키마 하위 호환 — 를 각각 Testcontainers 통합 테스트·속성 테스트·레지스트리 호환성 검사로 덮는다.
- **Alternatives considered**: JUnit 5 + Mockito — 동작은 하지만 Kotlin의 final 클래스·널 처리와 덜 맞는다.
  k6 — 부하 테스트 스크립트가 JavaScript라 헌법과 맞지 않는다.

## 11. 판정 규칙 실행 (확정 경로)

- **Decision**: 일별 확정은 Spring 스케줄 작업(`finalizer`)이 SQL로 실행한다.
  중복 클릭은 `LAG(clicked_at) OVER (PARTITION BY impression_id ORDER BY clicked_at, click_id)`로 측정 시각 순서
  기준 직전 클릭과의 간격을 구한다. 규칙 파라미터는 `rule_version`별 설정 파일에 둔다.
- **Rationale**: 늦은 도착이 모두 들어온 뒤 한 번에 판정하므로 결과가 결정적이고, 같은 입력이면 같은 결과가
  나온다(FR-024). 같은 측정 시각이면 `click_id`로 순서를 고정해 결정성을 지킨다.

## 12. 클릭 식별자

- **Decision**: UUIDv7(시간 순 정렬되는 UUID)을 수집 서버가 측정 시점에 발급.
- **Rationale**: 전역 고유 + 시간 순 정렬이라 인덱스 삽입 성능이 좋고, 나중에 날짜 파티셔닝할 때 ID에서 날짜를
  파생할 수 있다.

## 13. 광고주 인증

- **Decision**: 광고주별 정적 API 키(`X-API-Key` 헤더). 키는 해시로 저장하고 시드 데이터로 등록.
- **Rationale**: 스펙상 계정 가입·관리는 범위 밖이고 로컬 데모다. 다른 광고주 데이터 거부(FR-019)는 모든
  조회 쿼리에 `advertiser_id` 조건을 강제해 보장한다.
- **Alternatives considered**: OAuth2/JWT — 이 범위에서는 인증 서버를 따로 둘 이유가 없다.

## 14. 데모 시나리오 구현 방식

- **Decision**: 데모 프로필에서만 켜지는 장애 주입 지점을 둔다.
  - **중복 전송 폭주**: 수집 서버가 일정 시간 동안 같은 클릭을 Kafka에 2~3번 발행(프로듀서 재시도 상황 재현).
  - **늦게 도착한 클릭**: 수집 서버가 일부 클릭의 발행을 N분 지연(네트워크 지연 재현).
  - **단계 장애**: 저장 컨슈머(`ingester`)를 일정 시간 멈췄다가 자동 재개(Kafka에 적체가 쌓였다가 해소).
  - 시나리오 상태는 조회 API가 관리하고 한 번에 하나만 실행(FR-029).
- **Rationale**: 실제 장애 유형을 재현하면서도 데모 프로필이 아니면 코드 경로가 꺼져 운영 동작에 영향이 없다.

## 15. 로컬 실행·CI

- **Decision**: Docker Compose로 Kafka·Apicurio·PostgreSQL·Prometheus와 앱 서비스·웹 화면을 한 번에 띄운다
  (`make demo-up` / `make demo-down`, FR-033). CI는 GitHub Actions: 빌드, Kotest, Testcontainers 통합 테스트,
  Avro 호환성 검사, 웹 화면 타입 검사·테스트.
- **Rationale**: 헌법의 PR·CI 게이트를 실제로 적용한다. 노트북 한 대 기준(spec Assumptions).
