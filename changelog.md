# Jupiter — Changelog

Append-only delivery history for **Jupiter**, a native Android file manager
(Kotlin · Jetpack Compose · Material 3 · MVVM/Hilt · Coroutines/Flow).

---

## Kötelező changelog rutin

Minden új fejlesztési vagy javítási kör **elején** ezt a teljes fájlt végig kell olvasni
(forward-read), MIELŐTT bármit kódolnál.

Kötelező szabályok:
1. A changelogban felsorolt korábbi funkciókat **nem szabad véletlenül kivenni** a kódból vagy regresszióval eltüntetni.
2. Minden új fejlesztést, javítást ehhez a fájlhoz kell **hozzáfűzni** (append-only).
3. A fájl tartalmát **nem szabad lenullázni, felülírni vagy új changelog fájlba szétszedni**; csak appendelni szabad.
4. Minden új bejegyzéshez tartozzon: dátum/timestamp; verzió/scope; **Added / Changed / Fixed / Known issues** bontás; és az érintett réteg (data / ui / feature / remote / build / docs).
5. Kódmódosítás előtt mindig ellenőrizni kell, hogy a changelogban említett, már leszállított funkciók továbbra is megvannak-e.
6. A részletesebb requirement/acceptance/regression/verification tartalom a `versioning/` mappába kerül (lásd `versioning/VERSIONING_CONTROL.md`); a changelog a tömör, időrendi napló.
7. A hibaminták a külső lessons-rendszerbe kerülnek (`…/HenrisForge/lessons-system/lessons_orchestrator.md`); ha az itt nem elérhető, a `versioning/` rekord "Lessons captured" blokkjába.

## Ajánlott bejegyzésstruktúra

```
## [jupiter:X.Y.Z] - YYYY-MM-DD
### Added
### Changed
### Fixed
### Known issues
### Planned next
```

A formátum a *Keep a Changelog* mintát követi; a verziózás szemantikus.

> **CI a fordító.** A dev-konténerből a `dl.google.com` nem elérhető, ezért APK-t csak a GitHub Actions
> (`.github/workflows/android.yml`) fordít. "Zöld"-nek csak konkrét, sikeres CI run számít. Minden build
> archiválódik időbélyeggel az `archive` release-tagbe (visszagörgetési háló).

---

## Időrendi changelog-törzs

> Az alábbi 0.1.0–0.9.0 bejegyzések a jelen session során leszállított munkát rekonstruálják tömören
> (a changelog-rutin visszamenőleg lett bevezetve); a 0.10.0-tól minden kör élőben, e szabályok szerint készül.

## [jupiter:0.1.0] - 2026-06-27
### Added
- Natív Android file manager alapváz: Kotlin 2.0, Jetpack Compose, Material 3, MVVM + Clean Architecture, Hilt DI, Coroutines/Flow. Gradle 8.14 / AGP 8.7.3, minSdk 26 / target+compile 35.
- Fájlböngésző (lista/rács, breadcrumb, rendezés/szűrés), fájlműveletek (másolás/mozgatás/törlés/átnevezés), tárhely-hozzáférés (MANAGE_EXTERNAL_STORAGE), splash/onboarding/permission folyamat.
- Termékstratégiai dokumentum (`docs/PRODUCT_STRATEGY.md`).

## [jupiter:0.2.0] - 2026-06-28
### Added
- A "NEXUS" 40 képernyős mockup-készlet implementálása: Home, Recent, Favorites, Search, Analytics, Cleanup, Downloads, Tags, Workspace, Details, Transfer, Cloud, Privacy, Automation, Archive, Preview, Version, Sync, Vault, Settings stb.
- CI (`.github/workflows/android.yml`): debug+release APK, publikus `build-latest` GitHub Release (állandó letöltőlink).

## [jupiter:0.3.0] - 2026-06-29
### Added
- Valós távoli backendek: SMB (SMBJ), SFTP (JSch), FTP/FTPS (commons-net), WebDAV (OkHttp); Wi-Fi transzfer szerver (NanoHTTPD).
- Archívumok (zip/7z/tar/rar — commons-compress/xz/junrar), média-előnézetek (kép/videó/audio/PDF/szöveg), titkosított Vault (Jetpack Security).

## [jupiter:0.4.0] - 2026-06-30
### Added
- Growth Batch 1: Pro entitlement + paywall (alapból feloldva, nincs regresszió), opt-in analytics (NoOp), personalizáció (accent szín, AMOLED, dynamic color), "What's new".

