//! Deletion service — the ONLY code path that removes files. Enforces CODING_RULES §1:
//!   * re-validates the plan against the engine safety invariants (defense in depth —
//!     even if the UI was bypassed, the engine refuses unsafe plans here);
//!   * writes the audit intent row BEFORE each removal (lesson ORDER-001);
//!   * defaults to Recycle Bin; quarantine moves, permanent deletes;
//!   * reports partial success as PARTIAL, never as success (lesson PARTIAL-FAIL-001);
//!   * on removal, flips file.status to 'deleted_by_user' — the row & content history
//!     are preserved forever (the historical-intelligence promise).

use crate::db::repo;
use crate::engine::safety::{self, Copy as SafetyCopy, Decision};
use crate::model::{DeletionFailure, DeletionOutcome, FolderDeletionGroup, FolderDeletionPreview, FolderEntry};
use rusqlite::{params, Connection, OptionalExtension, Result as SqlResult};
use std::path::{Path, PathBuf};

pub enum Mode {
    Recycle,
    Quarantine(PathBuf),
    Permanent,
}

impl Mode {
    fn label(&self) -> &'static str {
        match self {
            Mode::Recycle => "recycle",
            Mode::Quarantine(_) => "quarantine",
            Mode::Permanent => "permanent",
        }
    }
    fn reversible(&self) -> bool {
        !matches!(self, Mode::Permanent)
    }
}

struct Item {
    file_id: i64,
    content_id: Option<i64>,
    volume_id: i64,
    decision_remove: bool,
    status_present: bool,
    volume_online: bool,
    mount_point: Option<String>,
    path_raw: String, // volume-relative
}

/// Execute a deletion plan. `acknowledged_online` carries the content ids for which the
/// user explicitly accepted "only offline copies will remain". Returns a structured,
/// honest outcome. Refuses (Err) the entire plan if a safety invariant is violated.
pub fn execute_plan(
    conn: &Connection,
    plan_id: i64,
    mode: Mode,
    acknowledged_online: &[i64],
    protected: &[String],
) -> Result<DeletionOutcome, String> {
    let items = load_items(conn, plan_id).map_err(|e| format!("load plan: {e}"))?;
    if items.is_empty() {
        return Err("plan has no items".into());
    }

    // --- Engine-authoritative safety gate (defense in depth) ---
    let pairs: Vec<(SafetyCopy, Decision)> = items
        .iter()
        .filter_map(|i| {
            i.content_id.map(|cid| {
                (
                    SafetyCopy {
                        file_id: i.file_id,
                        content_id: cid,
                        volume_online: i.volume_online,
                        status_present: i.status_present,
                        path_raw: i.path_raw.clone(),
                    },
                    if i.decision_remove { Decision::Remove } else { Decision::Keep },
                )
            })
        })
        .collect();
    let report = safety::validate_plan(&pairs, acknowledged_online);
    if !report.ok() {
        return Err(format!(
            "refused by safety engine: {} violation(s) — plan would destroy a last surviving copy",
            report.violations.len()
        ));
    }

    // --- Execute removals one by one, auditing intent before each ---
    let mut removed = 0u64;
    let mut failed = 0u64;
    let mut failures = Vec::new();
    let mut reclaimed = 0u64;

    for item in items.iter().filter(|i| i.decision_remove) {
        let abs = match absolute_path(item) {
            Some(p) => p,
            None => {
                failed += 1;
                failures.push(DeletionFailure {
                    file_id: item.file_id,
                    path_raw: item.path_raw.clone(),
                    reason: "owning volume is offline — cannot remove".into(),
                });
                continue;
            }
        };

        let audit_id = repo::audit_intent(
            conn,
            mode.label(),
            Some(item.file_id),
            item.content_id,
            Some(item.volume_id),
            Some(&abs),
            mode.reversible(),
            None,
        )
        .map_err(|e| format!("audit: {e}"))?;

        match remove_one(&abs, &mode, protected) {
            Ok(()) => {
                // The file is ALREADY gone from disk. A failure in the post-removal
                // bookkeeping must NOT abort the loop or be reported as a total failure
                // (lesson PARTIAL-FAIL-001) — that would misreport real deletions. Capture
                // size before status flips, record-and-continue, and always finalize audit.
                reclaimed += size_of(conn, item.file_id).unwrap_or(0) as u64;
                if let Err(e) = finalize_removed(conn, item) {
                    let _ = set_item_result(conn, plan_id, item.file_id, "removed", Some(&format!("finalize failed: {e}")));
                } else {
                    let _ = set_item_result(conn, plan_id, item.file_id, "removed", None);
                }
                let _ = repo::audit_outcome(conn, audit_id, "success");
                removed += 1;
            }
            Err(reason) => {
                let _ = set_item_result(conn, plan_id, item.file_id, "failed", Some(&reason));
                let _ = repo::audit_outcome(conn, audit_id, "failed");
                failed += 1;
                failures.push(DeletionFailure {
                    file_id: item.file_id,
                    path_raw: item.path_raw.clone(),
                    reason,
                });
            }
        }
    }

    let state = if failed == 0 {
        "success"
    } else if removed > 0 {
        "partial"
    } else {
        "failed"
    };
    let _ = conn.execute(
        "UPDATE deletion_plan SET state=?2, committed_at=?3 WHERE id=?1",
        params![plan_id, state, repo::now()],
    );

    Ok(DeletionOutcome {
        state: state.into(),
        removed,
        failed,
        failures,
        reclaimed_bytes: reclaimed,
    })
}

