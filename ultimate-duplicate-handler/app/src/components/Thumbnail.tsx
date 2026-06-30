import { useEffect, useState } from "react";
import { getThumbnail } from "../lib/contract";

// Module-level cache so scrolling/re-render doesn't re-decode images. data: URLs are
// allowed by the app CSP (img-src 'self' data:).
const cache = new Map<string, string>();

export function Thumbnail({ fileId, size = 120 }: { fileId: number; size?: number }) {
  const key = `${fileId}:${size}`;
  const [src, setSrc] = useState<string | null>(cache.get(key) ?? null);
  const [err, setErr] = useState(false);

  useEffect(() => {
    if (cache.has(key)) {
      setSrc(cache.get(key)!);
      return;
    }
    let alive = true;
    getThumbnail(fileId, size)
      .then((b64) => {
        const url = `data:image/png;base64,${b64}`;
        cache.set(key, url);
        if (alive) setSrc(url);
      })
      .catch(() => alive && setErr(true));
    return () => {
      alive = false;
    };
  }, [key, fileId, size]);

  const box = { width: size, height: size };
  if (err)
    return <div style={box} className="grid place-items-center bg-surface-2 text-muted text-[10px] rounded">no preview</div>;
  if (!src) return <div style={box} className="bg-surface-2 animate-pulse rounded" />;
  return <img src={src} style={box} className="rounded object-cover bg-surface-2 border border-border" alt="" />;
}
