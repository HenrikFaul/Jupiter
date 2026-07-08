// Shared presentational atoms for media integrity & technical-quality signals.
// Used by Index Explorer, the Duplicates inspector, and Similar Images so the badges,
// formatting, and keeper logic stay consistent. Token-only styling (FORGE Law 5).
import { FileView, Integrity, MediaMeta } from "../lib/contract";

export function fmtDuration(s: number | null): string {
  if (!s || s <= 0) return "—";
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = Math.floor(s % 60);
  const pad = (n: number) => String(n).padStart(2, "0");
  return h > 0 ? `${h}:${pad(m)}:${pad(sec)}` : `${m}:${pad(sec)}`;
}

/** Short resolution tag, e.g. "4K", "1080p", "720p". null if unknown. */
export function resTag(m: MediaMeta): string | null {
  const h = m.height ?? 0;
  if (!h) return null;
  if (h >= 2160) return "4K";
  if (h >= 1080) return "1080p";
  if (h >= 720) return "720p";
  if (h >= 480) return "480p";
  return `${h}p`;
}

export function fmtResolution(m: MediaMeta): string {
  if (m.width && m.height) return `${m.width}×${m.height}`;
  return "—";
}

export function fmtBitrate(b: number | null): string {
  if (!b || b <= 0) return "—";
  return b >= 1_000_000 ? `${(b / 1_000_000).toFixed(1)} Mbps` : `${Math.round(b / 1000)} kbps`;
}

const INTEG_STYLE: Record<Integrity, string> = {
  healthy: "bg-ok/15 text-ok",
  suspicious: "bg-warn/15 text-warn",
  partial: "bg-danger/15 text-danger",
  corrupted: "bg-danger/15 text-danger",
  unreadable: "bg-danger/15 text-danger",
};
const INTEG_LABEL: Record<Integrity, string> = {
  healthy: "Healthy",
  suspicious: "Suspicious",
  partial: "Partial",
  corrupted: "Corrupted",
  unreadable: "Unreadable",
};

export function IntegrityBadge({ media }: { media: MediaMeta | null }) {
  if (!media) return <span className="text-muted text-xs">—</span>;
  return (
    <span
      className={`inline-flex items-center rounded px-1.5 py-0.5 text-[11px] font-medium whitespace-nowrap ${INTEG_STYLE[media.integrity]}`}
      title={media.warnReason ?? `Integrity: ${media.integrity}`}
    >
      {INTEG_LABEL[media.integrity]}
    </span>
  );
}

export function QualityBadge({ media }: { media: MediaMeta | null }) {
  if (!media || media.qualityGrade === "unknown") return <span className="text-muted text-xs">—</span>;
  const style =
    media.qualityGrade === "good"
      ? "bg-ok/15 text-ok"
      : media.qualityGrade === "poor"
        ? "bg-danger/15 text-danger"
        : "bg-warn/15 text-warn";
  return (
    <span
      className={`inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[11px] font-medium whitespace-nowrap ${style}`}
      title={media.warnReason ?? `Quality: ${media.qualityGrade}`}
    >
      {media.qualityWarning && <span aria-hidden>⚠</span>}
      {media.qualityGrade}
    </span>
  );
}

/** Compact one-line media summary (for tight layouts like the similar-image cards). */
export function MediaSummary({ media }: { media: MediaMeta | null }) {
  if (!media) return null;
  return (
    <div className="flex flex-wrap items-center gap-1.5 mt-1">
      <IntegrityBadge media={media} />
      {media.durationS ? <span className="text-muted text-[11px]">{fmtDuration(media.durationS)}</span> : null}
      {media.height ? <span className="text-muted text-[11px]">{resTag(media)}</span> : null}
      {media.bitrate ? <span className="text-muted text-[11px]">{fmtBitrate(media.bitrate)}</span> : null}
      <QualityBadge media={media} />
    </div>
  );
}

/** True when the members differ in any technical-quality signal — i.e. a keeper-by-quality
 *  recommendation is actually meaningful (for byte-identical exact duplicates it is not). */
export function mediaVaries(files: FileView[]): boolean {
  const sig = (f: FileView) =>
    f.media ? `${f.media.integrity}|${f.media.qualityGrade}|${f.media.width}x${f.media.height}|${f.media.bitrate}` : "none";
  const seen = new Set(files.map(sig));
  return seen.size > 1;
}

const INTEG_RANK: Record<Integrity, number> = {
  healthy: 4,
  suspicious: 3,
  partial: 2,
  corrupted: 1,
  unreadable: 0,
};

/**
 * Recommend which copy to KEEP based on health + technical quality: prefer the healthiest,
 * then highest resolution, then highest bitrate, then largest file. Returns null when no
 * member carries media metadata (nothing to differentiate on — caller falls back to its own
 * logic, e.g. "keep one online copy"). Present files are preferred over missing ones.
 */
export function recommendKeeper(files: FileView[]): number | null {
  const present = files.filter((f) => f.status === "present");
  const pool = present.length ? present : files;
  if (!pool.some((f) => f.media)) return null;
  const score = (f: FileView): number[] => {
    const m = f.media;
    return [
      m ? INTEG_RANK[m.integrity] : 3, // unanalyzed sits between suspicious & healthy
      m?.width && m?.height ? m.width * m.height : 0,
      m?.bitrate ?? 0,
      f.sizeBytes,
    ];
  };
  const better = (a: number[], b: number[]): boolean => {
    for (let i = 0; i < a.length; i++) {
      if (a[i] !== b[i]) return a[i] > b[i];
    }
    return false;
  };
  let best = pool[0];
  let bestScore = score(best);
  for (const f of pool.slice(1)) {
    const s = score(f);
    if (better(s, bestScore)) {
      best = f;
      bestScore = s;
    }
  }
  return best.fileId;
}
