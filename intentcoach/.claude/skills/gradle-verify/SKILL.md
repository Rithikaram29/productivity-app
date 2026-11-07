---
name: gradle-verify
description: How to build and test IntentCoach with the Gradle wrapper, including the parallel-agent lock rule and how to read failures. Use whenever a build, compile-check, or unit-test run is needed.
---

# Building and testing IntentCoach

Run everything from `intentcoach/` with the committed wrapper (Gradle 8.9, AGP 8.5.2, JDK 17):

```bash
./gradlew :app:compileDebugKotlin --console=plain   # fast compile check
./gradlew :app:assembleDebug --console=plain        # full debug build
./gradlew :app:testDebugUnitTest --console=plain    # JVM unit tests
./gradlew :app:testDebugUnitTest --tests 'com.intentcoach.app.data.*'
```

`.claude/tools/verify.sh` runs the build, the tests, and the constraint check in one pass
and prints a short PASS/FAIL summary.

## The lock rule (matters when agents run in parallel)
Gradle takes an exclusive lock on the project. **Only the wave's designated verifier runs
Gradle.** If you are not it and you see "Timeout waiting to lock", wait 30s, retry once,
then report contention — never kill another Gradle process or run with `--no-daemon` to
force past it.

## Reading failures
Kotlin errors arrive as `e: file:///…/Foo.kt:42:17 message` — report `file:line` plus the
message verbatim. Room's KSP errors surface at build time and usually point at the DAO
query string, not the call site. A test failure prints the assertion and the report path
under `app/build/reports/tests/testDebugUnitTest/index.html`.

Instrumented tests are out of scope — there is no connected device.
