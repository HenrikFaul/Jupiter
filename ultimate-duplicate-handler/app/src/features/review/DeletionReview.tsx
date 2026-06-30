// Action review & commit surface. Handles BOTH destructive deletion (Recycle/Quarantine/
// Permanent — engine-validated) AND the non-destructive "mark (rename)" action that adds a
// prefix/suffix to the selected files so the user can flag them for their own later cleanup.
//
// CODING_RULES §1: deletion never happens without select → preview → Validate(engine) →
// Commit; the engine re-checks at execute time. Rename is non-destructive, so it skips the
// keep-at-least-one safety gate but is still audited and reported honestly.

import { useMemo, useState } from "react";
import {
  ClusterView,
  DeletionMode,
  DeletionOutcome,
  PlanDecision,
  PlanItemInput,
  ValidationResult,
  AffixPosition,
  createDeletionPlan,
  executeDeletionPlan,
  validateDeletionPlan,
  markFiles,
  formatBytes as fmt,
} from "../../lib/contract";

export type ActionMode = DeletionMode | "rename";

interface Props {
  clusters: ClusterView[];
  decisions: Map<number, boolean>; // fileId -> keep? (missing = keep)
  mode: ActionMode;
  affix: string;
  position: AffixPosition;
  onDone?: (o: DeletionOutcome) => void;
}

type Phase = "idle" | "validating" | "ready" | "blocked" | "committing" | "done";

