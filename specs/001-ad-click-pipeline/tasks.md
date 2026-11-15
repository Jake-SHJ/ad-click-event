---
description: "Task list for 광고 클릭 수집·집계·준실시간 서빙 파이프라인"
---

# Tasks: 광고 클릭 수집·집계·준실시간 서빙 파이프라인

**Input**: Design documents from `/specs/001-ad-click-pipeline/`
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: 포함한다. 헌법 V(테스트 우선, NON-NEGOTIABLE)에 따라 각 스토리의 테스트를 구현보다 먼저 쓰고,
실패를 확인한 뒤 구현한다. 원칙 I–III(무손실·중복 제거·스키마 호환)은 전용 테스트를 둔다.

**Organization**: 사용자 스토리별로 묶어, 스토리마다 따로 구현·검증할 수 있게 한다.

**Commits**: 저장소 CLAUDE.md 규칙 — `feat`/`test`/`build` 등 영어 prefix + 한글 메시지, 한 커밋은 한 기능을
넘지 않고, 각 커밋은 빌드·테스트가 통과하는 상태. 스토리 하나가 끝날 때마다 PR 단위로 묶는다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 병렬 가능(다른 파일, 끝나지 않은 작업에 의존하지 않음)
- **[Story]**: 해당 사용자 스토리(US1~US4)

## Path Conventions

- Kotlin 모듈: `<module>/src/main/kotlin/dev/jakeshj/adclick/<module>/`,
  단위 테스트 `<module>/src/test/kotlin/dev/jakeshj/adclick/<module>/`,
  통합 테스트 `<module>/src/integrationTest/kotlin/dev/jakeshj/adclick/<module>/`
- 모듈: `schema`, `common`, `db`, `collector`, `ingester`, `aggregator`, `finalizer`, `query-api`, `loadgen`, `loadtest`
- 웹: `web/src/`, 테스트는 같은 폴더의 `*.test.ts(x)`
- 아래 경로는 위 규칙에 따른 전체 경로다.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 빌드·로컬 인프라·CI 뼈대

- [ ] T001 Create Gradle multi-module root with modules `schema, common, db, collector, ingester, aggregator, finalizer, query-api, loadgen, loadtest` in settings.gradle.kts and build.gradle.kts
- [ ] T002 Pin latest stable versions (Kotlin 2.x, JDK 21 toolchain, Spring Boot, Spring Kafka, Kafka Streams, Avro, Apicurio serdes, Flyway, PostgreSQL JDBC, Micrometer Prometheus, Kotest, MockK, Testcontainers, Gatling) in gradle/libs.versions.toml
- [ ] T003 Create convention plugins `kotlin-library`, `spring-service` (Spring Boot + Actuator + Prometheus endpoint + JSON logging) and `integration-test` (adds `integrationTest` source set and task running Kotest with Testcontainers) in build-logic/src/main/kotlin/
- [ ] T004 [P] Configure ktlint via Gradle plugin and shared rules in .editorconfig
- [ ] T005 [P] Create docker-compose.yml infra services: Kafka (KRaft single broker, port 9092), Apicurio Registry (port 8080, compatibility rule BACKWARD), PostgreSQL (port 5432), Prometheus (port 9090) with health checks
- [ ] T006 [P] Create Prometheus scrape config for all app services' `/actuator/prometheus` in infra/prometheus/prometheus.yml
- [ ] T007 [P] Create Kafka topic init (`impressions.v1`, `clicks.v1`, `minute-ad-aggregates.v1`, `events.quarantine.v1`; partitions 6; replication 1) in infra/kafka/create-topics.sh run by a compose init service
- [ ] T008 [P] Scaffold React + TypeScript + Vite app with Vitest + Testing Library and strict tsconfig in web/package.json, web/vite.config.ts, web/tsconfig.json
- [ ] T009 Create Makefile targets `demo-up`, `demo-down`, `demo-clean` wrapping docker compose (FR-033) in Makefile
- [ ] T010 [P] Create GitHub Actions workflow: Gradle build, unit tests, integrationTest, Avro compatibility check, web typecheck + Vitest in .github/workflows/ci.yml
- [ ] T011 [P] Create demo environment file with seeded advertiser API keys `ADV1_KEY..ADV3_KEY` (synthetic values) in .env.demo

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 모든 스토리가 쓰는 스키마·DB·공통 코드·테스트 픽스처

