import { useEffect, useMemo, useState } from "react";
import {
  SimilarVideoGroup,
  SimilarVideoResult,
  DeletionMode,
  DeletionOutcome,
  FfprobeStatus,
  startVideoSimilarity,
  cancelVideoSimilarity,
  videoSimilarityActive,
  onVideoProgress,
  onVideoDone,
  onVideoError,
  ffprobeStatus,
  deleteFiles,
  revealInExplorer,
  formatBytes as bytes,
} from "../../lib/contract";
import { useStore } from "../../lib/store";
import { Card } from "../../components/ui";
import { VideoThumbnail } from "../../components/VideoThumbnail";

// Strictness bands map to MEAN per-keyframe Hamming thresholds on the 64-bit dHash signature —
// the SAME 0..64 scale as similar-images (videohash::signature_distance), so the bands transfer:
// ≤2 near-identical, ≤5 strong, ≤12 similar, ≤20 loose.
const BANDS: { label: string; threshold: number; hint: string }[] = [
  { label: "Exact", threshold: 2, hint: "near-identical (re-encoded / re-wrapped)" },
  { label: "Strong", threshold: 5, hint: "clearly the same clip" },
  { label: "Similar", threshold: 12, hint: "re-resolutioned / trimmed / re-compressed" },
  { label: "Loose", threshold: 20, hint: "loosely similar (more false matches)" },
];

