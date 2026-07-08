# Jupiter — Product Strategy

> Strategy companion to the native Android application implemented in this
> repository under `app/src/main/java/com/jupiter/filemanager` (Kotlin, Jetpack
> Compose, Material 3, MVVM + Clean Architecture, Hilt). This document is the
> "why" and the "where next" for the code that already exists. It is decisive
> and opinionated by design: a file manager market that contains 40+ credible
> apps does not reward timidity.

---

## 1. Product thesis

The mobile file manager category is simultaneously **enormous and abandoned**.
Hundreds of millions of installs flow to apps that are, at their core, twenty
years of conceptual debt wearing a Material wrapper: a hierarchical tree of
folders bolted onto a glass slab that has no keyboard, no mouse, no second
window, and a user who has never seen the folder their photos live in. The two
dominant strategies are both wrong. The "cleaner" camp (ES, many Lufick-class
apps) monetizes anxiety with ads and junk-removal theater. The "simple" camp
(Files by Google, Apple Files) is clean but deliberately shallow — it refuses to
let a user do anything a power user actually needs.

**Jupiter's thesis: the file manager should disappear as a *browser of folders*
and reappear as an *intent engine over content the user owns*, wherever it
lives.** Users do not want to "navigate to `/storage/emulated/0/DCIM/Camera`."
They want to *find the contract I signed last March*, *get this off my phone
without losing it*, *send these 40 photos to my laptop*, and *stop the "storage
full" warning forever*. Every one of those is an intent over content, not a path.

Three convictions drive everything below:

1. **Content over hierarchy.** The folder tree is an implementation detail we
   must still expose flawlessly (power users live in it) but never lead with. The
   front door is search, categories, intents, and the AI concierge.
2. **One model for local + cloud + network + app sandbox.** Today a user juggles
   Files by Google for local, Drive's app for Drive, the Dropbox app for Dropbox,
   and Documents by Readdle to reach an SMB share. Jupiter's universal index and
   universal action layer make *source* a property of a file, not a different app.
3. **On-device-first AI with a strict safety contract.** AI is the unlock that
   makes intents tractable, but a file manager touches a user's most private data.
   AI that uploads files silently, hallucinates contents, or deletes without
   consent is an instant uninstall. Jupiter's AI is local-first, confidence-scored,
   and human-in-the-loop for anything destructive — exactly the contract the
   shipped `AiAssistant` interface already encodes (`AiSuggestion.confidence`,
   `AppResult.Failure` instead of throwing, gated on `isEnabled`).

Jupiter is **cross-platform (Android + iPhone)** because the file lives across a
user's devices, and a manager that stops at the OS boundary recreates the exact
fragmentation it claims to solve. The Android app in this repo is the reference
implementation and the MVP foundation; iOS is a SwiftUI counterpart sharing the
same domain model and AI contract.

---

## 2. Competitive synthesis of all reference apps

The reference set spans 30 apps across Android and iOS. Grouping them *by
platform* is useless — the durable insight is that the market has converged on a
small number of **winning patterns**, and almost every successful app is really
one or two of these patterns executed well. Jupiter's strategy is to be the first
app that executes *all* of them coherently under one model.

### Reference apps grouped by pattern

| Pattern (winning behavior) | Android exemplars | iOS exemplars | Why it wins | Where it fails today |
|---|---|---|---|---|
| **Dual-pane / power tools** | Solid Explorer, Total Commander, X-plore, FX File Explorer, Root Explorer, MK Explorer | Commander One Mobile | Two-pane copy/move, batch ops, scriptable depth — power users are loyal and vocal | Intimidating UI, desktop-era ergonomics on a touch screen, tiny audience |
| **Cleanup-first utility** | Files by Google, File Manager by Lufick, File Manager+, RS File Manager, ASUS File Manager | FileMaster | "Free up space" is the #1 felt need; junk/duplicate/large-file finders convert | Often ad-laden, scary, low-trust "junk" claims, deletes safe files |
| **Cloud-first simplicity** | Files by Google, CX File Explorer | Dropbox, OneDrive, Documents by Readdle, Apple Files | Meets users where their files actually are; clean onboarding | Each app silos *one* cloud; no unified local+cloud model |
| **Media-centric workflows** | Mi File Manager, Samsung My Files, ASTRO, Amaze | FileApp, File Hub, MyFileManager | Phones are cameras; photo/video/audio triage is the daily job | Galleries are separate apps; weak metadata, no semantic grouping |
| **Privacy-first / secure folder** | Solid Explorer (vault), Samsung My Files (Secure Folder), File Manager+ | FileMaster (secret space) | Hidden/encrypted spaces are a top-rated, sticky feature | Often weak crypto, PIN-only, no biometric, no real key management |
| **Archive handling** | ZArchiver, Total Commander, Solid Explorer, B1/Amaze | Documents by Readdle, FileApp | ZIP/RAR/7z is a universal pain; in-place extract is delightful | Format gaps (RAR5, 7z), no streaming preview, memory blowups |
| **Network protocols (LAN/SMB/WebDAV/FTP)** | X-plore, Solid Explorer, CX File Explorer, ASTRO, Total Commander, FE File Explorer (cross) | FE File Explorer, Documents by Readdle, Commander One | NAS/home-server users are a passionate prosumer niche | Connection setup is brutal; flaky reconnects; no caching |
| **Wi-Fi / desktop transfer** | X-plore (Wi-Fi share), CX (network from PC), File Manager+ | Documents by Readdle, FE File Explorer | "Get it onto my laptop" without a cable or a cloud round-trip | Browser-based, clunky, no resume, address-typing friction |
| **Built-in viewers (PDF/media/text)** | FX, Solid Explorer, ASTRO, File Commander | Documents by Readdle, FileApp, FileApp, Apple Files (Quick Look) | Never leave the app to read a doc; annotation retains users | Viewers are basic; annotation/OCR rare; PDF tools paywalled |
| **Offline-first / on-device** | Files by Google, Amaze, FV File Explorer | Apple Files (local) | Works in airplane mode; fast; private | Often the *only* mode — no graceful cloud bridge |

