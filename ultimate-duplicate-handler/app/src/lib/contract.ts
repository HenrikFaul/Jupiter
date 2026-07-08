// TypeScript mirror of the Rust IPC contract (src-tauri/src/model.rs + commands.rs).
// MUST stay in lockstep with the Rust types — a drift returns undefined fields with no
// error (lesson SCHEMA-001 / FRONTEND-006). CI compares this file against the Rust structs.

import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";

// ----- enums / primitives -----
export type Confidence = "high" | "medium" | "low";
export type ClusterKind = "exact" | "name_size" | "folder";
export type ScanMode = "lightweight" | "thorough";
export type ClusterSort = "reclaimable" | "size" | "memberCount";
export type DeletionMode = "recycle" | "quarantine" | "permanent";
export type FileStatus = "present" | "missing" | "deleted_by_user" | "moved" | "error";

// ----- views -----
export type Integrity = "healthy" | "suspicious" | "partial" | "corrupted" | "unreadable";
export type QualityGrade = "good" | "fair" | "poor" | "unknown";

/** Media integrity + technical-quality metadata (present once a file has been probed). */
export interface MediaMeta {
  probeState: "ok" | "error" | "unsupported";
  integrity: Integrity;
  durationS: number | null;
  width: number | null;
  height: number | null;
  bitrate: number | null;      // overall bits/sec
  codec: string | null;
  fps: number | null;
  hasAudio: boolean;
  streamCount: number | null;
  qualityGrade: QualityGrade;
  qualityWarning: boolean;
  warnReason: string | null;
  analyzedAt: number;
}

export interface FileView {
  fileId: number;
  volumeLabel: string;
  volumeOnline: boolean;
  drive: string | null;        // mount point / drive letter (null if offline)
  pathRaw: string;             // volume-relative path
  absPath: string | null;      // full absolute path when the volume is online
  sizeBytes: number;
  createdAt: number | null;    // filesystem creation time
  modifiedAt: number | null;
  firstSeenAt: number | null;  // first indexed
  lastSeenAt: number | null;   // last confirmed by a scan ("scanned at")
  lastScanJobId: number | null; // links to the scan session that touched it
  status: FileStatus;
  hashState: "none" | "partial" | "full" | "error";
  media: MediaMeta | null;     // present once the media-probe pass has analyzed this file
}

export interface ClusterView {
  clusterId: number;
  contentId: number;
  sizeBytes: number;
  memberCount: number;
  reclaimableBytes: number;
  confidence: Confidence;
  kind: ClusterKind;
  members: FileView[];
}

export interface VolumeView {
  volumeId: number;
  label: string;
  fsType: string;
  mountPoint: string | null;
  isOnline: boolean;
  isRemovable: boolean;
  totalBytes: number | null;   // drive capacity
  indexedBytes: number;        // sum of present indexed file sizes (folder-tree root)
  fileCount: number;
  lastSeenAt: number;
}

/** One folder in the lazy, collapsible folder-size tree (TreeSize-style). */
export interface FolderNode {
  name: string;
  relPath: string;             // volume-relative path
  totalBytes: number;          // recursive size of all files under this folder
  fileCount: number;           // recursive file count
  subfolderCount: number;      // recursive subfolder count
  hasChildren: boolean;        // whether it can be expanded
}

export interface IndexStats {
  totalFiles: number;
  presentFiles: number;
  distinctContents: number;
  duplicateClusters: number;
  reclaimableBytes: number;
  knownVolumes: number;
  onlineVolumes: number;
}

export interface PresenceView {
  contentId: number;
  onlineCopies: number;
  offlineCopies: number;
  deletedCopies: number;
  verdict: string;
  copies: FileView[];
}

export interface IndexQuery {
  text?: string | null;        // SUBSTRING (contains) match on file name / path
  volumeId?: number | null;
  exts: string[];              // checkbox-selected extensions (preferred)
  ext?: string | null;         // legacy single ext (still honored if exts is empty)
  onlyDuplicates: boolean;
  status?: FileStatus | null;
  minSizeBytes?: number | null;
  maxSizeBytes?: number | null;
  scannedAfter?: number | null;  // last_seen_at >= (time-range from)
  scannedBefore?: number | null; // last_seen_at <= (time-range to)
  integrity?: Integrity | null;  // legacy single integrity (honored if `integrities` empty)
  integrities: Integrity[];      // multi-select integrity filter (preferred)
  onlyWarnings: boolean;         // only files with a quality warning
  onlyMedia: boolean;            // only files that have media metadata
  sortKey?: SortKey | null;      // column to sort by
  sortDir?: "asc" | "desc" | null;
  limit: number;
  offset: number;
}

