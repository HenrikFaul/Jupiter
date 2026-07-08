import { useState } from "react";
import { ReportKind, exportReport, listAudit, reclaimSummary } from "../../lib/contract";
import { useAsync } from "../../lib/useAsync";
import { useStore } from "../../lib/store";
import { Card, AsyncBoundary, Stat, bytes } from "../../components/ui";

// Append-only audit trail of every destructive/privileged action (CODING_RULES §8).
export function Reports() {
  const dataVersion = useStore((s) => s.dataVersion);
  const audit = useAsync(() => listAudit(500), [dataVersion]);
  const reclaim = useAsync(reclaimSummary, [dataVersion]);
  const [exported, setExported] = useState<string | null>(null);

  const outcomeTone = (o: string) =>
    o === "success" ? "text-ok" : o === "partial" ? "text-warn" : o === "failed" ? "text-danger" : "text-muted";

  async function doExport(kind: ReportKind) {
    setExported(null);
    try {
      const path = await exportReport(kind);
      setExported(`Saved: ${path}`);
    } catch (e) {
      setExported(`Export failed: ${String(e)}`); // fail loud (§2)
    }
  }

  const KINDS: ReportKind[] = ["reclaim", "duplicates", "folders", "drives", "audit"];

  return (
    <div className="p-6 space-y-4">
      <header>
        <h1 className="text-xl font-semibold">Reports & audit</h1>
        <p className="text-muted">A permanent, append-only record of every action that changed your files.</p>
      </header>

      {/* Reclaim summary — what Sift has actually cleaned up (initiative #8). */}
      {reclaim.data && (
        <Card title="Reclaim summary">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <Stat label="Space reclaimed" value={bytes(reclaim.data.bytesReclaimed)} tone="ok" />
            <Stat label="Files removed" value={reclaim.data.filesRemoved.toLocaleString()} />
            <Stat label="Still reclaimable" value={bytes(reclaim.data.stillReclaimableBytes)} tone="warn" />
            <Stat label="Files restored" value={reclaim.data.filesRestored.toLocaleString()} tone="accent" />
          </div>
          <p className="text-muted text-xs mt-3">
            Recycled {reclaim.data.recycled} · Quarantined {reclaim.data.quarantined} · Permanent {reclaim.data.permanentlyDeleted} · Marked/renamed {reclaim.data.filesRenamed}.
            {" "}{reclaim.data.presentFiles.toLocaleString()} files indexed · {reclaim.data.duplicateClusters.toLocaleString()} duplicate groups remaining.
            Export the <strong>reclaim</strong> bundle below for a shareable evidence record.
          </p>
        </Card>
      )}

      <Card title="Export reports (CSV)">
        <div className="flex flex-wrap gap-2 items-center">
          {KINDS.map((k) => (
            <button key={k} className="btn" onClick={() => doExport(k)}>Export {k}</button>
          ))}
          {exported && <span className="text-sm text-muted ml-2">{exported}</span>}
        </div>
        <p className="text-muted text-xs mt-2">Exports are written to <span className="font-mono">Sift-Data\exports\</span> next to the app.</p>
      </Card>

      <Card title="Audit log">
        <AsyncBoundary loading={audit.loading} error={audit.error} data={audit.data}
          empty="No destructive actions recorded yet.">
          {(rows) => (
            <table className="w-full text-left">
              <thead className="text-muted text-xs">
                <tr><th className="cell">When</th><th className="cell">Action</th><th className="cell">Outcome</th><th className="cell">Path</th><th className="cell">Reversible</th></tr>
              </thead>
              <tbody>
                {rows.map((a) => (
                  <tr key={a.id}>
                    <td className="cell text-muted text-xs">{new Date(a.at * 1000).toLocaleString()}</td>
                    <td className="cell">{a.action}</td>
                    <td className={`cell ${outcomeTone(a.outcome)}`}>{a.outcome}</td>
                    <td className="cell font-mono text-xs truncate max-w-[40ch]" title={a.pathRaw ?? ""}>{a.pathRaw ?? "—"}</td>
                    <td className="cell text-xs">{a.reversible ? "yes" : "no"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </AsyncBoundary>
      </Card>
    </div>
  );
}
