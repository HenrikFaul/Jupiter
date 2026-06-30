//! Scan orchestrator — ties walker → incremental reconcile → staged hashing cascade →
//! cluster maintenance, with progress events and checkpointing to `scan_job`
//! (recovery truth — CODING_RULES §3). Reads parallelize (rayon); the DB has a single
//! writer (CODING_RULES §6), so all mutations happen on the calling (writer) thread.
//!
//! Pipeline:
//!   1. Enumerate each source, upsert metadata, mark unseen files 'missing'.
//!   2. Select candidates: present, not-yet-fully-hashed files whose size collides with
//!      another present file OR matches a size already in `content` (the cross-time hook).
//!   3. Stage 1 (parallel): partial head+tail fingerprint.
//!   4. Stage 2 (parallel, thorough mode): full BLAKE3 for files surviving partial
//!      grouping; attach to content; refresh clusters & counters.

use crate::db::repo::{self, FileMeta};
use crate::engine::hashing;
use crate::engine::identity;
use crate::engine::pathkey;
use crate::engine::walker::{self, Entry};
use crate::model::{ScanFilters, ScanLogEvent, ScanMode, ScanProgress};
use rayon::prelude::*;
use rusqlite::{params, Connection};
use std::cell::Cell;
use std::collections::HashMap;
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

pub struct Source {
    pub volume_mount: String, // e.g. "E:\"
    pub rel_root: Option<String>, // folder within the volume, or None for whole volume
    pub source_id: Option<i64>,
}

pub struct ScanService;