export type SortKey =
  | "name" | "path" | "size" | "created" | "modified" | "scanned"
  | "duration" | "bitrate" | "resolution" | "integrity" | "quality";

export interface ScanFilters {
  includeExts: string[];
  excludeExts: string[];
  minSizeBytes?: number | null;
  maxSizeBytes?: number | null;
  modifiedAfter?: number | null;
  modifiedBefore?: number | null;
  includeHidden: boolean;
  includeSystem: boolean;
  followReparse: boolean;
  excludePathGlobs: string[];
  skipZeroByte: boolean;
}

export function defaultFilters(): ScanFilters {
  return {
    includeExts: [],
    excludeExts: [],
    includeHidden: false,
    includeSystem: false,
    followReparse: false,
    excludePathGlobs: [],
    skipZeroByte: true,
  };
}

export interface SourceInput {
  volumeMount: string;
  relRoot?: string | null;
  sourceId?: number | null;
}

export interface ScanProgress {
  jobId: number;
  state: string;
  stage: string;
  filesSeen: number;
  filesHashed: number;
  bytesHashed: number;
  candidateGroups: number;
  etaSeconds: number | null;
}

export interface AuditView {
  id: number;
  at: number;
  action: string;
  outcome: string;
  pathRaw: string | null;
  reversible: boolean;
}

export interface PlanItemInput {
  fileId: number;
  contentId: number;
  volumeOnline: boolean;
  statusPresent: boolean;
  pathRaw: string;
  keep: boolean;
}

export interface PlanDecision {
  fileId: number;
  keep: boolean;
  reason?: string | null;
}

export interface ValidationResult {
  ok: boolean;
  violations: string[];
}

export interface DeletionOutcome {
  state: "success" | "partial" | "failed";
  removed: number;
  failed: number;
  failures: { fileId: number; pathRaw: string; reason: string }[];
  reclaimedBytes: number;
}

// ===== command wrappers (every call propagates engine errors) =====
export const getIndexStats = () => invoke<IndexStats>("get_index_stats");
export const listVolumes = () => invoke<VolumeView[]>("list_volumes");
export const queryClusters = (sort: ClusterSort, limit: number, offset: number) =>
  invoke<ClusterView[]>("query_clusters", { sort, limit, offset });
export const getPresence = (contentId: number) =>
  invoke<PresenceView>("get_presence", { contentId });
export const searchIndex = (query: IndexQuery) =>
  invoke<FileView[]>("search_index", { query });

// ----- bookmarks: named, recallable Index Explorer searches (Everything-style) -----
export interface Bookmark {
  name: string;
  query: IndexQuery;
}
export const getBookmarks = () => invoke<Bookmark[]>("get_bookmarks");
export const saveBookmark = (name: string, query: IndexQuery) =>
  invoke<Bookmark[]>("save_bookmark", { name, query });
export const deleteBookmark = (name: string) =>
  invoke<Bookmark[]>("delete_bookmark", { name });

// ----- custom actions ("plugins"): run user-defined external tools on selected files -----
/** A user-defined external tool. `args` is a template with %path% / %folder% / %name% / %ext%. */
export interface CustomAction {
  name: string;
  program: string;
  args: string;
}
export interface ActionRunResult {
  ran: number;
  failed: number;
  failures: string[];
}
export const getActions = () => invoke<CustomAction[]>("get_actions");
export const saveAction = (action: CustomAction) =>
  invoke<CustomAction[]>("save_action", { action });
export const deleteAction = (name: string) =>
  invoke<CustomAction[]>("delete_action", { name });
export const runAction = (name: string, fileIds: number[]) =>
  invoke<ActionRunResult>("run_action", { name, fileIds });
export const listAudit = (limit: number) => invoke<AuditView[]>("list_audit", { limit });
export const dbIntegrityCheck = () => invoke<boolean>("db_integrity_check");

export const validateDeletionPlan = (items: PlanItemInput[], acknowledgedOnline: number[]) =>
  invoke<ValidationResult>("validate_deletion_plan", { items, acknowledgedOnline });
export const createDeletionPlan = (
  name: string | null,
  mode: DeletionMode,
  decisions: PlanDecision[],
) => invoke<number>("create_deletion_plan", { name, mode, decisions });
export const executeDeletionPlan = (
  planId: number,
  mode: DeletionMode,
  acknowledgedOnline: number[],
) => invoke<DeletionOutcome>("execute_deletion_plan", { planId, mode, acknowledgedOnline });

