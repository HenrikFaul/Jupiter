import { useEffect, useState } from "react";
import { open } from "@tauri-apps/plugin-dialog";
import { FolderOpen } from "lucide-react";
import { dbIntegrityCheck, storageInfo, ffprobeStatus, getSchedule, setSchedule, runRescanNow, getProtectedPaths, setProtectedPaths, getUpdateStatus } from "../../lib/contract";
import { useAsync } from "../../lib/useAsync";
import { useStore } from "../../lib/store";
import { Card, bytes } from "../../components/ui";
import { LicenseCard } from "./LicenseCard";
import { ActionsCard } from "./ActionsCard";

export function Settings() {
  const go = useStore((s) => s.go);
  const theme = useStore((s) => s.theme);
  const toggleTheme = useStore((s) => s.toggleTheme);
  const [integrity, setIntegrity] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const storage = useAsync(storageInfo, []);
  const ffprobe = useAsync(ffprobeStatus, []);
  const update = useAsync(getUpdateStatus, []);

  // Scheduled auto-rescan (initiative #3).
  const schedule = useAsync(getSchedule, []);
  const [interval, setInterval] = useState(24);
  const [schedEnabled, setSchedEnabled] = useState(false);
  const [schedMsg, setSchedMsg] = useState<string | null>(null);
  const [schedBusy, setSchedBusy] = useState(false);
  useEffect(() => {
    if (schedule.data) { setInterval(schedule.data.intervalHours); setSchedEnabled(schedule.data.enabled); }
  }, [schedule.data]);

  async function saveSchedule() {
    setSchedBusy(true);
    setSchedMsg(null);
    try {
      const cfg = await setSchedule(interval, schedEnabled);
      setSchedMsg(cfg.enabled
        ? cfg.taskRegistered ? `Scheduled — every ${cfg.intervalHours}h.` : "Saved, but the Windows task could not be registered (try running Sift as administrator)."
        : "Auto-rescan disabled.");
    } catch (e) {
      setSchedMsg(String(e));
    } finally {
      setSchedBusy(false);
    }
  }
  async function rescanNow() {
    try { await runRescanNow(); go("monitor"); } catch (e) { setSchedMsg(String(e)); }
  }

  // Protected folders (initiative #6).
  const protectedPaths = useAsync(getProtectedPaths, []);
  const [newProtected, setNewProtected] = useState("");
  const [protList, setProtList] = useState<string[] | null>(null);
  useEffect(() => { if (protectedPaths.data) setProtList(protectedPaths.data); }, [protectedPaths.data]);
  async function saveProtected(next: string[]) {
    setProtList(next);
    try { await setProtectedPaths(next); } catch { /* surfaced on next load */ }
  }
  // Native Windows folder picker — choose folder(s) to protect instead of typing the path.
  async function pickProtectedFolder() {
    try {
      const picked = await open({ directory: true, multiple: true, title: "Choose folder(s) to protect" });
      if (!picked) return;
      const arr = Array.isArray(picked) ? picked : [picked];
      const next = [...(protList ?? [])];
      for (const p of arr) if (p && !next.includes(p)) next.push(p);
      saveProtected(next);
    } catch { /* user cancelled or dialog unavailable */ }
  }

  async function runIntegrity() {
    setChecking(true);
    setIntegrity(null);
    try {
      const ok = await dbIntegrityCheck();
      setIntegrity(ok ? "Index is healthy (integrity_check = ok)." : "Index reports integrity problems — consider a rebuild.");
    } catch (e) {
      setIntegrity(`Check failed: ${String(e)}`); // fail loud (§2)
    } finally {
      setChecking(false);
    }
  }

  return (
    <div className="p-6 space-y-4 max-w-3xl">
      <header>
        <h1 className="text-xl font-semibold">Settings</h1>
        <p className="text-muted">Index, performance, safety, and appearance.</p>
      </header>

      <Card title="Appearance">
        <button className="btn" onClick={toggleTheme}>Switch to {theme === "dark" ? "light" : "dark"} theme</button>
        <span className="text-muted text-xs ml-2">— or use the day / night toggle in the sidebar.</span>
      </Card>

      <LicenseCard />

      <ActionsCard />

      <Card title="Updates">
        {update.error && <div className="text-danger text-sm">{update.error}</div>}
        {update.data && (
          <div className="space-y-2 text-sm">
            <div className="flex items-center gap-2">
              <span>Version <span className="font-mono">{update.data.currentVersion}</span></span>
              <span className="chip">{update.data.portable ? "portable" : "installed"}</span>
            </div>
            <p className="text-muted">{update.data.message}</p>
            {update.data.updatesEnabled && update.data.feedUrl && (
              <p className="text-muted text-xs">Release feed: <span className="font-mono">{update.data.feedUrl}</span> — the signed in-app updater activates with the first published release.</p>
            )}
          </div>
        )}
      </Card>

      <Card title="Scheduled auto-rescan">
        <p className="text-muted text-sm mb-3">
          Keep the persistent index fresh automatically. When enabled, Windows Task Scheduler relaunches Sift hidden on the interval to re-scan your known online drives — so cross-drive duplicate matching is always up to date, even when the app is closed.
        </p>
        <div className="flex flex-wrap items-center gap-3">
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={schedEnabled} onChange={(e) => setSchedEnabled(e.target.checked)} /> Enable scheduled rescan
          </label>
          <label className="flex items-center gap-2 text-sm text-muted">every
            <input type="number" min={1} max={720} value={interval} disabled={!schedEnabled}
              onChange={(e) => setInterval(Math.max(1, Math.min(720, Number(e.target.value) || 24)))}
              className="w-20 bg-surface-2 border border-border rounded px-2 py-1 text-text" /> hours
          </label>
          <button className="btn btn-primary" disabled={schedBusy} onClick={saveSchedule}>{schedBusy ? "Saving…" : "Save schedule"}</button>
          <button className="btn" onClick={rescanNow} title="Re-scan all online known drives now">Rescan all drives now</button>
        </div>
        {schedule.data && (
          <p className="text-muted text-xs mt-2">
            Status: {schedule.data.enabled ? `enabled (every ${schedule.data.intervalHours}h)` : "disabled"} ·
            Windows task: <span className={schedule.data.taskRegistered ? "text-ok" : "text-muted"}>{schedule.data.taskRegistered ? "registered" : "not registered"}</span>
          </p>
        )}
        {schedMsg && <p className="text-xs mt-1">{schedMsg}</p>}
      </Card>

      <Card title="Protected folders">
        <p className="text-muted text-sm mb-3">
          Singula will <strong>never delete</strong> from these locations. Built-in system protections (Windows, Program Files, ProgramData, System Volume Information) are always on; the Windows Recycle Bin is now skipped during scans entirely, so its files are never indexed. Add your own never-touch folders — click the folder icon to browse, or type a path. Any file whose path contains one of these is refused at delete time.
        </p>
        <div className="flex gap-2 items-center mb-2">
          <button className="btn px-2" title="Browse for a folder…" aria-label="Browse for folder to protect" onClick={pickProtectedFolder}><FolderOpen size={16} /></button>
          <input className="flex-1 bg-surface-2 border border-border rounded px-2 py-1 text-text font-mono text-sm"
            placeholder="e.g. D:\Archive   or   \Family Photos\" value={newProtected}
            onChange={(e) => setNewProtected(e.target.value)} />
          <button className="btn btn-primary" disabled={!newProtected.trim()}
            onClick={() => {
              const v = newProtected.trim();
              if (v && !(protList ?? []).includes(v)) saveProtected([...(protList ?? []), v]);
              setNewProtected("");
            }}>Add</button>
        </div>
        {(protList ?? []).length === 0 ? (
          <p className="text-muted text-xs">No custom protected folders yet — the system locations above are still protected.</p>
        ) : (
          <ul className="space-y-1">
            {(protList ?? []).map((p) => (
              <li key={p} className="flex items-center gap-2 text-sm">
                <span className="font-mono text-xs flex-1 truncate" title={p}>{p}</span>
                <button className="btn px-2 py-0.5 text-xs" onClick={() => saveProtected((protList ?? []).filter((x) => x !== p))}>Remove</button>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card title="Portability & storage">
        {storage.error && <div role="alert" className="text-danger">Could not read storage info: {storage.error}</div>}
        {storage.data && (
          <dl className="grid grid-cols-2 gap-y-2 text-sm">
            <dt className="text-muted">Mode</dt>
            <dd>
              <span className={`chip ${storage.data.portable ? "online" : "offline"}`}>
                {storage.data.portable ? "PORTABLE (data travels with the app)" : "Installed (data in %APPDATA%)"}
              </span>
            </dd>
            <dt className="text-muted">Data folder</dt><dd className="font-mono text-xs break-all">{storage.data.dataDir}</dd>
            <dt className="text-muted">Index file</dt><dd className="font-mono text-xs break-all">{storage.data.dbPath}</dd>
            <dt className="text-muted">Index size</dt><dd>{bytes(storage.data.dbBytes)}</dd>
          </dl>
        )}
        <p className="text-muted text-xs mt-2">
          Copy the whole app folder (the EXE + the <span className="font-mono">Sift-Data</span> folder beside it)
          to any Windows PC and your entire index — including offline-drive history — comes with it.
        </p>
      </Card>

      <Card title="Media analysis (integrity & quality)">
        <div className="flex items-center gap-3 text-sm">
          <span className="text-muted">ffprobe backend</span>
          {ffprobe.loading && <span className="text-muted">checking…</span>}
          {ffprobe.data && (
            <span className={`chip ${ffprobe.data.available ? "online" : "offline"}`}>
              {ffprobe.data.available ? "available" : "not found"}
            </span>
          )}
          {ffprobe.data?.version && <span className="text-muted text-xs">{ffprobe.data.version}</span>}
        </div>
        {ffprobe.data && !ffprobe.data.available && (
          <p className="text-muted text-xs mt-2">
            Video integrity &amp; quality analysis needs <strong>ffprobe</strong> (part of FFmpeg). Install it with{" "}
            <span className="font-mono bg-surface-2 px-1 rounded">winget install Gyan.FFmpeg</span>, or drop{" "}
            <span className="font-mono">ffprobe.exe</span> next to <span className="font-mono">Sift.exe</span> (it travels with the portable app). Then reopen Settings.
          </p>
        )}
        {ffprobe.data?.available && (
          <p className="text-muted text-xs mt-2">
            Ready. Run analysis from <strong>Index Explorer → “Analyze media quality”</strong>. Results (integrity,
            duration, resolution, bitrate, quality grade) are cached per file and shown as columns there and in the
            Duplicates view. <span className="font-mono">{ffprobe.data.path}</span>
          </p>
        )}
      </Card>

      <Card title="Index & maintenance">
        <dl className="grid grid-cols-2 gap-y-2 text-sm">
          <dt className="text-muted">Database location</dt><dd className="font-mono text-xs">%APPDATA%\Sift\index.sqlite</dd>
          <dt className="text-muted">Hash algorithm</dt><dd>BLAKE3-256 (staged: size → partial → full)</dd>
          <dt className="text-muted">Journal mode</dt><dd>WAL (durable, concurrent reads)</dd>
          <dt className="text-muted">History retention</dt><dd>Until you explicitly clear or rebuild</dd>
        </dl>
        <div className="mt-3 flex items-center gap-3">
          <button className="btn" onClick={runIntegrity} disabled={checking}>{checking ? "Checking…" : "Run integrity check"}</button>
          {integrity && <span className="text-sm">{integrity}</span>}
        </div>
      </Card>

      <Card title="Safety defaults">
        <ul className="list-disc ml-5 text-sm space-y-1">
          <li>Deletions default to the <strong>Recycle Bin</strong>; permanent delete needs an explicit second confirm.</li>
          <li>The engine refuses any plan that removes the last online (or last known) copy.</li>
          <li>Every destructive action is written to the append-only audit log <em>before</em> it runs.</li>
        </ul>
        <p className="text-muted text-xs mt-2">
          Protected paths and retention policies are configured per profile (Phase 4).
        </p>
      </Card>
    </div>
  );
}
