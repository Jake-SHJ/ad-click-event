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

**Structure**: 앱 2개 — `collector`(단독) + `pipeline`(Spring Modulith). 근거는 research.md 16번.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 병렬 가능(다른 파일, 끝나지 않은 작업에 의존하지 않음)
- **[Story]**: 해당 사용자 스토리(US1~US4)

## Path Conventions

- Gradle 모듈: `shared`, `collector`, `pipeline`, `loadtest` (+ `web`)
- 패키지 루트: `dev/jakeshj/adclick/<module>/`. `pipeline`은 그 아래 Modulith 모듈 패키지
  `rules`, `ingest`, `realtime`, `finalize`, `query`, `demo`
- 소스 세트: `src/main/kotlin`, `src/test/kotlin`(단위), `src/integrationTest/kotlin`(Testcontainers),
  `shared/src/testFixtures/kotlin`(공용 테스트 픽스처)
- 웹: `web/src/`, 테스트는 같은 폴더의 `*.test.ts(x)`
- 아래 경로는 위 규칙에 따른 전체 경로다.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 빌드·로컬 인프라·CI 뼈대

- [X] T001 Create Gradle multi-module root with modules `shared, collector, pipeline, loadtest` and Gradle 9.9.1 wrapper in settings.gradle.kts and build.gradle.kts
- [X] T002 Pin latest stable versions (Kotlin 2.3.21 aligned with Spring Boot 4.1.2 BOM, JDK 21 toolchain, Spring Modulith 2.1.2, Avro, Apicurio serdes, Kotest, MockK, Gatling; BOM-managed: Kafka, Spring Kafka, Flyway, PostgreSQL JDBC, Micrometer, Testcontainers) in gradle/libs.versions.toml
- [X] T003 Create convention plugins `adclick.kotlin-library`, `adclick.spring-service` (Spring Boot + Actuator + Prometheus + JSON logging) and `adclick.integration-test` (`integrationTest` suite run by `check`) in build-logic/src/main/kotlin/
- [X] T004 [P] Configure ktlint via Gradle plugin and shared rules in .editorconfig
- [X] T005 [P] Create docker-compose.yml infra services: Kafka (KRaft single broker, port 9092), Apicurio Registry (port 8080), PostgreSQL 18 (port 5432), Prometheus (port 9090) with health checks
- [X] T006 [P] Create Prometheus scrape config for `collector:8081` and `pipeline:8082` `/actuator/prometheus` in infra/prometheus/prometheus.yml
- [X] T007 [P] Create Kafka topic init (`impressions.v1`, `clicks.v1`, `minute-ad-aggregates.v1`, `events.quarantine.v1`; partitions 6) and Apicurio global rules (COMPATIBILITY=BACKWARD, VALIDITY=FULL) in infra/kafka/create-topics.sh and infra/apicurio/init-rules.sh
- [X] T008 [P] Scaffold React + TypeScript (strict) + Vite app with Vitest + Testing Library, Oxlint and dev proxy to pipeline:8082 in web/package.json, web/vite.config.ts, web/tsconfig.app.json
- [X] T009 Create Makefile targets `demo-up`, `demo-down`, `demo-clean`, `demo-logs`, `infra-up` wrapping docker compose (FR-033) in Makefile
- [X] T010 [P] Create GitHub Actions workflow: Gradle build (ktlint, unit, integrationTest) and web typecheck + lint + Vitest + build in .github/workflows/ci.yml
- [X] T011 [P] Create demo environment file with synthetic advertiser API keys `ADV1_KEY..ADV3_KEY` in .env.demo
- [X] T012 Create `pipeline` Spring Modulith application with module verification and documentation test in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/PipelineApplication.kt and pipeline/src/test/kotlin/dev/jakeshj/adclick/pipeline/ModularityTest.kt

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 모든 스토리가 쓰는 스키마·DB·공통 코드·테스트 픽스처

**⚠️ CRITICAL**: 이 단계가 끝나기 전에는 어떤 스토리도 시작하지 않는다

### Tests for Foundation ⚠️