**⚠️ CRITICAL**: 이 단계가 끝나기 전에는 어떤 스토리도 시작하지 않는다

### Tests for Foundation ⚠️

- [ ] T012 [P] Write schema compatibility test: each `.avsc` in schema/src/main/avro must be BACKWARD compatible with its previous version in schema/src/main/avro/history/ (uses Avro `SchemaCompatibility`) in schema/src/test/kotlin/dev/jakeshj/adclick/schema/SchemaCompatibilityTest.kt
- [ ] T013 [P] Write UUIDv7 test: IDs are unique across 1M generations, time-ordered, and the embedded timestamp round-trips in common/src/test/kotlin/dev/jakeshj/adclick/common/UuidV7Test.kt
- [ ] T014 [P] Write migration test: Flyway migrates an empty Testcontainers PostgreSQL and all tables/constraints from data-model.md exist in db/src/integrationTest/kotlin/dev/jakeshj/adclick/db/MigrationTest.kt

### Implementation for Foundation

- [ ] T015 [P] Copy contracts/events/{click,impression,minute-ad-aggregate}.avsc into schema/src/main/avro/ and configure Avro Gradle code generation in schema/build.gradle.kts
- [ ] T016 [P] Implement UUIDv7 generator in common/src/main/kotlin/dev/jakeshj/adclick/common/UuidV7.kt
- [ ] T017 [P] Define metric names and tag keys from data-model.md "파이프라인 지표" (`adclick_events_received_total`, `adclick_events_stored_total`, `adclick_events_deduplicated_total`, `adclick_events_retried_total`, `adclick_events_quarantined_total`, `adclick_consumer_lag`, `adclick_end_to_end_latency_seconds`, `adclick_verdicts_total`) in common/src/main/kotlin/dev/jakeshj/adclick/common/Metrics.kt
- [ ] T018 [P] Configure JSON structured logging with `event_id` MDC key in common/src/main/resources/logback-adclick.xml
- [ ] T019 [P] Implement rule parameters per `rule_version` loaded from YAML (v1: impression validity 24h, re-click interval 10s, bot UA list, internal IP CIDRs) in common/src/main/kotlin/dev/jakeshj/adclick/common/RuleParameters.kt and common/src/main/resources/rules/v1.yml
- [ ] T020 Write Flyway migration for `advertiser`, `ad`, `impression`, `click`, `click_verdict`, `daily_ad_report`, `minute_ad_aggregate`, `quarantined_event`, `demo_scenario_run` exactly as data-model.md: `click.impression_id` nullable with NO FK, `click_verdict` PK `(click_id, rule_version)`, `validity` IN ('VALID','INVALID'), `reason` IN ('NO_IMPRESSION','STALE_IMPRESSION','DUPLICATE','NON_HUMAN','AD_MISMATCH'), `daily_ad_report` PK `(ad_id, report_date, revision)` with "revision_reason NOT NULL when revision ≥ 2", partial unique index allowing at most one `demo_scenario_run` with status IN ('RUNNING','RECOVERING'), indexes `click(impression_id, clicked_at, click_id)` and `click(ad_id, clicked_at)`, and `COMMENT ON COLUMN click.ip/click.user_agent IS 'PII'` in db/src/main/resources/db/migration/V1__init.sql
- [ ] T021 Write demo seed migration (3 advertisers with `Asia/Seoul`, `UTC`, `America/New_York`; 30 ads with landing URLs; API key hashes for .env.demo keys) in db/src/main/resources/db/migration/demo/V100__demo_seed.sql
- [ ] T022 [P] Create shared Testcontainers fixtures (Kafka, PostgreSQL with Flyway applied, Apicurio) as Kotest project extensions in common/src/testFixtures/kotlin/dev/jakeshj/adclick/common/test/Containers.kt
- [ ] T023 [P] Create Avro serde configuration for Apicurio (auto-register off, use registered schemas) in common/src/main/kotlin/dev/jakeshj/adclick/common/AvroSerdes.kt

