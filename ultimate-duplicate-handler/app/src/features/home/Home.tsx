import { getIndexStats, listVolumes } from "../../lib/contract";
import { useAsync } from "../../lib/useAsync";
import { useStore } from "../../lib/store";
import { Card, Stat, OnlineChip, AsyncBoundary, bytes } from "../../components/ui";
import { Onboarding } from "./Onboarding";

export function Home() {
  const dataVersion = useStore((s) => s.dataVersion);
  const go = useStore((s) => s.go);
  const stats = useAsync(getIndexStats, [dataVersion]);
  const vols = useAsync(listVolumes, [dataVersion]);

  const offline = (vols.data ?? []).filter((v) => !v.isOnline);

  // First run / empty index → guided onboarding instead of an empty dashboard.
  if (stats.data && stats.data.totalFiles === 0) return <Onboarding />;

  return (
    <div className="p-6 space-y-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold">Overview</h1>
          <p className="text-muted">Your persistent file-intelligence index at a glance.</p>
        </div>
        <div className="flex gap-2">
          <button className="btn" onClick={() => go("quickclean")} title="Reclaim space in a few guided clicks">⚡ Quick Clean</button>
          <button className="btn btn-primary" onClick={() => go("scan")}>+ New scan</button>
        </div>
      </header>

      {stats.error && <div role="alert" className="text-danger">Failed to load stats: {stats.error}</div>}
      {stats.data && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Stat label="Indexed files" value={stats.data.totalFiles.toLocaleString()} />
          <Stat label="Duplicate groups" value={stats.data.duplicateClusters.toLocaleString()} tone="accent" />
          <Stat label="Reclaimable" value={bytes(stats.data.reclaimableBytes)} tone="warn" />
          <Stat label="Known drives" value={`${stats.data.onlineVolumes}/${stats.data.knownVolumes} online`} />
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card title="Known drives">
          <AsyncBoundary
            loading={vols.loading}
            error={vols.error}
            data={vols.data}
            empty="No drives indexed yet — run a scan to begin."
          >
            {(rows) => (
              <table className="w-full text-left">
                <thead className="text-muted text-xs">
                  <tr><th className="cell">Label</th><th className="cell">Type</th><th className="cell">Files</th><th className="cell">State</th></tr>
                </thead>
                <tbody>
                  {rows.map((v) => (
                    <tr key={v.volumeId}>
                      <td className="cell">{v.label}{v.mountPoint ? <span className="text-muted"> ({v.mountPoint})</span> : null}</td>
                      <td className="cell text-muted">{v.fsType}</td>
                      <td className="cell">{v.fileCount.toLocaleString()}</td>
                      <td className="cell"><OnlineChip online={v.isOnline} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </AsyncBoundary>
        </Card>

        <Card title="Offline-drive reminders">
          {offline.length === 0 ? (
            <p className="text-muted">All known drives are currently connected.</p>
          ) : (
            <ul className="space-y-2">
              {offline.map((v) => (
                <li key={v.volumeId} className="flex items-center gap-2">
                  <OnlineChip online={false} />
                  <span>{v.label}</span>
                  <span className="text-muted text-xs">
                    last seen {new Date(v.lastSeenAt * 1000).toLocaleDateString()} · {v.fileCount.toLocaleString()} files still known
                  </span>
                </li>
              ))}
            </ul>
          )}
          <p className="text-muted text-xs mt-3">
            Files on disconnected drives stay in the index — Sift still recognizes them as
            surviving copies when you review duplicates elsewhere.
          </p>
        </Card>
      </div>
    </div>
  );
}
