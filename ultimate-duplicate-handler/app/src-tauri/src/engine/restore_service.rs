//! Restore service — cross-session UNDO for the reversible actions (recycle / quarantine /
//! rename). Sift's job is removing the user's files, so one-click restore from the persistent
//! `audit_log` is the single biggest trust feature — it makes Sift the *safest* dedup tool.
//!
//! Reversibility per action (recorded at action time in `audit_log.reversible`):
//!   * recycle    → restore from the OS Recycle Bin (via the `trash` crate's os_limited API)
//!   * quarantine → move the file back from `Sift-Data/quarantine/` to its original path
//!   * rename     → rename the file back (new path is in `audit_log.detail_json`)
//!   * permanent  → NOT restorable (the bytes are gone)
//!
//! Every restore is itself audited (`action='restore'`) so the history stays complete and a
//! file can't be silently double-restored. Engine-authoritative + honest partial success.

use crate::db::repo;
use crate::engine::pathkey;
use crate::model::{DeletionFailure, DeletionOutcome, RestoreEntry};
use rusqlite::{params, Connection, OptionalExtension, Result as SqlResult};
use std::path::Path;

struct AuditRec {
    id: i64,
    action: String,
    reversible: bool,
    file_id: Option<i64>,
    content_id: Option<i64>,
    volume_id: Option<i64>,
    path_raw: Option<String>,    // original absolute path (or OLD path for a rename)
    detail_json: Option<String>, // NEW absolute path for a rename; else None
}

fn load_audit(conn: &Connection, audit_id: i64) -> SqlResult<Option<AuditRec>> {
    conn.query_row(
        "SELECT id, action, reversible, file_id, content_id, volume_id, path_raw, detail_json \
         FROM audit_log WHERE id=?1",
        params![audit_id],
        |r| {
            Ok(AuditRec {
                id: r.get("id")?,
                action: r.get("action")?,
                reversible: r.get::<_, i64>("reversible")? != 0,
                file_id: r.get("file_id")?,
                content_id: r.get("content_id")?,
                volume_id: r.get("volume_id")?,
                path_raw: r.get("path_raw")?,
                detail_json: r.get("detail_json")?,
            })
        },
    )
    .optional()
}

/// List the reversible actions, newest first, with a computed "still restorable?" verdict.
/// An entry is marked already-restored if a later `action='restore'` exists for its file.
pub fn list_restorable(conn: &Connection, limit: i64) -> Result<Vec<RestoreEntry>, String> {
    let mut stmt = conn
        .prepare(
            "SELECT a.id AS id, a.at AS at, a.action AS action, a.path_raw AS path, \
                    a.detail_json AS detail, a.reversible AS rev, a.file_id AS fid, \
                    (SELECT COUNT(*) FROM audit_log r WHERE r.action='restore' \
                       AND r.file_id=a.file_id AND r.at>=a.at) AS restored \
             FROM audit_log a \
             WHERE a.action IN ('recycle','quarantine','permanent','rename') \
               AND a.outcome='success' \
             ORDER BY a.at DESC, a.id DESC LIMIT ?1",
        )
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map(params![limit], |r| {
            let action: String = r.get("action")?;
            let path: Option<String> = r.get("path")?;
            let detail: Option<String> = r.get("detail")?;
            let reversible = r.get::<_, i64>("rev")? != 0;
            let already = r.get::<_, i64>("restored")? > 0;
            let display = match action.as_str() {
                "rename" => match (&path, &detail) {
                    (Some(o), Some(n)) => format!("{n}  →  {o}"),
                    _ => path.clone().unwrap_or_default(),
                },
                _ => path.clone().unwrap_or_default(),
            };
            let (restorable, note) = if already {
                (false, Some("already restored".to_string()))
            } else if !reversible {
                (false, Some("permanent — bytes are gone, cannot restore".to_string()))
            } else {
                (true, None)
            };
            Ok(RestoreEntry {
                audit_id: r.get("id")?,
                at: r.get("at")?,
                action,
                path: display,
                already_restored: already,
                restorable,
                note,
            })
        })
        .map_err(|e| e.to_string())?;
    rows.collect::<SqlResult<Vec<_>>>().map_err(|e| e.to_string())
}

