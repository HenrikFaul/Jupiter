//! Tauri command surface — the typed IPC contract (FORGE Law 7). Every command returns
//! `Result<_, String>` so errors reach the UI (CODING_RULES §2).
//!
//! THREADING (the stability rule): Tauri runs synchronous commands on the MAIN thread, so
//! any command doing real DB/file work there freezes the UI ("Not responding"). Therefore
//! every command that touches the DB or the filesystem is `async` and runs its work on a
//! dedicated blocking thread via `blocking(app, …)`. Only trivial atomics/pure checks stay
//! synchronous. Reads use a read-only connection; writes use the single writer Mutex.

use rusqlite::{params, Connection};
use sift_core::db::{self, repo};
use sift_core::engine::deletion_service::{self, Mode};
use sift_core::engine::safety::{self, Copy as SafetyCopy, Decision};
use sift_core::engine::scan_service::{ScanService, Source};
use sift_core::model::*;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use tauri::{Emitter, Manager};

pub struct AppState {
    pub db_path: PathBuf,
    pub data_dir: PathBuf,
    pub writer: Mutex<Connection>,
    pub scan_cancel: Arc<AtomicBool>,
    /// True while a scan thread is running. Prevents a second scan from silently
    /// blocking on the writer lock (which is what made a stuck scan feel unrecoverable).
    pub scan_active: Arc<AtomicBool>,
    /// Same guard pair for the media-analysis background pass (cancellable, non-blocking).
    pub media_cancel: Arc<AtomicBool>,
    pub media_active: Arc<AtomicBool>,
}

impl AppState {
    pub fn open(db_path: PathBuf, data_dir: PathBuf) -> Result<Self, String> {
        let writer = db::open_writer(&db_path).map_err(|e| e.to_string())?;
        Ok(Self {
            db_path,
            data_dir,
            writer: Mutex::new(writer),
            scan_cancel: Arc::new(AtomicBool::new(false)),
            scan_active: Arc::new(AtomicBool::new(false)),
            media_cancel: Arc::new(AtomicBool::new(false)),
            media_active: Arc::new(AtomicBool::new(false)),
        })
    }
    fn reader(&self) -> Result<Connection, String> {
        db::open_reader(&self.db_path).map_err(|e| e.to_string())
    }
    fn write<T>(&self, f: impl FnOnce(&Connection) -> Result<T, String>) -> Result<T, String> {
        let conn = self.writer.lock().map_err(|_| "writer lock poisoned".to_string())?;
        f(&conn)
    }
    /// Entitlement gate (initiative #1). With enforcement OFF (the shipped default) this always
    /// returns Ok — so the gate is wired through every Pro command but blocks NOTHING until the
    /// `license::ENFORCEMENT_ENABLED` flag is flipped at commercial launch.
    fn require(&self, feature: &str) -> Result<(), String> {
        if sift_core::engine::license::is_entitled(&self.data_dir, feature) {
            Ok(())
        } else {
            Err(format!("This is a Pro feature ({feature}). Activate a license in Settings → License."))
        }
    }
}

/// Run blocking work off the main thread so the UI never freezes. The closure gets the
/// managed `AppState` (resolved inside the blocking thread via the AppHandle, so no borrow
/// crosses an await point).
async fn blocking<T, F>(app: tauri::AppHandle, f: F) -> Result<T, String>
where
    T: Send + 'static,
    F: FnOnce(&AppState) -> Result<T, String> + Send + 'static,
{
    match tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<AppState>();
        f(state.inner())
    })
    .await
    {
        Ok(inner) => inner,
        Err(e) => Err(format!("background task failed: {e}")),
    }
}

// ---------------------------------------------------------------------------
// Reads
// ---------------------------------------------------------------------------

#[tauri::command]
pub async fn get_index_stats(app: tauri::AppHandle) -> Result<IndexStats, String> {
    blocking(app, |s| s.reader().and_then(|c| repo::index_stats(&c).map_err(|e| e.to_string()))).await
}

#[tauri::command]
pub async fn list_volumes(app: tauri::AppHandle) -> Result<Vec<VolumeView>, String> {
    blocking(app, |s| s.reader().and_then(|c| repo::list_volumes(&c).map_err(|e| e.to_string()))).await
}

#[tauri::command]
pub async fn query_clusters(sort: ClusterSort, limit: i64, offset: i64, app: tauri::AppHandle) -> Result<Vec<ClusterView>, String> {
    blocking(app, move |s| s.reader().and_then(|c| repo::query_clusters(&c, sort, limit, offset).map_err(|e| e.to_string()))).await
}

#[tauri::command]
pub async fn get_presence(content_id: i64, app: tauri::AppHandle) -> Result<PresenceView, String> {
    blocking(app, move |s| s.reader().and_then(|c| repo::presence_summary(&c, content_id).map_err(|e| e.to_string()))).await
}

#[tauri::command]
pub async fn search_index(query: IndexQuery, app: tauri::AppHandle) -> Result<Vec<FileView>, String> {
    blocking(app, move |s| s.reader().and_then(|c| repo::search_index(&c, &query).map_err(|e| e.to_string()))).await
}

