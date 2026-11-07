#!/usr/bin/env bash
# One-shot verification: build, unit tests, constraint grep. Run from anywhere.
# Only ONE agent should run this at a time — Gradle's project lock is exclusive.
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 1

log=$(mktemp -t intentcoach-verify)
build=FAIL; tests=SKIPPED

echo "→ ./gradlew :app:assembleDebug"
if ./gradlew :app:assembleDebug --console=plain >"$log" 2>&1; then
  build=PASS
  echo "→ ./gradlew :app:testDebugUnitTest"
  if ./gradlew :app:testDebugUnitTest --console=plain >>"$log" 2>&1; then tests=PASS; else tests=FAIL; fi
fi

[ "$build" = PASS ] && [ "$tests" != FAIL ] || { echo; echo "--- gradle output (errors) ---"; grep -nE '^e: |FAILED|FAILURE:|> Task .*FAILED|^\s+[A-Za-z.]+Error' "$log" | head -40; echo "(full log: $log)"; }

echo
echo "→ constraints"
"$(dirname "$0")/check-constraints.sh"

echo
echo "===== SUMMARY ====="
echo "assembleDebug:     $build"
echo "testDebugUnitTest: $tests"
echo "full gradle log:   $log"
