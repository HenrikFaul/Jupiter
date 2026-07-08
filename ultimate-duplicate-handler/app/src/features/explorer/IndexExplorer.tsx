import { useEffect, useMemo, useRef, useState } from "react";
import { Settings2, FolderTree, FileText, ChevronDown, ChevronRight, Bookmark as BookmarkIcon } from "lucide-react";
import {
  IndexQuery,
  FileStatus,
  FileView,
  Integrity,
  SortKey,
  DeletionMode,
  DeletionOutcome,
  AffixPosition,
  searchIndex,
  listVolumes,
  revealInExplorer,
  deleteFiles,
  markFiles,
  startMediaAnalysis,
  cancelMediaAnalysis,
  VolumeView,
  previewFolderDeletion,
  deleteFolders,
  FolderDeletionPreview,
  Bookmark,
  getBookmarks,
  saveBookmark,
  deleteBookmark,
  CustomAction,
  getActions,
  runAction,
} from "../../lib/contract";
import { useAsync } from "../../lib/useAsync";
import { useStore } from "../../lib/store";
import { Card, OnlineChip, AsyncBoundary, bytes } from "../../components/ui";
import { usePathMenu } from "../../components/PathMenu";
import { FileTypePicker } from "../scan/FileTypePicker";
import { SizeFilter } from "../scan/SizeFilter";
import { IntegrityBadge, QualityBadge, fmtDuration, fmtResolution, fmtBitrate } from "../../components/MediaCells";

const EMPTY: IndexQuery = { exts: [], integrities: [], onlyDuplicates: false, onlyWarnings: false, onlyMedia: false, limit: 1000, offset: 0 };
const INTEGRITY_VALUES: Integrity[] = ["healthy", "suspicious", "partial", "corrupted", "unreadable"];

function dayStart(d: string): number | null {
  return d ? Math.floor(new Date(`${d}T00:00:00`).getTime() / 1000) : null;
}
function dayEnd(d: string): number | null {
  return d ? Math.floor(new Date(`${d}T23:59:59`).getTime() / 1000) : null;
}
function folderOf(abs: string): string {
  return abs.replace(/[\\/][^\\/]*$/, "") || abs;
}
function fmtScanned(epoch: number | null): string {
  return epoch ? new Date(epoch * 1000).toLocaleString() : "—";
}
/** Preview the renamed filename (mirrors Rust rename_service incl. affix.trim()). */
function markedName(name: string, affix: string, position: AffixPosition): string {
  const a = affix.trim();
  if (position === "prefix") return `${a}${name}`;
  const i = name.lastIndexOf(".");
  return i > 0 ? `${name.slice(0, i)}${a}${name.slice(i)}` : `${name}${a}`;
}
type ActionMode = DeletionMode | "rename";

