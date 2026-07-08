# Runs Sift in development mode (hot-reload UI + live Rust). For iterating, not shipping.
# Use scripts/build-portable.ps1 to produce the distributable portable app.
# ASCII-only on purpose (parses under Windows PowerShell 5.1, which reads .ps1 as ANSI).
$ErrorActionPreference = "Stop"
$app = Resolve-Path "$PSScriptRoot/.."
Push-Location $app
try {
  if (-not (Get-Command cargo -ErrorAction SilentlyContinue)) {
    throw "cargo (Rust) is not installed or not on PATH. Install Rust from https://rustup.rs (then open a NEW terminal), or run: winget install Rustlang.Rustup"
  }
  if (-not (Test-Path "icon-source.png")) { node scripts/render-logo.mjs 1024 }
  if (-not (Test-Path "node_modules")) { npm install }
  if (-not (Test-Path "src-tauri/icons/icon.ico")) { npm run tauri -- icon icon-source.png }
  Write-Host "Launching Sift (dev)..." -ForegroundColor Cyan
  npm run tauri -- dev
}
finally {
  Pop-Location
}
