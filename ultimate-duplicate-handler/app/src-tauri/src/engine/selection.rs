//! Smart-selection rule engine. Given duplicate clusters and an ordered list of rules,
//! it picks exactly ONE keeper per cluster and marks the rest for removal — a *preview*
//! the user reviews before committing (engine-authoritative decision, UI is preview).
//!
//! Invariants (CODING_RULES §1): never marks a non-present file for removal; always keeps
//! at least one present copy per cluster (the chosen keeper). The deletion safety engine
//! (`safety.rs`) is still the final gate at commit time — this only proposes.

use serde::{Deserialize, Serialize};
use std::cmp::Ordering;
use std::collections::HashMap;

/// Rules are applied in priority order: the first rule that distinguishes two candidates
/// decides which is "better" (and therefore kept).
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(tag = "kind", content = "value", rename_all = "camelCase")]
pub enum SelectionRule {
    KeepNewest,
    KeepOldest,
    KeepShortestPath,
    KeepLongestPath,
    PreferOnline,
    KeepOnVolume(i64),
    KeepInPathContaining(String),
}

/// One candidate copy within a cluster.
#[derive(Debug, Clone)]
pub struct Candidate {
    pub file_id: i64,
    pub content_id: i64,
    pub volume_id: i64,
    pub online: bool,
    pub present: bool,
    pub path_raw: String,
    pub modified_at: Option<i64>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SelectionDecision {
    pub file_id: i64,
    pub keep: bool,
    pub reason: String,
}

/// Apply the rules across all candidates, grouped by content. Returns a decision per
/// file. A content with zero present copies yields all-keep (nothing to do).
pub fn apply(candidates: &[Candidate], rules: &[SelectionRule]) -> Vec<SelectionDecision> {
    let mut by_content: HashMap<i64, Vec<&Candidate>> = HashMap::new();
    for c in candidates {
        by_content.entry(c.content_id).or_default().push(c);
    }

    let mut out = Vec::with_capacity(candidates.len());
    for (_content, group) in by_content {
        let present: Vec<&&Candidate> = group.iter().filter(|c| c.present).collect();
        // Non-present copies are always kept (we never "remove" something already gone).
        for c in group.iter().filter(|c| !c.present) {
            out.push(SelectionDecision { file_id: c.file_id, keep: true, reason: "not present".into() });
        }
        if present.is_empty() {
            continue;
        }
        // Pick the keeper = the maximum under the rule comparator.
        let keeper = present
            .iter()
            .copied()
            .max_by(|a, b| compare(a, b, rules))
            .expect("present is non-empty");
        let reason = keeper_reason(rules);
        for c in present {
            let keep = c.file_id == keeper.file_id;
            out.push(SelectionDecision {
                file_id: c.file_id,
                keep,
                reason: if keep { reason.clone() } else { "duplicate".into() },
            });
        }
    }
    out
}

/// Ordering where `Greater` means "more deserving to be KEPT".
fn compare(a: &Candidate, b: &Candidate, rules: &[SelectionRule]) -> Ordering {
    for rule in rules {
        let ord = match rule {
            SelectionRule::KeepNewest => a.modified_at.unwrap_or(0).cmp(&b.modified_at.unwrap_or(0)),
            SelectionRule::KeepOldest => b.modified_at.unwrap_or(i64::MAX).cmp(&a.modified_at.unwrap_or(i64::MAX)),
            SelectionRule::KeepShortestPath => b.path_raw.len().cmp(&a.path_raw.len()),
            SelectionRule::KeepLongestPath => a.path_raw.len().cmp(&b.path_raw.len()),
            SelectionRule::PreferOnline => a.online.cmp(&b.online),
            SelectionRule::KeepOnVolume(v) => (a.volume_id == *v).cmp(&(b.volume_id == *v)),
            SelectionRule::KeepInPathContaining(s) => {
                let s = s.to_lowercase();
                a.path_raw.to_lowercase().contains(&s).cmp(&b.path_raw.to_lowercase().contains(&s))
            }
        };
        if ord != Ordering::Equal {
            return ord;
        }
    }
    // Tie-break deterministically by lowest file_id so reruns are stable.
    b.file_id.cmp(&a.file_id)
}

fn keeper_reason(rules: &[SelectionRule]) -> String {
    match rules.first() {
        Some(SelectionRule::KeepNewest) => "kept: newest".into(),
        Some(SelectionRule::KeepOldest) => "kept: oldest".into(),
        Some(SelectionRule::KeepShortestPath) => "kept: shortest path".into(),
        Some(SelectionRule::KeepLongestPath) => "kept: longest path".into(),
        Some(SelectionRule::PreferOnline) => "kept: online copy".into(),
        Some(SelectionRule::KeepOnVolume(_)) => "kept: preferred drive".into(),
        Some(SelectionRule::KeepInPathContaining(_)) => "kept: preferred folder".into(),
        None => "kept".into(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn c(file_id: i64, present: bool, online: bool, modified: i64, path: &str) -> Candidate {
        Candidate { file_id, content_id: 1, volume_id: 1, online, present, path_raw: path.into(), modified_at: Some(modified) }
    }

    #[test]
    fn keep_newest_keeps_exactly_one() {
        let cands = vec![c(1, true, true, 100, "a"), c(2, true, true, 200, "b"), c(3, true, true, 150, "c")];
        let d = apply(&cands, &[SelectionRule::KeepNewest]);
        let kept: Vec<i64> = d.iter().filter(|x| x.keep).map(|x| x.file_id).collect();
        assert_eq!(kept, vec![2], "newest (file 2) kept, others removed");
    }

    #[test]
    fn never_removes_non_present() {
        let cands = vec![c(1, false, false, 100, "a"), c(2, true, true, 50, "b")];
        let d = apply(&cands, &[SelectionRule::KeepNewest]);
        // file 1 is deleted-from-location => kept; file 2 is the only present => kept.
        assert!(d.iter().all(|x| x.keep));
    }

    #[test]
    fn prefer_online_then_newest() {
        let cands = vec![
            c(1, true, false, 300, "old-offline"), // newest but offline
            c(2, true, true, 100, "online"),       // online, older
        ];
        let d = apply(&cands, &[SelectionRule::PreferOnline, SelectionRule::KeepNewest]);
        let kept: Vec<i64> = d.iter().filter(|x| x.keep).map(|x| x.file_id).collect();
        assert_eq!(kept, vec![2], "online beats newer-but-offline");
    }

    #[test]
    fn keep_on_preferred_volume() {
        let mut a = c(1, true, true, 100, "x");
        a.volume_id = 1;
        let mut b = c(2, true, true, 100, "x");
        b.volume_id = 7;
        let d = apply(&[a, b], &[SelectionRule::KeepOnVolume(7)]);
        let kept: Vec<i64> = d.iter().filter(|x| x.keep).map(|x| x.file_id).collect();
        assert_eq!(kept, vec![2], "file on the preferred volume (7) is kept");
    }

    #[test]
    fn tie_break_is_deterministic_lowest_id() {
        // Two indistinguishable candidates (no rule separates them) → lowest file_id kept,
        // so reruns are stable (a property the lessons file insists on).
        let cands = vec![c(5, true, true, 100, "same"), c(3, true, true, 100, "same")];
        let d = apply(&cands, &[SelectionRule::KeepNewest]);
        let kept: Vec<i64> = d.iter().filter(|x| x.keep).map(|x| x.file_id).collect();
        assert_eq!(kept, vec![3], "lowest file_id wins ties deterministically");
    }
}
