# Prompt for Claude Code — finish IntentCoach to a testable v1

Copy everything below the line into Claude Code, run from the `intentcoach/` directory.

---

You are working on **IntentCoach**, an Android app in Kotlin + Jetpack Compose. Read
`README.md` first — it explains the product and lists the known rough edges. Then read
every file under `app/src/main/java/com/intentcoach/app/` before writing any code.

**The product in one line:** when the user opens a distracting app, we interrupt with one
question — "what did you pick up your phone to do?" — log the answer locally, and let them
redirect or proceed. No blocking, no shaming, no network. Ever.

## Non-negotiable constraints

1. **No network code. At all.** No Retrofit, OkHttp, Firebase, analytics, crash reporting,
   or `INTERNET` permission. The privacy promise is that there is nowhere for the data to
   go. If a task seems to need the network, stop and tell me instead of adding it.
2. **Do not add `QUERY_ALL_PACKAGES`.** It is a restricted permission requiring a Play
   Console declaration. See constraint details in the App Picker task below.
3. **Do not switch to `AccessibilityService`.** Polling via `UsageStatsManager` stays, for
   Play Store review reasons documented in `AppWatchService.kt`.
4. **Match the existing style:** the dark interrupt palette (`Ink`/`Cream`/`Muted`/
   `Field`/`Accent`/`Green`) already defined in `InterruptActivity.kt`, `RoundedCornerShape(12–14.dp)`,
   Material3, 52.dp primary buttons. Copy should be plain, warm, and never scolding.
5. **Keep the layer boundaries.** `data/` and `util/` must not import from `ui/`,
   `overlay/`, or `detection/`. Dependencies point inward.
6. Every new screen is a new file in the package that matches its role.

## Work through these in order. Show me a diff and stop after each task.

### Task 1 — Fix the minSdk / foreground-service mismatch (BLOCKING BUG)

`build.gradle.kts` sets `minSdk = 26`, but `AndroidManifest.xml` declares
`foregroundServiceType="specialUse"` and `FOREGROUND_SERVICE_SPECIAL_USE`, which are
**API 34+**. On Android 8–13 the service will fail to start.

Fix it so the app runs correctly on **API 26 through 35**. Add the appropriate
`android:maxSdkVersion` / `android:minSdkVersion` attributes on the permission
declarations and branch the `startForeground(...)` call in `AppWatchService.onCreate()` by
`Build.VERSION.SDK_INT`. Explain in a comment why the branch exists.

### Task 2 — Gradle wrapper

There is no `gradlew` or `gradle/wrapper/` directory. Generate a Gradle wrapper pinned to
a version compatible with Android Gradle Plugin 8.5.2, and commit it (the wrapper JAR
should be committed; confirm `.gitignore` does not exclude it).

### Task 3 — Enabled flag + service lifecycle

Currently `BootReceiver` restarts the service unconditionally, and there is no way to stop
it. Add:

- A `data/Settings.kt` object wrapping SharedPreferences with an `isEnabled` flag
  (mirror the style of `WatchedApps.kt`).
- `AppWatchService.stop(context)` alongside the existing `start()`.
- `BootReceiver` only starts the service when `isEnabled` is true **and** both permissions
  are still granted (`util/Permissions.kt`).
- `MainActivity`'s button toggles between **Start** and **Stop**, reflecting real state.

### Task 4 — App picker screen

New file `ui/AppPickerScreen.kt`, reachable from `MainActivity`. Lets the user choose
which apps trigger the interrupt; writes via `WatchedApps.set()`.

**Critical constraint:** do NOT enumerate all installed apps — that needs
`QUERY_ALL_PACKAGES`. Instead, hold a curated list of ~20 well-known distracting apps
(package name + display name) in `WatchedApps.kt`, and use
`packageManager.getApplicationInfo(pkg, 0)` in a try/catch per package to show only the
ones actually installed on this device. That call on a *specific known* package does not
require the restricted permission. Show each app's real icon and label.

### Task 5 — Insights screen

New file `ui/InsightsScreen.kt`, reachable from `MainActivity`. This is the product's
differentiator: insight, not blocking. Read from `IntentLogDao` — `countForAppSince`,
`redirectsSince`, and `recent` are already written and currently unused.

Show, for the last 7 days:

- Total interrupts, and how many ended in `REDIRECTED` (frame it as a win, warmly — "you
  turned back 14 times this week", not a score or streak).
- A per-app breakdown of interrupt counts.
- The **habit rate**: `HABIT` outcomes as a share of the total, per app. This is the
  sharpest insight the data holds — "you opened Instagram 23 times this week; 14 of those
  you had no reason."
- A short list of recent stated intents with their outcome.

Add whatever DAO queries you need. No charting library — build it with Compose
primitives. Handle the empty state gracefully for a brand-new user.

### Task 6 — Navigation

Now that there are three screens, add `androidx.navigation:navigation-compose` and wire
`MainActivity` → App Picker → Insights properly. Keep `InterruptActivity` entirely outside
the nav graph — it is a separate launched surface, not a destination.

### Task 7 — Battery optimisation prompt

On Xiaomi/Samsung/Oppo devices the OS kills the background service. Add a dismissible card
on the main screen that appears only when the app is not exempt
(`PowerManager.isIgnoringBatteryOptimizations`) and deep-links to the exemption setting.
Explain plainly why it's needed. Do not nag — dismissible means it stays dismissed.

### Task 8 — Tests

There is no `src/test/` source set. Add one with JVM unit tests for the pure logic:
`WatchedApps`, the new `Settings`, and the habit-rate/statistics calculations from Task 5.
Use Robolectric only if a test genuinely needs `Context`. Do not attempt instrumented
tests.

### Task 9 — Verify

Run `./gradlew assembleDebug` and `./gradlew test`. Fix anything that fails. Then update
`README.md`: move the now-resolved items out of "Known rough edges" and document the new
screens.

## When you are done

Give me a short list of what still needs a **real phone** to verify, and anything you were
unsure about. Do not mark something complete that you could not build successfully.

## How to work with me

I am new to Kotlin and Android. When you write non-obvious code — coroutine scoping,
Compose state, service lifecycle, PackageManager quirks — add a brief comment explaining
*why*, in the style of the comments already in this codebase. I am going to read this code
to learn from it.
