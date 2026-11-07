---
name: intentcoach-conventions
description: IntentCoach's non-negotiable constraints, layer rules, palette, and copy voice. Load before writing or reviewing any code in this app — Kotlin, Compose, manifest, or Gradle.
---

# IntentCoach conventions

**Product in one line:** when the user opens a distracting app, interrupt with one
question — "what did you pick up your phone to do?" — log the answer locally, and let them
redirect or proceed. No blocking, no shaming, no network. Ever.

## Non-negotiable
1. **No network code. At all.** No Retrofit, OkHttp, Firebase, analytics, crash reporting,
   or `INTERNET` permission. The privacy promise is that there is nowhere for the data to
   go. If a task seems to need the network, stop and report it — do not add it.
2. **Never add `QUERY_ALL_PACKAGES`** — restricted, needs a Play Console declaration.
   To show installed apps, hold a curated package list and call
   `packageManager.getApplicationInfo(pkg, 0)` per package in a try/catch. That call on a
   *specific known* package needs no restricted permission.
3. **Never switch to `AccessibilityService`.** `UsageStatsManager` polling stays, for Play
   Store review reasons documented in `AppWatchService.kt`.
4. **`minSdk = 26`, `targetSdk`/`compileSdk = 35`.** Anything API 34+ needs a
   `Build.VERSION.SDK_INT` branch or manifest sdk-version attributes, with a comment saying why.

## Layers
`data/` and `util/` must not import from `ui/`, `overlay/`, or `detection/`.
Dependencies point inward. Every new screen is a new file in the package matching its role.
`InterruptActivity` is a separately launched surface, never a navigation destination.

## Style
- Palette (defined in `overlay/InterruptActivity.kt`): `Ink`, `Cream`, `Muted`, `Field`,
  `Accent`, `Green`. Dark interrupt surface. Material3.
- `RoundedCornerShape(12–14.dp)`; primary buttons `52.dp` tall.
- No charting library — Compose primitives only.
- Room DAO functions are `suspend`; call them from `LaunchedEffect`/`produceState`, and
  comment the scoping choice.

## Copy voice
Plain, warm, never scolding. Frame wins warmly — "you turned back 14 times this week" —
never a score, streak, or guilt trip. The sharpest insight the data holds is the habit
rate: "you opened Instagram 23 times this week; 14 of those you had no reason."

## Comments
The user is new to Kotlin and Android and reads this code to learn. For non-obvious code —
coroutine scoping, Compose state, service lifecycle, PackageManager quirks — add a brief
comment explaining *why*, matching the existing comment style.