impl ScanService {
    /// Run a full scan synchronously on the writer thread. `cancel` aborts promptly;
    /// `emit` receives debounced progress (caller decides throttling). Returns job id.
    #[allow(clippy::too_many_arguments)]
    pub fn run(
        conn: &Connection,
        sources: &[Source],
        filters: &ScanFilters,
        mode: ScanMode,
        cancel: Arc<AtomicBool>,
        mut emit: impl FnMut(ScanProgress),
        mut emit_log: impl FnMut(ScanLogEvent),
    ) -> Result<i64, String> {
        let started = repo::now();
        let scan_start = Instant::now();
        let job_id = create_job(conn).map_err(|e| format!("create job: {e}"))?;
        let mode_str = match mode {
            ScanMode::Thorough => "thorough",
            ScanMode::Lightweight => "lightweight",
        };

        // Cell-based counters shared by the walk closures (two closures can't both hold a
        // &mut to a plain local; Cells let them share via shared borrows).
        let files_seen = Cell::new(0u64);
        let hashed = Cell::new(0u64);
        let bytes = Cell::new(0u64);
        let errors = Cell::new(0u64);
        let skipped = Cell::new(0u64);
        let folders_done = Cell::new(0u64);
        let scanning_ms = Cell::new(0u64);
        let hashing_ms = Cell::new(0u64);
        let last_minute = Cell::new(scan_start); // Instant is Copy
        let mut drive_label: Option<String> = None;

        let el = || scan_start.elapsed().as_millis() as i64;

        // "started" event (persisted + streamed).
        let msg = format!("Scan started · {} source(s) · {} mode", sources.len(), mode_str);
        let _ = repo::log_event(conn, job_id, 0, "started", &msg, 0, None);
        emit_log(mk_event(job_id, 0, "started", &msg, 0));

        // ---- The scan body. Every cancel point breaks to the single finalize below. ----
        let final_state: &str = 'scan: {
            // === Phase 1: enumerate + reconcile metadata ===
            update_job(conn, job_id, "enumerating", 0, 0, 0);
            let enum_start = Instant::now();
            for src in sources {
                if cancel.load(Ordering::Relaxed) {
                    break 'scan "cancelled";
                }
                let id = identity::identify_mount(&src.volume_mount)
                    .map_err(|e| format!("identify {}: {e}", src.volume_mount))?;
                if drive_label.is_none() {
                    drive_label = id.fs_label.clone().or_else(|| Some(src.volume_mount.clone()));
                }
                let volume_id = repo::upsert_online_volume(conn, &id, &src.volume_mount)
                    .map_err(|e| format!("upsert volume: {e}"))?;

                let label = id.fs_label.clone().unwrap_or_else(|| src.volume_mount.clone());
                let smsg = format!("Source mounted: {} ({})", label, src.volume_mount);
                let _ = repo::log_event(conn, job_id, el(), "source", &smsg, files_seen.get() as i64, None);
                emit_log(mk_event(job_id, el(), "source", &smsg, files_seen.get() as i64));

                let root = match &src.rel_root {
                    Some(r) => Path::new(&src.volume_mount).join(r),
                    None => Path::new(&src.volume_mount).to_path_buf(),
                };

                // --- Accelerated NTFS MFT path (whole-volume, Windows, elevated). Auto
                //     fallback to the directory walk; folder-tree detail is walk-only. ---
                let mut used_mft = false;
                #[cfg(windows)]
                if src.rel_root.is_none() && is_volume_root(&src.volume_mount) {
                    match crate::engine::ntfs::enumerate_mft(&src.volume_mount, &cancel) {
                        Ok(mft_files) => {
                            let m = format!("MFT acceleration active · {} entries", mft_files.len());
                            let _ = repo::log_event(conn, job_id, el(), "stage", &m, files_seen.get() as i64, None);
                            emit_log(mk_event(job_id, el(), "stage", &m, files_seen.get() as i64));
                            for mf in &mft_files {
                                if cancel.load(Ordering::Relaxed) {
                                    break;
                                }
                                let abs = Path::new(&src.volume_mount).join(&mf.rel_path);
                                match walker::entry_for(&abs) {
                                    Ok(e) => {
                                        if passes_filters(&e, filters) {
                                            match index_entry(conn, volume_id, &src.volume_mount, src.source_id, job_id, &e) {
                                                Ok(()) => {
                                                    let n = files_seen.get() + 1;
                                                    files_seen.set(n);
                                                    if n % 300 == 0 {
                                                        emit(progress(job_id, "enumerating", n, 0, 0, 0));
                                                    }
                                                    minute_tick(conn, job_id, &el, &last_minute, files_seen.get(), folders_done.get(), &mut emit_log);
                                                }
                                                Err(_) => errors.set(errors.get() + 1),
                                            }
                                        }
                                    }
                                    Err(_) => errors.set(errors.get() + 1),
                                }
                            }
                            used_mft = true;
                        }
                        Err(e) => {
                            tracing::info!(error = %e, volume = %src.volume_mount, "MFT unavailable; directory walk");
                        }
                    }
                }

                if !used_mft {
                    let cancel2 = cancel.clone();
                    let dir_start = Cell::new(Instant::now());
                    walker::walk(
                        &root,
                        filters.follow_reparse,
                        |e| {
                            if !passes_filters(&e, filters) {
                                return;
                            }
                            match index_entry(conn, volume_id, &src.volume_mount, src.source_id, job_id, &e) {
                                Ok(()) => {
                                    let n = files_seen.get() + 1;
                                    files_seen.set(n);
                                    if n % 300 == 0 {
                                        emit(progress(job_id, "enumerating", n, 0, 0, 0));
                                    }
                                }
                                Err(_) => errors.set(errors.get() + 1),
                            }
                        },
                        |_werr| errors.set(errors.get() + 1),
                        |dev| {
                            // Folder traversal → folder_traversal table + live log event.
                            let rel = relative_to_mount(&dev.path.to_string_lossy(), &src.volume_mount);
                            match dev.phase {
                                walker::DirPhase::Entered => {
                                    dir_start.set(Instant::now());
                                    let _ = repo::folder_enter(conn, job_id, volume_id, &rel, dev.depth as i64);
                                }
                                walker::DirPhase::Completed => {
                                    let dur = dir_start.get().elapsed().as_millis() as i64;
                                    let _ = repo::folder_complete(conn, job_id, &rel, dev.file_count as i64, dur);
                                    folders_done.set(folders_done.get() + 1);
                                    // Live folder event (UI only — not persisted per-folder to avoid bloat).
                                    let fm = format!("Completed: {} ({} files)", rel, dev.file_count);
                                    emit_log(mk_event(job_id, el(), "folder", &fm, files_seen.get() as i64));
                                    minute_tick(conn, job_id, &el, &last_minute, files_seen.get(), folders_done.get(), &mut emit_log);
                                }
                                walker::DirPhase::Skipped => {
                                    let _ = repo::folder_mark(conn, job_id, volume_id, &rel, dev.depth as i64, "skipped");
                                    skipped.set(skipped.get() + 1);
                                }
                                walker::DirPhase::Failed => {
                                    let _ = repo::folder_mark(conn, job_id, volume_id, &rel, dev.depth as i64, "failed");
                                    errors.set(errors.get() + 1);
                                }
                            }
                        },
                        || !cancel2.load(Ordering::Relaxed),
                    );
                }

                // Files previously present under this root but not seen now → 'missing'
                // (skipped on cancel: a partial scan hasn't seen everything).
                if !cancel.load(Ordering::Relaxed) {
                    let prefix = src.rel_root.as_ref().map(|r| pathkey::make_key(r));
                    let _ = repo::mark_unseen_missing(conn, volume_id, started, prefix.as_deref());
                }
            }
            scanning_ms.set(enum_start.elapsed().as_millis() as u64);

            if cancel.load(Ordering::Relaxed) {
                break 'scan "cancelled";
            }

            // === Phase 2: candidate selection + staged hashing ===
            let hash_start = Instant::now();
            update_job(conn, job_id, "hashing", files_seen.get(), 0, 0);
            let hm = format!("Hashing stage · {} files indexed", files_seen.get());
            let _ = repo::log_event(conn, job_id, el(), "stage", &hm, files_seen.get() as i64, None);
            emit_log(mk_event(job_id, el(), "stage", &hm, files_seen.get() as i64));

            let candidates = select_candidates(conn).map_err(|e| format!("candidates: {e}"))?;
            emit(progress(job_id, "hashing", files_seen.get(), 0, 0, candidates.len() as u64));

            let partials: Vec<(Candidate, Option<hashing::Digest>)> = candidates
                .par_iter()
                .map(|c| {
                    if cancel.load(Ordering::Relaxed) {
                        return (c.clone(), None);
                    }
                    (c.clone(), hashing::partial_hash(Path::new(&c.abs_path), c.size as u64).ok())
                })
                .collect();

            let mut buckets: HashMap<(i64, Vec<u8>), Vec<&Candidate>> = HashMap::new();
            for (c, p) in &partials {
                if let Some(d) = p {
                    buckets.entry((c.size, d.to_vec())).or_default().push(c);
                }
            }
            let needs_full: Vec<&Candidate> = match mode {
                ScanMode::Lightweight => Vec::new(),
                ScanMode::Thorough => buckets
                    .values()
                    .filter(|v| v.len() > 1)
                    .flat_map(|v| v.iter().copied())
                    .collect(),
            };

            // Full-hash in BYTE-BUDGETED batches and report progress after EACH batch.
            // The old all-at-once `par_iter().collect()` only emitted progress after the
            // whole set finished — with multi-GB media files that meant a long silent
            // stretch that looked frozen. Batching by ~bytes (≥1 file, capped count) keeps
            // FILES/BYTES HASHED moving and the minute-ticks flowing through huge files.
            let total_full = needs_full.len() as u64;
            if total_full > 0 {
                let fm = format!("Confirming {total_full} candidate(s) by full content hash");
                let _ = repo::log_event(conn, job_id, el(), "stage", &fm, files_seen.get() as i64, None);
                emit_log(mk_event(job_id, el(), "stage", &fm, files_seen.get() as i64));
                // Immediate target so the UI shows 0/total before the first (possibly large) batch.
                emit(progress(job_id, "hashing", files_seen.get(), 0, 0, total_full));
            }
            const BYTE_BUDGET: u64 = 2 * 1024 * 1024 * 1024; // ~2 GB of reads per batch
            const MAX_BATCH: usize = 64;
            let mut i = 0usize;
            let mut aborted = false;
            while i < needs_full.len() {
                if cancel.load(Ordering::Relaxed) {
                    aborted = true;
                    break;
                }
                // Grow a batch up to the byte budget (always at least one file). A single
                // 5 GB file becomes its own batch → progress emitted after that one file.
                let mut j = i;
                let mut batch_bytes = 0u64;
                while j < needs_full.len() && j - i < MAX_BATCH && (j == i || batch_bytes < BYTE_BUDGET) {
                    batch_bytes += needs_full[j].size.max(0) as u64;
                    j += 1;
                }
                let results: Vec<(i64, i64, Option<Vec<u8>>, Result<hashing::Digest, String>)> =
                    needs_full[i..j]
                        .par_iter()
                        .map(|c| {
                            let part = partial_for(&partials, c.file_id);
                            let res = hashing::full_hash(Path::new(&c.abs_path)).map_err(|e| e.to_string());
                            (c.file_id, c.size, part, res)
                        })
                        .collect();
                for (file_id, size, partial, res) in results {
                    match res {
                        Ok(digest) => {
                            let content_id = repo::get_or_create_content(conn, &digest, size, partial.as_deref())
                                .map_err(|e| format!("content: {e}"))?;
                            repo::attach_content(conn, file_id, content_id, true)
                                .map_err(|e| format!("attach: {e}"))?;
                            repo::refresh_cluster(conn, content_id).map_err(|e| format!("cluster: {e}"))?;
                            hashed.set(hashed.get() + 1);
                            bytes.set(bytes.get() + size as u64);
                        }
                        Err(emsg) => {
                            let _ = repo::mark_file_error(conn, file_id, &emsg);
                            errors.set(errors.get() + 1);
                        }
                    }
                }
                // The key fix: progress + minute-tick after every batch (not once at the end).
                emit(progress(job_id, "hashing", files_seen.get(), hashed.get(), bytes.get(), total_full));
                minute_tick(conn, job_id, &el, &last_minute, files_seen.get(), folders_done.get(), &mut emit_log);
                i = j;
            }
            hashing_ms.set(hash_start.elapsed().as_millis() as u64);
            if aborted {
                break 'scan "cancelled";
            }

            update_job(conn, job_id, "clustering", files_seen.get(), hashed.get(), bytes.get());
            "done"
        };