### The recurring winning patterns, distilled

Across all 30 apps, nine patterns recur as the things that actually drive
ratings, retention, and word of mouth: **(1) dual-pane power tools**, **(2)
cleanup-first utility**, **(3) cloud-first simplicity**, **(4) media-centric
workflows**, **(5) privacy-first secure folders**, **(6) competent archive
handling**, **(7) broad network protocol support**, **(8) frictionless Wi-Fi /
desktop transfer**, and **(9) excellent built-in viewers**.

No single app does more than three of these well. Files by Google nails cleanup +
cloud-simplicity + offline but is deliberately shallow. Solid Explorer nails
dual-pane + archives + network + vault but is a power-user product casual users
bounce off. Documents by Readdle nails viewers + network + cloud on iOS but is an
iOS-only walled experience. **The market has never had one app that is simple at
the front door and deep in the back rooms.**

### How Jupiter merges them

Jupiter treats these nine patterns not as nine features to cram in, but as
**layers of a single progressive product**:

- **Cleanup-first** and **cloud-first simplicity** form the *default surface* —
  what a casual user sees first (Home dashboard, "free up space," categories).
  This is already scaffolded in the repo's `feature.home`, `feature.cleanup`, and
  `feature.search`.
- **Media-centric workflows** and **built-in viewers** form the *daily-use
  surface* — triage, preview, annotate. Repo: `feature.preview`.
- **Privacy-first vault**, **archives**, **network protocols**, and **Wi-Fi
  transfer** form the *power back rooms*, progressively revealed and never in the
  casual user's way. Repo: `feature.vault`, `data.file.ArchiveManager`,
  `feature.transfer`.
- **Dual-pane power tools** become an *adaptive* mode that turns on for large
  screens, foldables, and tablets — and stays out of a phone user's face.

The merge mechanism is the **universal index + universal action layer** (see §4,
§6): every pattern operates over the same indexed content from the same set of
sources, so "extract this archive," "send to laptop," "move to vault," and "find
in my NAS" are the *same kind of action* differing only by source and verb.

---

## 3. What to copy, what to improve, what to reject

Opinionated verdicts. "Copy" means adopt the pattern roughly as-is; "Improve"
means the pattern is right but every existing implementation is weak; "Reject"
means the pattern is a category-wide mistake.

| Pattern / Feature | Verdict | Rationale |
|---|---|---|
| Cleanup / "free up space" as front-door value | **Improve** | The need is real and #1; the execution is dishonest. Replace scary "junk" claims with *explainable, simulated, reversible* cleanup (see §4 cleanup simulation). |
| Duplicate detection (size bucket → hash) | **Copy** | The repo already does exactly this (`feature.cleanup`). It's the correct, battery-cheap algorithm. Improve only by *clustering* near-duplicates (§4). |
| Encrypted vault with biometric unlock | **Copy** | Sticky, top-rated, already shipped via Jetpack Security `EncryptedFile` + `MasterKey`. Keep, extend to per-file keys and decoy vault later. |
| Dual-pane on phones, always on | **Reject** | Two panes on a 6" portrait screen is desktop nostalgia. Make it *adaptive*: dual-pane only on tablets/foldables/landscape. |
| Ads / interstitials / "boost" nags | **Reject** | The single biggest trust-killer in the category. Jupiter is ad-free, period. Monetize with a Pro tier, not anxiety. |
| Per-cloud silo apps (one app per provider) | **Reject** | The core fragmentation Jupiter exists to kill. One unified mount model for Drive/iCloud/Dropbox/OneDrive/Box/WebDAV. |
| Root browsing (Android) | **Improve** | Valuable to a tiny vocal segment; keep it but quarantine it behind explicit, scary-on-purpose confirmation and an "advanced" gate. Never on iOS. |
| Browser-based Wi-Fi transfer (type an IP into a browser) | **Improve** | The job is right; the UX is a relic. Replace with QR pairing + resumable transfer engine + a tiny desktop helper/PWA. |
| Built-in PDF/media/text viewers | **Copy + Improve** | Keep in-app viewing (repo `feature.preview`); add annotation, OCR, and content-aware previews (§4). |
| Archive ZIP only (repo today) | **Improve** | ZIP-only is table stakes; add RAR/7z/tar/gz with streaming extraction to avoid OOM. |
| Tabs, bookmarks, recents, favorites | **Copy** | Cheap, expected, retention-positive. Repo already has bookmarks + recents. |
| File-type categories on Home | **Copy** | Universally loved entry point; already in `feature.home`. |
| Tree view of the filesystem | **Copy (as power feature)** | Power users need it; casual users never see it. Progressive revelation. |
| "Analyze storage" sunburst/treemap | **Improve** | Useful but usually a dead-end chart. Make every segment *actionable* (tap → cleanup intent). |
| Manual cloud re-auth every few weeks | **Reject** | Token fragility is why people abandon cloud features. Invest in robust token refresh + clear re-auth UX. |
| Natural-language search (rare, gimmicky today) | **Improve** | The repo already routes NL search through `AiAssistant.parseNaturalQuery`. Make it the *headline*, backed by a real local semantic index. |
| Paywalling basic file operations | **Reject** | Copy/move/extract are not premium. Gate *advanced AI and cross-device sync*, never core file management. |
| Notification spam for completed copies | **Improve** | Keep foreground progress (needed for long ops) but make it quiet, grouped, and dismissible. |

