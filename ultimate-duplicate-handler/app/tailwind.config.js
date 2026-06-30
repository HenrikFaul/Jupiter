/** @type {import('tailwindcss').Config} */
// Colors are referenced via CSS variables (semantic tokens in tokens.css) so there is
// ONE source of truth for theming — no hardcoded hex in components (FORGE Law 5).
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        bg: "rgb(var(--bg) / <alpha-value>)",
        surface: "rgb(var(--surface) / <alpha-value>)",
        "surface-2": "rgb(var(--surface-2) / <alpha-value>)",
        border: "rgb(var(--border) / <alpha-value>)",
        text: "rgb(var(--text) / <alpha-value>)",
        muted: "rgb(var(--muted) / <alpha-value>)",
        accent: "rgb(var(--accent) / <alpha-value>)",
        cyan: "rgb(var(--cyan) / <alpha-value>)",
        ok: "rgb(var(--ok) / <alpha-value>)",
        warn: "rgb(var(--warn) / <alpha-value>)",
        danger: "rgb(var(--danger) / <alpha-value>)",
        sidebar: "rgb(var(--sidebar) / <alpha-value>)",
        "sidebar-2": "rgb(var(--sidebar-2) / <alpha-value>)",
        "sidebar-text": "rgb(var(--sidebar-text) / <alpha-value>)",
        "sidebar-muted": "rgb(var(--sidebar-muted) / <alpha-value>)",
      },
      fontFamily: { mono: ["ui-monospace", "SFMono-Regular", "Menlo", "monospace"] },
    },
  },
  plugins: [],
};
