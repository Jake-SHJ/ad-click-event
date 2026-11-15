# Implementation Plan: 광고 클릭 수집·집계·준실시간 서빙 파이프라인

**Branch**: `001-ad-click-pipeline` | **Date**: 2026-11-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-ad-click-pipeline/spec.md`

## Summary

광고 클릭을 측정해 Kafka에 유실 없이 기록하고, 두 경로로 처리한다. **실시간 경로**는 Kafka Streams가
1분 텀블링 윈도우(유예 15초)로 잠정 판정·집계해 광고주 조회 API에 몇 분 안에 반영한다. **확정 경로**는
PostgreSQL에 append-only로 저장된 사실을 24시간 유예 후 SQL로 판정해 일별 확정 리포트(개정본 포함)를 만든다.
로컬 Docker Compose 데모에서 React 시각화 화면이 SSE로 단계별 지표·대조식을 보여주고, 데모 시나리오
(중복 폭주·늦은 도착·단계 장애)를 실행할 수 있다.

배포 단위는 **앱 2개**다. 지연 목표가 있는 수집 서버 `collector`는 단독으로 두고, 나머지 단계(저장·실시간
집계·확정·조회·데모)는 **Spring Modulith** 앱 `pipeline` 하나의 모듈로 묶어 구조 복잡도를 줄인다.
결정 근거는 [research.md](./research.md).

## Technical Context

**Language/Version**: Kotlin 2.x on JDK 21 (백엔드), TypeScript (웹 화면)

**Primary Dependencies**: Spring Boot(MVC + 가상 스레드), Spring Modulith, Spring Kafka, Kafka Streams,
Apache Avro + Apicurio Registry serdes, Micrometer(Prometheus), Flyway(스키마 마이그레이션), React + Vite

**Storage**: PostgreSQL (사실·판정·리포트·1분 집계·운영 기록), Kafka(이벤트 로그·스트림 상태)

**Testing**: Kotest + MockK, Testcontainers(Kafka·PostgreSQL·Apicurio), Kafka Streams TopologyTestDriver,
Gatling Kotlin DSL(부하), Vitest + Testing Library(웹)

**Target Platform**: 로컬 Docker Compose (macOS 노트북 한 대, Docker 메모리 8GB)

**Project Type**: 백엔드 앱 2개(collector + Spring Modulith pipeline, Gradle 멀티 모듈) + 웹 화면

**Performance Goals**: 초당 클릭 10,000건 30분 지속(SC-002), 리다이렉트 p95 ≤ 1초·p99 ≤ 2초(SC-003),
측정 → 조회 반영 p95 ≤ 2분·p99 ≤ 5분(SC-009), 광고주 조회 p95 ≤ 1초(SC-007), 시각화 반영 ≤ 5초(SC-010)

**Constraints**: 유실 0건·중복 반영 0건, 사실 append-only, 판정은 (click_id, rule_version)당 하나,
확정 리포트 불변, 백엔드 Kotlin만·웹 TypeScript만, 외부 공개·호스팅 없음

**Scale/Scope**: 광고주 3·광고 30(시드), 30분 부하 시 클릭 약 1,800만 건, 앱 2개(pipeline 안 모듈 6개) + 웹 1개

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 설계에서 지키는 방법 | 결과 |
|------|----------------------|------|
| I. 이벤트 무손실 | collector는 Kafka `acks=all` 확인 후 302(실패 시 로컬 디스크 버퍼 후 재발행). 컨슈머는 저장 후 오프셋 커밋. 재시도 초과 → `events.quarantine.v1` → `quarantined_event`. 대조식 지표 | PASS |
| II. 중복 제거·멱등성 | `click_id`(UUIDv7) PK + `ON CONFLICT DO NOTHING`, Streams 24시간 중복 제거 저장소, 집계는 누적 값 upsert, 판정 PK (click_id, rule_version). 중복 판단 기간: 저장은 영구, 실시간은 24시간 | PASS |
| III. 스키마 계약 | `.avsc` 저장소 관리 + Apicurio 호환성 규칙(BACKWARD), CI 호환성 검사, 수집 시 검증 실패 → 격리 | PASS |
| IV. 개인정보 최소수집 | `ip`·`user_agent`를 스키마·DDL에 PII 표시. 합성 데이터만 사용(로컬 데모) | PASS |
| V. 테스트 우선 | tasks에서 각 스토리 테스트를 구현보다 먼저 배치. 유실(장애 주입)·중복(속성 테스트)·스키마 호환 전용 테스트 | PASS (tasks에서 확인) |
| VI. 관측성 | JSON 로그 + `event_id`, data-model의 단계별 지표 8종 | PASS |
| VII. 성능 목표 | Performance Goals에 수치 명시, Gatling으로 검증·리포트 보존 | PASS |
| VIII. 단순성 | 앱은 2개로 최소화(나머지 단계는 Modulith 모듈). 인프라 4종(Kafka·Apicurio·PostgreSQL·Prometheus)과 앱 분리 → Complexity Tracking에 정당화 | PASS (정당화 있음) |
| 기술 제약 | 백엔드 Kotlin, 웹 TypeScript, 웹은 `/demo/stream`·조회 API만 읽음 | PASS |
| 개발 흐름 | 기능 브랜치 + PR + GitHub Actions(빌드·테스트·호환성 검사) | PASS |

**Post-design re-check (Phase 1 이후)**: data-model·contracts 작성 후 다시 확인함. ERD 대비 변경(FK 미적용,
`AD_MISMATCH` 사유, PRELIMINARY 행 미저장 등)은 모두 불변식을 강화하거나 단순화하는 방향이며 새 위반 없음.
웹 화면은 판정·집계를 재구현하지 않고 서버가 계산한 `balanced`를 표시한다. **결과: PASS.**

## Project Structure

### Documentation (this feature)

```text
specs/001-ad-click-pipeline/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── collector-api.yaml
│   ├── query-api.yaml
│   ├── demo-stream.md
│   └── events/
│       ├── click.avsc
│       ├── impression.avsc
│       └── minute-ad-aggregate.avsc
└── tasks.md             # /speckit-tasks 에서 생성
```

### Source Code (repository root)

```text
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml
build-logic/                   # 컨벤션 플러그인: kotlin-library, spring-service, integration-test
Makefile                       # demo-up / demo-down / demo-clean / infra-up
docker-compose.yml             # kafka, apicurio, postgres, prometheus + collector + pipeline + web
infra/                         # Kafka 토픽 생성, Apicurio 전역 규칙, Prometheus 수집 설정
.github/workflows/ci.yml