- [ ] T013 [P] Write schema compatibility test: each `.avsc` in shared/src/main/avro must be BACKWARD compatible with its previous version in shared/src/main/avro/history/ (Avro `SchemaCompatibility`) in shared/src/test/kotlin/dev/jakeshj/adclick/shared/SchemaCompatibilityTest.kt
- [ ] T014 [P] Write UUIDv7 test: IDs are unique across 1M generations, time-ordered, and the embedded timestamp round-trips in shared/src/test/kotlin/dev/jakeshj/adclick/shared/UuidV7Test.kt
- [ ] T015 [P] Write migration test: Flyway migrates an empty Testcontainers PostgreSQL and all tables/constraints from data-model.md exist in pipeline/src/integrationTest/kotlin/dev/jakeshj/adclick/pipeline/MigrationTest.kt

### Implementation for Foundation

- [ ] T016 [P] Copy contracts/events/{click,impression,minute-ad-aggregate}.avsc into shared/src/main/avro/ and configure Avro code generation (Gradle task using Avro `SpecificCompiler`, output to build/generated) in shared/build.gradle.kts
- [ ] T017 [P] Implement UUIDv7 generator in shared/src/main/kotlin/dev/jakeshj/adclick/shared/UuidV7.kt
- [ ] T018 [P] Define metric names and tag keys from data-model.md "파이프라인 지표" (`adclick_events_received_total`, `adclick_events_stored_total`, `adclick_events_deduplicated_total`, `adclick_events_retried_total`, `adclick_events_quarantined_total`, `adclick_consumer_lag`, `adclick_end_to_end_latency_seconds`, `adclick_verdicts_total`; `stage` values `collector, ingest, realtime, finalize, query`) in shared/src/main/kotlin/dev/jakeshj/adclick/shared/Metrics.kt
- [ ] T019 [P] Configure JSON structured logging with `event_id` MDC key in shared/src/main/resources/logback-adclick.xml
- [ ] T020 [P] Create Avro serde configuration for Apicurio (auto-register off, use registered schemas) in shared/src/main/kotlin/dev/jakeshj/adclick/shared/AvroSerdes.kt
- [ ] T021 [P] Create shared Testcontainers fixtures (Kafka, PostgreSQL, Apicurio) as Kotest project extensions in shared/src/testFixtures/kotlin/dev/jakeshj/adclick/shared/test/Containers.kt
- [ ] T022 [P] Implement rule parameters per `rule_version` loaded from YAML (v1: impression validity 24h, re-click interval 10s, bot UA list, internal IP CIDRs), exposed as the `rules` module API in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/rules/RuleParameters.kt and pipeline/src/main/resources/rules/v1.yml
- [ ] T023 Write Flyway migration for `advertiser`, `ad`, `impression`, `click`, `click_verdict`, `daily_ad_report`, `minute_ad_aggregate`, `quarantined_event`, `demo_scenario_run` exactly as data-model.md: `click.impression_id` nullable with NO FK, `click_verdict` PK `(click_id, rule_version)`, `validity` IN ('VALID','INVALID'), `reason` IN ('NO_IMPRESSION','STALE_IMPRESSION','DUPLICATE','NON_HUMAN','AD_MISMATCH'), `daily_ad_report` PK `(ad_id, report_date, revision)` with "revision_reason NOT NULL when revision ≥ 2", partial unique index allowing at most one `demo_scenario_run` with status IN ('RUNNING','RECOVERING'), indexes `click(impression_id, clicked_at, click_id)` and `click(ad_id, clicked_at)`, and `COMMENT ON COLUMN click.ip/click.user_agent IS 'PII'` in pipeline/src/main/resources/db/migration/V1__init.sql
- [ ] T024 Write demo seed migration (3 advertisers with `Asia/Seoul`, `UTC`, `America/New_York`; 30 ads with landing URLs; API key hashes for .env.demo keys), applied only under the `demo` profile via `spring.flyway.locations`, in pipeline/src/main/resources/db/demo/V100__demo_seed.sql
- [ ] T025 Add Flyway, JDBC, PostgreSQL, Spring Kafka, Kafka Streams and Avro/Apicurio dependencies to the apps (`collector`: Kafka + JDBC read-only; `pipeline`: all) in collector/build.gradle.kts and pipeline/build.gradle.kts

**Checkpoint**: 스키마·DB·공통 코드 준비 완료. T013~T015와 ModularityTest가 통과해야 한다.

---

## Phase 3: User Story 1 - 클릭을 유실·중복 없이 측정·저장 (Priority: P1) 🎯 MVP