        // ---- Single finalize path (reached for done AND cancelled) ----
        let resumable = final_state == "cancelled";
        let sources_desc: Vec<serde_json::Value> = sources
            .iter()
            .map(|s| serde_json::json!({"mount": s.volume_mount, "rel": s.rel_root}))
            .collect();
        // Bind serialized strings to locals so the &str borrows in SessionMetrics live.
        let sources_json = serde_json::to_string(&sources_desc).ok();
        let filters_json = serde_json::to_string(filters).ok();
        let metrics = repo::SessionMetrics {
            job_id,
            started_at: started,
            state: final_state,
            mode: mode_str,
            drive_label: drive_label.as_deref(),
            sources_json: sources_json.as_deref(),
            filters_json: filters_json.as_deref(),
            profile_name: None,
            skipped_count: skipped.get() as i64,
            error_count: errors.get() as i64,
            scanning_ms: scanning_ms.get() as i64,
            hashing_ms: hashing_ms.get() as i64,
            resumable,
        };
        let _ = repo::finalize_scan_session(conn, &metrics);
        let _ = finish(conn, job_id, final_state, None);
        // Flush the WAL into index.sqlite so this scan is durable on disk immediately — the
        // index must survive a close/reopen (and travel intact if the folder is copied).
        let _ = crate::db::checkpoint(conn);

