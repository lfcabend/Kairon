#!/usr/bin/env bash
# Backs `task e2e` (see Taskfile.yml). Runs as a real shell (not go-task's
# built-in POSIX interpreter, which does not reliably support background jobs
# and `trap` — see the commit that added this script) so the throwaway
# database, port-forward, and backend are guaranteed to be torn down even if
# the Playwright run fails.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

PG_NAMESPACE="${PG_NAMESPACE:-postgres}"
PG_SVC="${PG_SVC:-postgresql}"
PG_POD="${PG_POD:-${PG_SVC}-0}"
PG_LOCAL_PORT="${PG_LOCAL_PORT:-5432}"
APP_LOCAL_PORT="${APP_LOCAL_PORT:-8080}"
DB_USER="${DB_USER:-kairon}"
DB_PASSWORD="${DB_PASSWORD:-kairon}"
E2E_DB="${E2E_DB:-kairon_e2e}"

BOOTRUN_PID=""
PF_PID=""
cleanup() {
  status=$?
  set +e
  [ -n "$BOOTRUN_PID" ] && kill "$BOOTRUN_PID" 2>/dev/null
  [ -n "$PF_PID" ] && kill "$PF_PID" 2>/dev/null
  PGPW=$(kubectl -n "$PG_NAMESPACE" get secret "$PG_SVC" -o jsonpath='{.data.postgres-password}' | base64 -d)
  kubectl -n "$PG_NAMESPACE" exec "$PG_POD" -- env PGPASSWORD="$PGPW" \
    psql -U postgres -c "DROP DATABASE IF EXISTS ${E2E_DB};" >/dev/null 2>&1
  rm -f e2e-portforward.log e2e-backend.log
  exit "$status"
}
trap cleanup EXIT

echo "creating throwaway database ${E2E_DB}"
PGPW=$(kubectl -n "$PG_NAMESPACE" get secret "$PG_SVC" -o jsonpath='{.data.postgres-password}' | base64 -d)
kubectl -n "$PG_NAMESPACE" exec "$PG_POD" -- env PGPASSWORD="$PGPW" \
  psql -U postgres -c "DROP DATABASE IF EXISTS ${E2E_DB};" -c "CREATE DATABASE ${E2E_DB} OWNER ${DB_USER};" >/dev/null

echo "port-forwarding Postgres to localhost:${PG_LOCAL_PORT}"
kubectl -n "$PG_NAMESPACE" port-forward "svc/${PG_SVC}" "${PG_LOCAL_PORT}:5432" >e2e-portforward.log 2>&1 &
PF_PID=$!
sleep 3

echo "starting the backend (local profile) against ${E2E_DB}"
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${PG_LOCAL_PORT}/${E2E_DB}" \
SPRING_DATASOURCE_USERNAME="$DB_USER" SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
./gradlew :backend:bootRun --args='--spring.profiles.active=local' >e2e-backend.log 2>&1 &
BOOTRUN_PID=$!

echo "waiting for the backend to become healthy"
i=0
until curl -sf "http://localhost:${APP_LOCAL_PORT}/kairon/api/v1/ping" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge 60 ]; then
    echo "backend did not become healthy in time -- see e2e-backend.log" >&2
    exit 1
  fi
  sleep 2
done

echo "running the e2e suite"
set +e
(cd web && npx playwright test)
test_status=$?
set -e

echo "HTML report: cd web && npx playwright show-report"
exit "$test_status"
