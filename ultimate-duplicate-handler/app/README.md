# Sift — Persistent Duplicate & File-Index Intelligence (Windows)

> A serious desktop deduplication **workstation**: the indexing mindset of *Everything*,
> the duplicate-management UX of *Cisdem*, plus a **persistent historical intelligence
> layer** that remembers files across drives and time — even after a drive is unplugged
> or a copy is deleted. Built under the FORGE engineering discipline and the
> non-regression charter in [`CODING_RULES.md`](CODING_RULES.md).

## Why this is not a toy
- **Native engine in Rust** (OS-level file access, BLAKE3, bounded parallel hashing,
  NTFS volume identity) behind a **Tauri** shell → a real, small, signed Windows `.exe`.
- **Durable SQLite index** that is *recovery truth* — survives restart, reboot, and drive
  disconnect; knowledge persists until the user clears it.
- **Historical duplicate intelligence**: content identity (`content` table) is preserved
  forever, so newly-connected drives are matched against everything ever seen.
- **Deletion safety enforced in the engine**, not the UI: keep-at-least-one-online-copy
  is impossible to bypass; everything is audited; partial failures are reported as partial.

## Stack
| Layer | Choice | Why |
|---|---|---|
| Shell / window / installer | **Tauri 2** | Tiny native EXE, NSIS/MSI, web UI freedom |
| Engine | **Rust** | OS-level FS access, fearless concurrency, BLAKE3, no GC stalls at scale |
| Index | **SQLite (WAL)** via `rusqlite` | Single-file durable index, transactional safety |
| UI | **React + TypeScript + Tailwind**, virtualized tables | Premium, information-dense, fast |

## Project layout
```
app/
├─ CODING_RULES.md          ← binding non-regression charter (FORGE + lessons)
├─ src-tauri/               ← Rust engine + Tauri shell
│  ├─ Cargo.toml
│  ├─ tauri.conf.json
│  ├─ migrations/
│  │  └─ 0001_init.sql      ← the persistent index schema (history-aware)
│  └─ src/
│     ├─ lib.rs             ← testable core library crate (sift_core)
│     ├─ main.rs            ← Tauri binary entrypoint
│     ├─ model.rs           ← IPC contract DTOs (Rust side)
│     ├─ commands.rs        ← Tauri command surface (typed IPC)
│     ├─ db/mod.rs          ← connection, PRAGMAs, versioned migrations
│     └─ engine/
│        ├─ hashing.rs      ← size → partial → full BLAKE3 cascade  (✔ implemented + tests)
│        ├─ identity.rs     ← Windows volume/device identity        (✔ implemented + tests)
│        ├─ pathkey.rs      ← long-path / Unicode / case / reserved (✔ implemented + tests)
│        ├─ safety.rs       ← keep-at-least-one-copy invariant       (✔ implemented + tests)
│        └─ walker.rs       ← enumeration, reparse-safe, cancellable (✔ implemented + tests)
└─ src/                     ← React UI
   ├─ lib/contract.ts       ← TS mirror of the Rust contract
   └─ features/review/DeletionReview.tsx  ← engine-authoritative delete gate
```

## Portable — runs from any folder, any Windows PC
The index lives in **`Sift-Data\` next to the EXE** (see [`paths.rs`](src-tauri/src/paths.rs)),
so copying the folder carries your whole index — including the history of drives that are
currently disconnected. Build it with one command:
```powershell
./scripts/build-portable.ps1     # -> app/Sift-Portable/ {Sift.exe, Sift-Data/, README.txt}
```
See [`BUILD.md`](BUILD.md). No installer, no admin (admin only unlocks the faster NTFS scan).

## Verification status — COMPILES, TESTS PASS, RUNS (verified on Windows 11)
- ✅ **Engine**: `cargo test --lib` → **28/28 tests pass** (hashing, identity, pathkey,
  safety, walker, db, scan end-to-end, deletion safety, selection ×5, dupfolder ×2).
- ✅ **Full app binary**: `cargo build` → compiles clean (main.rs, all Tauri commands,
  `generate_handler!`, `generate_context!`, Windows FFI, icons). No errors, no warnings.
- ✅ **Frontend**: `tsc --noEmit` clean + `vite build` (62 modules).
- ✅ **Portable EXE**: `build-portable.ps1` produced `Sift-Portable/Sift.exe` (~14 MB).
- ✅ **Runtime smoke test**: the portable EXE boots, creates `Sift-Data\index.sqlite`
  (valid `SQLite format 3`, full 0001+0002 schema in the WAL), runs without crashing, and
  **re-boots recovering the existing index** — proving persistence across restarts.
- A 6-dimension multi-agent adversarial review (run before the toolchain was available)
  caught 3 real issues that are now fixed: a non-exhaustive `match` (E0004) in `safety.rs`,
  missing build-time icons, and a `?`-early-return in `deletion_service.rs` that violated the
  partial-failure contract.

## Implementation status
**Engine — implemented with unit tests** (`cargo test --lib`):
- `hashing` (size→partial→full cascade), `identity` (Windows volume identity),
  `pathkey` (case/Unicode/long-path/reserved), `safety` (keep-at-least-one-copy),
  `walker` (reparse-safe, cancellable), `db` (PRAGMAs + idempotent migrations + integrity).
- `db/repo` — the full repository: volumes/presence, files+content with incremental
  reuse, counter & cluster maintenance, presence/historical queries, index search, audit.
- `engine/scan_service` — end-to-end scan (enumerate → reconcile → staged hash → cluster),
  cancellable, checkpointed, with an end-to-end "finds a duplicate" test.
- `engine/deletion_service` — audit-before-act executor (Recycle/quarantine/permanent),
  engine-revalidated, partial-failure honest; test proves it refuses to destroy a last copy.

**Shell + UI — implemented**: full Tauri command surface (`commands.rs`), the React app
with all eight screens (Home, Sources, Scan Builder, Scan Monitor, Duplicates/Review,
Index Explorer, Reports/Audit, Settings), token-only theming, and the engine-authoritative
deletion gate.

**Not yet wired (Phase 3+)**: the NTFS MFT/USN accelerated enumerator (a faster backend
behind the same `Entry` stream), background/scheduled indexing, smart-selection rule
builder, duplicate-folder detection, and reports export. See `ARCHITECTURE.md`.

> Honest note: this workspace has no Rust toolchain, so the engine has **not been
> compiled here**. First task on a real machine is `cargo test --lib` (engine, no UI) then
> `npm install && npm run tauri dev`. Code is written for correctness, not yet machine-verified.

## Build & test
```powershell
# engine unit tests (no UI/Tauri needed — compiles only the sift_core library)
cd app/src-tauri ; cargo test --lib

# full desktop app (after: rustup + Node + Tauri prereqs)
cd app ; npm install ; npm run tauri dev
```