#[tauri::command]
pub async fn list_audit(limit: i64, app: tauri::AppHandle) -> Result<Vec<AuditView>, String> {
    blocking(app, move |s| s.reader().and_then(|c| repo::list_audit(&c, limit).map_err(|e| e.to_string()))).await
}

#[tauri::command]
pub async fn query_folder_clusters(limit: i64, app: tauri::AppHandle) -> Result<Vec<FolderClusterView>, String> {
    blocking(app, move |s| s.reader().and_then(|c| repo::query_folder_clusters(&c, limit).map_err(|e| e.to_string()))).await
}

#[tauri::command]
pub async fn db_integrity_check(app: tauri::AppHandle) -> Result<bool, String> {
    blocking(app, |s| s.reader().and_then(|c| db::integrity_check(&c).map_err(|e| e.to_string()))).await
}

#[tauri::command]
pub async fn storage_info(app: tauri::AppHandle) -> Result<StorageInfo, String> {
    blocking(app, |s| {
        let db_bytes = std::fs::metadata(&s.db_path).map(|m| m.len() as i64).unwrap_or(0);
        let portable = std::env::current_exe()
            .ok()
            .and_then(|e| e.parent().map(|p| s.data_dir.starts_with(p)))
            .unwrap_or(false);
        Ok(StorageInfo {
            data_dir: s.data_dir.display().to_string(),
            db_path: s.db_path.display().to_string(),
            db_bytes,
            portable,
        })
    })
    .await
}

// ---------------------------------------------------------------------------
// Scan history / log
// ---------------------------------------------------------------------------

#[tauri::command]
pub async fn list_scan_sessions(limit: i64, app: tauri::AppHandle) -> Result<Vec<ScanSessionView>, String> {
    blocking(app, move |s| s.reader().and_then(|c| repo::query_scan_sessions(&c, limit).map_err(|e| e.to_string()))).await
}

#[tauri::command]
pub async fn get_scan_session(job_id: i64, app: tauri::AppHandle) -> Result<ScanSessionDetail, String> {
    blocking(app, move |s| {
        let conn = s.reader()?;
        let session = repo::scan_session(&conn, job_id)
            .map_err(|e| e.to_string())?
            .ok_or_else(|| format!("scan session #{job_id} not found"))?;
        let events = repo::scan_session_events(&conn, job_id, 2000).map_err(|e| e.to_string())?;
        let folders = repo::scan_session_folders(&conn, job_id, 5000).map_err(|e| e.to_string())?;
        Ok(ScanSessionDetail { session, events, folders })
    })
    .await
}

#[tauri::command]
pub async fn delete_scan_session(job_id: i64, app: tauri::AppHandle) -> Result<(), String> {
    blocking(app, move |s| s.write(|conn| repo::delete_scan_session(conn, job_id).map_err(|e| e.to_string()))).await
}

#[tauri::command]
pub async fn clear_scan_history(app: tauri::AppHandle) -> Result<usize, String> {
    blocking(app, |s| s.write(|conn| repo::clear_scan_history(conn).map_err(|e| e.to_string()))).await
}

// ---------------------------------------------------------------------------
// Mark (rename) — non-destructive alternative to deletion
// ---------------------------------------------------------------------------

#[tauri::command]
pub async fn mark_files(file_ids: Vec<i64>, affix: String, position: String, app: tauri::AppHandle) -> Result<DeletionOutcome, String> {
    blocking(app, move |s| {
        use sift_core::engine::rename_service::{mark_files as mark, Position};
        let pos = if position == "prefix" { Position::Prefix } else { Position::Suffix };
        s.write(|conn| mark(conn, &file_ids, &affix, pos))
    })
    .await
}

// ---------------------------------------------------------------------------
// Reveal in Explorer (fast — spawns a process, stays synchronous)
// ---------------------------------------------------------------------------

#[tauri::command]
pub fn reveal_in_explorer(abs_path: String) -> Result<(), String> {
    #[cfg(windows)]
    {
        std::process::Command::new("explorer.exe")
            .arg(format!("/select,{abs_path}"))
            .spawn()
            .map_err(|e| format!("could not open Explorer: {e}"))?;
        Ok(())
    }
    #[cfg(target_os = "macos")]
    {
        std::process::Command::new("open")
            .arg("-R")
            .arg(&abs_path)
            .spawn()
            .map_err(|e| format!("could not reveal in Finder: {e}"))?;
        Ok(())
    }
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        // Best-effort: open the containing folder in the default file manager.
        let folder = std::path::Path::new(&abs_path)
            .parent()
            .map(|p| p.to_path_buf())
            .unwrap_or_else(|| std::path::PathBuf::from("."));
        std::process::Command::new("xdg-open")
            .arg(folder)
            .spawn()
            .map_err(|e| format!("could not open file manager: {e}"))?;
        Ok(())
    }
}

// ---------------------------------------------------------------------------
// Duplicate folders / selection / reports
// ---------------------------------------------------------------------------

#[tauri::command]
pub async fn detect_duplicate_folders(app: tauri::AppHandle) -> Result<usize, String> {
    blocking(app, |s| s.write(|conn| sift_core::engine::dupfolder::detect(conn))).await
}

