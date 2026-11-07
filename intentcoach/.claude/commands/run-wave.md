---
description: Dispatch the next unfinished wave of plan.md as parallel subagents, then verify and update statuses.
argument-hint: "[wave number, e.g. 1 — omit to run the next unfinished wave]"
allowed-tools: Read, Edit, Write, Grep, Glob, Bash, Agent
---

Run wave $1 of `plan.md` (omit the argument → the lowest wave with unfinished tasks).

Follow the `parallel-waves` skill exactly:

1. Read `plan.md`. Confirm every dependency of this wave is `done`; stop and say so if not.
2. Perform the wave's **pre-wave step** yourself — shared files only you may touch.
3. Spawn every task in the wave **in one message** so they run in parallel, using the agent
   named in each task row. Each prompt must include, verbatim: the task id and goal, its
   owned-files list, its acceptance criteria, and the instruction to touch only those files
   and report a blocker rather than editing anything else.
4. When all agents return, run `build-verifier` and `constraint-auditor`.
5. Update each task's status in `plan.md` and report: what landed, what is blocked and why,
   and what still needs a real phone.

Do not start the next wave without being asked.