export function SimilarVideos() {
  const bumpData = useStore((s) => s.bumpData);
  const [band, setBand] = useState(1); // default "Strong"
  const [result, setResult] = useState<SimilarVideoResult | null>(null);
  const [running, setRunning] = useState(false);
  const [progress, setProgress] = useState<{ done: number; total: number } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [ffprobe, setFfprobe] = useState<FfprobeStatus | null>(null);
  const [decisions, setDecisions] = useState<Map<number, boolean>>(new Map()); // keep?
  const [mode, setMode] = useState<DeletionMode>("recycle");
  const [outcome, setOutcome] = useState<DeletionOutcome | null>(null);
  const [busy, setBusy] = useState(false); // deletion in flight

  // Wire the background video:// event stream (this pass is slow — it shells out to ffmpeg per
  // video — so it runs on a Rust thread and streams progress, exactly like media analysis).
  useEffect(() => {
    let offP = () => {};
    let offD = () => {};
    let offE = () => {};
    onVideoProgress((p) => {
      setRunning(true);
      setProgress({ done: p.done, total: p.total });
    }).then((f) => (offP = f));
    onVideoDone((r) => {
      setRunning(false);
      setProgress(null);
      setResult(r);
      setDecisions(new Map()); // default: keep everything (similar ≠ identical — you review)
      if (r.newlyHashed > 0) bumpData();
    }).then((f) => (offD = f));
    onVideoError((msg) => {
      setRunning(false);
      setProgress(null);
      setError(msg); // fail loud
    }).then((f) => (offE = f));
    // Re-sync if a pass is already running (e.g. user navigated away and back).
    videoSimilarityActive().then(setRunning).catch(() => {});
    ffprobeStatus().then(setFfprobe).catch(() => {});
    return () => {
      offP();
      offD();
      offE();
    };
  }, [bumpData]);

  async function detect() {
    setError(null);
    setOutcome(null);
    try {
      await startVideoSimilarity(BANDS[band].threshold);
      setRunning(true);
      setProgress({ done: 0, total: 0 });
    } catch (e) {
      setError(String(e)); // fail loud (e.g. "already running")
    }
  }

  async function stop() {
    try {
      await cancelVideoSimilarity();
    } catch {
      /* best-effort */
    }
  }

  const removeIds = useMemo(
    () => [...decisions.entries()].filter(([, keep]) => !keep).map(([id]) => id),
    [decisions],
  );

  function toggle(fileId: number) {
    setDecisions((p) => new Map(p).set(fileId, p.get(fileId) === false ? true : false));
  }
  // Keep the suggested keeper (largest) in every group, mark the rest for removal.
  function autoSelectExtras() {
    const next = new Map<number, boolean>();
    for (const g of result?.groups ?? [])
      for (const m of g.members) next.set(m.file.fileId, m.isRepresentative);
    setDecisions(next);
  }

  async function remove() {
    if (removeIds.length === 0) return;
    setBusy(true);
    try {
      const res = await deleteFiles(removeIds, mode);
      setOutcome(res);
      bumpData();
      // Re-run detection so the (now cached) signatures re-cluster without the removed files.
      await detect();
    } catch (e) {
      setOutcome({ state: "failed", removed: 0, failed: removeIds.length, failures: [{ fileId: -1, pathRaw: "(batch)", reason: String(e) }], reclaimedBytes: 0 });
    } finally {
      setBusy(false);
    }
  }

  const pct = progress && progress.total > 0 ? Math.round((progress.done / progress.total) * 100) : 0;

  return (
    <div className="p-6 space-y-4">
      <header>
        <h1 className="text-xl font-semibold">Similar videos</h1>
        <p className="text-muted">Find near-duplicate videos — re-encoded, re-resolutioned, trimmed, or re-wrapped copies of the same clip — by perceptual keyframe fingerprinting. Exact-hash dedup misses these because one re-encode changes every byte.</p>
      </header>

      {ffprobe && !ffprobe.available && (
        <Card>
          <p className="text-warn text-sm">
            ffmpeg was not found. Video fingerprinting samples keyframes with ffmpeg — install it
            (<code>winget install Gyan.FFmpeg</code>) or drop <code>ffmpeg.exe</code> beside Sift, then re-detect.
          </p>
        </Card>
      )}

      <Card>
        <div className="flex flex-wrap items-center gap-3">
          <span className="text-muted text-xs">Strictness</span>
          {BANDS.map((b, i) => (
            <button key={b.label} className={`btn ${band === i ? "btn-primary" : ""}`} disabled={running} onClick={() => setBand(i)}>
              {band === i ? "✓ " : ""}{b.label}
            </button>
          ))}
          {running ? (
            <button className="btn danger ml-2" onClick={stop}>Stop</button>
          ) : (
            <button className="btn btn-primary ml-2" onClick={detect}>
              {result ? "Re-detect" : "Detect similar videos"}
            </button>
          )}
        </div>
        <p className="text-muted text-xs mt-2">{BANDS[band].hint}. Signatures are cached, so changing strictness re-detects fast — only new videos are sampled.</p>

        {running && (
          <div className="mt-3" role="status">
            <div className="flex items-center justify-between text-xs text-muted mb-1">
              <span>Fingerprinting videos…</span>
              <span>{progress ? `${progress.done.toLocaleString()} / ${progress.total.toLocaleString()}` : ""}</span>
            </div>
            <div className="h-2 rounded bg-surface-2 overflow-hidden">
              <div className="h-full bg-accent transition-all" style={{ width: `${pct}%` }} />
            </div>
          </div>
        )}

        {error && <div role="alert" className="text-danger text-sm mt-2">Detection failed: {error}</div>}
        {result && !running && (
          <p className="text-sm mt-2">
            {result.groups.length} similar group(s) · {result.totalHashed.toLocaleString()} videos fingerprinted
            {result.newlyHashed > 0 ? ` (${result.newlyHashed.toLocaleString()} new this run)` : ""}.
          </p>
        )}
      </Card>

      {result && result.groups.length > 0 && (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <button className="btn" onClick={autoSelectExtras}>Select all but the largest in each group</button>
            <button className="btn" onClick={() => setDecisions(new Map())}>Reset (keep all)</button>
            <span className="ml-auto text-muted text-xs">Action</span>
            <select className="bg-surface-2 border border-border rounded px-2 py-1" value={mode} onChange={(e) => setMode(e.target.value as DeletionMode)}>
              <option value="recycle">Recycle Bin (reversible)</option>
              <option value="quarantine">Quarantine</option>
              <option value="permanent">Permanent</option>
            </select>
            <button className="btn danger" onClick={remove} disabled={busy || removeIds.length === 0}>
              {busy ? "Removing…" : `Remove ${removeIds.length} selected`}
            </button>
          </div>

          {outcome && (
            <div role="status" className={`rounded-lg border p-3 shadow-sm ${outcome.state === "success" ? "border-ok/50 text-ok" : outcome.state === "partial" ? "border-warn/50 text-warn" : "border-danger/50 text-danger"}`}>
              {outcome.state === "success" && <p>Removed {outcome.removed} videos. Reclaimed {bytes(outcome.reclaimedBytes)}.</p>}
              {outcome.state === "partial" && <p>Partial: removed {outcome.removed}, {outcome.failed} failed.</p>}
              {outcome.state === "failed" && <p>Removal failed{outcome.failures[0] ? `: ${outcome.failures[0].reason}` : ""}.</p>}
            </div>
          )}

          <div className="space-y-3">
            {result.groups.map((g: SimilarVideoGroup) => (
              <Card key={g.groupId}>
                <div className="text-muted text-xs mb-2">{g.memberCount} similar videos · up to {bytes(g.maxSizeBytes)}</div>
                <div className="flex flex-wrap gap-3">
                  {g.members.map((m) => {
                    const keep = decisions.get(m.file.fileId) ?? true;
                    return (
                      <div key={m.file.fileId} className={`w-[150px] rounded-lg border p-2 ${keep ? "border-border" : "border-danger/60 bg-danger/5"}`}>
                        <VideoThumbnail fileId={m.file.fileId} size={132} />
                        <div className="flex items-center justify-between mt-1.5">
                          <span className={`chip ${m.isRepresentative ? "online" : ""}`} title="perceptual similarity to the largest video">
                            {m.isRepresentative ? "keeper" : `${m.similarityPct}%`}
                          </span>
                          <span className="text-muted text-[11px]">{bytes(m.file.sizeBytes)}</span>
                        </div>
                        <div className="text-[11px] truncate mt-0.5" title={m.file.absPath ?? m.file.pathRaw}>
                          {(m.file.pathRaw.split(/[\\/]/).pop()) || m.file.pathRaw}
                        </div>
                        <label className="flex items-center gap-1.5 mt-1.5 text-xs cursor-pointer">
                          <input type="checkbox" checked={!keep} onChange={() => toggle(m.file.fileId)} />
                          remove
                        </label>
                        <div className="flex gap-1 mt-1">
                          <button className="btn px-1.5 py-0.5 text-[11px] flex-1" disabled={!m.file.absPath}
                            onClick={() => m.file.absPath && revealInExplorer(m.file.absPath).catch(() => {})}>Reveal</button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </Card>
            ))}
          </div>
        </>
      )}

      {result && !running && result.groups.length === 0 && (
        <Card><p className="text-muted">No similar videos found at this strictness. Try a looser band, or run a scan first to index your videos.</p></Card>
      )}
    </div>
  );
}