#[tauri::command]
pub async fn apply_selection_rules(
    rules: Vec<sift_core::engine::selection::SelectionRule>,
    app: tauri::AppHandle,
) -> Result<Vec<sift_core::engine::selection::SelectionDecision>, String> {
    blocking(app, move |s| {
        s.require("smart_selection")?; // Pro gate (inert until enforcement is enabled)
        let conn = s.reader()?;
        let candidates = repo::selection_inputs(&conn).map_err(|e| e.to_string())?;
        Ok(sift_core::engine::selection::apply(&candidates, &rules))
    })
    .await
}

#[tauri::command]
pub async fn export_report(kind: String, app: tauri::AppHandle) -> Result<String, String> {
    blocking(app, move |s| {
        let out_dir = s.data_dir.join("exports");
        let conn = s.reader()?;
        sift_core::engine::reports::export(&conn, &kind, &out_dir).map(|p| p.display().to_string())
    })
    .await
}

// ---------------------------------------------------------------------------
// Similar-image detection (perceptual hashing) + thumbnails
// ---------------------------------------------------------------------------

#[tauri::command]
pub async fn detect_similar_images(threshold: u32, app: tauri::AppHandle) -> Result<SimilarImageResult, String> {
    blocking(app, move |s| {
        s.require("similar_images")?; // Pro gate (inert until enforcement is enabled)
        s.write(|conn| sift_core::engine::imagehash::detect(conn, threshold))
    })
    .await
}

/// Detect perceptually-similar VIDEOS (initiative #4): keyframe-dHash signatures via the
/// bundled ffmpeg, cached + clustered (BK-tree + per-vector confirm). Mirrors similar-images.
#[tauri::command]
pub async fn detect_similar_videos(threshold: u32, app: tauri::AppHandle) -> Result<SimilarVideoResult, String> {
    blocking(app, move |s| {
        s.require("similar_videos")?; // Pro gate (inert until enforcement is enabled)
        let ffmpeg = sift_core::engine::mediaprobe::resolve_ffmpeg().unwrap_or_default();
        s.write(|conn| sift_core::engine::videohash::detect(conn, threshold, &ffmpeg, &|| false))
    })
    .await
}

/// A base64 PNG thumbnail of a video's first frame (for the Similar Videos grid).
#[tauri::command]
pub async fn get_video_thumbnail(file_id: i64, max: u32, app: tauri::AppHandle) -> Result<String, String> {
    blocking(app, move |s| {
        use base64::Engine as _;
        let ffmpeg = sift_core::engine::mediaprobe::resolve_ffmpeg().ok_or("ffmpeg not available — install FFmpeg for video thumbnails")?;
        let conn = s.reader()?;
        let path = repo::file_abs_path(&conn, file_id)
            .map_err(|e| e.to_string())?
            .ok_or("file is offline or not in the index")?;
        let png = sift_core::engine::videohash::video_thumbnail_png(&ffmpeg, std::path::Path::new(&path), max.clamp(64, 512))
            .ok_or("could not render a video thumbnail")?;
        Ok(base64::engine::general_purpose::STANDARD.encode(png))
    })
    .await
}

#[tauri::command]
pub async fn get_thumbnail(file_id: i64, max: u32, app: tauri::AppHandle) -> Result<String, String> {
    blocking(app, move |s| {
        use base64::Engine as _;
        let conn = s.reader()?;
        let path = repo::file_abs_path(&conn, file_id)
            .map_err(|e| e.to_string())?
            .ok_or("file is offline or not in the index")?;
        let png = sift_core::engine::imagehash::thumbnail_png(std::path::Path::new(&path), max.clamp(32, 512))
            .ok_or("could not decode image")?;
        Ok(base64::engine::general_purpose::STANDARD.encode(png))
    })
    .await
}

// ---------------------------------------------------------------------------
// Folder tree (lazy, collapsible structure view)
// ---------------------------------------------------------------------------

/// Immediate child folders of `parent_rel` on a volume, each with recursive size/counts.
/// The UI calls this once per expanded node (lazy tree), so each call aggregates only the
/// requested subtree.
#[tauri::command]
pub async fn folder_children(
    volume_id: i64,
    parent_rel: String,
    app: tauri::AppHandle,
) -> Result<Vec<FolderNode>, String> {
    blocking(app, move |s| {
        s.reader()
            .and_then(|c| repo::folder_children(&c, volume_id, &parent_rel).map_err(|e| e.to_string()))
    })
    .await
}

// ---------------------------------------------------------------------------
// Media integrity & quality analysis (ffprobe-backed; optional dependency)
// ---------------------------------------------------------------------------

