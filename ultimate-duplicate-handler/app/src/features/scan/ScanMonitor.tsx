import { useEffect, useMemo, useState } from "react";
import { cancelScan, scanIsActive, ScanLogEvent } from "../../lib/contract";
import { useStore } from "../../lib/store";
import { Card, Stat, bytes } from "../../components/ui";
import { FolderTree } from "./FolderTree";

const STAGES = ["enumerating", "hashing", "clustering", "done"];

function fmtClock(epochSec: number): string {
  return new Date(epochSec * 1000).toLocaleTimeString();
}
function fmtElapsed(ms: number): string {
  const s = Math.floor(ms / 1000);
  return `${Math.floor(s / 60)}m ${String(s % 60).padStart(2, "0")}s`;
}
function kindClass(kind: string): string {
  switch (kind) {
    case "completed": return "text-ok";
    case "cancelled": case "interrupted": return "text-warn";
    case "failed": case "warning": return "text-danger";
    case "progress": return "text-accent";
    case "folder": return "text-muted";
    default: return "text-text";
  }
}

export function ScanMonitor() {
  const progress = useStore((s) => s.progress);
  const running = useStore((s) => s.scanRunning);
  const error = useStore((s) => s.scanError);
  const scanLog = useStore((s) => s.scanLog);
  const go = useStore((s) => s.go);
  const [stopping, setStopping] = useState(false);
  const [logOpen, setLogOpen] = useState(true);
  const [treeOpen, setTreeOpen] = useState(true);

  useEffect(() => {
    scanIsActive().then((active) => { if (!active) useStore.setState({ scanRunning: false }); }).catch(() => {});
  }, []);
  useEffect(() => { if (!running) setStopping(false); }, [running]);

  const stageIdx = progress ? STAGES.indexOf(progress.stage) : -1;
  const latest: ScanLogEvent | undefined = scanLog[scanLog.length - 1];
  const filesProcessed = progress?.filesSeen ?? latest?.filesProcessed ?? 0;
  const completedFolders = useMemo(() => scanLog.filter((e) => e.kind === "folder"), [scanLog]);

  async function stop() {
    setStopping(true);
    await cancelScan();
    setTimeout(() => {
      scanIsActive().then((a) => !a && useStore.setState({ scanRunning: false })).catch(() => {});
    }, 800);
  }

  return (
    <div className="p-6 space-y-5 max-w-4xl">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">Scan monitor</h1>
          <p className="text-muted">{running ? "A scan is in progress." : "No scan running."}</p>
        </div>
        <div className="flex gap-2">
          {running && (
            <button className="btn danger" onClick={stop} disabled={stopping}>
              {stopping ? "Stopping…" : "Stop scan"}
            </button>
          )}
          {!running && progress?.stage === "done" && (
            <button className="btn btn-primary" onClick={() => go("results")}>View duplicates →</button>
          )}
          {!running && <button className="btn" onClick={() => go("scan")}>+ New scan</button>}
          <button className="btn" onClick={() => go("scanlog")}>Scan history</button>
        </div>
      </header>

      {error && (
        <div role="alert" className="rounded-lg border border-danger/50 bg-surface p-4 text-danger shadow-sm">
          Scan failed: {error}
        </div>
      )}
      {stopping && (
        <div className="rounded-lg border border-warn/50 bg-surface p-3 text-warn text-sm shadow-sm">
          Stopping… the scan finishes its current chunk and stops within a moment.
        </div>
      )}

      {/* Stage pipeline */}
      <Card title="Pipeline">
        <ol className="flex gap-2">
          {STAGES.map((s, i) => (
            <li key={s} className={`flex-1 text-center py-2 rounded-lg border ${
              i < stageIdx ? "border-ok text-ok bg-ok/5" : i === stageIdx ? "border-accent text-accent bg-accent/5" : "border-border text-muted"
            }`}>
              {i < stageIdx ? "✓ " : i === stageIdx ? "● " : "○ "}{s}
            </li>
          ))}
        </ol>
      </Card>

      {progress && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Stat label="Files seen" value={progress.filesSeen.toLocaleString()} />
          <Stat label="Files hashed" value={progress.filesHashed.toLocaleString()} tone="accent" />
          <Stat label="Bytes hashed" value={bytes(progress.bytesHashed)} />
          <Stat label="Candidate groups" value={progress.candidateGroups.toLocaleString()} />
        </div>
      )}

      {/* Collapsible scan telemetry log — always shows the latest line + files processed,
          even collapsed (so the user can trust the scan is alive and moving). */}
      <div className="rounded-xl border border-border bg-surface shadow-sm overflow-hidden">
        <button
          className="w-full flex items-center gap-3 px-4 py-2.5 text-left hover:bg-surface-2"
          onClick={() => setLogOpen((o) => !o)}
          aria-expanded={logOpen}
        >
          <span aria-hidden className="text-muted">{logOpen ? "▾" : "▸"}</span>
          <span className="font-medium">Scan log</span>
          {/* Always-visible compact status, even when collapsed */}
          <span className={`truncate ${latest ? kindClass(latest.kind) : "text-muted"}`}>
            {latest ? latest.message : "Waiting for scan activity…"}
          </span>
          <span className="ml-auto chip" title="files processed so far">
            {filesProcessed.toLocaleString()} files
          </span>
        </button>

        {logOpen && (
          <div className="border-t border-border max-h-72 overflow-auto px-2 py-2 font-mono text-xs">
            {scanLog.length === 0 ? (
              <p className="text-muted px-2 py-1">No log entries yet. They appear once a scan starts (and at least every minute while it runs).</p>
            ) : (
              <ul className="space-y-0.5">
                {scanLog.slice().reverse().map((e, i) => (
                  <li key={`${e.at}-${i}`} className="flex gap-2">
                    <span className="text-muted shrink-0 w-16">{fmtClock(e.at)}</span>
                    <span className="text-muted shrink-0 w-14">{fmtElapsed(e.elapsedMs)}</span>
                    <span className={`shrink-0 w-20 ${kindClass(e.kind)}`}>{e.kind}</span>
                    <span className="truncate">{e.message}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>

      {/* Folder completion visibility (live). Full structured state is in Scan history. */}
      <Card title={`Folders completed (${completedFolders.length})`}>
        {completedFolders.length === 0 ? (
          <p className="text-muted">Completed folders appear here as the scanner traverses them (subfolders included).</p>
        ) : (
          <ul className="max-h-60 overflow-auto space-y-0.5 font-mono text-xs">
            {completedFolders.slice().reverse().slice(0, 300).map((e, i) => (
              <li key={`${e.at}-${i}`} className="flex gap-2 items-center">
                <span className="text-ok">✓</span>
                <span className="truncate">{e.message.replace(/^Completed:\s*/, "")}</span>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {/* Classic, collapsible folder-structure tree of everything indexed (size rolls up). */}
      <div className="rounded-xl border border-border bg-surface shadow-sm overflow-hidden">
        <button className="w-full flex items-center gap-3 px-4 py-2.5 text-left hover:bg-surface-2"
          onClick={() => setTreeOpen((o) => !o)} aria-expanded={treeOpen}>
          <span aria-hidden className="text-muted">{treeOpen ? "▾" : "▸"}</span>
          <span className="font-medium">Folder structure</span>
          <span className="text-muted text-xs">browse the indexed tree — sizes &amp; counts roll up recursively</span>
        </button>
        {treeOpen && <div className="border-t border-border p-3"><FolderTree /></div>}
      </div>

      {!progress && scanLog.length === 0 && (
        <p className="text-muted">Progress will appear here once a scan starts. Start one from “New Scan”.</p>
      )}
    </div>
  );
}
