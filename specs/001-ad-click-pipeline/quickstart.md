# Quickstart: 로컬 데모로 전체 흐름 검증하기

**Feature**: [spec.md](./spec.md) | **Contracts**: [contracts/](./contracts/) | **Data model**: [data-model.md](./data-model.md)

이 문서는 구현이 끝난 뒤 스펙의 성공 기준을 손으로 확인하는 순서다. 명령 이름은 구현 단계에서 확정된다.

## 준비물

- Docker Desktop(메모리 8GB 이상 할당), JDK 21, Node.js LTS
- 포트: 8081(collector), 8082(pipeline: 조회 API·데모 스트림), 5173(web), 9092(Kafka), 5432(PostgreSQL), 8080(Apicurio), 9090(Prometheus)

## 1. 띄우기 (FR-033)

```bash
make demo-up
```

- 인프라(Kafka, Apicurio, PostgreSQL, Prometheus)와 앱 2개(collector, pipeline), 웹 화면이 함께 뜬다. 시드 데이터로 광고주 3명·광고 30개와 광고주별 API 키가 등록된다.
- `pipeline`의 `demo` 모듈이 합성 노출·클릭을 collector로 계속 보낸다.
- 확인: http://localhost:5173 에서 흐름 다이어그램의 숫자가 움직인다.

내리기: `make demo-down` (데이터까지 지우려면 `make demo-clean`).

## 2. 유실·중복 없음 (US1, SC-001)

```bash
./gradlew :pipeline:integrationTest --tests '*NoLossNoDuplicate*'
```

- 기대: 중복 전송·브로커 재시작·컨슈머 강제 종료를 섞은 시나리오에서 저장된 고유 클릭 수 = 보낸 고유 클릭 수.

## 3. 판정 규칙 (US2, SC-004, SC-005)

```bash
./gradlew :pipeline:integrationTest --tests '*finalize*'
```

- 기대: 사유별 픽스처(노출 없음, 오래된 노출, 중복 클릭, 비인간, 광고 불일치)의 판정이 모두 기대값과 같고,
  확정 리포트마다 `valid + invalid = 총수`가 성립한다.
- 늦은 클릭 개정본: 확정된 날짜에 클릭을 추가 주입하면 revision 2가 `revision_reason`과 함께 생긴다.

## 4. 광고주 조회 (US3, SC-007~SC-009)

시드 API 키는 `.env.demo`에 있다([contracts/query-api.yaml](./contracts/query-api.yaml)).

```bash
curl -H "X-API-Key: $ADV1_KEY" "http://localhost:8082/v1/ads/101/clicks/recent?minutes=10"
curl -H "X-API-Key: $ADV1_KEY" "http://localhost:8082/v1/ads/top?minutes=10&limit=5"
curl -H "X-API-Key: $ADV1_KEY" "http://localhost:8082/v1/ads/201/clicks/recent?minutes=10"   # 광고주 2의 광고
```

- 기대: 앞의 두 요청은 `PRELIMINARY` 수치, 세 번째는 404.

## 5. 시각화와 데모 시나리오 (US4, SC-010~SC-013)

웹 화면(http://localhost:5173)에서 시나리오 버튼을 하나씩 누른다.

| 시나리오 | 기대 화면 |
|----------|-----------|
| 중복 전송 폭주 | 10초 안에 중복 제거 수가 늘고, 대조식은 계속 balanced |
| 단계 장애 | ingest 적체가 늘다가 자동 재개 후 2분 안에 0으로 복귀, 유실 0건 |
| 늦게 도착한 클릭 | 1분 집계에서 빠진 클릭 수가 표시되고, 확정 시 반영됨을 설명 |

시나리오 진행 중 다른 버튼은 비활성화된다. 모든 시나리오 후 대조식이 balanced여야 한다(SC-012).

## 6. 처리량·지연 (SC-002, SC-003, SC-009)

```bash
./gradlew :loadtest:gatlingRun
```

- 기대: 초당 10,000건 30분 지속 동안 유실 0건, 리다이렉트 p95 ≤ 1초·p99 ≤ 2초,
  클릭 측정 → 조회 반영 p95 ≤ 2분·p99 ≤ 5분. 결과 리포트는 `loadtest/build/reports/gatling/`에 남는다.
