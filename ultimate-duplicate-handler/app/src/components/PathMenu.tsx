import { useEffect, useState, MouseEvent, ReactNode } from "react";
import { revealInExplorer } from "../lib/contract";

// A reusable right-click context menu for any displayed path: open it in the OS file manager,
// or copy the exact full / folder path to the clipboard. Used anywhere Singula shows a file or
// folder path (Index Explorer rows, the folder-structure tree, …). Getting the path right is the
// whole point — we pass through the same absolute path the row already shows.
type MenuState = { x: number; y: number; path: string; online: boolean } | null;

const MENU_W = 220;

export function usePathMenu() {
  const [menu, setMenu] = useState<MenuState>(null);

  useEffect(() => {
    if (!menu) return;
    const close = () => setMenu(null);
    window.addEventListener("click", close);
    window.addEventListener("scroll", close, true);
    window.addEventListener("resize", close);
    window.addEventListener("keydown", (e) => { if ((e as KeyboardEvent).key === "Escape") close(); });
    return () => {
      window.removeEventListener("click", close);
      window.removeEventListener("scroll", close, true);
      window.removeEventListener("resize", close);
    };
  }, [menu]);

  function openMenu(e: MouseEvent, path: string, online = true) {
    e.preventDefault();
    e.stopPropagation();
    setMenu({ x: e.clientX, y: e.clientY, path, online });
  }

  const folderOf = (p: string) => p.replace(/[\\/][^\\/]*$/, "") || p;

  const node: ReactNode = menu ? (
    <div
      className="fixed z-50 min-w-[200px] rounded-lg border border-border bg-surface shadow-lg py-1 text-sm"
      style={{ left: Math.min(menu.x, window.innerWidth - MENU_W), top: Math.min(menu.y, window.innerHeight - 130) }}
      onClick={(e) => e.stopPropagation()}
      role="menu"
    >
      <div className="px-3 py-1 text-[11px] text-muted font-mono truncate" style={{ maxWidth: MENU_W }} title={menu.path}>{menu.path}</div>
      <button className="w-full text-left px-3 py-1.5 hover:bg-surface-2 disabled:opacity-40" role="menuitem"
        disabled={!menu.online}
        title={menu.online ? "Open in File Explorer" : "drive offline"}
        onClick={() => { revealInExplorer(menu.path).catch(() => {}); setMenu(null); }}>
        Open in File Explorer
      </button>
      <button className="w-full text-left px-3 py-1.5 hover:bg-surface-2" role="menuitem"
        onClick={() => { navigator.clipboard.writeText(menu.path).catch(() => {}); setMenu(null); }}>
        Copy full path
      </button>
      <button className="w-full text-left px-3 py-1.5 hover:bg-surface-2" role="menuitem"
        onClick={() => { navigator.clipboard.writeText(folderOf(menu.path)).catch(() => {}); setMenu(null); }}>
        Copy folder path
      </button>
    </div>
  ) : null;

  return { openMenu, menu: node };
}

/** Join a volume mount point and a volume-relative path into one absolute path (Windows). */
export function joinAbs(mount: string | null | undefined, rel: string): string | null {
  if (!mount) return null;
  const m = mount.replace(/[\\/]+$/, "");
  const r = rel.replace(/^[\\/]+/, "");
  return r ? `${m}\\${r}` : m;
}
