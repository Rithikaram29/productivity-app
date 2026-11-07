# IntentCoach

Android app (Kotlin + Jetpack Compose). When the user opens a distracting app, interrupt
with one question — "what did you pick up your phone to do?" — log the answer locally, and
let them redirect or proceed. No blocking, no shaming, no network. Ever.

- **Constraints and style:** `.claude/skills/intentcoach-conventions/SKILL.md` — read it
  before writing any code here. The no-network / no-`QUERY_ALL_PACKAGES` /
  no-`AccessibilityService` rules are non-negotiable.
- **Build and test:** `.claude/skills/gradle-verify/SKILL.md`, or `/verify`.
- **Work plan:** `plan.md` — waves of parallel agents with disjoint file ownership.
  Dispatch with `/run-wave`; protocol in `.claude/skills/parallel-waves/SKILL.md`.

The author is new to Kotlin and Android and reads this code to learn: comment *why* for
coroutine scoping, Compose state, service lifecycle, and PackageManager quirks.
