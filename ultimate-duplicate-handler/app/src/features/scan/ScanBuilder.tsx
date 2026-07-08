import { useState } from "react";
import { open } from "@tauri-apps/plugin-dialog";
import {
  ScanFilters,
  ScanMode,
  SourceInput,
  defaultFilters,
  startScan,
} from "../../lib/contract";
import { useStore } from "../../lib/store";
import { Card } from "../../components/ui";
import { FileTypePicker } from "./FileTypePicker";
import { SizeFilter } from "./SizeFilter";

/** Split an absolute Windows path into (volumeMount, relRoot). A drive-letter root
 *  becomes the mount; the remainder is the folder within the volume. */
function parseSource(path: string): SourceInput {
  const drive = path.match(/^([A-Za-z]:)[\\/]?(.*)$/);
  if (drive) {
    const rel = drive[2].replace(/[\\/]+$/, "");
    return { volumeMount: `${drive[1]}\\`, relRoot: rel.length ? rel : null };
  }
  return { volumeMount: path, relRoot: null }; // UNC or non-standard: scan as-is
}

export function ScanBuilder() {
  const go = useStore((s) => s.go);
  const [sources, setSources] = useState<SourceInput[]>([]);
  const [filters, setFilters] = useState<ScanFilters>(defaultFilters());
  const [mode, setMode] = useState<ScanMode>("thorough");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function addFolders() {
    const picked = await open({ directory: true, multiple: true });
    if (!picked) return;
    const list = Array.isArray(picked) ? picked : [picked];
    setSources((prev) => [...prev, ...list.map(parseSource)]);
  }

  async function launch() {
    setBusy(true);
    setError(null);
    try {
      await startScan(sources, filters, mode);
      useStore.setState({ scanRunning: true, scanError: null });
      go("monitor");
    } catch (e) {
      setError(String(e)); // fail loud (§2)
    } finally {
      setBusy(false);
    }
  }

  const setF = (patch: Partial<ScanFilters>) => setFilters((f) => ({ ...f, ...patch }));

  return (
    <div className="p-6 space-y-6 max-w-4xl">
      <header>
        <h1 className="text-xl font-semibold">New scan</h1>
        <p className="text-muted">Choose what to scan, narrow with filters, pick a strategy.</p>
      </header>

      <Card title="1 · Sources">
        <div className="flex gap-2 mb-2">
          <button className="btn" onClick={addFolders}>+ Add folders / drives…</button>
          {sources.length > 0 && (
            <button className="btn" onClick={() => setSources([])}>Clear</button>
          )}
        </div>
        <p className="text-muted text-xs mb-3">
          Local drives, external/USB, <strong>network shares &amp; NAS</strong> (<span className="font-mono">\\server\share</span>) and mapped drives all work — pick them like any folder. Cloud-only files (OneDrive/Dropbox/Drive “Files On-Demand”) are skipped so dedup never force-downloads them.
        </p>
        {sources.length === 0 ? (
          <p className="text-muted">No sources yet. Add one or more folders or whole drives.</p>
        ) : (
          <ul className="space-y-1 font-mono text-xs">
            {sources.map((s, i) => (
              <li key={i} className="flex items-center gap-2">
                <span className="chip">{s.volumeMount}</span>
                <span>{s.relRoot ?? "(entire volume)"}</span>
                <button className="ml-auto text-muted hover:text-danger" onClick={() => setSources((p) => p.filter((_, j) => j !== i))}>✕</button>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card title="2 · Filters">
        <div className="grid grid-cols-2 gap-3 mb-4">
          <label className="flex items-center gap-2"><input type="checkbox" checked={filters.skipZeroByte} onChange={(e) => setF({ skipZeroByte: e.target.checked })} /> Skip zero-byte files</label>
          <label className="flex items-center gap-2"><input type="checkbox" checked={filters.includeHidden} onChange={(e) => setF({ includeHidden: e.target.checked })} /> Include hidden files</label>
          <label className="flex items-center gap-2"><input type="checkbox" checked={filters.includeSystem} onChange={(e) => setF({ includeSystem: e.target.checked })} /> Include system files</label>
          <label className="flex items-center gap-2"><input type="checkbox" checked={filters.followReparse} onChange={(e) => setF({ followReparse: e.target.checked })} /> Follow junctions/symlinks &amp; include cloud placeholders <span className="text-warn text-xs">(may download cloud files)</span></label>
        </div>

        <div className="mb-2 font-medium">File size</div>
        <SizeFilter
          minBytes={filters.minSizeBytes ?? null}
          maxBytes={filters.maxSizeBytes ?? null}
          onChange={(min, max) => setF({ minSizeBytes: min, maxSizeBytes: max })}
        />

        <div className="mt-5 mb-2 font-medium">File types</div>
        <FileTypePicker
          value={filters.includeExts}
          onChange={(exts) => setF({ includeExts: exts })}
        />
      </Card>

      <Card title="3 · Strategy">
        <div className="flex gap-3">
          {(["thorough", "lightweight"] as ScanMode[]).map((m) => (
            <button key={m} onClick={() => setMode(m)} aria-pressed={mode === m}
              className={`btn ${mode === m ? "btn-primary" : ""}`}>
              {mode === m ? "✓ " : ""}{m === "thorough" ? "Thorough (full-content confirm)" : "Lightweight (partial fingerprint)"}
            </button>
          ))}
        </div>
        <p className="text-muted text-xs mt-2">
          Thorough confirms every duplicate with a full BLAKE3 hash. Lightweight stops at a
          head+tail fingerprint — faster, slightly lower confidence.
        </p>
      </Card>

      {error && <div role="alert" className="text-danger">Could not start scan: {error}</div>}

      <div className="flex gap-3">
        <button className="btn btn-primary" disabled={sources.length === 0 || busy} onClick={launch}>
          {busy ? "Starting…" : "Start scan →"}
        </button>
      </div>
    </div>
  );
}