/// Directly remove a set of files by id (used for SIMILAR images and other distinct-file
/// selections). Unlike `execute_plan`, there is no content keep-at-least-one gate — these
/// are distinct files the user explicitly chose, not byte-identical copies of one content.
/// Still audits before acting, defaults to recycle, and reports partial vs full honestly.
pub fn delete_files(conn: &Connection, file_ids: &[i64], mode: Mode, protected: &[String]) -> Result<DeletionOutcome, String> {
    let mut removed = 0u64;
    let mut failed = 0u64;
    let mut failures = Vec::new();
    let mut reclaimed = 0u64;

    for &fid in file_ids {
        let item = match load_file_item(conn, fid) {
            Ok(Some(i)) => i,
            _ => {
                failed += 1;
                failures.push(DeletionFailure { file_id: fid, path_raw: String::new(), reason: "file not found in index".into() });
                continue;
            }
        };
        if !item.status_present || !item.volume_online {
            failed += 1;
            failures.push(DeletionFailure { file_id: fid, path_raw: item.path_raw.clone(), reason: "file is not present on an online drive".into() });
            continue;
        }
        let abs = match absolute_path(&item) {
            Some(a) => a,
            None => {
                failed += 1;
                failures.push(DeletionFailure { file_id: fid, path_raw: item.path_raw.clone(), reason: "owning volume is offline".into() });
                continue;
            }
        };
        let audit_id = repo::audit_intent(conn, mode.label(), Some(fid), item.content_id, Some(item.volume_id), Some(&abs), mode.reversible(), None)
            .map_err(|e| format!("audit: {e}"))?;
        match remove_one(&abs, &mode, protected) {
            Ok(()) => {
                reclaimed += size_of(conn, fid).unwrap_or(0) as u64;
                let _ = finalize_removed(conn, &item);
                let _ = repo::audit_outcome(conn, audit_id, "success");
                removed += 1;
            }
            Err(reason) => {
                let _ = repo::audit_outcome(conn, audit_id, "failed");
                failed += 1;
                failures.push(DeletionFailure { file_id: fid, path_raw: item.path_raw.clone(), reason });
            }
        }
    }

    let state = if failed == 0 { "success" } else if removed > 0 { "partial" } else { "failed" };
    Ok(DeletionOutcome { state: state.into(), removed, failed, failures, reclaimed_bytes: reclaimed })
}

fn load_file_item(conn: &Connection, file_id: i64) -> SqlResult<Option<Item>> {
    conn.query_row(
        "SELECT f.content_id AS cid, f.volume_id AS vid, f.status AS st, v.is_online AS online, \
                v.current_mount_point AS mp, f.path_raw AS path \
         FROM file f JOIN volume v ON v.id=f.volume_id WHERE f.id=?1",
        params![file_id],
        |r| {
            Ok(Item {
                file_id,
                content_id: r.get("cid")?,
                volume_id: r.get("vid")?,
                decision_remove: true,
                status_present: r.get::<_, String>("st")? == "present",
                volume_online: r.get::<_, i64>("online")? != 0,
                mount_point: r.get("mp")?,
                path_raw: r.get("path")?,
            })
        },
    )
    .optional()
}

fn finalize_removed(conn: &Connection, item: &Item) -> SqlResult<()> {
    conn.execute(
        "UPDATE file SET status='deleted_by_user', removed_at=?2 WHERE id=?1",
        params![item.file_id, repo::now()],
    )?;
    if let Some(cid) = item.content_id {
        repo::recompute_counters(conn, cid)?;
        repo::refresh_cluster(conn, cid)?;
    }
    Ok(())
}