**Goal**: 클릭을 측정해 click_id를 발급하고 랜딩으로 보내며, Kafka를 거쳐 PostgreSQL에 정확히 한 번 저장한다.

**Independent Test**: 중복 전송·브로커 재시작·컨슈머 강제 종료를 섞어 보낸 뒤, 저장된 고유 클릭 수 = 보낸 고유
클릭 수(SC-001). 형식 오류 이벤트는 `quarantined_event`에 남는다.

### Tests for User Story 1 ⚠️

> 먼저 쓰고, 실패하는 것을 확인한 뒤 구현한다

- [ ] T026 [P] [US1] Contract test for `GET /c/{adId}` (302 + `Location` = ad landing_url + `X-Click-Id` UUID, 404 for unknown ad, `imp` optional) and `POST /impressions` (202, 400 on missing `impressionId`/`adId`/`servedAt`) per contracts/collector-api.yaml in collector/src/test/kotlin/dev/jakeshj/adclick/collector/CollectorContractTest.kt
- [ ] T027 [P] [US1] Integration test: the 302 is returned only after the click record is acknowledged on `clicks.v1`, and the record matches click.avsc in collector/src/integrationTest/kotlin/dev/jakeshj/adclick/collector/PublishBeforeRedirectTest.kt
- [ ] T028 [P] [US1] Integration test: with Kafka stopped, clicks still redirect, are written to the disk buffer, and are all published after Kafka restarts (no loss) in collector/src/integrationTest/kotlin/dev/jakeshj/adclick/collector/DiskBufferRecoveryTest.kt
- [ ] T029 [P] [US1] Property-based integration test `NoLossNoDuplicate`: random click sets with random duplicate re-sends, consumer stop/restart and broker restart → stored unique clicks == sent unique clicks and `deduplicated_total` == duplicates sent in pipeline/src/integrationTest/kotlin/dev/jakeshj/adclick/pipeline/ingest/NoLossNoDuplicateTest.kt
- [ ] T030 [P] [US1] Integration test: a record failing deserialization or exceeding retries lands in `quarantined_event` with `reason` `SCHEMA_INVALID` or `RETRY_EXHAUSTED` and the original payload in pipeline/src/integrationTest/kotlin/dev/jakeshj/adclick/pipeline/ingest/QuarantineTest.kt
- [ ] T031 [P] [US1] Integration test: impressions are stored once per `impression_id` and `received_at` is set in pipeline/src/integrationTest/kotlin/dev/jakeshj/adclick/pipeline/ingest/ImpressionIngestTest.kt

### Implementation for User Story 1

- [ ] T032 [P] [US1] Implement read-only ad lookup cache (ad_id → landing_url, advertiser_id from `ad` table; refresh every 60s) in collector/src/main/kotlin/dev/jakeshj/adclick/collector/AdCatalog.kt
- [ ] T033 [P] [US1] Implement idempotent Kafka publisher (`enable.idempotence=true`, `acks=all`, synchronous ack wait with timeout, key = impressionId else clickId) in collector/src/main/kotlin/dev/jakeshj/adclick/collector/EventPublisher.kt
- [ ] T034 [P] [US1] Implement append-only disk buffer with fsync and background replayer that republishes and truncates only after ack in collector/src/main/kotlin/dev/jakeshj/adclick/collector/DiskBuffer.kt
- [ ] T035 [US1] Implement `GET /c/{adId}`: issue UUIDv7 `clickId` and `clickedAt`, capture `ip`/`userAgent`, publish (fallback to DiskBuffer), then 302 with `X-Click-Id` in collector/src/main/kotlin/dev/jakeshj/adclick/collector/ClickController.kt
- [ ] T036 [US1] Implement `POST /impressions` with request validation; invalid payloads are sent to `events.quarantine.v1` with reason `SCHEMA_INVALID` in collector/src/main/kotlin/dev/jakeshj/adclick/collector/ImpressionController.kt
- [ ] T037 [US1] Add collector metrics (`received_total{stage=collector,type}`, `retried_total`, `quarantined_total`) and `event_id` logging in collector/src/main/kotlin/dev/jakeshj/adclick/collector/CollectorMetrics.kt
- [ ] T038 [P] [US1] Implement batch JDBC writer: multi-row `INSERT ... ON CONFLICT (click_id) DO NOTHING` returning inserted count; `deduplicated = batch size − inserted` in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/ingest/ClickWriter.kt
- [ ] T039 [P] [US1] Implement batch JDBC writer for impressions with `ON CONFLICT (impression_id) DO NOTHING` in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/ingest/ImpressionWriter.kt
- [ ] T040 [US1] Implement batch Kafka listeners for `clicks.v1` and `impressions.v1` with manual offset commit after DB write, `DefaultErrorHandler` (exponential backoff, max 5 retries) and `DeadLetterPublishingRecoverer` to `events.quarantine.v1` in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/ingest/IngestListeners.kt
- [ ] T041 [US1] Implement quarantine consumer storing `source_topic`, raw `payload`, `reason`, `error_detail` into `quarantined_event` in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/ingest/QuarantineListener.kt
- [ ] T042 [US1] Add ingest metrics (`stored_total`, `deduplicated_total{stage=ingest}`, `consumer_lag{stage=ingest}`) in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/ingest/IngestMetrics.kt
- [ ] T043 [US1] Add `collector` and `pipeline` app services (Dockerfile-less Spring Boot buildpack image or `bootJar` + JRE image, depends_on infra health) to docker-compose.yml

