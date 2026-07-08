import { useState } from "react";
import { SourceInput } from "../../lib/contract";

/** Normalize a typed network location into a UNC source. Accepts:
 *   \\server\share              -> { volumeMount: "\\\\server\\share", relRoot: null }
 *   \\server\share\sub\folder   -> { volumeMount: "\\\\server\\share", relRoot: "sub\\folder" }
 *   //server/share/sub          -> same (forward slashes tolerated, normalized to "\\")
 * Returns null when the text is not a UNC path (no leading "\\" or "//"). */
export function parseUncSource(raw: string): SourceInput | null {
  const trimmed = raw.trim();
  if (!/^[\\/]{2}/.test(trimmed)) return null; // must start with \\ or //
  // Normalize all separators to backslash, collapse the leading run to exactly "\\".
  const norm = trimmed.replace(/[\\/]+/g, "\\").replace(/^\\+/, "\\\\");
  const m = norm.match(/^\\\\([^\\]+)\\([^\\]+)(?:\\(.*))?$/);
  if (!m) return null; // need at least \\host\share
  const share = `\\\\${m[1]}\\${m[2]}`;
  const rel = (m[3] ?? "").replace(/\\+$/, "");
  return { volumeMount: share, relRoot: rel.length ? rel : null };
}

/** A typed-path affordance for adding a network share (\\server\share) as a scan source.
 *  The OS directory dialog cannot always reach UNMAPPED UNC roots, so power users need a
 *  raw path field. Emits a parsed SourceInput; the parent pushes it into the same `sources`
 *  state and the unchanged startScan call — no IPC contract change. */
export function NetworkShareInput({ onAdd }: { onAdd: (s: SourceInput) => void }) {
  const [text, setText] = useState("");
  const [error, setError] = useState<string | null>(null);

  function submit() {
    const parsed = parseUncSource(text);
    if (!parsed) {
      setError("Enter a UNC path like \\\\server\\share or \\\\server\\share\\folder");
      return;
    }
    onAdd(parsed);
    setText("");
    setError(null);
  }

  return (
    <div className="mt-3">
      <div className="text-xs text-muted mb-1">Add a network share (NAS / UNC path)</div>
      <div className="flex gap-2">
        <input
          type="text"
          className="flex-1 bg-surface-2 border border-border rounded px-2 py-1 font-mono text-xs"
          placeholder="\\nas\media  or  \\server\share\folder"
          value={text}
          spellCheck={false}
          onChange={(e) => {
            setText(e.target.value);
            if (error) setError(null);
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              submit();
            }
          }}
        />
        <button className="btn" type="button" onClick={submit} disabled={text.trim().length === 0}>
          + Add share
        </button>
      </div>
      {error && (
        <div role="alert" className="text-danger text-xs mt-1">
          {error}
        </div>
      )}
      <p className="text-muted text-[11px] mt-1">
        Cloud placeholders (OneDrive / Dropbox Files-On-Demand) under a scanned folder are
        indexed by metadata only — Sift never downloads them just to look for duplicates.
      </p>
    </div>
  );
}