/// Start media analysis on a BACKGROUND thread. Returns immediately; progress streams as
/// `media://progress` and a terminal `media://done`. Crucially, results are persisted in
/// small BATCHES that each acquire-then-release the writer lock, so the pass never
/// monopolizes the single writer (this is what froze renames for 11 minutes) and stays
/// cancellable. `file_ids = Some([...])` scopes the work to those rows (the UI passes the
/// currently-filtered result set); `None` analyzes every not-yet-probed video.
#[tauri::command]
pub fn start_media_analysis(
    file_ids: Option<Vec<i64>>,
    deep: bool,
    app: tauri::AppHandle,
) -> Result<(), String> {
    use sift_core::engine::mediaprobe;
    let state = app.state::<AppState>();
    // Pro gate (inert until enforcement is enabled) — checked before claiming the active flag.
    if !sift_core::engine::license::is_entitled(&state.data_dir, "media_analysis") {
        return Err("This is a Pro feature (media analysis). Activate a license in Settings → License.".into());
    }
    if state.media_active.swap(true, Ordering::SeqCst) {
        return Err("Media analysis is already running. Stop it first, then start a new one.".into());
    }
    state.media_cancel.store(false, Ordering::SeqCst);

    // RAII guard: ALWAYS clears media_active, and if the pass ends WITHOUT a terminal event
    // (e.g. a panic in the thread body) it emits a fallback media://error so the UI lifecycle
    // can never wedge with the progress bar stuck. Normal/early-return paths set `finished`.
    struct AnalysisGuard {
        active: Arc<AtomicBool>,
        app: tauri::AppHandle,
        finished: bool,
    }
    impl Drop for AnalysisGuard {
        fn drop(&mut self) {
            self.active.store(false, Ordering::SeqCst);
            if !self.finished {
                let _ = self.app.emit("media://error", "Media analysis ended unexpectedly".to_string());
            }
        }
    }

    let app2 = app.clone();
    let spawn = std::thread::Builder::new()
        .name("sift-media-analysis".into())
        .spawn(move || {
            let state = app2.state::<AppState>();
            let mut guard = AnalysisGuard { active: state.media_active.clone(), app: app2.clone(), finished: false };
            let cancel = state.media_cancel.clone();

            // ffprobe is needed only for MEDIA quality. If it's missing we still proceed:
            // non-media files get a generic integrity check, and media files fall back to one
            // too (we just can't grade their quality) — never fail outright for a missing tool.
            let ffprobe_opt = mediaprobe::resolve_ffprobe();
            let ffprobe = ffprobe_opt.clone().unwrap_or_default();

            // Build the work list (reader connection — does not hold the writer lock).
            let work = match state.reader().and_then(|c| {
                repo::media_needing_probe_scoped(&c, file_ids.as_deref()).map_err(|e| e.to_string())
            }) {
                Ok(w) => w,
                Err(e) => {
                    guard.finished = true;
                    let _ = app2.emit("media://error", e);
                    return;
                }
            };
            let total = work.len() as i64;
            let _ = app2.emit("media://progress", MediaProgress { done: 0, total, warnings: 0, corrupted: 0 });

            // Probe in small parallel batches; persist each batch under a BRIEF writer lock,
            // then RELEASE it (so a rename or any other write can interleave). Cancel between
            // batches. `done` counts only rows actually PERSISTED — never report success for
            // files we failed to save (fail-loud / honest-partial-success).
            const BATCH: usize = 8;
            let mut done = 0i64;
            let mut failed_writes = 0i64;
            let mut warns = 0i64;
            let mut bad = 0i64;
            for chunk in work.chunks(BATCH) {
                if cancel.load(Ordering::SeqCst) {
                    break;
                }
                let probed = mediaprobe::probe_many(&ffprobe, chunk, deep);
                // A poisoned writer lock means a prior writer panicked mid-write — surface it
                // loudly (mirroring the scan thread) instead of silently dropping every result.
                let conn = match state.writer.lock() {
                    Ok(c) => c,
                    Err(_) => {
                        guard.finished = true;
                        let _ = app2.emit("media://error", "writer lock poisoned — media results could not be saved".to_string());
                        return;
                    }
                };
                for (id, meta) in &probed {
                    match repo::store_media_meta(&conn, *id, meta) {
                        Ok(()) => {
                            done += 1;
                            if meta.quality_warning {
                                warns += 1;
                            }
                            if matches!(meta.integrity.as_str(), "corrupted" | "unreadable" | "partial") {
                                bad += 1;
                            }
                        }
                        Err(_) => failed_writes += 1,
                    }
                }
                drop(conn); // release the writer lock BEFORE emitting (never hold it across an emit)
                let _ = app2.emit("media://progress", MediaProgress { done, total, warnings: warns, corrupted: bad });
            }

            let cancelled = cancel.load(Ordering::SeqCst);
            let totals = state.reader().and_then(|c| repo::media_counts(&c).map_err(|e| e.to_string()));
            let (total_media, tot_warns, tot_bad) = totals.unwrap_or((0, warns, bad));
            let message = (failed_writes > 0)
                .then(|| format!("{failed_writes} file(s) were probed but could not be saved to the index."));
            // If ffprobe was missing, non-media still got an integrity check — but tell the
            // user media quality couldn't be graded.
            let message = message.or_else(|| {
                ffprobe_opt.is_none().then(|| {
                    "ffprobe not found — files got a basic integrity check, but install FFmpeg \
                     (`winget install Gyan.FFmpeg`) for full media quality (duration/bitrate/resolution)."
                        .to_string()
                })
            });
            guard.finished = true;
            let _ = app2.emit("media://done", MediaAnalyzeResult {
                available: ffprobe_opt.is_some(),
                ffprobe_path: ffprobe_opt.clone(),
                newly_analyzed: done,
                total_media,
                warnings: tot_warns,
                corrupted: tot_bad,
                cancelled,
                message,
            });
        });

    // If the OS could not create the thread, don't leave media_active stuck on.
    if let Err(e) = spawn {
        state.media_active.store(false, Ordering::SeqCst);
        return Err(format!("could not start the media-analysis thread: {e}"));
    }
    Ok(())
}

