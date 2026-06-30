import { useEffect, useState } from "react";
import { open } from "@tauri-apps/plugin-dialog";
import { FolderOpen } from "lucide-react";
import { CustomAction, getActions, saveAction, deleteAction } from "../../lib/contract";
import { Card } from "../../components/ui";

// Custom actions ("plugins") — user-defined external tools that run on the file(s) selected in
// the Index Explorer. Persisted in Sift-Data/actions.json; only ever run on explicit trigger.
export function ActionsCard() {
  const [actions, setActions] = useState<CustomAction[]>([]);
  const [name, setName] = useState("");
  const [program, setProgram] = useState("");
  const [args, setArgs] = useState("%path%");
  const [msg, setMsg] = useState<string | null>(null);

  useEffect(() => { getActions().then(setActions).catch(() => {}); }, []);

  async function add() {
    if (!name.trim() || !program.trim()) { setMsg("Name and program are both required."); return; }
    try {
      setActions(await saveAction({ name: name.trim(), program: program.trim(), args }));
      setName(""); setProgram(""); setArgs("%path%"); setMsg(null);
    } catch (e) { setMsg(String(e)); }
  }
  async function remove(n: string) {
    try { setActions(await deleteAction(n)); } catch (e) { setMsg(String(e)); }
  }
  async function pickProgram() {
    try {
      const picked = await open({ directory: false, multiple: false, title: "Choose a program" });
      if (typeof picked === "string") setProgram(picked);
    } catch { /* cancelled or unavailable */ }
  }

  return (
    <Card title="Custom actions (plugins)">
      <p className="text-muted text-sm mb-3">
        Extend Singula with your own tools. An action runs a program on the file(s) you select in the
        Index Explorer. Use <code>%path%</code>, <code>%folder%</code>, <code>%name%</code> and <code>%ext%</code> in
        the arguments. Actions run directly (no shell) and only ever when you explicitly trigger them.
      </p>

      {actions.length > 0 && (
        <div className="space-y-1.5 mb-3">
          {actions.map((a) => (
            <div key={a.name} className="flex items-center gap-2 text-sm rounded-lg border border-border bg-surface-2 px-3 py-2">
              <span className="font-medium shrink-0">{a.name}</span>
              <span className="text-muted font-mono text-xs truncate" title={`${a.program} ${a.args}`}>{a.program} {a.args}</span>
              <button className="btn danger px-2 py-0.5 text-xs ml-auto shrink-0" onClick={() => remove(a.name)}>Remove</button>
            </div>
          ))}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
        <input className="bg-surface-2 border border-border rounded px-2 py-1.5 text-sm" placeholder="Name — e.g. Open in VLC"
          value={name} onChange={(e) => setName(e.target.value)} />
        <div className="flex gap-1">
          <input className="flex-1 min-w-0 bg-surface-2 border border-border rounded px-2 py-1.5 text-sm" placeholder="Program — e.g. vlc or full path"
            value={program} onChange={(e) => setProgram(e.target.value)} />
          <button className="btn px-2 shrink-0" title="Browse for a program…" aria-label="Browse for program" onClick={pickProgram}><FolderOpen size={15} /></button>
        </div>
        <input className="bg-surface-2 border border-border rounded px-2 py-1.5 text-sm font-mono" placeholder="%path%"
          value={args} onChange={(e) => setArgs(e.target.value)} />
      </div>
      <div className="flex flex-wrap items-center gap-2 mt-2">
        <button className="btn btn-primary" onClick={add}>Add action</button>
        {msg && <span className="text-xs text-danger">{msg}</span>}
        <span className="text-muted text-[11px] ml-auto font-mono">ffmpeg -i %path% %folder%\out.mp4 · explorer /select,%path%</span>
      </div>
    </Card>
  );
}
