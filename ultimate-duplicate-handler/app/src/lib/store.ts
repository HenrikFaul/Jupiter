import { create } from "zustand";
import {
  ScanProgress,
  ScanLogEvent,
  MediaProgress,
  MediaAnalyzeResult,
  onScanProgress,
  onScanDone,
  onScanError,
  onScanLog,
  onMediaProgress,
  onMediaDone,
  onMediaError,
} from "./contract";

const LOG_CAP = 500; // bounded so a long scan can't grow the buffer unboundedly

// ----- theme (day / night) — centralized so the sidebar toggle and Settings stay in sync,
// and the choice persists across restarts. -----
export type Theme = "light" | "dark";
function readTheme(): Theme {
  try {
    const t = localStorage.getItem("sift-theme");
    if (t === "dark" || t === "light") return t;
  } catch { /* localStorage may be unavailable */ }
  return "light";
}
function applyTheme(t: Theme) {
  const el = document.documentElement;
  el.classList.toggle("dark", t === "dark");
  el.classList.toggle("light", t === "light");
}

export type Route =
  | "home"
  | "quickclean"
  | "sources"
  | "scan"
  | "monitor"
  | "scanlog"
  | "results"
  | "similar"
  | "videos"
  | "folders"
  | "explorer"
  | "restore"
  | "reports"
  | "settings";

interface AppStore {
  route: Route;
  go: (r: Route) => void;

  // scan lifecycle (driven by Rust scan:// events)
  scanRunning: boolean;
  progress: ScanProgress | null;
  scanError: string | null;
  lastJobId: number | null;
  setRunning: (b: boolean) => void;

  // live scan telemetry log (bounded; reset at each scan's "started")
  scanLog: ScanLogEvent[];
  clearScanLog: () => void;

  // media-analysis lifecycle (driven by Rust media:// events)
  mediaRunning: boolean;
  mediaProgress: MediaProgress | null;
  mediaResult: MediaAnalyzeResult | null;
  mediaError: string | null;

  // a monotonically-increasing token so screens can refetch after a scan completes
  dataVersion: number;
  bumpData: () => void;

  // appearance (day / night)
  theme: Theme;
  toggleTheme: () => void;
  setTheme: (t: Theme) => void;
}

export const useStore = create<AppStore>((set) => ({
  route: "home",
  go: (route) => set({ route }),

  scanRunning: false,
  progress: null,
  scanError: null,
  lastJobId: null,
  setRunning: (scanRunning) => set({ scanRunning }),

  scanLog: [],
  clearScanLog: () => set({ scanLog: [] }),

  mediaRunning: false,
  mediaProgress: null,
  mediaResult: null,
  mediaError: null,

  dataVersion: 0,
  bumpData: () => set((s) => ({ dataVersion: s.dataVersion + 1 })),

  theme: (() => { const t = readTheme(); applyTheme(t); return t; })(),
  setTheme: (t) => { applyTheme(t); try { localStorage.setItem("sift-theme", t); } catch { /* ignore */ } set({ theme: t }); },
  toggleTheme: () => set((s) => {
    const next: Theme = s.theme === "dark" ? "light" : "dark";
    applyTheme(next);
    try { localStorage.setItem("sift-theme", next); } catch { /* ignore */ }
    return { theme: next };
  }),
}));

/** Wire the Rust scan event stream into the store exactly once. Returns a disposer. */
export async function initScanEvents(): Promise<() => void> {
  const offProgress = await onScanProgress((p) =>
    useStore.setState({ progress: p, scanRunning: p.stage !== "done" }),
  );
  const offDone = await onScanDone((jobId) => {
    useStore.setState((s) => ({
      scanRunning: false,
      lastJobId: jobId,
      dataVersion: s.dataVersion + 1, // signal screens to refetch
    }));
  });
  // Fail loud: surface scan errors to the UI, never swallow (CODING_RULES §2).
  const offError = await onScanError((msg) =>
    useStore.setState({ scanError: msg, scanRunning: false }),
  );
  // Live telemetry log. The "started" event resets the buffer for the new scan.
  const offLog = await onScanLog((e) => {
    useStore.setState((s) => {
      const base = e.kind === "started" ? [] : s.scanLog;
      const next = [...base, e];
      return { scanLog: next.length > LOG_CAP ? next.slice(-LOG_CAP) : next };
    });
  });
  // ----- media analysis stream -----
  const offMediaProgress = await onMediaProgress((p) =>
    useStore.setState({ mediaRunning: true, mediaProgress: p, mediaError: null }),
  );
  const offMediaDone = await onMediaDone((r) => {
    useStore.setState((s) => ({
      mediaRunning: false,
      mediaResult: r,
      // refetch so the new columns appear, but only if something was actually written
      dataVersion: r.newlyAnalyzed > 0 ? s.dataVersion + 1 : s.dataVersion,
    }));
  });
  const offMediaError = await onMediaError((msg) =>
    useStore.setState({ mediaError: msg, mediaRunning: false }),
  );

  return () => {
    offProgress();
    offDone();
    offError();
    offLog();
    offMediaProgress();
    offMediaDone();
    offMediaError();
  };
}
