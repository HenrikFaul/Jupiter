import { useMemo, useState } from "react";
import {
  SimilarVideoGroup,
  SimilarVideoResult,
  DeletionMode,
  DeletionOutcome,
  detectSimilarVideos,
  deleteFiles,
  revealInExplorer,
  formatBytes as bytes,
} from "../../lib/contract";
import { useStore } from "../../lib/store";
import { Card } from "../../components/ui";
import { VideoThumbnail } from "../../components/VideoThumbnail";

// Mean-Hamming thresholds over the keyframe signature (same 0..=64 scale as similar-images).
const BANDS: { label: string; threshold: number; hint: string }[] = [
  { label: "Exact", threshold: 2, hint: "near-identical (re-encode / container swap)" },
  { label: "Strong", threshold: 5, hint: "clearly the same footage" },
  { label: "Similar", threshold: 12, hint: "same scene, different resolution / crop" },
  { label: "Loose", threshold: 20, hint: "loosely similar (more false matches)" },
];

export function SimilarVideos() {
  const bumpData = useStore((s) => s.bumpData);
  const [band, setBand] = useState(1);
  const [result, setResult] = useState<SimilarVideoResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [decisions, setDecisions] = useState<Map<number, boolean>>(new Map());
  const [mode, setMode] = useState<DeletionMode>("recycle");
  const [outcome, setOutcome] = useState<DeletionOutcome | null>(null);

  async function detect() {
    setBusy(true);
    setError(null);
    setOutcome(null);
    try {
      const r = await detectSimilarVideos(BANDS[band].threshold);
      setResult(r);
      setDecisions(new Map());
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }

  const removeIds = useMemo(
    () => [...decisions.entries()].filter(([, keep]) => !keep).map(([id]) => id),
    [decisions],
  );
  function toggle(fileId: number) {
    setDecisions((p) => new Map(p).set(fileId, p.get(fileId) === false ? true : false));
  }
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
      await detect();
    } catch (e) {
      setOutcome({ state: "failed", removed: 0, failed: removeIds.length, failures: [{ fileId: -1, pathRaw: "(batch)", reason: String(e) }], reclaimedBytes: 0 });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="p-6 space-y-4">
      <header>
        <h1 className="text-xl font-semibold">Similar videos</h1>
        <p className="text-muted">Find near-duplicate videos — re-encodes, resolution changes, container swaps, or trims of the same footage — by perceptual keyframe fingerprint. Uses the bundled ffmpeg; results are cached.</p>
      </header>

      <Card>
        <div className="flex flex-wrap items-center gap-3">
          <span className="text-muted text-xs">Strictness</span>
          {BANDS.map((b, i) => (
            <button key={b.label} className={`btn ${band === i ? "btn-primary" : ""}`} onClick={() => setBand(i)}>
              {band === i ? "✓ " : ""}{b.label}
            </button>
          ))}
          <button className="btn btn-primary ml-2" onClick={detect} disabled={busy}>
            {busy ? "Working…" : result ? "Re-detect" : "Detect similar videos"}
          </button>
        </div>
        <p className="text-muted text-xs mt-2">{BANDS[band].hint}. Signatures are cached, so changing strictness re-detects fast. First run samples keyframes (slower).</p>
        {error && <div role="alert" className="text-danger text-sm mt-2">Detection failed: {error}</div>}
        {result && (
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
                      <div key={m.file.fileId} className={`w-[160px] rounded-lg border p-2 ${keep ? "border-border" : "border-danger/60 bg-danger/5"}`}>
                        <VideoThumbnail fileId={m.file.fileId} size={144} />
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

      {result && result.groups.length === 0 && (
        <Card><p className="text-muted">No similar videos found at this strictness. Try a looser band, or scan a folder with videos first.</p></Card>
      )}
    </div>
  );
}
