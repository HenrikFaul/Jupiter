// File-type catalog — the bootstrap of commonly-occurring extensions, grouped by
// category, that powers the smart file-type picker. Each extension lives in exactly ONE
// category (deduped) so a category master-checkbox is unambiguous.
//
// To extend: add new extensions to the right category here. Anything a user types that
// isn't in this catalog shows up live under the "Custom" group in the picker — and is a
// signal that it belongs here permanently.

export interface FileCategory {
  key: string;
  label: string;
  icon: string; // emoji glyph, kept lightweight (no icon dependency)
  exts: string[];
}

export const FILE_CATALOG: FileCategory[] = [
  {
    key: "images",
    label: "Images",
    icon: "🖼️",
    exts: ["jpg", "jpeg", "jfif", "png", "gif", "bmp", "tiff", "tif", "webp", "heic", "heif", "avif", "svg", "ico", "jp2", "raw", "cr2", "cr3", "nef", "arw", "dng", "orf", "rw2", "raf", "sr2", "pef"],
  },
  {
    key: "video",
    label: "Video",
    icon: "🎬",
    exts: ["mp4", "m4v", "mkv", "avi", "mov", "wmv", "flv", "webm", "mpg", "mpeg", "3gp", "3g2", "ts", "mts", "m2ts", "vob", "ogv", "divx", "rm", "rmvb", "asf", "f4v", "mxf"],
  },
  {
    key: "audio",
    label: "Audio",
    icon: "🎵",
    exts: ["mp3", "wav", "flac", "aac", "ogg", "oga", "m4a", "m4b", "wma", "aiff", "aif", "alac", "opus", "mid", "midi", "amr", "ape", "dsf", "dff", "ac3", "wv"],
  },
  {
    key: "documents",
    label: "Documents",
    icon: "📄",
    exts: ["pdf", "doc", "docx", "dot", "dotx", "txt", "rtf", "odt", "pages", "wpd", "tex", "md", "markdown", "log", "xps"],
  },
  {
    key: "spreadsheets",
    label: "Spreadsheets",
    icon: "📊",
    exts: ["xls", "xlsx", "xlsm", "xlsb", "csv", "tsv", "ods", "numbers", "dif"],
  },
  {
    key: "presentations",
    label: "Presentations",
    icon: "📽️",
    exts: ["ppt", "pptx", "pps", "ppsx", "odp", "key"],
  },
  {
    key: "archives",
    label: "Archives & Compressed",
    icon: "🗜️",
    exts: ["zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "lz", "lzma", "cab", "arj", "ace", "z", "iso"],
  },
  {
    key: "code",
    label: "Code & Scripts",
    icon: "💻",
    exts: ["js", "mjs", "cjs", "ts", "jsx", "tsx", "py", "java", "c", "cc", "cpp", "h", "hpp", "cs", "go", "rs", "rb", "php", "swift", "kt", "scala", "sh", "bash", "bat", "ps1", "sql", "vue", "svelte", "dart", "lua", "r", "pl", "asm", "gradle", "groovy"],
  },
  {
    key: "web",
    label: "Web",
    icon: "🌐",
    exts: ["html", "htm", "css", "scss", "sass", "less", "asp", "aspx", "jsp", "wasm"],
  },
  {
    key: "data",
    label: "Data & Config",
    icon: "🧩",
    exts: ["json", "xml", "yaml", "yml", "toml", "ini", "cfg", "conf", "env", "plist", "properties", "parquet", "avro", "ndjson"],
  },
  {
    key: "database",
    label: "Databases",
    icon: "🗄️",
    exts: ["db", "sqlite", "sqlite3", "mdb", "accdb", "dbf", "frm", "myd", "sdf"],
  },
  {
    key: "executables",
    label: "Executables & Installers",
    icon: "⚙️",
    exts: ["exe", "msi", "msix", "appx", "dll", "com", "app", "deb", "rpm", "dmg", "apk", "jar", "bin", "appimage"],
  },
  {
    key: "fonts",
    label: "Fonts",
    icon: "🔤",
    exts: ["ttf", "otf", "woff", "woff2", "eot", "fon", "fnt"],
  },
  {
    key: "ebooks",
    label: "E-books & Comics",
    icon: "📚",
    exts: ["epub", "mobi", "azw", "azw3", "fb2", "lit", "djvu", "cbr", "cbz"],
  },
  {
    key: "design",
    label: "Design",
    icon: "🎨",
    exts: ["psd", "ai", "eps", "xd", "fig", "sketch", "indd", "cdr", "afdesign", "afphoto", "afpub"],
  },
  {
    key: "cad3d",
    label: "3D & CAD",
    icon: "📐",
    exts: ["obj", "fbx", "stl", "dae", "3ds", "blend", "dwg", "dxf", "step", "stp", "iges", "igs", "skp", "gltf", "glb", "ply", "max", "c4d"],
  },
  {
    key: "diskimages",
    label: "Disk & VM Images",
    icon: "💽",
    exts: ["img", "vhd", "vhdx", "vmdk", "vdi", "qcow2", "dsk", "nrg", "bin", "cue"],
  },
];

const _index: Map<string, string> = (() => {
  const m = new Map<string, string>();
  for (const c of FILE_CATALOG) for (const e of c.exts) m.set(e, c.key);
  return m;
})();

/** Lowercased ext (no dot) -> category key, or undefined if not in the catalog. */
export function categoryOfExt(ext: string): string | undefined {
  return _index.get(ext.toLowerCase());
}

/** Every catalog extension, flat. */
export function allCatalogExts(): string[] {
  return FILE_CATALOG.flatMap((c) => c.exts);
}

/** Parse a free-text "jpg, png, .mp4" string into clean lowercased extensions. */
export function parseExtInput(text: string): string[] {
  return text
    .split(/[,\s]+/)
    .map((s) => s.trim().replace(/^\./, "").toLowerCase())
    .filter(Boolean);
}
