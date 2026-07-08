//! Shared DTOs that cross the Rust↔TypeScript boundary. These are the API contract
//! (FORGE Law 7: contract before code). The TypeScript mirror lives in
//! `src/lib/contract.ts` and MUST stay in lockstep with these shapes
//! (lesson SCHEMA-001: keep struct/interface/query columns in sync).

use serde::{Deserialize, Serialize};

/// Filters applied before/while scanning. Mirrors `scan_profile.filters_json`.
/// `#[serde(default)]` so the UI may omit optional fields without a deserialize error
/// (lesson: validate/tolerate missing params; distinguish absent from invalid).
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct ScanFilters {
    pub include_exts: Vec<String>,     // empty = all
    pub exclude_exts: Vec<String>,
    pub min_size_bytes: Option<u64>,
    pub max_size_bytes: Option<u64>,
    pub modified_after: Option<i64>,
    pub modified_before: Option<i64>,
    pub include_hidden: bool,
    pub include_system: bool,
    pub follow_reparse: bool,          // default false (CODING_RULES §5)
    pub exclude_path_globs: Vec<String>,
    pub skip_zero_byte: bool,
}

/// Scan thoroughness. `lightweight` stops at partial-hash confidence; `thorough`
/// promotes every candidate to a full hash before declaring a duplicate.
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum ScanMode {
    Lightweight,
    Thorough,
}

/// A duplicate cluster as shown in the Results workspace.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ClusterView {
    pub cluster_id: i64,
    pub content_id: i64,
    pub size_bytes: i64,
    pub member_count: i64,
    pub reclaimable_bytes: i64,
    pub confidence: String, // "high" | "medium" | "low"
    pub kind: String,       // "exact" | "name_size" | "folder"
    pub members: Vec<FileView>,
}

/// Media integrity + technical-quality metadata for one file (from the ffprobe-backed
/// probe pass). Attached to a `FileView` only when the file has been analyzed; absent
/// otherwise (non-media file, or analysis not yet run / ffprobe unavailable). All numeric
/// fields are optional because a corrupt/partial file may expose only some of them.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MediaMeta {
    pub probe_state: String,      // ok | error | unsupported
    pub integrity: String,        // healthy | suspicious | partial | corrupted | unreadable
    pub duration_s: Option<f64>,
    pub width: Option<i64>,
    pub height: Option<i64>,
    pub bitrate: Option<i64>,     // overall bits/sec
    pub codec: Option<String>,
    pub fps: Option<f64>,
    pub has_audio: bool,
    pub stream_count: Option<i64>,
    pub quality_grade: String,    // good | fair | poor | unknown
    pub quality_warning: bool,
    pub warn_reason: Option<String>,
    pub analyzed_at: i64,
}

/// A single file row in a cluster or in the index explorer.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FileView {
    pub file_id: i64,
    pub volume_label: String,
    pub volume_online: bool,
    pub drive: Option<String>,      // mount point / drive letter, e.g. "E:\" (None if offline)
    pub path_raw: String,           // volume-relative path
    pub abs_path: Option<String>,   // full absolute path when the volume is online
    pub size_bytes: i64,
    pub created_at: Option<i64>,    // filesystem creation time
    pub modified_at: Option<i64>,
    pub first_seen_at: Option<i64>, // when first indexed
    pub last_seen_at: Option<i64>,  // when last confirmed by a scan (the "scanned at" time)
    pub last_scan_job_id: Option<i64>, // links the row to the scan session that touched it
    pub status: String,    // present | missing | deleted_by_user | moved | error
    pub hash_state: String,
    pub media: Option<MediaMeta>, // present once the media-probe pass has analyzed this file
}

/// Terminal result of the media-probe pass (also the `media://done` event payload).
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct MediaAnalyzeResult {
    pub available: bool,        // was ffprobe found & runnable?
    pub ffprobe_path: Option<String>,
    pub newly_analyzed: i64,    // files probed this pass
    pub total_media: i64,       // total media files now carrying metadata
    pub warnings: i64,          // files flagged with a quality warning
    pub corrupted: i64,         // files whose integrity is corrupted/unreadable/partial
    pub cancelled: bool,        // true if the user stopped the pass early
    pub message: Option<String>, // user-facing note (e.g. "ffprobe not found")
}