fn size_of(conn: &Connection, file_id: i64) -> SqlResult<i64> {
    conn.query_row(
        "SELECT size_bytes AS sz FROM file WHERE id=?1",
        params![file_id],
        |r| r.get("sz"),
    )
}

fn load_items(conn: &Connection, plan_id: i64) -> SqlResult<Vec<Item>> {
    let mut stmt = conn.prepare(
        "SELECT pi.file_id AS fid, f.content_id AS cid, f.volume_id AS vid, \
                pi.decision AS dec, f.status AS st, v.is_online AS online, \
                v.current_mount_point AS mp, f.path_raw AS path \
         FROM deletion_plan_item pi \
         JOIN file f ON f.id=pi.file_id \
         JOIN volume v ON v.id=f.volume_id \
         WHERE pi.plan_id=?1",
    )?;
    let rows = stmt
        .query_map(params![plan_id], |r| {
            Ok(Item {
                file_id: r.get("fid")?,
                content_id: r.get("cid")?,
                volume_id: r.get("vid")?,
                decision_remove: r.get::<_, String>("dec")? == "remove",
                status_present: r.get::<_, String>("st")? == "present",
                volume_online: r.get::<_, i64>("online")? != 0,
                mount_point: r.get("mp")?,
                path_raw: r.get("path")?,
            })
        })?
        .collect::<SqlResult<_>>()?;
    Ok(rows)
}

fn set_item_result(
    conn: &Connection,
    plan_id: i64,
    file_id: i64,
    result: &str,
    detail: Option<&str>,
) -> SqlResult<()> {
    conn.execute(
        "UPDATE deletion_plan_item SET result=?3, result_detail=?4 WHERE plan_id=?1 AND file_id=?2",
        params![plan_id, file_id, result, detail],
    )?;
    Ok(())
}

/// Build the absolute path from the volume mount point + the volume-relative path_raw,
/// joining with the OS separator (Path::join). Returns None when the owning volume is
/// offline (no mount point) — we never act on an unreachable file.
fn absolute_path(item: &Item) -> Option<String> {
    let mount = item.mount_point.as_ref()?;
    let rel = item.path_raw.trim_start_matches(['\\', '/']);
    Some(std::path::Path::new(mount).join(rel).to_string_lossy().to_string())
}

// --------------------------------------------------------------------------
// Platform removal primitives. Recycle Bin via the cross-platform `trash` crate
// (handles Windows IFileOperation / long paths correctly); permanent + quarantine
// via std::fs with verbatim long-path prefixing on Windows.
// --------------------------------------------------------------------------

/// Build the honest protected-location refusal, distinguishing a permanent BUILT-IN system rule
/// (Windows / Program Files / System Volume Information …) from a USER-ADDED folder the user can
/// remove in Settings. Returns None when the path is not protected.
fn protected_error(abs: &str, protected: &[String]) -> Option<String> {
    safety::matched_protected_kind(abs, protected).map(|(root, builtin)| {
        if builtin {
            format!("'{root}' is a built-in system protection — Singula never deletes from Windows system areas (permanent, not configurable).")
        } else {
            format!("protected location '{root}' — you added it in Settings -> Protected folders; remove it there to delete from this location.")
        }
    })
}

fn remove_one(abs: &str, mode: &Mode, protected: &[String]) -> Result<(), String> {
    // Protected-folders guard: refuse to remove anything under a protected location. Additive
    // safety — this can only ever PREVENT a deletion.
    if let Some(msg) = protected_error(abs, protected) {
        return Err(msg);
    }
    match mode {
        Mode::Recycle => trash::delete(abs).map_err(|e| format!("recycle: {e}")),
        Mode::Quarantine(dir) => move_to_quarantine(abs, dir),
        Mode::Permanent => std::fs::remove_file(native(abs)).map_err(|e| e.to_string()),
    }
}

fn move_to_quarantine(abs: &str, dir: &std::path::Path) -> Result<(), String> {
    let name = std::path::Path::new(abs).file_name().ok_or("no file name")?;
    std::fs::create_dir_all(dir).map_err(|e| e.to_string())?;
    let dest = dir.join(name);
    // rename is atomic on the same volume; fall back to copy+delete across volumes.
    match std::fs::rename(native(abs), &dest) {
        Ok(()) => Ok(()),
        Err(_) => {
            std::fs::copy(native(abs), &dest).map_err(|e| e.to_string())?;
            std::fs::remove_file(native(abs)).map_err(|e| e.to_string())
        }
    }
}