---

## 4. Missing features that could disrupt the market

These are the features no incumbent ships well, ordered roughly by disruption
potential. They are the reason a user switches *to* Jupiter rather than tolerating
what they have.

1. **Intent-based action bar.** Instead of "browse then act," lead with verbs:
   *Free up space*, *Find a document*, *Send to my laptop*, *Lock something away*,
   *Clean up my downloads*. The path-browsing UI becomes the fallback, not the home.

2. **Semantic content search.** Search *inside* documents, images (OCR + objects),
   and filenames with meaning, not just substring match. "lease agreement," "photos
   of whiteboards," "screenshots with code." Built on a local embedding index.

3. **AI storage concierge.** A persistent, plain-language assistant that knows
   your storage state and proposes safe, explained actions: "You have 3.2 GB of
   WhatsApp video you last opened in 2023 — want me to archive it to Drive and free
   the local copies?" Built on the shipped `AiAssistant.explainStorage`.

4. **Cross-source universal index.** One searchable, browsable index spanning
   local, SD/USB, every connected cloud, every network share, and (where the OS
   allows) other apps' shared content. Source becomes a filter, not a different app.

5. **Smart duplicate clustering and merging.** Beyond exact-hash dupes: cluster
   *near*-duplicates (burst photos, re-encoded videos, doc revisions `v1/v2/final`)
   and offer "keep best, merge metadata, trash the rest" with a preview.

6. **Adaptive interface.** The UI reshapes to the user and the device: a casual
   user sees three big intents; a power user who opens the tree view twice gets the
   tree pinned; a tablet/foldable gets dual-pane automatically.

7. **Relationship-aware storage / project bundles.** Recognize that
   `invoice.pdf`, `photos/`, and `notes.txt` modified together belong to one
   project, and let the user treat them as a bundle — move, share, or archive as a
   unit.

8. **One-tap workspace packaging.** Turn a selection (or a detected project
   bundle) into a single shareable, encrypted, self-describing package — files plus
   a manifest of where they came from — for handoff to a laptop or a colleague.

9. **Rule engine / automation from plain English.** "Every screenshot older than
   30 days, move to Drive and delete locally." Authored in natural language, compiled
   to an explicit, previewable rule (extends `AiAssistant` rule creation).

10. **Time-machine file history.** A local, space-bounded change journal so
    *delete, move, rename, and cleanup are reversible for N days* — the antidote to
    the #1 fear that stops people cleaning up.

11. **AI naming and auto-organization.** Suggest descriptive names for
    `IMG_4821.jpg` / `Scan0007.pdf` and propose tidy destinations — already stubbed
    as `AiAssistant.suggestName`, confidence-scored, applied only on confirmation.

12. **Cross-app handoff.** First-class "open in / send to / receive from" with
    provenance preserved, so files moving between apps don't lose where they came
    from.

13. **Privacy health dashboard.** Show, in one place, what has access to what:
    which clouds are connected, which folders are shared, what's in the vault, what
    permissions are granted — with one-tap revoke.

14. **Transfer resilience engine.** Every transfer (cloud, network, device) is
    chunked, checksummed, pausable, and *resumable* across network drops and app
    restarts. "Failed transfer" becomes a non-event.

15. **Content-aware previews.** Previews that understand content: a PDF shows its
    outline and key entities; a CSV renders as a table; a video scrubs by scene; a
    code file is syntax-highlighted; a contract surfaces parties and dates.

16. **Universal file actions.** A consistent verb palette available on *any*
    file regardless of source: **summarize, OCR, translate, encrypt, redact,
    watermark, sign, compress, extract, convert**. The same actions whether the file
    is local, on Drive, or on a NAS.

17. **Context-aware compression.** Pick the right codec/quality automatically:
    aggressive for a "free up space" intent on old media, lossless for a document
    you're sending to a lawyer. Show the projected savings before committing.

18. **Smart cleanup simulation.** Before any bulk delete, render an explicit
    *simulation*: exactly what will be removed, how much is reclaimed, what's
    protected, and a one-tap undo window. No more blind "Clean."

