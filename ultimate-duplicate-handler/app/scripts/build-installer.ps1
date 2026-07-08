# Builds Sift as a SIGNED Windows INSTALLER (NSIS + MSI) and the updater artifacts.
#
# This is the "installed channel" counterpart to scripts/build-portable.ps1 (which stays the
# reproducible, UNSIGNED portable arm: a single Sift.exe + Sift-Data). Both come from one
# codebase; only this script signs and produces an installer + auto-update feed.
#
# NOTE: this file is intentionally ASCII-only so it parses under Windows PowerShell 5.1
# (which reads .ps1 as ANSI, not UTF-8). Do not add non-ASCII characters here.
#
# CODE SIGNING (optional but strongly recommended for a file-DELETING tool):
#   Set the environment variable SIFT_CODESIGN_PFX to the path of your OV/EV Authenticode
#   .pfx certificate, and SIFT_CODESIGN_PFX_PASSWORD to its password. When set, this script
#   runs signtool over Sift.exe, the NSIS installer .exe and the .msi. When UNSET, the build
#   still succeeds and produces UNSIGNED installers (with a clear warning) so the pipeline is
#   never blocked on a dev machine without a cert. See docs/RELEASE.md for obtaining a cert.
#
#   Alternatively, set SIFT_CODESIGN_THUMBPRINT to sign with a cert already in the Windows
#   certificate store (e.g. an HSM/token-backed EV cert), instead of a .pfx file.
#
# Prerequisites (one-time, on the BUILD machine):
#   * Rust toolchain (https://rustup.rs) + the MSVC C++ build tools
#   * Node.js 18+ and the Tauri prerequisites (https://tauri.app/start/prerequisites/)
#   * signtool.exe on PATH (ships with the Windows SDK) -- only needed when signing
#
# Usage (from the app folder):
#   $env:SIFT_CODESIGN_PFX = "C:\secrets\sift-ov.pfx"
#   $env:SIFT_CODESIGN_PFX_PASSWORD = "..."
#   ./scripts/build-installer.ps1

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

  $timestampUrl = "http://timestamp.digicert.com"

  # Resolve the signing identity (if any). Three mutually-exclusive modes, checked in order:
  #   1) SIFT_CODESIGN_PFX (+ optional password)  -> file-based cert
  #   2) SIFT_CODESIGN_THUMBPRINT                 -> cert already in the Windows store
  #   3) none                                     -> unsigned build (warn loudly)
  $signMode = "none"
  $pfx = $env:SIFT_CODESIGN_PFX
  $thumb = $env:SIFT_CODESIGN_THUMBPRINT
  if ($pfx) {
    if (-not (Test-Path $pfx)) { throw "SIFT_CODESIGN_PFX is set but the file does not exist: $pfx" }
    if (-not (Get-Command signtool -ErrorAction SilentlyContinue)) {
      throw "SIFT_CODESIGN_PFX is set but signtool.exe is not on PATH. Install the Windows SDK, or run from a Developer PowerShell."
    }
    $signMode = "pfx"
    Write-Host "==> Code signing: PFX at $pfx" -ForegroundColor Cyan
  }
  elseif ($thumb) {
    if (-not (Get-Command signtool -ErrorAction SilentlyContinue)) {
      throw "SIFT_CODESIGN_THUMBPRINT is set but signtool.exe is not on PATH. Install the Windows SDK, or run from a Developer PowerShell."
    }
    $signMode = "thumb"
    Write-Host "==> Code signing: store certificate $thumb" -ForegroundColor Cyan
  }
  else {
    Write-Host "==> WARNING: no signing identity (SIFT_CODESIGN_PFX / SIFT_CODESIGN_THUMBPRINT unset)." -ForegroundColor Yellow
    Write-Host "    Installers will be UNSIGNED and will trip SmartScreen. See docs/RELEASE.md." -ForegroundColor Yellow
  }

  # Helper: Authenticode-sign one file with a timestamp (SHA-256), honoring the chosen mode.
  function Sign-File([string]$path) {
    if ($signMode -eq "none") { return }
    if (-not (Test-Path $path)) { return }
    Write-Host "    signing $([System.IO.Path]::GetFileName($path))" -ForegroundColor DarkGray
    if ($signMode -eq "pfx") {
      $pw = $env:SIFT_CODESIGN_PFX_PASSWORD
      if ($pw) {
        signtool sign /fd sha256 /td sha256 /tr $timestampUrl /f $pfx /p $pw $path
      } else {
        # No password env var: signtool will prompt if the .pfx is protected.
        signtool sign /fd sha256 /td sha256 /tr $timestampUrl /f $pfx $path
      }
    }
    elseif ($signMode -eq "thumb") {
      signtool sign /fd sha256 /td sha256 /tr $timestampUrl /sha1 $thumb $path
    }
    if ($LASTEXITCODE -ne 0) { throw "signtool failed for $path (exit $LASTEXITCODE)" }
  }

  Write-Host "==> Rendering logo to icon source" -ForegroundColor Cyan
  node scripts/render-logo.mjs 1024

  Write-Host "==> Installing JS dependencies" -ForegroundColor Cyan
  npm install

  Write-Host "==> Generating app icons (.ico + sizes)" -ForegroundColor Cyan
  npm run tauri -- icon icon-source.png

  # Stop any running instance so the EXE / index file isn't locked during the build.
  Get-Process -Name "Sift","Singula" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
  Start-Sleep -Milliseconds 400

  Write-Host "==> Building signed installer bundle (NSIS + MSI + updater artifacts)" -ForegroundColor Cyan
  # tauri.conf.json sets bundle.targets = [nsis, msi] and bundle.createUpdaterArtifacts = true.
  # Triggers the Vite build via beforeBuildCommand, then cargo --release, then bundling.
  npm run tauri -- build

  $relDir = "src-tauri/target/release"
  $exe = Join-Path $relDir "sift.exe"
  if (-not (Test-Path $exe)) { throw "build did not produce $exe" }

  # 1) Sign the raw application EXE first, so the copy embedded inside the installers is signed.
  Sign-File $exe

  # 2) Sign the produced installers. Tauri writes them under target/release/bundle/{nsis,msi}.
  $bundleDir = Join-Path $relDir "bundle"
  $installers = @()
  if (Test-Path $bundleDir) {
    $installers += Get-ChildItem -Path (Join-Path $bundleDir "nsis") -Filter *.exe -ErrorAction SilentlyContinue
    $installers += Get-ChildItem -Path (Join-Path $bundleDir "msi")  -Filter *.msi -ErrorAction SilentlyContinue
  }
  foreach ($f in $installers) { Sign-File $f.FullName }

  # 3) The updater artifacts (.zip / .nsis.zip + .sig) are produced by createUpdaterArtifacts.
  #    Tauri signs these with the updater (minisign) key from TAURI_SIGNING_PRIVATE_KEY when set;
  #    we only surface where they are. latest.json is assembled by the release pipeline from the
  #    .sig + the download URL (schema documented in docs/RELEASE.md).
  $sigs = @()
  if (Test-Path $bundleDir) {
    $sigs = Get-ChildItem -Path $bundleDir -Recurse -Filter *.sig -ErrorAction SilentlyContinue
  }

  Write-Host ""
  Write-Host "DONE. Installer bundle is in: $app\$relDir\bundle" -ForegroundColor Green
  foreach ($f in $installers) {
    $mb = [math]::Round($f.Length / 1MB, 1)
    $state = if ($signMode -eq "none") { "UNSIGNED" } else { "signed" }
    Write-Host ("  " + $f.Name + " (" + $mb + " MB) [" + $state + "]") -ForegroundColor Green
  }
  if ($sigs.Count -gt 0) {
    Write-Host "  Updater signatures:" -ForegroundColor Green
    foreach ($s in $sigs) { Write-Host ("    " + $s.FullName) -ForegroundColor DarkGray }
  } else {
    Write-Host "  (no .sig updater artifacts found - set TAURI_SIGNING_PRIVATE_KEY to emit them; see docs/RELEASE.md)" -ForegroundColor DarkYellow
  }

  if ($signMode -ne "none") {
    Write-Host "==> Verifying Authenticode signatures" -ForegroundColor Cyan
    foreach ($f in (@($exe) + ($installers | ForEach-Object { $_.FullName }))) {
      $sig = Get-AuthenticodeSignature $f
      Write-Host ("    " + [System.IO.Path]::GetFileName($f) + " : " + $sig.Status) -ForegroundColor DarkGray
      if ($sig.Status -ne "Valid") { throw "Authenticode verification FAILED for $f (status: $($sig.Status))" }
    }
  }
}
finally {
  Pop-Location
}
