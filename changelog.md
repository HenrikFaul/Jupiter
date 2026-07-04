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

## [jupiter:0.18.0] - 2026-07-02
### Added
- **Eszköz-érzékeny tömörítés** (`feature/compress`, `data/media/DeviceDisplayProfile`+`MediaCompressor`): kiolvassa a telefon valódi kijelzőméretét és ajánl cél-felbontást/bitrátát (SOHA nem nagyít fel); képek (Bitmap újrakódolás) és videó (Media3 Transformer, Main-szálú korutin-híd progresszel+megszakítással) tömörítése; Eredeti→Tömörített + megtakarított hely. More → Compress.
- **Kezdőképernyő-widget** (Glance, `widget/*`): a kedvenc (bookmark) mappák/fájlok listája; a sorra koppintva az app az adott útvonalon nyílik meg (deep link a MainActivity-ben). Manifest receiver.
- **Kép-albumok** (`feature/albums`, `data/media/AlbumsSource`): a képek MediaStore bucket szerint csoportosítva (Camera/Screenshots/WhatsApp…) — album-rács → kép-rács → megnyitás; típus-alapú auto-tag javaslatok. More → Albums.
### Fixed
- Widget review-hibák (kézzel javítva commit előtt): a Glance nappali/éjszakai `ColorProvider` importütközése (teljes minősítés `androidx.glance.color`), és a beégetett `com.jupiter.filemanager` csomagnév, ami a `.debug` buildnél nem indította volna a MainActivity-t (most a futásidejű `packageName`).
### Changed
- `gradle/libs.versions.toml`+`app/build.gradle.kts`: Media3 Transformer + Glance függőségek; `versionName` → 0.18.0.
### Known issues
- A videó-transzkódolás (Media3) és a Glance widget új, lokálisan nem fordítható függőségek — a CI a mérvadó. A widget SINGLE_TOP újraindításnál az onNewIntent finomítás későbbre.
### Planned next
- A 6 kért funkció kész (drag, packer, live index, compress, widget, albumok/tagek). További: onNewIntent widget-deep-link, több ellenőrzési faktor teszté alakítása, valós háttér-letöltésfigyelés foreground service-szel.

## [jupiter:0.17.0] - 2026-07-02
### Added
- **Dual-pane drag FOGANTYÚ**: külön DragHandle ikon, amit megnyomva-húzva AZONNAL (long-press nélkül) áthelyezhető/másolható a fájl a másik panelre/mappára. (A régi long-press-drag azért nem működött, mert a sor saját click-kezelője elnyelte a long-press-t — most fix.)
- **Csomagoló minden formátumra**: `ArchiveManager.createArchive` — zip mellett 7z, tar, tar.gz, tar.bz2 létrehozás (a kicsomagolás már mindet tudta).
- **Valós idejű élő index**: minden fájlművelet (másolás/mozgatás/átnevezés, kukázás/visszaállítás) azonnal frissíti az indexet (delta, nincs újraszkennelés); nyitáskor nincs deep-scan várakozás. `DownloadIndexObserver` (MediaStore ContentObserver) indexeli az új fájlokat és **tartalom-alapú duplikátum-ellenőrzést** futtat ("ez már megvan", névtől/formátumtól függetlenül), értesítéssel. Minden index-frissítés best-effort (runCatching), sosem szakítja meg vagy kerüli meg a fájl/kuka-műveletet; a törlés továbbra is a Lomtáron megy át.
### Fixed
- Index-korrektség (a workflow verifiere fogta): a `childPathsUnder` LIKE ESCAPE nélkül a mappanévben lévő `_`-t jokerként kezelte és testvér-mappák index-sorait törölte (`photos_2024` → `photosX2024`); `ESCAPE '\'` + prefix-escape.
### Changed
- `app/build.gradle.kts`: `versionName` → 0.17.0.
### Known issues
- A ContentObserver csak amíg az app-folyamat él; valódi háttér-letöltésfigyeléshez foreground service kellene (későbbre).
### Planned next
- Eszköz-érzékeny tömörítés (compress); kezdőképernyő-widget kedvenc mappákhoz/fájlokhoz; automatikus taggelés/kategorizálás.

