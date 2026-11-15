#!/bin/sh
# 스키마 레지스트리 전역 규칙: 새 스키마 버전은 BACKWARD 호환이어야 하고, 문법이 맞아야 한다(헌법 III).
# 이미 있으면 409가 나오므로 그 경우는 성공으로 본다.
set -eu

REGISTRY_URL="${REGISTRY_URL:-http://localhost:8080}"

add_rule() {
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$REGISTRY_URL/apis/registry/v3/admin/rules" \
    -H 'Content-Type: application/json' -d "{\"ruleType\":\"$1\",\"config\":\"$2\"}")
  case "$code" in
    2*|409) echo "global rule ready: $1=$2" ;;
    *) echo "failed to set $1 (HTTP $code)" >&2; exit 1 ;;
  esac
}

add_rule COMPATIBILITY BACKWARD
add_rule VALIDITY FULL