export function DeletionReview({ clusters, decisions, mode, affix, position, onDone }: Props) {
  const [phase, setPhase] = useState<Phase>("idle");
  const [validation, setValidation] = useState<ValidationResult | null>(null);
  const [ackOnline, setAckOnline] = useState<number[]>([]);
  const [outcome, setOutcome] = useState<DeletionOutcome | null>(null);

  const isRename = mode === "rename";

  const items: PlanItemInput[] = useMemo(
    () =>
      clusters.flatMap((c) =>
        c.members.map((m) => ({
          fileId: m.fileId,
          contentId: c.contentId,
          volumeOnline: m.volumeOnline,
          statusPresent: m.status === "present",
          pathRaw: m.pathRaw,
          keep: decisions.get(m.fileId) ?? true,
        })),
      ),
    [clusters, decisions],
  );

  const targets = items.filter((i) => !i.keep);
  const reclaimable = useMemo(
    () =>
      clusters.reduce((sum, c) => {
        const removing = c.members.filter((m) => decisions.get(m.fileId) === false).length;
        return sum + c.sizeBytes * removing;
      }, 0),
    [clusters, decisions],
  );

  async function runValidation() {
    setPhase("validating");
    try {
      const result = await validateDeletionPlan(items, ackOnline);
      setValidation(result);
      setPhase(result.ok ? "ready" : "blocked");
    } catch (e) {
      setValidation({ ok: false, violations: [`Validation call failed: ${String(e)}`] });
      setPhase("blocked");
    }
  }

  async function commitDelete() {
    if (phase !== "ready") return;
    setPhase("committing");
    try {
      const planDecisions: PlanDecision[] = items.map((i) => ({ fileId: i.fileId, keep: i.keep }));
      const planId = await createDeletionPlan(null, mode as DeletionMode, planDecisions);
      const res = await executeDeletionPlan(planId, mode as DeletionMode, ackOnline);
      finish(res);
    } catch (e) {
      finish(failure(String(e)));
    }
  }

  async function commitRename() {
    setPhase("committing");
    try {
      const res = await markFiles(targets.map((t) => t.fileId), affix.trim(), position);
      finish(res);
    } catch (e) {
      finish(failure(String(e)));
    }
  }

  function finish(res: DeletionOutcome) {
    setOutcome(res);
    setPhase("done");
    onDone?.(res);
  }
  function failure(reason: string): DeletionOutcome {
    return { state: "failed", removed: 0, failed: targets.length, failures: [{ fileId: -1, pathRaw: "(batch)", reason }], reclaimedBytes: 0 };
  }

  return (
    <section className="rounded-lg border border-border bg-surface p-4 space-y-3 shadow-sm" aria-label="Action review and commit">
      <header className="flex items-center gap-2 flex-wrap">
        {isRename ? (
          <>
            <span className="text-warn font-medium">✎ Renaming {targets.length}</span>
            <span className="text-muted">· keeping {items.length - targets.length} · adds</span>
            <span className="chip">{position === "suffix" ? `name${affix.trim()}` : `${affix.trim()}name`}</span>
            <span className="text-muted text-xs">(nothing is deleted)</span>
          </>
        ) : (
          <>
            <span className="text-danger font-medium">🗑 Removing {targets.length}</span>
            <span className="text-muted">· keeping {items.length - targets.length} ·</span>
            <strong>{fmt(reclaimable)}</strong>
            <span className="text-muted">reclaimable ·</span>
            <span className="chip">{labelForMode(mode as DeletionMode)}</span>
          </>
        )}
      </header>

      {phase === "blocked" && validation && (
        <div role="alert" className="rounded border border-danger/50 p-3 space-y-2">
          <h4 className="text-danger font-medium">Blocked by safety checks</h4>
          <ul className="list-disc ml-5 text-danger/90">
            {validation.violations.map((v, i) => <li key={i}>{v}</li>)}
          </ul>
          <label className="flex items-start gap-2 text-warn">
            <input type="checkbox" onChange={(e) => setAckOnline(e.target.checked ? clusters.map((c) => c.contentId) : [])} />
            I understand some remaining copies live only on disconnected drives, and I accept removing the last online copy.
          </label>
          <button className="btn" onClick={runValidation}>Re-check</button>
        </div>
      )}

      {phase === "done" && outcome && (
        <div role="status" aria-live="polite" className={`rounded border p-3 ${
          outcome.state === "success" ? "border-ok/50 text-ok" : outcome.state === "partial" ? "border-warn/50 text-warn" : "border-danger/50 text-danger"
        }`}>
          {outcome.state === "success" && <p>{isRename ? `Renamed ${outcome.removed} files.` : `Removed ${outcome.removed} files. Reclaimed ${fmt(outcome.reclaimedBytes)}.`}</p>}
          {outcome.state === "partial" && (
            <>
              <p>Partial: {isRename ? "renamed" : "removed"} {outcome.removed}, {outcome.failed} failed.</p>
              <ul className="list-disc ml-5">{outcome.failures.map((f) => <li key={f.fileId}>{f.pathRaw}: {f.reason}</li>)}</ul>
            </>
          )}
          {outcome.state === "failed" && <p>Action failed{outcome.failures[0] ? `: ${outcome.failures[0].reason}` : ""}.</p>}
        </div>
      )}

      <footer className="flex gap-3">
        {isRename ? (
          <button className="btn btn-primary" onClick={commitRename}
            disabled={phase === "committing" || targets.length === 0 || !affix.trim()}>
            {phase === "committing" ? "Renaming…" : `Rename ${targets.length} file(s)`}
          </button>
        ) : (
          <>
            <button className="btn" onClick={runValidation} disabled={phase === "validating" || targets.length === 0}>
              {phase === "validating" ? "Checking…" : "Validate plan"}
            </button>
            <button className="btn danger" onClick={commitDelete} disabled={phase !== "ready"}
              title={phase !== "ready" ? "Run validation first — the engine must approve the plan" : ""}>
              {phase === "committing" ? "Removing…" : `Commit (${labelForMode(mode as DeletionMode)})`}
            </button>
          </>
        )}
      </footer>
    </section>
  );
}

function labelForMode(m: DeletionMode): string {
  return m === "recycle" ? "Recycle Bin" : m === "quarantine" ? "Quarantine" : "Permanent";
}