/// Stop a running media-analysis pass (it finishes the current small batch and stops).
#[tauri::command]
pub fn cancel_media_analysis(app: tauri::AppHandle) -> Result<(), String> {
    app.state::<AppState>().media_cancel.store(true, Ordering::SeqCst);
    Ok(())
}

/// Whether a media-analysis pass is currently running (UI re-sync).
#[tauri::command]
pub fn media_is_active(app: tauri::AppHandle) -> Result<bool, String> {
    Ok(app.state::<AppState>().media_active.load(Ordering::SeqCst))
}

/// Whether the ffprobe backend is present (for the Settings indicator / pre-flight).
#[tauri::command]
pub async fn ffprobe_status(app: tauri::AppHandle) -> Result<FfprobeStatus, String> {
    blocking(app, |_s| Ok(sift_core::engine::mediaprobe::status())).await
}

/// Cumulative "reclaim" evidence for the Reports screen (initiative #8).
#[tauri::command]
pub async fn reclaim_summary(app: tauri::AppHandle) -> Result<ReclaimSummary, String> {
    blocking(app, |s| s.reader().and_then(|c| repo::reclaim_summary(&c).map_err(|e| e.to_string()))).await
}

/// User-added protected folders (initiative #6) — never-delete locations beyond the built-in
/// system roots. Read.
#[tauri::command]
pub async fn get_protected_paths(app: tauri::AppHandle) -> Result<Vec<String>, String> {
    blocking(app, |s| Ok(safety::load_protected_paths(&s.data_dir))).await
}

/// User-added protected folders. Write.
#[tauri::command]
pub async fn set_protected_paths(paths: Vec<String>, app: tauri::AppHandle) -> Result<(), String> {
    blocking(app, move |s| safety::save_protected_paths(&s.data_dir, &paths)).await
}

// ---------------------------------------------------------------------------
// License / Free-Pro entitlement mechanism (initiative #1) — built, default UNLOCKED
// ---------------------------------------------------------------------------

/// Current entitlement (tier, license details, the Pro feature list, and whether enforcement
/// is on). With enforcement off, every feature is unlocked regardless of tier.
#[tauri::command]
pub async fn license_status(app: tauri::AppHandle) -> Result<LicenseStatus, String> {
    blocking(app, |s| Ok(sift_core::engine::license::status(&s.data_dir))).await
}

/// Verify + activate an offline license token (Ed25519). Audited; returns the new status.
#[tauri::command]
pub async fn activate_license(token: String, app: tauri::AppHandle) -> Result<LicenseStatus, String> {
    blocking(app, move |s| {
        s.write(|conn| sift_core::engine::license::save_entitlement(conn, &s.data_dir, &token).map(|_| ()))?;
        Ok(sift_core::engine::license::status(&s.data_dir))
    })
    .await
}

/// Remove the active license (revert to Free). Audited; returns the new status.
#[tauri::command]
pub async fn deactivate_license(app: tauri::AppHandle) -> Result<LicenseStatus, String> {
    blocking(app, |s| {
        s.write(|conn| sift_core::engine::license::clear_entitlement(conn, &s.data_dir))?;
        Ok(sift_core::engine::license::status(&s.data_dir))
    })
    .await
}

// ---------------------------------------------------------------------------
// Deletion safety + plans
// ---------------------------------------------------------------------------

#[derive(serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PlanItemInput {
    pub file_id: i64,
    pub content_id: i64,
    pub volume_online: bool,
    pub status_present: bool,
    pub path_raw: String,
    pub keep: bool,
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ValidationResult {
    pub ok: bool,
    pub violations: Vec<String>,
}

/// Pure engine pre-check (no DB) — stays synchronous; it's instant.
#[tauri::command]
pub fn validate_deletion_plan(items: Vec<PlanItemInput>, acknowledged_online: Vec<i64>) -> ValidationResult {
    let pairs: Vec<(SafetyCopy, Decision)> = items
        .into_iter()
        .map(|i| {
            (
                SafetyCopy {
                    file_id: i.file_id,
                    content_id: i.content_id,
                    volume_online: i.volume_online,
                    status_present: i.status_present,
                    path_raw: i.path_raw,
                },
                if i.keep { Decision::Keep } else { Decision::Remove },
            )
        })
        .collect();
    let report = safety::validate_plan(&pairs, &acknowledged_online);
    ValidationResult {
        ok: report.ok(),
        violations: report.violations.iter().map(violation_text).collect(),
    }
}

fn violation_text(v: &safety::SafetyViolation) -> String {
    match v {
        safety::SafetyViolation::WouldRemoveLastOnlineCopy { content_id } => format!(
            "Plan would remove the last ONLINE copy of content #{content_id}. Remaining copies are on offline drives only — acknowledge to proceed."
        ),
        safety::SafetyViolation::WouldRemoveLastKnownCopy { content_id } => format!(
            "Plan would destroy the LAST known copy of content #{content_id}. This is blocked."
        ),
    }
}

