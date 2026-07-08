//! Deletion safety & historical-copy awareness.
//!
//! This module enforces the data-loss invariants from CODING_RULES §1 in the ENGINE,
//! not the UI — the UI is only a preview (lesson: server/engine-authoritative, client
//! is preview-only). No deletion plan can violate these, regardless of what the UI sent.
//!
//! Central invariant — "keep at least one ONLINE copy":
//!   A plan must never remove the last currently-online copy of a content hash unless
//!   the user has explicitly acknowledged that only offline/historical copies remain.
//!   Removing a file when its only other copies live on disconnected drives is the
//!   classic way users destroy their last accessible copy — we refuse it by default.

use std::collections::HashMap;

/// A copy of some content, as seen by the planner. Mirrors `file` joined to `volume`.
#[derive(Debug, Clone)]
pub struct Copy {
    pub file_id: i64,
    pub content_id: i64,
    pub volume_online: bool,
    pub status_present: bool, // file.status == 'present'
    pub path_raw: String,
}

/// A user decision for one copy within a plan.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Decision {
    Keep,
    Remove,
}

/// Why a plan item was rejected — surfaced verbatim to the user (fail loud — §2).
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SafetyViolation {
    /// Removing these would leave zero online copies of the content.
    WouldRemoveLastOnlineCopy { content_id: i64 },
    /// Removing these would leave zero present copies anywhere (total annihilation).
    WouldRemoveLastKnownCopy { content_id: i64 },
}

/// Result of validating a plan. `ok` is true only when there are no violations the
/// user has not explicitly acknowledged.
#[derive(Debug)]
pub struct SafetyReport {
    pub violations: Vec<SafetyViolation>,
}

impl SafetyReport {
    pub fn ok(&self) -> bool {
        self.violations.is_empty()
    }
}

/// Validate a set of (copy, decision) pairs grouped by content. `acknowledged_online`
/// holds content_ids for which the user explicitly accepted "only offline copies will
/// remain" — those downgrade `WouldRemoveLastOnlineCopy` from a hard block to allowed,
/// but `WouldRemoveLastKnownCopy` is NEVER waivable here (total data loss).
pub fn validate_plan(
    items: &[(Copy, Decision)],
    acknowledged_online: &[i64],
) -> SafetyReport {
    let ack: std::collections::HashSet<i64> = acknowledged_online.iter().copied().collect();

    // Group copies by content so each cluster is reasoned about as a whole.
    let mut by_content: HashMap<i64, Vec<&(Copy, Decision)>> = HashMap::new();
    for item in items {
        by_content.entry(item.0.content_id).or_default().push(item);
    }

    let mut violations = Vec::new();
    for (content_id, group) in by_content {
        // Count what SURVIVES the plan.
        let mut surviving_online = 0;
        let mut surviving_present = 0;
        for (copy, decision) in &group {
            let survives = *decision == Decision::Keep && copy.status_present;
            if survives {
                surviving_present += 1;
                if copy.volume_online {
                    surviving_online += 1;
                }
            }
        }

        if surviving_present == 0 {
            // Hard, non-waivable: nothing of this content would remain anywhere.
            violations.push(SafetyViolation::WouldRemoveLastKnownCopy { content_id });
        } else if surviving_online == 0 && !ack.contains(&content_id) {
            // Present copies remain only on offline drives, and the user did not
            // acknowledge that. Block by default.
            violations.push(SafetyViolation::WouldRemoveLastOnlineCopy { content_id });
        }
    }

    SafetyReport { violations }
}

/// Historical presence summary for the "does this still exist elsewhere?" panel.
/// Computed from the persistent index across ALL volumes, including offline ones.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PresenceSummary {
    pub online_copies: u32,        // present on a currently-mounted volume
    pub offline_copies: u32,       // present, but on a disconnected known volume
    pub deleted_copies: u32,       // previously seen, now removed
    pub last_online_volume: Option<i64>,
}

impl PresenceSummary {
    /// A plain-language safety verdict for the UI badge.
    pub fn verdict(&self) -> &'static str {
        // The final arm is an unguarded catch-all: match guards do NOT contribute to
        // exhaustiveness, so a literal `(0, 0)` here would be a non-exhaustive-match
        // compile error (E0004). Only (0, 0) can actually reach it at runtime.
        match (self.online_copies, self.offline_copies) {
            (n, _) if n >= 2 => "safe — multiple online copies exist",
            (1, _) => "caution — this is the only ONLINE copy",
            (0, m) if m >= 1 => "warning — remaining copies are on OFFLINE drives only",
            _ => "danger — no other known copy survives",
        }
    }
}

// ===========================================================================
// PROTECTED FOLDERS (initiative #6) — locations Sift must NEVER delete from.
// A pure, additive guard: it can only REFUSE a deletion, never cause one.
// ===========================================================================

/// Path fragments that are ALWAYS protected (system locations no one should dedup). Matched
/// case-insensitively as a substring of the absolute path.
// NOTE: the per-volume Recycle Bin ($Recycle.Bin) is intentionally NOT here. It holds
// already-deleted files, so deleting a duplicate that lives there is harmless — and the
// scanner now skips it entirely (scan_service::passes_filters), so it shouldn't be indexed
// at all. These remaining entries are genuinely OS-critical: deleting from them breaks Windows.
pub const DEFAULT_PROTECTED: &[&str] = &[
    "\\windows\\",
    "\\program files",
    "\\programdata\\",
    "\\system volume information\\",
    "\\appdata\\local\\microsoft\\",
];