/// Restore each audited action. Returns an honest outcome (`removed` = files restored).
pub fn restore(conn: &Connection, data_dir: &Path, audit_ids: &[i64]) -> Result<DeletionOutcome, String> {
    let mut restored = 0u64;
    let mut failed = 0u64;
    let mut failures = Vec::new();

    for &aid in audit_ids {
        let rec = match load_audit(conn, aid) {
            Ok(Some(r)) => r,
            _ => {
                failed += 1;
                failures.push(DeletionFailure { file_id: aid, path_raw: String::new(), reason: "audit entry not found".into() });
                continue;
            }
        };
        match restore_one(conn, data_dir, &rec) {
            Ok(()) => restored += 1,
            Err(reason) => {
                failed += 1;
                failures.push(DeletionFailure {
                    file_id: rec.file_id.unwrap_or(rec.id),
                    path_raw: rec.path_raw.clone().unwrap_or_default(),
                    reason,
                });
            }
        }
    }

    let state = if failed == 0 { "success" } else if restored > 0 { "partial" } else { "failed" };
    Ok(DeletionOutcome { state: state.into(), removed: restored, failed, failures, reclaimed_bytes: 0 })
}

fn restore_one(conn: &Connection, data_dir: &Path, rec: &AuditRec) -> Result<(), String> {
    if !rec.reversible {
        return Err("permanent deletion cannot be restored".into());
    }
    let orig = rec.path_raw.clone().ok_or("audit entry has no recorded path")?;

    match rec.action.as_str() {
        "rename" => {
            let new_abs = rec.detail_json.clone().ok_or("rename audit has no new-path record")?;
            if !exists(&new_abs) {
                return Err(format!("the renamed file no longer exists at {new_abs}"));
            }
            if exists(&orig) {
                return Err("a file already exists at the original name".into());
            }
            do_move(&new_abs, &orig)?;
            // Point the index row back at the original name.
            if let Some(fid) = rec.file_id {
                update_file_path(conn, fid, &orig)?;
            }
        }
        "quarantine" => {
            let name = Path::new(&orig).file_name().ok_or("no file name in path")?;
            let qfile = data_dir.join("quarantine").join(name);
            if !qfile.exists() {
                return Err("the quarantined file is no longer in Sift-Data/quarantine".into());
            }
            if exists(&orig) {
                return Err("a file already exists at the original location".into());
            }
            if let Some(parent) = Path::new(&orig).parent() {
                std::fs::create_dir_all(parent).map_err(|e| format!("recreate folder: {e}"))?;
            }
            do_move(&qfile.to_string_lossy(), &orig)?;
            mark_present(conn, rec)?;
        }
        "recycle" => {
            if exists(&orig) {
                return Err("a file already exists at the original location".into());
            }
            restore_from_recycle_bin(&orig)?;
            mark_present(conn, rec)?;
        }
        other => return Err(format!("action '{other}' is not restorable")),
    }

    // Audit the restore so it can't be silently double-applied and the history stays whole.
    if let Ok(id) = repo::audit_intent(
        conn,
        "restore",
        rec.file_id,
        rec.content_id,
        rec.volume_id,
        Some(&orig),
        false,
        Some(&rec.action),
    ) {
        let _ = repo::audit_outcome(conn, id, "success");
    }
    Ok(())
}

/// Flip the file row back to 'present' and refresh the content counters/cluster.
fn mark_present(conn: &Connection, rec: &AuditRec) -> Result<(), String> {
    if let Some(fid) = rec.file_id {
        conn.execute(
            "UPDATE file SET status='present', removed_at=NULL, last_seen_at=?2 WHERE id=?1",
            params![fid, repo::now()],
        )
        .map_err(|e| e.to_string())?;
        if let Some(cid) = rec.content_id {
            let _ = repo::recompute_counters(conn, cid);
            let _ = repo::refresh_cluster(conn, cid);
        }
    }
    Ok(())
}

/// After an un-rename, set the index row's path fields back to the original.
fn update_file_path(conn: &Connection, file_id: i64, orig_abs: &str) -> Result<(), String> {
    let mount: Option<String> = conn
        .query_row(
            "SELECT v.current_mount_point AS mp FROM file f JOIN volume v ON v.id=f.volume_id WHERE f.id=?1",
            params![file_id],
            |r| r.get("mp"),
        )
        .map_err(|e| e.to_string())?;
    let rel = strip_mount(orig_abs, mount.as_deref());
    let name = Path::new(orig_abs)
        .file_name()
        .map(|s| s.to_string_lossy().to_string())
        .unwrap_or_else(|| rel.clone());
    let key = pathkey::make_key(&rel);
    let ext = pathkey::extension_of(&name);
    conn.execute(
        "UPDATE file SET path_raw=?2, path_key=?3, file_name=?4, ext=?5, status='present', \
         removed_at=NULL, last_seen_at=?6 WHERE id=?1",
        params![file_id, rel, key, name, ext, repo::now()],
    )
    .map_err(|e| e.to_string())?;
    Ok(())
}

