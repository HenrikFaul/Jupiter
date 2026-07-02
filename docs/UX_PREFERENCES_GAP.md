# Jupiter — Google Play UX-preference gap analysis

Maps the user-submitted Google Play preference research (File Manager+, fm.clean, Awesome File
Manager, ES/ZArchiver, etc. — what reviewers love vs. dislike across popular Android file managers)
against **Jupiter's current implementation**, and lists only what is **genuinely missing** to build
(present capabilities are marked ✅ and intentionally not re-built).

## What users LOVE → Jupiter status

| Preference (from the research) | In Jupiter today | Gap? |
|---|---|---|
| **Speed / low load time** | Room file index + instant search (0.11), streaming scans, `withTimeoutOrNull` probes (0.7) | ✅ mostly — keep hardening via the 100 verification factors |
| **Full basic ops** (copy/cut/paste/rename/**compress**) | Copy/move/rename/delete, archive create/extract (zip/7z/tar/rar) | ✅ present |
| **Category browsing** (images/videos/music/docs) **in one tap** | Home shows category **usage**, but tapping opens **Analytics**, not a device-wide file list; **no MediaStore browser** | ❌ **BUILD — flagship** |
| **Cloud + transfer** | Google Drive sign-in, SMB/SFTP/FTP/WebDAV, Wi-Fi transfer | ✅ present |
| **One-tap common actions on the main screen** | Quick-access row (Downloads/Documents/Images → folders), Tools row | ⚠️ partial — see below |
| **Clean, uncluttered, beginner-friendly UI** | Onboarding, sectioned Home | ✅ present (watch clutter) |
| **Transparent permissions / trustworthy dev** | Permission screen (grant flow) | ⚠️ **BUILD — a concise trust/permission-rationale surface** |
| **No aggressive ads** | No ads anywhere; honest paywall (all features currently free) | ✅ a differentiator — surface it |

## What users DISLIKE → how Jupiter avoids it

| Dislike | Jupiter posture |
|---|---|
| Too many ads / dark patterns | **Zero ads**; paywall never fakes a purchase (0.4/0.6) |
| Overcrowded UI when features pile up | Feature-rich; mitigate with the category browser (fewer taps) + keeping the flagship actions one-tap |
| Slowness on large sets | Index + MediaStore category queries (no full walk for media) |
| Permission distrust / vague dev background | The trust surface below + local-only data messaging |
| Outdated / mixed reputation | Fresh Material 3 app, active changelog/versioning |

---

## Build list (only the genuine gaps)

### 1. MediaStore-backed **Category browser** — flagship, high value
Tapping **Images / Videos / Audio / Documents / APKs / Downloads / Large / Recent** opens an
**instant** device-wide listing backed by `android.provider.MediaStore` (and a typed query for
docs/apks), *not* a recursive filesystem walk — so it is fast even with tens of thousands of files.
Grid for images/videos (Coil thumbnails), list for others; sort (date/size/name); tap opens via the
existing `openByType`; multi-select reuses the existing file-action set (share/delete-to-trash/…).
- New: `data/media/MediaStoreCategorySource.kt` (queries by `MediaStore.Files`/`Images`/`Video`/
  `Audio` + mime sets), `feature/categories/CategoryBrowseScreen/ViewModel/UiState`,
  `Destination.CategoryBrowse("category/{type}")` + NavHost route.
- Wire Home's category cards + quick-access to open the browser instead of Analytics.
- Manifest: `READ_MEDIA_IMAGES/VIDEO/AUDIO` (API 33+) alongside the existing storage grants.

### 2. Permission & privacy **trust surface** — medium
A concise, honest screen/section: *what* each permission is for, *that data stays on the device*
(no ads/no tracking; analytics is opt-in and off by default — already true since 0.4), and a link to
the vault/encryption story. Reduces the #1 trust objection reviewers cite.
- New: `feature/privacy` already exists — extend it with a "Why these permissions / your data stays
  local" card; add a one-line trust badge on Home ("No ads · on-device").

### 3. Keep the **most-common actions one-tap** — small
Ensure New Folder / Sort / Select / Search / Paste are reachable in ≤1 tap from any folder (audit the
browser top bar); add a lightweight paste affordance when a copy/move is pending. (Verify against the
browser as-is; only add what's missing — no redesign.)

> Explicitly **NOT** building (already present): copy/move/rename/delete, compress/extract, cloud &
> remote, Wi-Fi transfer, search, duplicates/cleanup, vault, dual-pane, trash, thumbnails, dark/AMOLED
> theming, personalization. The goal is an ultra-premium bar, so we add only the true gaps and then
> re-verify everything with the 100 cross-method factors in `RESEARCH_100_VERIFICATION_FACTORS.md`.
