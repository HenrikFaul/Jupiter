import { useEffect, useState } from "react";
import { licenseStatus, activateLicense, deactivateLicense, type LicenseStatus } from "../../lib/contract";
import { useStore } from "../../lib/store";
import { Card } from "../../components/ui";

/** Settings → License: paste an offline Ed25519 key to unlock Pro, or deactivate to revert to
 *  Free. Mirrors the storage/ffprobe cards' look (the `chip online/offline` classes). Keeps the
 *  resolved tier in the global store so Pro screens can show a lock badge. */
export function LicenseCard() {
  const setTier = useStore((s) => s.setTier);
  const [status, setStatus] = useState<LicenseStatus | null>(null);
  const [token, setToken] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);

  async function refresh() {
    try {
      const s = await licenseStatus();
      setStatus(s);
      setTier(s.tier); // keep the store in lockstep so lock badges are accurate
    } catch (e) {
      setError(String(e)); // fail loud (§2)
    }
  }

  useEffect(() => {
    void refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function onActivate() {
    setBusy(true);
    setError(null);
    setNote(null);
    try {
      const s = await activateLicense(token.trim());
      setStatus(s);
      setTier(s.tier);
      setToken("");
      setNote(s.tier === "pro" ? "Pro features unlocked. Thank you!" : "License accepted.");
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }

  async function onDeactivate() {
    setBusy(true);
    setError(null);
    setNote(null);
    try {
      const s = await deactivateLicense();
      setStatus(s);
      setTier(s.tier);
      setNote("License removed — reverted to Free.");
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  }

  const isPro = status?.tier === "pro";

  return (
    <Card title="License">
      <div className="flex items-center gap-3 text-sm">
        <span className="text-muted">Current plan</span>
        <span className={`chip ${isPro ? "online" : "offline"}`}>{isPro ? "PRO" : "FREE"}</span>
        {status?.email && <span className="text-muted text-xs">{status.email}</span>}
        {status?.expiresAt != null && (
          <span className="text-muted text-xs">
            expires {new Date(status.expiresAt * 1000).toLocaleDateString()}
          </span>
        )}
      </div>

      {!isPro && (
        <p className="text-muted text-xs mt-2">
          The core scan → review → safe-delete loop is free forever. Pro unlocks{" "}
          <strong>similar-image detection</strong>, <strong>media quality analysis</strong>,{" "}
          <strong>smart selection rules</strong>, and <strong>bulk delete</strong>. Activation is
          fully offline — your key never leaves this machine.
        </p>
      )}

      <div className="mt-3 space-y-2">
        <textarea
          className="w-full rounded-lg border border-border bg-surface-2 p-2 font-mono text-xs"
          rows={3}
          placeholder="Paste your Sift license key (payload.signature)…"
          value={token}
          onChange={(e) => setToken(e.target.value)}
          disabled={busy}
        />
        <div className="flex items-center gap-2">
          <button className="btn" onClick={onActivate} disabled={busy || token.trim().length === 0}>
            {busy ? "Working…" : "Activate"}
          </button>
          {isPro && (
            <button className="btn" onClick={onDeactivate} disabled={busy}>
              Deactivate
            </button>
          )}
        </div>
      </div>

      {error && (
        <div role="alert" className="text-danger text-sm mt-2">
          {error}
        </div>
      )}
      {note && <div className="text-ok text-sm mt-2">{note}</div>}
    </Card>
  );
}
