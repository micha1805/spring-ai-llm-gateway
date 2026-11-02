#!/usr/bin/env bash
# Uploads the fictional mutuality samples. The same bytes are idempotent (HTTP 200 on a repeat).
set -euo pipefail

base="${1:-http://127.0.0.1:8080}"
root="$(cd "$(dirname "$0")/.." && pwd)"
dir="${root}/src/main/resources/samples/mutualite"

if [[ ! -d "${dir}" ]]; then
  echo "Sample directory not found: ${dir}" >&2
  exit 1
fi

for file in "${dir}"/*; do
  name="$(basename "${file}")"
  echo "Uploading ${name}"
  curl -sS -X POST "${base}/api/documents" \
    -F "file=@${file};filename=${name}" \
    -F "source=sample"
  echo
done