/// Live progress of the media-probe pass (streamed as `media://progress`, ~per batch).
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct MediaProgress {
    pub done: i64,
    pub total: i64,
    pub warnings: i64,
    pub corrupted: i64,
}

/// Whether the ffprobe binary backing media analysis is available.
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct FfprobeStatus {
    pub available: bool,
    pub path: Option<String>,
    pub version: Option<String>,
}

/// Progress event streamed to the UI during a scan (debounced ~250ms — §7).
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ScanProgress {
    pub job_id: i64,
    pub state: String,
    pub stage: String,
    pub files_seen: u64,
    pub files_hashed: u64,
    pub bytes_hashed: u64,
    pub candidate_groups: u64,
    pub eta_seconds: Option<u64>,
}

/// Outcome of executing a deletion plan. Distinguishes partial from full success
/// (lesson PARTIAL-FAIL-001: partial success is NOT success).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeletionOutcome {
    pub state: String, // "success" | "partial" | "failed"
    pub removed: u64,
    pub failed: u64,
    pub failures: Vec<DeletionFailure>,
    pub reclaimed_bytes: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeletionFailure {
    pub file_id: i64,
    pub path_raw: String,
    pub reason: String,
}

// ===========================================================================
// ADVANCED (parent-folder) DELETION — Index Explorer. Lets the user delete not
// just a selected file but its whole containing folder, with a precise preview
// of the collateral and a hard safety gate against wiping a SHARED root folder
// (e.g. "Downloads") when a selected file sits directly inside it.
// ===========================================================================

/// One immediate child of a parent folder, shown in the collateral preview.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FolderEntry {
    pub name: String,
    pub is_dir: bool,
    pub size_bytes: i64,    // file size, or recursive size for a subfolder (bounded)
    pub is_selected: bool,  // true if this entry IS one of the user's selected files
}

/// One parent folder that would be deleted wholesale, with its risk verdict.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FolderDeletionGroup {
    pub folder_abs: String,
    pub folder_name: String,
    pub volume_id: i64,
    pub immediate_entries: i64,  // count of immediate children
    pub recursive_files: i64,    // total files under the folder (bounded)
    pub total_bytes: i64,        // recursive size under the folder (bounded)
    pub selected_inside: i64,    // how many of the user's selected files live here
    pub sample: Vec<FolderEntry>, // up to N immediate children for the collapse preview
    pub risk: String,            // "safe" | "caution" | "danger"
    pub risk_reason: String,
    pub eligible: bool,          // false = excluded by the safety gate (danger)
}

/// Preview of an advanced (folder) deletion over the selected files.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FolderDeletionPreview {
    pub groups: Vec<FolderDeletionGroup>,
    pub skipped: Vec<DeletionFailure>, // selected files that map to no deletable folder
    pub eligible_folders: i64,
    pub eligible_bytes: i64,           // sum of recursive size over ELIGIBLE folders
    pub eligible_files: i64,           // sum of recursive files over ELIGIBLE folders
}

/// A known volume as shown in the Source Manager / Home (incl. offline drives).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VolumeView {
    pub volume_id: i64,
    pub label: String,
    pub fs_type: String,
    pub mount_point: Option<String>,
    pub is_online: bool,
    pub is_removable: bool,
    pub total_bytes: Option<i64>,    // drive capacity
    pub indexed_bytes: i64,          // sum of present indexed file sizes (for the folder tree)
    pub file_count: i64,
    pub last_seen_at: i64,
}

/// One folder node in the lazy, collapsible folder-size tree (TreeSize-style).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FolderNode {
    pub name: String,         // leaf folder name
    pub rel_path: String,     // volume-relative path
    pub total_bytes: i64,     // recursive size of all files under this folder
    pub file_count: i64,      // recursive file count
    pub subfolder_count: i64, // recursive subfolder count
    pub has_children: bool,   // whether it can be expanded
}

/// Headline counters for the Home screen.
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct IndexStats {
    pub total_files: i64,
    pub present_files: i64,
    pub distinct_contents: i64,
    pub duplicate_clusters: i64,
    pub reclaimable_bytes: i64,
    pub known_volumes: i64,
    pub online_volumes: i64,
}

/// Per-content presence across the whole history (drives the "still exists?" badge).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PresenceView {
    pub content_id: i64,
    pub online_copies: u32,
    pub offline_copies: u32,
    pub deleted_copies: u32,
    pub verdict: String,
    pub copies: Vec<FileView>,
}

