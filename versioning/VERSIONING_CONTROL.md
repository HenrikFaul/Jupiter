# MONOLITH APK Analyzer — versioning control

This file governs **MONOLITH APK Analyzer** (the project at `C:\Work\APK-benchmark`) development
iterations. It is the authoritative, copy-able control prompt: give it to any AI or human who changes
MONOLITH and it tells them exactly how to record the change. It is NOT for APKs analysed by MONOLITH.

> **START HERE:** the master entry point for every iteration is
> **`C:\Work\APK-benchmark\DEVELOPMENT_CONTROL_PROMPT.md`** — read it first. It references this file for the
> versioning-record format and adds the full read-order, the mandatory artifact-sync list, the honesty contract,
> and the no-regression invariants.

> Heritage: the format follows the Henris Forge versioning convention. Boundary: these files describe
> MONOLITH's own evolution; they must never be written into a user's analysed app.

---

## Mandatory rule for every MONOLITH development iteration

Before claiming completion, every change to MONOLITH must update ALL of these:

1. `app/package.json` **version** and `app/engine/monolith/__init__.py` `__version__` (kept in lock-step).
2. `changelog.md` (append-only, the forward-read + append rules at the top of that file).
3. One **versioning summary** file in `versioning/`.
4. One **AI development prompt / implementation record** file in `versioning/`.
5. Relevant **verification notes** (the exact commands run + their result) — folded into the summary.
6. New error patterns → appended to the lessons bible **via** `C:\Work\Henris\HenrisForge\lessons-system\lessons_orchestrator.md`
   (read that orchestrator FIRST; append to the correct PART/section, never the file end).

## File naming

`versioning/<YYMMDD>_<NN>_v<version>_<slug>.md` and
`versioning/<YYMMDD>_<NN>_v<version>_ai-dev-prompt.md`
(e.g. `260630_05_v0.5.0_qa-1000-implementation.md`). `NN` is the day's change-package sequence.

## Required versioning-summary depth

Each summary MUST include:
- version · date · change-package id · scope (MONOLITH platform, not analysed apps);
- **Goal / why the change exists** (tie to the authoritative user requirement);
- **Added** (every new file/module, one line each);
- **Modified** (every changed file + what changed);
- **Control / prompt / governance changes** (roles, gates, contracts);
- **Acceptance criteria** (objective, checkable);
- **Regression checks** (what must STILL work — list the prior capabilities you confirmed);
- **Verification** (commands run + observed result);
- **Remaining risk**.

## Required AI-development-record depth

Each AI dev record MUST include:
- **Objective**;
- **Authoritative user requirement** (quote/paraphrase the user's exact ask);
- **Implementation constraints** (incl. "no regression", honesty contract, lessons/changelog/versioning duties);
- **Files to inspect before editing**;
- **Exact execution steps** (ordered);
- **Files expected to change / be added**;
- **Verification commands**;
- **Completion rule** (the work is not done until version + changelog + versioning + verification are updated and
  no prior capability regressed).

## Engine-specific invariants to assert in every "Regression checks" block

- Honesty contract intact: ERROR/SKIP over fake PASS; LLM = judgement (INFO), not evidence; coverage always shown.
- The pipeline still runs end-to-end (`python -m monolith.cli <apk> --no-dynamic --no-decompile --no-llm` passes).
- `--doctor` still green; the RPC channel is encoding-safe (UTF-8); the report still emits all 17 sections.
- No probe/corroborator/QA-evaluator removed; the 100 cross-verification corroborators and the 1000-method QA
  evaluators still load (`from monolith.corroborate import runner; from monolith.qa_checks import runner`).

## Boundary

These files describe MONOLITH's evolution only. Do not generate or copy them into an analysed app's report or
working tree.
