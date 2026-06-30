// Lazy, collapsible "classic" folder-structure view of the indexed data (TreeSize-style):
// expand a drive to see its top folders with recursive size + subfolder/file counts, drill
// in node by node. Children are fetched on demand (one query per expanded node), so it stays
// fast even on a whole-drive index. Reflects the index at query time — handy to watch what a
// running scan has populated so far (hit Refresh to re-read).
//
// Every row supports a right-click menu (Open in File Explorer / Copy path) — the absolute path
// is composed from the volume mount point + the node's volume-relative path.
import { useState, MouseEvent } from "react";
import { ChevronRight, ChevronDown, Folder, HardDrive, RotateCw } from "lucide-react";
import { folderChildren, listVolumes, FolderNode, VolumeView } from "../../lib/contract";
import { useAsync } from "../../lib/useAsync";
import { bytes } from "../../components/ui";
import { usePathMenu, joinAbs } from "../../components/PathMenu";

const INDENT = 14; // px per depth level
type OpenMenu = (e: MouseEvent, path: string, online?: boolean) => void;

function Meta({ size, folders, files }: { size: number; folders: number; files: number }) {
  return (
    <span className="ml-auto pl-3 shrink-0 text-xs text-muted tabular-nums">
      <span className="text-accent font-medium">{bytes(size)}</span>
      {" · "}
      {folders.toLocaleString()} folders · {files.toLocaleString()} files
    </span>
  );
}

/** A relative-size bar (fraction of the largest sibling) — the visual TreeSize cue. */
function Bar({ frac }: { frac: number }) {
  return (
    <span className="hidden sm:block w-16 h-1.5 rounded-full bg-surface-2 overflow-hidden shrink-0" aria-hidden>
      <span className="block h-full rounded-full bg-accent/60" style={{ width: `${Math.max(2, Math.round(frac * 100))}%` }} />
    </span>
  );
}

function Row({
  depth, open, expandable, onToggle, onContextMenu, icon, name, children, frac,
}: {
  depth: number;
  open: boolean;
  expandable: boolean;
  onToggle: () => void;
  onContextMenu?: (e: MouseEvent) => void;
  icon: React.ReactNode;
  name: React.ReactNode;
  children: React.ReactNode; // the Meta block
  frac: number;
}) {
  return (
    <div
      className={`flex items-center gap-2 pr-2 py-1 rounded ${expandable ? "cursor-pointer hover:bg-surface-2" : ""}`}
      style={{ paddingLeft: depth * INDENT + 4 }}
      onClick={expandable ? onToggle : undefined}
      onContextMenu={onContextMenu}
    >
      <span className="w-4 shrink-0 text-muted">
        {expandable ? (open ? <ChevronDown size={14} /> : <ChevronRight size={14} />) : null}
      </span>
      <span className="shrink-0 text-accent">{icon}</span>
      <span className="truncate text-sm" title={typeof name === "string" ? name : undefined}>{name}</span>
      <Bar frac={frac} />
      {children}
    </div>
  );
}

function ChildList({ volumeId, mount, online, parentRel, depth, reloadKey, openMenu }: { volumeId: number; mount: string | null; online: boolean; parentRel: string; depth: number; reloadKey: number; openMenu: OpenMenu }) {
  const children = useAsync(() => folderChildren(volumeId, parentRel), [volumeId, parentRel, reloadKey]);
  if (children.loading) return <div style={{ paddingLeft: depth * INDENT + 26 }} className="text-xs text-muted py-1">Loading…</div>;
  if (children.error) return <div style={{ paddingLeft: depth * INDENT + 26 }} className="text-xs text-danger py-1">{children.error}</div>;
  if (!children.data || children.data.length === 0)
    return <div style={{ paddingLeft: depth * INDENT + 26 }} className="text-xs text-muted py-1">(no subfolders)</div>;
  const max = Math.max(...children.data.map((c) => c.totalBytes), 1);
  return (
    <>
      {children.data.map((node) => (
        <FolderRow key={node.relPath} volumeId={volumeId} mount={mount} online={online} node={node} depth={depth} frac={node.totalBytes / max} reloadKey={reloadKey} openMenu={openMenu} />
      ))}
    </>
  );
}

function FolderRow({ volumeId, mount, online, node, depth, frac, reloadKey, openMenu }: { volumeId: number; mount: string | null; online: boolean; node: FolderNode; depth: number; frac: number; reloadKey: number; openMenu: OpenMenu }) {
  const [open, setOpen] = useState(false);
  const abs = joinAbs(mount, node.relPath);
  return (
    <div>
      <Row depth={depth} open={open} expandable={node.hasChildren} onToggle={() => setOpen((o) => !o)}
        onContextMenu={abs ? (e) => openMenu(e, abs, online) : undefined}
        icon={<Folder size={15} />} name={node.name} frac={frac}>
        <Meta size={node.totalBytes} folders={node.subfolderCount} files={node.fileCount} />
      </Row>
      {open && <ChildList volumeId={volumeId} mount={mount} online={online} parentRel={node.relPath} depth={depth + 1} reloadKey={reloadKey} openMenu={openMenu} />}
    </div>
  );
}

function VolumeRow({ v, reloadKey, openMenu }: { v: VolumeView; reloadKey: number; openMenu: OpenMenu }) {
  const [open, setOpen] = useState(false);
  return (
    <div>
      <Row depth={0} open={open} expandable={v.fileCount > 0} onToggle={() => setOpen((o) => !o)}
        onContextMenu={v.mountPoint ? (e) => openMenu(e, v.mountPoint as string, v.isOnline) : undefined}
        icon={<HardDrive size={15} />} frac={1}
        name={<span className="font-medium">{v.label}{v.isOnline ? "" : " (offline)"}</span>}>
        <Meta size={v.indexedBytes} folders={0} files={v.fileCount} />
      </Row>
      {open && <ChildList volumeId={v.volumeId} mount={v.mountPoint} online={v.isOnline} parentRel="" depth={1} reloadKey={reloadKey} openMenu={openMenu} />}
    </div>
  );
}

export function FolderTree() {
  const [reloadKey, setReloadKey] = useState(0);
  const volumes = useAsync(listVolumes, [reloadKey]);
  const { openMenu, menu } = usePathMenu();

  return (
    <div>
      <div className="flex items-center justify-between mb-2">
        <p className="text-muted text-xs">Expand a drive to walk the indexed folder structure — size, subfolders and files roll up recursively. Right-click any row to open it in Explorer or copy its path.</p>
        <button className="btn px-2 py-0.5 text-xs flex items-center gap-1" onClick={() => setReloadKey((k) => k + 1)} title="Re-read the index (e.g. to see new folders a running scan added)">
          <RotateCw size={13} /> Refresh
        </button>
      </div>
      {volumes.loading && <p className="text-muted text-sm">Loading drives…</p>}
      {volumes.error && <p className="text-danger text-sm">Error: {volumes.error}</p>}
      {volumes.data && volumes.data.length === 0 && <p className="text-muted text-sm">No indexed drives yet — run a scan to populate the tree.</p>}
      {volumes.data && volumes.data.length > 0 && (
        <div className="max-h-[28rem] overflow-auto border border-border rounded-lg p-1 bg-bg/40">
          {volumes.data.map((v) => <VolumeRow key={v.volumeId} v={v} reloadKey={reloadKey} openMenu={openMenu} />)}
        </div>
      )}
      {menu}
    </div>
  );
}