// ----- similar images (perceptual hashing) -----
export interface SimilarImageMember {
  file: FileView;
  similarityPct: number;
  isRepresentative: boolean;
}
export interface SimilarImageGroup {
  groupId: number;
  memberCount: number;
  maxSizeBytes: number;
  members: SimilarImageMember[];
}
export interface SimilarImageResult {
  newlyHashed: number;
  totalHashed: number;
  groups: SimilarImageGroup[];
}
export const detectSimilarImages = (threshold: number) =>
  invoke<SimilarImageResult>("detect_similar_images", { threshold });
export const getThumbnail = (fileId: number, max: number) =>
  invoke<string>("get_thumbnail", { fileId, max });

// ----- similar videos (perceptual keyframe fingerprinting, initiative #4) -----
export interface SimilarVideoMember {
  file: FileView;
  similarityPct: number;
  isRepresentative: boolean;
}
export interface SimilarVideoGroup {
  groupId: number;
  memberCount: number;
  maxSizeBytes: number;
  members: SimilarVideoMember[];
}
export interface SimilarVideoResult {
  newlyHashed: number;
  totalHashed: number;
  groups: SimilarVideoGroup[];
}
export const detectSimilarVideos = (threshold: number) =>
  invoke<SimilarVideoResult>("detect_similar_videos", { threshold });
export const getVideoThumbnail = (fileId: number, max: number) =>
  invoke<string>("get_video_thumbnail", { fileId, max });
export const deleteFiles = (fileIds: number[], mode: DeletionMode) =>
  invoke<DeletionOutcome>("delete_files", { fileIds, mode });

// ----- advanced (parent-folder) deletion — Index Explorer -----
/** One immediate child of a parent folder, shown in the collateral preview. */
export interface FolderEntry {
  name: string;
  isDir: boolean;
  sizeBytes: number;     // file size, or recursive size for a subfolder
  isSelected: boolean;   // true if this entry IS one of the selected files
}
export type FolderRisk = "safe" | "caution" | "danger";
/** One parent folder that would be deleted wholesale, with its safety verdict. */
export interface FolderDeletionGroup {
  folderAbs: string;
  folderName: string;
  volumeId: number;
  immediateEntries: number;
  recursiveFiles: number;
  totalBytes: number;
  selectedInside: number;
  sample: FolderEntry[];
  risk: FolderRisk;
  riskReason: string;
  eligible: boolean;     // false = excluded by the safety gate (danger)
}
export interface FolderDeletionPreview {
  groups: FolderDeletionGroup[];
  skipped: { fileId: number; pathRaw: string; reason: string }[];
  eligibleFolders: number;
  eligibleBytes: number;
  eligibleFiles: number;
}
/** Preview which parent folders would be deleted (and the collateral) for the selected files. */
export const previewFolderDeletion = (fileIds: number[]) =>
  invoke<FolderDeletionPreview>("preview_folder_deletion", { fileIds });
/** Delete whole folders. The engine re-validates each against the safety gate. */
export const deleteFolders = (folders: string[], mode: DeletionMode) =>
  invoke<DeletionOutcome>("delete_folders", { folders, mode });

// ----- folder tree (lazy, collapsible structure view) -----
/** Immediate child folders of `parentRel` ("" = volume root) with recursive size/counts. */
export const folderChildren = (volumeId: number, parentRel: string) =>
  invoke<FolderNode[]>("folder_children", { volumeId, parentRel });

// ----- media integrity & quality analysis (ffprobe-backed, background + cancellable) -----
export interface MediaAnalyzeResult {
  available: boolean;          // was ffprobe found & runnable?
  ffprobePath: string | null;
  newlyAnalyzed: number;
  totalMedia: number;
  warnings: number;
  corrupted: number;
  cancelled: boolean;          // user stopped the pass early
  message: string | null;      // guidance (e.g. ffprobe not found)
}
export interface MediaProgress {
  done: number;
  total: number;
  warnings: number;
  corrupted: number;
}
export interface FfprobeStatus {
  available: boolean;
  path: string | null;
  version: string | null;
}
/** Start analysis on a background thread. `fileIds=null` analyzes every not-yet-probed
 *  video; pass an explicit list to scope it to the filtered/selected rows. Progress arrives
 *  via media://progress and a terminal media://done — see the listeners below. */
export const startMediaAnalysis = (fileIds: number[] | null, deep: boolean) =>
  invoke<void>("start_media_analysis", { fileIds, deep });