19. **Granular access control.** Per-folder, per-source, per-action consent —
    especially for what the AI may read and what may ever leave the device.

20. **Device-to-device workspace sync.** Keep a chosen working set (not your
    whole phone) in sync between your Android phone, your iPhone, and your laptop —
    a lightweight, file-manager-native alternative to forcing everything into a cloud.

**Original additions:** (21) **Provenance ledger** — every file carries a small
trail of where it came from, where it's been copied, and where it's going,
visible in its detail sheet; (22) **"Quiet mode" cleanup** — a background, fully
reversible auto-tidy that the user reviews weekly as a digest rather than a nag;
(23) **Explain-before-act guarantee** — any AI or bulk action can be expanded into
a plain-language "here's exactly what I'll do" before you confirm.

---

## 5. Full product specification

### 5.1 Core file management (the back rooms, executed flawlessly)

- **Browse + CRUD + share.** Directory listing, breadcrumbs, multi-select,
  create/rename/copy/move/delete with live, pausable progress; share via
  `FileProvider` (never raw `file://`). *Shipped: `feature.browser`,
  `data.file.FileOperationsManager`, `FileRepositoryImpl`.*
- **Compress / extract.** ZIP today (`ArchiveManager`); roadmap adds RAR, 7z,
  tar, gz with streaming extraction and in-archive preview.
- **Dual-pane** (adaptive: tablets/foldables/landscape) and **tree view** (power
  feature, progressively revealed).
- **Storage analysis.** Category breakdown, treemap, and per-segment actions;
  junk, **duplicate** (size-bucket → content-hash, *shipped*), and **large-file**
  finders. *Shipped: `feature.cleanup`, `data.storage.StorageAnalyticsRepositoryImpl`.*
- **Cloud sources.** Google Drive, iCloud Drive, Dropbox, OneDrive, Box, and any
  WebDAV server, mounted into the universal index with robust token refresh.
- **Network sources.** SMB/CIFS, FTP, SFTP, FTPS, DLNA/UPnP, generic NAS, with
  QR/saved connections and reconnect handling.
- **Archives.** ZIP/RAR/7z/tar/gz — create, extract, browse-in-place, preview.
- **Media + PDF.** Preview for image/video/audio/text/PDF (*shipped:
  `feature.preview`*); roadmap adds annotation, OCR, redaction.
- **Nearby transfer.** Device-to-device over the resilient transfer engine
  (Wi-Fi Direct / local network / Nearby), QR-paired.
- **Vault / encryption / biometrics.** App-private encrypted store
  (`EncryptedFile` + `MasterKey`), optional biometric unlock. *Shipped:
  `feature.vault`, `data.vault.VaultRepositoryImpl`.*
- **Storage breadth.** Internal, SD card, USB OTG, and (Android, advanced/gated)
  **root** browsing. *Shipped tiered permission model:
  `data.permission.StorageAccessManager` → `FULL_ACCESS / SCOPED_ONLY / NONE`.*
- **Wi-Fi desktop transfer.** QR-paired, resumable transfer to a desktop helper/PWA.
- **Navigation aids.** Tabs, bookmarks, favorites, recents. *Shipped: bookmarks +
  recents via `data.bookmark.BookmarkRepositoryImpl`.*
- **Power operations.** Batch ops; sort/filter (*shipped: `SortOption`,
  `FilterOption`*); tagging, custom metadata, and color labels.
- **Built-in viewers** for all common types, content-aware (§4).

### 5.2 Persona coverage

| Persona | Primary needs | Jupiter's answer |
|---|---|---|
| **Casual** | "Phone's full," find a photo, send a file | Intent home, explainable cleanup, semantic search, one-tap share |
| **Students** | Cheap phones, assignments, PDFs, sharing | Low-storage mode, PDF viewer/annotation, project bundles, nearby transfer |
| **Creators** | Photos/video bulk, ratings, offload | Media workflows, near-dup clustering, context-aware compression, NAS/cloud offload |
| **Office** | Docs, PDFs, email attachments, signing | Document viewers, summarize/OCR/translate/sign, cloud integration |
| **Power users** | Dual-pane, root, network, scripting | Adaptive dual-pane, tree, root (gated), SMB/SFTP, rule engine |
| **Privacy-conscious** | Hidden/encrypted data, no leaks | Vault, on-device AI, privacy health dashboard, granular access control |
| **Enterprise** | Managed clouds, policy, audit | SSO/MDM-friendly cloud, provenance ledger, policy-gated AI, audit log |
| **Low-storage** | Constant "storage full" | Quiet-mode auto-tidy, cloud offload, context-aware compression, simulation |
| **Cloud-ecosystem** | Lives in Drive/OneDrive/iCloud | Unified mounts, one index across clouds, robust re-auth |
| **LAN / NAS** | Home server, media library | SMB/SFTP/DLNA, resilient transfers, cached browsing, casting |

### 5.3 Classic weakness → Jupiter fix