## [jupiter:0.5.0] - 2026-06-30
### Added
- Duplikátum-kezelő port 1. hullám: fájlra kattintás → megnyitás típus szerint, elérési út másolása vágólapra (`COPY_PATH`), "Open with…" (FileProvider `ACTION_VIEW`).
- `MediaQualityProbe` + `MediaQuality` modell: kép felbontás/MP, videó 1080p·Mbps, audió kbps. A Smart Merge a legjobb minőségű példányt ajánlja megtartásra.
### Changed
- `docs/DUPLICATE_HANDLER_PORT_ANALYSIS.md`: a Sift asztali duplikátum-kezelő 127 képességének 3-kategóriás port-térképe.

## [jupiter:0.6.0] - 2026-06-30
### Fixed
- 20 megerősített hiba a bug-hunt körből (`docs/BUG_HUNT_REPORT.md`): copy/move self-overwrite adatvesztés; Wi-Fi szerver `INTERNET` engedély; Vault biometrikus megkerülés (host → FragmentActivity, hiányzó host = zárva); törlés-megerősítés (egyes + tömeges); `MediaQuality.score` mértékegység-hiba; accent szín crash; createZip üres-mappa crash; FTPS PROT P; SFTP TOFU host-key; API-kulcs titkosítás; AI main-szál `runBlocking` (ANR); tar progressz; és továbbiak.

## [jupiter:0.7.0] - 2026-06-30
### Fixed
- Futásidejű "végtelen spinner" javítások (`docs/RUNTIME_DIAGNOSIS.md`): a tároló-szkennelés név-alapú osztályozással + `Android/data`/`.thumbnails` kihagyással gyors; `findDuplicates` inkrementálisan streamel (8KB prefix-előhash); `observeStorageOverview()` részleges eredményeket emittál; `MediaQualityProbe` `withTimeoutOrNull(4s)` — a Smart Merge örök-fagyása megszűnt; a loading-gate az első tartalomnál felenged.
### Added
- Engedély-üres-állapot: hiányzó All-Files-Access esetén "Grant All Files Access" gomb (spinner helyett), és ON_RESUME újraszken a Cleanup/Analytics/Duplicates/Smart Merge képernyőkön.
### Changed
- CI: minden build időbélyeges archiválása az `archive` release-tagbe (date+timestamp postfix) — visszagörgetési háló.

## [jupiter:0.8.0] - 2026-06-30
### Added
- Dual Pane v1: valódi másolás/mozgatás a panelek között (`copySelectedTo`/`moveSelectedTo`), akció-bar (Copy/Move/Select-all/Clear), kép/videó bélyegképek (Coil), sűrűbb sorok, Swap + Equalize toolbar, aktív-panel akcentkeret, `OperationProgressCard`, cél-panel auto-refresh.

## [jupiter:0.9.0] - 2026-06-30
### Added
- Valódi Google Drive bejelentkezés: rendszer fiókválasztó (Credential Manager / `GetSignInWithGoogleOption`) → Drive authorization (`drive.file`, consent resolution) → `drive/v3/about` kvóta; a kapcsolt kártya a valós e-mailt + tárhelyet mutatja. Token EncryptedSharedPreferences-ben, sosem logolva.
- Rögzített debug aláírókulcs (`app/keystore/jupiter-debug.keystore`) a stabil SHA-1-ért; `GDRIVE_WEB_CLIENT_ID` build-config (`docs/GOOGLE_DRIVE_SETUP.md`). Aktiválás a felhasználó OAuth-kliensével.
### Known issues
- A Google Drive végpont a felhasználó OAuth Web client id-jét igényli (egyszeri, ~5 perc); addig a Connect őszinte "set up" jelzést mutat, nem hamis "coming soon"-t.

## [jupiter:0.10.0] - 2026-07-01
### Added
- **Dual-pane drag & drop**: fájl (vagy a kijelölés) hosszú-nyomásra "felemelhető", lebegő ghosttal áthúzható a másik panelre (vagy annak egy mappájára), és ott **másolódik/áthelyeződik**. **Drop-mód kapcsoló (Copy | Move)**. Stabil pointer-gesztusokkal (`detectDragGesturesAfterLongPress` + window-koordinátás találat), nem a verzió-érzékeny Compose DnD API-val.
- **Compare** toolbar-kapcsoló: a mindkét panelben azonos nevű fájlokat kiemeli (gyors, memóriabeli név-egyezés).
- **Duplikátum minőség-összehasonlítás láthatóvá téve**: minden csoport minőség-pontszám szerint rendezve, a legjobb példány **BEST** jelvénnyel, a többi eltávolíthatóként; méret + minőség-címke + relatív jelzés. A Smart Merge a **KEEP · best quality** vs **REMOVE** összevetést mutatja, indoklással.
- **Process artifactok**: append-only `changelog.md` + `versioning/VERSIONING_CONTROL.md` (vezérlőprompt) + a 0.10.0 verzió-rekordpár (`260701_15_v0.10.0_*`). Az importált MONOLITH minta-fájlok törölve.
### Changed
- `app/build.gradle.kts`: `versionName` → 0.10.0.
### Known issues
- A lessons-system orchestrator (`…/HenrisForge/lessons-system/lessons_orchestrator.md`) helyi Windows-út, ebből a felhőkonténerből nem elérhető; a leckék a verzió-rekordba kerülnek, onnan szinkronizálandók.
### Planned next
- Room-alapú **fájl-indexálás** (a legnagyobb Sift/UDH port) a gyorsabb böngészéshez/kereséshez/duplikátum-szkeneléshez.
- További `ultimate-duplicate-handler` portok: partial head+tail elő-hash újrahasznosítás, trash/restore + audit napló, scan-szűrők, perceptuális near-duplicate.