export const cancelMediaAnalysis = () => invoke<void>("cancel_media_analysis");
export const mediaIsActive = () => invoke<boolean>("media_is_active");
export const ffprobeStatus = () => invoke<FfprobeStatus>("ffprobe_status");
export function onMediaProgress(cb: (p: MediaProgress) => void): Promise<UnlistenFn> {
  return listen<MediaProgress>("media://progress", (e) => cb(e.payload));
}
export function onMediaDone(cb: (r: MediaAnalyzeResult) => void): Promise<UnlistenFn> {
  return listen<MediaAnalyzeResult>("media://done", (e) => cb(e.payload));
}
export function onMediaError(cb: (msg: string) => void): Promise<UnlistenFn> {
  return listen<string>("media://error", (e) => cb(e.payload));
}

export const startScan = (sources: SourceInput[], filters: ScanFilters, mode: ScanMode) =>
  invoke<void>("start_scan", { sources, filters, mode });
export const cancelScan = () => invoke<void>("cancel_scan");
/** Source-of-truth check the UI can poll to recover from a stuck-looking state. */
export const scanIsActive = () => invoke<boolean>("scan_is_active");

// ----- duplicate folders -----
export interface FolderMemberView {
  volumeLabel: string;
  volumeOnline: boolean;
  folderPathRaw: string;
}
export interface FolderClusterView {
  clusterId: number;
  fileCount: number;
  totalBytes: number;
  memberCount: number;
  reclaimableBytes: number;
  members: FolderMemberView[];
}
export const queryFolderClusters = (limit: number) =>
  invoke<FolderClusterView[]>("query_folder_clusters", { limit });
export const detectDuplicateFolders = () => invoke<number>("detect_duplicate_folders");

// ----- smart selection rule engine -----
export type SelectionRule =
  | { kind: "keepNewest" }
  | { kind: "keepOldest" }
  | { kind: "keepShortestPath" }
  | { kind: "keepLongestPath" }
  | { kind: "preferOnline" }
  | { kind: "keepOnVolume"; value: number }
  | { kind: "keepInPathContaining"; value: string };

export interface SelectionDecision {
  fileId: number;
  keep: boolean;
  reason: string;
}
export const applySelectionRules = (rules: SelectionRule[]) =>
  invoke<SelectionDecision[]>("apply_selection_rules", { rules });

// ----- storage / portability -----
export interface StorageInfo {
  dataDir: string;
  dbPath: string;
  dbBytes: number;
  portable: boolean;
}
export const storageInfo = () => invoke<StorageInfo>("storage_info");

// ----- update channel status (initiative #5; signed auto-download lands with the first release) -----
export interface UpdateStatus {
  currentVersion: string;
  portable: boolean;
  updatesEnabled: boolean;
  feedUrl: string | null;
  message: string;
}
export const getUpdateStatus = () => invoke<UpdateStatus>("get_update_status");

// ----- scan history / log / folder traversal -----
export type ScanLogKind =
  | "progress" | "started" | "source" | "folder" | "stage"
  | "paused" | "resumed" | "interrupted" | "completed" | "failed" | "cancelled" | "warning";

export interface ScanLogEvent {
  jobId: number;
  at: number;
  elapsedMs: number;
  kind: ScanLogKind;
  message: string;
  filesProcessed: number;
}

export type FolderState =
  | "pending" | "in_progress" | "completed" | "skipped" | "failed" | "unavailable";

export interface FolderTraversalView {
  folderPathRaw: string;
  depth: number;
  state: FolderState;
  fileCount: number;
  startedAt: number | null;
  completedAt: number | null;
  durationMs: number;
}

export interface ScanSessionView {
  jobId: number;
  state: string;
  mode: string | null;
  driveLabel: string | null;
  sourcesJson: string | null;
  startedAt: number;
  finishedAt: number | null;
  durationMs: number;
  filesSeen: number;
  filesHashed: number;
  bytesHashed: number;
  totalBytes: number;
  foldersTraversed: number;
  subfoldersTraversed: number;
  skippedCount: number;
  errorCount: number;
  duplicatesFound: number;
  scanningMs: number;
  hashingMs: number;
  resumable: boolean;
  profileName: string | null;
  buildVersion: string | null;
  errorMessage: string | null;
}

export interface ScanSessionDetail {
  session: ScanSessionView;
  events: ScanLogEvent[];
  folders: FolderTraversalView[];
}