/// Long-path-safe path for raw fs calls: verbatim `\\?\` on Windows, plain elsewhere.
#[cfg(windows)]
fn native(abs: &str) -> String {
    crate::engine::pathkey::to_verbatim(abs)
}
#[cfg(not(windows))]
fn native(abs: &str) -> String {
    abs.to_string()
}

// ===========================================================================
// ADVANCED (parent-folder) DELETION — Index Explorer.
//
// Lets the user delete not just a selected file but its whole containing folder
// (e.g. a movie's own folder, sweeping up its `covers`/`sample` subfolders too).
//
// The DANGER this guards against (per the user's scenario): if a selected file
// sits DIRECTLY in a shared root like "Downloads", deleting its parent would wipe
// Downloads and everything in it. The safety gate below refuses any folder that
// is a drive/source root, a known system/user folder, a protected location, or
// simply holds too many items to plausibly be a per-item folder. The refusal is
// enforced HERE in the engine (defense in depth) — never trust the UI preview.
// ===========================================================================

/// Folder names that are shared roots — never safe to delete wholesale.
const SHARED_ROOTS: &[&str] = &[
    "downloads", "desktop", "documents", "pictures", "videos", "movies", "music",
    "onedrive", "dropbox", "google drive", "icloud drive", "users", "public",
    "appdata", "program files", "program files (x86)", "programdata", "windows",
    "$recycle.bin", "temp", "tmp", "system32",
];

/// A bare drive root like `D:` / `D:\`.
fn is_drive_root(abs: &str) -> bool {
    let t = abs.trim_end_matches(['\\', '/']);
    t.len() == 2 && t.as_bytes().get(1) == Some(&b':')
}

/// Risk verdict for deleting `folder_abs` wholesale. `immediate` = its direct child
/// count, `collateral` = children that are NOT user-selected files. Pure + reused by
/// both the preview and the engine re-check, so the gate cannot be bypassed.
fn folder_risk(
    folder_abs: &str,
    mount: &str,
    immediate: i64,
    collateral: i64,
    protected: &[String],
) -> (&'static str, String, bool) {
    if let Some(root) = safety::matched_protected(folder_abs, protected) {
        return ("danger", format!("protected location ({root}) — excluded"), false);
    }
    let norm = folder_abs.trim_end_matches(['\\', '/']);
    let mnt = mount.trim_end_matches(['\\', '/']);
    if is_drive_root(folder_abs) || norm.eq_ignore_ascii_case(mnt) {
        return (
            "danger",
            "this is a drive / scan-source root — deleting it would wipe the whole drive".into(),
            false,
        );
    }
    let fname = Path::new(norm)
        .file_name()
        .map(|s| s.to_string_lossy().to_lowercase())
        .unwrap_or_default();
    if SHARED_ROOTS.iter().any(|r| *r == fname) {
        return (
            "danger",
            format!("\"{fname}\" is a shared system/user folder — too dangerous to delete wholesale"),
            false,
        );
    }
    if immediate > 25 {
        return (
            "danger",
            format!("folder holds {immediate} items — looks like a shared folder, not a per-item folder; excluded"),
            false,
        );
    }
    if collateral > 6 {
        return (
            "caution",
            format!("folder also contains {collateral} other item(s) that will be deleted too"),
            true,
        );
    }
    (
        "safe",
        "dedicated folder — its few extra files (covers, samples, etc.) get cleaned up with it".into(),
        true,
    )
}

/// Recursive byte+file count, bounded by an entry budget so a mis-pick on a huge tree
/// can never hang the preview.
fn dir_size_bounded(path: &Path, budget: &mut u64) -> (i64, i64) {
    let mut bytes = 0i64;
    let mut files = 0i64;
    let rd = match std::fs::read_dir(path) {
        Ok(r) => r,
        Err(_) => return (0, 0),
    };
    for e in rd.flatten() {
        if *budget == 0 {
            break;
        }
        *budget -= 1;
        match e.file_type() {
            Ok(ft) if ft.is_dir() => {
                let (b, f) = dir_size_bounded(&e.path(), budget);
                bytes += b;
                files += f;
            }
            Ok(ft) if ft.is_file() => {
                if let Ok(m) = e.metadata() {
                    bytes += m.len() as i64;
                }
                files += 1;
            }
            _ => {}
        }
    }
    (bytes, files)
}

fn count_immediate(folder: &str) -> i64 {
    std::fs::read_dir(folder)
        .map(|rd| rd.flatten().count() as i64)
        .unwrap_or(0)
}