**Checkpoint**: 스키마·DB·공통 코드 준비 완료. T012~T014가 통과해야 한다.

---

## Phase 3: User Story 1 - 클릭을 유실·중복 없이 측정·저장 (Priority: P1) 🎯 MVP

**Goal**: 클릭을 측정해 click_id를 발급하고 랜딩으로 보내며, Kafka를 거쳐 PostgreSQL에 정확히 한 번 저장한다.

**Independent Test**: 중복 전송·브로커 재시작·컨슈머 강제 종료를 섞어 보낸 뒤, 저장된 고유 클릭 수 = 보낸 고유
클릭 수(SC-001). 형식 오류 이벤트는 `quarantined_event`에 남는다.

### Tests for User Story 1 ⚠️

> 먼저 쓰고, 실패하는 것을 확인한 뒤 구현한다

- [ ] T024 [P] [US1] Contract test for `GET /c/{adId}` (302 + `Location` = ad landing_url + `X-Click-Id` UUID, 404 for unknown ad, `imp` optional) and `POST /impressions` (202, 400 on missing `impressionId`/`adId`/`servedAt`) per contracts/collector-api.yaml in collector/src/test/kotlin/dev/jakeshj/adclick/collector/CollectorContractTest.kt
- [ ] T025 [P] [US1] Integration test: the 302 is returned only after the click record is acknowledged on `clicks.v1`, and the record matches click.avsc in collector/src/integrationTest/kotlin/dev/jakeshj/adclick/collector/PublishBeforeRedirectTest.kt
- [ ] T026 [P] [US1] Integration test: with Kafka stopped, clicks still redirect, are written to the disk buffer, and are all published after Kafka restarts (no loss) in collector/src/integrationTest/kotlin/dev/jakeshj/adclick/collector/DiskBufferRecoveryTest.kt
- [ ] T027 [P] [US1] Property-based integration test `NoLossNoDuplicate`: random click sets with random duplicate re-sends, consumer kill/restart and broker restart → stored unique clicks == sent unique clicks and `deduplicated_total` == duplicates sent in ingester/src/integrationTest/kotlin/dev/jakeshj/adclick/ingester/NoLossNoDuplicateTest.kt
- [ ] T028 [P] [US1] Integration test: a record failing deserialization or exceeding retries lands in `quarantined_event` with `reason` `SCHEMA_INVALID` or `RETRY_EXHAUSTED` and the original payload in ingester/src/integrationTest/kotlin/dev/jakeshj/adclick/ingester/QuarantineTest.kt
- [ ] T029 [P] [US1] Integration test: impressions are stored once per `impression_id` and `received_at` is set in ingester/src/integrationTest/kotlin/dev/jakeshj/adclick/ingester/ImpressionIngestTest.kt

### Implementation for User Story 1

