//! Scheduled & background auto-rescan — keep the persistent index fresh on a cadence.
//!
//! The whole moat (a persistent, history-aware content index) is only as valuable as it is
//! fresh, yet today every refresh is a manual act. This module automates it the pragmatic
//! Windows way: a **Task Scheduler** job (created/removed via `schtasks.exe`) relaunches
//! `Sift.exe --rescan` on an interval, even when the UI window is closed. The headless run
//! reuses the EXACT same `ScanService::run` path as a manual scan (so it benefits from
//! `repo::upsert_file`'s incremental reuse — unchanged size+mtime keeps the content_id and
//! only re-hashes what actually changed) over every currently-online known volume.
//!
//! Persistence: the cadence is a tiny `schedule.json` next to the index in `Sift-Data\`
//! (resolved via the same `data_dir` used for quarantine/exports), so reading it never needs
//! the DB writer lock. Default = disabled (opt-in).
//!
//! Design rules honored:
//!   * ZERO change to `ScanService` behavior — we only orchestrate around it.
//!   * Fail loud, honest results — errors propagate as `Result<_, String>`.
//!   * Windows-gated OS integration; non-Windows builds still compile (tests run anywhere).

use crate::db::{self, repo};
use crate::engine::scan_service::{ScanService, Source};
use crate::model::{ScanFilters, ScanMode, ScheduleConfig};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::atomic::AtomicBool;
use std::sync::Arc;

/// The fixed Task Scheduler task name we create/update/delete. Stable so re-registering
/// replaces the prior job instead of stacking duplicates.
const TASK_NAME: &str = "SiftAutoRescan";

/// File that persists the schedule policy, beside the portable index.
fn config_path(data_dir: &Path) -> PathBuf {
    data_dir.join("schedule.json")
}

/// Read the persisted schedule policy. A missing/unparseable file is NOT an error — it means
/// "never configured", so we return the disabled default (opt-in behavior).
pub fn load_config(data_dir: &Path) -> ScheduleConfig {
    match std::fs::read_to_string(config_path(data_dir)) {
        Ok(text) => serde_json::from_str(&text).unwrap_or_default(),
        Err(_) => ScheduleConfig::default(),
    }
}

/// Persist the schedule policy as `schedule.json`. Fail loud if the write fails.
pub fn save_config(data_dir: &Path, cfg: &ScheduleConfig) -> Result<(), String> {
    let text = serde_json::to_string_pretty(cfg).map_err(|e| format!("serialize schedule: {e}"))?;
    std::fs::write(config_path(data_dir), text).map_err(|e| format!("write schedule.json: {e}"))
}

/// Clamp an interval to a sane range so a typo can't create a job that fires every minute or
/// once a decade. Hours, 1..=720 (up to ~monthly).
fn clamp_hours(h: i64) -> i64 {
    h.clamp(1, 720)
}

/// Suppress the console window that spawning `schtasks.exe` would otherwise flash on Windows
/// (mirrors `mediaprobe::hide_window`).
#[cfg(windows)]
fn hide_window(cmd: &mut Command) {
    use std::os::windows::process::CommandExt;
    const CREATE_NO_WINDOW: u32 = 0x0800_0000;
    cmd.creation_flags(CREATE_NO_WINDOW);
}
#[cfg(not(windows))]
fn hide_window(_cmd: &mut Command) {}

/// Absolute path to the running executable, as a string (for the schtasks `/tr` action).
fn current_exe_string() -> Result<String, String> {
    std::env::current_exe()
        .map_err(|e| format!("resolve current exe: {e}"))
        .map(|p| p.to_string_lossy().to_string())
}

/// Apply a policy to the OS scheduler: when enabled, (re)create the Task Scheduler job;
/// when disabled, remove it. Best-effort registration is surfaced as an error string so the
/// command layer can report it — but the persisted config is the source of truth either way.
///
/// On non-Windows builds this is a no-op success (there is no `schtasks`).
pub fn apply_os_schedule(cfg: &ScheduleConfig) -> Result<(), String> {
    #[cfg(windows)]
    {
        if cfg.enabled {
            register_task(clamp_hours(cfg.interval_hours))
        } else {
            // Removing a task that doesn't exist must not be treated as a failure.
            let _ = remove_task();
            Ok(())
        }
    }
    #[cfg(not(windows))]
    {
        let _ = cfg;
        Ok(())
    }
}

/// Create or replace the Windows Task Scheduler job that relaunches `Sift.exe --rescan` every
/// `interval_hours`. `/f` overwrites any existing task with the same name (idempotent update);
/// `/sc HOURLY /mo N` sets the cadence; `/rl LIMITED` runs without elevation.
#[cfg(windows)]
pub fn register_task(interval_hours: i64) -> Result<(), String> {
    let exe = current_exe_string()?;
    // Quote the exe path inside the /tr action so spaces (e.g. "Program Files") are safe.
    let action = format!("\"{exe}\" --rescan");
    let mut cmd = Command::new("schtasks");
    cmd.args([
        "/create",
        "/tn",
        TASK_NAME,
        "/tr",
        &action,
        "/sc",
        "HOURLY",
        "/mo",
        &interval_hours.to_string(),
        "/rl",
        "LIMITED",
        "/f",
    ]);
    hide_window(&mut cmd);
    run_schtasks(cmd)
}

/// Remove the auto-rescan task. `/f` suppresses the confirmation prompt.
#[cfg(windows)]
pub fn remove_task() -> Result<(), String> {
    let mut cmd = Command::new("schtasks");
    cmd.args(["/delete", "/tn", TASK_NAME, "/f"]);
    hide_window(&mut cmd);
    run_schtasks(cmd)
}

