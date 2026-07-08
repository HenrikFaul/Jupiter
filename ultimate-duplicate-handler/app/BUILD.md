# Building Sift

Two paths: **dev** (hot-reload, for iterating) and **portable** (the distributable EXE).

## One-time prerequisites (BUILD machine only)
You only need these on the machine that *compiles* Sift — not on machines you copy the
portable app to. (The portable `Sift.exe` only needs the WebView2 runtime, which ships
with Windows 11 and current Windows 10.)

You need three things: **Rust**, the **MSVC C++ Build Tools** (Rust's linker on Windows),
and **Node.js 18+**.

### Fastest install (winget)
```powershell
# 1. Rust toolchain (cargo + rustup)
winget install --id Rustlang.Rustup -e --accept-source-agreements --accept-package-agreements

# 2. MSVC C++ Build Tools — provides link.exe + the Windows SDK that Rust links against.
#    Multi-GB download; takes a while. Unavoidable for compiling a native Windows app.
winget install --id Microsoft.VisualStudio.2022.BuildTools -e --override "--quiet --wait --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"

# 3. Node.js (skip if you already have it)
winget install --id OpenJS.NodeJS.LTS -e
```
Alternatively: Rust from <https://rustup.rs>, Node from <https://nodejs.org>, and see the
Tauri prerequisites at <https://tauri.app/start/prerequisites/>.

### IMPORTANT: open a NEW terminal after installing
winget adds `cargo` to your PATH, but your *current* shell won't see it until you open a
fresh PowerShell window. Then set the default toolchain and verify everything is on PATH:
```powershell
rustup default stable
cargo --version      # must print a version (e.g. cargo 1.x.x)
node --version       # must print a version (>= 18)
```
If `cargo` is still "not recognized" after a new terminal, log out/in once so the PATH
change is picked up everywhere.

> The `dev.ps1` and `build-portable.ps1` scripts run this same preflight automatically and
> stop with a clear message if `cargo` or `node` is missing — so you'll never get a cryptic
> half-build. (The scripts are kept ASCII-only so they parse under Windows PowerShell 5.1,
> which reads `.ps1` files as ANSI rather than UTF-8.)

## Dev mode (hot reload)
```powershell
./scripts/dev.ps1
# or manually:
npm install
npm run tauri -- icon icon-source.png   # first time only (generates app icons)
npm run tauri -- dev
```
The Rust engine recompiles on change; the React UI hot-reloads.

## Engine unit tests (no UI, no window)
```powershell
cd src-tauri
cargo test --lib        # hashing, identity, pathkey, safety, walker, db, repo,
                        # scan_service (end-to-end dup), deletion_service, selection
```

## Portable build (the distributable app)
```powershell
./scripts/build-portable.ps1
```
Produces `app/Sift-Portable/` containing:
```
Sift-Portable/
├─ Sift.exe        ← the whole app (~10–15 MB)
├─ Sift-Data/      ← writable data folder (index, quarantine, exports) — travels with it
└─ README.txt
```
Copy that folder to any Windows PC and double-click `Sift.exe`. No installer, no admin
(admin only unlocks the faster NTFS MFT scan mode; the folder-walk fallback always works).

### Why it's portable
`src-tauri/src/paths.rs` stores the index in `Sift-Data\` **next to the executable**
whenever that location is writable, falling back to `%APPDATA%\Sift` only when the EXE
lives somewhere read-only. So the index — including the history of drives that are
currently disconnected — is carried by the folder, not tied to one machine's user profile.

## Installer build (optional, instead of portable)
```powershell
npm run tauri -- build          # produces NSIS + MSI installers under src-tauri/target/release/bundle/
```

## Frontend-only checks (no Rust needed)
```powershell
npm run typecheck   # tsc --noEmit
npm run build       # vite production build -> dist/
```
Both are verified green in this repo.
