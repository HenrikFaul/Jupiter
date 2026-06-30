import { useEffect, useState } from "react";

/** Minimal data-fetch hook with explicit loading/error/data (state matrix — Law 6).
 *  Distinguishes "loaded empty" from "failed" (lesson RPC-091): error stays null on
 *  success even when data is empty. Refetches whenever any `deps` value changes. */
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    fn()
      .then((d) => alive && (setData(d), setLoading(false)))
      .catch((e) => alive && (setError(String(e)), setLoading(false)));
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return { data, error, loading, setData };
}