/// Preview an advanced deletion: group the selected files by parent folder, enumerate the
/// collateral, and assign each folder a risk verdict. Read-only.
pub fn preview_folders(
    conn: &Connection,
    file_ids: &[i64],
    protected: &[String],
) -> Result<FolderDeletionPreview, String> {
    struct Grp {
        folder_abs: String,
        volume_id: i64,
        mount: String,
        selected_abs: Vec<String>,
    }
    let mut skipped = Vec::new();
    let mut groups: std::collections::BTreeMap<String, Grp> = std::collections::BTreeMap::new();

    for &fid in file_ids {
        let item = match load_file_item(conn, fid) {
            Ok(Some(i)) => i,
            _ => {
                skipped.push(DeletionFailure { file_id: fid, path_raw: String::new(), reason: "file not found in index".into() });
                continue;
            }
        };
        if !item.status_present || !item.volume_online {
            skipped.push(DeletionFailure { file_id: fid, path_raw: item.path_raw.clone(), reason: "file is not present on an online drive".into() });
            continue;
        }
        let mount = match &item.mount_point {
            Some(m) => m.clone(),
            None => {
                skipped.push(DeletionFailure { file_id: fid, path_raw: item.path_raw.clone(), reason: "owning volume is offline".into() });
                continue;
            }
        };
        let abs = match absolute_path(&item) {
            Some(a) => a,
            None => {
                skipped.push(DeletionFailure { file_id: fid, path_raw: item.path_raw.clone(), reason: "owning volume is offline".into() });
                continue;
            }
        };
        let parent = match Path::new(&abs).parent() {
            Some(p) => p.to_string_lossy().to_string(),
            None => {
                skipped.push(DeletionFailure { file_id: fid, path_raw: item.path_raw.clone(), reason: "file has no parent folder".into() });
                continue;
            }
        };
        groups
            .entry(parent.to_lowercase())
            .or_insert_with(|| Grp { folder_abs: parent.clone(), volume_id: item.volume_id, mount: mount.clone(), selected_abs: Vec::new() })
            .selected_abs
            .push(abs);
    }

    let mut out_groups = Vec::new();
    let (mut elig_folders, mut elig_bytes, mut elig_files) = (0i64, 0i64, 0i64);
    for (_k, g) in groups {
        let folder = &g.folder_abs;
        let sel_set: std::collections::HashSet<String> =
            g.selected_abs.iter().map(|s| s.to_lowercase()).collect();
        let mut immediate = 0i64;
        let mut selected_inside = 0i64;
        let mut immediate_file_bytes = 0i64;
        let mut sample: Vec<FolderEntry> = Vec::new();
        if let Ok(rd) = std::fs::read_dir(folder) {
            for e in rd.flatten() {
                immediate += 1;
                let name = e.file_name().to_string_lossy().to_string();
                let is_dir = e.file_type().map(|t| t.is_dir()).unwrap_or(false);
                let is_sel = sel_set.contains(&e.path().to_string_lossy().to_lowercase());
                if is_sel {
                    selected_inside += 1;
                }
                let sz = if is_dir { 0 } else { e.metadata().map(|m| m.len() as i64).unwrap_or(0) };
                if !is_dir {
                    immediate_file_bytes += sz;
                }
                if sample.len() < 16 {
                    sample.push(FolderEntry { name, is_dir, size_bytes: sz, is_selected: is_sel });
                }
            }
        }
        let collateral = (immediate - selected_inside).max(0);
        let (risk, reason, eligible) = folder_risk(folder, &g.mount, immediate, collateral, protected);
        let (total_bytes, recursive_files) = if eligible {
            let mut budget = 200_000u64;
            dir_size_bounded(Path::new(folder), &mut budget)
        } else {
            (immediate_file_bytes, immediate)
        };
        if eligible {
            // Fill subfolder sizes in the preview so the user sees "covers (12 MB)" etc.
            for ent in sample.iter_mut() {
                if ent.is_dir {
                    let mut b = 50_000u64;
                    let (bytes, _) = dir_size_bounded(&Path::new(folder).join(&ent.name), &mut b);
                    ent.size_bytes = bytes;
                }
            }
            elig_folders += 1;
            elig_bytes += total_bytes;
            elig_files += recursive_files;
        }
        let folder_name = Path::new(folder)
            .file_name()
            .map(|s| s.to_string_lossy().to_string())
            .unwrap_or_else(|| folder.clone());
        out_groups.push(FolderDeletionGroup {
            folder_abs: folder.clone(),
            folder_name,
            volume_id: g.volume_id,
            immediate_entries: immediate,
            recursive_files,
            total_bytes,
            selected_inside,
            sample,
            risk: risk.into(),
            risk_reason: reason,
            eligible,
        });
    }
    // Eligible first, then biggest first.
    out_groups.sort_by(|a, b| b.eligible.cmp(&a.eligible).then(b.total_bytes.cmp(&a.total_bytes)));

    Ok(FolderDeletionPreview {
        groups: out_groups,
        skipped,
        eligible_folders: elig_folders,
        eligible_bytes: elig_bytes,
        eligible_files: elig_files,
    })
}