#[derive(serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PlanDecision {
    pub file_id: i64,
    pub keep: bool,
    pub reason: Option<String>,
}

#[tauri::command]
pub async fn create_deletion_plan(
    name: Option<String>,
    mode: String,
    decisions: Vec<PlanDecision>,
    app: tauri::AppHandle,
) -> Result<i64, String> {
    blocking(app, move |s| {
        s.write(|conn| {
            conn.execute(
                "INSERT INTO deletion_plan (name, created_at, mode, state) VALUES (?1,?2,?3,'draft')",
                params![name, repo::now(), mode],
            )
            .map_err(|e| e.to_string())?;
            let plan_id = conn.last_insert_rowid();
            for d in &decisions {
                conn.execute(
                    "INSERT INTO deletion_plan_item (plan_id, file_id, decision, reason, result) \
                     VALUES (?1,?2,?3,?4,'pending')",
                    params![plan_id, d.file_id, if d.keep { "keep" } else { "remove" }, d.reason],
                )
                .map_err(|e| e.to_string())?;
            }
            Ok(plan_id)
        })
    })
    .await
}

/// Execute a plan. Runs OFF the main thread — recycling large files no longer freezes the UI.
#[tauri::command]
pub async fn execute_deletion_plan(
    plan_id: i64,
    mode: String,
    acknowledged_online: Vec<i64>,
    app: tauri::AppHandle,
) -> Result<DeletionOutcome, String> {
    blocking(app, move |s| {
        let quarantine = s.data_dir.join("quarantine");
        let mode = parse_mode(&mode, quarantine)?;
        let protected = safety::load_protected_paths(&s.data_dir);
        s.write(|conn| deletion_service::execute_plan(conn, plan_id, mode, &acknowledged_online, &protected))
    })
    .await
}

/// Remove distinct files by id (similar-image cleanup). No content keep-one gate.
#[tauri::command]
pub async fn delete_files(file_ids: Vec<i64>, mode: String, app: tauri::AppHandle) -> Result<DeletionOutcome, String> {
    blocking(app, move |s| {
        let quarantine = s.data_dir.join("quarantine");
        let mode = parse_mode(&mode, quarantine)?;
        let protected = safety::load_protected_paths(&s.data_dir);
        s.write(|conn| deletion_service::delete_files(conn, &file_ids, mode, &protected))
    })
    .await
}

fn parse_mode(mode: &str, quarantine: PathBuf) -> Result<Mode, String> {
    match mode {
        "recycle" => Ok(Mode::Recycle),
        "quarantine" => Ok(Mode::Quarantine(quarantine)),
        "permanent" => Ok(Mode::Permanent),
        other => Err(format!("unknown mode '{other}'")),
    }
}

/// Preview an advanced (parent-folder) deletion over the selected files: which folders would
/// be deleted wholesale, the collateral inside each, and a per-folder safety verdict. Read-only.
#[tauri::command]
pub async fn preview_folder_deletion(file_ids: Vec<i64>, app: tauri::AppHandle) -> Result<FolderDeletionPreview, String> {
    blocking(app, move |s| {
        let protected = safety::load_protected_paths(&s.data_dir);
        let conn = s.reader()?;
        deletion_service::preview_folders(&conn, &file_ids, &protected)
    })
    .await
}

/// Execute an advanced (parent-folder) deletion. The engine re-validates every folder against
/// the safety gate (refuses drive/source roots, shared user folders, protected locations, and
/// over-large folders) regardless of what the UI sent.
#[tauri::command]
pub async fn delete_folders(folders: Vec<String>, mode: String, app: tauri::AppHandle) -> Result<DeletionOutcome, String> {
    blocking(app, move |s| {
        let quarantine = s.data_dir.join("quarantine");
        let mode = parse_mode(&mode, quarantine)?;
        let protected = safety::load_protected_paths(&s.data_dir);
        s.write(|conn| deletion_service::delete_folders(conn, &folders, mode, &protected))
    })
    .await
}

// ----- bookmarks: named, recallable Index Explorer searches (Everything-style) -----
#[tauri::command]
pub async fn get_bookmarks(app: tauri::AppHandle) -> Result<Vec<Bookmark>, String> {
    blocking(app, |s| Ok(sift_core::engine::bookmarks::load(&s.data_dir))).await
}
#[tauri::command]
pub async fn save_bookmark(name: String, query: IndexQuery, app: tauri::AppHandle) -> Result<Vec<Bookmark>, String> {
    blocking(app, move |s| sift_core::engine::bookmarks::put(&s.data_dir, &name, query)).await
}
#[tauri::command]
pub async fn delete_bookmark(name: String, app: tauri::AppHandle) -> Result<Vec<Bookmark>, String> {
    blocking(app, move |s| sift_core::engine::bookmarks::remove(&s.data_dir, &name)).await
}

