# Sift — Engineering Rules & Non-Regression Charter

> Derived from `FORGE/kernel/00_KERNEL.system.md` (the ten global laws) and the
> accumulated `codingLessonsLearnt*.md`. This file is **binding**, **append-only**,
> and enforced by automated gates wherever possible (see `§ Gates`). A rule that is
> only written down drifts silently; a rule that fails a build cannot. Encode every
> recurring failure here *and* as a gate.

## 0. Prime law — non-regression
- **Never break already-working functionality.** Every change re-verifies prior behavior.
  When ≥2 approaches exist, choose the lowest-regression-risk one and say why.
- Updates are **additive or strengthening only**. Removing/weakening a working capability
  is the one unacceptable outcome. Migrations must be named, justified, tested, reversible.

## 1. Data-loss safety (this app deletes files — non-negotiable)
- **Never auto-delete.** The only path to removal is explicit `select → preview → confirm → commit`.
- **Default to Recycle Bin / quarantine**, never permanent delete. Permanent delete requires a
  second, explicit confirmation and is logged.
- **Keep-at-least-one invariant:** the engine must refuse any plan that would remove the last
  known *online* surviving copy of a content hash. Enforced in the engine, not just the UI.
- **Write the audit row BEFORE the destructive op**, inside the same transaction boundary,
  so a crash mid-delete leaves a record of intent. (lesson ORDER-001)
- **Partial success is not success.** A batch delete returns `success | partial | failed`
  with the explicit list of failed items and reasons. Never report "OK" for a partial run.
  (lesson PARTIAL-FAIL-001)
- Destructive QA runs only against a throwaway scratch index, never the user's real DB. (F013)

## 2. Error handling — fail loud, fail fast, never fail silent
- **Every fs / hash / DB call's error is checked and surfaced.** No swallowed `Result`/`Err`.
  A file that fails to stat/hash is recorded with an error status, never silently dropped.
  (lessons CAPACITY-ERROR-001, SUPABASE-SDK-086)
- Distinguish **empty result** from **call failed** at every boundary. `len == 0` is not an error
  signal. (lesson RPC-091)
- Rust: no `.unwrap()` / `.expect()` on fallible I/O in engine paths — propagate typed errors.
  `unwrap` is allowed only on invariants that are statically true (and commented as such).

## 3. Persistence & crash recovery (the index is the product)
- The SQLite index is **recovery truth**. All long-job state (scan cursor, hash queue) is
  checkpointed to it so an interrupted run resumes.
- **Resume must NEVER silently degrade into "start fresh."** An invalid/corrupt checkpoint is a
  hard, surfaced error with explicit user choice — never an auto-rescan that overwrites knowledge.
  (lesson F022)
- All multi-writer state writes are **serialized (single-writer) and atomic** (WAL + transactions;
  file artifacts via temp-write + rename). (lessons F022, TOCTOU-001)
- Schema changes go through **versioned migrations** persisted to `migrations/`. `CREATE TABLE IF
  NOT EXISTS` does NOT alter an existing table — never rely on it for evolution. (lesson F014)

## 4. SQLite discipline
- One writer connection (serialized); many reader connections. `PRAGMA journal_mode=WAL`,
  `foreign_keys=ON`, `busy_timeout` set. (see `src/db/mod.rs`)
- **Aggregates MUST be aliased and read by alias** (`COUNT(*) AS n` → read `n`). (lesson F020)
- Keep Rust structs, schema DDL, and query columns in exact lockstep. Verify column names against
  the DDL before writing a query. Mismatches return empty/undefined with no error. (lesson SCHEMA-001)
- Push filtering into SQL with indexed predicates — never load all rows and filter in app code.
  (lesson QUERY-SCOPE-001)

## 5. Windows file-system correctness (the gap the lessons file did NOT cover — harden proactively)
- **Long paths:** all path I/O tolerates > 260 chars. Use `\\?\`-prefixed verbatim paths for raw
  Win32 calls; never assume MAX_PATH.
- **Unicode:** store paths losslessly (UTF-16 ↔ UTF-8 via `OsStr`); normalize a *separate*
  display/compare key with NFC. Never lose original bytes. (lesson EMAIL-DIACRITIC-001 generalized)
- **Case:** NTFS is case-insensitive, case-preserving. Path-equality compares case-folded; the
  stored original case is preserved for display and deletion.
- **Reserved names / ADS / junctions:** detect reserved device names (CON, PRN, …), alternate data
  streams, reparse points/junctions/symlinks. **Never recurse through a reparse point by default**
  (cycle + double-count hazard); record it, follow only on explicit opt-in.
- Volume identity is captured so offline drives remain addressable (see `src/identity.rs`).

## 6. Concurrency
- **Reads/scans parallelize; mutations serialize.** Hashing is a bounded worker pool (≈ CPU cores,
  capped); the DB has a single writer task fed by a channel. (lessons "single-writer", F009)
- Worker-pool size is bounded and configurable; queue wait counts toward timeouts. Never spawn
  unbounded tasks per file.
- Cancellation: every long job honors a cancel token and stops in-flight work cleanly. (CLEANUP-001)
- Guard concurrent state transitions with atomic `UPDATE ... WHERE <expected>` + affected-row check
  (TOCTOU). (lesson TOCTOU-001)

## 7. Performance at scale (millions of files)
- Multi-stage narrowing — never hash everything up front. Order: metadata → group by size →
  partial head/tail fingerprint → full content hash only for survivors. (see `src/hashing.rs`)
- Reuse prior index: a file unchanged by (size, mtime, volume, id) is NOT re-hashed. (incremental)
- Build in-memory `HashMap` indexes once for grouping; never nested O(n²) scans. (ORGCHART-FLATTEN-001)
- Stream/batch DB writes; debounce UI progress events (~250 ms). (TIMELINE-FETCH-001)

## 8. Auditability & logging
- Every destructive action and every privileged operation writes a structured audit row.
  Audit table is append-only; never written directly outside the controlled path. (AUDIT-091/092)
- Logs are structured, replayable, locale-neutral in engine code (`"Unknown"`, not a localized
  word). Show evidence (actual command/error output), not assertions. (LOCALE-002)

## 9. Code shape
- Functions ≤ ~40 lines, ≤ 3 nesting levels, early returns. (lesson F003)
- One source of truth for any list/registry; config-driven over per-entity code when ≥3 similar
  cases. (SSOT-001, IMPORT-081)
- Remove dead code immediately — orphaned items break builds and become latent risks. (DEAD-CODE-002)
- Generate/verify in dependency order: model → db → engine services → tauri commands → UI.

## Gates (automated enforcement — CI fails the build)
- `cargo clippy -D warnings` with a denylist for `unwrap()/expect()/panic!` in `engine`/`db` modules.
- A source-scanning test that asserts: every `rusqlite` aggregate query has an `AS` alias; no
  `CREATE TABLE IF NOT EXISTS` outside the migration runner; every public engine fn returns `Result`.
- A schema-drift test comparing struct field names against `PRAGMA table_info`.
- A "delete-safety" integration test: a plan that would remove the last online copy is rejected.
- `eslint` + `tsc --noEmit` on the frontend; no hardcoded colors (token-only) check.

> When a new failure class appears: (1) fix the root cause, (2) add a rule here, (3) add a gate.
> Append-only. Positively phrased: "instead of X, ALWAYS do Y."
