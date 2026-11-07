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
   The picker lists launchable apps through the manifest's `<queries>` MAIN/LAUNCHER intent
   (intent-scoped visibility, not a restricted permission). Monitor nothing by default.
3. **Never switch to `AccessibilityService`.** `UsageStatsManager` polling stays, for Play
   Store review reasons documented in `AppWatchService.kt`.
4. **`minSdk = 26`, `targetSdk`/`compileSdk = 35`.** Anything API 34+ needs a
   `Build.VERSION.SDK_INT` branch or manifest sdk-version attributes, with a comment saying why.

## Layers
`domain/` is pure Kotlin (no Android imports) and holds every rule worth testing.
`data/` and `util/` must not import from `ui/`, `overlay/`, or `detection/`.
The app container (`IntentCoachApp.kt`) wires repositories by hand — no DI framework.
Dependencies point inward. Every new screen is a new file in the package matching its role.
`InterruptActivity` is a separately launched surface, never a navigation destination.

## Style
- Palette v1.1 pastel-on-dark in `ui/Palette.kt` (copy in `overlay/InterruptActivity.kt`):
  Ink/Umber/Field backgrounds, Cream/Muted text, Clay primary, Lime positive, Ember warmth.
- Never show an unmeasured duration as 0 — show "—" / "not measured". Unknown sessions are
  never counted as unplanned.
- `RoundedCornerShape(12–14.dp)`; primary buttons `52.dp` tall.
- No charting library — Compose primitives only.
- Screens get state from a ViewModel (`appViewModel { ... }`); DAO one-shots are `suspend`,
  live reads are `Flow`. Writes that must outlive a screen use `container.appScope`.

## Copy voice
Plain, warm, never scolding. Frame wins warmly — "you turned back 14 times this week" —
never a score, streak, or guilt trip. The sharpest insight the data holds is the habit
rate as the user's own words: "75% of the sessions you went ahead with had a purpose you named."

## Comments
The user is new to Kotlin and Android and reads this code to learn. For non-obvious code —
coroutine scoping, Compose state, service lifecycle, PackageManager quirks — add a brief
comment explaining *why*, matching the existing comment style.