**Checkpoint**: US1 단독으로 동작. T026~T031 통과, quickstart 2번 확인 가능.

---

## Phase 4: User Story 2 - 클릭 유효성 판정과 일별 확정 리포트 (Priority: P2)

**Goal**: 24시간 유예가 지난 클릭을 규칙 버전별로 한 번 판정하고, 광고주 시간대 기준 일별 확정 리포트와 개정본을 만든다.

**Independent Test**: PostgreSQL에 사유별 픽스처를 직접 넣고 finalize 작업을 실행해, 판정·일별 합계·
`valid + invalid = 총수`·늦은 클릭 개정본을 확인한다(SC-004, SC-005). US1 없이도 검증 가능.

### Tests for User Story 2 ⚠️

- [ ] T044 [P] [US2] Integration test for rule order `AD_MISMATCH → NO_IMPRESSION → STALE_IMPRESSION → DUPLICATE → NON_HUMAN → VALID` with one fixture per reason in pipeline/src/integrationTest/kotlin/dev/jakeshj/adclick/pipeline/finalize/VerdictRulesTest.kt
- [ ] T045 [P] [US2] Integration test: `DUPLICATE` uses the previous click on the same impression by `(clicked_at, click_id)` regardless of its verdict, independent of insert order in pipeline/src/integrationTest/kotlin/dev/jakeshj/adclick/pipeline/finalize/DuplicateOrderingTest.kt
- [ ] T046 [P] [US2] Integration test: clicks younger than 24h are not judged; re-running the job inserts no new `click_verdict` rows and yields identical reports (FR-024) in pipeline/src/integrationTest/kotlin/dev/jakeshj/adclick/pipeline/finalize/IdempotentFinalizeTest.kt
- [ ] T047 [P] [US2] Integration test: daily report day boundaries follow advertiser `time_zone` and `time_zone_effective_from`; every FINAL row satisfies `valid_clicks + invalid_clicks` = clicks measured that day and `invalid_by_reason` sums to `invalid_clicks` in pipeline/src/integrationTest/kotlin/dev/jakeshj/adclick/pipeline/finalize/DailyReportTest.kt
- [ ] T048 [P] [US2] Integration test: a late click for an already FINAL date creates revision n+1 with non-null `revision_reason` and leaves revision n unchanged in pipeline/src/integrationTest/kotlin/dev/jakeshj/adclick/pipeline/finalize/LateClickRevisionTest.kt
- [ ] T049 [P] [US2] Integration test: a new `rule_version` re-judges clicks and keeps the previous version's verdicts in pipeline/src/integrationTest/kotlin/dev/jakeshj/adclick/pipeline/finalize/RuleVersionTest.kt

### Implementation for User Story 2

