import { useState } from "react";
import { Wand2, ShieldCheck, Trash2, ArrowRight, ArrowLeft } from "lucide-react";
import {
  getIndexStats, applySelectionRules, deleteFiles,
  SelectionRule, SelectionDecision, DeletionOutcome,
} from "../../lib/contract";
import { useAsync } from "../../lib/useAsync";
import { useStore } from "../../lib/store";
import { Card, Stat, bytes } from "../../components/ui";

// Quick Clean (initiative #10) — a 4-step guided wizard: review the index's duplicate waste →
// pick a keep rule → preview exactly what's removed → recycle. Pure UI over the existing
// engine-authoritative APIs (applySelectionRules keeps one copy per group; deleteFiles is
// audited + defaults to the Recycle Bin + honours protected folders).
type RuleChoice = { key: string; label: string; hint: string; rule: SelectionRule };
const RULES: RuleChoice[] = [
  { key: "newest", label: "Keep the newest copy", hint: "recommended — keep the most recently modified file in each group", rule: { kind: "keepNewest" } },
  { key: "oldest", label: "Keep the oldest copy", hint: "keep the original (earliest) file in each group", rule: { kind: "keepOldest" } },
  { key: "online", label: "Prefer a copy on an online drive", hint: "never leave only an offline copy behind", rule: { kind: "preferOnline" } },
  { key: "shortest", label: "Keep the shortest path", hint: "keep the file nearest the drive root", rule: { kind: "keepShortestPath" } },
];

