---
name: parallel-waves
description: How this repo runs work as parallel waves of subagents off plan.md with disjoint file ownership. Use when dispatching, resuming, or adding tasks to the plan.
---

# Parallel waves

`plan.md` is the single source of truth. It splits the work into **waves**. Every task in a
wave owns a **disjoint set of files**, so the wave's agents can run at the same time
without clobbering each other.

## Dispatch protocol (orchestrator)
1. Read `plan.md`. Pick the lowest wave with unfinished tasks.
2. Do any **pre-wave step** yourself (shared-file edits: `build.gradle.kts`, the manifest —
   never delegate a file two tasks would both want).
3. Spawn one subagent per task in that wave, **all in a single message** so they run in
   parallel. Each prompt must carry: the task id, its owned file list verbatim, the
   acceptance criteria, and "touch only your owned files; report a blocker instead of
   editing anything else."
4. When all return, run `build-verifier` and `constraint-auditor`.
5. Update each task's status in `plan.md` (`todo` → `done` / `blocked`), then move to the
   next wave. Never start a wave whose dependencies are not `done`.

## Rules that keep it safe
- **One writer per file, ever.** If two tasks want the same file, they belong in different
  waves, or the edit is a pre-wave step.
- **One Gradle runner per wave** — the lock is exclusive (see `gradle-verify`).
- Shared files by convention: `app/build.gradle.kts`, `AndroidManifest.xml`,
  `ui/MainActivity.kt`, `README.md` — orchestrator-owned unless a task explicitly owns one.
- A blocked task does not block its wave: collect the blocker, finish the rest, resolve it
  before the next wave.
- Model choice: Haiku for read-only verification and audits, Sonnet for code that must
  match existing style.