/// Filters for the Index Explorer (queries the persistent index, no scan needed).
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct IndexQuery {
    pub text: Option<String>,        // SUBSTRING match on file_name / path_key (contains)
    pub volume_id: Option<i64>,
    pub exts: Vec<String>,           // checkbox-selected extensions (IN list); preferred
    pub ext: Option<String>,         // legacy single-ext (still honored if exts is empty)
    pub only_duplicates: bool,
    pub status: Option<String>,      // present | missing | deleted_by_user | ...
    pub min_size_bytes: Option<i64>,
    pub max_size_bytes: Option<i64>,
    pub scanned_after: Option<i64>,  // last_seen_at >= (time range, from)
    pub scanned_before: Option<i64>, // last_seen_at <= (time range, to)
    pub integrity: Option<String>,   // legacy single integrity (honored if `integrities` empty)
    pub integrities: Vec<String>,    // multi-select integrity filter (IN list); preferred
    pub only_warnings: bool,         // only files with a quality warning
    pub only_media: bool,            // only files that have media metadata
    pub sort_key: Option<String>,    // name|path|size|created|modified|scanned|duration|bitrate|resolution (whitelisted)
    pub sort_dir: Option<String>,    // asc|desc
    pub limit: i64,
    pub offset: i64,
}

/// A saved, recallable Index Explorer search (Everything-style bookmark). Persisted as
/// `Sift-Data/bookmarks.json` — the whole filter set, re-applied with one click.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Bookmark {
    pub name: String,
    pub query: IndexQuery,
}

/// A user-defined "custom action" (the plugin surface): run an external program on selected
/// files. `args` is a template with %path% / %folder% / %name% / %ext% tokens. Persisted as
/// `Sift-Data/actions.json`.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CustomAction {
    pub name: String,
    pub program: String,
    pub args: String,
}

/// Honest result of running a custom action over a set of files (partial != success).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ActionRunResult {
    pub ran: u32,
    pub failed: u32,
    pub failures: Vec<String>,
}

/// How the Results workspace sorts clusters.
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum ClusterSort {
    Reclaimable,
    Size,
    MemberCount,
}

/// An audit-log row for the Reports/Audit screen.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AuditView {
    pub id: i64,
    pub at: i64,
    pub action: String,
    pub outcome: String,
    pub path_raw: Option<String>,
    pub reversible: bool,
}

/// Free/Pro entitlement status for the Settings → License card (initiative #1). With
/// `enforcement_enabled = false` (the shipped default) every feature is unlocked regardless of
/// tier — the mechanism is built but the paywall is off until commercial launch.
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct LicenseStatus {
    pub tier: String,               // "free" | "pro"
    pub email: Option<String>,
    pub expires_at: Option<i64>,
    pub enforcement_enabled: bool,  // false => all features unlocked (pre-launch)
    pub pro_features: Vec<String>,  // feature keys that move behind Pro once enforced
}

/// Headline "reclaim" evidence for the Reports screen — what Sift has actually cleaned up,
/// derived from the persistent audit log + index (initiative #8).
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct ReclaimSummary {
    pub bytes_reclaimed: i64,          // cumulative size of files Sift removed
    pub files_removed: i64,
    pub recycled: i64,
    pub quarantined: i64,
    pub permanently_deleted: i64,
    pub files_renamed: i64,            // non-destructive "mark" actions
    pub files_restored: i64,           // brought back via Restore Center
    pub still_reclaimable_bytes: i64,  // remaining duplicate waste in the index
    pub present_files: i64,
    pub duplicate_clusters: i64,
    pub generated_at: i64,
}

/// One reversible action in the Restore Center (cross-session undo of recycle/quarantine/rename).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RestoreEntry {
    pub audit_id: i64,
    pub at: i64,
    pub action: String,         // recycle | quarantine | rename | permanent
    pub path: String,           // original path (or "new → old" for a rename)
    pub already_restored: bool,
    pub restorable: bool,
    pub note: Option<String>,   // why it can't be restored, when restorable=false
}

/// Scheduled & background auto-rescan policy. Persisted as `Sift-Data\schedule.json` and
/// mirrored to a Windows Task Scheduler job. `enabled=false` (the default) = fully opt-in.
/// `taskRegistered` is a read-only echo of whether the OS scheduler currently has the job.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ScheduleConfig {
    pub enabled: bool,
    pub interval_hours: i64,
    #[serde(default)]
    pub task_registered: bool,
}

