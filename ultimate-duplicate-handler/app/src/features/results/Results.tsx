import { useMemo, useState } from "react";
import {
  ClusterSort,
  ClusterView,
  FileView,
  SelectionRule,
  AffixPosition,
  applySelectionRules,
  getPresence,
  queryClusters,
  revealInExplorer,
} from "../../lib/contract";
import { useAsync } from "../../lib/useAsync";
import { useStore } from "../../lib/store";
import { Card, OnlineChip, AsyncBoundary, bytes } from "../../components/ui";
import { DeletionReview, ActionMode } from "../review/DeletionReview";
import { IntegrityBadge, QualityBadge, recommendKeeper, mediaVaries } from "../../components/MediaCells";

function fullPathOf(m: FileView): string {
  return m.absPath ?? (m.drive ? `${m.drive.replace(/[\\/]+$/, "")}\\${m.pathRaw.replace(/^[\\/]+/, "")}` : m.pathRaw);
}

function baseName(m: FileView): string {
  return m.pathRaw.split(/[\\/]/).pop() || m.pathRaw;
}

/** Preview the renamed filename (mirrors the Rust rename_service logic, including its
 *  `affix.trim()`) so the user sees EXACTLY what a marked file becomes. */
function markedName(name: string, affix: string, position: AffixPosition): string {
  const a = affix.trim(); // the engine trims the marker — match it so the preview is truthful
  if (position === "prefix") return `${a}${name}`;
  const i = name.lastIndexOf(".");
  return i > 0 ? `${name.slice(0, i)}${a}${name.slice(i)}` : `${name}${a}`;
}

