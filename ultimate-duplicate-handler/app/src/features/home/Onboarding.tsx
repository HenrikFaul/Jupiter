import { useState } from "react";
import { open } from "@tauri-apps/plugin-dialog";
import { pictureDir, downloadDir, documentDir } from "@tauri-apps/api/path";
import { SourceInput, defaultFilters, startScan } from "../../lib/contract";
import { useStore } from "../../lib/store";
import { Logo } from "../../components/Logo";

function parseSource(path: string): SourceInput {
  const drive = path.match(/^([A-Za-z]:)[\\/]?(.*)$/);
  if (drive) {
    const rel = drive[2].replace(/[\\/]+$/, "");
    return { volumeMount: `${drive[1]}\\`, relRoot: rel.length ? rel : null };
  }
  return { volumeMount: path, relRoot: null };
}

// First-run onboarding: one obvious action, smart default targets, and the safety promise
// up front — the highest-leverage activation moment (per the UX research).
export function Onboarding() {
  const go = useStore((s) => s.go);
  const [error, setError] = useState<string | null>(null);

  async function scanPath(path: string | null) {
    if (!path) return;
    setError(null);
    try {
      await startScan([parseSource(path)], defaultFilters(), "thorough");
      useStore.setState({ scanRunning: true, scanError: null });
      go("monitor");
    } catch (e) {
      setError(String(e));
    }
  }
  async function scanKnown(kind: "pictures" | "downloads" | "documents") {
    try {
      const dir = kind === "pictures" ? await pictureDir() : kind === "downloads" ? await downloadDir() : await documentDir();
      await scanPath(dir);
    } catch (e) {
      setError(String(e));
    }
  }
  async function pickAndScan() {
    const picked = await open({ directory: true, multiple: false });
    if (typeof picked === "string") await scanPath(picked);
  }

  const targets: { key: "pictures" | "downloads" | "documents"; label: string; icon: string; sub: string }[] = [
    { key: "pictures", label: "Pictures", icon: "🖼", sub: "photos & screenshots" },
    { key: "downloads", label: "Downloads", icon: "⬇", sub: "the usual dupe hotspot" },
    { key: "documents", label: "Documents", icon: "📄", sub: "files & exports" },
  ];

  return (
    <div className="min-h-[70vh] grid place-items-center p-6">
      <div className="max-w-2xl w-full text-center space-y-6">
        <div className="flex flex-col items-center gap-3">
          <Logo size={72} />
          <h1 className="text-2xl font-semibold">Find &amp; safely reclaim duplicate files</h1>
          <p className="text-muted max-w-md">
            Sift builds a persistent, history-aware index of your files and finds exact
            duplicates, similar images, and duplicate folders across your drives.
          </p>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          {targets.map((t) => (
            <button key={t.key} onClick={() => scanKnown(t.key)}
              className="rounded-xl border border-border bg-surface p-4 shadow-sm hover:border-accent text-left transition-colors">
              <div className="text-2xl" aria-hidden>{t.icon}</div>
              <div className="font-medium mt-1">{t.label}</div>
              <div className="text-muted text-xs">{t.sub}</div>
            </button>
          ))}
          <button onClick={pickAndScan}
            className="rounded-xl border border-dashed border-border bg-surface p-4 shadow-sm hover:border-accent text-left transition-colors">
            <div className="text-2xl" aria-hidden>📁</div>
            <div className="font-medium mt-1">Pick a folder…</div>
            <div className="text-muted text-xs">or a whole drive</div>
          </button>
        </div>

        {error && <div role="alert" className="text-danger text-sm">Could not start: {error}</div>}

        <div className="inline-flex items-center gap-2 rounded-full border border-ok/40 bg-ok/5 px-4 py-2 text-sm text-ok">
          <span aria-hidden>🛡</span>
          Nothing is deleted automatically — you review everything first, and deletions go to the Recycle Bin.
        </div>

        <div>
          <button className="text-accent text-sm hover:underline" onClick={() => go("scan")}>
            Advanced scan options →
          </button>
        </div>
      </div>
    </div>
  );
}
