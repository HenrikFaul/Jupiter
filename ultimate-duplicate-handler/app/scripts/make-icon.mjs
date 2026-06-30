// Generates app/icon-source.png — a 1024×1024 RGBA app icon, with ZERO native deps
// (pure Node: a minimal PNG encoder over zlib). `tauri icon` then derives every size +
// the .ico from this. The mark: a rounded-square blue gradient with two offset light
// squares — the "duplicate / copy" motif.
import zlib from "node:zlib";
import { writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const SIZE = 1024;
const buf = new Uint8Array(SIZE * SIZE * 4); // RGBA

function setPx(x, y, r, g, b, a) {
  if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
  const i = (y * SIZE + x) * 4;
  const sa = a / 255;
  const da = buf[i + 3] / 255;
  const oa = sa + da * (1 - sa);
  if (oa === 0) return;
  buf[i] = Math.round((r * sa + buf[i] * da * (1 - sa)) / oa);
  buf[i + 1] = Math.round((g * sa + buf[i + 1] * da * (1 - sa)) / oa);
  buf[i + 2] = Math.round((b * sa + buf[i + 2] * da * (1 - sa)) / oa);
  buf[i + 3] = Math.round(oa * 255);
}

// inside a rounded rectangle?
function inRound(x, y, x0, y0, x1, y1, rad) {
  if (x < x0 || x > x1 || y < y0 || y > y1) return false;
  const cx = x < x0 + rad ? x0 + rad : x > x1 - rad ? x1 - rad : x;
  const cy = y < y0 + rad ? y0 + rad : y > y1 - rad ? y1 - rad : y;
  return (x - cx) ** 2 + (y - cy) ** 2 <= rad * rad;
}

function fillRound(x0, y0, x1, y1, rad, colorFn) {
  for (let y = y0; y <= y1; y++) {
    for (let x = x0; x <= x1; x++) {
      if (inRound(x, y, x0, y0, x1, y1, rad)) {
        const [r, g, b, a] = colorFn(x, y);
        setPx(x, y, r, g, b, a);
      }
    }
  }
}

// Background: rounded square, vertical gradient blue-400 -> blue-600.
fillRound(0, 0, SIZE - 1, SIZE - 1, 180, (_x, y) => {
  const t = y / SIZE;
  const r = Math.round(96 + (37 - 96) * t);
  const g = Math.round(165 + (99 - 165) * t);
  const b = Math.round(250 + (235 - 250) * t);
  return [r, g, b, 255];
});

// Back square (lower-right, translucent) then front square (upper-left, near-opaque).
fillRound(360, 360, 800, 800, 70, () => [235, 240, 250, 130]);
fillRound(230, 230, 670, 670, 70, () => [240, 244, 252, 240]);

// ---- minimal PNG encoder ----
const CRC = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(bytes) {
  let c = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) c = CRC[(c ^ bytes[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const typeBuf = Buffer.from(type, "ascii");
  const body = Buffer.concat([typeBuf, Buffer.from(data)]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body), 0);
  return Buffer.concat([len, body, crc]);
}

const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(SIZE, 0);
ihdr.writeUInt32BE(SIZE, 4);
ihdr[8] = 8; // bit depth
ihdr[9] = 6; // color type RGBA
// 10,11,12 = 0 (deflate / filter / no interlace)

// Filtered raw: each row prefixed with filter byte 0.
const raw = Buffer.alloc(SIZE * (1 + SIZE * 4));
for (let y = 0; y < SIZE; y++) {
  const rowStart = y * (1 + SIZE * 4);
  raw[rowStart] = 0;
  buf.copy
    ? buf.copy(raw, rowStart + 1, y * SIZE * 4, (y + 1) * SIZE * 4)
    : raw.set(buf.subarray(y * SIZE * 4, (y + 1) * SIZE * 4), rowStart + 1);
}
const idat = zlib.deflateSync(raw, { level: 9 });

const png = Buffer.concat([
  Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
  chunk("IHDR", ihdr),
  chunk("IDAT", idat),
  chunk("IEND", Buffer.alloc(0)),
]);

const outDir = dirname(fileURLToPath(import.meta.url));
const out = join(outDir, "..", "icon-source.png");
writeFileSync(out, png);
console.log(`wrote ${out} (${png.length} bytes)`);
