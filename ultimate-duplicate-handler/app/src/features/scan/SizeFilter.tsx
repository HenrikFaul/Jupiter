import { useMemo, useState, useEffect, useCallback } from "react";
import { formatBytes } from "../../lib/contract";

interface Props {
  minBytes: number | null;
  maxBytes: number | null;
  onChange: (min: number | null, max: number | null) => void;
}

// Sensible minimum-size stops for the slider. Index 0 = "Any" (no minimum).
const STOPS: { b: number; label: string }[] = [
  { b: 0, label: "Any" },
  { b: 64 * 1024, label: "64 KB" },
  { b: 256 * 1024, label: "256 KB" },
  { b: 1024 * 1024, label: "1 MB" },
  { b: 5 * 1024 * 1024, label: "5 MB" },
  { b: 10 * 1024 * 1024, label: "10 MB" },
  { b: 50 * 1024 * 1024, label: "50 MB" },
  { b: 100 * 1024 * 1024, label: "100 MB" },
  { b: 250 * 1024 * 1024, label: "250 MB" },
  { b: 500 * 1024 * 1024, label: "500 MB" },
  { b: 1024 ** 3, label: "1 GB" },
  { b: 2 * 1024 ** 3, label: "2 GB" },
  { b: 5 * 1024 ** 3, label: "5 GB" },
  { b: 10 * 1024 ** 3, label: "10 GB" },
  { b: 50 * 1024 ** 3, label: "50 GB" },
];

const PRESETS: { label: string; min: number | null }[] = [
  { label: "Any", min: null },
  { label: "≥ 1 MB", min: 1024 * 1024 },
  { label: "≥ 100 MB", min: 100 * 1024 * 1024 },
  { label: "≥ 1 GB", min: 1024 ** 3 },
];

function nearestIndex(bytes: number | null): number {
  if (!bytes) return 0;
  let best = 0;
  let bestD = Infinity;
  STOPS.forEach((s, i) => {
    const d = Math.abs(s.b - bytes);
    if (d < bestD) {
      bestD = d;
      best = i;
    }
  });
  return best;
}

const MB = 1024 * 1024;
const GB = 1024 ** 3;
const UNIT_BYTES = { MB, GB } as const;
type SizeUnit = keyof typeof UNIT_BYTES;

function autoUnit(bytes: number | null): SizeUnit {
  return bytes && bytes >= GB ? "GB" : "MB";
}
/** Render a byte count as a trimmed number string in the given unit (for the input box). */
function splitForInput(bytes: number | null, unit: SizeUnit): string {
  if (!bytes) return "";
  return String(Math.round((bytes / UNIT_BYTES[unit]) * 100) / 100);
}