## [jupiter:0.11.0] - 2026-07-01
### Added
- **Perzisztens Room fájl-index** (`data/index/`): `FileIndexEntry`/`@Dao`/`@Database`, `FileIndexRepository`, `IndexStats`; a `di/IndexModule` szolgáltatja az adatbázist+DAO-t és `@Binds`-eli a repót.
- **Háttér-indexelő** (`IndexingWorker`, `@HiltWorker`): a tárolót kötegekben járja be és metaadatot indexel — kihagyva az `Android/data`, `Android/obb`, `.thumbnails`, `.trashed` fákat; megszakítható, crash-safe. `IndexingScheduler` egyedi OneTime munkaként ütemezi.
- **Azonnali keresés**: bekapcsolt indexelésnél a gyorsítótárazott találatok azonnal megjelennek, majd az élő bejárás felülírja őket (üres/kikapcsolt index ⇒ eredeti viselkedés).
- **Beállítások → Indexing** szekció: kapcsoló, "Rebuild now", és "N files indexed" státusz.
### Changed
- `app/build.gradle.kts` + `gradle/libs.versions.toml`: Room runtime/ktx + `ksp(room-compiler)`; `versionName` → 0.11.0.
### Known issues
- A `contentHash` oszlop egyelőre nincs feltöltve (metaadat-index); a duplikátum-szken hash-újrahasznosítása a következő kör.
### Planned next
- `findDuplicates` hash-újrahasznosítás az indexből; könyvtárlisták kiszolgálása az indexből; UDH portok (partial pre-hash, trash/restore + audit, scan-szűrők, perceptuális near-dup).

## [jupiter:0.12.0] - 2026-07-01
### Added
- **Duplikátum-szken hash-újrahasznosítás az indexből**: a `findDuplicates` a Room-indexből veszi a teljes-tartalom hasht, ha a fájl mérete+mtime-ja változatlan (`hashForUnchanged`), különben egyszer kiszámolja és visszaírja (`putHash`). Így az ismételt duplikátum-szken nem olvassa újra a fájlokat — a Sift inkrementális hash-reuse portja. Best-effort: index-hiba esetén közvetlen hashre esik vissza.
### Changed
- `app/build.gradle.kts`: `versionName` → 0.12.0.
### Planned next
- Trash / restore + audit (minden törlés visszaállíthatóan a Lomtárba); scan-szűrők; perceptuális near-duplicate.

## [jupiter:0.13.0] - 2026-07-01
### Added
- **Visszaállítható Lomtár (Recycle Bin)**: minden törlés a Lomtárba kerül (app-privát `getExternalFilesDir("trash")`, saját nem-destruktív `jupiter_trash.db`, `deletedAt` audit). **Lomtár képernyő** (Restore / Delete permanently / Empty + megerősítés) a More-ból elérve; `Destination.Trash` + NavHost útvonal.
- A törlési útvonalak a Lomtáron keresztül mennek: `FileOperationsManager.delete` a forrást **megőrzi** hiba esetén (nincs csendes hard-delete); a `DuplicatesViewModel`+`SmartMergeViewModel` közvetlen `File.delete()` megkerülései megszűntek.
### Fixed
- **Adatvesztés-védelem** (a workflow dedikált verifiere találta, majd javítva): a keresztkötetes copy-then-delete SOSEM törli a már ellenőrzött cél-másolatot — inkább egy részleges forrás-maradványt hagy, mint hogy az egyetlen ép példányt megsemmisítse; a DB-rollback ugyanezt a szabályt követi. (A workflow `movePath`-ot csonkán hagyta — újraírva.)
### Changed
- `app/build.gradle.kts`: `versionName` → 0.13.0.
### Planned next
- Régi lomtár auto-ürítés; scan-szűrők (méret/típus/dátum/mappa-scope); perceptuális near-duplicate (kép dHash).