/// Restore a file from the OS Recycle Bin by matching its original absolute path.
fn restore_from_recycle_bin(orig_abs: &str) -> Result<(), String> {
    #[cfg(any(windows, all(unix, not(target_os = "macos"))))]
    {
        use trash::os_limited;
        let items = os_limited::list().map_err(|e| format!("read Recycle Bin: {e}"))?;
        let mut matches: Vec<_> = items
            .into_iter()
            .filter(|it| {
                let full = it.original_parent.join(&it.name);
                full.to_string_lossy().to_lowercase() == orig_abs.to_lowercase()
            })
            .collect();
        if matches.is_empty() {
            return Err("not found in the Recycle Bin (it may have been emptied or already restored)".into());
        }
        // Restore the most recently deleted match.
        matches.sort_by_key(|it| it.time_deleted);
        let chosen = matches.pop().ok_or("no recycle item")?;
        os_limited::restore_all([chosen]).map_err(|e| format!("restore from Recycle Bin: {e}"))?;
        Ok(())
    }
    #[cfg(not(any(windows, all(unix, not(target_os = "macos")))))]
    {
        let _ = orig_abs;
        Err("Recycle Bin restore is not supported on this platform — restore manually".into())
    }
}

fn exists(abs: &str) -> bool {
    Path::new(&native(abs)).exists()
}

fn do_move(from: &str, to: &str) -> Result<(), String> {
    match std::fs::rename(native(from), native(to)) {
        Ok(()) => Ok(()),
        Err(_) => {
            // Cross-volume fallback: copy then delete the source.
            std::fs::copy(native(from), native(to)).map_err(|e| e.to_string())?;
            std::fs::remove_file(native(from)).map_err(|e| e.to_string())
        }
    }
}

fn strip_mount(abs: &str, mount: Option<&str>) -> String {
    if let Some(m) = mount {
        let m_trim = m.trim_end_matches(['\\', '/']);
        if abs.len() >= m_trim.len() && abs[..m_trim.len()].eq_ignore_ascii_case(m_trim) {
            return abs[m_trim.len()..].trim_start_matches(['\\', '/']).to_string();
        }
    }
    abs.trim_start_matches(['\\', '/']).to_string()
}

#[cfg(windows)]
fn native(abs: &str) -> String {
    pathkey::to_verbatim(abs)
}
#[cfg(not(windows))]
fn native(abs: &str) -> String {
    abs.to_string()
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
    fn quarantine_restore_round_trips_and_marks_present() {
        let conn = mem();
        let tmp = std::env::temp_dir().join(format!("sift_restore_{}", std::process::id()));
        let qdir = tmp.join("quarantine");
        std::fs::create_dir_all(&qdir).unwrap();
        // Original file location (in the temp root, acting as the "drive").
        let orig = tmp.join("movie.mp4");
        std::fs::write(&orig, b"hello").unwrap();
        let orig_abs = orig.to_string_lossy().to_string();

        // Simulate a quarantine: move the file aside + seed the index + audit row.
        let qfile = qdir.join("movie.mp4");
        std::fs::rename(&orig, &qfile).unwrap();
        conn.execute("INSERT INTO volume(id,fs_label,fs_type,is_online,is_removable,first_seen_at,last_seen_at,current_mount_point) VALUES (1,'V','NTFS',1,0,0,0,?1)", params![tmp.to_string_lossy().to_string()]).unwrap();
        conn.execute("INSERT INTO file(id,volume_id,path_raw,path_key,file_name,size_bytes,status,hash_state,first_seen_at,last_seen_at) VALUES (1,1,'movie.mp4','movie.mp4','movie.mp4',5,'deleted_by_user','none',0,0)", []).unwrap();
        let aid = repo::audit_intent(&conn, "quarantine", Some(1), None, Some(1), Some(&orig_abs), true, None).unwrap();
        repo::audit_outcome(&conn, aid, "success").unwrap();

        // Listing shows it as restorable.
        let list = list_restorable(&conn, 50).unwrap();
        assert!(list.iter().any(|e| e.audit_id == aid && e.restorable));

        // Restore it.
        let out = restore(&conn, &tmp, &[aid]).unwrap();
        assert_eq!(out.state, "success");
        assert_eq!(out.removed, 1);
        assert!(orig.exists(), "file must be back at its original location");
        let st: String = conn.query_row("SELECT status AS s FROM file WHERE id=1", [], |r| r.get("s")).unwrap();
        assert_eq!(st, "present");

        // Now it must show as already-restored (no double-restore).
        let list2 = list_restorable(&conn, 50).unwrap();
        assert!(list2.iter().any(|e| e.audit_id == aid && e.already_restored && !e.restorable));

        let _ = std::fs::remove_dir_all(&tmp);
    }
}