- [ ] T030 [P] [US1] Implement ad lookup cache (ad_id → landing_url, advertiser_id; refresh every 60s) in collector/src/main/kotlin/dev/jakeshj/adclick/collector/AdCatalog.kt
- [ ] T031 [P] [US1] Implement idempotent Kafka publisher (`enable.idempotence=true`, `acks=all`, synchronous ack wait with timeout, key = impressionId else clickId) in collector/src/main/kotlin/dev/jakeshj/adclick/collector/EventPublisher.kt
- [ ] T032 [P] [US1] Implement append-only disk buffer with fsync and background replayer that republishes and truncates only after ack in collector/src/main/kotlin/dev/jakeshj/adclick/collector/DiskBuffer.kt
- [ ] T033 [US1] Implement `GET /c/{adId}`: issue UUIDv7 `clickId` and `clickedAt`, capture `ip`/`userAgent`, publish (fallback to DiskBuffer), then 302 with `X-Click-Id` in collector/src/main/kotlin/dev/jakeshj/adclick/collector/ClickController.kt
- [ ] T034 [US1] Implement `POST /impressions` with request validation; invalid payloads are sent to `events.quarantine.v1` with reason `SCHEMA_INVALID` in collector/src/main/kotlin/dev/jakeshj/adclick/collector/ImpressionController.kt
- [ ] T035 [US1] Add collector metrics (`received_total{stage=collector,type}`, `retried_total`, `quarantined_total`) and `event_id` logging in collector/src/main/kotlin/dev/jakeshj/adclick/collector/CollectorMetrics.kt
- [ ] T036 [P] [US1] Implement batch JDBC writer: multi-row `INSERT ... ON CONFLICT (click_id) DO NOTHING` returning inserted count; `deduplicated = batch size − inserted` in ingester/src/main/kotlin/dev/jakeshj/adclick/ingester/ClickWriter.kt
- [ ] T037 [P] [US1] Implement batch JDBC writer for impressions with `ON CONFLICT (impression_id) DO NOTHING` in ingester/src/main/kotlin/dev/jakeshj/adclick/ingester/ImpressionWriter.kt
- [ ] T038 [US1] Implement batch Kafka listeners for `clicks.v1` and `impressions.v1` with manual offset commit after DB write, `DefaultErrorHandler` (exponential backoff, max 5 retries) and `DeadLetterPublishingRecoverer` to `events.quarantine.v1` in ingester/src/main/kotlin/dev/jakeshj/adclick/ingester/IngestListeners.kt
- [ ] T039 [US1] Implement quarantine consumer storing `source_topic`, raw `payload`, `reason`, `error_detail` into `quarantined_event` in ingester/src/main/kotlin/dev/jakeshj/adclick/ingester/QuarantineListener.kt
- [ ] T040 [US1] Add ingester metrics (`stored_total`, `deduplicated_total{stage=ingester}`, `consumer_lag{stage=ingester}`) in ingester/src/main/kotlin/dev/jakeshj/adclick/ingester/IngesterMetrics.kt
- [ ] T041 [US1] Add `collector` and `ingester` services (demo profile, depends_on infra health) to docker-compose.yml

**Checkpoint**: US1 단독으로 동작. T024~T029 통과, quickstart 2번 확인 가능.

---

## Phase 4: User Story 2 - 클릭 유효성 판정과 일별 확정 리포트 (Priority: P2)

**Goal**: 24시간 유예가 지난 클릭을 규칙 버전별로 한 번 판정하고, 광고주 시간대 기준 일별 확정 리포트와 개정본을 만든다.

**Independent Test**: PostgreSQL에 사유별 픽스처를 직접 넣고 finalizer를 실행해, 판정·일별 합계·`valid + invalid = 총수`·
늦은 클릭 개정본을 확인한다(SC-004, SC-005). US1 없이도 검증 가능.

### Tests for User Story 2 ⚠️

- [ ] T042 [P] [US2] Integration test for rule order `AD_MISMATCH → NO_IMPRESSION → STALE_IMPRESSION → DUPLICATE → NON_HUMAN → VALID` with one fixture per reason in finalizer/src/integrationTest/kotlin/dev/jakeshj/adclick/finalizer/VerdictRulesTest.kt
- [ ] T043 [P] [US2] Integration test: `DUPLICATE` uses the previous click on the same impression by `(clicked_at, click_id)` regardless of its verdict, independent of insert order in finalizer/src/integrationTest/kotlin/dev/jakeshj/adclick/finalizer/DuplicateOrderingTest.kt
- [ ] T044 [P] [US2] Integration test: clicks younger than 24h are not judged; re-running the job inserts no new `click_verdict` rows and yields identical reports (FR-024) in finalizer/src/integrationTest/kotlin/dev/jakeshj/adclick/finalizer/IdempotentFinalizeTest.kt
- [ ] T045 [P] [US2] Integration test: daily report day boundaries follow advertiser `time_zone` and `time_zone_effective_from`; every FINAL row satisfies `valid_clicks + invalid_clicks` = clicks measured that day and `invalid_by_reason` sums to `invalid_clicks` in finalizer/src/integrationTest/kotlin/dev/jakeshj/adclick/finalizer/DailyReportTest.kt
- [ ] T046 [P] [US2] Integration test: a late click for an already FINAL date creates revision n+1 with non-null `revision_reason` and leaves revision n unchanged in finalizer/src/integrationTest/kotlin/dev/jakeshj/adclick/finalizer/LateClickRevisionTest.kt
- [ ] T047 [P] [US2] Integration test: a new `rule_version` re-judges clicks and keeps the previous version's verdicts in finalizer/src/integrationTest/kotlin/dev/jakeshj/adclick/finalizer/RuleVersionTest.kt