## [jupiter:0.16.0] - 2026-07-02
### Added
- **Verifikációs kutatás 2. kör** (`docs/RESEARCH_VERIFICATION_FACTORS_ROUND2.md`): a session-limit miatt kimaradt 10 tudományterület befejezve — **631 forrás, 170 faktor**, ebből a top 10/terület a #101–200 faktor (competitor-UX teardown, archive/media korrektség, i18n/RTL, QA-módszertan, battery/background, design/motion, trust-onboarding, business metrics, observability). Így összesen **200 kereszt-módszeres ellenőrzési faktor mind a 20 területen (~1260 forrás)**.
### Changed
- `docs/RESEARCH_100_VERIFICATION_FACTORS.md` összefoglaló a companion dokumentumra hivatkozik; `app/build.gradle.kts` `versionName` → 0.16.0.
### Planned next
- További magas-prioritású faktorok kikényszerített CI-teszté alakítása; a competitor-UX megállapítások alapján empty/error-state audit (NN/g) ahol valós UX-javulást hoz.

## [jupiter:0.15.0] - 2026-07-02
### Added
- **"Your data & privacy in Jupiter" bizalmi felület** (`feature/privacy/DataTransparencyScreen`): minden engedély + MIÉRT; minden a készüléken marad, nincs reklám, nincs harmadik-fél tracker; Vault + titok-titkosítás; analytics alapból KI, opt-in; visszaállítható Lomtár-törlés. A Beállításokból ("Your data & privacy" sor) elérhető.
- **Kikényszerített (enforced) tesztek** a CI `testDebugUnitTest`-ben (dokumentált szigor → kikényszerített): `ArchiveZipSlipTest` (rosszindulatú `../` bejegyzések FAILED-del végződnek és sosem írnak a célgyökéren kívülre; abszolút bejegyzés a gyökérbe re-bázolódik; jóindulatú archívum COMPLETED, egyező bájtokkal) és `ArchiveRoundTripTest` (createZip→extractZip bájthű + üres almappát újrateremt — a 0.6.0 fix ellenőrzése).
### Changed
- `app/build.gradle.kts`: `versionName` → 0.15.0.
### Planned next
- A kutatás 2. körének (10 további tudományterület) faktorainak beépítése a research dokumentumba; több magas-prioritású faktor teszté alakítása.

## [jupiter:0.14.0] - 2026-07-01
### Added
- **Azonnali kategória-böngésző** (MediaStore alapú): a Képek/Videók/Zene/Dokumentumok/APK-k/Letöltések egy érintésre eszköz-szintű listát nyitnak (rács + Coil bélyegkép médiára, lista a többihez, rendezés dátum/név/méret, elemszám + összméret) — nem fájlrendszer-bejárás, így nagy fájlszámnál is gyors. `data/media/MediaStoreCategorySource` (cursor-safe, off-main, engedély híján üres, sosem omlik); `feature/categories/*`; `Destination.CategoryBrowse` + NavHost; a Home kategória/gyorselérés ide navigál (Analytics továbbra is elérhető).
- **Kutatási artefaktok**: `docs/RESEARCH_100_VERIFICATION_FACTORS.md` — 100 kereszt-módszeres ellenőrzési faktor (629 forrás, 10 tudományterület), mindegyik egy MÁR meglévő képességet ellenőriz újra más módszerrel (standard + pass-kritérium + automatizálás). `docs/UX_PREFERENCES_GAP.md` — a Google Play preferencia-kutatás a kódbázishoz mérve (valódi hiányok vs. meglévő funkciók).
- `AndroidManifest.xml`: `READ_MEDIA_IMAGES/VIDEO/AUDIO` (API 33+).
### Changed
- `app/build.gradle.kts`: `versionName` → 0.14.0.
### Known issues
- A 20-ügynökös kutatás 10/20 tudományterülete futott le a provider session-limit előtt; a maradék 10 (competitor-UX, archive, media, i18n, QA-módszertan, battery, design, trust-onboarding, business, observability) a round-2 backlog.
### Planned next
- Engedély/adatvédelmi bizalmi felület; a kutatás kiterjesztése a maradék 10 területre; a legmagasabb prioritású ellenőrzési faktorok CI/instrumentált tesztként való bevezetése.

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
