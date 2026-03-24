#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/deploy/docker-compose.yml"
ENV_FILE="$PROJECT_ROOT/deploy/.env"
BASE_URL="http://127.0.0.1:8081"
RUN_BUILD=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      BASE_URL="$2"
      shift 2
      ;;
    --skip-build)
      RUN_BUILD=0
      shift
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

require_cmd docker
require_cmd curl
require_cmd bash

write_step() {
  echo "[rehearse] $1"
}

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

wait_for_http() {
  local url="$1"
  local label="$2"
  local attempts="${3:-30}"

  for (( i=1; i<=attempts; i++ )); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      write_step "$label is ready"
      return 0
    fi
    sleep 2
  done

  echo "$label did not become ready: $url" >&2
  exit 1
}

ocr_enabled="$(grep '^OCR_ENABLED=' "$ENV_FILE" | tail -n 1 | cut -d= -f2- | tr '[:upper:]' '[:lower:]' || true)"

write_step "Project root: $PROJECT_ROOT"
write_step "Using compose file: $COMPOSE_FILE"

if (( RUN_BUILD == 1 )); then
  write_step "Starting project stack with build"
  compose up -d --build
else
  write_step "Starting project stack without build"
  compose up -d
fi

write_step "Waiting for backend health"
wait_for_http "$BASE_URL/actuator/health" "backend health"

if [[ "$ocr_enabled" == "true" ]]; then
  write_step "Waiting for OCR health"
  wait_for_http "http://127.0.0.1:8090/healthz" "ocr health"
fi

write_step "Running text smoke"
bash "$PROJECT_ROOT/scripts/smoke.sh" --base-url "$BASE_URL"

if [[ "$ocr_enabled" == "true" ]]; then
  write_step "Running OCR smoke"
  bash "$PROJECT_ROOT/scripts/smoke.sh" --base-url "$BASE_URL" --run-ocr-check
fi

write_step "Linux rehearsal passed"
