import { useState } from "react";
import { queryFolderClusters, detectDuplicateFolders } from "../../lib/contract";
import { useAsync } from "../../lib/useAsync";
import { Card, OnlineChip, AsyncBoundary, bytes } from "../../components/ui";

// Duplicate folders: directories whose entire recursive content is identical. Detection
// is on-demand (a heavy pass) — the user triggers it; results persist in the index.
export function Folders() {
  const [version, setVersion] = useState(0);
  const [detecting, setDetecting] = useState(false);
  const [note, setNote] = useState<string | null>(null);
  const clusters = useAsync(() => queryFolderClusters(500), [version]);

  async function detect() {
    setDetecting(true);
    setNote(null);
    try {
      const found = await detectDuplicateFolders();
      setNote(`Found ${found} duplicate-folder group(s).`);
      setVersion((v) => v + 1);
    } catch (e) {
      setNote(`Detection failed: ${String(e)}`); // fail loud (§2)
    } finally {
      setDetecting(false);
    }
  }

  return (
    <div className="p-6 space-y-4">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">Duplicate folders</h1>
          <p className="text-muted">Folders whose entire recursive content is identical.</p>
        </div>
        <div className="flex items-center gap-3">
          {note && <span className="text-sm text-muted">{note}</span>}
          <button className="btn btn-primary" onClick={detect} disabled={detecting}>
            {detecting ? "Analyzing…" : "Detect duplicate folders"}
          </button>
        </div>
      </header>

      <p className="text-muted text-xs">
        Only files that have been fully hashed by a thorough scan are compared. Detection
        reports the outermost duplicated folder (nested copies are folded into their parent).
      </p>

      <Card title="Duplicate-folder groups">
        <AsyncBoundary loading={clusters.loading} error={clusters.error} data={clusters.data}
          empty="No duplicate folders detected yet. Run a thorough scan, then click Detect.">
          {(rows) => (
            <ul className="space-y-3">
              {rows.map((c) => (
                <li key={c.clusterId} className="border border-border rounded p-3">
                  <div className="flex justify-between mb-2">
                    <span>{c.memberCount} identical folders · {c.fileCount} files each · {bytes(c.totalBytes)} each</span>
                    <span className="text-warn">{bytes(c.reclaimableBytes)} reclaimable</span>
                  </div>
                  <ul className="space-y-1 font-mono text-xs">
                    {c.members.map((m, i) => (
                      <li key={i} className="flex items-center gap-2">
                        <span>{m.volumeLabel}</span>
                        <OnlineChip online={m.volumeOnline} />
                        <span className="truncate" title={m.folderPathRaw}>{m.folderPathRaw}</span>
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          )}
        </AsyncBoundary>
      </Card>
    </div>
  );
}
