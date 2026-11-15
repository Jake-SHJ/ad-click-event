#!/usr/bin/env bash
# 데모·개발용 Kafka 토픽을 만든다. 이미 있으면 건너뛴다(여러 번 실행해도 안전).
set -euo pipefail

BOOTSTRAP="${BOOTSTRAP:-localhost:9092}"
PARTITIONS="${PARTITIONS:-6}"
KAFKA_BIN="${KAFKA_BIN:-/opt/kafka/bin}"

create() {
  local topic="$1"; shift
  "$KAFKA_BIN/kafka-topics.sh" --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$topic" --partitions "$PARTITIONS" --replication-factor 1 "$@"
  echo "topic ready: $topic"
}

# 노출·클릭: 같은 키(impression_id)로 파티셔닝해 실시간 조인이 같은 파티션에서 일어난다.
create impressions.v1 --config retention.ms=172800000            # 48h
create clicks.v1 --config retention.ms=172800000                 # 48h
# 1분 집계: (ad_id, window_start) 키의 최신 값만 필요하다.
create minute-ad-aggregates.v1 --config cleanup.policy=compact
# 격리: 원본을 오래 보관한다.
create events.quarantine.v1 --config retention.ms=1209600000     # 14d
