//! Filesystem enumeration (Stage 1 of the scan: metadata harvest).
//!
//! Design goals (CODING_RULES §5/§7): tolerate long/Unicode paths, never recurse
//! through reparse points by default, surface per-entry errors instead of aborting
//! the whole walk, and stream entries to the caller so memory stays flat over
//! millions of files.
//!
//! This is the portable directory-walk path. A faster NTFS-specific enumerator
//! (reading the MFT / USN journal, Everything-style) is a separate accelerated
//! backend selected at runtime when the volume is NTFS and the process is elevated;
//! it produces the same `Entry` stream so the rest of the pipeline is unchanged.
//! That accelerated backend is described in ARCHITECTURE.md and is a Phase-2 module.

use std::fs::Metadata;
use std::path::{Path, PathBuf};

/// One enumerated filesystem entry handed to the indexer.
#[derive(Debug, Clone)]
pub struct Entry {
    pub path: PathBuf,
    pub size: u64,
    pub modified: Option<i64>, // unix seconds
    pub created: Option<i64>,
    pub is_hidden: bool,
    pub is_system: bool,
    pub is_reparse: bool,
}

/// A problem with a single entry. Collected, not fatal (fail-loud-not-abort — §2).
#[derive(Debug, Clone)]
pub struct WalkError {
    pub path: String,
    pub message: String,
}

/// Folder-traversal lifecycle phase, reported via `on_dir` for completion visibility.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DirPhase {
    Entered,
    Completed,
    Skipped, // reparse point not followed
    Failed,  // could not read the directory
}

/// A folder-traversal event. `file_count` is the directory's DIRECT file count, known
/// only at `Completed`.
#[derive(Debug, Clone)]
pub struct DirEvent {
    pub path: PathBuf,
    pub depth: usize,
    pub phase: DirPhase,
    pub file_count: usize,
}

/// Callback-driven walk. `on_entry` receives each file; `on_error` receives per-path
/// failures; `on_dir` receives folder lifecycle events (for the completion tree).
/// Returning `false` from `should_continue` cancels promptly (CLEANUP-001).
pub fn walk<FEntry, FErr, FDir, FCancel>(
    root: &Path,
    follow_reparse: bool,
    mut on_entry: FEntry,
    mut on_error: FErr,
    mut on_dir: FDir,
    mut should_continue: FCancel,
) where
    FEntry: FnMut(Entry),
    FErr: FnMut(WalkError),
    FDir: FnMut(DirEvent),
    FCancel: FnMut() -> bool,
{
    // Explicit stack of (dir, depth) — avoids recursion depth limits on deep trees.
    let mut stack: Vec<(PathBuf, usize)> = vec![(root.to_path_buf(), 0)];

    while let Some((dir, depth)) = stack.pop() {
        if !should_continue() {
            return;
        }
        on_dir(DirEvent { path: dir.clone(), depth, phase: DirPhase::Entered, file_count: 0 });

        let read = match std::fs::read_dir(&dir) {
            Ok(rd) => rd,
            Err(e) => {
                on_error(WalkError {
                    path: dir.display().to_string(),
                    message: format!("read_dir: {e}"),
                });
                on_dir(DirEvent { path: dir, depth, phase: DirPhase::Failed, file_count: 0 });
                continue;
            }
        };

        let mut file_count = 0usize;
        for (i, dirent) in read.enumerate() {
            // Honor cancellation even inside one huge directory (every 2048 entries).
            if i % 2048 == 0 && !should_continue() {
                return;
            }
            let dirent = match dirent {
                Ok(d) => d,
                Err(e) => {
                    on_error(WalkError {
                        path: dir.display().to_string(),
                        message: format!("dir entry: {e}"),
                    });
                    continue;
                }
            };
            let path = dirent.path();
            // Use symlink_metadata so a reparse point reports as itself, not its target.
            let meta = match std::fs::symlink_metadata(&path) {
                Ok(m) => m,
                Err(e) => {
                    on_error(WalkError {
                        path: path.display().to_string(),
                        message: format!("stat: {e}"),
                    });
                    continue;
                }
            };
            let is_reparse = is_reparse_point(&meta);

            if meta.is_dir() {
                if is_reparse && !follow_reparse {
                    // Record-but-don't-recurse: avoids cycles & double counting (§5).
                    on_dir(DirEvent { path, depth: depth + 1, phase: DirPhase::Skipped, file_count: 0 });
                    continue;
                }
                if is_system_junk_dir(&path) {
                    // The Recycle Bin / System Volume Information are never user data.
                    on_dir(DirEvent { path, depth: depth + 1, phase: DirPhase::Skipped, file_count: 0 });
                    continue;
                }
                stack.push((path, depth + 1));
            } else if meta.is_file() {
                // Cloud/NAS awareness (initiative #7): skip cloud-only "Files On-Demand"
                // placeholders (OneDrive/Dropbox/GDrive) unless the user opted to follow special
                // files. Their bytes are not on disk — hashing one would force a multi-gigabyte
                // download. Additive: it only ever SKIPS a cloud-only file, never a local one.
                if is_cloud_placeholder(&meta) && !follow_reparse {
                    continue;
                }
                file_count += 1;
                on_entry(Entry {
                    size: meta.len(),
                    modified: mtime_secs(&meta),
                    created: ctime_secs(&meta),
                    is_hidden: is_hidden(&meta),
                    is_system: is_system(&meta),
                    is_reparse,
                    path,
                });
            }
        }
        on_dir(DirEvent { path: dir, depth, phase: DirPhase::Completed, file_count });
    }
}

