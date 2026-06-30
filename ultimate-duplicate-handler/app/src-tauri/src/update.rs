//! Auto-update surface — PORTABLE-AWARE.
//!
//! Sift ships in two shapes from one codebase (see `scripts/build-portable.ps1` vs
//! `scripts/build-installer.ps1`):
//!   * a **portable** `Sift.exe` whose index lives in `Sift-Data\` *beside the EXE*, and
//!   * a **signed installer** (NSIS/MSI) that installs into Program Files.
//!
//! In-place auto-update (the `tauri-plugin-updater` flow) must apply ONLY to the installed
//! channel. A portable folder is the user's property — silently overwriting `Sift.exe` next
//! to their `Sift-Data\` could orphan or clobber the index that travels with it. So this
//! module exposes the channel/version facts to the UI and a hard portable guard; the actual
//! network download+swap is driven from the frontend via `@tauri-apps/plugin-updater`'s
//! `check()`/`downloadAndInstall()`, which the UI only invokes when `updates_enabled` is true.
//!
//! This mirrors the portable detection already used by `commands::storage_info` and
//! `paths::data_dir` — there is exactly one source of truth for "are we portable".

use serde::{Deserialize, Serialize};
use sift_core::paths;

/// What the Settings "Updates" card needs to render: the running version, whether we are the
/// portable build (auto-update disabled) or the installed build (auto-update available), and a
/// human-readable line explaining the state. Mirrored 1:1 in `src/lib/contract.ts`.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateStatus {
    /// The version baked into the binary at build time (Cargo package version).
    pub current_version: String,
    /// True when the data dir resolves to `<exe_dir>\Sift-Data` — i.e. the portable build.
    pub portable: bool,
    /// True only for the installed channel; the UI gates the network check on this.
    pub updates_enabled: bool,
    /// The configured release feed (informational; the JS plugin reads the same value from
    /// `tauri.conf.json`). `None` when not applicable (portable).
    pub feed_url: Option<String>,
    /// Guidance for the user, e.g. why a portable build won't self-update.
    pub message: String,
}

/// Default updater feed. Kept in sync with `tauri.conf.json -> plugins.updater.endpoints[0]`
/// so the Settings card can show *where* updates come from without re-reading the config.
const UPDATE_FEED: &str = "https://releases.sift.app/latest.json";

/// Resolve the update channel facts without touching the network.
///
/// Portable detection reuses the exact rule from `paths.rs`: the build is portable iff the
/// resolved `data_dir()` lives directly under the executable's own directory (`Sift-Data\`).
/// This is the same predicate `commands::storage_info` surfaces as the "PORTABLE" chip, so the
/// Updates card and the Portability card can never disagree.
pub fn compute_status(current_version: String) -> UpdateStatus {
    let data_dir = paths::data_dir();
    let portable = std::env::current_exe()
        .ok()
        .and_then(|e| e.parent().map(|p| data_dir.starts_with(p)))
        .unwrap_or(false);

    if portable {
        UpdateStatus {
            current_version,
            portable: true,
            updates_enabled: false,
            feed_url: None,
            message: "Portable build — automatic in-place updates are disabled so your \
                      Sift-Data folder is never overwritten. Download a newer portable build \
                      and copy your existing Sift-Data folder beside the new Singula.exe."
                .to_string(),
        }
    } else {
        UpdateStatus {
            current_version,
            portable: false,
            updates_enabled: true,
            feed_url: Some(UPDATE_FEED.to_string()),
            message: "Installed build — use \u{201c}Check now\u{201d} to look for a newer signed \
                      release. Updates are verified against Sift\u{2019}s release signing key \
                      before they are applied."
                .to_string(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn status_reports_running_version() {
        let s = compute_status("9.9.9".to_string());
        assert_eq!(s.current_version, "9.9.9");
    }

    #[test]
    fn portable_and_installed_are_mutually_exclusive_flags() {
        // Whatever the test environment resolves to, `updates_enabled` must be the inverse of
        // `portable` — the UI relies on exactly one of them being true.
        let s = compute_status("1.0.0".to_string());
        assert_eq!(s.updates_enabled, !s.portable);
        // A portable build must never advertise a feed URL (nothing to update in place).
        if s.portable {
            assert!(s.feed_url.is_none());
        } else {
            assert!(s.feed_url.is_some());
        }
        assert!(!s.message.is_empty());
    }
}
