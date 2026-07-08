import { listVolumes } from "../../lib/contract";
import { useAsync } from "../../lib/useAsync";
import { useStore } from "../../lib/store";
import { Card, OnlineChip, AsyncBoundary, bytes } from "../../components/ui";

// The known-device registry: every volume Sift has ever indexed, online or not.
export function SourceManager() {
  const dataVersion = useStore((s) => s.dataVersion);
  const go = useStore((s) => s.go);
  const vols = useAsync(listVolumes, [dataVersion]);

  return (
    <div className="p-6 space-y-4">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">Sources & drives</h1>
          <p className="text-muted">Every drive Sift knows about — connected or in a drawer.</p>
        </div>
        <button className="btn border-accent text-accent" onClick={() => go("scan")}>+ New scan</button>
      </header>

      <Card title="Known device registry">
        <AsyncBoundary loading={vols.loading} error={vols.error} data={vols.data}
          empty="No drives indexed yet. Start a scan to register your first drive.">
          {(rows) => (
            <table className="w-full text-left">
              <thead className="text-muted text-xs">
                <tr>
                  <th className="cell">Label</th><th className="cell">Type</th><th className="cell">Mount</th>
                  <th className="cell">Files (present)</th><th className="cell">Capacity</th>
                  <th className="cell">Last seen</th><th className="cell">State</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((v) => (
                  <tr key={v.volumeId}>
                    <td className="cell">{v.label}{v.isRemovable && <span className="chip ml-1">removable</span>}</td>
                    <td className="cell text-muted">{v.fsType}</td>
                    <td className="cell font-mono text-xs">{v.mountPoint ?? "—"}</td>
                    <td className="cell">{v.fileCount.toLocaleString()}</td>
                    <td className="cell">{v.totalBytes ? bytes(v.totalBytes) : "—"}</td>
                    <td className="cell text-muted text-xs">{new Date(v.lastSeenAt * 1000).toLocaleString()}</td>
                    <td className="cell"><OnlineChip online={v.isOnline} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </AsyncBoundary>
      </Card>

      <p className="text-muted text-xs">
        Drives are identified by volume GUID / serial — not by drive letter — so a drive
        keeps its identity (and its indexed history) even if Windows assigns it a different
        letter next time, or it stays disconnected for months.
      </p>
    </div>
  );
}