/// System areas that are never user data and must never be indexed/deduped: the per-volume
/// Recycle Bin and the System Volume Information store (restore points, etc.). Matched by
/// directory name, case-insensitive.
fn is_system_junk_dir(path: &Path) -> bool {
    matches!(
        path.file_name()
            .and_then(|n| n.to_str())
            .map(|n| n.to_ascii_lowercase())
            .as_deref(),
        Some("$recycle.bin") | Some("system volume information")
    )
}

/// Build an `Entry` by stat-ing a single path (used by the MFT-accelerated enumerator,
/// which has paths but not metadata). Errors if the path is not a regular file.
pub fn entry_for(path: &Path) -> std::io::Result<Entry> {
    let meta = std::fs::symlink_metadata(path)?;
    if !meta.is_file() {
        return Err(std::io::Error::new(std::io::ErrorKind::Other, "not a regular file"));
    }
    Ok(Entry {
        size: meta.len(),
        modified: mtime_secs(&meta),
        created: ctime_secs(&meta),
        is_hidden: is_hidden(&meta),
        is_system: is_system(&meta),
        is_reparse: is_reparse_point(&meta),
        path: path.to_path_buf(),
    })
}

#[cfg(windows)]
fn is_reparse_point(meta: &Metadata) -> bool {
    use std::os::windows::fs::MetadataExt;
    const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x400;
    meta.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0
}
/// A cloud-only placeholder whose bytes live in OneDrive/Dropbox/GDrive, not on disk.
#[cfg(windows)]
fn is_cloud_placeholder(meta: &Metadata) -> bool {
    use std::os::windows::fs::MetadataExt;
    const FILE_ATTRIBUTE_OFFLINE: u32 = 0x1000;
    const FILE_ATTRIBUTE_RECALL_ON_OPEN: u32 = 0x40000;
    const FILE_ATTRIBUTE_RECALL_ON_DATA_ACCESS: u32 = 0x400000;
    meta.file_attributes()
        & (FILE_ATTRIBUTE_OFFLINE | FILE_ATTRIBUTE_RECALL_ON_OPEN | FILE_ATTRIBUTE_RECALL_ON_DATA_ACCESS)
        != 0
}
#[cfg(not(windows))]
fn is_cloud_placeholder(_meta: &Metadata) -> bool {
    false
}
#[cfg(windows)]
fn is_hidden(meta: &Metadata) -> bool {
    use std::os::windows::fs::MetadataExt;
    const FILE_ATTRIBUTE_HIDDEN: u32 = 0x2;
    meta.file_attributes() & FILE_ATTRIBUTE_HIDDEN != 0
}
#[cfg(windows)]
fn is_system(meta: &Metadata) -> bool {
    use std::os::windows::fs::MetadataExt;
    const FILE_ATTRIBUTE_SYSTEM: u32 = 0x4;
    meta.file_attributes() & FILE_ATTRIBUTE_SYSTEM != 0
}

