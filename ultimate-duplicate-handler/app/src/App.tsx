import { useEffect } from "react";
import { Route, useStore, initScanEvents } from "./lib/store";
import { Logo } from "./components/Logo";
import {
  LayoutDashboard, HardDrive, ScanSearch, Activity, History,
  CopyCheck, Images, Film, FolderTree, Search, RotateCcw, FileText, Settings as SettingsIcon,
  Sun, Moon, Wand2,
  type LucideIcon,
} from "lucide-react";
import { Home } from "./features/home/Home";
import { QuickClean } from "./features/quickclean/QuickClean";
import { SourceManager } from "./features/sources/SourceManager";
import { ScanBuilder } from "./features/scan/ScanBuilder";
import { ScanMonitor } from "./features/scan/ScanMonitor";
import { ScanLog } from "./features/scanlog/ScanLog";
import { Results } from "./features/results/Results";
import { SimilarImages } from "./features/similar/SimilarImages";
import { SimilarVideos } from "./features/similar/SimilarVideos";
import { Folders } from "./features/folders/Folders";
import { IndexExplorer } from "./features/explorer/IndexExplorer";
import { RestoreCenter } from "./features/restore/RestoreCenter";
import { Reports } from "./features/reports/Reports";
import { Settings } from "./features/settings/Settings";

const NAV: { route: Route; label: string; Icon: LucideIcon }[] = [
  { route: "home", label: "Overview", Icon: LayoutDashboard },
  { route: "quickclean", label: "Quick Clean", Icon: Wand2 },
  { route: "sources", label: "Sources & Drives", Icon: HardDrive },
  { route: "scan", label: "New Scan", Icon: ScanSearch },
  { route: "monitor", label: "Scan Monitor", Icon: Activity },
  { route: "scanlog", label: "Scan History", Icon: History },
  { route: "results", label: "Duplicates", Icon: CopyCheck },
  { route: "similar", label: "Similar Images", Icon: Images },
  { route: "videos", label: "Similar Videos", Icon: Film },
  { route: "folders", label: "Duplicate Folders", Icon: FolderTree },
  { route: "explorer", label: "Index Explorer", Icon: Search },
  { route: "restore", label: "Restore Center", Icon: RotateCcw },
  { route: "reports", label: "Reports & Audit", Icon: FileText },
  { route: "settings", label: "Settings", Icon: SettingsIcon },
];

export function App() {
  const route = useStore((s) => s.route);
  const go = useStore((s) => s.go);
  const scanRunning = useStore((s) => s.scanRunning);
  const theme = useStore((s) => s.theme);
  const toggleTheme = useStore((s) => s.toggleTheme);

  useEffect(() => {
    // Guard against the async-cleanup race (React 18 StrictMode runs effects twice in dev):
    // if we unmount before initScanEvents resolves, dispose the moment it does so we never
    // leak a second set of Rust event listeners (which would double-fire every scan/media event).
    let dispose: (() => void) | undefined;
    let cancelled = false;
    initScanEvents().then((d) => {
      if (cancelled) d();
      else dispose = d;
    });
    return () => {
      cancelled = true;
      dispose?.();
    };
  }, []);

  return (
    <div className="h-full flex bg-bg text-text">
      {/* Sidebar — the enterprise brand chrome: deep navy, always dark, with the glowing mark. */}
      <nav className="w-[244px] shrink-0 flex flex-col bg-sidebar text-sidebar-text select-none">
        <div className="px-4 py-4 flex items-center gap-3 border-b border-white/10">
          <Logo size={54} glow halo />
          <div>
            <div className="text-[19px] font-semibold tracking-tight leading-none text-white">Singula</div>
            <div className="text-[8.5px] tracking-[0.24em] text-cyan mt-1.5 font-medium">FILE&nbsp;INTELLIGENCE</div>
          </div>
        </div>
        <ul className="flex-1 px-2.5 py-2 space-y-0.5 overflow-y-auto">
          {NAV.map((n) => {
            const active = route === n.route;
            return (
              <li key={n.route}>
                <button
                  onClick={() => go(n.route)}
                  aria-current={active}
                  className={`w-full text-left pl-3 pr-3 py-2 rounded-lg flex items-center gap-2.5 border-l-2 transition-colors ${
                    active
                      ? "bg-accent/15 text-white border-cyan font-medium"
                      : "text-sidebar-muted hover:text-white hover:bg-white/[0.06] border-transparent"
                  }`}
                >
                  <n.Icon size={17} className={`shrink-0 ${active ? "text-cyan" : ""}`} aria-hidden />
                  {n.label}
                  {n.route === "monitor" && scanRunning && (
                    <span className="ml-auto w-2 h-2 rounded-full bg-cyan animate-pulse" />
                  )}
                </button>
              </li>
            );
          })}
        </ul>
        <div className="border-t border-white/10 px-3 py-2.5 flex items-center justify-between gap-2">
          <span className="text-[9px] tracking-[0.16em] text-sidebar-muted">PERSISTENT · HISTORY-AWARE</span>
          <button onClick={toggleTheme} aria-label="Toggle day / night mode"
            title={`Switch to ${theme === "dark" ? "day" : "night"} mode`}
            className="flex items-center gap-1.5 rounded-md px-2 py-1 text-sidebar-text hover:bg-white/10 transition-colors">
            {theme === "dark" ? <Sun size={14} className="text-cyan" /> : <Moon size={14} className="text-cyan" />}
            <span className="text-[10px]">{theme === "dark" ? "Day" : "Night"}</span>
          </button>
        </div>
      </nav>

      {/* Main */}
      <main className="flex-1 overflow-auto relative">
        {/* Branded header wash — a crisp top accent rule + a cyan→teal gradient that
            fades gradually down the top of every page. Token-driven so it adapts to theme. */}
        <div aria-hidden className="pointer-events-none absolute inset-x-0 top-0 z-0">
          {/* Bold branded top rule + an aggressive navy wash that fades fast, so the dark
              page title (which sits ~24px down) stays on a light, readable band. */}
          <div
            className="h-[5px] w-full"
            style={{ background: "linear-gradient(90deg, rgb(var(--sidebar)) 0%, rgb(var(--accent)) 50%, rgb(var(--cyan)) 100%)" }}
          />
          <div
            className="h-36"
            style={{ background: "linear-gradient(180deg, rgb(var(--sidebar) / 0.72) 0%, rgb(var(--sidebar) / 0.22) 13%, rgb(var(--accent) / 0.12) 36%, rgb(var(--cyan) / 0) 100%)" }}
          />
        </div>
        <div className="relative z-10">
          {route === "home" && <Home />}
          {route === "quickclean" && <QuickClean />}
          {route === "sources" && <SourceManager />}
          {route === "scan" && <ScanBuilder />}
          {route === "monitor" && <ScanMonitor />}
          {route === "scanlog" && <ScanLog />}
          {route === "results" && <Results />}
          {route === "similar" && <SimilarImages />}
          {route === "videos" && <SimilarVideos />}
          {route === "folders" && <Folders />}
          {route === "explorer" && <IndexExplorer />}
          {route === "restore" && <RestoreCenter />}
          {route === "reports" && <Reports />}
          {route === "settings" && <Settings />}
        </div>
      </main>
    </div>
  );
}
