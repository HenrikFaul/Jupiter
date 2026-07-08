// The Sift mark — a faceted "sift-prism": duplicate particles converge into a precision
// prism (the sift), and a single verified KEEPER emerges below, ringed by faint "echoes"
// (the persistent, history-aware index that remembers every file forever). Inline SVG so it
// stays crisp at any size and ships in the bundle. Keep this in lockstep with assets/logo.svg.
export function Logo({ size = 32, glow = false, halo = false }: { size?: number; glow?: boolean; halo?: boolean }) {
  const id = "sift";
  // A subtle BLUE "point-cloud" halo ringing the mark — an AI intelligence field. Only when
  // halo=true; the viewBox expands to make room so the core mark itself stays identical.
  // Deterministic golden-angle positions (no randomness) — tasteful, not noisy.
  const blues = ["#60a5fa", "#3b82f6", "#38bdf8", "#7dd3fc", "#2563eb"];
  const dots = halo
    ? Array.from({ length: 40 }, (_, i) => {
        const a = i * 2.399963229; // golden angle
        const r = 296 + (i % 5) * 8 + (i % 3) * 8; // hug the tile, a few px out
        return {
          x: 256 + Math.cos(a) * r,
          y: 256 + Math.sin(a) * r,
          rad: 2 + (i % 4) * 1.0,
          op: 0.2 + ((i * 41) % 50) / 100,
          fill: blues[i % blues.length],
        };
      })
    : [];
  return (
    <svg width={size} height={size} viewBox={halo ? "-96 -96 704 704" : "0 0 512 512"} aria-label="Singula" role="img"
      style={glow ? { filter: "drop-shadow(0 4px 16px rgba(34,211,238,0.35))" } : undefined}>
      <defs>
        <linearGradient id={`${id}-bg`} x1="0.06" y1="0" x2="0.95" y2="1">
          <stop offset="0" stopColor="#0b1c3f" />
          <stop offset="0.52" stopColor="#0c6b6e" />
          <stop offset="1" stopColor="#22d3ee" />
        </linearGradient>
        <radialGradient id={`${id}-sheen`} cx="0.28" cy="0.16" r="0.95">
          <stop offset="0" stopColor="#ffffff" stopOpacity="0.30" />
          <stop offset="0.5" stopColor="#ffffff" stopOpacity="0" />
        </radialGradient>
        <linearGradient id={`${id}-faceL`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#ffffff" />
          <stop offset="1" stopColor="#cdf7f2" />
        </linearGradient>
        <linearGradient id={`${id}-faceR`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#bfeef1" />
          <stop offset="1" stopColor="#86d6de" />
        </linearGradient>
        <radialGradient id={`${id}-glow`} cx="0.5" cy="0.5" r="0.5">
          <stop offset="0" stopColor="#7af2dd" stopOpacity="0.95" />
          <stop offset="0.55" stopColor="#2dd4bf" stopOpacity="0.32" />
          <stop offset="1" stopColor="#2dd4bf" stopOpacity="0" />
        </radialGradient>
        <radialGradient id={`${id}-halo`} cx="0.5" cy="0.5" r="0.5">
          <stop offset="0.6" stopColor="#3b82f6" stopOpacity="0" />
          <stop offset="0.84" stopColor="#3b82f6" stopOpacity="0.14" />
          <stop offset="1" stopColor="#3b82f6" stopOpacity="0" />
        </radialGradient>
      </defs>

      {/* blue point-cloud halo — the AI intelligence field, behind the mark */}
      {halo && (
        <g aria-hidden="true">
          <circle cx="256" cy="256" r="316" fill={`url(#${id}-halo)`} />
          {dots.map((d, i) => (
            <circle key={i} cx={d.x} cy={d.y} r={d.rad} fill={d.fill} opacity={d.op} />
          ))}
        </g>
      )}

      {/* premium app tile */}
      <rect width="512" height="512" rx="118" fill={`url(#${id}-bg)`} />
      <rect width="512" height="512" rx="118" fill={`url(#${id}-sheen)`} />
      <rect x="6" y="6" width="500" height="500" rx="112" fill="none" stroke="#ffffff" strokeOpacity="0.13" strokeWidth="3" />

      {/* echo rings — the persistent history-aware memory, behind the keeper */}
      <circle cx="256" cy="388" r="96" fill="none" stroke="#ffffff" strokeOpacity="0.10" strokeWidth="3" />
      <circle cx="256" cy="388" r="126" fill="none" stroke="#ffffff" strokeOpacity="0.055" strokeWidth="2.5" />

      {/* duplicate particles converging into the prism */}
      <circle cx="256" cy="92" r="27" fill="#ffffff" />
      <circle cx="195" cy="126" r="20" fill="#ffffff" opacity="0.5" />
      <circle cx="317" cy="126" r="20" fill="#ffffff" opacity="0.5" />
      <circle cx="226" cy="80" r="8" fill="#ffffff" opacity="0.34" />
      <circle cx="298" cy="86" r="6.5" fill="#ffffff" opacity="0.3" />

      {/* prism rim lip */}
      <ellipse cx="256" cy="190" rx="120" ry="17" fill="#ffffff" opacity="0.92" />

      {/* faceted prism body — two faces meeting at a bright center ridge, narrowing to a stem */}
      <path d="M136 192 L256 192 L256 374 L238 374 L238 326 Z" fill={`url(#${id}-faceL)`} />
      <path d="M376 192 L256 192 L256 374 L274 374 L274 326 Z" fill={`url(#${id}-faceR)`} />
      <line x1="256" y1="192" x2="256" y2="374" stroke="#ffffff" strokeOpacity="0.55" strokeWidth="2.5" />

      {/* the verified keeper — a glowing disc + precise check (the one survivor) */}
      <circle cx="256" cy="388" r="64" fill={`url(#${id}-glow)`} />
      <circle cx="256" cy="388" r="41" fill="#ffffff" />
      <path d="M235 388 l13 15 l26 -31" fill="none" stroke="#0c6b6e" strokeWidth="10" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
