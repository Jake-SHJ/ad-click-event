# Contract: 시각화 SSE 스트림 (`GET /demo/stream`)

웹 화면은 이 스트림만 읽는다(헌법 기술 제약). 이벤트 종류는 두 가지다.

## `snapshot` (1초마다)

```json
{
  "at": "2026-11-15T12:00:01Z",
  "stages": [
    { "id": "collector",  "ratePerSec": 9980.0, "p95LatencyMs": 4.1, "backlog": 0 },
    { "id": "ingester",   "ratePerSec": 9975.0, "p95LatencyMs": 120.0, "backlog": 312 },
    { "id": "aggregator", "ratePerSec": 9970.0, "p95LatencyMs": 15500.0, "backlog": 40 },
    { "id": "finalizer",  "ratePerSec": 0.0, "lastRunAt": "2026-11-15T00:30:00Z" },
    { "id": "query",      "ratePerSec": 12.0, "p95LatencyMs": 35.0 }
  ],
  "reconciliation": {
    "received": 1234567, "stored": 1230000, "deduplicated": 4255, "quarantined": 0, "inFlight": 312,
    "balanced": true
  },
  "verdicts": {
    "valid": 1100000, "pending": 900,
    "invalidByReason": { "NO_IMPRESSION": 1200, "STALE_IMPRESSION": 800, "DUPLICATE": 9000, "NON_HUMAN": 4000, "AD_MISMATCH": 0 }
  },
  "endToEndLatency": { "p95Seconds": 78.0, "p99Seconds": 140.0 },
  "scenario": null
}
```

- `stages[].id`는 고정 목록이며 화면의 흐름 다이어그램 노드와 1:1로 대응한다.
- `reconciliation.balanced` = `received == stored + deduplicated + quarantined + inFlight`. 서버가 계산한다.
- `verdicts`는 실시간 경로의 잠정 판정 누적치다.
- 데모 시작 시점부터의 누적 값이다(데모 재시작 시 0부터).

## `scenario` (시나리오 상태가 바뀔 때)

```json
{ "runId": "…", "scenario": "STAGE_OUTAGE", "status": "RUNNING", "startedAt": "…", "endsAt": "…" }
```

`status`: `RUNNING` → `RECOVERING` → `DONE`. `snapshot.scenario`에도 같은 객체가 들어간다.

## 단계 설명 텍스트

단계별 "하는 일·설계 이유" 설명(FR-028)은 웹 화면에 정적으로 둔다. 서버는 수치만 보낸다.
