# Sift — Release, Code-Signing & Auto-Update

Sift deletes users' files, so being **trusted enough to install** is a hard prerequisite for
every other feature. An unsigned file-deleting utility triggers Microsoft Defender SmartScreen's
full-screen "Windows protected your PC" wall and assorted antivirus heuristics — exactly the
moment a cautious user abandons the download. This document describes the two distribution
shapes Sift ships, how to sign the installer, and how the in-app auto-updater works (and why it
deliberately stays off for the portable build).

---

## 1. Two distribution shapes, one codebase

| Shape | Built by | Signed? | Auto-update? | Data location |
|-------|----------|---------|--------------|---------------|
| **Portable** | `scripts/build-portable.ps1` | No (reproducible, unsigned) | **No** (guarded off) | `Sift-Data\` beside `Sift.exe` |
| **Installer** (NSIS + MSI) | `scripts/build-installer.ps1` | Yes (Authenticode) | **Yes** | `%APPDATA%\Sift\` |

`scripts/build-portable.ps1` is intentionally left **unchanged** — it remains the reproducible,
admin-free, single-EXE arm. The installer arm is additive.

Portable detection is a single source of truth in `src-tauri/src/paths.rs`: the build is
"portable" iff `data_dir()` resolves to `<exe_dir>\Sift-Data`. The same predicate powers the
"PORTABLE" chip in Settings (`commands::storage_info`) and the auto-update guard
(`src-tauri/src/update.rs`). They can never disagree.

---

## 2. Obtaining an Authenticode code-signing certificate

You need a Windows **Authenticode** certificate from a recognized CA (DigiCert, Sectigo,
GlobalSign, SSL.com, etc.). Two classes matter:

- **OV (Organization Validation)** — cheaper, issued to a verified legal entity. SmartScreen
  reputation **accrues over time** as download volume grows; early installs may still warn.
- **EV (Extended Validation)** — issued on a hardware token / HSM (or a cloud signing service
  like **Azure Trusted Signing** / DigiCert KeyLocker). Grants SmartScreen reputation
  **immediately**. Recommended for a deletion tool where first-run trust is the conversion gate.

Whichever you choose, the certificate's **friendly publisher name** (e.g. "Sift" or your legal
entity) is what users see in the UAC prompt — keep it stable across releases so reputation is not
reset.

### Where to put the credential

`scripts/build-installer.ps1` supports three modes, checked in order:

1. **`SIFT_CODESIGN_PFX`** — absolute path to a `.pfx` file (OV cert exported with its private
   key). Optionally set **`SIFT_CODESIGN_PFX_PASSWORD`**. Best for OV on a build server.
2. **`SIFT_CODESIGN_THUMBPRINT`** — SHA-1 thumbprint of a certificate already in the Windows
   certificate store (e.g. an EV token/HSM-backed cert). Best for EV.
3. **Neither set** — the build still succeeds and produces **unsigned** installers with a loud
   warning. This keeps a dev machine without a cert unblocked. Never ship an unsigned build.

> Keep secrets out of the repo. In CI, store the `.pfx` (base64) and password as encrypted
> secrets, or use a cloud-signing service that exposes only a thumbprint.

---

## 3. Building a signed installer

```powershell
# From the app folder:
$env:SIFT_CODESIGN_PFX = "C:\secrets\sift-ov.pfx"
$env:SIFT_CODESIGN_PFX_PASSWORD = "********"
./scripts/build-installer.ps1
```

The script:

1. Renders icons, installs JS deps (same preflight as the portable script).
2. Runs `npm run tauri -- build` which, per `tauri.conf.json`, emits **NSIS + MSI** and the
   **updater artifacts** (`bundle.createUpdaterArtifacts = true`).
3. Signs, in order: the raw `Sift.exe`, then the NSIS `.exe`, then the `.msi`, using
   `signtool sign /fd sha256 /td sha256 /tr http://timestamp.digicert.com ...`.
   A **timestamp** is mandatory so signatures stay valid after the certificate expires.
4. Verifies every signed binary with `Get-AuthenticodeSignature` and **fails the build** if any
   shipped binary is not `Valid` (the release CI gate).

Output lands in `src-tauri/target/release/bundle/{nsis,msi}`.

### Tauri's built-in `signCommand` (alternative)

