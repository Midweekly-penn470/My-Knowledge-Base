#!/usr/bin/env bash
set -euo pipefail

BASE_URL="http://127.0.0.1:8081"
QUESTION="Summarize the uploaded document."
TASK_POLL_COUNT=40
TASK_POLL_INTERVAL_SECONDS=3
RUN_OCR_CHECK=0
DRY_RUN=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      BASE_URL="$2"
      shift 2
      ;;
    --question)
      QUESTION="$2"
      shift 2
      ;;
    --task-poll-count)
      TASK_POLL_COUNT="$2"
      shift 2
      ;;
    --task-poll-interval)
      TASK_POLL_INTERVAL_SECONDS="$2"
      shift 2
      ;;
    --run-ocr-check)
      RUN_OCR_CHECK=1
      shift
      ;;
    --dry-run)
      DRY_RUN=1
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

require_cmd curl
require_cmd python3

write_step() {
  echo "[smoke] $1"
}

json_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local token="${4:-}"
  local accept="${5:-application/json}"
  local response_file
  response_file="$(mktemp)"

  local curl_args=(
    -sS
    -o "$response_file"
    -w "%{http_code}"
    -X "$method"
    -H "Accept: $accept"
  )

  if [[ -n "$token" ]]; then
    curl_args+=(-H "Authorization: Bearer $token")
  fi

  if [[ -n "$body" ]]; then
    curl_args+=(-H "Content-Type: application/json" --data "$body")
  fi

  local status
  status="$(curl "${curl_args[@]}" "$BASE_URL$path")"
  local text
  text="$(cat "$response_file")"
  rm -f "$response_file"

  if (( status < 200 || status >= 300 )); then
    echo "HTTP $status $path failed: $text" >&2
    exit 1
  fi

  printf '%s' "$text"
}

upload_file() {
  local path="$1"
  local file_path="$2"
  local token="$3"
  local response_file
  response_file="$(mktemp)"

  local status
  status="$(curl -sS -o "$response_file" -w "%{http_code}" -X POST \
    -H "Authorization: Bearer $token" \
    -H "Accept: application/json" \
    -F "file=@${file_path}" \
    "$BASE_URL$path")"

  local text
  text="$(cat "$response_file")"
  rm -f "$response_file"

  if (( status < 200 || status >= 300 )); then
    echo "HTTP $status upload failed: $text" >&2
    exit 1
  fi

  printf '%s' "$text"
}

json_get() {
  local expression="$1"
  python3 - "$expression" <<'PY'
import json
import sys

expr = sys.argv[1]
data = json.load(sys.stdin)
value = data
for part in expr.split('.'):
    if isinstance(value, list):
        value = value[int(part)]
    else:
        value = value.get(part)
    if value is None:
        break
if isinstance(value, bool):
    print('true' if value else 'false')
elif value is None:
    print('')
else:
    print(value)
PY
}

task_snapshot() {
  local task_id="$1"
  python3 - "$task_id" <<'PY'
import json
import sys

task_id = sys.argv[1]
data = json.load(sys.stdin)
for item in data.get('data', []):
    if item.get('id') == task_id:
        print(item.get('status', ''))
        print(item.get('currentStage', ''))
        print(item.get('failureMessage', ''))
        sys.exit(0)
sys.exit(1)
PY
}

wait_ingestion_task() {
  local knowledge_base_id="$1"
  local task_id="$2"
  local token="$3"

  for (( index=0; index<TASK_POLL_COUNT; index++ )); do
    local result
    result="$(json_request GET "/api/v1/knowledge-bases/${knowledge_base_id}/ingestion-tasks" "" "$token")"

    local snapshot
    if ! snapshot="$(printf '%s' "$result" | task_snapshot "$task_id")"; then
      echo "Task $task_id not found in knowledge base $knowledge_base_id" >&2
      exit 1
    fi

    mapfile -t fields <<< "$snapshot"
    local status="${fields[0]:-}"
    local stage="${fields[1]:-}"
    local failure_message="${fields[2]:-}"
    write_step "Task $task_id status=${status} stage=${stage}"

    if [[ "$status" == "SUCCEEDED" ]]; then
      return 0
    fi
    if [[ "$status" == "FAILED" ]]; then
      echo "Task $task_id failed: ${failure_message}" >&2
      exit 1
    fi

    sleep "$TASK_POLL_INTERVAL_SECONDS"
  done

  echo "Task $task_id did not finish within the polling window" >&2
  exit 1
}