export function SizeFilter({ minBytes, maxBytes, onChange }: Props) {
  const idx = useMemo(() => nearestIndex(minBytes), [minBytes]);

  // Free-typed minimum: local text + unit, kept in sync with the bytes prop (which the slider
  // and presets also write), so all three controls stay consistent bidirectionally.
  const [minUnit, setMinUnit] = useState<SizeUnit>(() => autoUnit(minBytes));
  const [minText, setMinText] = useState<string>(() => splitForInput(minBytes, autoUnit(minBytes)));
  useEffect(() => {
    // Re-sync the box from the prop ONLY when they disagree. Crucially, parse the box with
    // the EXACT same rule commitMin uses (empty / non-finite / <= 0 => null), otherwise a
    // transient "0" while typing would look like a disagreement and clobber the keystroke.
    const t = minText.trim();
    const v = parseFloat(t);
    const typed = t === "" || !isFinite(v) || v <= 0 ? null : Math.round(v * UNIT_BYTES[minUnit]);
    if (typed !== (minBytes ?? null)) {
      const u = autoUnit(minBytes);
      setMinUnit(u);
      setMinText(splitForInput(minBytes, u));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [minBytes]);

  const commitMin = useCallback(
    (text: string, unit: SizeUnit) => {
      const t = text.trim();
      const v = parseFloat(t);
      if (t === "" || !isFinite(v) || v <= 0) {
        onChange(null, maxBytes);
      } else {
        onChange(Math.round(v * UNIT_BYTES[unit]), maxBytes);
      }
    },
    [onChange, maxBytes],
  );

  const readout = useMemo(() => {
    if (!minBytes && !maxBytes) return "Any size — no size limit applied";
    if (minBytes && !maxBytes) return `Only files larger than ${formatBytes(minBytes)}`;
    if (!minBytes && maxBytes) return `Only files smaller than ${formatBytes(maxBytes)}`;
    return `Files between ${formatBytes(minBytes!)} and ${formatBytes(maxBytes!)}`;
  }, [minBytes, maxBytes]);

  const active = !!minBytes || !!maxBytes;

  return (
    <div className="space-y-3">
      {/* live feedback chip — confirms the system understood the setting */}
      <div className={`inline-flex items-center gap-2 rounded-md px-3 py-1.5 text-sm border ${active ? "border-accent text-accent bg-accent/10" : "border-border text-muted"}`}>
        <span aria-hidden>{active ? "✓" : "•"}</span>
        <span aria-live="polite">{readout}</span>
      </div>

      {/* presets */}
      <div className="flex flex-wrap gap-2">
        {PRESETS.map((p) => (
          <button
            key={p.label}
            type="button"
            onClick={() => onChange(p.min, maxBytes)}
            className={`btn ${(p.min ?? null) === (minBytes ?? null) ? "border-accent text-accent" : ""}`}
          >
            {p.label}
          </button>
        ))}
      </div>

      {/* minimum-size slider — native range styled blue via accent-color (reliable in
          WebView2; avoids appearance-none which can hide the thumb). */}
      <div>
        <label className="text-muted text-xs">Minimum size</label>
        <input
          type="range"
          min={0}
          max={STOPS.length - 1}
          step={1}
          value={idx}
          onChange={(e) => {
            const b = STOPS[Number(e.target.value)].b;
            onChange(b === 0 ? null : b, maxBytes);
          }}
          className="w-full cursor-pointer"
          aria-label="Minimum file size"
          aria-valuetext={STOPS[idx].label}
        />
        <div className="flex justify-between text-[10px] text-muted">
          <span>Any</span>
          <span className="text-accent font-medium">{STOPS[idx].label}</span>
          <span>50 GB+</span>
        </div>
      </div>

      {/* exact minimum — type the value by hand (kept in sync with the slider/presets) */}
      <div className="flex items-end gap-2">
        <label className="flex flex-col gap-1 text-xs text-muted">
          Minimum size (type exact)
          <input
            type="number"
            min={0}
            step="any"
            placeholder="any"
            value={minText}
            onChange={(e) => { setMinText(e.target.value); commitMin(e.target.value, minUnit); }}
            className="w-32 rounded-md border border-border bg-surface-2 px-2 py-1 text-text"
            aria-label="Minimum file size value"
          />
        </label>
        <select
          value={minUnit}
          onChange={(e) => { const u = e.target.value as SizeUnit; setMinUnit(u); commitMin(minText, u); }}
          className="mb-0.5 rounded-md border border-border bg-surface-2 px-2 py-1 text-text"
          aria-label="Minimum size unit"
        >
          <option value="MB">MB</option>
          <option value="GB">GB</option>
        </select>
      </div>

      {/* optional maximum */}
      <div className="flex items-end gap-2">
        <label className="flex flex-col gap-1 text-xs text-muted">
          Maximum size (optional)
          <input
            type="number"
            min={0}
            placeholder="no limit"
            value={maxBytes ? Math.round(maxBytes / MB) : ""}
            onChange={(e) => onChange(minBytes, e.target.value ? Number(e.target.value) * MB : null)}
            className="w-32 rounded-md border border-border bg-surface-2 px-2 py-1 text-text"
          />
        </label>
        <span className="text-muted text-xs pb-1.5">MB</span>
        {(minBytes || maxBytes) && (
          <button type="button" className="btn ml-auto" onClick={() => onChange(null, null)}>Reset size</button>
        )}
      </div>
    </div>
  );
}
