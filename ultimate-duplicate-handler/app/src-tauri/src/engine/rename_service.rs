//! Rename-to-mark service — a NON-DESTRUCTIVE alternative to deletion. Appends a postfix
//! (or prepends a prefix) to selected files' names, e.g. "tobedeleted", so the user can
//! flag duplicates for their own later cleanup without removing anything. Each rename is
//! audited (reversible) and the index is updated in place (same content, same hash).

use crate::db::repo;
use crate::engine::pathkey;
use crate::model::{DeletionFailure, DeletionOutcome};
use rusqlite::{params, Connection, OptionalExtension, Result as SqlResult};

#[derive(Clone, Copy)]
pub enum Position {
    Prefix,
    Suffix,
}

struct Target {
    online: bool,
    present: bool,
    mount: Option<String>,
    path_raw: String,
    file_name: String,
}

/// Rename each file by adding `affix` as a prefix or suffix. Returns an honest outcome
/// distinguishing partial from full success (lesson PARTIAL-FAIL-001).
pub fn mark_files(
    conn: &Connection,
    file_ids: &[i64],
    affix: &str,
    position: Position,
) -> Result<DeletionOutcome, String> {
    let affix = affix.trim();
    if affix.is_empty() {
        return Err("the marker text is empty".into());
    }
    if affix.contains(['\\', '/', ':', '*', '?', '"', '<', '>', '|']) {
        return Err("the marker contains characters not allowed in file names".into());
    }

    let mut renamed = 0u64;
    let mut failed = 0u64;
    let mut failures = Vec::new();

    for &file_id in file_ids {
        let t = match load_target(conn, file_id) {
            Ok(Some(t)) => t,
            _ => {
                failed += 1;
                failures.push(DeletionFailure { file_id, path_raw: String::new(), reason: "file not found in index".into() });
                continue;
            }
        };
        if !t.present || !t.online || t.mount.is_none() {
            failed += 1;
            failures.push(DeletionFailure { file_id, path_raw: t.path_raw.clone(), reason: "file is not present on an online drive".into() });
            continue;
        }
        let mount = t.mount.as_deref().unwrap_or("");
        let new_name = new_file_name(&t.file_name, affix, position);
        let parent = parent_rel(&t.path_raw);
        let new_rel = if parent.is_empty() { new_name.clone() } else { format!("{parent}\\{new_name}") };
        let old_abs = repo::compose_abs_path(mount, &t.path_raw);
        let new_abs = repo::compose_abs_path(mount, &new_rel);

        // Audit intent BEFORE acting (lesson ORDER-001); rename is reversible.
        let audit_id = repo::audit_intent(conn, "rename", Some(file_id), None, None, Some(&old_abs), true, Some(&new_abs))
            .map_err(|e| format!("audit: {e}"))?;

        match do_rename(&old_abs, &new_abs) {
            Ok(()) => {
                let new_key = pathkey::make_key(&new_rel);
                let ext = pathkey::extension_of(&new_name);
                let _ = conn.execute(
                    "UPDATE file SET path_raw=?2, path_key=?3, file_name=?4, ext=?5, last_seen_at=?6 WHERE id=?1",
                    params![file_id, new_rel, new_key, new_name, ext, repo::now()],
                );
                let _ = repo::audit_outcome(conn, audit_id, "success");
                renamed += 1;
            }
            Err(reason) => {
                let _ = repo::audit_outcome(conn, audit_id, "failed");
                failed += 1;
                failures.push(DeletionFailure { file_id, path_raw: t.path_raw.clone(), reason });
            }
        }
    }

    let state = if failed == 0 { "success" } else if renamed > 0 { "partial" } else { "failed" };
    Ok(DeletionOutcome { state: state.into(), removed: renamed, failed, failures, reclaimed_bytes: 0 })
}

/// Suffix is inserted BEFORE the extension (keeps the file type & still findable);
/// prefix is prepended. With no extension, the affix is simply appended.
fn new_file_name(name: &str, affix: &str, pos: Position) -> String {
    match pos {
        Position::Prefix => format!("{affix}{name}"),
        Position::Suffix => match name.rfind('.') {
            Some(i) if i > 0 => format!("{}{}{}", &name[..i], affix, &name[i..]),
            _ => format!("{name}{affix}"),
        },
    }
}

fn parent_rel(path_raw: &str) -> String {
    match path_raw.rfind(['\\', '/']) {
        Some(i) => path_raw[..i].to_string(),
        None => String::new(),
    }
}

fn load_target(conn: &Connection, file_id: i64) -> SqlResult<Option<Target>> {
    conn.query_row(
        "SELECT v.is_online AS online, v.current_mount_point AS mount, f.path_raw AS path, \
                f.file_name AS name, (f.status='present') AS present \
         FROM file f JOIN volume v ON v.id=f.volume_id WHERE f.id=?1",
        params![file_id],
        |r| {
            Ok(Target {
                online: r.get::<_, i64>("online")? != 0,
                present: r.get::<_, i64>("present")? != 0,
                mount: r.get("mount")?,
                path_raw: r.get("path")?,
                file_name: r.get("name")?,
            })
        },
    )
    .optional()
}

#[cfg(windows)]
fn native(p: &str) -> String {
    pathkey::to_verbatim(p)
}
#[cfg(not(windows))]
fn native(p: &str) -> String {
    p.to_string()
}

fn do_rename(old: &str, new: &str) -> Result<(), String> {
    // Never overwrite an existing file (std::fs::rename overwrites on Unix but errors on
    // Windows; we check explicitly so behavior is the same and safe everywhere).
    if std::path::Path::new(&native(new)).exists() {
        return Err("a file with that marked name already exists".into());
    }
    std::fs::rename(native(old), native(new)).map_err(|e| e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn suffix_keeps_extension() {
        assert_eq!(new_file_name("movie.mp4", "_tobedeleted", Position::Suffix), "movie_tobedeleted.mp4");
        assert_eq!(new_file_name("archive.tar.gz", "_x", Position::Suffix), "archive.tar_x.gz");
        assert_eq!(new_file_name("README", "_x", Position::Suffix), "README_x");
        assert_eq!(new_file_name(".gitignore", "_x", Position::Suffix), ".gitignore_x");
    }
    #[test]
    fn prefix_prepends() {
        assert_eq!(new_file_name("movie.mp4", "DEL_", Position::Prefix), "DEL_movie.mp4");
    }
    #[test]
    fn parent_split() {
        assert_eq!(parent_rel("Secure\\sub\\f.mp4"), "Secure\\sub");
        assert_eq!(parent_rel("f.mp4"), "");
    }
}