export function Results() {
  const dataVersion = useStore((s) => s.dataVersion);
  const bumpData = useStore((s) => s.bumpData);
  const [sort, setSort] = useState<ClusterSort>("reclaimable");
  const [selected, setSelected] = useState<number | null>(null);
  const [decisions, setDecisions] = useState<Map<number, boolean>>(new Map());
  const [mode, setMode] = useState<ActionMode>("recycle");
  const [affix, setAffix] = useState("tobedeleted");
  const [position, setPosition] = useState<AffixPosition>("suffix");
  const [ruleError, setRuleError] = useState<string | null>(null);
  const [copied, setCopied] = useState<string | null>(null);

  async function copyPath(text: string) {
    try { await navigator.clipboard.writeText(text); setCopied(text); setTimeout(() => setCopied(null), 1500); }
    catch { setCopied("clipboard blocked"); }
  }

  const clusters = useAsync(() => queryClusters(sort, 500, 0), [sort, dataVersion]);

  const selectedCluster = useMemo(
    () => clusters.data?.find((c) => c.clusterId === selected) ?? null,
    [clusters.data, selected],
  );

  function setDecision(fileId: number, keep: boolean) {
    setDecisions((prev) => new Map(prev).set(fileId, keep));
  }

  // Smart selection runs in the RUST engine (authoritative), returning a keep/remove
  // preview that we merge into the decisions map. The UI never decides keepers itself.
  async function applyEngineRules(rules: SelectionRule[]) {
    setRuleError(null);
    try {
      const proposed = await applySelectionRules(rules);
      const next = new Map<number, boolean>();
      // Default everything to keep, then apply the engine's per-file decisions.
      for (const c of clusters.data ?? []) c.members.forEach((m) => next.set(m.fileId, true));
      for (const d of proposed) next.set(d.fileId, d.keep);
      setDecisions(next);
    } catch (e) {
      setRuleError(String(e)); // fail loud (§2)
    }
  }

  function resetKeepAll() {
    const next = new Map<number, boolean>();
    for (const c of clusters.data ?? []) c.members.forEach((m) => next.set(m.fileId, true));
    setDecisions(next);
  }

  return (
    <div className="p-6 space-y-4">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">Duplicates</h1>
          <p className="text-muted">Review clusters, choose what to keep, and reclaim space safely.</p>
        </div>
        <div className="flex gap-2 items-center">
          <label className="text-muted text-xs">Sort</label>
          <select className="bg-surface-2 border border-border rounded px-2 py-1" value={sort}
            onChange={(e) => setSort(e.target.value as ClusterSort)}>
            <option value="reclaimable">Reclaimable space</option>
            <option value="size">File size</option>
            <option value="memberCount">Group size</option>
          </select>
        </div>
      </header>

      <div className="flex gap-2 flex-wrap items-center">
        <span className="text-muted text-xs">Smart selection:</span>
        <button className="btn" onClick={() => applyEngineRules([{ kind: "keepNewest" }])}>Keep newest</button>
        <button className="btn" onClick={() => applyEngineRules([{ kind: "keepOldest" }])}>Keep oldest</button>
        <button className="btn" onClick={() => applyEngineRules([{ kind: "preferOnline" }, { kind: "keepNewest" }])}>Keep one online copy</button>
        <button className="btn" onClick={() => applyEngineRules([{ kind: "keepShortestPath" }])}>Keep shortest path</button>
        <button className="btn" onClick={resetKeepAll}>Reset (keep all)</button>
        <span className="ml-auto text-muted text-xs">Action</span>
        <select className="bg-surface-2 border border-border rounded px-2 py-1" value={mode}
          onChange={(e) => setMode(e.target.value as ActionMode)}>
          <option value="recycle">Recycle Bin (reversible)</option>
          <option value="quarantine">Quarantine (move aside)</option>
          <option value="permanent">Permanent (irreversible)</option>
          <option value="rename">Mark (rename, non-destructive)</option>
        </select>
      </div>
      {ruleError && <div role="alert" className="text-danger text-sm">Selection failed: {ruleError}</div>}

      {mode !== "rename" && (
        <p className="text-muted text-xs">
          Prefer to mark instead of delete? Set <strong>Action → “Mark (rename)”</strong> to add a tag like{" "}
          <span className="font-mono bg-surface-2 px-1 rounded">tobedeleted</span> to the filenames — nothing is deleted.
        </p>
      )}

      {/* Rename/mark options — only when the "Mark" action is chosen */}
      {mode === "rename" && (
        <div className="flex flex-wrap items-center gap-2 rounded-lg border border-border bg-surface p-3 text-sm shadow-sm">
          <span className="text-muted">Marker text</span>
          <input className="bg-surface-2 border border-border rounded px-2 py-1 w-44" value={affix}
            onChange={(e) => setAffix(e.target.value)} placeholder="tobedeleted" />
          <span className="text-muted ml-2">Position</span>
          <button className={`btn ${position === "suffix" ? "btn-primary" : ""}`} onClick={() => setPosition("suffix")}>Suffix</button>
          <button className={`btn ${position === "prefix" ? "btn-primary" : ""}`} onClick={() => setPosition("prefix")}>Prefix</button>
          <span className="text-muted text-xs ml-2">
            e.g. <span className="font-mono">{markedName("movie.mp4", affix, position)}</span> — nothing is deleted, files are only renamed.
          </span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Cluster list */}
        <Card title="Duplicate groups" className="lg:col-span-1">
          <AsyncBoundary loading={clusters.loading} error={clusters.error} data={clusters.data}
            empty="No duplicates found yet. Run a scan to populate this.">
            {(rows: ClusterView[]) => (
              <ul className="space-y-1 max-h-[60vh] overflow-auto">
                {/* Production note: wrap in @tanstack/react-virtual for million-row lists. */}
                {rows.map((c) => (
                  <li key={c.clusterId}>
                    <button onClick={() => setSelected(c.clusterId)}
                      className={`w-full text-left px-2 py-1 rounded ${selected === c.clusterId ? "bg-surface-2" : "hover:bg-surface-2"}`}>
                      <div className="flex justify-between">
                        <span className="font-mono text-xs truncate">{c.members[0]?.pathRaw ?? `content #${c.contentId}`}</span>
                        <span className="text-warn text-xs">{bytes(c.reclaimableBytes)}</span>
                      </div>
                      <div className="text-muted text-xs">{c.memberCount} copies · {bytes(c.sizeBytes)} each · {c.confidence}</div>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </AsyncBoundary>
        </Card>

        {/* Inspector */}
        <div className="lg:col-span-2 space-y-4">
          {copied && <div className="text-ok text-xs">Copied to clipboard: <span className="font-mono">{copied}</span></div>}
          {selectedCluster ? (
            <ClusterInspector cluster={selectedCluster} decisions={decisions} onDecide={setDecision} onCopy={copyPath}
              mode={mode} affix={affix} position={position} />
          ) : (
            <Card><p className="text-muted">Select a group to inspect its copies, full paths, and history.</p></Card>
          )}

          {clusters.data && clusters.data.length > 0 && (
            <DeletionReview clusters={clusters.data} decisions={decisions} mode={mode} affix={affix} position={position} onDone={() => bumpData()} />
          )}
        </div>
      </div>
    </div>
  );
}

function ClusterInspector({
  cluster,
  decisions,
  onDecide,
  onCopy,
  mode,
  affix,
  position,
}: {
  cluster: ClusterView;
  decisions: Map<number, boolean>;
  onDecide: (fileId: number, keep: boolean) => void;
  onCopy: (text: string) => void;
  mode: ActionMode;
  affix: string;
  position: AffixPosition;
}) {
  const presence = useAsync(() => getPresence(cluster.contentId), [cluster.contentId]);
  // Keeper-by-quality only makes sense when the copies actually differ technically (exact
  // byte-duplicates are identical media, so we suppress the badge for them).
  const keeperId = mediaVaries(cluster.members) ? recommendKeeper(cluster.members) : null;

  // Make the action explicit & unambiguous (the old "Keep" checkbox confused users — they
  // read unchecking as deselecting, yet the action count grew). We now show a clear
  // per-row Keep | Rename/Delete toggle, highlight the rows that WILL be touched, and
  // preview the exact renamed filename.
  const isRename = mode === "rename";
  const actVerb = isRename ? "Rename" : "Delete";
  const actActive = isRename ? "bg-warn/20 text-warn" : "bg-danger/20 text-danger";
  const rowMarkBg = isRename ? "bg-warn/5" : "bg-danger/5";
  const present = cluster.members.filter((m) => m.status === "present");
  const markedCount = present.filter((m) => decisions.get(m.fileId) === false).length;
  const keptCount = present.length - markedCount;

  return (
    <Card title={`Group · ${cluster.memberCount} copies · ${bytes(cluster.sizeBytes)} each`}>
      {presence.data && (
        <div className="mb-3 text-sm">
          <span className={`chip ${presence.data.onlineCopies >= 2 ? "online" : "offline"}`}>{presence.data.verdict}</span>
          <span className="text-muted ml-2">
            {presence.data.onlineCopies} online · {presence.data.offlineCopies} offline · {presence.data.deletedCopies} previously deleted
          </span>
        </div>
      )}
      {/* Unambiguous running tally for THIS group: what stays vs what the action touches. */}
      <div className="mb-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
        <span className="text-ok">✓ Keeping {keptCount}</span>
        <span className={markedCount > 0 ? (isRename ? "text-warn" : "text-danger") : "text-muted"}>
          {isRename ? "✎" : "🗑"} {isRename ? "Renaming" : "Deleting"} {markedCount}
        </span>
        {markedCount > 0 && isRename && (
          <span className="text-muted text-xs">— marked files get <span className="font-mono">{markedName("name", affix, position)}</span>; nothing is deleted</span>
        )}
        <span className="text-muted text-xs ml-auto">Toggle each row’s <strong>Keep</strong> / <strong>{actVerb}</strong> below.</span>
      </div>

      {/* The whole table scrolls horizontally so the FULL path is always reachable — drag
          the bottom scrollbar left/right; the path cells never truncate. */}
      <div className="overflow-x-auto">
        <table className="text-left" style={{ minWidth: "100%" }}>
          <thead className="text-muted text-xs">
            <tr>
              <th className="cell">Action</th>
              <th className="cell">Drive</th>
              <th className="cell">Full path</th>
              <th className="cell">Modified</th>
              <th className="cell">Integrity</th>
              <th className="cell">Quality</th>
              <th className="cell">State</th>
              <th className="cell">Actions</th>
            </tr>
          </thead>
          <tbody>
            {cluster.members.map((m) => {
              const keep = decisions.get(m.fileId) ?? true;
              const full = fullPathOf(m);
              const disabled = m.status !== "present";
              const marked = !keep && !disabled;
              return (
                <tr key={m.fileId} className={`${disabled ? "opacity-60" : marked ? rowMarkBg : ""}`}>
                  {/* Explicit action toggle — no more ambiguous "keep" checkbox. The chosen
                      side is filled; choosing Rename/Delete clearly flags the row. */}
                  <td className="cell whitespace-nowrap">
                    {disabled ? (
                      <span className="text-muted text-xs">{m.status}</span>
                    ) : (
                      <div className="inline-flex rounded-md border border-border overflow-hidden text-xs" role="group">
                        <button className={`px-2 py-0.5 ${keep ? "bg-ok/20 text-ok font-medium" : "text-muted hover:bg-surface-2"}`}
                          onClick={() => onDecide(m.fileId, true)} aria-pressed={keep}>Keep</button>
                        <button className={`px-2 py-0.5 border-l border-border ${marked ? `${actActive} font-medium` : "text-muted hover:bg-surface-2"}`}
                          onClick={() => onDecide(m.fileId, false)} aria-pressed={marked}>{actVerb}</button>
                      </div>
                    )}
                  </td>
                  <td className="cell whitespace-nowrap"><span className="mr-1">{m.volumeLabel}</span><OnlineChip online={m.volumeOnline} /></td>
                  {/* full path, no truncation — guaranteed visible via horizontal scroll */}
                  <td className="cell font-mono text-xs whitespace-nowrap" title={full}>
                    {m.fileId === keeperId && (
                      <span className="text-ok mr-1" title="Recommended keeper — healthiest / highest-quality copy">★</span>
                    )}
                    {full}
                    {marked && isRename && (
                      <span className="text-warn ml-2" title="this file will be renamed to:">→ {markedName(baseName(m), affix, position)}</span>
                    )}
                  </td>
                  <td className="cell text-muted text-xs whitespace-nowrap">{m.modifiedAt ? new Date(m.modifiedAt * 1000).toLocaleDateString() : "—"}</td>
                  <td className="cell"><IntegrityBadge media={m.media} /></td>
                  <td className="cell"><QualityBadge media={m.media} /></td>
                  <td className="cell text-xs whitespace-nowrap">{m.status}</td>
                  <td className="cell whitespace-nowrap">
                    <div className="flex gap-1">
                      <button className="btn px-2 py-0.5 text-xs" disabled={!m.absPath || !m.volumeOnline}
                        title={m.volumeOnline ? "Open in Explorer" : "drive offline"}
                        onClick={() => m.absPath && revealInExplorer(m.absPath).catch(() => {})}>Reveal</button>
                      <button className="btn px-2 py-0.5 text-xs" title="Copy full path" onClick={() => onCopy(full)}>Copy</button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {keeperId != null && (
        <p className="text-ok text-xs mt-2">
          ★ These copies differ in technical quality. The starred copy looks healthiest / highest quality —
          consider keeping it and removing the weaker ones.
        </p>
      )}
      <p className="text-muted text-xs mt-2">
        Why grouped: identical size and full BLAKE3 content hash ({cluster.confidence} confidence).
        Integrity &amp; Quality come from the media analysis pass (run it from Index Explorer → “Analyze media quality”).
        Tip: scroll the table sideways to read the full path; use Copy or Reveal on any row.
      </p>
    </Card>
  );
}