        // Terminal event + final progress, both persisted + streamed. The event KIND is
        // "completed"/"cancelled" (UI vocabulary) even though scan_job.state is "done".
        let term_kind = if final_state == "done" { "completed" } else { final_state };
        let done_msg = if final_state == "cancelled" {
            format!("Scan cancelled · {} files indexed, {} hashed", files_seen.get(), hashed.get())
        } else {
            format!("Scan completed · {} files, {} hashed, {} errors", files_seen.get(), hashed.get(), errors.get())
        };
        let _ = repo::log_event(conn, job_id, el(), term_kind, &done_msg, files_seen.get() as i64, None);
        emit_log(mk_event(job_id, el(), term_kind, &done_msg, files_seen.get() as i64));
        emit(progress(job_id, "done", files_seen.get(), hashed.get(), bytes.get(), 0));

        if errors.get() > 0 {
            tracing::warn!(errors = errors.get(), "scan finished with per-file errors");
        }
        Ok(job_id)
    }
}

/// Build a streamable log event (mirrors what we persist to scan_log_event).
fn mk_event(job_id: i64, elapsed_ms: i64, kind: &str, message: &str, files: i64) -> ScanLogEvent {
    ScanLogEvent {
        job_id,
        at: repo::now(),
        elapsed_ms,
        kind: kind.to_string(),
        message: message.to_string(),
        files_processed: files,
    }
}