| Classic file-manager weakness | Jupiter fix |
|---|---|
| Clutter, ads, "boost" nags | Zero ads; calm UI; monetize a Pro tier, never anxiety |
| Too technical (paths, flags) | Intent-first home; hierarchy demoted to a fallback surface |
| Too limited (deliberately shallow) | Progressive depth: simple front door, full power back rooms |
| Shallow, single-provider cloud | Unified mounts for 6 clouds + WebDAV in one index |
| Fragile transfers | Chunked, checksummed, resumable transfer resilience engine |
| Poor duplicate/cleanup | Near-dup clustering + simulated, explainable, reversible cleanup |
| Weak organization | Tags, metadata, AI naming, project bundles, rule engine |
| Weak content search | Local semantic + OCR index; NL search as the headline |
| No unified local/cloud/network/app model | One universal index; source is a filter, not an app |
| Weak privacy / permissions | Vault, on-device-first AI, privacy health dashboard, granular consent |
| Weak desktop interop | QR-paired resumable Wi-Fi transfer + desktop helper/PWA |
| Weak automation | Plain-English rule engine compiled to previewable rules |
| Bloat vs. barebones | Adaptive UI scales complexity to the user, not a fixed compromise |
| Poor onboarding | Permission-first, value-first onboarding (already routed via `Permission`) |
| Doesn't scale consumer → pro | One product, progressively revealed; same engine for both |

---

## 6. Architecture blueprint

Jupiter is **local-first, source-pluggable, AI-orchestrated**. The Android
reference implementation already realizes the lower half of this blueprint; the
sections below mark what exists and what extends it.

### 6.1 Layered architecture (matches the repo today)

```
┌──────────────────────────────────────────────────────────────┐
│  UI  — Compose (Android) / SwiftUI (iOS): screens + components │
│        observe StateFlow<UiState> / @Observable view state     │
├──────────────────────────────────────────────────────────────┤
│  Presentation — @HiltViewModel (Android) / ViewModel (iOS)     │
│        unidirectional: events ▼  state ▲                       │
├──────────────────────────────────────────────────────────────┤
│  Domain — models (FileItem, FileType, Sort/Filter, Storage…)   │
│           + repository interfaces; AppResult<T> / AppError      │
├──────────────────────────────────────────────────────────────┤
│  Data — repository impls + data sources                        │
│         filesystem · storage · prefs · bookmark · vault         │
│         (+ roadmap: cloud · network · index · transfer)         │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 Core modules

- **Source layer (pluggable).** A `FileSource` abstraction generalizes today's
  `FileSystemDataSource`. Each source (Local, SD/USB, Drive, iCloud, Dropbox,
  OneDrive, Box, WebDAV, SMB, FTP/SFTP/FTPS, DLNA, Root) implements list / read /
  write / stat / watch. The browser, search, cleanup, and transfer features are
  *source-agnostic* — they consume `FileItem`s, not paths.
- **Universal index.** A local store (Room on Android, GRDB/SQLite on iOS) holding
  metadata, content hashes, OCR text, and semantic embeddings for indexed content
  across all enabled sources. Incremental, battery-aware, cache-bounded.
- **Operations engine.** Generalizes `FileOperationsManager` to cross-source
  copy/move/delete/compress/extract with progress, cancellation, and the
  time-machine journal.
- **Transfer engine.** Chunked, checksummed, resumable transport for cloud,
  network, and device-to-device; survives process death (WorkManager on Android,
  `BGTaskScheduler` / URLSession background on iOS).
- **AI orchestration layer.** Routes intents to on-device models first, optionally
  to a remote model with explicit consent; enforces the safety contract (§8).
- **Vault / crypto.** `EncryptedFile` + `MasterKey` (Android); Keychain +
  CryptoKit / file protection (iOS).

### 6.3 Storage & indexing model

```
 Sources ──► Scanner ──► Extractors ──► Index (Room/SQLite)
 (local,     (incre-     (metadata,     ├─ files(meta, source, hash)
  cloud,      mental,      OCR text,     ├─ fts(content, ocr, names)
  network)    watched)     embeddings,   ├─ vectors(embedding)
                           thumbnails)   └─ journal(reversible ops)
                                              │
                              Search/Concierge/Cleanup read here