// ----- custom actions ("plugins"): run user-defined external tools on selected files -----
#[tauri::command]
pub async fn get_actions(app: tauri::AppHandle) -> Result<Vec<CustomAction>, String> {
    blocking(app, |s| Ok(sift_core::engine::actions::load(&s.data_dir))).await
}
#[tauri::command]
pub async fn save_action(action: CustomAction, app: tauri::AppHandle) -> Result<Vec<CustomAction>, String> {
    blocking(app, move |s| sift_core::engine::actions::put(&s.data_dir, action)).await
}
#[tauri::command]
pub async fn delete_action(name: String, app: tauri::AppHandle) -> Result<Vec<CustomAction>, String> {
    blocking(app, move |s| sift_core::engine::actions::remove(&s.data_dir, &name)).await
}
/// Run a named action against each present+online file. Honest partial reporting; only ever
/// invoked explicitly by the user (never automatically).
#[tauri::command]
pub async fn run_action(name: String, file_ids: Vec<i64>, app: tauri::AppHandle) -> Result<ActionRunResult, String> {
    blocking(app, move |s| {
        let conn = s.reader()?;
        let (mut ran, mut failed, mut failures) = (0u32, 0u32, Vec::new());
        for fid in &file_ids {
            let row = conn.query_row(
                "SELECT v.current_mount_point AS mp, f.path_raw AS path, f.status AS st, v.is_online AS online \
                 FROM file f JOIN volume v ON v.id=f.volume_id WHERE f.id=?1",
                params![fid],
                |r| Ok((
                    r.get::<_, Option<String>>("mp")?,
                    r.get::<_, String>("path")?,
                    r.get::<_, String>("st")?,
                    r.get::<_, i64>("online")?,
                )),
            );
            match row {
                Ok((Some(mp), path, st, online)) if st == "present" && online != 0 => {
                    let abs = std::path::Path::new(&mp)
                        .join(path.trim_start_matches(['\\', '/']))
                        .to_string_lossy()
                        .to_string();
                    match sift_core::engine::actions::run(&s.data_dir, &name, &abs) {
                        Ok(()) => ran += 1,
                        Err(e) => { failed += 1; failures.push(e); }
                    }
                }
                _ => { failed += 1; failures.push(format!("file {fid} is not present on an online drive")); }
            }
        }
        Ok(ActionRunResult { ran, failed, failures })
    })
    .await
}

/// Update-channel status for the Settings "Updates" card (no network). Surfaces the running
/// version and whether this is the portable build (in-place update disabled) or installed.
#[tauri::command]
pub fn get_update_status() -> crate::update::UpdateStatus {
    crate::update::compute_status(env!("CARGO_PKG_VERSION").to_string())
}

// ---------------------------------------------------------------------------
// Restore Center (cross-session undo of recycle / quarantine / rename)
// ---------------------------------------------------------------------------

/// The reversible actions from the audit log, newest first, with a "still restorable?" verdict.
#[tauri::command]
pub async fn list_restorable(limit: i64, app: tauri::AppHandle) -> Result<Vec<RestoreEntry>, String> {
    blocking(app, move |s| {
        s.reader().and_then(|c| sift_core::engine::restore_service::list_restorable(&c, limit))
    })
    .await
}

/// Restore the given audited actions (recycle ← Recycle Bin, quarantine ← Sift-Data, rename ←
/// rename back). Off the main thread; writes the index + audits the restore. Honest partial.
#[tauri::command]
pub async fn restore_files(audit_ids: Vec<i64>, app: tauri::AppHandle) -> Result<DeletionOutcome, String> {
    blocking(app, move |s| {
        let data_dir = s.data_dir.clone();
        s.write(|conn| sift_core::engine::restore_service::restore(conn, &data_dir, &audit_ids))
    })
    .await
}

// ---------------------------------------------------------------------------
// Scheduled & background auto-rescan (keep the persistent index fresh on a cadence)
// ---------------------------------------------------------------------------

/// Current schedule policy (read from `Sift-Data\schedule.json`), with a live echo of whether
/// the Windows Task Scheduler job actually exists right now.
#[tauri::command]
pub async fn get_schedule(app: tauri::AppHandle) -> Result<ScheduleConfig, String> {
    use sift_core::engine::scheduler;
    blocking(app, |s| {
        let mut cfg = scheduler::load_config(&s.data_dir);
        cfg.task_registered = scheduler::task_registered();
        Ok(cfg)
    })
    .await
}

/// Persist the schedule policy AND reconcile the OS scheduler: enabling (re)creates the
/// Task Scheduler job that relaunches Sift with `--rescan` on the interval; disabling removes
/// it. The persisted JSON is the source of truth even if OS registration fails (the returned
/// config's `taskRegistered` reflects the real post-state so the UI can warn).
#[tauri::command]
pub async fn set_schedule(interval_hours: i64, enabled: bool, app: tauri::AppHandle) -> Result<ScheduleConfig, String> {
    use sift_core::engine::scheduler;
    blocking(app, move |s| {
        let cfg = ScheduleConfig { enabled, interval_hours, task_registered: false };
        scheduler::save_config(&s.data_dir, &cfg)?;
        // Surface a registration failure (e.g. no permission to create tasks) but keep the
        // saved policy — fail loud, never silently pretend the job was scheduled.
        let os_result = scheduler::apply_os_schedule(&cfg);
        let registered = scheduler::task_registered();
        if let Err(e) = os_result {
            return Err(format!("Saved the schedule, but the Windows task could not be updated: {e}"));
        }
        Ok(ScheduleConfig { enabled, interval_hours, task_registered: registered })
    })
    .await
}

