#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:9900}"
TOKEN="${CONTEST_API_TOKEN:-${KAFU_API_TOKEN:-}}"

if [[ -z "${TOKEN}" ]]; then
  echo "Please set CONTEST_API_TOKEN before testing /chat." >&2
  exit 1
fi

echo "1. Milvus health"
curl -fsS "${BASE_URL}/milvus/health"
echo

echo "2. Knowledge files"
curl -fsS "${BASE_URL}/api/knowledge/files"
echo

echo "3. RAG search"
curl -fsS --get "${BASE_URL}/api/rag/search" \
  --data-urlencode "q=payment order timeout" \
  --data-urlencode "topK=3"
echo

echo "4. Contest chat"
curl -fsS -X POST "${BASE_URL}/chat" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"question":"How should we troubleshoot a payment order timeout?","stream":false}'
echo