```

Indexing is **opt-in per source**, runs on `@IoDispatcher`/background tasks,
respects battery and metered-network constraints, and stores only derived
metadata locally — never ships file contents anywhere without consent.

### 6.4 Search pipeline

`query → (classifier) → {literal | structured | semantic}` →
- **Literal:** FTS over names/paths (instant).
- **Structured:** parsed to a `FilterOption` (type/size/date/source) — exactly the
  shipped `AiAssistant.parseNaturalQuery` → `FilterOption` path, extended.
- **Semantic:** embed query, ANN over the local vector index, rank, explain.

Results merge across sources with provenance badges. AI never invents results; it
ranks and explains over the deterministic index.

### 6.5 Permission, privacy & local-first data

Tiered, version-aware access — *already implemented*: `MANAGE_EXTERNAL_STORAGE`
(API 30+), legacy read/write capped by `maxSdkVersion`, surfaced as
`FULL_ACCESS / SCOPED_ONLY / NONE` via `StorageAccessManager`; iOS uses security-
scoped bookmarks and the document picker. **All user data and the index are
local by default.** Cloud/remote AI is strictly opt-in with per-source granular
consent. Settings persist via DataStore (Android) / `UserDefaults`+files (iOS).

### 6.6 Sync & conflict resolution

Device-to-device workspace sync uses per-file version vectors. Conflicts are
**never silently resolved**: the user sees both versions with provenance and a
clear choice (keep both / keep newer / keep mine). Cloud edits use ETag/revision
checks before overwrite. The transfer engine's checksums guarantee integrity.

### 6.7 Plugin strategy

Sources, viewers, and universal actions are registered behind interfaces so new
protocols, file formats, and AI actions ship without touching feature code. This
keeps the binary lean (lazy-loaded source/viewer modules) and lets the same
action palette light up across every source.

### 6.8 Performance, battery, memory, failure/recovery

Compose is pure UI; **all IO is off-main** on the injected `@IoDispatcher`
(threading rule already enforced in the repo). Large dirs and archives stream
rather than load whole. Indexing is incremental and constraint-gated. Cross-layer
errors are typed `AppResult.Failure` / `AppError` — *the codebase never throws
across boundaries*. Long ops run in foreground services / background tasks and
**resume after process death** via the journal + transfer engine.

### 6.9 Telemetry & security model

Telemetry is **privacy-preserving and opt-in**: aggregate, anonymous, no file
names/paths/contents ever. Security: encrypted vault, FileProvider-only sharing,
no `file://` exposure, biometric gating, on-device-first AI, and signed releases
(swap the debug keystore before production — flagged in the repo).

### 6.10 Cross-platform UI strategy

**Shared:** domain model, `AppResult`/`AppError` contract, `AiAssistant`
interface and safety policy, index/search/transfer specs.
**Android (this repo):** Kotlin + Jetpack Compose + Material 3 (dynamic color),
MVVM, Hilt DI, Coroutines/Flow/StateFlow.
**iOS:** SwiftUI + the platform design language, an equivalent MVVM/`@Observable`
presentation layer, Swift Concurrency mirroring the coroutine model, and a Swift
port of the domain layer. We *do not* force a single shared-UI framework; we
share the **logic and the model**, and let each platform feel native — the
difference between a respected app and a wrapped web view.

### 6.11 Onboarding & phased rollout

Onboarding is permission-first and value-first (the repo already routes first
launch to `Permission`): explain access, grant, then immediately demonstrate one
win (a cleanup estimate or a search). Rollout is phased per §11.

---

## 7. UX principles and screen map

**Principles (non-negotiable):**

1. **Progressive revelation.** Power appears as the user reaches for it; it is
   never the first thing they see. Tree, dual-pane, root, and rules are earned
   surfaces.
2. **Simple by default.** The home is intents and categories, not a path.
3. **Adaptive power tools.** Layout and exposed depth respond to device and
   demonstrated user behavior.
4. **Reversible destructive actions.** Delete, move, and cleanup are journaled
   and undoable for a window. Nothing scary is irreversible by default.
5. **Clear progress / pause / resume / retry.** Every long op shows state and is
   controllable (extends the shipped `OperationProgressCard`).
6. **First-class history / undo.** A visible activity log of what happened, with
   undo — the time-machine made tangible.
7. **Provenance everywhere.** Every file's detail shows *where it lives, where it
   came from, and where it's going.*
8. **Explain before act.** AI and bulk actions expand into plain language before
   confirmation. No AI theater.

**Screen map (✓ = scaffolded in repo today):**

```
Permission ✓ (first-run access onboarding)
Home ✓ (dashboard: volumes, categories, recents, bookmarks, intents)
 ├─ Search ✓ (literal · structured · semantic; NL via AiAssistant ✓)
 ├─ Browser ✓ (breadcrumbs, multi-select, sort/filter, CRUD, progress)
 │    ├─ File detail / provenance sheet
 │    ├─ FileActionsSheet ✓ (universal action palette)
 │    ├─ SortFilterSheet ✓
 │    └─ Preview ✓ (image/video/audio/text/PDF → +annotate/OCR)
 ├─ Cleanup ✓ (storage overview, large files, duplicates → +simulation)
 ├─ Vault ✓ (encrypted store, biometric unlock)
 ├─ Transfer ✓ (nearby + desktop, resumable) 
 ├─ Concierge (AI chat over storage + actions)            [roadmap]
 ├─ Sources (mount clouds/network; privacy health)        [roadmap]
 ├─ Automation (plain-English rules)                       [roadmap]
 ├─ Activity / History (undo, time-machine)                [roadmap]
 └─ Settings ✓ (theme, AI consent, access, granular control)
```

---

## 8. AI feature design and safety model

### 8.1 How Claude (and on-device models) are used

The AI layer is built on the shipped `AiAssistant` contract (`suggestName`,
`explainStorage`, `parseNaturalQuery`, `isEnabled`, confidence-scored
`AiSuggestion`, failure-returning rather than throwing). Capabilities:

- **Natural-language search** — turn "large videos I haven't watched since last
  summer" into a structured `FilterOption` + semantic ranking.
- **Cleanup explanations** — narrate the storage state and *why* each suggestion
  is safe (`explainStorage`), with the number that will actually be reclaimed.
- **Classification & tagging** — categorize and label content for organization.
- **Summarization** — summarize documents/PDFs on demand (a universal action).
- **Naming** — propose descriptive names (`suggestName`), applied only on confirm.
- **Rule creation from plain English** — compile "archive old screenshots to
  Drive" into an explicit, previewable automation.
