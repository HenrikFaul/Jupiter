import { useMemo, useState } from "react";
import {
  FILE_CATALOG,
  allCatalogExts,
  categoryOfExt,
  parseExtInput,
} from "../../lib/filetypes";

interface Props {
  value: string[]; // selected extensions (catalog + custom), lowercased, no dot
  onChange: (exts: string[]) => void;
}

// Smart file-type picker: pick from a categorized catalog (preferred), with per-category
// and global select/clear, live search, AND a free-text box for anything custom. Custom
// extensions that aren't in the catalog surface under their own group so they're visible.
export function FileTypePicker({ value, onChange }: Props) {
  const [search, setSearch] = useState("");
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [customText, setCustomText] = useState("");

  const selected = useMemo(() => new Set(value.map((e) => e.toLowerCase())), [value]);
  const q = search.trim().toLowerCase();

  function setMany(exts: string[], on: boolean) {
    const next = new Set(selected);
    for (const e of exts) (on ? next.add(e) : next.delete(e));
    onChange([...next]);
  }
  const toggleExt = (ext: string) => setMany([ext], !selected.has(ext));

  function selectAllCatalog() {
    onChange([...new Set([...value, ...allCatalogExts()])]);
  }
  function clearAll() {
    onChange([]);
  }

  const customExts = value.filter((e) => !categoryOfExt(e));
  const totalSelected = value.length;

  const visibleCats = FILE_CATALOG.map((c) => {
    const labelHit = q.length > 0 && c.label.toLowerCase().includes(q);
    return { cat: c, matches: !q || labelHit ? c.exts : c.exts.filter((e) => e.includes(q)) };
  }).filter((x) => x.matches.length > 0);

  return (
    <div className="space-y-3">
      {/* toolbar */}
      <div className="flex flex-wrap items-center gap-2">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search types… (e.g. mp4, raw)"
          className="flex-1 min-w-[12rem] rounded-md border border-border bg-surface-2 px-3 py-1.5"
          aria-label="Search file types"
        />
        <button type="button" className="btn" onClick={selectAllCatalog}>Select all</button>
        <button type="button" className="btn" onClick={clearAll}>Clear all</button>
        <span className="chip" title="extensions selected">
          {totalSelected === 0 ? "All types" : `${totalSelected} selected`}
        </span>
      </div>

      {totalSelected === 0 && (
        <p className="text-muted text-xs">No filter set — <strong>all file types</strong> will be scanned. Pick categories below to narrow it.</p>
      )}

      {/* categories */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
        {visibleCats.map(({ cat, matches }) => {
          const inCat = cat.exts.filter((e) => selected.has(e)).length;
          const all = inCat === cat.exts.length;
          const some = inCat > 0 && !all;
          const isOpen = expanded.has(cat.key) || q.length > 0;
          return (
            <div key={cat.key} className="rounded-lg border border-border bg-surface">
              <div className="flex items-center gap-2 px-3 py-2">
                <input
                  type="checkbox"
                  checked={all}
                  ref={(el) => el && (el.indeterminate = some)}
                  onChange={(e) => setMany(cat.exts, e.target.checked)}
                  aria-label={`Select all ${cat.label}`}
                />
                <button
                  type="button"
                  className="flex-1 flex items-center gap-2 text-left"
                  onClick={() =>
                    setExpanded((s) => {
                      const n = new Set(s);
                      n.has(cat.key) ? n.delete(cat.key) : n.add(cat.key);
                      return n;
                    })
                  }
                >
                  <span aria-hidden>{cat.icon}</span>
                  <span className="font-medium">{cat.label}</span>
                  <span className="text-muted text-xs">{inCat}/{cat.exts.length}</span>
                  <span className="ml-auto text-muted">{isOpen ? "▾" : "▸"}</span>
                </button>
              </div>
              {isOpen && (
                <div className="flex flex-wrap gap-1.5 px-3 pb-3">
                  {matches.map((ext) => {
                    const on = selected.has(ext);
                    return (
                      <button
                        key={ext}
                        type="button"
                        onClick={() => toggleExt(ext)}
                        aria-pressed={on}
                        className={`px-2 py-0.5 rounded-full text-xs border transition-colors ${
                          on
                            ? "bg-accent/15 border-accent text-accent"
                            : "border-border text-muted hover:border-accent/60"
                        }`}
                      >
                        {on ? "✓ " : ""}.{ext}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* custom free-text */}
      <div className="rounded-lg border border-dashed border-border bg-surface p-3 space-y-2">
        <div className="flex items-center gap-2">
          <span aria-hidden>➕</span>
          <span className="font-medium">Custom extensions</span>
          <span className="text-muted text-xs">not in the catalog above</span>
        </div>
        <div className="flex gap-2">
          <input
            value={customText}
            onChange={(e) => setCustomText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                addCustom();
              }
            }}
            placeholder="e.g. dwg, prproj, blend1  (comma or space separated)"
            className="flex-1 rounded-md border border-border bg-surface-2 px-3 py-1.5"
          />
          <button type="button" className="btn" onClick={addCustom}>Add</button>
        </div>
        {customExts.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {customExts.map((ext) => (
              <span key={ext} className="px-2 py-0.5 rounded-full text-xs border border-accent text-accent bg-accent/10 flex items-center gap-1">
                .{ext}
                <button type="button" aria-label={`remove ${ext}`} onClick={() => setMany([ext], false)} className="hover:text-danger">✕</button>
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  );

  function addCustom() {
    const exts = parseExtInput(customText);
    if (exts.length) onChange([...new Set([...value, ...exts])]);
    setCustomText("");
  }
}
