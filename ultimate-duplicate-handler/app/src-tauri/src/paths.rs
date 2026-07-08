//! Data-directory resolution — PORTABLE-FIRST.
//!
//! The whole point of a portable app: copy the folder to any Windows machine and it
//! just works, carrying its index with it. So we store data **next to the executable**
//! (in `Sift-Data\`) whenever that location is writable. We only fall back to
//! `%APPDATA%\Sift` when the exe sits in a read-only location (e.g. Program Files after
//! an installer), or a marker forces it.
//!
//! Precedence:
//!   1. `<exe_dir>\Sift-Data\`  if writable          (portable — the default)
//!   2. `%APPDATA%\Sift\`       if exe dir read-only  (installed mode)
//!   3. `%TEMP%\Sift\`          last resort (never silently lose the user's index path)

use std::path::{Path, PathBuf};

/// The directory that holds the index, quarantine, logs, exports. Created if missing.
pub fn data_dir() -> PathBuf {
    if let Some(dir) = portable_dir() {
        return dir;
    }
    // Installed mode: the per-user application-data root, resolved per-OS (Windows %APPDATA%,
    // macOS ~/Library/Application Support, Linux $XDG_DATA_HOME / ~/.local/share).
    if let Some(root) = os_data_root() {
        let d = root.join("Sift");
        if std::fs::create_dir_all(&d).is_ok() {
            return d;
        }
    }
    let d = std::env::temp_dir().join("Sift");
    let _ = std::fs::create_dir_all(&d);
    d
}

/// Per-user application-data root for the installed build, by OS. Cross-platform so the
/// engine resolves a sane index location on Windows, macOS and Linux alike.
fn os_data_root() -> Option<PathBuf> {
    #[cfg(target_os = "windows")]
    return std::env::var("APPDATA").ok().map(PathBuf::from);
    #[cfg(target_os = "macos")]
    return std::env::var("HOME")
        .ok()
        .map(|h| PathBuf::from(h).join("Library").join("Application Support"));
    #[cfg(all(unix, not(target_os = "macos")))]
    return std::env::var("XDG_DATA_HOME")
        .ok()
        .map(PathBuf::from)
        .or_else(|| std::env::var("HOME").ok().map(|h| PathBuf::from(h).join(".local").join("share")));
    #[allow(unreachable_code)]
    None
}

fn portable_dir() -> Option<PathBuf> {
    let exe = std::env::current_exe().ok()?;
    let dir = exe.parent()?.join("Sift-Data");
    std::fs::create_dir_all(&dir).ok()?;
    if is_writable(&dir) {
        Some(dir)
    } else {
        None
    }
}

/// A directory is usable for the portable index only if we can actually write to it.
fn is_writable(dir: &Path) -> bool {
    let probe = dir.join(".sift-write-test");
    match std::fs::write(&probe, b"ok") {
        Ok(()) => {
            let _ = std::fs::remove_file(&probe);
            true
        }
        Err(_) => false,
    }
}

pub fn index_db_path() -> PathBuf {
    data_dir().join("index.sqlite")
}

pub fn quarantine_dir() -> PathBuf {
    data_dir().join("quarantine")
}

pub fn exports_dir() -> PathBuf {
    let d = data_dir().join("exports");
    let _ = std::fs::create_dir_all(&d);
    d
}