- [ ] T050 [P] [US2] Implement verdict SQL: `INSERT INTO click_verdict ... SELECT` with `LAG(clicked_at) OVER (PARTITION BY impression_id ORDER BY clicked_at, click_id)`, parameters from the `rules` module, `ON CONFLICT DO NOTHING`, limited to clicks with `clicked_at <= now() - grace` in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/finalize/VerdictJob.kt
- [ ] T051 [US2] Implement daily FINAL report: for each (ad, local date) whose end + grace has passed and no FINAL exists, insert revision 1 with counts and `invalid_by_reason` in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/finalize/DailyReportJob.kt
- [ ] T052 [US2] Implement revision detection: compare stored FINAL counts with recomputed counts; on difference insert revision n+1 with `revision_reason` = "late clicks: +N" in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/finalize/RevisionJob.kt
- [ ] T053 [US2] Implement scheduler running VerdictJob → DailyReportJob → RevisionJob every 10 minutes (single-run lock via `pg_try_advisory_lock`); grace from `adclick.finalize.grace` (default PT24H, demo profile PT5M so revisions are visible in a demo) in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/finalize/FinalizeScheduler.kt
- [ ] T054 [US2] Add metrics `verdicts_total{path=final,validity,reason}` and job duration in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/finalize/FinalizeMetrics.kt

**Checkpoint**: US2 단독 동작. T044~T049 통과, quickstart 3번 확인 가능.

---

## Phase 5: User Story 3 - 광고주 준실시간 조회 (Priority: P3)

**Goal**: Kafka Streams가 1분 윈도우로 잠정 집계하고, 광고주가 API 키로 최근 M분·상위 N·일별 수치를 조회한다.

**Independent Test**: TopologyTestDriver로 윈도우·유예·중복 제거를 검증하고, `minute_ad_aggregate`·`daily_ad_report`
픽스처로 조회 API의 응답·격리·시간대를 검증한다(SC-007~SC-009).

### Tests for User Story 3 ⚠️

- [ ] T055 [P] [US3] Topology test: duplicate `clickId` within 24h counted once; 1-minute tumbling windows by `clickedAt`; record arriving within 15s grace is counted, after grace is dropped and counted in `late_dropped_total` in pipeline/src/test/kotlin/dev/jakeshj/adclick/pipeline/realtime/WindowingTopologyTest.kt
- [ ] T056 [P] [US3] Topology test: provisional verdicts — no impression yet → `pendingClicks`; stale/duplicate/non-human/ad-mismatch → `invalidByReason`; and `valid + invalid + pending` = clicks in window in pipeline/src/test/kotlin/dev/jakeshj/adclick/pipeline/realtime/ProvisionalVerdictTest.kt
- [ ] T057 [P] [US3] Integration test: aggregate sink upserts `minute_ad_aggregate` so replaying the same records yields identical rows in pipeline/src/integrationTest/kotlin/dev/jakeshj/adclick/pipeline/realtime/AggregateSinkTest.kt
- [ ] T058 [P] [US3] Contract tests for `/v1/ads/{adId}/clicks/recent` (minutes 1..1440), `/v1/ads/top` (limit ≤ 100, default 10, sorted by validClicks desc) and `/v1/ads/{adId}/clicks/daily` per contracts/query-api.yaml in pipeline/src/test/kotlin/dev/jakeshj/adclick/pipeline/query/AdvertiserApiContractTest.kt
- [ ] T059 [P] [US3] Integration test: missing/invalid `X-API-Key` → 401; another advertiser's ad → 404 on every endpoint; top-N never includes other advertisers' ads in pipeline/src/integrationTest/kotlin/dev/jakeshj/adclick/pipeline/query/AdvertiserIsolationTest.kt
- [ ] T060 [P] [US3] Integration test: daily endpoint returns latest revision FINAL for finalized dates and PRELIMINARY sums of `minute_ad_aggregate` for others, using advertiser time zone boundaries in pipeline/src/integrationTest/kotlin/dev/jakeshj/adclick/pipeline/query/DailyClicksTest.kt

### Implementation for User Story 3