Instead of post-signing in PowerShell, Tauri can sign during bundling via
`bundle.windows.signCommand` in `tauri.conf.json`. This repo uses the script approach so the
**portable** and **installer** arms share one obvious signing entry point and so signing can be
verified/gated outside the bundler. If you prefer `signCommand`, set
`bundle.windows.certificateThumbprint` (already scaffolded as `null`) and remove the post-sign
loop — do **not** do both, or you will double-sign.

---

## 4. Auto-update (installed channel only)

Sift uses **`tauri-plugin-updater`** (registered in `src-tauri/src/main.rs`). The release feed
and verification key live in `tauri.conf.json`:

```jsonc
"plugins": {
  "updater": {
    "endpoints": ["https://releases.sift.app/latest.json"],
    "pubkey": "REPLACE_WITH_MINISIGN_PUBLIC_KEY",
    "windows": { "installMode": "passive" }
  }
}
```

### Updater signing key (separate from the Authenticode cert!)

The updater verifies each downloaded artifact against a **minisign** key pair — this is *not*
the Authenticode certificate. Generate it once:

```powershell
npm run tauri -- signer generate -w sift-updater.key
```

- Put the **public** key into `tauri.conf.json -> plugins.updater.pubkey`.
- Keep the **private** key out of the repo. At build time, export it so the bundler signs the
  updater artifacts:

```powershell
$env:TAURI_SIGNING_PRIVATE_KEY = Get-Content sift-updater.key -Raw
$env:TAURI_SIGNING_PRIVATE_KEY_PASSWORD = "********"
./scripts/build-installer.ps1
```

This emits a `.sig` next to each updater artifact under `target/release/bundle`.

### The `latest.json` feed schema

Host this at the `endpoints` URL; the updater fetches it, compares `version` to the running
version, and (if newer) downloads `platforms.windows-x86_64.url` and verifies it against the
`signature` using the `pubkey`:

```json
{
  "version": "0.2.0",
  "notes": "Bug fixes and a faster scan stage.",
  "pub_date": "2026-06-14T00:00:00Z",
  "platforms": {
    "windows-x86_64": {
      "signature": "<contents of the .sig file>",
      "url": "https://releases.sift.app/0.2.0/Sift_0.2.0_x64-setup.nsis.zip"
    }
  }
}
```

### Portable guard (why portable never self-updates)

`src-tauri/src/update.rs::compute_status` reports `updates_enabled = false` for the portable
build. The Settings "Updates" card only enables the "Check now" network call when
`updates_enabled` is true. Rationale: a portable folder is the user's property — silently
swapping `Sift.exe` beside their `Sift-Data\` could orphan the index that travels with it.
Portable users update by downloading a new portable build and copying their existing `Sift-Data`
folder next to the new `Sift.exe`.

### Audit trail (recommended follow-up)

For full provenance, write an `audit_log` row (`action = 'app_update'`, `outcome`, `detail_json`
with `{ from, to }` versions) via `db::repo` when an update is applied, consistent with the
append-only audit pattern used for deletions. This is left as a small follow-up so the
config-only change here stays zero-regression.

---

## 5. Release checklist

1. Bump `version` in `src-tauri/tauri.conf.json` **and** `src-tauri/Cargo.toml` (and
   `package.json`) — keep them identical.
2. Build the portable arm: `./scripts/build-portable.ps1` (unsigned, reproducible).
3. Build + sign the installer: `./scripts/build-installer.ps1` with the signing env vars set.
4. Confirm the CI gate passed (every shipped EXE/MSI/NSIS is Authenticode `Valid`).
5. Upload the installer + updater `.zip`/`.sig` to the release host.
6. Publish an updated `latest.json` pointing at the new artifact + signature.
7. Smoke-test: install the previous version, launch, open **Settings -> Updates**, click
   **Check now**, confirm it offers and applies the new version.

---

## 6. SmartScreen reputation warm-up

- **EV** cert: SmartScreen trust is effectively immediate.
- **OV** cert: reputation accrues with verified download volume. Expect some early
  "unrecognized app" prompts; they fade as installs accumulate under the **same** publisher
  identity. Do not rotate the signing identity casually — it resets the reputation clock.
- Always **timestamp** signatures (`/tr`) so they remain valid after the cert expires.