- **Transfer assistance** — pick the right method/codec and explain trade-offs.
- **Productivity workflows** — one-tap workspace packaging, project-bundle
  detection, handoff suggestions.
- **Risk assessment** — score the danger of a bulk action before it runs.
- **Plain-language state** — answer "what's taking up space / what changed?"
- **Learning preferences** — adapt to which suggestions the user accepts/rejects,
  *stored locally*.

Claude is used for the heavy reasoning (rule compilation, explanations,
summarization, complex NL parsing) **only with explicit consent and only on
metadata or user-selected content**; lightweight, frequent tasks (literal/struct
search, basic classification, embeddings) run **on-device** by default.

### 8.2 Safety model (the contract that earns trust)

A file manager touches a user's most private data. The AI obeys hard rules:

- **No hallucinated file content.** The model never asserts what's in a file it
  hasn't been given; summaries are grounded in actually-read content or refused.
- **No unauthorized exfiltration.** File bytes never leave the device without an
  explicit, per-action consent. Default is on-device.
- **No hidden cloud uploads.** Any upload (cloud sync, remote AI) is visible,
  consented, and logged in the privacy health dashboard.
- **No unsafe destructive action without explicit confirmation.** Delete / bulk
  move / cleanup require a human-in-the-loop confirmation showing exactly what will
  change; all of it is journaled and reversible.
- **No privacy compromise.** Granular per-source access control governs what the
  AI may even read.
- **No overconfident guesses.** Every suggestion carries a confidence score
  (`AiSuggestion.confidence`); low-confidence items are presented as questions, not
  actions.
- **No AI theater.** If a deterministic index can answer, AI doesn't pretend to;
  AI explains and ranks, it doesn't fabricate results.

**Implementation posture:** on-device first; explicit consent for any upload;
confidence scoring surfaced in the UI; human-in-the-loop confirmation for anything
destructive or anything leaving the device; a visible activity log; and graceful
degradation — when AI is unavailable, the app is fully functional (the repo's
`NoOpAiAssistant` already guarantees this).

---

## 9. Launch strategy and differentiation

**Positioning:** *"The file manager that finally understands your stuff."* Not a
cleaner, not a browser — an intent engine over everything you own.

**The one-line differentiator:** Jupiter is the only app that is *simple at the
front door and deep in the back rooms*, unifies *local + cloud + network + device*
in one index, and runs *trustworthy, on-device-first AI* over it.

**Wedge:** lead with the universal felt need — **"free up space, safely, and find
anything."** Explainable, reversible cleanup + semantic search are the demos that
convert in 30 seconds, and they're closest to shipping (repo: `cleanup`,
`search`). Power and cross-device features deepen retention after the wedge lands.

**Channels:** Play Store / App Store ASO around "free up space," "file manager,"
"document search"; a credible privacy story for the Reddit/HN prosumer crowd
(the people who evangelize Solid Explorer and Documents); creator/student
communities for media and PDF workflows.

**Monetization:** generous free tier (all core file management, basic cleanup,
on-device search, vault — *never* ad-supported). **Jupiter Pro** unlocks advanced
AI (summaries, rules, concierge depth), cross-device workspace sync, unlimited
cloud/network mounts, and pro viewers/annotation. Honest, value-aligned, no nags.

**Trust as a feature:** ad-free, on-device-first, reversible-by-default, and
transparent about every byte that moves. In a category defined by dark patterns,
trust *is* the marketing.

---

## 10. Risks, trade-offs, and mitigations

| Risk / trade-off | Mitigation |
|---|---|
| **Scope explosion** (9 patterns + 20 features is a lot) | Strict roadmap (§11); the repo MVP already proves the spine. Ship the wedge first. |
| **AI trust failure** (one bad delete = uninstall) | Hard safety contract (§8), reversible-by-default journal, confidence scoring, human-in-the-loop. |
| **Privacy perception** ("a file manager with AI is reading my files") | On-device-first, granular consent, privacy dashboard, loud transparency, no ads. |
| **Platform permission tightening** (Android scoped storage, iOS sandbox) | Already version-tiered (`StorageAccessManager`); degrade gracefully to scoped/document-picker. |
| **Cloud API fragility** (token expiry, rate limits, ToS) | Robust refresh, clear re-auth UX, caching, respect provider quotas; treat each source as best-effort. |
| **Cross-platform cost** (two native UIs) | Share domain/model/AI logic; only UI diverges. Android leads, iOS follows the same contracts. |
| **Battery/storage from indexing** | Opt-in per source, incremental, constraint-gated, cache-bounded; user-visible controls. |
| **Performance on cheap/low-storage devices** | Low-storage mode, streaming ops, lazy modules, no whole-file loads. |
| **Monetization vs. goodwill** | Never paywall core file ops; gate only advanced AI + sync; no ads ever. |
| **Format/protocol edge cases** (RAR5, exotic SMB) | Pluggable source/viewer modules; fail loudly and safely, never corrupt. |
| **Over-promising AI** | "Explain before act," confidence scores, graceful no-AI fallback (`NoOpAiAssistant`). |
| **Differentiation erosion** (incumbents copy us) | The moat is the *coherent whole* (index + actions + trust), not any single feature. |