// The persistent index, browsable even with no scan running. Checkbox-first file-type
// filtering (matching Scan), drive + full-path visibility, scanned timestamps, row actions
// (reveal / copy), and rich combinable filters mapped to indexed DB columns.
export function IndexExplorer() {
  const go = useStore((s) => s.go);
  const dataVersion = useStore((s) => s.dataVersion);
  const [query, setQuery] = useState<IndexQuery>(EMPTY);
  const [textDraft, setTextDraft] = useState("");
  const [showTypes, setShowTypes] = useState(false);
  const [copied, setCopied] = useState<string | null>(null);
  // Media analysis runs in the Rust background thread; its lifecycle lives in the store.
  const mediaRunning = useStore((s) => s.mediaRunning);
  const mediaProgress = useStore((s) => s.mediaProgress);
  const mediaResult = useStore((s) => s.mediaResult);
  const mediaError = useStore((s) => s.mediaError);
  const bumpData = useStore((s) => s.bumpData);
  // Row selection + bulk action (delete / rename) over the filtered result set.
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [actionMode, setActionMode] = useState<ActionMode>("recycle");
  const [affix, setAffix] = useState("tobedeleted");
  const [position, setPosition] = useState<AffixPosition>("suffix");
  const [busy, setBusy] = useState(false);
  const [actionMsg, setActionMsg] = useState<string | null>(null);
  // Advanced (parent-folder) delete: a previewed plan + which folders are included.
  const [advanced, setAdvanced] = useState(false);
  const [folderPreview, setFolderPreview] = useState<FolderDeletionPreview | null>(null);
  const [folderBusy, setFolderBusy] = useState(false);
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set());
  const [includedFolders, setIncludedFolders] = useState<Set<string>>(new Set());

  const volumes = useAsync(listVolumes, []);
  const results = useAsync(() => searchIndex(query), [query, dataVersion]);
  const { openMenu, menu } = usePathMenu();

  const rowIds = useMemo(() => (results.data ?? []).map((f) => f.fileId), [results.data]);
  // Reset selection whenever the result set changes (new filter / sort / refresh).
  useEffect(() => { setSelected(new Set()); setActionMsg(null); }, [query, dataVersion]);
  const allSelected = rowIds.length > 0 && selected.size === rowIds.length;
  const someSelected = selected.size > 0 && !allSelected;
  function toggleAll() { setSelected(allSelected ? new Set() : new Set(rowIds)); }
  function toggleOne(id: number) {
    setSelected((p) => { const n = new Set(p); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  }
  // Total size of the selected rows — "how much capacity you'll free up" (shown on select-all).
  const selectedBytes = useMemo(
    () => (results.data ?? []).reduce((a, r) => a + (selected.has(r.fileId) ? r.sizeBytes : 0), 0),
    [results.data, selected],
  );
  // Any selection change invalidates a previewed folder-deletion plan.
  useEffect(() => { setFolderPreview(null); }, [selected]);

  // Bulk action over the SELECTED rows. Deletions go through the engine-authoritative,
  // audited delete_files command (distinct files, default Recycle Bin); rename uses mark_files.
  async function runBulkAction() {
    const ids = [...selected];
    if (ids.length === 0) return;
    const isRename = actionMode === "rename";
    const what = isRename
      ? `rename ${ids.length} file(s) by adding "${affix.trim()}"`
      : `${actionMode === "permanent" ? "PERMANENTLY delete" : actionMode === "quarantine" ? "quarantine" : "send to the Recycle Bin"} ${ids.length} file(s)`;
    if (!window.confirm(`Are you sure you want to ${what}?`)) return;
    setBusy(true);
    setActionMsg(null);
    try {
      const res: DeletionOutcome = isRename
        ? await markFiles(ids, affix.trim(), position)
        : await deleteFiles(ids, actionMode as DeletionMode);
      setActionMsg(
        res.state === "success"
          ? isRename ? `Renamed ${res.removed} file(s).` : `Removed ${res.removed} file(s). Reclaimed ${bytes(res.reclaimedBytes)}.`
          : res.state === "partial"
            ? `Partial: ${isRename ? "renamed" : "removed"} ${res.removed}, ${res.failed} failed.`
            : `Action failed${res.failures[0] ? `: ${res.failures[0].reason}` : ""}.`,
      );
      setSelected(new Set());
      bumpData();
    } catch (e) {
      setActionMsg(`Action failed: ${String(e)}`);
    } finally {
      setBusy(false);
    }
  }

  // Multi-select integrity filter (req: checkboxes, not a single dropdown).
  function toggleIntegrity(v: Integrity) {
    setQuery((q) => {
      const cur = q.integrities ?? [];
      const next = cur.includes(v) ? cur.filter((x) => x !== v) : [...cur, v];
      return { ...q, integrities: next, integrity: null, offset: 0 };
    });
  }

  // Advanced (parent-folder) delete — preview which folders would be removed wholesale,
  // with the collateral and the engine's per-folder safety verdict.
  async function analyzeFolders() {
    const ids = [...selected];
    if (ids.length === 0) return;
    setFolderBusy(true);
    setActionMsg(null);
    try {
      const p = await previewFolderDeletion(ids);
      setFolderPreview(p);
      setIncludedFolders(new Set(p.groups.filter((g) => g.eligible).map((g) => g.folderAbs)));
      setExpandedFolders(new Set());
    } catch (e) {
      setActionMsg(`Folder preview failed: ${String(e)}`);
    } finally {
      setFolderBusy(false);
    }
  }

  async function runFolderDelete() {
    if (!folderPreview) return;
    const chosen = folderPreview.groups.filter((g) => g.eligible && includedFolders.has(g.folderAbs));
    if (chosen.length === 0) return;
    const mode = (actionMode === "rename" ? "recycle" : actionMode) as DeletionMode;
    const totalFiles = chosen.reduce((a, g) => a + g.recursiveFiles, 0);
    const totalBytes = chosen.reduce((a, g) => a + g.totalBytes, 0);
    const verb = mode === "permanent" ? "PERMANENTLY delete" : mode === "quarantine" ? "quarantine" : "send to the Recycle Bin";
    if (!window.confirm(
      `Advanced delete will ${verb} ${chosen.length} folder(s) and EVERYTHING inside them — ` +
      `${totalFiles.toLocaleString()} file(s), ${bytes(totalBytes)}. This includes files you did not individually select. Continue?`,
    )) return;
    setFolderBusy(true);
    setActionMsg(null);
    try {
      const res = await deleteFolders(chosen.map((g) => g.folderAbs), mode);
      setActionMsg(
        res.state === "success" ? `Deleted ${res.removed} folder(s). Reclaimed ${bytes(res.reclaimedBytes)}.`
        : res.state === "partial" ? `Partial: deleted ${res.removed} folder(s), ${res.failed} failed.`
        : `Folder delete failed${res.failures[0] ? `: ${res.failures[0].reason}` : ""}.`,
      );
      setFolderPreview(null);
      setAdvanced(false);
      setSelected(new Set());
      bumpData();
    } catch (e) {
      setActionMsg(`Folder delete failed: ${String(e)}`);
    } finally {
      setFolderBusy(false);
    }
  }

  function toggleExpand(folder: string) {
    setExpandedFolders((p) => { const n = new Set(p); if (n.has(folder)) n.delete(folder); else n.add(folder); return n; });
  }
  function toggleInclude(folder: string) {
    setIncludedFolders((p) => { const n = new Set(p); if (n.has(folder)) n.delete(folder); else n.add(folder); return n; });
  }
  const includedGroups = (folderPreview?.groups ?? []).filter((g) => g.eligible && includedFolders.has(g.folderAbs));
  const includedCount = includedGroups.length;
  const includedBytes = includedGroups.reduce((a, g) => a + g.totalBytes, 0);

  // Top horizontal scrollbar synced to the results table — sits right above the column
  // headers and stays put while you scroll rows, so it is ALWAYS reachable (req).
  const mainScrollRef = useRef<HTMLDivElement>(null);
  const topScrollRef = useRef<HTMLDivElement>(null);
  const tableRef = useRef<HTMLTableElement>(null);
  const syncing = useRef(false);
  const [contentWidth, setContentWidth] = useState(1180);
  useEffect(() => {
    if (tableRef.current) setContentWidth(tableRef.current.scrollWidth);
  }, [results.data, dataVersion, query]);
  function onTopScroll() {
    if (syncing.current || !mainScrollRef.current || !topScrollRef.current) return;
    syncing.current = true;
    mainScrollRef.current.scrollLeft = topScrollRef.current.scrollLeft;
    syncing.current = false;
  }
  function onMainScroll() {
    if (syncing.current || !mainScrollRef.current || !topScrollRef.current) return;
    syncing.current = true;
    topScrollRef.current.scrollLeft = mainScrollRef.current.scrollLeft;
    syncing.current = false;
  }

  // Bookmarks — save the current filter set as a named search and recall it with one click.
  const [bookmarks, setBookmarks] = useState<Bookmark[]>([]);
  useEffect(() => { getBookmarks().then(setBookmarks).catch(() => {}); }, []);
  async function saveCurrentSearch() {
    const name = window.prompt("Name this saved search:");
    if (!name || !name.trim()) return;
    try { setBookmarks(await saveBookmark(name.trim(), query)); }
    catch (e) { setActionMsg(`Bookmark failed: ${String(e)}`); }
  }
  function applyBookmark(b: Bookmark) {
    setTextDraft(b.query.text ?? "");
    setQuery({ ...b.query, offset: 0 });
  }
  async function removeBookmark(name: string) {
    try { setBookmarks(await deleteBookmark(name)); } catch { /* surfaced on next load */ }
  }

  // Custom actions ("plugins") — run a user-defined external tool on the selected files.
  const [customActions, setCustomActions] = useState<CustomAction[]>([]);
  const [chosenAction, setChosenAction] = useState("");
  useEffect(() => { getActions().then(setCustomActions).catch(() => {}); }, []);
  async function runChosenAction() {
    if (!chosenAction || selected.size === 0) return;
    const act = customActions.find((a) => a.name === chosenAction);
    if (!act) return;
    const ids = [...selected];
    if (!window.confirm(`Run "${act.name}" (${act.program} ${act.args}) on ${ids.length} file(s)?`)) return;
    try {
      const res = await runAction(chosenAction, ids);
      setActionMsg(res.failed === 0
        ? `Ran "${chosenAction}" on ${res.ran} file(s).`
        : `Ran on ${res.ran}, ${res.failed} failed${res.failures[0] ? `: ${res.failures[0]}` : ""}.`);
    } catch (e) { setActionMsg(`Action failed: ${String(e)}`); }
  }

  // Analyze is SCOPED to the rows currently in view (the user narrows via the filters above),
  // so it never kicks off an unbounded multi-thousand-file pass. Non-video rows in the scope
  // are ignored by the engine; already-analyzed ones are skipped.
  async function runAnalyze(deep: boolean) {
    const ids = (results.data ?? []).map((f) => f.fileId);
    if (ids.length === 0) return;
    try {
      await startMediaAnalysis(ids, deep);
    } catch (e) {
      useStore.setState({ mediaError: String(e) });
    }
  }

  // Debounce the substring filename search so typing doesn't spam queries.
  useEffect(() => {
    const t = setTimeout(() => setQuery((q) => ({ ...q, text: textDraft || null, offset: 0 })), 300);
    return () => clearTimeout(t);
  }, [textDraft]);

  const set = (patch: Partial<IndexQuery>) => setQuery((q) => ({ ...q, ...patch, offset: 0 }));
  const statuses: (FileStatus | "")[] = ["", "present", "missing", "deleted_by_user", "moved", "error"];

  // Column sorting (mapped to indexed DB columns server-side). Click a header to sort;
  // click again to flip direction. Dates/size default to descending, text to ascending.
  function sortBy(k: SortKey) {
    setQuery((q) => {
      if (q.sortKey === k) return { ...q, sortDir: q.sortDir === "asc" ? "desc" : "asc", offset: 0 };
      const defDir = k === "name" || k === "path" ? "asc" : "desc";
      return { ...q, sortKey: k, sortDir: defDir, offset: 0 };
    });
  }
  function Th({ label, k }: { label: string; k?: SortKey }) {
    const active = k && query.sortKey === k;
    return (
      <th className={`cell ${k ? "cursor-pointer select-none hover:text-accent" : ""}`}
        onClick={k ? () => sortBy(k) : undefined} title={k ? "Click to sort" : undefined}>
        {label}{active ? (query.sortDir === "asc" ? " ▲" : " ▼") : k ? " ⇅" : ""}
      </th>
    );
  }

  const volName = (id: number) => volumes.data?.find((v) => v.volumeId === id)?.label ?? `#${id}`;

  // Active filter chips (each removable) + a global clear.
  const chips = useMemo(() => {
    const c: { label: string; clear: () => void }[] = [];
    if (query.text) c.push({ label: `name contains "${query.text}"`, clear: () => { setTextDraft(""); set({ text: null }); } });
    if (query.volumeId != null) c.push({ label: `drive: ${volName(query.volumeId)}`, clear: () => set({ volumeId: null }) });
    if (query.exts.length) c.push({ label: `${query.exts.length} file type(s)`, clear: () => set({ exts: [] }) });
    if (query.status) c.push({ label: `status: ${query.status}`, clear: () => set({ status: null }) });
    if (query.onlyDuplicates) c.push({ label: "duplicates only", clear: () => set({ onlyDuplicates: false }) });
    if (query.integrities?.length) c.push({ label: `integrity: ${query.integrities.join(", ")}`, clear: () => set({ integrities: [] }) });
    else if (query.integrity) c.push({ label: `integrity: ${query.integrity}`, clear: () => set({ integrity: null }) });
    if (query.onlyWarnings) c.push({ label: "quality warnings only", clear: () => set({ onlyWarnings: false }) });
    if (query.onlyMedia) c.push({ label: "analyzed media only", clear: () => set({ onlyMedia: false }) });
    if (query.minSizeBytes || query.maxSizeBytes) c.push({ label: "size range", clear: () => set({ minSizeBytes: null, maxSizeBytes: null }) });
    if (query.scannedAfter || query.scannedBefore) c.push({ label: "scanned-time range", clear: () => set({ scannedAfter: null, scannedBefore: null }) });
    return c;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query, volumes.data]);

  async function copy(text: string, what: string) {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(what);
      setTimeout(() => setCopied(null), 1500);
    } catch {
      setCopied("clipboard blocked");
    }
  }

  return (
    <div className="p-6 space-y-4">
      <header>
        <h1 className="text-xl font-semibold">Index explorer</h1>
        <p className="text-muted">Search everything Singula has ever indexed — online, offline, or deleted — by name, drive, type, size, and scan time.</p>
      </header>

      {/* Saved searches (bookmarks) — recall a whole filter set with one click. */}
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-muted text-xs font-medium">Saved searches:</span>
        {bookmarks.length === 0 && <span className="text-muted text-xs italic">none yet — filter below, then save</span>}
        {bookmarks.map((b) => (
          <span key={b.name} className="chip flex items-center gap-1">
            <button className="hover:text-accent" onClick={() => applyBookmark(b)} title="Apply this saved search">{b.name}</button>
            <button aria-label={`delete saved search ${b.name}`} className="hover:text-danger" onClick={() => removeBookmark(b.name)}>✕</button>
          </span>
        ))}
        <button className="btn px-2 py-0.5 text-xs ml-auto" onClick={saveCurrentSearch} title="Save the current filters as a bookmark">
          <BookmarkIcon size={13} className="inline -mt-0.5 mr-1" />Save current search
        </button>
      </div>

      <Card>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-3 items-end">
          <label className="flex flex-col gap-1 md:col-span-2">Filename contains (substring)
            <input className="bg-surface-2 border border-border rounded px-3 py-1.5" value={textDraft}
              onChange={(e) => setTextDraft(e.target.value)} placeholder="ast  →  matches fast, astrology…" />
          </label>
          <label className="flex flex-col gap-1">Drive
            <select className="bg-surface-2 border border-border rounded px-2 py-1.5"
              value={query.volumeId ?? ""}
              onChange={(e) => set({ volumeId: e.target.value ? Number(e.target.value) : null })}>
              <option value="">any drive</option>
              {(volumes.data ?? []).map((v: VolumeView) => (
                <option key={v.volumeId} value={v.volumeId}>{v.label}{v.isOnline ? "" : " (offline)"}</option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1">Status
            <select className="bg-surface-2 border border-border rounded px-2 py-1.5" value={query.status ?? ""}
              onChange={(e) => set({ status: (e.target.value || null) as FileStatus | null })}>
              {statuses.map((s) => <option key={s} value={s}>{s || "any"}</option>)}
            </select>
          </label>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
          <div>
            <div className="font-medium mb-2">Size range</div>
            <SizeFilter minBytes={query.minSizeBytes ?? null} maxBytes={query.maxSizeBytes ?? null}
              onChange={(min, max) => set({ minSizeBytes: min, maxSizeBytes: max })} />
          </div>
          <div>
            <div className="font-medium mb-2">Scanned-time range</div>
            <div className="flex gap-3 items-end">
              <label className="flex flex-col gap-1 text-xs text-muted">From
                <input type="date" className="bg-surface-2 border border-border rounded px-2 py-1.5 text-text"
                  onChange={(e) => set({ scannedAfter: dayStart(e.target.value) })} />
              </label>
              <label className="flex flex-col gap-1 text-xs text-muted">To
                <input type="date" className="bg-surface-2 border border-border rounded px-2 py-1.5 text-text"
                  onChange={(e) => set({ scannedBefore: dayEnd(e.target.value) })} />
              </label>
            </div>
            <label className="flex items-center gap-2 mt-3">
              <input type="checkbox" checked={query.onlyDuplicates} onChange={(e) => set({ onlyDuplicates: e.target.checked })} />
              Only files that are duplicates
            </label>
          </div>
        </div>

        <div className="mt-4">
          <button className="btn" onClick={() => setShowTypes((v) => !v)}>
            {showTypes ? "▾" : "▸"} File types {query.exts.length ? `(${query.exts.length} selected)` : "(all)"}
          </button>
          {showTypes && (
            <div className="mt-3">
              <FileTypePicker value={query.exts} onChange={(exts) => set({ exts })} />
            </div>
          )}
        </div>

        {/* Media integrity & quality — filters + the ffprobe-backed analysis trigger */}
        <div className="mt-4 pt-3 border-t border-border">
          <div className="flex flex-wrap items-end gap-3">
            <div className="flex flex-col gap-1 text-xs text-muted">
              <span>Integrity {query.integrities?.length ? `(${query.integrities.length})` : ""}</span>
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                {INTEGRITY_VALUES.map((v) => (
                  <label key={v} className="flex items-center gap-1.5 text-text">
                    <input type="checkbox" checked={(query.integrities ?? []).includes(v)} onChange={() => toggleIntegrity(v)} />
                    {v}
                  </label>
                ))}
              </div>
            </div>
            <label className="flex items-center gap-2"><input type="checkbox" checked={query.onlyWarnings}
              onChange={(e) => set({ onlyWarnings: e.target.checked })} />Quality warnings only</label>
            <label className="flex items-center gap-2"><input type="checkbox" checked={query.onlyMedia}
              onChange={(e) => set({ onlyMedia: e.target.checked })} />Analyzed media only</label>
            <div className="ml-auto flex items-center gap-2">
              {!mediaRunning ? (
                <>
                  <button className="btn btn-primary" disabled={(results.data?.length ?? 0) === 0}
                    onClick={() => runAnalyze(false)}
                    title="Analyze the rows shown for integrity & quality — media via ffprobe (duration/resolution/bitrate); other file types get a read/complete integrity check. Scoped to your filter; already-analyzed files are skipped.">
                    Analyze these results ({results.data?.length ?? 0})
                  </button>
                  <button className="btn" disabled={(results.data?.length ?? 0) === 0}
                    onClick={() => runAnalyze(true)}
                    title="Slower: fully decodes each video to catch mid-file corruption (needs ffmpeg).">Deep check</button>
                </>
              ) : (
                <button className="btn danger" onClick={() => cancelMediaAnalysis().catch(() => {})}>Stop analysis</button>
              )}
            </div>
          </div>
          <p className="text-muted text-[11px] mt-2">
            Tip: narrow with the filters first (e.g. a drive, the video file types, or a size range), then analyze just those — analysis is scoped to the rows shown, so it always finishes.
          </p>
          {mediaRunning && mediaProgress && (
            <div className="mt-2">
              <div className="flex justify-between text-xs text-muted mb-1">
                <span>Analyzing… {mediaProgress.done.toLocaleString()} / {mediaProgress.total.toLocaleString()}</span>
                <span>{mediaProgress.warnings} warning(s) · {mediaProgress.corrupted} issue(s)</span>
              </div>
              <div className="h-1.5 rounded-full bg-surface-2 overflow-hidden">
                <div className="h-full bg-accent transition-all"
                  style={{ width: `${mediaProgress.total ? Math.round((mediaProgress.done / mediaProgress.total) * 100) : 100}%` }} />
              </div>
            </div>
          )}
          {!mediaRunning && mediaResult && (
            <div className={`text-xs mt-2 ${mediaResult.available ? "text-muted" : "text-warn"}`}>
              {mediaResult.available
                ? `${mediaResult.cancelled ? "Stopped early — " : ""}Analyzed ${mediaResult.newlyAnalyzed.toLocaleString()} file(s). ` +
                  `${mediaResult.totalMedia.toLocaleString()} have quality data · ${mediaResult.warnings} warning(s) · ${mediaResult.corrupted} integrity issue(s).`
                : mediaResult.message}
            </div>
          )}
          {mediaError && <div className="text-danger text-xs mt-2">Analysis error: {mediaError}</div>}
        </div>

        {/* Active filter chips + clear-all */}
        {chips.length > 0 && (
          <div className="flex flex-wrap gap-2 items-center mt-4 pt-3 border-t border-border">
            <span className="text-muted text-xs">Active:</span>
            {chips.map((c, i) => (
              <span key={i} className="chip flex items-center gap-1">
                {c.label}
                <button aria-label="remove filter" className="hover:text-danger" onClick={c.clear}>✕</button>
              </span>
            ))}
            <button className="btn ml-auto" onClick={() => { setTextDraft(""); setQuery(EMPTY); }}>Clear all</button>
          </div>
        )}
      </Card>

      <Card title={results.data ? `${results.data.length} result(s)${results.data.length >= query.limit ? ` (showing first ${query.limit})` : ""}` : "Results"}>
        {copied && <div className="text-ok text-xs mb-2">Copied {copied} to clipboard.</div>}

        {/* Bulk-action bar over the SELECTED rows (delete / rename), like the other views. */}
        {selected.size > 0 && (
          <div className="mb-3 rounded-lg border border-accent/40 bg-accent/5 p-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-medium">{selected.size} selected</span>
              <span className="text-muted text-xs" title="Total size of the selected files — the space you'll free up">
                · {bytes(selectedBytes)} total{allSelected ? " (all results)" : ""}
              </span>
              <button className="btn px-2 py-0.5 text-xs" onClick={() => setSelected(new Set())}>Clear</button>
              <span className="ml-2 text-muted text-xs">Action</span>
              <select className="bg-surface-2 border border-border rounded px-2 py-1 text-sm" value={actionMode}
                onChange={(e) => setActionMode(e.target.value as ActionMode)}>
                <option value="recycle">Recycle Bin (reversible)</option>
                <option value="quarantine">Quarantine (move aside)</option>
                <option value="permanent">Permanent (irreversible)</option>
                <option value="rename">Mark (rename, non-destructive)</option>
              </select>
              {actionMode === "rename" && (
                <>
                  <input className="bg-surface-2 border border-border rounded px-2 py-1 w-40 text-sm" value={affix}
                    onChange={(e) => setAffix(e.target.value)} placeholder="tobedeleted" />
                  <button className={`btn px-2 py-0.5 text-xs ${position === "suffix" ? "btn-primary" : ""}`} onClick={() => setPosition("suffix")}>Suffix</button>
                  <button className={`btn px-2 py-0.5 text-xs ${position === "prefix" ? "btn-primary" : ""}`} onClick={() => setPosition("prefix")}>Prefix</button>
                  <span className="text-muted text-xs font-mono">→ {markedName("movie.mp4", affix, position)}</span>
                </>
              )}
              <button className={`btn ${actionMode === "rename" ? "btn-primary" : "danger"}`}
                disabled={busy || (actionMode === "rename" && !affix.trim())} onClick={runBulkAction}>
                {busy ? "Working…" : actionMode === "rename" ? `Rename ${selected.size}` : `Delete ${selected.size}`}
              </button>
              {actionMode !== "rename" && (
                <button className={`btn px-2 py-1 ${advanced ? "btn-primary" : ""}`}
                  title="Advanced delete — also remove each file's parent folder and everything inside it"
                  aria-label="Advanced delete options" aria-pressed={advanced}
                  onClick={() => setAdvanced((v) => !v)}>
                  <Settings2 size={15} />
                </button>
              )}
              {customActions.length > 0 && (
                <>
                  <span className="text-muted text-xs ml-1">Run</span>
                  <select className="bg-surface-2 border border-border rounded px-2 py-1 text-sm" value={chosenAction}
                    onChange={(e) => setChosenAction(e.target.value)} title="Custom action (manage in Settings)">
                    <option value="">action…</option>
                    {customActions.map((a) => <option key={a.name} value={a.name}>{a.name}</option>)}
                  </select>
                  <button className="btn px-2 py-1" disabled={!chosenAction} onClick={runChosenAction}
                    title="Run this custom action on the selected files">Run</button>
                </>
              )}
              {actionMsg && <span className="text-xs ml-1">{actionMsg}</span>}
            </div>

            {advanced && actionMode !== "rename" && (
              <div className="mt-3 rounded-lg border border-warn/40 bg-warn/5 p-3 space-y-3">
                <div className="flex items-start gap-2 text-xs">
                  <FolderTree size={16} className="text-warn shrink-0 mt-0.5" />
                  <div>
                    <div className="font-medium text-text">Advanced delete — remove whole parent folders</div>
                    <div className="text-muted">
                      Deletes each selected file's containing folder and <span className="font-medium">everything inside it</span> (e.g. a movie's
                      own folder plus its <code>covers</code>/<code>sample</code> subfolders). Shared roots like Downloads, drive roots and protected
                      locations are detected and <span className="font-medium">excluded</span> so you never wipe a whole drive by accident.
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <button className="btn btn-primary px-3 py-1 text-xs" disabled={folderBusy} onClick={analyzeFolders}>
                    {folderBusy ? "Analyzing…" : folderPreview ? "Re-analyze parent folders" : "Analyze parent folders"}
                  </button>
                  {folderPreview && (
                    <span className="text-xs text-muted">
                      {folderPreview.eligibleFolders} deletable · {folderPreview.groups.length - folderPreview.eligibleFolders} excluded
                    </span>
                  )}
                </div>

                {folderPreview && folderPreview.groups.length === 0 && (
                  <div className="text-xs text-muted">No parent folders resolved for the selected files.</div>
                )}

                {folderPreview && folderPreview.groups.map((g) => {
                  const open = expandedFolders.has(g.folderAbs);
                  const included = includedFolders.has(g.folderAbs);
                  const badge = g.risk === "danger" ? "bg-danger/15 text-danger"
                    : g.risk === "caution" ? "bg-warn/15 text-warn" : "bg-ok/15 text-ok";
                  return (
                    <div key={g.folderAbs} className={`rounded-lg border bg-surface ${g.eligible ? "border-border" : "border-danger/40 opacity-80"}`}>
                      <div className="flex items-center gap-2 p-2">
                        <input type="checkbox" className="shrink-0" disabled={!g.eligible} checked={g.eligible && included}
                          aria-label={`include ${g.folderName}`} onChange={() => toggleInclude(g.folderAbs)} />
                        <button className="flex items-center gap-1.5 min-w-0 flex-1 text-left" onClick={() => toggleExpand(g.folderAbs)}>
                          {open ? <ChevronDown size={14} className="shrink-0" /> : <ChevronRight size={14} className="shrink-0" />}
                          <span className={`shrink-0 px-1.5 py-0.5 rounded text-[10px] font-medium ${badge}`}>{g.risk}</span>
                          <span className="font-mono text-xs truncate" title={g.folderAbs}>{g.folderAbs}</span>
                        </button>
                        <span className="text-xs text-muted whitespace-nowrap shrink-0">
                          {g.immediateEntries} item(s) · {bytes(g.totalBytes)}
                        </span>
                      </div>
                      <div className="px-2 pb-1.5 ml-6 text-[11px] text-muted">{g.riskReason}</div>
                      {open && (
                        <div className="px-2 pb-2 ml-6 space-y-0.5">
                          {g.sample.map((e, i) => (
                            <div key={i} className="flex items-center gap-2 text-xs">
                              {e.isDir ? <FolderTree size={12} className="text-muted shrink-0" /> : <FileText size={12} className="text-muted shrink-0" />}
                              <span className={`truncate ${e.isSelected ? "text-accent font-medium" : ""}`} title={e.name}>
                                {e.name}{e.isDir ? "\\" : ""}{e.isSelected ? "  ← selected" : ""}
                              </span>
                              <span className="ml-auto text-muted shrink-0">{e.isDir && e.sizeBytes === 0 ? "" : bytes(e.sizeBytes)}</span>
                            </div>
                          ))}
                          {g.immediateEntries > g.sample.length && (
                            <div className="text-[11px] text-muted">+ {g.immediateEntries - g.sample.length} more…</div>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}

                {folderPreview && folderPreview.eligibleFolders > 0 && (
                  <div className="flex items-center gap-2 pt-1">
                    <button className="btn danger" disabled={folderBusy || includedCount === 0} onClick={runFolderDelete}>
                      {folderBusy ? "Working…" : `Delete ${includedCount} folder(s) · ${bytes(includedBytes)}`}
                    </button>
                    <span className="text-xs text-muted">
                      mode: {actionMode === "permanent" ? "permanent (irreversible)" : actionMode === "quarantine" ? "quarantine" : "Recycle Bin"}
                    </span>
                  </div>
                )}
                {folderPreview && folderPreview.skipped.length > 0 && (
                  <div className="text-[11px] text-muted">{folderPreview.skipped.length} selected file(s) skipped (offline or missing).</div>
                )}
              </div>
            )}
          </div>
        )}
        {actionMsg && selected.size === 0 && <div className="text-ok text-xs mb-2">{actionMsg}</div>}

        <AsyncBoundary loading={results.loading} error={results.error} data={results.data} empty="No matching files in the index.">
          {(rows: FileView[]) => (
            <div className="rounded-lg border border-border">
              {/* Synced top horizontal scrollbar — sits right above the sticky headers and
                  stays put while you scroll rows, so it is always visible & reachable. */}
              <div ref={topScrollRef} onScroll={onTopScroll}
                className="overflow-x-auto overflow-y-hidden border-b border-border bg-surface-2"
                style={{ height: 14 }} aria-hidden>
                <div style={{ width: contentWidth, height: 1 }} />
              </div>
              <div ref={mainScrollRef} onScroll={onMainScroll} className="max-h-[68vh] overflow-auto">
                <table ref={tableRef} className="w-full min-w-[1180px] text-left">
                <thead className="text-muted text-xs sticky top-0 z-10 bg-surface">
                  <tr>
                    <th className="cell">
                      <input type="checkbox" aria-label="select all results" checked={allSelected}
                        ref={(el) => { if (el) el.indeterminate = someSelected; }} onChange={toggleAll} />
                    </th>
                    <Th label="Name" k="name" />
                    <Th label="Drive" />
                    <Th label="Full path" k="path" />
                    <Th label="Size" k="size" />
                    <Th label="Integrity" k="integrity" />
                    <Th label="Duration" k="duration" />
                    <Th label="Resolution" k="resolution" />
                    <Th label="Bitrate" k="bitrate" />
                    <Th label="Quality" k="quality" />
                    <Th label="Created" k="created" />
                    <Th label="Modified" k="modified" />
                    <Th label="Scanned" k="scanned" />
                    <Th label="Status" />
                    <Th label="Actions" />
                  </tr>
                </thead>
                <tbody>
                  {rows.map((f) => {
                    const name = f.pathRaw.split(/[\\/]/).pop() || f.pathRaw;
                    // Backend supplies absPath (online); for offline, compose with exactly
                    // one separator so we never produce "D:Secure" or "D:\\Secure".
                    const full = f.absPath ??
                      (f.drive ? `${f.drive.replace(/[\\/]+$/, "")}\\${f.pathRaw.replace(/^[\\/]+/, "")}` : f.pathRaw);
                    const sel = selected.has(f.fileId);
                    return (
                      <tr key={f.fileId} className={`${f.status !== "present" ? "opacity-70" : ""} ${sel ? "bg-accent/5" : ""}`}
                        onContextMenu={(e) => openMenu(e, full, f.volumeOnline)} title="Right-click for Open in Explorer / Copy path">
                        <td className="cell">
                          <input type="checkbox" checked={sel} aria-label={`select ${name}`}
                            onChange={() => toggleOne(f.fileId)} />
                        </td>
                        <td className="cell font-medium max-w-[24ch] truncate" title={name}>{name}</td>
                        <td className="cell whitespace-nowrap">{f.volumeLabel} <OnlineChip online={f.volumeOnline} /></td>
                        <td className="cell font-mono text-xs max-w-[40ch] truncate" title={full}>{full}</td>
                        <td className="cell whitespace-nowrap">{bytes(f.sizeBytes)}</td>
                        <td className="cell"><IntegrityBadge media={f.media} /></td>
                        <td className="cell text-xs whitespace-nowrap">{f.media?.durationS ? fmtDuration(f.media.durationS) : "—"}</td>
                        <td className="cell text-xs whitespace-nowrap" title={f.media?.codec ?? undefined}>{f.media?.width ? fmtResolution(f.media) : "—"}</td>
                        <td className="cell text-xs whitespace-nowrap">{fmtBitrate(f.media?.bitrate ?? null)}</td>
                        <td className="cell"><QualityBadge media={f.media} /></td>
                        <td className="cell text-xs text-muted whitespace-nowrap">{fmtScanned(f.createdAt)}</td>
                        <td className="cell text-xs text-muted whitespace-nowrap">{fmtScanned(f.modifiedAt)}</td>
                        <td className="cell text-xs text-muted whitespace-nowrap" title={`first indexed: ${fmtScanned(f.firstSeenAt)}`}>
                          {fmtScanned(f.lastSeenAt)}
                          {f.lastScanJobId != null && (
                            <button className="ml-1 text-accent hover:underline" title="view scan session" onClick={() => go("scanlog")}>
                              #{f.lastScanJobId}
                            </button>
                          )}
                        </td>
                        <td className="cell text-xs">{f.status}</td>
                        <td className="cell whitespace-nowrap">
                          <div className="flex gap-1">
                            <button className="btn px-2 py-0.5 text-xs" disabled={!f.absPath || !f.volumeOnline}
                              title={f.volumeOnline ? "Open in Explorer" : "drive offline"}
                              onClick={() => f.absPath && revealInExplorer(f.absPath).catch(() => {})}>Reveal</button>
                            <button className="btn px-2 py-0.5 text-xs" title="Copy full path"
                              onClick={() => copy(full, "full path")}>Copy</button>
                            <button className="btn px-2 py-0.5 text-xs" title="Copy folder path"
                              onClick={() => copy(folderOf(full), "folder path")}>Folder</button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
                </table>
              </div>
            </div>
          )}
        </AsyncBoundary>
      </Card>
      {menu}
    </div>
  );
}
