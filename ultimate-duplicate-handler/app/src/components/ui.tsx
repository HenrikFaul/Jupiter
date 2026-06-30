// Small shared presentational atoms. Token-only styling (FORGE Law 5).
import { ReactNode } from "react";
import { formatBytes } from "../lib/contract";

const CARD_SHADOW = "elev";

export function Card({ title, children, className = "" }: { title?: string; children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-xl border border-border bg-surface ${CARD_SHADOW} ${className}`}>
      {title && <div className="px-4 py-2.5 border-b border-border text-text font-medium text-[13px]">{title}</div>}
      <div className="p-4">{children}</div>
    </div>
  );
}

export function Stat({ label, value, tone = "text" }: { label: string; value: string; tone?: "text" | "ok" | "warn" | "danger" | "accent" }) {
  const color = { text: "text-text", ok: "text-ok", warn: "text-warn", danger: "text-danger", accent: "text-accent" }[tone];
  return (
    <div className={`rounded-xl border border-border bg-surface p-4 ${CARD_SHADOW}`}>
      <div className="text-muted text-[11px] uppercase tracking-wider">{label}</div>
      <div className={`text-2xl font-semibold mt-1 tracking-tight ${color}`}>{value}</div>
    </div>
  );
}

export function OnlineChip({ online }: { online: boolean }) {
  return <span className={`chip ${online ? "online" : "offline"}`}>{online ? "online" : "offline"}</span>;
}

/** Generic state-matrix wrapper: every data surface renders loading/empty/error/success
 *  (FORGE Law 6 — no happy-path-only UI). */
export function AsyncBoundary<T>({
  loading,
  error,
  data,
  empty,
  children,
}: {
  loading: boolean;
  error: string | null;
  data: T[] | null;
  empty?: ReactNode;
  children: (d: T[]) => ReactNode;
}) {
  if (loading) return <div className="text-muted p-6">Loading…</div>;
  if (error) return <div role="alert" className="text-danger p-6">Error: {error}</div>;
  if (!data || data.length === 0) return <div className="text-muted p-6">{empty ?? "Nothing here yet."}</div>;
  return <>{children(data)}</>;
}

export const bytes = formatBytes;