/// Emit a once-per-minute progress entry (persisted + streamed) when ≥60s have elapsed
/// since the last one. Guarantees visible forward movement to detect stalls.
fn minute_tick(
    conn: &Connection,
    job_id: i64,
    el: &impl Fn() -> i64,
    last_minute: &Cell<Instant>,
    files: u64,
    folders: u64,
    emit_log: &mut impl FnMut(ScanLogEvent),
) {
    if last_minute.get().elapsed() >= Duration::from_secs(60) {
        last_minute.set(Instant::now());
        let secs = el() / 1000;
        let msg = format!(
            "Progress · {}m{:02}s elapsed · {} files processed · {} folders completed",
            secs / 60, secs % 60, files, folders
        );
        let _ = repo::log_event(conn, job_id, el(), "progress", &msg, files as i64, None);
        emit_log(mk_event(job_id, el(), "progress", &msg, files as i64));
    }
}

#[derive(Clone)]
struct Candidate {
    file_id: i64,
    size: i64,
    abs_path: String,
}

fn partial_for(
    partials: &[(Candidate, Option<hashing::Digest>)],
    file_id: i64,
) -> Option<Vec<u8>> {
    partials
        .iter()
        .find(|(c, _)| c.file_id == file_id)
        .and_then(|(_, d)| d.map(|x| x.to_vec()))
}

/// Candidates: present, not yet full-hashed, whose size collides with another present
/// file OR matches a size already recorded in `content`. The latter is what lets a
/// freshly connected drive match content known from earlier (incl. offline/deleted).
fn select_candidates(conn: &Connection) -> rusqlite::Result<Vec<Candidate>> {
    // Fetch the volume mount + the volume-relative path, then join with the OS path
    // separator in Rust (Path::join) — never hardcode a separator in SQL (portable,
    // and correct on the actual target volume).
    let mut stmt = conn.prepare(
        "SELECT f.id AS id, f.size_bytes AS sz, v.current_mount_point AS mount, f.path_raw AS path \
         FROM file f JOIN volume v ON v.id=f.volume_id \
         WHERE f.status='present' AND v.is_online=1 AND f.hash_state IN ('none','partial') \
           AND f.size_bytes > 0 AND v.current_mount_point IS NOT NULL \
           AND ( f.size_bytes IN (SELECT size_bytes FROM file WHERE status='present' \
                                  GROUP BY size_bytes HAVING COUNT(*) > 1) \
              OR f.size_bytes IN (SELECT size_bytes FROM content) )",
    )?;
    let rows = stmt
        .query_map([], |r| {
            let mount: String = r.get("mount")?;
            let rel: String = r.get("path")?;
            let abs = Path::new(&mount).join(&rel).to_string_lossy().to_string();
            Ok(Candidate {
                file_id: r.get("id")?,
                size: r.get("sz")?,
                abs_path: abs,
            })
        })?
        .collect::<rusqlite::Result<_>>()?;
    Ok(rows)
}

fn index_entry(
    conn: &Connection,
    volume_id: i64,
    mount: &str,
    source_id: Option<i64>,
    scan_job_id: i64,
    e: &Entry,
) -> rusqlite::Result<()> {
    let abs = e.path.to_string_lossy().to_string();
    let rel = relative_to_mount(&abs, mount);
    let file_name = e
        .path
        .file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_else(|| rel.clone());
    let ext = pathkey::extension_of(&file_name);
    let key = pathkey::make_key(&rel);
    let meta = FileMeta {
        volume_id,
        path_raw: &rel,
        path_key: &key,
        file_name: &file_name,
        ext: ext.as_deref(),
        size_bytes: e.size as i64,
        created_at: e.created,
        modified_at: e.modified,
        is_hidden: e.is_hidden,
        is_system: e.is_system,
        is_reparse: e.is_reparse,
        source_id,
        scan_job_id: Some(scan_job_id),
    };
    repo::upsert_file(conn, &meta).map(|_| ())
}

