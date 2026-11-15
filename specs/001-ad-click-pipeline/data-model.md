# Data Model: 광고 클릭 수집·집계·준실시간 서빙 파이프라인

**Feature**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

기준은 "광고 클릭 집계 · 경량 도메인 ERD"(엔티티 6개)이며, 검토에서 나온 보완점을 반영했다.
ERD와 달라진 점은 맨 아래 [ERD 대비 변경](#erd-대비-변경)에 모았다. 저장소는 PostgreSQL 하나다.

## 도메인 엔티티

### advertiser (참조)

| 필드 | 타입 | 규칙 |
|------|------|------|
| advertiser_id | bigint | PK |
| time_zone | text | IANA 시간대(예: `Asia/Seoul`). 일 경계 기준 |
| time_zone_effective_from | date | 현재 시간대가 적용되기 시작한 날짜. 그 이전 날짜의 확정 리포트는 다시 만들지 않음 |
| api_key_hash | text | 광고주 API 키의 해시. 원본 키는 저장하지 않음 |

### ad (참조)

| 필드 | 타입 | 규칙 |
|------|------|------|
| ad_id | bigint | PK |
| advertiser_id | bigint | FK → advertiser. NOT NULL |
| landing_url | text | 클릭 후 이동할 주소. NOT NULL |

### impression (사실, append-only)

| 필드 | 타입 | 규칙 |
|------|------|------|
| impression_id | uuid | PK. 같은 ID 재전달은 무시(`ON CONFLICT DO NOTHING`) |
| ad_id | bigint | FK → ad. NOT NULL |
| served_at | timestamptz | 노출 시각. NOT NULL |
| received_at | timestamptz | 저장 시각. 운영 기록(지연 측정·늦은 도착 판단)용, 판정 규칙에는 안 씀 |

### click (사실, append-only)

| 필드 | 타입 | 규칙 |
|------|------|------|
| click_id | uuid (v7) | PK. 측정 시 발급. 같은 ID 재전달은 무시 |
| ad_id | bigint | FK → ad. NOT NULL |
| impression_id | uuid | nullable. **FK 제약을 걸지 않음**(노출이 늦게 도착할 수 있음). 참조 무결성은 판정 시 검사 |
| ip | inet | **개인정보 필드** (헌법 IV). 현재는 원본 저장 |
| user_agent | text | **개인정보 필드** (헌법 IV). 현재는 원본 저장 |
| clicked_at | timestamptz | 측정 시각(302 응답 시각). 날짜 귀속 기준. NOT NULL |
| received_at | timestamptz | 저장 시각. 운영 기록용 |

인덱스: `(impression_id, clicked_at, click_id)` — 중복 클릭 판정, `(ad_id, clicked_at)` — 일별 확정 집계.

검증 규칙:
- 노출이 있으면 `click.ad_id = impression.ad_id` (불일치 클릭은 판정에서 무효 / `AD_MISMATCH`로 처리, 아래 참조).

### click_verdict (판정, append-only)

| 필드 | 타입 | 규칙 |
|------|------|------|
| click_id | uuid | PK, FK → click |
| rule_version | text | PK. 예: `v1` |
| validity | text | `VALID` \| `INVALID` |
| reason | text | INVALID일 때만: `NO_IMPRESSION` \| `STALE_IMPRESSION` \| `DUPLICATE` \| `NON_HUMAN` \| `AD_MISMATCH` |

- 확정 경로(`pipeline`의 `finalize` 모듈)만 기록한다. 긴 유예(24시간)가 지난 클릭만 판정하므로 한 번 기록한 판정은 바뀌지 않는다.
- 규칙 평가 순서: `AD_MISMATCH` → `NO_IMPRESSION` → `STALE_IMPRESSION` → `DUPLICATE` → `NON_HUMAN` → `VALID`.
- 중복 클릭의 "직전 클릭"은 판정 결과와 관계없이 같은 노출에서 `(clicked_at, click_id)` 순서상 바로 앞 클릭.

### daily_ad_report (약속)

| 필드 | 타입 | 규칙 |
|------|------|------|
| ad_id | bigint | PK, FK → ad |
| report_date | date | PK. 광고주 시간대 기준 날짜 |
| revision | int | PK. 1부터 시작 |
| status | text | `FINAL` (아래 상태 전이 참조) |
| rule_version | text | 적용한 판정 규칙 버전 |
| valid_clicks | bigint | |
| invalid_clicks | bigint | |
| invalid_by_reason | jsonb | 사유별 무효 수. 합계 = invalid_clicks |
| revision_reason | text | revision ≥ 2일 때 NOT NULL |
| created_at | timestamptz | |

불변식: `valid_clicks + invalid_clicks` = 그날 측정된 클릭 총수 (FR-015). 행은 수정·삭제하지 않는다.

상태 전이:

```text
(오늘·유예 중) ──조회 시 1분 집계 합산──▶ 잠정치(저장 안 함, API가 "PRELIMINARY"로 표시)
       │
       └─ 날짜 종료 + 24시간 ─▶ revision 1, FINAL 기록
                                   │
                                   └─ 확정 후 그 날짜의 늦은 클릭 발견 ─▶ revision n+1, FINAL, revision_reason 기록
```

## 실시간 경로 읽기 모델

### minute_ad_aggregate

| 필드 | 타입 | 규칙 |
|------|------|------|
| ad_id | bigint | PK |
| window_start | timestamptz | PK. 1분 경계(UTC) |
| advertiser_id | bigint | 조회 시 광고주 격리용 |
| valid_clicks | bigint | 잠정 판정 기준 |
| invalid_clicks | bigint | 잠정 판정 기준 |
| invalid_by_reason | jsonb | |
| pending_clicks | bigint | 아직 노출이 도착하지 않아 판정 대기인 클릭 |
| updated_at | timestamptz | |

- Kafka Streams가 내보낸 윈도우 누적 값을 `ON CONFLICT (ad_id, window_start) DO UPDATE`로 덮어쓴다(멱등).
- 언제든 원본 사실에서 다시 만들 수 있는 읽기 모델이다(FR-024). 확정 리포트의 근거가 아니다.
- 불변식(잠정): `valid + invalid + pending` = 그 윈도우에 반영된 클릭 수.

## 운영 기록

### quarantined_event

| 필드 | 타입 | 규칙 |
|------|------|------|
| quarantine_id | bigserial | PK |
| source_topic | text | |
| payload | bytea | 원본 그대로 |
| reason | text | `SCHEMA_INVALID` \| `RETRY_EXHAUSTED` \| ... |
| error_detail | text | |
| quarantined_at | timestamptz | |

`events.quarantine.v1` 토픽을 컨슈머가 이 테이블에 저장한다. 0이 아니면 시각화 화면과 지표에 표시된다.

### demo_scenario_run

| 필드 | 타입 | 규칙 |
|------|------|------|
| run_id | uuid | PK |
| scenario | text | `DUPLICATE_BURST` \| `LATE_ARRIVAL` \| `STAGE_OUTAGE` |
| started_at / ends_at | timestamptz | |
| status | text | `RUNNING` → `RECOVERING` → `DONE` |

동시에 `RUNNING`/`RECOVERING`인 행은 최대 1개(부분 유니크 인덱스).

## 파이프라인 지표 (저장소 아님, Prometheus)

| 지표 | 종류 | 레이블 |
|------|------|--------|
| `adclick_events_received_total` | counter | stage, type(click/impression) |
| `adclick_events_stored_total` | counter | type |
| `adclick_events_deduplicated_total` | counter | stage |
| `adclick_events_retried_total` | counter | stage |
| `adclick_events_quarantined_total` | counter | stage, reason |
| `adclick_consumer_lag` | gauge | stage |
| `adclick_end_to_end_latency_seconds` | histogram | path(realtime/final) |
| `adclick_verdicts_total` | counter | path, validity, reason |

대조식: `received = stored + deduplicated + quarantined` (+ 처리 중 적체).

## ERD 대비 변경

| 변경 | 이유 |
|------|------|
| `impression`, `click`에 `received_at` 추가(운영 기록) | 헌법 VI의 지연 지표와 늦은 도착 판단에 필요. 판정 규칙에는 쓰지 않음 |
| `click.impression_id`에 FK 제약 없음 | 노출이 클릭보다 늦게 도착할 수 있어 삽입 시점 FK 검사가 불가능 |
| 판정 사유 `AD_MISMATCH` 추가 | ERD 불변식 3(노출이 있으면 광고 일치)을 어긴 클릭을 버리지 않고 무효로 남기기 위해 |
| `daily_ad_report`에 PRELIMINARY 행을 저장하지 않음 | 잠정치는 `minute_ad_aggregate` 합산으로 조회 시 계산. 덮어쓰는 행을 따로 두지 않아 단순해짐 |
| `daily_ad_report.invalid_by_reason` 추가 | 스펙 FR-014(사유별 무효 수) |
| `advertiser.time_zone_effective_from` 추가 | 시간대 변경이 과거 확정 리포트의 날짜 경계를 흔들지 않게 |
| `minute_ad_aggregate`, `quarantined_event`, `demo_scenario_run` 추가 | ERD가 "아키텍처 단계에서 다룸"으로 미룬 읽기 모델과 운영 기록 |
| `ad.landing_url` 추가 | 클릭 측정 후 이동할 주소(FR-002) |