impl Default for ScheduleConfig {
    fn default() -> Self {
        // Opt-in: disabled, with a sensible daily cadence pre-filled for when the user enables it.
        Self { enabled: false, interval_hours: 24, task_registered: false }
    }
}

/// A duplicate-folder cluster for the Folders view.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FolderClusterView {
    pub cluster_id: i64,
    pub file_count: i64,
    pub total_bytes: i64,
    pub member_count: i64,
    pub reclaimable_bytes: i64,
    pub members: Vec<FolderMemberView>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FolderMemberView {
    pub volume_label: String,
    pub volume_online: bool,
    pub folder_path_raw: String,
}

/// Where the portable index lives + how big it is (Home/Settings "storage" panel).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StorageInfo {
    pub data_dir: String,
    pub db_path: String,
    pub db_bytes: i64,
    pub portable: bool,
}

/// One timeline entry of a scan (minute-progress tick or a lifecycle/stage event).
/// Also streamed live to the Scan Monitor via the `scan://log` event.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ScanLogEvent {
    pub job_id: i64,
    pub at: i64,
    pub elapsed_ms: i64,
    pub kind: String,        // progress|started|source|folder|stage|paused|resumed|interrupted|completed|failed|cancelled|warning
    pub message: String,
    pub files_processed: i64,
}

/// Per-folder traversal state for the completion tree + resume awareness.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FolderTraversalView {
    pub folder_path_raw: String,
    pub depth: i64,
    pub state: String,       // pending|in_progress|completed|skipped|failed|unavailable
    pub file_count: i64,
    pub started_at: Option<i64>,
    pub completed_at: Option<i64>,
    pub duration_ms: i64,
}

/// A completed/ongoing scan session for the Scan Log history view.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ScanSessionView {
    pub job_id: i64,
    pub state: String,
    pub mode: Option<String>,
    pub drive_label: Option<String>,
    pub sources_json: Option<String>,
    pub started_at: i64,
    pub finished_at: Option<i64>,
    pub duration_ms: i64,
    pub files_seen: i64,
    pub files_hashed: i64,
    pub bytes_hashed: i64,
    pub total_bytes: i64,
    pub folders_traversed: i64,
    pub subfolders_traversed: i64,
    pub skipped_count: i64,
    pub error_count: i64,
    pub duplicates_found: i64,
    pub scanning_ms: i64,
    pub hashing_ms: i64,
    pub resumable: bool,
    pub profile_name: Option<String>,
    pub build_version: Option<String>,
    pub error_message: Option<String>,
}

/// Full detail for one scan session (header + timeline + folder traversal).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ScanSessionDetail {
    pub session: ScanSessionView,
    pub events: Vec<ScanLogEvent>,
    pub folders: Vec<FolderTraversalView>,
}

/// One image in a similar-image group, with its visual similarity to the group's keeper.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SimilarImageMember {
    pub file: FileView,
    pub similarity_pct: u32,     // (1 - hamming/64) * 100, vs the representative
    pub is_representative: bool, // the largest image — the suggested keeper
}

/// A cluster of visually-similar images (perceptual hash within the strictness threshold).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SimilarImageGroup {
    pub group_id: i64,
    pub member_count: i64,
    pub max_size_bytes: i64,
    pub members: Vec<SimilarImageMember>,
}

/// Result of the similar-image detection pass.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SimilarImageResult {
    pub newly_hashed: i64,
    pub total_hashed: i64,
    pub groups: Vec<SimilarImageGroup>,
}

/// One video in a similar-video group (initiative #4) — mirrors the similar-image DTOs.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SimilarVideoMember {
    pub file: FileView,
    pub similarity_pct: u32,
    pub is_representative: bool, // the largest video — the suggested keeper
}

/// A cluster of perceptually-similar videos (keyframe-dHash signature within threshold).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SimilarVideoGroup {
    pub group_id: i64,
    pub member_count: i64,
    pub max_size_bytes: i64,
    pub members: Vec<SimilarVideoMember>,
}

/// Result of the similar-video detection pass.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SimilarVideoResult {
    pub newly_hashed: i64,
    pub total_hashed: i64,
    pub groups: Vec<SimilarVideoGroup>,
}
