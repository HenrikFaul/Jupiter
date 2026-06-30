# Sift — Architecture

A concise architecture-of-record for the implemented system, plus the planned
accelerated paths. Written under the FORGE discipline; the binding engineering rules are
in [`CODING_RULES.md`](CODING_RULES.md).

## 1. Process & threading model
```
┌─────────────────────────────── Tauri process ───────────────────────────────┐
│  React UI (webview)            IPC (commands + events)         Rust core      │
│  ─ screens, virtualized   ⇄  invoke()/listen()  ⇄  commands.rs ─ thin shell  │
│     tables, review gate                              │                        │
│                                                      ▼                        │
│                              AppState { db_path, Mutex<Connection> (writer),  │
│                                          Arc<AtomicBool> scan_cancel }        │
│   reads ── open_reader() ──► (many read-only connections, WAL snapshot)       │
│   writes ─ writer Mutex ───► single-writer connection                         │
│   scan ── std::thread ─────► ScanService::run(writer) ─ rayon pool for hashing │
└──────────────────────────────────────────────────────────────────────────────┘
```
- **Single writer, many readers** (CODING_RULES §6). The scan runs on a background
  thread holding the writer lock; the UI stays responsive because every read command
  opens its own read-only WAL connection.
- **Hashing parallelism** is a bounded `rayon` pool; results are applied to the DB on the
  single writer thread. Reads parallelize, mutations serialize.

## 2. The index (recovery truth)
SQLite/WAL, schema in [`migrations/0001_init.sql`](src-tauri/migrations/0001_init.sql).
Identity is keyed to **volumes** (GUID→serial→label), never drive letters, so offline
drives stay addressable. **Content** is content-addressed and retained forever; **files**
are path-sightings whose `status` flips on deletion but whose rows/history persist. This
is what powers cross-time, cross-drive, post-deletion duplicate recognition.

## 3. Scan pipeline (`engine/scan_service.rs`)
1. **Enumerate** each source (`walker.rs`) — reparse-safe, cancellable, per-entry errors
   collected not fatal. `upsert_file` reconciles metadata and **reuses prior hashes** when
   size+mtime are unchanged; changed files invalidate their stale hash.
2. **Mark unseen → missing** (knowledge preserved).
3. **Select candidates**: present, not-fully-hashed files whose size collides with another
   present file *or matches a size already in `content`* (the cross-time hook).
4. **Stage 1** parallel partial (head+tail) fingerprint → bucket by (size, partial).
5. **Stage 2** parallel full BLAKE3 for survivors → `get_or_create_content` → `attach` →
   `refresh_cluster`. Checkpointed to `scan_job` throughout; progress streamed as events.

## 4. Deletion (`engine/deletion_service.rs`)
`select → preview → validate(engine) → confirm → commit`. The engine **re-validates** the
plan (defense in depth), writes the **audit intent row before** each removal, defaults to
**Recycle Bin** (via the cross-platform `trash` crate), and returns an honest
`success | partial | failed` outcome. The keep-at-least-one-online-copy and
keep-at-least-one-known-copy invariants live in `safety.rs` and cannot be bypassed.

## 5. Planned accelerated / Phase-3+ paths
- **NTFS MFT + USN journal enumerator** (`engine/ntfs/`, admin-only): reads the Master
  File Table directly and tails the USN change journal for near-instant, Everything-class
  enumeration and live incremental updates. Emits the same `walker::Entry` stream, so
  steps 2–5 above are unchanged; the portable walker remains the fallback for non-NTFS /
  non-elevated runs (graceful degradation).
- **Background indexer** as a Windows scheduled task running the same engine headless.
- **Duplicate-folder equivalence**, **smart-selection rule builder**, **reports export**,
  **protected-paths / retention policies**.

## 6. Why these choices (trade-offs)
- **Tauri + Rust**: smallest signed EXE, deepest OS-level FS access, no GC stalls at
  millions of files, engine is a pure testable library crate. Cost: Rust maintenance — kept
  low by isolating the engine and unit-testing it.
- **SQLite over an embedded KV**: transactional integrity (constraints enforce invariants
  at the DB, FORGE Law 2), one portable file, mature WAL concurrency.
- **BLAKE3 over SHA-256**: faster (SIMD), strong enough; dedup does not need collision
  resistance against an adversary.