/// True only for a real drive root like "E:\" or "E:" — NOT a deeper folder. MFT
/// enumeration opens the whole volume device, so it must never run for a subfolder
/// (which would otherwise enumerate the entire drive the folder happens to live on).
#[cfg(windows)]
fn is_volume_root(mount: &str) -> bool {
    let t = mount.trim_end_matches(['\\', '/']);
    let b = t.as_bytes();
    b.len() == 2 && b[1] == b':' && b[0].is_ascii_alphabetic()
}

/// Strip the volume mount prefix to get a volume-relative path (case-insensitive).
fn relative_to_mount(abs: &str, mount: &str) -> String {
    let m = mount.trim_end_matches('\\');
    if abs.len() >= m.len() && abs[..m.len()].eq_ignore_ascii_case(m) {
        abs[m.len()..].trim_start_matches('\\').to_string()
    } else {
        abs.to_string()
    }
}

fn passes_filters(e: &Entry, f: &ScanFilters) -> bool {
    // Never index system areas that are not user data — the per-volume Recycle Bin and the
    // System Volume Information store. Path-based so it covers BOTH the MFT-accelerated and the
    // portable walker backends (the MFT path doesn't go through walker's dir skip).
    let lower = e.path.to_string_lossy().to_ascii_lowercase();
    if lower.contains("\\$recycle.bin\\")
        || lower.contains("/$recycle.bin/")
        || lower.contains("\\system volume information\\")
        || lower.contains("/system volume information/")
    {
        return false;
    }
    if f.skip_zero_byte && e.size == 0 {
        return false;
    }
    if !f.include_hidden && e.is_hidden {
        return false;
    }
    if !f.include_system && e.is_system {
        return false;
    }
    if let Some(min) = f.min_size_bytes {
        if e.size < min {
            return false;
        }
    }
    if let Some(max) = f.max_size_bytes {
        if e.size > max {
            return false;
        }
    }
    if let Some(after) = f.modified_after {
        if e.modified.map(|m| m < after).unwrap_or(false) {
            return false;
        }
    }
    if let Some(before) = f.modified_before {
        if e.modified.map(|m| m > before).unwrap_or(false) {
            return false;
        }
    }
    let ext = e
        .path
        .file_name()
        .and_then(|n| pathkey::extension_of(&n.to_string_lossy()));
    if !f.include_exts.is_empty() {
        match &ext {
            Some(x) if f.include_exts.iter().any(|i| i.eq_ignore_ascii_case(x)) => {}
            _ => return false,
        }
    }
    if let Some(x) = &ext {
        if f.exclude_exts.iter().any(|i| i.eq_ignore_ascii_case(x)) {
            return false;
        }
    }
    true
}

// ---- scan_job persistence (recovery truth) ----

fn create_job(conn: &Connection) -> rusqlite::Result<i64> {
    let ts = repo::now();
    conn.execute(
        "INSERT INTO scan_job (state, stage, started_at, updated_at) VALUES ('enumerating','enumerating',?1,?1)",
        params![ts],
    )?;
    Ok(conn.last_insert_rowid())
}

fn update_job(conn: &Connection, job_id: i64, stage: &str, seen: u64, hashed: u64, bytes: u64) {
    let _ = conn.execute(
        "UPDATE scan_job SET stage=?2, state=?2, files_seen=?3, files_hashed=?4, bytes_hashed=?5, updated_at=?6 WHERE id=?1",
        params![job_id, stage, seen as i64, hashed as i64, bytes as i64, repo::now()],
    );
}

fn finish(conn: &Connection, job_id: i64, state: &str, err: Option<&str>) -> Result<i64, String> {
    conn.execute(
        "UPDATE scan_job SET state=?2, finished_at=?3, error_message=?4 WHERE id=?1",
        params![job_id, state, repo::now(), err],
    )
    .map_err(|e| e.to_string())?;
    Ok(job_id)
}

