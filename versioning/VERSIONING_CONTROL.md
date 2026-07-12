# Jupiter — versioning control

This file governs **Jupiter** (the native Android file manager in this repo) development
iterations. It is the authoritative, copy-able **control prompt**: give it to any AI or human
who changes Jupiter and it tells them exactly how to record the change. It is NOT about files
that Jupiter *manages* on a device — only about Jupiter's own evolution.

> **START HERE for every iteration** — read, in order:
> 1. `changelog.md` (repo root) — forward-read the whole file, obey its "Kötelező changelog rutin".
> 2. this file (`versioning/VERSIONING_CONTROL.md`) — the record format + invariants below.
> 3. every `versioning/*_v*.md` record in chronological order, oldest to newest — the
>    accumulated requirements and fixes that must not regress.
> 4. `docs/RUNTIME_DIAGNOSIS.md`, `docs/BUG_HUNT_REPORT.md`, `docs/DUPLICATE_HANDLER_PORT_ANALYSIS.md`
>    — the standing defect/port backlog.
>
> Lessons bible: the required entry point is the external orchestrator
> `…/HenrisForge/lessons-system/lessons_orchestrator.md` (read it FIRST, append new lessons to the
> correct PART/section). Check the concrete local path supplied for the round; when it is reachable,
> it MUST be read and updated there. Only after a real access failure may the round capture a pending
> lesson in the version record for later synchronization.

> Heritage: the format follows the Henris Forge versioning convention. Boundary: these files describe
> **Jupiter's** development; never write them into a user's device/managed files.

---

## Mandatory rule for every Jupiter development iteration

### Git synchronization contract

For this repository the owner requires direct integration on the default branch:

1. Before implementation, confirm the current branch is `main` (or `master` only if that is the
   repository's actual default), fetch the remote, and run an `--ff-only` pull from that branch.
2. Work directly on that synchronized default branch; do not create a parallel feature branch for
   an ordinary Jupiter iteration. Preserve any pre-existing local work and stop rather than resetting it.
3. After the required build/tests/docs gates pass, commit the complete scoped change and push the same
   default branch to `origin`. Record the pull/branch/commit/push evidence in the version summary.

Before claiming a round complete, every change to Jupiter must update ALL of these:

1. `app/build.gradle.kts` **versionName** (and **versionCode** when shipping a build), bumped in step.
2. `changelog.md` (append-only; obey the forward-read + append rules at the top of that file).
3. One **versioning summary** file in `versioning/`.
4. One **AI development record** file in `versioning/`.
5. **Verification notes** — the exact checks run (CI job + conclusion, greps that confirm the wiring) —
   folded into the summary.
6. New error/regression patterns → the lessons bible via the external `lessons_orchestrator.md`
   (or, when unreachable here, the version record's "Lessons captured" block).

## File naming

`versioning/<YYMMDD>_<NN>_v<version>_<slug>.md` (summary) and
`versioning/<YYMMDD>_<NN>_v<version>_ai-dev-prompt.md` (AI development record)
— e.g. `260701_15_v0.10.0_dualpane-dnd-quality.md`. `NN` is the day's change-package sequence.

## Required versioning-summary depth

Each summary MUST include:
- version · date · change-package id · scope (Jupiter app, not managed files);
- **Goal / why the change exists** (tie to the authoritative user requirement, quoted);
- **Added** (every new file/module, one line each);
- **Modified** (every changed file + what changed);
- **Control / prompt / governance changes** (contracts, single-owner maps, gates);
- **Acceptance criteria** (objective, checkable);
- **Regression checks** (the prior capabilities you confirmed STILL work — list them);
- **Verification** (CI run id + conclusion, and the concrete greps/reviews run);
- **Remaining risk / rollback**.

## Required AI-development-record depth

Each AI dev record MUST include:
- **Objective**;
- **Authoritative user requirement** (quote/paraphrase the user's exact ask, incl. language);
- **Implementation constraints** (no-regression, additive-only, single-owner-per-file, honesty,
  changelog/versioning/lessons duties, and the evidence-source rule: local Gradle and GitHub Actions
  are independent gates, so record only the concrete result that actually ran);
- **Files to inspect before editing**;
- **Exact execution steps** (ordered, incl. the workflow/agent decomposition used);
- **Files expected to change / be added**;
- **Verification** (how correctness + no-regression were confirmed);
- **Completion rule** (not done until version + changelog + versioning + verification are updated and
  no prior capability regressed).

## Jupiter invariants to assert in every "Regression checks" block

- **Build and tests are green in the available environment** (`assembleDebug` succeeds; unit tests
  pass). A local Gradle result and a GitHub Actions result are separate evidence: record each one that
  actually ran, and never claim a green CI run without its concrete run id/conclusion.
- **No public symbol / screen signature / enum constant removed or renamed**; new params are appended
  with behavior-preserving defaults; new UiState fields default to the pre-change behavior.
- **Storage scans never hang**: the media probe stays time-bounded (`MediaQualityProbe` withTimeoutOrNull),
  `findDuplicates`/`observeStorageOverview` stream incrementally, loading gates clear on first content,
  and a missing All-Files-Access shows the "Grant access" empty-state (not a dead spinner).
- **Data-safety invariants hold**: copy/move refuses a same-path self-overwrite; every permanent delete is
  confirmed; the vault stays LOCKED without a real biometric host; Duplicate cleanup protects the
  quality-ranked keeper, and the removed standalone Smart Merge route does not return.
- **Remote transport security holds**: FTPS PROT P, SFTP TOFU host-key rejection, secrets in EncryptedShared­Preferences.
- **The navigation graph + Hilt graph still resolve** (every `@Inject` dependency is provided; `JupiterNavHost`
  routes unchanged unless the change is the routing itself).

## Boundary

These files describe Jupiter's evolution only. Do not generate or copy them into a device/managed-file tree.