/// Find the online volume whose mount point owns `abs` (longest matching mount).
fn volume_for_path(conn: &Connection, abs: &str) -> Option<(i64, String)> {
    let mut stmt = conn
        .prepare("SELECT id, current_mount_point FROM volume WHERE is_online=1 AND current_mount_point IS NOT NULL")
        .ok()?;
    let rows = stmt
        .query_map([], |r| Ok((r.get::<_, i64>(0)?, r.get::<_, String>(1)?)))
        .ok()?;
    let lower = abs.to_lowercase();
    let mut best: Option<(i64, String)> = None;
    for (id, mount) in rows.flatten() {
        let m = mount.trim_end_matches(['\\', '/']).to_lowercase();
        if m.is_empty() {
            continue;
        }
        if lower == m || lower.starts_with(&format!("{m}\\")) || lower.starts_with(&format!("{m}/")) {
            if best.as_ref().map(|(_, bm)| mount.len() > bm.len()).unwrap_or(true) {
                best = Some((id, mount));
            }
        }
    }
    best
}

/// Remove a whole directory: Recycle Bin (reversible), quarantine (move), or permanent.
fn remove_dir_one(abs: &str, mode: &Mode, protected: &[String]) -> Result<(), String> {
    if let Some(msg) = protected_error(abs, protected) {
        return Err(msg);
    }
    match mode {
        Mode::Recycle => trash::delete(abs).map_err(|e| format!("recycle: {e}")),
        Mode::Quarantine(dir) => {
            let name = Path::new(abs).file_name().ok_or("no folder name")?;
            std::fs::create_dir_all(dir).map_err(|e| e.to_string())?;
            std::fs::rename(native(abs), dir.join(name)).map_err(|e| format!("quarantine move: {e}"))
        }
        Mode::Permanent => std::fs::remove_dir_all(native(abs)).map_err(|e| e.to_string()),
    }
}

/// After a folder is gone from disk, flip every indexed file under it to deleted_by_user
/// (the rows + content history are preserved forever, like single-file deletion).
fn mark_folder_removed(conn: &Connection, volume_id: i64, mount: &str, folder_abs: &str) -> SqlResult<()> {
    let prefix = format!("{}\\", folder_abs.trim_end_matches(['\\', '/']).to_lowercase());
    let rows: Vec<(i64, Option<i64>, String)> = {
        let mut stmt = conn.prepare("SELECT id, content_id, path_raw FROM file WHERE volume_id=?1 AND status='present'")?;
        let v = stmt
            .query_map(params![volume_id], |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)))?
            .collect::<SqlResult<Vec<_>>>()?;
        v
    };
    for (id, cid, path_raw) in rows {
        let abs = Path::new(mount)
            .join(path_raw.trim_start_matches(['\\', '/']))
            .to_string_lossy()
            .to_lowercase();
        if abs.starts_with(&prefix) {
            conn.execute("UPDATE file SET status='deleted_by_user', removed_at=?2 WHERE id=?1", params![id, repo::now()])?;
            if let Some(c) = cid {
                repo::recompute_counters(conn, c)?;
                repo::refresh_cluster(conn, c)?;
            }
        }
    }
    Ok(())
}

