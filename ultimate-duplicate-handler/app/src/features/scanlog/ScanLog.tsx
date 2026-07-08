import { useMemo, useState } from "react";
import {
  ScanSessionView,
  FolderState,
  getScanSession,
  listScanSessions,
  deleteScanSession,
  clearScanHistory,
} from "../../lib/contract";
import { useAsync } from "../../lib/useAsync";
import { useStore } from "../../lib/store";
import { Card, AsyncBoundary, bytes } from "../../components/ui";

type SortKey = "recent" | "files" | "duration" | "duplicates";

function fmtDate(epoch: number): string {
  return new Date(epoch * 1000).toLocaleString();
}
function fmtDur(ms: number): string {
  const s = Math.round(ms / 1000);
  if (s < 60) return `${s}s`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ${s % 60}s`;
  return `${Math.floor(m / 60)}h ${m % 60}m`;
}
function stateTone(state: string): string {
  if (state === "done") return "text-ok";
  if (state === "cancelled" || state === "paused") return "text-warn";
  if (state === "failed") return "text-danger";
  return "text-accent";
}
function folderTone(s: FolderState): string {
  return s === "completed" ? "text-ok" : s === "failed" ? "text-danger" : s === "skipped" ? "text-warn" : "text-muted";
}

// The historical memory of the scan engine: every completed/interrupted scan session,
// sortable + filterable, with a drill-down into its timeline + folder traversal.
export function ScanLog() {
  const dataVersion = useStore((s) => s.dataVersion);
  const [sort, setSort] = useState<SortKey>("recent");
  const [stateFilter, setStateFilter] = useState<string>("");
  const [selected, setSelected] = useState<number | null>(null);
  const [ver, setVer] = useState(0);

  const sessions = useAsync(() => listScanSessions(500), [dataVersion, ver]);

  async function deleteOne(jobId: number) {
    try {
      await deleteScanSession(jobId);
      if (selected === jobId) setSelected(null);
      setVer((v) => v + 1);
    } catch { /* surfaced by reload */ }
  }
  async function clearAll() {
    if (!confirm("Delete the ENTIRE scan history? Indexed files are NOT affected.")) return;
    try {
      await clearScanHistory();
      setSelected(null);
      setVer((v) => v + 1);
    } catch { /* surfaced by reload */ }
  }

  const rows = useMemo(() => {
    let list = sessions.data ?? [];
    if (stateFilter) list = list.filter((s) => s.state === stateFilter);
    const sorted = [...list];
    sorted.sort((a, b) => {
      if (sort === "files") return b.filesSeen - a.filesSeen;
      if (sort === "duration") return b.durationMs - a.durationMs;
      if (sort === "duplicates") return b.duplicatesFound - a.duplicatesFound;
      return b.startedAt - a.startedAt;
    });
    return sorted;
  }, [sessions.data, sort, stateFilter]);

  return (
    <div className="p-6 space-y-4">
      <header>
        <h1 className="text-xl font-semibold">Scan history</h1>
        <p className="text-muted">A durable record of every scan: what it covered, how long it took, and what it found.</p>
      </header>

      <div className="flex flex-wrap gap-2 items-center">
        <label className="text-muted text-xs">Sort</label>
        <select className="bg-surface-2 border border-border rounded px-2 py-1" value={sort} onChange={(e) => setSort(e.target.value as SortKey)}>
          <option value="recent">Most recent</option>
          <option value="files">Most files</option>
          <option value="duration">Longest</option>
          <option value="duplicates">Most duplicates</option>
        </select>
        <label className="text-muted text-xs ml-2">State</label>
        <select className="bg-surface-2 border border-border rounded px-2 py-1" value={stateFilter} onChange={(e) => setStateFilter(e.target.value)}>
          <option value="">any</option>
          <option value="done">done</option>
          <option value="cancelled">cancelled</option>
          <option value="failed">failed</option>
        </select>
        {(sessions.data?.length ?? 0) > 0 && (
          <button className="btn danger ml-auto" onClick={clearAll}>Clear all history</button>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card title="Sessions" className="lg:col-span-1">
          <AsyncBoundary loading={sessions.loading} error={sessions.error} data={rows} empty="No scans recorded yet.">
            {(list: ScanSessionView[]) => (
              <ul className="space-y-1 max-h-[64vh] overflow-auto">
                {list.map((s) => (
                  <li key={s.jobId} className={`group rounded flex items-stretch ${selected === s.jobId ? "bg-surface-2" : "hover:bg-surface-2"}`}>
                    <button onClick={() => setSelected(s.jobId)} className="flex-1 text-left px-2 py-1.5">
                      <div className="flex justify-between items-center">
                        <span className="font-medium">#{s.jobId} · {s.driveLabel ?? "scan"}</span>
                        <span className={`text-xs ${stateTone(s.state)}`}>{s.state}</span>
                      </div>
                      <div className="text-muted text-xs">
                        {fmtDate(s.startedAt)} · {s.filesSeen.toLocaleString()} files · {fmtDur(s.durationMs)}
                        {s.resumable && <span className="chip ml-1">resumable</span>}
                      </div>
                    </button>
                    <button title="Delete this scan from history"
                      className="px-2 text-muted hover:text-danger opacity-0 group-hover:opacity-100"
                      onClick={() => deleteOne(s.jobId)}>✕</button>
                  </li>
                ))}
              </ul>
            )}
          </AsyncBoundary>
        </Card>

        <div className="lg:col-span-2">
          {selected == null ? (
            <Card><p className="text-muted">Select a scan to see its full outcome, timeline, and folder traversal.</p></Card>
          ) : (
            <SessionDetail jobId={selected} key={selected} />
          )}
        </div>
      </div>
    </div>
  );
}

function SessionDetail({ jobId }: { jobId: number }) {
  const detail = useAsync(() => getScanSession(jobId), [jobId]);
  const [tab, setTab] = useState<"timeline" | "folders">("timeline");

  if (detail.loading) return <Card><p className="text-muted">Loading…</p></Card>;
  if (detail.error || !detail.data) return <Card><p className="text-danger">Failed to load: {detail.error}</p></Card>;
  const { session: s, events, folders } = detail.data;

  return (
    <div className="space-y-4">
      <Card title={`Scan #${s.jobId} · ${s.state}`}>
        <div className="grid grid-cols-2 md:grid-cols-3 gap-3 text-sm">
          <Field label="Drive" value={s.driveLabel ?? "—"} />
          <Field label="Mode" value={s.mode ?? "—"} />
          <Field label="Started" value={fmtDate(s.startedAt)} />
          <Field label="Ended" value={s.finishedAt ? fmtDate(s.finishedAt) : "—"} />
          <Field label="Duration" value={fmtDur(s.durationMs)} />
          <Field label="Files processed" value={s.filesSeen.toLocaleString()} />
          <Field label="Files hashed" value={s.filesHashed.toLocaleString()} />
          <Field label="Data scanned" value={bytes(s.totalBytes)} />
          <Field label="Duplicates found" value={s.duplicatesFound.toLocaleString()} />
          <Field label="Folders" value={`${s.foldersTraversed} (${s.subfoldersTraversed} sub)`} />
          <Field label="Skipped" value={s.skippedCount.toLocaleString()} />
          <Field label="Errors" value={s.errorCount.toLocaleString()} />
          <Field label="Scan time" value={fmtDur(s.scanningMs)} />
          <Field label="Hash time" value={fmtDur(s.hashingMs)} />
          <Field label="Resumable" value={s.resumable ? "yes" : "no"} />
          <Field label="Build" value={s.buildVersion ?? "—"} />
        </div>
        {s.sourcesJson && (
          <details className="mt-2 text-xs">
            <summary className="text-muted cursor-pointer">Sources</summary>
            <pre className="mt-1 whitespace-pre-wrap font-mono">{s.sourcesJson}</pre>
          </details>
        )}
      </Card>

      <Card>
        <div className="flex gap-2 mb-3">
          <button className={`btn ${tab === "timeline" ? "border-accent text-accent" : ""}`} onClick={() => setTab("timeline")}>Timeline ({events.length})</button>
          <button className={`btn ${tab === "folders" ? "border-accent text-accent" : ""}`} onClick={() => setTab("folders")}>Folders ({folders.length})</button>
        </div>

        {tab === "timeline" ? (
          <ul className="max-h-80 overflow-auto space-y-0.5 font-mono text-xs">
            {events.map((e, i) => (
              <li key={i} className="flex gap-2">
                <span className="text-muted shrink-0 w-32">{fmtDate(e.at)}</span>
                <span className={`shrink-0 w-20 ${stateTone(e.kind)}`}>{e.kind}</span>
                <span className="truncate">{e.message}</span>
              </li>
            ))}
            {events.length === 0 && <li className="text-muted">No timeline events recorded.</li>}
          </ul>
        ) : (
          <ul className="max-h-80 overflow-auto space-y-0.5 font-mono text-xs">
            {folders.map((f, i) => (
              <li key={i} className="flex gap-2 items-center" style={{ paddingLeft: `${Math.min(f.depth, 8) * 12}px` }}>
                <span className={folderTone(f.state)}>{f.state === "completed" ? "✓" : f.state === "failed" ? "✕" : f.state === "skipped" ? "⊘" : "•"}</span>
                <span className="truncate flex-1">{f.folderPathRaw || "(root)"}</span>
                <span className="text-muted shrink-0">{f.fileCount} files</span>
                {f.durationMs > 0 && <span className="text-muted shrink-0">{fmtDur(f.durationMs)}</span>}
              </li>
            ))}
            {folders.length === 0 && <li className="text-muted">No folder traversal recorded (e.g. an MFT-accelerated whole-volume scan).</li>}
          </ul>
        )}
      </Card>
    </div>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-muted text-xs">{label}</div>
      <div className="font-medium">{value}</div>
    </div>
  );
}
