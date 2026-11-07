---
description: Build IntentCoach, run the unit tests, and audit the non-negotiable constraints.
allowed-tools: Read, Grep, Glob, Bash, Agent
---

Run `.claude/tools/verify.sh` and report the summary.

If the build or tests fail, give `file:line` plus the compiler/assertion message verbatim
for each failure, then stop and ask before fixing anything — do not start editing code.
If the constraint grep flags candidates, read each hit and say whether it is a real
violation or a comment/doc false positive.
