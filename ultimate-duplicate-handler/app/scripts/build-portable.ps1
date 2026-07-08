# Builds Sift as a PORTABLE Windows app: a single EXE plus its Sift-Data folder, which
# you can copy to any Windows PC and run by double-click. No installer, no admin.
#
# NOTE: this file is intentionally ASCII-only so it parses under Windows PowerShell 5.1
# (which reads .ps1 as ANSI, not UTF-8). Do not add non-ASCII characters here.
#
# Prerequisites (one-time, on the BUILD machine only - not on machines you copy to):
#   * Rust toolchain (https://rustup.rs) + the MSVC C++ build tools
#   * Node.js 18+
#   * Tauri prerequisites: https://tauri.app/start/prerequisites/
#
# Usage (from the app folder):
#   ./scripts/build-portable.ps1

$ErrorActionPreference = "Stop"
$app = Resolve-Path "$PSScriptRoot/.."
Push-Location $app
try {
  # Hard preflight: fail with a clear message if the toolchain is missing.
  if (-not (Get-Command cargo -ErrorAction SilentlyContinue)) {
    throw "cargo (Rust) is not installed or not on PATH. Install Rust from https://rustup.rs (then open a NEW terminal), or run: winget install Rustlang.Rustup"
  }
  if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "node (Node.js) is not installed or not on PATH. Install from https://nodejs.org"
  }

  Write-Host "==> Rendering logo to icon source" -ForegroundColor Cyan
  node scripts/render-logo.mjs 1024

  Write-Host "==> Installing JS dependencies" -ForegroundColor Cyan
  npm install

  Write-Host "==> Generating app icons (.ico + sizes)" -ForegroundColor Cyan
  npm run tauri -- icon icon-source.png

  Write-Host "==> Building release EXE (no installer bundle)" -ForegroundColor Cyan
  # Triggers the Vite build via tauri.conf beforeBuildCommand, then cargo --release.
  npm run tauri -- build --no-bundle

  $exe = "src-tauri/target/release/sift.exe"
  if (-not (Test-Path $exe)) { throw "build did not produce $exe" }

  $out = "Sift-Portable"
  Write-Host "==> Assembling portable folder: $out" -ForegroundColor Cyan
  # Stop any running instance so the EXE / index file isn't locked.
  Get-Process -Name "Sift","Singula" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
  Start-Sleep -Milliseconds 400
  # -Force on a directory only ensures it exists (never truncates files). We do NOT delete
  # an existing Sift-Data, so a rebuild never destroys a user's index.
  New-Item -ItemType Directory -Force $out | Out-Null

  # NEVER overwrite the previous build in place: archive the prior EXE under _archive\<timestamp>
  # so older versions are always recoverable. Only the EXE is moved; the large ffprobe/ffmpeg
  # binaries and the user's Sift-Data index folder are left exactly where they are.
  $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
  $live = Join-Path $out "Singula.exe"
  if (Test-Path $live) {
    $archiveDir = Join-Path $out "_archive\$stamp"
    New-Item -ItemType Directory -Force $archiveDir | Out-Null
    Move-Item $live (Join-Path $archiveDir "Singula.exe") -Force
    if (Test-Path (Join-Path $out "README.txt")) { Copy-Item (Join-Path $out "README.txt") (Join-Path $archiveDir "README.txt") -Force }
    Write-Host "    archived previous build -> $archiveDir" -ForegroundColor DarkGray
  }
  # Migrate any legacy Sift.exe (pre-rename builds) into the archive too, so it is not orphaned.
  $legacy = Join-Path $out "Sift.exe"
  if (Test-Path $legacy) {
    $legacyDir = Join-Path $out "_archive\legacy-$stamp"
    New-Item -ItemType Directory -Force $legacyDir | Out-Null
    Move-Item $legacy (Join-Path $legacyDir "Sift.exe") -Force
    Write-Host "    archived legacy Sift.exe -> $legacyDir" -ForegroundColor DarkGray
  }

  Copy-Item $exe $live -Force
  Copy-Item "scripts/README-PORTABLE.txt" "$out/README.txt" -Force
  New-Item -ItemType Directory -Force "$out/Sift-Data" | Out-Null

  # Bundle the media-analysis backend (ffprobe/ffmpeg) if it has been vendored into
  # vendor/ffmpeg. Sift looks for these beside Sift.exe first, so copying them here makes
  # media integrity/quality analysis work on ANY PC the portable folder is copied to. If the
  # vendor folder is absent the app still runs - it just shows an "install ffprobe" prompt.
  $vendor = "vendor/ffmpeg"
  foreach ($tool in @("ffprobe.exe", "ffmpeg.exe")) {
    $src = Join-Path $vendor $tool
    if (Test-Path $src) {
      Copy-Item $src "$out/$tool" -Force
      Write-Host "    bundled $tool" -ForegroundColor DarkGray
    }
  }
  if (-not (Test-Path (Join-Path $vendor "ffprobe.exe"))) {
    Write-Host "    (no vendor/ffmpeg/ffprobe.exe - media analysis will prompt to install)" -ForegroundColor DarkYellow
  }

  $size = [math]::Round((Get-Item $live).Length / 1MB, 1)
  Write-Host ""
  Write-Host "DONE. Portable app is in: $app\$out" -ForegroundColor Green
  Write-Host ("  Singula.exe (" + $size + " MB) + Sift-Data - copy the whole folder anywhere.") -ForegroundColor Green
}
finally {
  Pop-Location
}