---

## 11. Prioritized roadmap (MVP → V1 → V2 → V3)

Prioritized by **impact × feasibility × differentiation**. The shipped Android
starter in this repository — browser, tiered permission onboarding,
cleanup/duplicate detection, preview, encrypted vault, search with an NL hook, a
transfer scaffold, and the Compose + Material 3 + MVVM + Hilt foundation with the
`AiAssistant` contract and `AppResult` error model — **IS the MVP foundation.**

### MVP (foundation — largely shipped in this repo)
- File browser: CRUD, breadcrumbs, multi-select, sort/filter, share, progress ✓
- Tiered permission onboarding (`FULL/SCOPED/NONE`) ✓
- Cleanup: storage overview, large files, exact-hash duplicate detection ✓
- Encrypted vault + biometric unlock ✓
- Preview (image/video/audio/text/PDF) ✓
- Search with NL hook → `FilterOption` ✓
- Bookmarks, recents ✓; ZIP archive support ✓
- **Close the MVP:** finish the transfer screen (nearby send/receive), tabs,
  RAR/7z/tar/gz, and real on-device literal+structured search backing the NL hook.

### V1 — *"Find anything, free up space, safely"* (the wedge)
- **Local semantic + OCR index** powering content search (headline feature).
- **Explainable, simulated, reversible cleanup** + near-duplicate clustering.
- **Time-machine journal** (undo for delete/move/cleanup).
- **First cloud mounts** (Drive + one more) + WebDAV in the universal index.
- **Resilient transfer engine** + QR-paired desktop transfer.
- **AI concierge v1** (`explainStorage`, `suggestName`, NL search) on-device-first
  with the full safety contract and consent UI.

### V2 — *"Everything in one place, automated"*
- **Full universal index** across all clouds (iCloud, Dropbox, OneDrive, Box) and
  **network** (SMB/SFTP/FTP/FTPS/DLNA).
- **Adaptive UI:** dual-pane on tablets/foldables, pinned tree for power users.
- **Plain-English rule engine** + quiet-mode auto-tidy with weekly digest.
- **Universal file actions** (summarize, OCR, translate, encrypt, redact,
  watermark, sign, convert) across all sources.
- **Privacy health dashboard** + granular per-source access control.
- **iOS app (SwiftUI)** reaching parity with the V1 wedge.

### V3 — *"Your stuff, across every device"*
- **Device-to-device workspace sync** (Android ↔ iPhone ↔ desktop) with version-
  vector conflict resolution.
- **Project bundles / relationship-aware storage** + one-tap workspace packaging.
- **Pro viewers/annotation, content-aware previews, context-aware compression.**
- **Enterprise:** MDM/SSO-friendly clouds, audit log, policy-gated AI, provenance
  ledger surfaced for compliance.
- **Plugin SDK** for third-party sources, viewers, and actions.

---

## 12. Final decisive product recommendation

**Build Jupiter as an intent engine over owned content, not another folder
browser — and ship the wedge before the platform.**

The market data is unambiguous: 30 reference apps, nine recurring winning
patterns, and *zero* apps that are simple at the front door and deep in the back
rooms while unifying local, cloud, network, and device under one trustworthy,
on-device-first AI. That gap is the entire opportunity, and it is wide open
because incumbents are structurally stuck — the cleaners can't shed their ads and
dark patterns without breaking their revenue, and the simple apps can't add depth
without breaking their "simple" brand.

The concrete recommendation:

1. **Lead with the wedge, not the platform.** "Find anything, free up space,
   safely" is the demo that converts in 30 seconds, sits closest to what this repo
   already ships, and earns the trust required to introduce everything else. Do not
   open with dual-pane or root or a 20-feature grid.
2. **Treat the shipped Android app as the MVP spine and extend it, don't restart.**
   The Clean Architecture + MVVM + Hilt + Compose foundation, the source-agnostic
   `FileItem` domain model, the `AppResult` no-throw error contract, the tiered
   permission model, and the confidence-scored `AiAssistant` interface are exactly
   the right bones. V1 is mostly *deepening* what's already here (real index, real
   cleanup, real transfer), not net-new architecture.
3. **Make trust the product, not a footnote.** Ad-free, on-device-first,
   reversible-by-default, explain-before-act, and transparent about every byte that
   moves. In a category defined by anxiety monetization and silent uploads, the
   trustworthy option is the disruptive option.
4. **Unify, then automate.** The universal index (V1→V2) collapses the app
   fragmentation users hate; the rule engine and concierge (V2) turn that unified
   surface into automation; cross-device sync (V3) extends ownership beyond one OS.
5. **Share the brain, localize the face.** One domain model, error contract, AI
   policy, and index spec across Android and iOS; native Compose and SwiftUI on top.
   This keeps both apps feeling first-class while paying for the logic once.

Execute this and Jupiter is not "a better file manager" — it is the first app that
makes the file manager *disappear into intent*, which is the only move in this
category that incumbents cannot copy without dismantling themselves. Build the
wedge, earn the trust, unify the sources, then automate. Ship it.