/// Run an auto-rescan of every online known volume NOW, on a background thread, streaming the
/// same `scan://progress`/`scan://log`/`scan://done` events as a manual scan. Reuses the
/// `scan_active`/`scan_cancel` guards so it never collides with (or is collided by) a manual
/// scan. Refuses if a scan is already running.
#[tauri::command]
pub fn run_rescan_now(app: tauri::AppHandle) -> Result<(), String> {
    use sift_core::engine::scheduler;
    let state = app.state::<AppState>();
    if state.scan_active.swap(true, Ordering::SeqCst) {
        return Err("A scan is already running. Stop it first, then start a rescan.".into());
    }
    state.scan_cancel.store(false, Ordering::SeqCst);

    let app2 = app.clone();
    let spawn = std::thread::Builder::new()
        .name("sift-auto-rescan".into())
        .spawn(move || {
            let state = app2.state::<AppState>();
            struct ActiveGuard(Arc<AtomicBool>);
            impl Drop for ActiveGuard {
                fn drop(&mut self) {
                    self.0.store(false, Ordering::SeqCst);
                }
            }
            let _active_guard = ActiveGuard(state.scan_active.clone());
            let cancel = state.scan_cancel.clone();

            let result = {
                let conn = match state.writer.lock() {
                    Ok(c) => c,
                    Err(_) => {
                        let _ = app2.emit("scan://error", "writer lock poisoned".to_string());
                        return;
                    }
                };
                let app3 = app2.clone();
                let app4 = app2.clone();
                scheduler::rescan_online_volumes(
                    &conn,
                    cancel,
                    move |p| {
                        let _ = app3.emit("scan://progress", p);
                    },
                    move |l| {
                        let _ = app4.emit("scan://log", l);
                    },
                )
            };

            match result {
                Ok(0) => {
                    let _ = app2.emit("scan://error", "No online drives to rescan — connect a known drive and try again.".to_string());
                }
                Ok(_) => { /* ScanService already emitted scan://done with the job id */ }
                Err(e) => {
                    let _ = app2.emit("scan://error", e);
                }
            }
        });

    if let Err(e) = spawn {
        state.scan_active.store(false, Ordering::SeqCst);
        return Err(format!("could not start the rescan thread: {e}"));
    }
    Ok(())
}

// ---------------------------------------------------------------------------
// Scan (background thread, streams scan://progress)
// ---------------------------------------------------------------------------

#[derive(serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SourceInput {
    pub volume_mount: String,
    pub rel_root: Option<String>,
    pub source_id: Option<i64>,
}

/// Begin a scan on a background thread. Returns immediately; progress arrives as
/// `scan://progress`/`scan://log` events and a terminal `scan://done`. Refuses (with a clear
/// error) if a scan is already running — never silently blocks.
#[tauri::command]
pub fn start_scan(
    sources: Vec<SourceInput>,
    filters: ScanFilters,
    mode: ScanMode,
    app: tauri::AppHandle,
) -> Result<(), String> {
    let state = app.state::<AppState>();
    if state.scan_active.swap(true, Ordering::SeqCst) {
        return Err("A scan is already running. Stop it first, then start a new one.".into());
    }
    state.scan_cancel.store(false, Ordering::SeqCst);

    let app2 = app.clone();
    std::thread::spawn(move || {
        let state = app2.state::<AppState>();
        struct ActiveGuard(Arc<AtomicBool>);
        impl Drop for ActiveGuard {
            fn drop(&mut self) {
                self.0.store(false, Ordering::SeqCst);
            }
        }
        let _active_guard = ActiveGuard(state.scan_active.clone());
        let cancel = state.scan_cancel.clone();
        let srcs: Vec<Source> = sources
            .into_iter()
            .map(|s| Source { volume_mount: s.volume_mount, rel_root: s.rel_root, source_id: s.source_id })
            .collect();

        let result = {
            let conn = match state.writer.lock() {
                Ok(c) => c,
                Err(_) => {
                    let _ = app2.emit("scan://error", "writer lock poisoned".to_string());
                    return;
                }
            };
            let app3 = app2.clone();
            let app4 = app2.clone();
            ScanService::run(
                &conn,
                &srcs,
                &filters,
                mode,
                cancel,
                move |p| {
                    let _ = app3.emit("scan://progress", p);
                },
                move |l| {
                    let _ = app4.emit("scan://log", l);
                },
            )
        };

        match result {
            Ok(job_id) => {
                let _ = app2.emit("scan://done", job_id);
            }
            Err(e) => {
                let _ = app2.emit("scan://error", e);
            }
        }
    });
    Ok(())
}

#[tauri::command]
pub fn cancel_scan(state: tauri::State<'_, AppState>) {
    state.scan_cancel.store(true, Ordering::SeqCst);
}

#[tauri::command]
pub fn scan_is_active(state: tauri::State<'_, AppState>) -> bool {
    state.scan_active.load(Ordering::SeqCst)
}