### Implementation for User Story 2

- [ ] T048 [P] [US2] Implement verdict SQL: `INSERT INTO click_verdict ... SELECT` with `LAG(clicked_at) OVER (PARTITION BY impression_id ORDER BY clicked_at, click_id)`, rule parameters bound from RuleParameters, `ON CONFLICT DO NOTHING`, limited to clicks with `clicked_at <= now() - 24h` in finalizer/src/main/kotlin/dev/jakeshj/adclick/finalizer/VerdictJob.kt
- [ ] T049 [US2] Implement daily FINAL report: for each (ad, local date) whose end + 24h has passed and no FINAL exists, insert revision 1 with counts and `invalid_by_reason` in finalizer/src/main/kotlin/dev/jakeshj/adclick/finalizer/DailyReportJob.kt
- [ ] T050 [US2] Implement revision detection: compare stored FINAL counts with recomputed counts; on difference insert revision n+1 with `revision_reason` = "late clicks: +N" in finalizer/src/main/kotlin/dev/jakeshj/adclick/finalizer/RevisionJob.kt
- [ ] T051 [US2] Implement scheduler running VerdictJob → DailyReportJob → RevisionJob every 10 minutes (single-instance lock via `pg_try_advisory_lock`) in finalizer/src/main/kotlin/dev/jakeshj/adclick/finalizer/FinalizeScheduler.kt
- [ ] T052 [US2] Add metrics `verdicts_total{path=final,validity,reason}` and job duration in finalizer/src/main/kotlin/dev/jakeshj/adclick/finalizer/FinalizerMetrics.kt
- [ ] T053 [US2] Add `finalizer` service to docker-compose.yml with demo override of grace period (env `ADCLICK_FINAL_GRACE`, default PT24H, demo PT5M so revisions are visible during a demo)

**Checkpoint**: US2 단독 동작. T042~T047 통과, quickstart 3번 확인 가능.

---

## Phase 5: User Story 3 - 광고주 준실시간 조회 (Priority: P3)

**Goal**: Kafka Streams가 1분 윈도우로 잠정 집계하고, 광고주가 API 키로 최근 M분·상위 N·일별 수치를 조회한다.

**Independent Test**: TopologyTestDriver로 윈도우·유예·중복 제거를 검증하고, `minute_ad_aggregate`·`daily_ad_report`
픽스처로 조회 API의 응답·격리·시간대를 검증한다(SC-007~SC-009).

### Tests for User Story 3 ⚠️

- [ ] T054 [P] [US3] Topology test: duplicate `clickId` within 24h counted once; 1-minute tumbling windows by `clickedAt`; record arriving within 15s grace is counted, after grace is dropped and counted in a `late_dropped` metric in aggregator/src/test/kotlin/dev/jakeshj/adclick/aggregator/WindowingTopologyTest.kt
- [ ] T055 [P] [US3] Topology test: provisional verdicts — no impression yet → `pendingClicks`; stale/duplicate/non-human/ad-mismatch → `invalidByReason`; and `valid + invalid + pending` = clicks in window in aggregator/src/test/kotlin/dev/jakeshj/adclick/aggregator/ProvisionalVerdictTest.kt
- [ ] T056 [P] [US3] Integration test: aggregate sink upserts `minute_ad_aggregate` so replaying the same records yields identical rows in aggregator/src/integrationTest/kotlin/dev/jakeshj/adclick/aggregator/AggregateSinkTest.kt
- [ ] T057 [P] [US3] Contract tests for `/v1/ads/{adId}/clicks/recent` (minutes 1..1440), `/v1/ads/top` (limit ≤ 100, default 10, sorted by validClicks desc) and `/v1/ads/{adId}/clicks/daily` per contracts/query-api.yaml in query-api/src/test/kotlin/dev/jakeshj/adclick/query/AdvertiserApiContractTest.kt
- [ ] T058 [P] [US3] Integration test: missing/invalid `X-API-Key` → 401; another advertiser's ad → 404 on every endpoint; top-N never includes other advertisers' ads in query-api/src/integrationTest/kotlin/dev/jakeshj/adclick/query/AdvertiserIsolationTest.kt
- [ ] T059 [P] [US3] Integration test: daily endpoint returns latest revision FINAL for finalized dates and PRELIMINARY sums of `minute_ad_aggregate` for others, using advertiser time zone boundaries in query-api/src/integrationTest/kotlin/dev/jakeshj/adclick/query/DailyClicksTest.kt

