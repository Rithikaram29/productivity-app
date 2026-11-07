#!/usr/bin/env bash
# Greps the IntentCoach tree for violations of the project's non-negotiables.
# Hits are CANDIDATES — a human or the constraint-auditor agent must read each one,
# since a match inside a comment explaining why we avoid something is not a violation.
set -uo pipefail
cd "$(dirname "$0")/../.." || exit 1

SRC="app/src"
fail=0
hit() { printf '\n== %s ==\n' "$1"; }
# Drops obvious comment lines (Kotlin //, KDoc *, XML <!-- and prose inside a manifest
# comment block). Crude on purpose: the auditor still reads every surviving hit.
nocomment() { grep -vE ':[0-9]+:[[:space:]]*(//|\*|<!--|-->)' | grep -vE ':[0-9]+:[[:space:]]{6,}[A-Za-z]'; }

hit "network (libraries / APIs)"
grep -rnE 'retrofit|okhttp|io\.ktor|firebase|crashlytics|HttpURLConnection|java\.net\.(URL|Socket)|WebView' \
  "$SRC" app/build.gradle.kts build.gradle.kts 2>/dev/null && fail=1

hit "INTERNET permission"
grep -rn 'permission.INTERNET' "$SRC" 2>/dev/null && fail=1

hit "QUERY_ALL_PACKAGES (uncommented)"
grep -rn 'QUERY_ALL_PACKAGES' "$SRC/main/AndroidManifest.xml" 2>/dev/null | nocomment && fail=1

hit "AccessibilityService"
grep -rn 'AccessibilityService' "$SRC" 2>/dev/null | nocomment && fail=1

hit "layer boundary: data/ or util/ importing ui|overlay|detection"
grep -rnE 'import com\.intentcoach\.app\.(ui|overlay|detection)' \
  "$SRC/main/java/com/intentcoach/app/data" "$SRC/main/java/com/intentcoach/app/util" 2>/dev/null && fail=1

hit "charting libraries"
grep -rniE 'mpandroidchart|vico|charts?:' app/build.gradle.kts 2>/dev/null && fail=1

hit "API 34+ symbols (each must be SDK_INT-guarded or manifest-gated)"
grep -rn 'FOREGROUND_SERVICE_SPECIAL_USE\|FOREGROUND_SERVICE_TYPE_SPECIAL_USE\|specialUse' "$SRC" 2>/dev/null

echo
if [ "$fail" -eq 0 ]; then
  echo "check-constraints: no candidate violations (review the API 34+ list above by hand)"
else
  echo "check-constraints: CANDIDATE VIOLATIONS above — verify each by reading the file"
fi
exit 0