qa_stream() {
  local knowledge_base_id="$1"
  local token="$2"
  local prompt="$3"
  local response_file
  response_file="$(mktemp)"
  local body
  body="$(python3 - "$prompt" <<'PY'
import json
import sys
print(json.dumps({"query": sys.argv[1]}, ensure_ascii=False))
PY
)"

  local status
  status="$(curl -sS -N -o "$response_file" -w "%{http_code}" -X POST \
    -H "Authorization: Bearer $token" \
    -H "Accept: text/event-stream" \
    -H "Content-Type: application/json" \
    --data "$body" \
    "$BASE_URL/api/v1/knowledge-bases/${knowledge_base_id}/qa/stream")"

  local text
  text="$(cat "$response_file")"
  rm -f "$response_file"

  if (( status < 200 || status >= 300 )); then
    echo "HTTP $status QA stream failed: $text" >&2
    exit 1
  fi

  if ! grep -q '^event: done' <<< "$text"; then
    echo "QA stream did not emit a done event" >&2
    exit 1
  fi
}

new_minimal_pdf() {
  local path="$1"
  cat > "$path" <<'PDF'
%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Count 1 /Kids [3 0 R] >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 144] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
endobj
4 0 obj
<< /Length 47 >>
stream
BT /F1 18 Tf 30 80 Td (OCR smoke PDF sample) Tj ET
endstream
endobj
5 0 obj
<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
endobj
xref
0 6
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
0000000241 00000 n 
0000000338 00000 n 
trailer
<< /Size 6 /Root 1 0 R >>
startxref
408
%%EOF
PDF
}

if (( DRY_RUN == 1 )); then
  write_step "Base URL: $BASE_URL"
  write_step "Will verify /actuator/health, auth register, KB create, document upload, task polling, QA stream"
  if (( RUN_OCR_CHECK == 1 )); then
    write_step "OCR smoke check enabled"
  fi
  exit 0
fi

suffix="$(python3 - <<'PY'
import uuid
print(uuid.uuid4().hex[:8])
PY
)"
email="smoke-${suffix}@example.com"
username="smoke_${suffix}"
password="SmokePass!123"
temp_root="$(mktemp -d)"
trap 'rm -rf "$temp_root"' EXIT

write_step "Checking actuator health"
health="$(json_request GET "/actuator/health")"
health_status="$(printf '%s' "$health" | json_get 'status')"
if [[ "$health_status" != "UP" ]]; then
  echo "Health endpoint is not UP: $health" >&2
  exit 1
fi

write_step "Registering smoke user $email"
register_body="$(python3 - "$username" "$email" "$password" <<'PY'
import json
import sys
print(json.dumps({
    "username": sys.argv[1],
    "email": sys.argv[2],
    "password": sys.argv[3],
}, ensure_ascii=False))
PY
)"
register_response="$(json_request POST "/api/v1/auth/register" "$register_body")"
token="$(printf '%s' "$register_response" | json_get 'data.accessToken')"
if [[ -z "$token" ]]; then
  echo "Smoke register returned no access token" >&2
  exit 1
fi

write_step "Creating knowledge base"
kb_body="$(python3 - "$suffix" <<'PY'
import json
import sys
print(json.dumps({
    "name": f"smoke-kb-{sys.argv[1]}",
    "description": "Smoke validation knowledge base",
}, ensure_ascii=False))
PY
)"
kb_response="$(json_request POST "/api/v1/knowledge-bases" "$kb_body" "$token")"
knowledge_base_id="$(printf '%s' "$kb_response" | json_get 'data.id')"
if [[ -z "$knowledge_base_id" ]]; then
  echo "Knowledge base creation returned no id" >&2
  exit 1
fi

txt_path="$temp_root/smoke.txt"
cat > "$txt_path" <<'TXT'
This is the smoke test document for My Knowledge Base. It proves upload and QA.
TXT

write_step "Uploading text document"
upload_response="$(upload_file "/api/v1/knowledge-bases/${knowledge_base_id}/documents" "$txt_path" "$token")"
text_task_id="$(printf '%s' "$upload_response" | json_get 'data.ingestionTask.id')"
if [[ -z "$text_task_id" ]]; then
  echo "Text upload returned no task id" >&2
  exit 1
fi
wait_ingestion_task "$knowledge_base_id" "$text_task_id" "$token"

write_step "Calling QA stream"
qa_stream "$knowledge_base_id" "$token" "$QUESTION"

if (( RUN_OCR_CHECK == 1 )); then
  pdf_path="$temp_root/smoke.pdf"
  new_minimal_pdf "$pdf_path"
  write_step "Uploading PDF document for OCR smoke"
  pdf_upload_response="$(upload_file "/api/v1/knowledge-bases/${knowledge_base_id}/documents" "$pdf_path" "$token")"
  pdf_task_id="$(printf '%s' "$pdf_upload_response" | json_get 'data.ingestionTask.id')"
  if [[ -z "$pdf_task_id" ]]; then
    echo "PDF upload returned no task id" >&2
    exit 1
  fi
  wait_ingestion_task "$knowledge_base_id" "$pdf_task_id" "$token"
fi

write_step "Smoke test passed"
