import { useMemo, useState } from "react";
import { RestoreEntry, DeletionOutcome, listRestorable, restoreFiles } from "../../lib/contract";
import { useAsync } from "../../lib/useAsync";
import { useStore } from "../../lib/store";
import { Card, AsyncBoundary } from "../../components/ui";
import { RotateCcw, Trash2, Tag, Archive } from "lucide-react";

function actionIcon(a: string) {
  if (a === "rename") return <Tag size={15} />;
  if (a === "quarantine") return <Archive size={15} />;
  return <Trash2 size={15} />;
}
function actionLabel(a: string) {
  return a === "recycle" ? "Recycle Bin" : a === "quarantine" ? "Quarantine"
    : a === "rename" ? "Rename" : a === "permanent" ? "Permanent" : a;
}

// Restore Center — cross-session UNDO. Reads the persistent audit log and brings back files
// the user recycled, quarantined, or renamed. The single biggest trust feature for a tool
// whose job is deleting files: nothing is ever truly lost unless it was a permanent delete.
export function RestoreCenter() {
  const dataVersion = useStore((s) => s.dataVersion);
  const bumpData = useStore((s) => s.bumpData);
  const entries = useAsync(() => listRestorable(300), [dataVersion]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const restorable = useMemo(() => (entries.data ?? []).filter((e) => e.restorable), [entries.data]);
  const allSel = restorable.length > 0 && selected.size === restorable.length;
  function toggleAll() { setSelected(allSel ? new Set() : new Set(restorable.map((e) => e.auditId))); }
  function toggle(id: number) {
    setSelected((p) => { const n = new Set(p); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  }

  async function runRestore(ids: number[]) {
    if (ids.length === 0) return;
    setBusy(true);
    setMsg(null);
    try {
      const r: DeletionOutcome = await restoreFiles(ids);
      setMsg(
        r.state === "success" ? `Restored ${r.removed} file(s).`
          : r.state === "partial" ? `Restored ${r.removed}, ${r.failed} failed${r.failures[0] ? ` — ${r.failures[0].reason}` : ""}.`
            : `Restore failed${r.failures[0] ? `: ${r.failures[0].reason}` : ""}.`,
      );
      setSelected(new Set());
      bumpData(); // refresh this list + every other screen (files are present again)
    } catch (e) {
      setMsg(`Restore failed: ${String(e)}`);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="p-6 space-y-4">
      <header className="flex items-center justify-between flex-wrap gap-2">
        <div>
          <h1 className="text-xl font-semibold">Restore center</h1>
          <p className="text-muted">One-click undo — bring back anything you recycled, quarantined, or renamed. Only permanent deletions can't be restored.</p>
        </div>
        <button className="btn btn-primary flex items-center gap-1.5" disabled={busy || selected.size === 0}
          onClick={() => runRestore([...selected])}>
          <RotateCcw size={15} /> {busy ? "Restoring…" : `Restore ${selected.size || ""} selected`}
        </button>
      </header>

      {msg && <div className="text-sm text-ok">{msg}</div>}

      <Card title={entries.data ? `${entries.data.length} reversible action(s)` : "Reversible actions"}>
        <AsyncBoundary loading={entries.loading} error={entries.error} data={entries.data}
          empty="No reversible actions yet. When you recycle, quarantine, or rename files, they appear here for one-click undo.">
          {(rows: RestoreEntry[]) => (
            <div className="max-h-[70vh] overflow-auto">
              <table className="w-full text-left">
                <thead className="text-muted text-xs sticky top-0 z-10 bg-surface">
                  <tr>
                    <th className="cell"><input type="checkbox" checked={allSel} disabled={restorable.length === 0}
                      onChange={toggleAll} aria-label="select all restorable" /></th>
                    <th className="cell">Action</th>
                    <th className="cell">File</th>
                    <th className="cell">When</th>
                    <th className="cell">Status</th>
                    <th className="cell"></th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((e) => (
                    <tr key={e.auditId} className={e.restorable ? "" : "opacity-60"}>
                      <td className="cell">
                        <input type="checkbox" disabled={!e.restorable} checked={selected.has(e.auditId)}
                          onChange={() => toggle(e.auditId)} aria-label={`select ${e.path}`} />
                      </td>
                      <td className="cell whitespace-nowrap">
                        <span className="inline-flex items-center gap-1.5 text-muted">{actionIcon(e.action)}{actionLabel(e.action)}</span>
                      </td>
                      <td className="cell font-mono text-xs max-w-[52ch] truncate" title={e.path}>{e.path}</td>
                      <td className="cell text-xs text-muted whitespace-nowrap">{new Date(e.at * 1000).toLocaleString()}</td>
                      <td className="cell text-xs whitespace-nowrap">
                        {e.alreadyRestored ? <span className="text-ok">restored ✓</span>
                          : e.restorable ? <span className="text-accent">restorable</span>
                            : <span className="text-muted">{e.note ?? "—"}</span>}
                      </td>
                      <td className="cell">
                        {e.restorable && (
                          <button className="btn px-2 py-0.5 text-xs" disabled={busy} onClick={() => runRestore([e.auditId])}>Restore</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </AsyncBoundary>
        <p className="text-muted text-xs mt-2">
          Recycled files are pulled back from the Windows Recycle Bin; quarantined files from <span className="font-mono">Sift-Data\quarantine</span>; renamed files are renamed back. Every restore is itself logged in the audit trail.
        </p>
      </Card>
    </div>
  );
}
