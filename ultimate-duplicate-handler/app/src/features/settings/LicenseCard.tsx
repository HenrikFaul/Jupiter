import { useEffect, useState } from "react";
import { licenseStatus, activateLicense, deactivateLicense, type LicenseStatus } from "../../lib/contract";
import { Card } from "../../components/ui";

/** Settings → License: the Free/Pro entitlement mechanism (initiative #1). Paste an offline
 *  Ed25519 key to set Pro, or deactivate to revert to Free. The mechanism is fully built; the
 *  paywall itself is OFF until commercial launch, so today every feature is unlocked for all. */
export function LicenseCard() {
  const [status, setStatus] = useState<LicenseStatus | null>(null);
  const [token, setToken] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);

  async function refresh() {
    try { setStatus(await licenseStatus()); } catch (e) { setError(String(e)); }
  }
  useEffect(() => { void refresh(); }, []);

  async function onActivate() {
    setBusy(true); setError(null); setNote(null);
    try {
      const s = await activateLicense(token.trim());
      setStatus(s); setToken("");
      setNote(s.tier === "pro" ? "Pro license activated. Thank you!" : "License accepted.");
    } catch (e) { setError(String(e)); } finally { setBusy(false); }
  }
  async function onDeactivate() {
    setBusy(true); setError(null); setNote(null);
    try { setStatus(await deactivateLicense()); setNote("License removed — reverted to Free."); }
    catch (e) { setError(String(e)); } finally { setBusy(false); }
  }

  const isPro = status?.tier === "pro";
  const enforced = status?.enforcementEnabled ?? false;

  return (
    <Card title="License (Free / Pro)">
      <div className="flex items-center gap-3 text-sm flex-wrap">
        <span className="text-muted">Current plan</span>
        <span className={`chip ${isPro ? "online" : "offline"}`}>{isPro ? "PRO" : "FREE"}</span>
        {status?.email && <span className="text-muted text-xs">{status.email}</span>}
        {status?.expiresAt != null && (
          <span className="text-muted text-xs">expires {new Date(status.expiresAt * 1000).toLocaleDateString()}</span>
        )}
      </div>

      {!enforced ? (
        <p className="text-muted text-xs mt-2">
          <strong>Pre-launch:</strong> every feature is currently unlocked for everyone — the Free/Pro
          mechanism is built but the paywall is off. At commercial launch, the Pro tier will gate{" "}
          {(status?.proFeatures ?? []).join(", ") || "the advanced features"} while the core
          scan → review → safe-delete loop stays free forever. Activation is fully offline; a key never leaves this machine.
        </p>
      ) : (
        <p className="text-muted text-xs mt-2">
          The core scan → review → safe-delete loop is free. Pro unlocks{" "}
          {(status?.proFeatures ?? []).join(", ")}. Activation is fully offline.
        </p>
      )}

      <div className="mt-3 space-y-2">
        <textarea className="w-full rounded-lg border border-border bg-surface-2 p-2 font-mono text-xs" rows={3}
          placeholder="Paste a Sift license key (payload.signature)…" value={token}
          onChange={(e) => setToken(e.target.value)} disabled={busy} />
        <div className="flex items-center gap-2">
          <button className="btn btn-primary" onClick={onActivate} disabled={busy || token.trim().length === 0}>
            {busy ? "Working…" : "Activate"}
          </button>
          {isPro && <button className="btn" onClick={onDeactivate} disabled={busy}>Deactivate</button>}
        </div>
      </div>

      {error && <div role="alert" className="text-danger text-sm mt-2">{error}</div>}
      {note && <div className="text-ok text-sm mt-2">{note}</div>}
    </Card>
  );
}
