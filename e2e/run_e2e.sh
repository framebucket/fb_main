#!/usr/bin/env bash
# Runs the FrameBucket end-to-end suites against the real Spring Boot backend and a fresh PostgreSQL
# database per suite, with stub_data_service.py standing in for the Python data service.
#
#   e2e/run_e2e.sh                      # every suite
#   e2e/run_e2e.sh e2e_suite e2e_growth # chosen suites
#
# Needs: a built backend jar (mvn -f backend/pom.xml -DskipTests package), psql, Python 3 with
# requests + Pillow (pip install -r e2e/requirements.txt). PostgreSQL connection comes from the usual
# PG* variables (default postgres:postgres@localhost:5432). The style / album / invitation suites run
# only when FB_DATA_SRC points at an fb_data checkout with its dependencies installed.
set -uo pipefail
cd "$(dirname "$0")"

export PGHOST="${PGHOST:-localhost}" PGPORT="${PGPORT:-5432}" PGUSER="${PGUSER:-postgres}" PGPASSWORD="${PGPASSWORD:-postgres}"
export PGDATABASE="${E2E_DATABASE:-fb_e2e}"
JDBC_HOST="${E2E_JDBC_HOST:-localhost}"
BACKEND_PORT="${E2E_BACKEND_PORT:-18080}"
STUB_PORT="${E2E_STUB_PORT:-8001}"
export FB_E2E_OUT="${FB_E2E_OUT:-$PWD/out}"
mkdir -p "$FB_E2E_OUT"

if [ -z "${BACKEND_CMD:-}" ]; then
  JAR="${JAR:-$(ls ../backend/target/*.jar 2>/dev/null | grep -v '\.original$' | head -1)}"
  if [ -z "$JAR" ]; then echo "No backend jar found: run 'mvn -f backend/pom.xml -DskipTests package' first." >&2; exit 2; fi
  BACKEND_CMD="java -Duser.timezone=UTC -jar $JAR"
fi

if [ "$#" -gt 0 ]; then suites=("$@"); else suites=(e2e_suite e2e_admin e2e_growth e2e_style e2e_album e2e_invite); fi

failures=0
summary=""
for suite in "${suites[@]}"; do
  echo "::group::$suite"
  psql -d postgres -qc "DROP DATABASE IF EXISTS \"$PGDATABASE\" WITH (FORCE)" -c "CREATE DATABASE \"$PGDATABASE\"" || exit 2

  python3 stub_data_service.py "$STUB_PORT" > "$FB_E2E_OUT/$suite-stub.log" 2>&1 &
  stub=$!

  backend_log="$FB_E2E_OUT/$suite-backend.log"
  DB_URL="jdbc:postgresql://$JDBC_HOST:$PGPORT/$PGDATABASE" DB_USERNAME="$PGUSER" DB_PASSWORD="$PGPASSWORD" \
  SERVER_PORT="$BACKEND_PORT" DATA_SERVICE_BASE_URL="http://127.0.0.1:$STUB_PORT" DATA_SERVICE_TOKEN=t \
  DATA_SERVICE_OUTBOX_POLL_MS=600000 APP_REMINDERS_CRON='0 0 3 1 1 *' APP_ADMIN_EMAILS='owner@framebucket.in' \
  APP_QUOTE_FOLLOW_UP_CRON='*/5 * * * * *' $BACKEND_CMD > "$backend_log" 2>&1 &
  backend=$!

  started=0
  for _ in $(seq 1 90); do
    if grep -q "Started FrameBucketApplication" "$backend_log"; then started=1; break; fi
    if grep -q "APPLICATION FAILED TO START\|Application run failed" "$backend_log" || ! kill -0 "$backend" 2>/dev/null; then break; fi
    sleep 2
  done

  if [ "$started" = 1 ]; then
    FB_BASE_URL="http://localhost:$BACKEND_PORT" FB_STUB_URL="http://127.0.0.1:$STUB_PORT" FB_APP_LOG="$backend_log" \
    FB_DATA_DATABASE_URL="postgresql://$PGUSER:$PGPASSWORD@$JDBC_HOST:$PGPORT/$PGDATABASE" \
      python3 "$suite.py" 2>&1 | tee "$FB_E2E_OUT/$suite.txt"
    rc=${PIPESTATUS[0]}
  else
    echo "Backend did not start; last log lines:"; tail -40 "$backend_log"
    rc=1
  fi

  kill "$backend" "$stub" 2>/dev/null; wait "$backend" "$stub" 2>/dev/null
  result="$(grep -E '^(RESULT|SKIPPED)' "$FB_E2E_OUT/$suite.txt" 2>/dev/null | tail -1)"
  summary+="$suite: ${result:-no result} (exit $rc)"$'\n'
  [ "$rc" -ne 0 ] && failures=$((failures + 1))
  echo "::endgroup::"
done

echo
echo "End-to-end summary"
printf '%s' "$summary"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  { echo "### End-to-end suites"; echo '```'; printf '%s' "$summary"; echo '```'; } >> "$GITHUB_STEP_SUMMARY"
fi
exit $(( failures > 0 ? 1 : 0 ))