- [ ] T061 [P] [US3] Implement provisional judge using the `rules` module (same rule order as final path, no-impression → pending) in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/realtime/ProvisionalJudge.kt
- [ ] T062 [US3] Implement Kafka Streams topology: dedup store (24h retention) on `clicks.v1`, impression state store from `impressions.v1`, last-click-per-impression store, 1-min tumbling window with 15s grace, emit cumulative MinuteAdAggregate to `minute-ad-aggregates.v1`, `processing.guarantee=exactly_once_v2` in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/realtime/AggregationTopology.kt
- [ ] T063 [US3] Implement aggregate sink consumer: `INSERT ... ON CONFLICT (ad_id, window_start) DO UPDATE` in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/realtime/AggregateSink.kt
- [ ] T064 [US3] Add realtime metrics (`verdicts_total{path=realtime}`, `end_to_end_latency_seconds{path=realtime}` from `clickedAt` to upsert time, `consumer_lag{stage=realtime}`, `late_dropped_total`) in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/realtime/RealtimeMetrics.kt
- [ ] T065 [P] [US3] Implement API key authentication filter (hash compare against `advertiser.api_key_hash`, resolves advertiser_id; applies to `/v1/**` only) in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/query/ApiKeyFilter.kt
- [ ] T066 [P] [US3] Implement repositories that always filter by `advertiser_id` for minute aggregates, top-N and daily reports in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/query/AdvertiserReadRepository.kt
- [ ] T067 [US3] Implement controllers for recent, top and daily endpoints with response shapes from contracts/query-api.yaml in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/query/AdvertiserController.kt

**Checkpoint**: US3 단독 동작. T055~T060 통과, quickstart 4번 확인 가능.

---

## Phase 6: User Story 4 - 시스템 설명 실시간 시각화 (포트폴리오 데모) (Priority: P4)

**Goal**: 로컬 데모에서 흐름·지표·대조식·설계 설명을 보여주고, 데모 시나리오 3종을 실행할 수 있다.

**Independent Test**: `make demo-up` 후 화면에서 수치가 5초 안에 갱신되고, 시나리오마다 10초 안에 효과가 보이며
끝난 뒤 대조식이 balanced(SC-010~SC-012).

### Tests for User Story 4 ⚠️

- [ ] T068 [P] [US4] Test: only one scenario runs at a time (second request → 409 with the running scenario), status goes `RUNNING → RECOVERING → DONE`, and auto-recovery fires at `ends_at` in pipeline/src/integrationTest/kotlin/dev/jakeshj/adclick/pipeline/demo/ScenarioServiceTest.kt
- [ ] T069 [P] [US4] Test: snapshot assembler maps Prometheus results to demo-stream.md `snapshot` JSON and computes `balanced` = `received == stored + deduplicated + quarantined + inFlight` in pipeline/src/test/kotlin/dev/jakeshj/adclick/pipeline/demo/SnapshotAssemblerTest.kt
- [ ] T070 [P] [US4] Test: collector fault-injection endpoints (duplicate publish ×2–3, delayed publish N minutes) exist only under the `demo` profile in collector/src/test/kotlin/dev/jakeshj/adclick/collector/demo/FaultInjectionProfileTest.kt
- [ ] T071 [P] [US4] Test: `ingest` module pause/resume API stops consumption and resumes without loss in pipeline/src/integrationTest/kotlin/dev/jakeshj/adclick/pipeline/ingest/PauseResumeTest.kt
- [ ] T072 [P] [US4] Web tests: stages render from a snapshot fixture; unbalanced reconciliation shows a warning; scenario buttons are disabled while a scenario runs; stage explanation shows on hover/click in web/src/App.test.tsx

### Implementation for User Story 4

