#!/bin/sh

set -eu

timeout_seconds="${WAIT_TIMEOUT_SECONDS:-300}"
poll_seconds="${WAIT_POLL_SECONDS:-2}"

if [ "$#" -eq 0 ]; then
  echo "usage: $0 <container>..." >&2
  exit 1
fi

for container in "$@"; do
  elapsed=0

  while :; do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"

    case "$status" in
      healthy|running)
        break
        ;;
      unhealthy|exited|dead)
        echo "container '$container' is $status" >&2
        exit 1
        ;;
      "")
        echo "container '$container' was not found" >&2
        exit 1
        ;;
    esac

    if [ "$elapsed" -ge "$timeout_seconds" ]; then
      echo "timed out waiting for '$container' to become healthy" >&2
      exit 1
    fi

    sleep "$poll_seconds"
    elapsed=$((elapsed + poll_seconds))
  done
done