export function QuickClean() {
  const go = useStore((s) => s.go);
  const bumpData = useStore((s) => s.bumpData);
  const dataVersion = useStore((s) => s.dataVersion);
  const stats = useAsync(getIndexStats, [dataVersion]);
  const [step, setStep] = useState(1);
  const [ruleKey, setRuleKey] = useState("newest");
  const [decisions, setDecisions] = useState<SelectionDecision[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<DeletionOutcome | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const chosen = RULES.find((r) => r.key === ruleKey)!;
  const toRemove = decisions ? decisions.filter((d) => !d.keep) : [];
  const dupes = stats.data?.duplicateClusters ?? 0;
  const reclaim = stats.data?.reclaimableBytes ?? 0;

  async function preview() {
    setBusy(true); setErr(null);
    try { setDecisions(await applySelectionRules([chosen.rule])); setStep(3); }
    catch (e) { setErr(String(e)); }
    finally { setBusy(false); }
  }
  async function clean() {
    if (toRemove.length === 0) return;
    setBusy(true); setErr(null);
    try {
      const res = await deleteFiles(toRemove.map((d) => d.fileId), "recycle");
      setResult(res); bumpData(); setStep(4);
    } catch (e) { setErr(String(e)); }
    finally { setBusy(false); }
  }

  return (
    <div className="p-6 max-w-3xl space-y-5">
      <header className="flex items-center gap-3">
        <span className="w-10 h-10 rounded-xl bg-accent/15 text-accent flex items-center justify-center"><Wand2 size={20} /></span>
        <div>
          <h1 className="text-xl font-semibold">Quick Clean</h1>
          <p className="text-muted">Reclaim space in a few clicks — keep one copy of each duplicate, recycle the rest.</p>
        </div>
      </header>

      <div className="flex items-center gap-2 text-xs">
        {["Review", "Rule", "Confirm", "Done"].map((s, i) => (
          <div key={s} className={`flex items-center gap-2 ${i + 1 <= step ? "text-accent" : "text-muted"}`}>
            <span className={`w-5 h-5 rounded-full flex items-center justify-center border ${i + 1 <= step ? "border-accent bg-accent/10" : "border-border"}`}>{i + 1}</span>
            {s}{i < 3 && <span className="text-border">—</span>}
          </div>
        ))}
      </div>

      {err && <div role="alert" className="text-danger text-sm">{err}</div>}

      {step === 1 && (
        <Card>
          {stats.loading ? <div className="text-muted">Reading the index…</div> : dupes === 0 ? (
            <div className="space-y-2">
              <div className="font-medium">Your index has no duplicate groups right now.</div>
              <p className="text-muted text-sm">Run a scan to index more drives, then come back.</p>
              <button className="btn" onClick={() => go("scan")}>Go to New scan</button>
            </div>
          ) : (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <Stat label="Duplicate groups" value={dupes.toLocaleString()} tone="accent" />
                <Stat label="Reclaimable" value={bytes(reclaim)} tone="warn" />
              </div>
              <p className="text-sm text-muted">Quick Clean keeps <span className="font-medium text-text">exactly one copy</span> of each duplicate group and moves the others to the Recycle Bin (fully reversible).</p>
              <button className="btn btn-primary" onClick={() => setStep(2)}>Choose a keep rule <ArrowRight size={14} className="inline -mt-0.5" /></button>
            </div>
          )}
        </Card>
      )}

      {step === 2 && (
        <Card>
          <div className="space-y-2">
            {RULES.map((r) => (
              <label key={r.key} className={`flex items-start gap-3 rounded-lg border p-3 cursor-pointer ${ruleKey === r.key ? "border-accent bg-accent/5" : "border-border"}`}>
                <input type="radio" name="rule" className="mt-1" checked={ruleKey === r.key} onChange={() => setRuleKey(r.key)} />
                <div><div className="font-medium">{r.label}</div><div className="text-muted text-xs">{r.hint}</div></div>
              </label>
            ))}
          </div>
          <div className="flex gap-2 mt-4">
            <button className="btn" onClick={() => setStep(1)}><ArrowLeft size={14} className="inline -mt-0.5" /> Back</button>
            <button className="btn btn-primary" disabled={busy} onClick={preview}>{busy ? "Analyzing…" : "Preview what will be removed"} <ArrowRight size={14} className="inline -mt-0.5" /></button>
          </div>
        </Card>
      )}

      {step === 3 && (
        <Card>
          <div className="space-y-3">
            <div className="flex items-center gap-2 text-sm"><ShieldCheck size={18} className="text-ok" /> Keeping one copy per group using <span className="font-medium">{chosen.label.toLowerCase()}</span>.</div>
            <div className="grid grid-cols-2 gap-4">
              <Stat label="Files to recycle" value={toRemove.length.toLocaleString()} tone="danger" />
              <Stat label="Space freed (≈)" value={bytes(reclaim)} tone="warn" />
            </div>
            <p className="text-muted text-xs">Removed files go to the Windows Recycle Bin — restore them there or from Singula's Restore Center. Protected folders are never touched, and at least one copy of every group is always kept.</p>
            <div className="flex gap-2">
              <button className="btn" onClick={() => setStep(2)}><ArrowLeft size={14} className="inline -mt-0.5" /> Back</button>
              <button className="btn danger" disabled={busy || toRemove.length === 0} onClick={clean}><Trash2 size={14} className="inline -mt-0.5" /> {busy ? "Cleaning…" : `Recycle ${toRemove.length} file(s)`}</button>
            </div>
          </div>
        </Card>
      )}

      {step === 4 && result && (
        <Card>
          <div className="space-y-3">
            <div className="text-lg font-semibold text-ok">Done — reclaimed {bytes(result.reclaimedBytes)}.</div>
            <p className="text-sm text-muted">{result.state === "success" ? `Recycled ${result.removed} file(s).` : result.state === "partial" ? `Recycled ${result.removed}; ${result.failed} could not be removed.` : "Nothing was removed."}</p>
            <div className="flex gap-2">
              <button className="btn btn-primary" onClick={() => go("home")}>Back to overview</button>
              <button className="btn" onClick={() => go("restore")}>Open Restore Center</button>
            </div>
          </div>
        </Card>
      )}
    </div>
  );
}