### Implementation for User Story 3

- [ ] T060 [P] [US3] Implement provisional judge using RuleParameters (same rule order as final path, no-impression → pending) in aggregator/src/main/kotlin/dev/jakeshj/adclick/aggregator/ProvisionalJudge.kt
- [ ] T061 [US3] Implement Kafka Streams topology: dedup store (24h retention) on `clicks.v1`, impression state store from `impressions.v1`, last-click-per-impression store, 1-min tumbling window with 15s grace, emit cumulative MinuteAdAggregate to `minute-ad-aggregates.v1`, `processing.guarantee=exactly_once_v2` in aggregator/src/main/kotlin/dev/jakeshj/adclick/aggregator/AggregationTopology.kt
- [ ] T062 [US3] Implement aggregate sink consumer: `INSERT ... ON CONFLICT (ad_id, window_start) DO UPDATE` in aggregator/src/main/kotlin/dev/jakeshj/adclick/aggregator/AggregateSink.kt
- [ ] T063 [US3] Add aggregator metrics (`verdicts_total{path=realtime}`, `end_to_end_latency_seconds{path=realtime}` from `clickedAt` to upsert time, `consumer_lag{stage=aggregator}`, `late_dropped_total`) in aggregator/src/main/kotlin/dev/jakeshj/adclick/aggregator/AggregatorMetrics.kt
- [ ] T064 [P] [US3] Implement API key authentication filter (hash compare against `advertiser.api_key_hash`, resolves advertiser_id) in query-api/src/main/kotlin/dev/jakeshj/adclick/query/ApiKeyFilter.kt
- [ ] T065 [P] [US3] Implement repositories that always filter by `advertiser_id` for minute aggregates, top-N and daily reports in query-api/src/main/kotlin/dev/jakeshj/adclick/query/AdvertiserReadRepository.kt
- [ ] T066 [US3] Implement controllers for recent, top and daily endpoints with response shapes from contracts/query-api.yaml in query-api/src/main/kotlin/dev/jakeshj/adclick/query/AdvertiserController.kt
- [ ] T067 [US3] Add `aggregator` and `query-api` services to docker-compose.yml

**Checkpoint**: US3 단독 동작. T054~T059 통과, quickstart 4번 확인 가능.

---

## Phase 6: User Story 4 - 시스템 설명 실시간 시각화 (포트폴리오 데모) (Priority: P4)

**Goal**: 로컬 데모에서 흐름·지표·대조식·설계 설명을 보여주고, 데모 시나리오 3종을 실행할 수 있다.

**Independent Test**: `make demo-up` 후 화면에서 수치가 5초 안에 갱신되고, 시나리오마다 10초 안에 효과가 보이며
끝난 뒤 대조식이 balanced(SC-010~SC-012).

### Tests for User Story 4 ⚠️

- [ ] T068 [P] [US4] Test: only one scenario runs at a time (second request → 409 with the running scenario), status goes `RUNNING → RECOVERING → DONE`, and auto-recovery fires at `ends_at` in query-api/src/integrationTest/kotlin/dev/jakeshj/adclick/query/demo/ScenarioServiceTest.kt
- [ ] T069 [P] [US4] Test: snapshot assembler maps Prometheus results to demo-stream.md `snapshot` JSON and computes `balanced` = `received == stored + deduplicated + quarantined + inFlight` in query-api/src/test/kotlin/dev/jakeshj/adclick/query/demo/SnapshotAssemblerTest.kt
- [ ] T070 [P] [US4] Test: collector fault-injection endpoints (duplicate publish ×2–3, delayed publish N minutes) exist only under the `demo` profile in collector/src/test/kotlin/dev/jakeshj/adclick/collector/demo/FaultInjectionProfileTest.kt
- [ ] T071 [P] [US4] Test: ingester pause/resume endpoint stops consumption and resumes without loss in ingester/src/integrationTest/kotlin/dev/jakeshj/adclick/ingester/demo/PauseResumeTest.kt
- [ ] T072 [P] [US4] Web tests: stages render from a snapshot fixture; unbalanced reconciliation shows a warning; scenario buttons are disabled while a scenario runs; stage explanation shows on hover/click in web/src/App.test.tsx