#[cfg(not(windows))]
fn is_reparse_point(meta: &Metadata) -> bool {
    meta.file_type().is_symlink()
}
#[cfg(not(windows))]
fn is_hidden(_: &Metadata) -> bool {
    false
}
#[cfg(not(windows))]
fn is_system(_: &Metadata) -> bool {
    false
}

fn mtime_secs(meta: &Metadata) -> Option<i64> {
    meta.modified().ok().and_then(to_unix)
}
fn ctime_secs(meta: &Metadata) -> Option<i64> {
    meta.created().ok().and_then(to_unix)
}
fn to_unix(t: std::time::SystemTime) -> Option<i64> {
    t.duration_since(std::time::UNIX_EPOCH).ok().map(|d| d.as_secs() as i64)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn walk_finds_files_and_reports_count() {
        let base = std::env::temp_dir().join(format!("sift_walk_{}", std::process::id()));
        let _ = fs::remove_dir_all(&base);
        fs::create_dir_all(base.join("sub")).unwrap();
        fs::write(base.join("a.txt"), b"a").unwrap();
        fs::write(base.join("sub/b.txt"), b"bb").unwrap();

        let mut files = Vec::new();
        let mut errors = Vec::new();
        let mut completed_dirs = 0;
        walk(
            &base,
            false,
            |e| files.push(e),
            |er| errors.push(er),
            |d| {
                if d.phase == DirPhase::Completed {
                    completed_dirs += 1;
                }
            },
            || true,
        );

        assert_eq!(files.len(), 2, "should find both files across subdirs");
        assert!(errors.is_empty());
        assert_eq!(completed_dirs, 2, "base + sub both reported completed");
        let _ = fs::remove_dir_all(&base);
    }

    #[test]
    fn skips_system_junk_dirs() {
        let base = std::env::temp_dir().join(format!("sift_junk_{}", std::process::id()));
        let _ = fs::remove_dir_all(&base);
        fs::create_dir_all(base.join("$RECYCLE.BIN")).unwrap();
        fs::create_dir_all(base.join("System Volume Information")).unwrap();
        fs::write(base.join("$RECYCLE.BIN").join("r.txt"), b"r").unwrap();
        fs::write(base.join("System Volume Information").join("s.txt"), b"s").unwrap();
        fs::write(base.join("keep.txt"), b"k").unwrap();

        let mut files = Vec::new();
        walk(&base, false, |e| files.push(e.path), |_| {}, |_| {}, || true);
        assert_eq!(files.len(), 1, "only keep.txt — the junk dirs are skipped");
        assert!(files[0].ends_with("keep.txt"));
        let _ = fs::remove_dir_all(&base);
    }

    #[test]
    fn cancellation_stops_walk() {
        let base = std::env::temp_dir().join(format!("sift_cancel_{}", std::process::id()));
        let _ = fs::remove_dir_all(&base);
        fs::create_dir_all(&base).unwrap();
        fs::write(base.join("a.txt"), b"a").unwrap();

        let mut count = 0;
        walk(&base, false, |_| count += 1, |_| {}, |_| {}, || false); // cancel immediately
        assert_eq!(count, 0);
        let _ = fs::remove_dir_all(&base);
    }
}