/// Whether the auto-rescan Task Scheduler job currently exists (for the Settings indicator).
/// `schtasks /query` exits non-zero when the task is absent — we map that to `false`, not an
/// error, so a missing task is a normal state.
#[cfg(windows)]
pub fn task_registered() -> bool {
    let mut cmd = Command::new("schtasks");
    cmd.args(["/query", "/tn", TASK_NAME]);
    hide_window(&mut cmd);
    cmd.output().map(|o| o.status.success()).unwrap_or(false)
}
#[cfg(not(windows))]
pub fn task_registered() -> bool {
    false
}

/// Run a prepared `schtasks` command and turn a non-zero exit into a readable error (its
/// stderr explains *why* — e.g. no permission to create tasks).
#[cfg(windows)]
fn run_schtasks(mut cmd: Command) -> Result<(), String> {
    let out = cmd.output().map_err(|e| format!("could not run schtasks: {e}"))?;
    if out.status.success() {
        Ok(())
    } else {
        let msg = String::from_utf8_lossy(&out.stderr);
        let msg = msg.trim();
        Err(if msg.is_empty() {
            format!("schtasks failed (exit {:?})", out.status.code())
        } else {
            format!("schtasks failed: {msg}")
        })
    }
}

/// Build a `Source` per currently-online known volume (whole-volume, default filters). Returns
/// an empty vec when nothing is online — the caller treats that as a successful no-op.
fn online_volume_sources(conn: &rusqlite::Connection) -> Result<Vec<Source>, String> {
    let volumes = repo::list_volumes(conn).map_err(|e| e.to_string())?;
    Ok(volumes
        .into_iter()
        .filter(|v| v.is_online)
        .filter_map(|v| {
            v.mount_point.map(|mount| Source {
                volume_mount: mount,
                rel_root: None,
                source_id: None,
            })
        })
        .collect())
}

/// Run a full rescan of every online known volume on the GIVEN writer connection, reusing the
/// real scan engine. `cancel` lets a UI-triggered run be stopped; pass a fresh `false` flag for
/// the headless path. Progress/log callbacks let the in-app path stream `scan://` events; the
/// headless path passes no-ops. Returns the number of volumes scanned (0 = nothing online).
pub fn rescan_online_volumes(
    conn: &rusqlite::Connection,
    cancel: Arc<AtomicBool>,
    mut emit: impl FnMut(crate::model::ScanProgress),
    mut emit_log: impl FnMut(crate::model::ScanLogEvent),
) -> Result<usize, String> {
    let sources = online_volume_sources(conn)?;
    if sources.is_empty() {
        return Ok(0);
    }
    let filters = ScanFilters::default();
    // Thorough so an auto-rescan resolves duplicates to full-hash confidence, exactly like the
    // default manual scan. ScanService is unchanged — we only feed it the online volumes.
    ScanService::run(
        conn,
        &sources,
        &filters,
        ScanMode::Thorough,
        cancel,
        &mut emit,
        &mut emit_log,
    )?;
    Ok(sources.len())
}

/// HEADLESS entry point invoked by `main.rs` when the EXE is launched with `--rescan` (by the
/// Task Scheduler job). Opens its OWN writer connection (no Tauri/UI involved), rescans every
/// online known volume, then returns. Never panics out — returns a readable error string.
pub fn run_headless(db_path: &Path) -> Result<usize, String> {
    let conn = db::open_writer(db_path).map_err(|e| format!("open index: {e}"))?;
    let cancel = Arc::new(AtomicBool::new(false));
    rescan_online_volumes(&conn, cancel, |_p| {}, |_l| {})
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db;
    use rusqlite::Connection;

    fn mem() -> Connection {
        let c = Connection::open_in_memory().unwrap();
        db::migrate(&c).unwrap();
        c
    }

    #[test]
    fn config_round_trips_through_disk_and_defaults_disabled() {
        let dir = std::env::temp_dir().join(format!("sift_sched_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();

        // A never-configured folder yields the disabled default (opt-in).
        let loaded = load_config(&dir);
        assert!(!loaded.enabled, "default schedule must be disabled (opt-in)");

        // Saving then loading returns the same policy.
        let cfg = ScheduleConfig { enabled: true, interval_hours: 24, ..Default::default() };
        save_config(&dir, &cfg).unwrap();
        let back = load_config(&dir);
        assert!(back.enabled);
        assert_eq!(back.interval_hours, 24);

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn interval_is_clamped_to_a_sane_range() {
        assert_eq!(clamp_hours(0), 1, "sub-hourly typo is clamped up to 1h");
        assert_eq!(clamp_hours(-5), 1);
        assert_eq!(clamp_hours(6), 6, "a normal cadence passes through");
        assert_eq!(clamp_hours(100_000), 720, "absurd cadence is capped");
    }

    #[test]
    fn rescan_is_a_noop_when_no_volume_is_online() {
        // With no online volume in the index, a rescan does nothing and reports 0 — it must
        // not error or invent a scan over nothing.
        let conn = mem();
        conn.execute(
            "INSERT INTO volume (id, fs_label, fs_type, is_online, is_removable, first_seen_at, last_seen_at, current_mount_point) \
             VALUES (1,'Archive','NTFS',0,1,0,0,NULL)",
            [],
        )
        .unwrap();
        let n = rescan_online_volumes(&conn, Arc::new(AtomicBool::new(false)), |_| {}, |_| {}).unwrap();
        assert_eq!(n, 0, "offline-only index yields a 0-volume rescan");
    }
}