fn progress(job_id: i64, stage: &str, seen: u64, hashed: u64, bytes: u64, groups: u64) -> ScanProgress {
    ScanProgress {
        job_id,
        state: stage.into(),
        stage: stage.into(),
        files_seen: seen,
        files_hashed: hashed,
        bytes_hashed: bytes,
        candidate_groups: groups,
        eta_seconds: None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db;
    use std::fs;

    #[test]
    fn end_to_end_finds_exact_duplicate() {
        // Arrange a folder with two identical files + one unique, scan it, expect a cluster.
        let base = std::env::temp_dir().join(format!("sift_scan_{}", std::process::id()));
        let _ = fs::remove_dir_all(&base);
        fs::create_dir_all(&base).unwrap();
        fs::write(base.join("dup1.bin"), b"the same bytes here").unwrap();
        fs::write(base.join("dup2.bin"), b"the same bytes here").unwrap();
        fs::write(base.join("unique.bin"), b"different content entirely").unwrap();

        let conn = Connection::open_in_memory().unwrap();
        db::migrate(&conn).unwrap();

        let mount = format!("{}", base.display());
        let sources = vec![Source { volume_mount: mount, rel_root: None, source_id: None }];
        let cancel = Arc::new(AtomicBool::new(false));
        // Capture progress to prove the batched full-hash emits while hashing (not just
        // once at the end) — the fix for "stuck at hashing" with large files.
        let mut hashing_progress: Vec<u64> = Vec::new();
        ScanService::run(
            &conn,
            &sources,
            &ScanFilters::default(),
            ScanMode::Thorough,
            cancel,
            |p| {
                if p.stage == "hashing" {
                    hashing_progress.push(p.files_hashed);
                }
            },
            |_l| {},
        )
        .unwrap();
        assert!(
            hashing_progress.iter().any(|&h| h >= 1),
            "batched hashing must emit progress with files_hashed>=1, got {hashing_progress:?}"
        );

        // Exactly one cluster, with 2 members and reclaimable == size of one copy.
        let clusters: i64 = conn
            .query_row("SELECT COUNT(*) AS n FROM cluster", [], |r| r.get("n"))
            .unwrap();
        assert_eq!(clusters, 1, "should find one duplicate cluster");
        let members: i64 = conn
            .query_row("SELECT member_count AS m FROM cluster", [], |r| r.get("m"))
            .unwrap();
        assert_eq!(members, 2);

        let _ = fs::remove_dir_all(&base);
    }

    #[test]
    fn cancellation_stops_scan_and_marks_job_cancelled() {
        // A pre-cancelled scan must return promptly with state 'cancelled', find no
        // duplicates, and (critically) NOT mark anything 'missing'.
        let base = std::env::temp_dir().join(format!("sift_cancel_scan_{}", std::process::id()));
        let _ = fs::remove_dir_all(&base);
        fs::create_dir_all(&base).unwrap();
        fs::write(base.join("a.bin"), b"dup").unwrap();
        fs::write(base.join("b.bin"), b"dup").unwrap();

        let conn = Connection::open_in_memory().unwrap();
        db::migrate(&conn).unwrap();

        let sources = vec![Source {
            volume_mount: format!("{}", base.display()),
            rel_root: None,
            source_id: None,
        }];
        let cancel = Arc::new(AtomicBool::new(true)); // already cancelled
        let job_id =
            ScanService::run(&conn, &sources, &ScanFilters::default(), ScanMode::Thorough, cancel, |_| {}, |_| {})
                .unwrap();

        let state: String = conn
            .query_row("SELECT state AS s FROM scan_job WHERE id=?1", [job_id], |r| r.get("s"))
            .unwrap();
        assert_eq!(state, "cancelled", "job must end in 'cancelled'");

        let clusters: i64 = conn
            .query_row("SELECT COUNT(*) AS n FROM cluster", [], |r| r.get("n"))
            .unwrap();
        assert_eq!(clusters, 0, "cancelled scan finds no duplicates");

        let missing: i64 = conn
            .query_row("SELECT COUNT(*) AS n FROM file WHERE status='missing'", [], |r| r.get("n"))
            .unwrap();
        assert_eq!(missing, 0, "cancelled scan must not mark files missing");

        let _ = fs::remove_dir_all(&base);
    }

    #[test]
    fn scan_runs_again_after_a_cancelled_one() {
        // Proves the engine leaves no blocking state behind: a cancelled scan is followed
        // by a fresh scan that completes normally (the "can't start a new one" complaint).
        let base = std::env::temp_dir().join(format!("sift_rerun_{}", std::process::id()));
        let _ = fs::remove_dir_all(&base);
        fs::create_dir_all(&base).unwrap();
        fs::write(base.join("a.bin"), b"same bytes").unwrap();
        fs::write(base.join("b.bin"), b"same bytes").unwrap();

        let conn = Connection::open_in_memory().unwrap();
        db::migrate(&conn).unwrap();
        let sources = vec![Source {
            volume_mount: format!("{}", base.display()),
            rel_root: None,
            source_id: None,
        }];

        // 1) Cancelled scan.
        ScanService::run(&conn, &sources, &ScanFilters::default(), ScanMode::Thorough, Arc::new(AtomicBool::new(true)), |_| {}, |_| {}).unwrap();
        // 2) A fresh, non-cancelled scan must complete and find the duplicate.
        ScanService::run(&conn, &sources, &ScanFilters::default(), ScanMode::Thorough, Arc::new(AtomicBool::new(false)), |_| {}, |_| {}).unwrap();

        let clusters: i64 = conn.query_row("SELECT COUNT(*) AS n FROM cluster", [], |r| r.get("n")).unwrap();
        assert_eq!(clusters, 1, "second scan after a cancel works normally");

        let _ = fs::remove_dir_all(&base);
    }

    #[test]
    fn scan_records_history_log_and_folder_traversal() {
        // A folder scan (walker path) must persist: timeline events, folder traversal with
        // a subfolder, finalized session metrics, and file->scan-session links.
        let base = std::env::temp_dir().join(format!("sift_hist_{}", std::process::id()));
        let _ = fs::remove_dir_all(&base);
        fs::create_dir_all(base.join("sub")).unwrap();
        fs::write(base.join("a.bin"), b"dup-bytes").unwrap();
        fs::write(base.join("sub/b.bin"), b"dup-bytes").unwrap(); // duplicate of a.bin
        fs::write(base.join("c.bin"), b"unique").unwrap();

        let conn = Connection::open_in_memory().unwrap();
        db::migrate(&conn).unwrap();
        let sources = vec![Source { volume_mount: format!("{}", base.display()), rel_root: None, source_id: None }];

        let mut kinds: Vec<String> = Vec::new();
        let job_id = ScanService::run(
            &conn, &sources, &ScanFilters::default(), ScanMode::Thorough,
            Arc::new(AtomicBool::new(false)), |_| {}, |e| kinds.push(e.kind),
        ).unwrap();

        // Timeline events persisted (started + source + completed at minimum).
        let events: i64 = conn.query_row("SELECT COUNT(*) AS n FROM scan_log_event WHERE scan_job_id=?1", [job_id], |r| r.get("n")).unwrap();
        assert!(events >= 3, "expected >=3 log events, got {events}");
        assert!(kinds.iter().any(|k| k == "started"), "started streamed");
        assert!(kinds.iter().any(|k| k == "completed"), "completed streamed");

        // Folder traversal recorded with a subfolder (depth>0).
        let completed_dirs: i64 = conn.query_row("SELECT COUNT(*) AS n FROM folder_traversal WHERE scan_job_id=?1 AND state='completed'", [job_id], |r| r.get("n")).unwrap();
        assert!(completed_dirs >= 2, "root + sub completed, got {completed_dirs}");
        let subdirs: i64 = conn.query_row("SELECT COUNT(*) AS n FROM folder_traversal WHERE scan_job_id=?1 AND state='completed' AND depth>0", [job_id], |r| r.get("n")).unwrap();
        assert!(subdirs >= 1, "at least one subfolder traversed");

        // Session finalized with metrics.
        let (folders, total, dups): (i64, i64, i64) = conn.query_row(
            "SELECT folders_traversed AS f, total_bytes AS t, duplicates_found AS d FROM scan_job WHERE id=?1",
            [job_id], |r| Ok((r.get("f")?, r.get("t")?, r.get("d")?))).unwrap();
        assert!(folders >= 2, "folders_traversed recorded");
        assert!(total > 0, "total_bytes recorded");
        assert_eq!(dups, 1, "the one duplicate cluster counted");

        // Files linked to the scan session that indexed them.
        let linked: i64 = conn.query_row("SELECT COUNT(*) AS n FROM file WHERE last_scan_job_id=?1", [job_id], |r| r.get("n")).unwrap();
        assert_eq!(linked, 3, "all three files linked to the scan session");

        let _ = fs::remove_dir_all(&base);
    }
}