shared/                        # 두 앱 공용: Avro .avsc + 생성 코드·호환성 테스트, 지표 이름, UUIDv7, 로깅 설정,
                               #   Avro serde 설정, testFixtures(Testcontainers 픽스처)
collector/                     # 앱 1: 클릭 리다이렉트·노출 수신 → Kafka (데모 장애 주입: 중복 발행·지연 발행)
pipeline/                      # 앱 2: Spring Modulith
  src/main/kotlin/dev/jakeshj/adclick/pipeline/
    rules/                     #   판정 규칙 파라미터(rule_version별), realtime·finalize 공용
    ingest/                    #   Kafka → PostgreSQL 사실 저장, 격리 저장, 일시정지 제어
    realtime/                  #   Kafka Streams: 중복 제거·잠정 판정·1분 윈도우 → PostgreSQL upsert
    finalize/                  #   24시간 유예 후 확정 판정·일별 리포트·개정본
    query/                     #   광고주 조회 API
    demo/                      #   /demo/stream(SSE)·시나리오 제어·합성 부하 생성(demo 프로필)
  src/main/resources/db/migration/   # Flyway(모든 테이블). pipeline이 스키마의 주인
  src/main/resources/db/demo/        # 데모 시드(demo 프로필에서만 적용)
loadtest/                      # Gatling Kotlin 시뮬레이션
web/                           # React + Vite + TypeScript 시각화 화면

# 각 Kotlin 모듈: src/main/kotlin, src/test/kotlin(단위), src/integrationTest/kotlin(Testcontainers)
# web: src/, src/**/*.test.tsx
```

**Structure Decision**: 앱 2개 + Spring Modulith. 부하 격리가 꼭 필요한 `collector`(초당 1만 건, 리다이렉트
p95 ≤ 1초)만 별도 프로세스로 두고, 나머지 단계는 `pipeline` 한 앱의 모듈로 묶는다. 모듈 경계는
`ModularityTest`(Spring Modulith `verify()`)가 강제하고, 모듈 구조 문서도 생성한다. 단계 간 데이터 흐름은
Kafka와 PostgreSQL을 통하므로, 책의 구조(수집 → 큐 → 집계 → 저장 → 조회)는 모듈 단위로 그대로 드러난다.
"단계 장애" 시나리오는 프로세스를 죽이는 대신 `ingest` 모듈의 컨슈머를 일시정지해 재현한다.
DB에 쓰는 앱이 `pipeline` 하나라 마이그레이션도 `pipeline`이 갖고, `collector`는 `ad` 테이블만 읽는다.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| 인프라 Kafka 추가 | 사용자 지정. 수집과 처리를 분리해 장애 시 적체로 버티고(무손실), 실시간 스트림 처리의 입력이 됨 | PostgreSQL 직접 적재만으로는 초당 1만 건 수집과 스트림 윈도우 집계를 동시에 감당하기 어렵고, 장애 동안 이벤트를 쌓아 둘 곳이 없음 |
| 인프라 Apicurio Registry 추가 | 헌법 III의 하위 호환 자동 검사와 메시지별 스키마 버전 식별 | 스키마 파일만 두면 실행 중 호환성 검사와 버전 식별을 직접 구현해야 함 |
| 인프라 Prometheus 추가 | 헌법 VI의 단계별 지표 수집, 시각화 화면의 수치 출처 | 서비스마다 지표를 직접 모으면 수집·보존 로직을 새로 만들어야 함 |
| 앱 2개(collector + pipeline) | collector의 리다이렉트 지연 목표(SC-003)를 Kafka Streams 집계·확정 작업의 CPU 사용과 격리 | 단일 앱으로 합치면 초당 1만 건 수집과 스트림 처리가 같은 JVM 자원을 다퉈 리다이렉트 지연이 흔들림. 반대로 단계마다 앱을 나누면(이전 안: 6개) 설정·배포·지표 대상만 늘고 얻는 격리 이득이 없음 |
| collector 로컬 디스크 버퍼 | Kafka 장애 중에도 사용자를 랜딩 페이지로 보내면서 유실 0건 유지 | 장애 시 오류 응답을 주면 사용자 경험이 깨지고, 버퍼 없이 302를 주면 헌법 I(기록 전 성공 응답 금지) 위반 |