export const listScanSessions = (limit: number) =>
  invoke<ScanSessionView[]>("list_scan_sessions", { limit });
export const getScanSession = (jobId: number) =>
  invoke<ScanSessionDetail>("get_scan_session", { jobId });
export const deleteScanSession = (jobId: number) =>
  invoke<void>("delete_scan_session", { jobId });
export const clearScanHistory = () => invoke<number>("clear_scan_history");
export const revealInExplorer = (absPath: string) =>
  invoke<void>("reveal_in_explorer", { absPath });

// ----- mark (rename) — non-destructive alternative to delete -----
export type AffixPosition = "prefix" | "suffix";
export const markFiles = (fileIds: number[], affix: string, position: AffixPosition) =>
  invoke<DeletionOutcome>("mark_files", { fileIds, affix, position });

// ----- Restore Center — cross-session undo of recycle / quarantine / rename -----
export interface RestoreEntry {
  auditId: number;
  at: number;
  action: "recycle" | "quarantine" | "rename" | "permanent" | string;
  path: string;             // original path (or "new → old" for a rename)
  alreadyRestored: boolean;
  restorable: boolean;
  note: string | null;      // why it can't be restored (when restorable=false)
}
export const listRestorable = (limit: number) =>
  invoke<RestoreEntry[]>("list_restorable", { limit });
export const restoreFiles = (auditIds: number[]) =>
  invoke<DeletionOutcome>("restore_files", { auditIds });

export function onScanLog(cb: (e: ScanLogEvent) => void): Promise<UnlistenFn> {
  return listen<ScanLogEvent>("scan://log", (e) => cb(e.payload));
}

// ----- reports export + reclaim evidence -----
export type ReportKind = "duplicates" | "audit" | "drives" | "folders" | "reclaim";
export const exportReport = (kind: ReportKind) =>
  invoke<string>("export_report", { kind });

export interface ReclaimSummary {
  bytesReclaimed: number;
  filesRemoved: number;
  recycled: number;
  quarantined: number;
  permanentlyDeleted: number;
  filesRenamed: number;
  filesRestored: number;
  stillReclaimableBytes: number;
  presentFiles: number;
  duplicateClusters: number;
  generatedAt: number;
}
export const reclaimSummary = () => invoke<ReclaimSummary>("reclaim_summary");

// ----- scheduled & background auto-rescan (initiative #3) -----
export interface ScheduleConfig {
  enabled: boolean;
  intervalHours: number;
  taskRegistered: boolean; // does the Windows Task Scheduler job actually exist now?
}
export const getSchedule = () => invoke<ScheduleConfig>("get_schedule");
export const setSchedule = (intervalHours: number, enabled: boolean) =>
  invoke<ScheduleConfig>("set_schedule", { intervalHours, enabled });
export const runRescanNow = () => invoke<void>("run_rescan_now");

// ----- protected folders (initiative #6) — never-delete locations -----
export const getProtectedPaths = () => invoke<string[]>("get_protected_paths");
export const setProtectedPaths = (paths: string[]) =>
  invoke<void>("set_protected_paths", { paths });

// ----- license / Free-Pro entitlement (initiative #1) — built, default UNLOCKED -----
export interface LicenseStatus {
  tier: "free" | "pro" | string;
  email: string | null;
  expiresAt: number | null;
  enforcementEnabled: boolean; // false = every feature unlocked (pre-launch)
  proFeatures: string[];       // features that move behind Pro once enforced
}
export const licenseStatus = () => invoke<LicenseStatus>("license_status");
export const activateLicense = (token: string) =>
  invoke<LicenseStatus>("activate_license", { token });
export const deactivateLicense = () => invoke<LicenseStatus>("deactivate_license");

// ===== scan event stream =====
export function onScanProgress(cb: (p: ScanProgress) => void): Promise<UnlistenFn> {
  return listen<ScanProgress>("scan://progress", (e) => cb(e.payload));
}
export function onScanDone(cb: (jobId: number) => void): Promise<UnlistenFn> {
  return listen<number>("scan://done", (e) => cb(e.payload));
}
export function onScanError(cb: (msg: string) => void): Promise<UnlistenFn> {
  return listen<string>("scan://error", (e) => cb(e.payload));
}

// ===== shared formatters =====
export function formatBytes(n: number): string {
  const u = ["B", "KB", "MB", "GB", "TB", "PB"];
  let i = 0;
  let v = n;
  while (v >= 1024 && i < u.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v.toFixed(i === 0 ? 0 : 1)} ${u[i]}`;
}