### Implementation for User Story 4

- [ ] T073 [P] [US4] Implement demo-profile fault injection controller (`POST /internal/demo/duplicate`, `POST /internal/demo/delay`) wired into EventPublisher in collector/src/main/kotlin/dev/jakeshj/adclick/collector/demo/FaultInjectionController.kt
- [ ] T074 [P] [US4] Implement demo-profile pause/resume endpoint for the ingest listener container in ingester/src/main/kotlin/dev/jakeshj/adclick/ingester/demo/PauseController.kt
- [ ] T075 [P] [US4] Implement Prometheus HTTP API client (instant queries for rates, p95 latency, lag, counters) in query-api/src/main/kotlin/dev/jakeshj/adclick/query/demo/PrometheusClient.kt
- [ ] T076 [US4] Implement snapshot assembler producing demo-stream.md `snapshot` in query-api/src/main/kotlin/dev/jakeshj/adclick/query/demo/SnapshotAssembler.kt
- [ ] T077 [US4] Implement scenario service persisting `demo_scenario_run` and calling collector/ingester internal endpoints (DUPLICATE_BURST 60s, LATE_ARRIVAL delay 2m, STAGE_OUTAGE 30s) in query-api/src/main/kotlin/dev/jakeshj/adclick/query/demo/ScenarioService.kt
- [ ] T078 [US4] Implement `GET /demo/stream` (SSE, `snapshot` every 1s, `scenario` on change) and `POST /demo/scenarios/{scenario}` (202/409), demo profile only, in query-api/src/main/kotlin/dev/jakeshj/adclick/query/demo/DemoController.kt
- [ ] T079 [P] [US4] Implement synthetic load generator (configurable rate, impressions then clicks via collector HTTP, mix of valid/stale/duplicate/bot/no-impression traffic) in loadgen/src/main/kotlin/dev/jakeshj/adclick/loadgen/LoadGenerator.kt
- [ ] T080 [P] [US4] Implement SSE client hook typed from demo-stream.md in web/src/api/useDemoStream.ts
- [ ] T081 [P] [US4] Implement SVG flow diagram (collector → Kafka → ingester/aggregator → PostgreSQL → finalizer/query) with per-stage rate, latency and backlog in web/src/components/FlowDiagram.tsx
- [ ] T082 [P] [US4] Implement stage explanations text (멱등 키 중복 제거, 늦은 도착 유예, 사실·판정 분리, 잠정→확정) in web/src/content/stageExplanations.ts
- [ ] T083 [P] [US4] Implement reconciliation panel with balanced/unbalanced state in web/src/components/ReconciliationPanel.tsx
- [ ] T084 [P] [US4] Implement verdict distribution panel (valid/pending/invalid by reason) in web/src/components/VerdictPanel.tsx
- [ ] T085 [P] [US4] Implement scenario panel (three buttons, running state, countdown) calling `POST /demo/scenarios/{scenario}` in web/src/components/ScenarioPanel.tsx
- [ ] T086 [US4] Compose the page layout from the components in web/src/App.tsx
- [ ] T087 [US4] Add `loadgen` and `web` services and set `SPRING_PROFILES_ACTIVE=demo` for all app services in docker-compose.yml