/// If `abs` lies within a protected location, return `(marker, is_builtin)` — `is_builtin`
/// distinguishes a permanent system rule (DEFAULT_PROTECTED) from a user-added folder (which
/// can be removed in Settings). Pure + case-insensitive.
pub fn matched_protected_kind(abs: &str, extra: &[String]) -> Option<(String, bool)> {
    let lower = abs.to_lowercase();
    for p in DEFAULT_PROTECTED {
        if lower.contains(p) {
            return Some((p.trim_matches('\\').to_string(), true));
        }
    }
    for p in extra {
        let pl = p.trim().to_lowercase();
        if !pl.is_empty() && lower.contains(&pl) {
            return Some((p.trim().to_string(), false));
        }
    }
    None
}

/// Back-compat: the matched marker only (built-in or user-added). Pure + case-insensitive.
pub fn matched_protected(abs: &str, extra: &[String]) -> Option<String> {
    matched_protected_kind(abs, extra).map(|(m, _)| m)
}

/// Load the user-added protected paths from `Sift-Data/protected.json` (empty if none/invalid).
pub fn load_protected_paths(data_dir: &std::path::Path) -> Vec<String> {
    std::fs::read_to_string(data_dir.join("protected.json"))
        .ok()
        .and_then(|s| serde_json::from_str::<Vec<String>>(&s).ok())
        .unwrap_or_default()
}

/// Persist the user-added protected paths.
pub fn save_protected_paths(data_dir: &std::path::Path, paths: &[String]) -> Result<(), String> {
    let json = serde_json::to_string_pretty(paths).map_err(|e| e.to_string())?;
    std::fs::write(data_dir.join("protected.json"), json).map_err(|e| e.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn protects_system_paths_and_user_additions() {
        assert!(matched_protected("C:\\Windows\\System32\\x.dll", &[]).is_some());
        assert!(matched_protected("D:\\Program Files\\App\\a.exe", &[]).is_some());
        assert!(matched_protected("E:\\Photos\\a.jpg", &[]).is_none());
        // user-added root
        let extra = vec!["E:\\Keep".to_string()];
        assert!(matched_protected("E:\\Keep\\important.psd", &extra).is_some());
        assert!(matched_protected("E:\\Other\\x.psd", &extra).is_none());
        // The Recycle Bin is NO LONGER a built-in protection (already-deleted files; the scanner
        // skips it now), so its indexed duplicates can be cleaned.
        assert!(matched_protected("D:\\$Recycle.Bin\\S-1-5-21\\$RABC.txt", &[]).is_none());
        // Built-in vs user-added is distinguishable so the refusal message can be honest.
        assert_eq!(matched_protected_kind("C:\\Windows\\x.dll", &[]).map(|(_, b)| b), Some(true));
        assert_eq!(matched_protected_kind("E:\\Keep\\x.psd", &extra).map(|(_, b)| b), Some(false));
    }

    fn copy(id: i64, content: i64, online: bool, present: bool) -> Copy {
        Copy {
            file_id: id,
            content_id: content,
            volume_online: online,
            status_present: present,
            path_raw: format!("X:\\f{id}"),
        }
    }

    #[test]
    fn keeps_one_online_copy_is_allowed() {
        let items = vec![
            (copy(1, 100, true, true), Decision::Keep),
            (copy(2, 100, true, true), Decision::Remove),
        ];
        assert!(validate_plan(&items, &[]).ok());
    }

    #[test]
    fn removing_last_online_copy_is_blocked_by_default() {
        // One online copy (remove) + one offline copy (keep) => no online survivor.
        let items = vec![
            (copy(1, 100, true, true), Decision::Remove),
            (copy(2, 100, false, true), Decision::Keep),
        ];
        let report = validate_plan(&items, &[]);
        assert!(!report.ok());
        assert_eq!(
            report.violations,
            vec![SafetyViolation::WouldRemoveLastOnlineCopy { content_id: 100 }]
        );
    }

    #[test]
    fn acknowledged_offline_only_is_allowed() {
        let items = vec![
            (copy(1, 100, true, true), Decision::Remove),
            (copy(2, 100, false, true), Decision::Keep),
        ];
        assert!(validate_plan(&items, &[100]).ok());
    }

    #[test]
    fn removing_every_copy_is_never_allowed_even_with_ack() {
        let items = vec![
            (copy(1, 100, true, true), Decision::Remove),
            (copy(2, 100, false, true), Decision::Remove),
        ];
        let report = validate_plan(&items, &[100]); // ack must NOT waive this
        assert_eq!(
            report.violations,
            vec![SafetyViolation::WouldRemoveLastKnownCopy { content_id: 100 }]
        );
    }

    #[test]
    fn presence_verdicts() {
        let mk = |on, off| PresenceSummary {
            online_copies: on,
            offline_copies: off,
            deleted_copies: 0,
            last_online_volume: None,
        };
        assert!(mk(3, 0).verdict().starts_with("safe"));
        assert!(mk(1, 5).verdict().starts_with("caution"));
        assert!(mk(0, 2).verdict().starts_with("warning"));
        assert!(mk(0, 0).verdict().starts_with("danger"));
    }
}