- [ ] T073 [P] [US4] Implement demo-profile fault injection controller (`POST /internal/demo/duplicate`, `POST /internal/demo/delay`) wired into EventPublisher in collector/src/main/kotlin/dev/jakeshj/adclick/collector/demo/FaultInjectionController.kt
- [ ] T074 [P] [US4] Expose `IngestControl` (pause/resume of the ingest listener containers) as the `ingest` module's public API in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/ingest/IngestControl.kt
- [ ] T075 [P] [US4] Implement Prometheus HTTP API client (instant queries for rates, p95 latency, lag, counters) in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/demo/PrometheusClient.kt
- [ ] T076 [US4] Implement snapshot assembler producing demo-stream.md `snapshot` in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/demo/SnapshotAssembler.kt
- [ ] T077 [US4] Implement scenario service persisting `demo_scenario_run`; DUPLICATE_BURST 60s and LATE_ARRIVAL delay 2m call collector internal endpoints, STAGE_OUTAGE 30s calls `IngestControl` in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/demo/ScenarioService.kt
- [ ] T078 [US4] Implement `GET /demo/stream` (SSE, `snapshot` every 1s, `scenario` on change) and `POST /demo/scenarios/{scenario}` (202/409), demo profile only, in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/demo/DemoController.kt
- [ ] T079 [P] [US4] Implement synthetic load generator for the demo profile (configurable rate, impressions then clicks via collector HTTP, mix of valid/stale/duplicate/bot/no-impression traffic) in pipeline/src/main/kotlin/dev/jakeshj/adclick/pipeline/demo/LoadGenerator.kt
- [ ] T080 [P] [US4] Implement SSE client hook typed from demo-stream.md in web/src/api/useDemoStream.ts
- [ ] T081 [P] [US4] Implement SVG flow diagram (collector → Kafka → ingest/realtime → PostgreSQL → finalize/query) with per-stage rate, latency and backlog in web/src/components/FlowDiagram.tsx
- [ ] T082 [P] [US4] Implement stage explanations text (멱등 키 중복 제거, 늦은 도착 유예, 사실·판정 분리, 잠정→확정, 앱 2개 + 모듈 구조 이유) in web/src/content/stageExplanations.ts
- [ ] T083 [P] [US4] Implement reconciliation panel with balanced/unbalanced state in web/src/components/ReconciliationPanel.tsx
- [ ] T084 [P] [US4] Implement verdict distribution panel (valid/pending/invalid by reason) in web/src/components/VerdictPanel.tsx
- [ ] T085 [P] [US4] Implement scenario panel (three buttons, running state, countdown) calling `POST /demo/scenarios/{scenario}` in web/src/components/ScenarioPanel.tsx
- [ ] T086 [US4] Compose the page layout from the components in web/src/App.tsx
- [ ] T087 [US4] Add `web` service and set `SPRING_PROFILES_ACTIVE=demo` for `collector` and `pipeline` in docker-compose.yml

**Checkpoint**: 모든 스토리 동작. `make demo-up`으로 quickstart 5번 확인 가능.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T088 [P] Implement Gatling Kotlin simulation: 10,000 clicks/s sustained 30 min; assertions redirect p95 ≤ 1s, p99 ≤ 2s, 0 failed requests in loadtest/src/gatling/kotlin/dev/jakeshj/adclick/loadtest/ClickLoadSimulation.kt
- [ ] T089 Run load test, verify SC-002/SC-003/SC-009 (realtime latency histogram p95 ≤ 2m, p99 ≤ 5m) and record results and tuning notes (including whether `realtime` needs its own process, research.md 16) in specs/001-ad-click-pipeline/load-test-results.md
- [ ] T090 [P] Write portfolio-facing README (architecture diagram, realtime vs final paths, 2 apps + Modulith modules with generated module docs, guarantees, how to run the demo) in README.md
- [ ] T091 [P] Add CI step that fails when an `.avsc` change is not BACKWARD compatible (runs T013 test against history) in .github/workflows/ci.yml
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
- **US4 (P4)**: US1~US3 전부 필요(보여줄 지표와 장애 주입 대상이 그 모듈들)

### Within Each User Story

- 테스트 먼저 작성 → 실패 확인 → 구현 (헌법 V)
- 저장·발행 같은 하위 구성요소 → 컨트롤러·리스너 → 지표 → compose 서비스 추가
- `pipeline` 모듈끼리는 각 모듈의 공개 API로만 의존한다(ModularityTest가 검증). 예: `demo` → `ingest.IngestControl`
- docker-compose.yml을 고치는 작업(T043, T087)은 같은 파일이라 병렬 불가

### Parallel Opportunities

- Foundational: 테스트 T013~T015, 구현 T016~T022
- 스토리 안: 각 스토리의 테스트 전부, 그리고 서로 다른 파일의 하위 구성요소
- Foundational 이후 US1·US2·US3은 동시에 진행 가능(서로 다른 Modulith 모듈 패키지)

---

## Parallel Example: User Story 1

```bash
# 테스트 먼저 (동시에):
Task: "Contract test ... in collector/src/test/kotlin/dev/jakeshj/adclick/collector/CollectorContractTest.kt"
Task: "Integration test ... in collector/src/integrationTest/kotlin/dev/jakeshj/adclick/collector/DiskBufferRecoveryTest.kt"
Task: "Property-based test ... in pipeline/src/integrationTest/kotlin/dev/jakeshj/adclick/pipeline/ingest/NoLossNoDuplicateTest.kt"

# 하위 구성요소 (동시에):
Task: "AdCatalog.kt", "EventPublisher.kt", "DiskBuffer.kt", "ingest/ClickWriter.kt", "ingest/ImpressionWriter.kt"
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