/// Execute an advanced (folder) deletion. Each folder is re-validated against the safety
/// gate here (defense in depth), audited before removal, and reported honestly (partial ≠
/// success). Deepest folders first so a parent+child pair in one request can't conflict.
pub fn delete_folders(
    conn: &Connection,
    folders: &[String],
    mode: Mode,
    protected: &[String],
) -> Result<DeletionOutcome, String> {
    let mut removed = 0u64;
    let mut failed = 0u64;
    let mut failures = Vec::new();
    let mut reclaimed = 0u64;

    let mut list: Vec<String> = folders.to_vec();
    list.sort_by_key(|s| std::cmp::Reverse(s.len()));

    for folder in &list {
        let (volume_id, mount) = match volume_for_path(conn, folder) {
            Some(v) => v,
            None => {
                failed += 1;
                failures.push(DeletionFailure { file_id: 0, path_raw: folder.clone(), reason: "folder is not on a known online drive".into() });
                continue;
            }
        };
        // Re-validate the HARD safety gate — never trust the UI preview.
        let immediate = count_immediate(folder);
        let (_risk, reason, eligible) = folder_risk(folder, &mount, immediate, immediate, protected);
        if !eligible {
            failed += 1;
            failures.push(DeletionFailure { file_id: 0, path_raw: folder.clone(), reason: format!("refused by safety gate: {reason}") });
            continue;
        }
        let mut budget = 1_000_000u64;
        let (bytes, _) = dir_size_bounded(Path::new(folder), &mut budget);
        let audit_id = repo::audit_intent(
            conn,
            &format!("{}_folder", mode.label()),
            None,
            None,
            Some(volume_id),
            Some(folder.as_str()),
            mode.reversible(),
            None,
        )
        .map_err(|e| format!("audit: {e}"))?;
        match remove_dir_one(folder, &mode, protected) {
            Ok(()) => {
                reclaimed += bytes as u64;
                let _ = mark_folder_removed(conn, volume_id, &mount, folder);
                let _ = repo::audit_outcome(conn, audit_id, "success");
                removed += 1;
            }
            Err(reason) => {
                let _ = repo::audit_outcome(conn, audit_id, "failed");
                failed += 1;
                failures.push(DeletionFailure { file_id: 0, path_raw: folder.clone(), reason });
            }
        }
    }

    let state = if failed == 0 { "success" } else if removed > 0 { "partial" } else { "failed" };
    Ok(DeletionOutcome { state: state.into(), removed, failed, failures, reclaimed_bytes: reclaimed })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db;

    fn mem() -> Connection {
        let c = Connection::open_in_memory().unwrap();
        db::migrate(&c).unwrap();
        c
    }

    #[test]
    fn refuses_plan_that_destroys_last_copy() {
        let conn = mem();
        // One online volume, one present file that is the sole copy of content 1.
        conn.execute("INSERT INTO volume(id,fs_label,fs_type,is_online,is_removable,first_seen_at,last_seen_at,current_mount_point) VALUES (1,'V','NTFS',1,0,0,0,'Z:\\')", []).unwrap();
        conn.execute("INSERT INTO content(id,full_hash,size_bytes,first_indexed_at,last_confirmed_at,known_copies,live_copies,online_copies) VALUES (1,X'00',10,0,0,1,1,1)", []).unwrap();
        conn.execute("INSERT INTO file(id,volume_id,content_id,path_raw,path_key,file_name,size_bytes,status,hash_state,first_seen_at,last_seen_at) VALUES (1,1,1,'a.bin','a.bin','a.bin',10,'present','full',0,0)", []).unwrap();
        conn.execute("INSERT INTO deletion_plan(id,created_at,mode,state) VALUES (1,0,'permanent','draft')", []).unwrap();
        conn.execute("INSERT INTO deletion_plan_item(plan_id,file_id,decision) VALUES (1,1,'remove')", []).unwrap();

        let err = execute_plan(&conn, 1, Mode::Permanent, &[], &[]).unwrap_err();
        assert!(err.contains("safety engine"), "got: {err}");
        // File must still be present — nothing was deleted.
        let st: String = conn.query_row("SELECT status AS s FROM file WHERE id=1", [], |r| r.get("s")).unwrap();
        assert_eq!(st, "present");
    }

    #[test]
    fn is_drive_root_detects() {
        assert!(is_drive_root("D:\\"));
        assert!(is_drive_root("D:"));
        assert!(!is_drive_root("D:\\Downloads"));
        assert!(!is_drive_root("D:\\Downloads\\MovieX"));
    }

    #[test]
    fn folder_risk_blocks_shared_roots_allows_dedicated() {
        let none: Vec<String> = vec![];
        // Drive root / source root => danger, excluded.
        assert!(!folder_risk("D:\\", "D:\\", 30, 30, &none).2);
        assert!(!folder_risk("E:\\Media", "E:\\Media", 5, 5, &none).2);
        // Known shared user folder (the dangerous "file directly in Downloads" case) => excluded.
        assert!(!folder_risk("D:\\Downloads", "D:\\", 20, 20, &none).2);
        // Protected system location => excluded.
        assert!(!folder_risk("C:\\Windows\\System32", "C:\\", 3, 3, &none).2);
        // Too many items to be a per-item folder => excluded.
        assert!(!folder_risk("D:\\Films", "D:\\", 40, 40, &none).2);
        // A dedicated per-item folder (movie + covers + sample) => safe + eligible.
        let (risk, _, eligible) = folder_risk("D:\\Downloads\\MovieX", "D:\\", 4, 3, &none);
        assert!(eligible);
        assert_eq!(risk, "safe");
        // Mid-range collateral => caution but still eligible (user sees + confirms).
        let (risk, _, eligible) = folder_risk("D:\\stuff\\dir", "D:\\", 11, 10, &none);
        assert!(eligible);
        assert_eq!(risk, "caution");
    }

    #[test]
    fn preview_and_delete_folder_roundtrip() {
        use std::fs;
        let conn = mem();
        let base = std::env::temp_dir().join(format!("sift_fold_{}", std::process::id()));
        let _ = fs::remove_dir_all(&base);
        let mount = base.join("DRV");
        let movie_dir = mount.join("Downloads").join("MovieX");
        fs::create_dir_all(movie_dir.join("sample")).unwrap();
        fs::create_dir_all(movie_dir.join("covers")).unwrap();
        fs::write(movie_dir.join("movie.mkv"), vec![0u8; 1000]).unwrap();
        fs::write(movie_dir.join("sample").join("s.mkv"), vec![0u8; 100]).unwrap();
        fs::write(movie_dir.join("covers").join("c.jpg"), vec![0u8; 50]).unwrap();

        let mount_s = mount.to_string_lossy().to_string();
        conn.execute("INSERT INTO volume(id,fs_label,fs_type,is_online,is_removable,first_seen_at,last_seen_at,current_mount_point) VALUES (1,'V','NTFS',1,0,0,0,?1)", params![mount_s]).unwrap();
        conn.execute("INSERT INTO content(id,full_hash,size_bytes,first_indexed_at,last_confirmed_at,known_copies,live_copies,online_copies) VALUES (1,X'00',1000,0,0,2,2,2)", []).unwrap();
        conn.execute("INSERT INTO file(id,volume_id,content_id,path_raw,path_key,file_name,size_bytes,status,hash_state,first_seen_at,last_seen_at) VALUES (1,1,1,'Downloads\\MovieX\\movie.mkv','downloads/moviex/movie.mkv','movie.mkv',1000,'present','full',0,0)", []).unwrap();

        let none: Vec<String> = vec![];
        let prev = preview_folders(&conn, &[1], &none).unwrap();
        assert_eq!(prev.groups.len(), 1);
        let g = &prev.groups[0];
        assert!(g.eligible, "MovieX should be eligible; got {} ({})", g.risk, g.risk_reason);
        assert_eq!(g.selected_inside, 1);
        assert!(g.total_bytes >= 1150, "recursive size {}", g.total_bytes);

        let out = delete_folders(&conn, &[g.folder_abs.clone()], Mode::Permanent, &none).unwrap();
        assert_eq!(out.removed, 1, "failures: {:?}", out.failures);
        assert!(!movie_dir.exists(), "folder should be gone");
        let st: String = conn.query_row("SELECT status AS s FROM file WHERE id=1", [], |r| r.get("s")).unwrap();
        assert_eq!(st, "deleted_by_user");
        let _ = fs::remove_dir_all(&base);
    }

    #[test]
    fn delete_folders_refuses_shared_root() {
        use std::fs;
        let conn = mem();
        let base = std::env::temp_dir().join(format!("sift_share_{}", std::process::id()));
        let _ = fs::remove_dir_all(&base);
        let mount = base.join("DRV");
        let downloads = mount.join("Downloads");
        fs::create_dir_all(&downloads).unwrap();
        fs::write(downloads.join("loose.mkv"), vec![0u8; 10]).unwrap();
        let mount_s = mount.to_string_lossy().to_string();
        conn.execute("INSERT INTO volume(id,fs_label,fs_type,is_online,is_removable,first_seen_at,last_seen_at,current_mount_point) VALUES (1,'V','NTFS',1,0,0,0,?1)", params![mount_s]).unwrap();

        // Engine must refuse to delete "Downloads" even if asked directly.
        let none: Vec<String> = vec![];
        let out = delete_folders(&conn, &[downloads.to_string_lossy().to_string()], Mode::Permanent, &none).unwrap();
        assert_eq!(out.removed, 0);
        assert_eq!(out.failed, 1);
        assert!(downloads.exists(), "Downloads must NOT be deleted");
        assert!(out.failures[0].reason.contains("safety gate"), "got: {}", out.failures[0].reason);
        let _ = fs::remove_dir_all(&base);
    }
}