**Checkpoint**: 모든 스토리 동작. `make demo-up`으로 quickstart 5번 확인 가능.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T088 [P] Implement Gatling Kotlin simulation: 10,000 clicks/s sustained 30 min; assertions redirect p95 ≤ 1s, p99 ≤ 2s, 0 failed requests in loadtest/src/gatling/kotlin/dev/jakeshj/adclick/loadtest/ClickLoadSimulation.kt
- [ ] T089 Run load test, verify SC-002/SC-003/SC-009 (realtime latency histogram p95 ≤ 2m, p99 ≤ 5m) and record results and tuning notes in specs/001-ad-click-pipeline/load-test-results.md
- [ ] T090 [P] Write portfolio-facing README (architecture diagram, realtime vs final paths, guarantees, how to run the demo) in README.md
- [ ] T091 [P] Add CI step that fails when an `.avsc` change is not BACKWARD compatible (runs T012 test against history) in .github/workflows/ci.yml
- [ ] T092 Run every step of specs/001-ad-click-pipeline/quickstart.md on a clean machine state and fix gaps
- [ ] T093 Re-check plan.md Constitution Check and Complexity Tracking against the implemented code and update specs/001-ad-click-pipeline/plan.md if anything changed

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 의존 없음
- **Foundational (Phase 2)**: Setup 완료 후. 모든 스토리를 막는다
- **US1~US4 (Phase 3~6)**: Foundational 완료 후
- **Polish (Phase 7)**: 원하는 스토리가 모두 끝난 뒤

### User Story Dependencies

- **US1 (P1)**: Foundational만 필요
- **US2 (P2)**: Foundational만 필요(테스트는 DB 픽스처 사용). 실제 데이터 흐름은 US1의 저장 결과를 쓴다
- **US3 (P3)**: Foundational만 필요(TopologyTestDriver·DB 픽스처). 실제 흐름은 US1의 Kafka 토픽, 일별 조회는 US2의 확정 리포트를 쓴다
- **US4 (P4)**: US1~US3 전부 필요(보여줄 지표와 장애 주입 대상이 그 서비스들)

### Within Each User Story

- 테스트 먼저 작성 → 실패 확인 → 구현 (헌법 V)
- 저장·발행 같은 하위 구성요소 → 컨트롤러·리스너 → 지표 → compose 서비스 추가
- docker-compose.yml을 고치는 작업(T041, T053, T067, T087)은 같은 파일이라 병렬 불가

### Parallel Opportunities

- Setup: T004~T008, T010, T011
- Foundational: 테스트 T012~T014, 구현 T015~T019, T022, T023
- 스토리 안: 각 스토리의 테스트 전부, 그리고 서로 다른 파일의 하위 구성요소
- Foundational 이후 US1·US2·US3은 동시에 진행 가능

---

## Parallel Example: User Story 1

```bash
# 테스트 먼저 (동시에):
Task: "Contract test ... in collector/src/test/kotlin/dev/jakeshj/adclick/collector/CollectorContractTest.kt"
Task: "Integration test ... in collector/src/integrationTest/kotlin/dev/jakeshj/adclick/collector/DiskBufferRecoveryTest.kt"
Task: "Property-based test ... in ingester/src/integrationTest/kotlin/dev/jakeshj/adclick/ingester/NoLossNoDuplicateTest.kt"

# 하위 구성요소 (동시에):
Task: "AdCatalog.kt", "EventPublisher.kt", "DiskBuffer.kt", "ClickWriter.kt", "ImpressionWriter.kt"
```

## Parallel Example: User Story 4 (web)

```bash
Task: "useDemoStream.ts", "FlowDiagram.tsx", "stageExplanations.ts", "ReconciliationPanel.tsx", "VerdictPanel.tsx", "ScenarioPanel.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup → Phase 2 Foundational
2. Phase 3 US1
3. **멈추고 검증**: quickstart 2번(유실 0건·중복 0건)
4. PR로 main에 병합

### Incremental Delivery

1. Setup + Foundational → PR
2. US1 → 검증 → PR (MVP: 유실·중복 없는 수집)
3. US2 → 검증 → PR (확정 리포트)
4. US3 → 검증 → PR (준실시간 조회)
5. US4 → 검증 → PR (포트폴리오 데모)
6. Polish → 부하 테스트 결과·README → PR

각 PR은 헌법의 셀프 체크리스트(원칙 I–VIII)와 CI 통과가 조건이다.
