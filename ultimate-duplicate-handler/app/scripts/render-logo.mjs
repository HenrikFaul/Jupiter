// Rasterize the vector logo (assets/logo.svg) to a high-res PNG that `tauri icon` then
// turns into every app-icon size + the .ico. Pure Node via @resvg/resvg-js (no system deps).
//   node scripts/render-logo.mjs [size]   ->  app/icon-source.png
import { Resvg } from "@resvg/resvg-js";
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const size = Number(process.argv[2] || 1024);
const svg = readFileSync(join(here, "..", "assets", "logo.svg"), "utf8");

const resvg = new Resvg(svg, { fitTo: { mode: "width", value: size }, background: "rgba(0,0,0,0)" });
const png = resvg.render().asPng();
const out = join(here, "..", "icon-source.png");
writeFileSync(out, png);
console.log(`rendered ${out} @ ${size}px (${png.length} bytes)`);
