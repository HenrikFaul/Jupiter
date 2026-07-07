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

## [jupiter:0.31.0] - 2026-07-07
### Fixed — App storage: 10,4 GB helyett a VALÓS app-tárhely (package visibility + cache-duplaszámolás)
- **Gyökér-ok (a képernyőképről azonosítva)**: az App-storage lista CSAK rendszer-appokat mutatott (Google Play services, Samsung appok…) és 362 appra mindössze 10,4 GB-t — miközben az Analytics ~95 GB app-adatot becsült. Ez az **Android 11+ package-visibility szűrés**: `QUERY_ALL_PACKAGES` engedély nélkül a `getInstalledApplications()` csendben kihagyja a legtöbb FELHASZNÁLÓI appot — pont a legnagyobb fogyasztókat (játékok, üzenetküldők). A többi fájlkezelő is ezzel az engedéllyel oldja meg.
- **Javítás 1 — `QUERY_ALL_PACKAGES`** (manifest, install-time normál engedély, nincs user-prompt): a lista mostantól tényleg teljes. Fájlkezelőnek — aminek fő funkciója az eszköz-szintű tárhely-elszámolás — ez a Play-szabályzat szerint kifejezetten engedélyezett használati eset.
- **Javítás 2 — cache-duplaszámolás**: a platform `dataBytes`-a MÁR TARTALMAZZA a cache-t, a v0.29 mégis `app+data+cache`-ként összegzett → minden app cache-e kétszer számolódott. Mostantól `total = app + data`, a sor-felirat „data" része pedig cache nélkül értendő (`dataBytesExcludingCache`) — így a három rész pontosan kiadja az összeget, ahogy a rendszer-Beállításokban.
- **Javítás 3 — kötet-helyes lekérdezés**: minden csomag a SAJÁT kötetén kérdeződik le (`ApplicationInfo.storageUuid`, adoptable-storage-biztos), nem vakon a default köteten.
### Adversarial review — egy tervezett „védőháló" bizonyítottan HIBÁS volt és KIKERÜLT
- Az első verzió a `StorageStatsManager.queryStatsForUser` aggregátummal egyeztette volna a per-app összeget („unattributed" maradék kijelzéssel). A 4-lencsés review (AOSP-forrás szintű ellenőrzéssel) 6 megerősített hibát talált benne, a legsúlyosabb: az aggregátum `dataBytes`-a a TELJES megosztott tárhelyet (fotók, letöltések!) tartalmazza, `appBytes`-a pedig az eszköz-szintű `/data/app`-ot (más profilok — pl. Secure Folder — kódját) + dalvik-cache-t. Az „aggregátum − per-app összeg" tehát főleg NEM appokat mért volna → gyakorlatilag minden valós eszközön fantom-GB-kat mutatott volna. A helyes és őszinte megoldás: a teljes csomaglistával a per-app összeg AZ eredmény; az aggregátum-egyeztetés törölve (`AppStorageReconciler` + tesztje + `unattributedBytes` mező + fejléc-sor eltávolítva még commit előtt).
### Added
- `AppStorageInfo.dataBytesExcludingCache`; `AppStorageInfoTest` frissítve az új total-szemantikára (+ „app + data-cache-nélkül + cache = total" invariáns, negatív-klumpolás).
### Changed
- `AndroidManifest.xml` (`QUERY_ALL_PACKAGES`); `AppStorageSource` (teljes lista, flags=0, per-app `storageUuid`); `AppStorageInfo/Overview` (total-szemantika, `dataBytesExcludingCache`); `AppStorageScreen` (pontos data-felirat); `versionName` → 0.31.0.
### Known issues / megjegyzés
- Work-profil / Secure Folder (másik UserHandle) appjai ezen a képernyőn kívül esnek (platform-korlát, privilegizált API kellene). Publikáláskor a `QUERY_ALL_PACKAGES`-hez Play Console-nyilatkozat szükséges (fájlkezelő = engedélyezett kategória). On-device validálás: a fejléc-összegnek az Analytics ~95 GB-os becslésével kell nagyságrendben egyeznie, és a nagy felhasználói appoknak (játékok, üzenetküldők) meg kell jelenniük a listában.

## [jupiter:0.30.0] - 2026-07-06
### Fixed — „adok hozzáférést → INDULJON a teljes felmérés" (a hozzáférés-megadás mostantól tényleg scannel)
- **Gyökér-ok**: az első felmérés a folyamat indulásakor ütemeződik (`JupiterApp.onCreate` → `ensureIndexed`), ez viszont MÉG az All-Files-Access megadása ELŐTT fut le, így a worker `!hasAllFilesAccess()` ágon **no-opol** (`Result.success(0,0)`, `beginScan` nélkül) és az index `EMPTY` marad. A megadás után **semmi nem indított újra** felmérést — csak a következő hidegindításkor futott —, ezért látszott úgy, hogy „megadtam a hozzáférést, mégsem scannel".
- **Javítás**: a permission-képernyő ViewModelje (`PermissionViewModel.refresh`) mostantól — amint a hozzáférés megvan — elindítja a teljes tárhely-felmérést. A döntés egy tiszta, unit-tesztelt policy (`GrantSurveyPolicy.shouldStartSurvey`): CSAK akkor indít, ha van hozzáférés, az indexelés engedélyezett, és az index se nem `RUNNING`, se nem `COMPLETE` (EMPTY/DIRTY/FAILED-ből igen). Így a megadás azonnal (hidegindítás nélkül) elindítja az előtér-workert, futó felmérést pedig nem indít újra és kész indexet nem scannel feleslegesen.
- **Race-keményítés (adversarial review nyomán)**: a felmérés-ütemezés **process-élettartamú `@ApplicationScope` CoroutineScope**-on fut, NEM `viewModelScope`-on — mert a megadás `popUpTo(Permission, inclusive=true)`-tal navigál el, ami törli a ViewModelt és félbe vágná a `viewModelScope` jobot (a DataStore/Room olvasás + enqueue előtt), különösen kikapcsolt rendszer-animációknál. Az `appScope`-on az `enqueue` garantáltan lefut. `rebuildNow()` (REPLACE) törli a hozzáférés előtti no-op munkát és friss, immár a kötetet látó felmérést indít.
### Added
- **`data/index/GrantSurveyPolicy.kt`** (tiszta policy) + **`GrantSurveyPolicyTest.kt`** (JVM igazságtábla-teszt: minden ág — access ki/be, indexelés ki/be, mind az 5 `IndexStatus` — bizonyítva).
- **`di/CoroutineModule`**: `@ApplicationScope` minősítő + `@Singleton` provider (`SupervisorJob + Dispatchers.IO`) — fire-and-forget munkához, ami túléli az őt indító komponenst.
- **Őszinte in-app magyarázat a platform-korlátról**: az „App storage" képernyőn magyarázó kártya („Miért nem böngészhetők ezek a fájlok"), a permission-képernyőn pedig rövid megjegyzés — pontosítva, hogy Android 11 óta az `Android/data`/`Android/obb` MINDEN fájlkezelő elől tiltott (All-Files-Access-szel is), a telepített APK-k (`/data/app`) és app-cache pedig root nélkül SEMMILYEN Android-verzión nem böngészhetők (nem scoped-storage kérdés — ez a review egy megerősített pontossági javítása volt).
### Changed
- `PermissionViewModel` (+ `SettingsDataStore`/`IndexStateRepository`/`IndexingScheduler`/`@ApplicationScope` injektálás); `PermissionScreen.kt` + `AppStorageScreen.kt` (magyarázó szövegek); `versionName` → 0.30.0.
### Known issues / megjegyzés
- A ~190 GB app-privát tárhely (`Android/data`/`Android/obb`) továbbra sem böngészhető SEMMILYEN fájlkezelővel Android 11+ alatt (csak root) — ez platform-korlát, nem Jupiter-hiba; a méretét a v0.29 App-storage képernyő `StorageStatsManager`-rel számolja el. A megadás→felmérés trigger on-device viselkedése (a policy-döntés JVM-tesztelt) készüléken validálandó. Index oldal hátraléka változatlan: #5 letöltés-dup null-hash, #6 delta-sync, #7 mutation coordinator, #9 rescan pipeline, #11 timestamp, #12 több kötet, #13 perceptuális, #14 FTS.

## [jupiter:0.29.0] - 2026-07-06
### Added — Per-app tárhely-bontás (a „hova tűnt a 190 GB" válasza)
- **Új „App storage" képernyő** (`feature/apps/*`, `data/apps/AppStorageSource`): a `StorageStatsManager`-rel kiolvassa MINDEN telepített app tárhelyét (APK + adat + cache), méret szerint csökkenő listában, app-ikonnal, összesítő fejléccel („X GB, N app, Y GB törölhető cache"). Ez számol el a ~190 GB app-privát tárhellyel (`Android/data`, `Android/obb`, cache), amit a fájlrendszeren keresztül SEMMILYEN fájlkezelő nem lát Android 11+ alatt.
- **Usage-access engedélykezelés**: mivel a `StorageStatsManager` a különleges „Usage access" (PACKAGE_USAGE_STATS app-op) engedélyt igényli, hiánya esetén a képernyő egy magyarázó CTA-t mutat, ami a rendszerbeállításokba visz; visszatéréskor automatikusan újralekérdez.
- **Belépési pont a Storage Analytics-ból**: új „App storage" kártya, ami kiírja a becsült app-adat/cache méretet (a „used" és az indexelt fájlok különbségét) és a részletes bontásra navigál.
### Changed
- `AndroidManifest.xml`: `PACKAGE_USAGE_STATS` engedély; `Destinations`/`JupiterNavHost`: `AppStorage` útvonal; `versionName` → 0.29.0.
### Known issues / megjegyzés
- A per-app méret csak a Usage-access megadása után látszik (platform-követelmény). A `StorageStatsManager` réteg on-device igényel validálást (a CI-ben JVM unit-teszt csak a modell-matematikát fedi: `AppStorageInfoTest`). Változatlanul hátra az index oldalon: #5 letöltés-dup null-hash, #6 delta-sync, #7 mutation coordinator, #9 rescan pipeline, #11 timestamp, #12 több kötet, #13 perceptuális, #14 FTS.

## [jupiter:0.28.0] - 2026-07-05
### Fixed (#10 — a teljes tárhely-felmérés VÉGRE lefut a végéig)
- **A felmérés mostantól FOREGROUND + EXPEDITED worker**: eddig sima háttér-`OneTimeWorkRequest` volt, amit a Doze / háttér-végrehajtási korlátok fojtottak és megöltek → egy nagy (200 GB-os) készüléken SOHA nem futott végig. Mostantól:
  - `IndexingWorker.getForegroundInfo()` + `setForeground(...)` a `doWork` elején → **előtérszolgáltatásként** fut folyamatos értesítéssel (a felhasználó LÁTJA is, hogy „Indexing storage — N files"), így a rendszer nem öli meg és végigfut.
  - `IndexingScheduler` `setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)` → azonnal indul, nem vár háttérablakra (quota kimerülésekor is lefut).
  - Manifest: `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` engedély + `foregroundServiceType="dataSync"` a WorkManager előtérszolgáltatására (API 34+).
- **Folytathatóság (resumability) helyesen**: a reconciliation séta mostantól csak az AKTUÁLIS generáció (a jelenlegi seed) útvonalait ugorja át (`pathsAtGeneration(gen)`), nem az összeset — így egy megszakadt korábbi generáció sorai újra-bélyegződnek az új generációval és a záró stale-sweep NEM törli a saját részleges haladását. Robolectric teszt (`resumedSurveyRestampsPriorProgressInsteadOfSweepingIt`) bizonyítja.
### Fontos, őszinte platform-korlát (nem Jupiter-hiba)
- A 215 GB „használt"-ból a fájlrendszeren keresztül csak ~a hozzáférhető rész (a régi séta ~23,5 GB-ot látott) indexelhető. A maradék ~190 GB az **`Android/data` / `Android/obb`** — app-privát tárhely (játékok, app-cache, letöltött asset-ek), amit az **Android 11+ MINDEN fájlkezelő elől letilt**, All-Files-Access-szel is. Ez platform-korlát; a Jupiter mostantól a teljes HOZZÁFÉRHETŐ tárhelyet megbízhatóan végigindexeli. (Az app-ok által foglalt hely megjelenítése `StorageStatsManager`-rel külön, opcionális jövőbeli fejlesztés.)
### Changed
- `IndexingWorker`/`IndexingScheduler`/`AndroidManifest.xml`; `FileIndexDao.pathsAtGeneration` + repo; `versionName` → 0.28.0.
### Known issues / hátralévő
- Checkpoint/mid-walk resume (jelenleg megszakadáskor a séta újraindul, de a már indexelt sorokat megtartja és újra-bélyegzi); #5 letöltés-dup null-hash; #6 MediaStore generation delta-sync; #7 teljes mutation coordinator; #9 egy rescan pipeline; #11 timestamp-normalizálás; #12 több kötet; #13 perceptuális; #14 FTS/migrációk.

## [jupiter:0.27.0] - 2026-07-05
### Fixed (#4 — az index mostantól a TELJES tárhelyet fedi, nem csak a média-katalógust)
- **Coverage-regresszió javítva**: a v0.24–0.26 csak MediaStore-ból épített (a média/letöltés/dokumentum katalógus), ezért a Storage Analytics csak ~6,6 GB-t mutatott a ~215 GB-ból — a MediaStore nem lát mappákat, `.nomedia`-t, és sok nem-média fájlt. Mostantól a felmérés **kétfázisú**:
  1. **Gyors MediaStore SEED** (azonnali részleges eredmény, nulla per-fájl syscall);
  2. **Fájlrendszer RECONCILIATION séta**, ami hozzáadja mindazt, amit a seed kihagyott — **nem-média fájlok ÉS mappák** —, a már indexelt (média) útvonalakat stat NÉLKÜL átugorva, minden új bejegyzést **egyetlen `stat`-tal** (`FileSystemDataSource.toIndexItem`, `Files.readAttributes`) leképezve (a régi ~6-syscall/fájl helyett).
  Az index csak a reconciliation után lesz **COMPLETE**, tehát a COMPLETE mostantól azt jelenti: „teljesen felmérve", nem „média-katalógus". Amíg a reconciliation fut (RUNNING), a képernyők az élő felmérésből szolgálnak ki (nem egy hiányos indexből).
- A két fázis **azonos generációval** ír, így a stale-sweep egyiket sem törli; a mappák is bekerülnek az indexbe (a böngésző index-fallbackje így valódi mappákat lát).
### Added
- `FileSystemDataSource.toIndexItem` (minimál-syscall metadata `Files.readAttributes`-szel); `FileIndexRepository.indexedPaths()` + `FileIndexDao.allPaths()`; új Robolectric teszt (`seedPlusReconcileShareGenerationAndSurviveSweep`) bizonyítja, hogy a seed+reconciliation sorok azonos generációval túlélik a sweepet és a mappák is indexelődnek.
### Changed
- `IndexingWorker`: a régi „csak ha 0 sor" fallback-walk helyett MINDIG lefut a reconciliation a seed után; `versionName` → 0.27.0.
### Known issues / hátralévő
- A reconciliation séta a háttérben fut, foreground/checkpoint (#10) nélkül — nagy fánál lassabb lehet, de a seed azonnali részeredményt ad és a séta folytatás nélkül újraindul megszakadáskor. Továbbra is hátra: #5 letöltés-dup null-hash, #6 MediaStore generation delta-sync, #7 teljes mutation coordinator (copy/restore subtree), #9 egy rescan pipeline, #10 foreground+checkpoint+resumable, #11 timestamp-normalizálás, #12 több kötet, #13 perceptuális, #14 FTS/migrációk.

## [jupiter:0.26.0] - 2026-07-05
### Added (index állapotgép alap — a 2. szakértői review P0 magja, VALÓDI Room-tesztekkel)
- **Room `index_state` tábla + `IndexStateRepository`** (#2): a completeness mostantól **tranzakcionálisan a Roomban** él (EMPTY/RUNNING/COMPLETE/DIRTY/FAILED + generációk), nem egy DataStore boolean. A DataStore `indexComplete` flag **törölve** — így egy DB-wipe sosem hagyhat maga után egy „kész” flaget üres index fölött (a state a data-val együtt nullázódik → EMPTY → rebuild).
- **Egyetlen, központi completeness-kapu MINDENHOL** (#1): a Cleanup, Storage Analytics, Large Files, Duplicate, Home és a kereső mostantól **`metadataStatus == COMPLETE`**-re kapuz, nem `isPopulated()`-re (rekordszám). Egy 500-soros részleges index többé nem számít késznek egyetlen képernyőn sem.
- **Generáció-alapú scan + globális stale-sweep** (#3): minden teljes felmérés új `scanGeneration`-t kap, minden írt sort ezzel bélyegez; a sikeres scan végén egy tranzakcióban törli az elavult (korábbi generációjú) sorokat → a más app által törölt „szellemfájlok” eltűnnek. A delta/böngészés-sorok (generáció 0) sosem esnek áldozatul.
- **Könyvtár-átnevezés/mozgatás ATOMI** (#7 rész): a részfa-átírás mostantól **egyetlen Room `@Transaction`**-ben (delete+insert) — process-death a kettő között nem tüntetheti el a részfát.
- **Index kikapcsolás mostantól tényleg leállít** (#8 rész): letiltáskor a futó worker **cancel**-elve, az adatbázis törölve, a state **EMPTY**-re állítva; a `JupiterApp` induláskor az `indexingEnabled`-t IS ellenőrzi, így letiltás után nem indítja újra magától.
- **Robolectric + Room JVM-tesztek** (`IndexStateMachineTest`) a CI `testDebugUnitTest`-ben — VALÓDI in-memory Room ellen bizonyítják: megszakított scan ≠ COMPLETE; sikeres scan → COMPLETE + stale-sweep törli a törölt fájlt; delta-sorok túlélik a sweepet; változatlan fájl hash-e megmarad rescan után; átnevezés a teljes részfát átírja a hash megőrzésével; testvér-prefix mappát nem érint.
### Changed
- `gradle/libs.versions.toml` + `app/build.gradle.kts`: Robolectric 4.14.1 + androidx-test-core + room-testing testImplementation; `testOptions.unitTests` engedélyezve; `FileIndexDatabase` version → 2 (destructive fallback — cache; a state Roomban van); `versionName` → 0.26.0.
### Known issues / továbbra is hátralévő (a review többi pontja, ŐSZINTÉN):
- #4 kiegészítő fájlrendszer-reconciliation (MediaStore csak fájlokat lát, mappákat/`.nomedia`-t nem — a COMPLETE jelenleg „a survey lefutott”, nem „minden bájt indexelve”); #5 letöltés-duplikáció null-hash meglévő fájl esetén; #6 MediaStore generation delta-sync + version check + DeltaSyncWorker; #7 teljes IndexMutationCoordinator (copy/restore subtree, extract/import); #9 egyetlen rescan pipeline; #10 foreground + checkpoint + resumable worker; #11 timestamp-normalizálás (mp vs ms); #12 több kötet (SD/OTG) volumeId-vel; #13 perceptuális kép-near-duplicate; #14 FTS + Paging + DB-indexek méret/hash-re + explicit migrációk; a maradék end-to-end instrumentációs teszt-mátrix.

## [jupiter:0.25.0] - 2026-07-05
### Fixed (index life-cycle — P0 a szakértői architektúra-review alapján)
- **#1 Töredékes index többé nem számít késznek**: a rendszer eddig `indexedCount == 0`/`isPopulated()` alapján döntötte el, hogy van-e kész index — így egy 500 rekord után megszakadt scan „késznek” látszott és a maradék 44 500 fájl sosem került be. Új **completeness-jelző** (`SettingsDataStore.indexComplete`, DataStore): a worker indításkor `false`-ra, sikeres befejezéskor `true`-ra állítja; a `JupiterApp` mostantól **NEM a rekordszám, hanem a completeness** alapján indít (re)build-et. Megszakított/kill-elt scan után a következő indítás újraépít.
- **#3 A keresés nem járja be újra a teljes tárhelyet**: kész index esetén a sima szöveges keresés **kizárólag a Room-indexből** szolgál ki — nulla `walkTopDown`. Élő bejárás csak akkor fut, amíg az első teljes index el nem készült (fallback), illetve természetes-nyelvi lekérdezésnél. (Ez volt az egyik legközvetlenebb oka a „nem működik igazán az index” érzésnek.)
- **#4 A teljes újraindexelés nem nullázza a hash-eket**: az `upsert()` eddig minden rekordot `contentHash = null`-lal írt felül (a `toEntry()` miatt), így egy rebuild eldobta a duplikátumkereséshez drágán kiszámolt hash-eket. Mostantól **változatlan azonosság (méret+mtime) esetén megőrzi** a tárolt hash-t (egy batch-elt lekérdezéssel).
- **#5 Könyvtár átnevezés/mozgatás nem veszíti el a részfát**: az `onMovedOrRenamed()` eddig **törölte az egész részfát** és csak az új gyökeret indexelte vissza → egy 10 000 fájlos mappa átnevezésénél 10 000 leszármazott eltűnt az indexből. Mostantól **útvonal-prefix átírás** történik (a teljes részfa path/parentPath-ja átíródik, a hash-ek megmaradnak). Új tiszta segédosztály `data/index/IndexPathRewrite.kt` (szegmens-pontos, testvér-mappát nem érint) **JVM unit-tesztekkel** (`IndexPathRewriteTest`).
### Added
- **`data/index/IndexPathRewrite.kt`** — tiszta, függőségmentes path-aritmetika (rewrite / parentOf / nameOf / identityUnchanged), CI-ben futó unit-tesztekkel.
### Changed
- `app/build.gradle.kts`: `versionName` → 0.25.0.
### Known issues / szándékos halasztás (őszinte roadmap, NEM elhallgatva)
- A review többi pontja nagyobb, generációs/sémás átalakítást igényel és a következő körökre marad: **#2 globális stale-sweep + teljes `index_state` táblagép** (generation-oszlop, séma-migráció), **#6/#10 foreground + resumable/checkpoint worker**, **#12 több kötet (SD/OTG) volumeId-vel**, **MediaStore generation-alapú delta-sync + verzióellenőrzés**, **#11 tipizált könyvtárlistázás (Success/AccessDenied)**, **#13 perceptuális kép-near-duplicate**, **FTS keresés**, **#15 index-diagnosztika UI**.
- **Tesztelési korlát (őszintén):** ebben a körben **JVM unit-tesztek** futnak a tiszta logikára (path-rewrite, hash-megőrzési feltétel). A review-ban kért **eszköz-/instrumentációs elfogadási tesztek** Android emulátort/eszközt igényelnek, amit a jelenlegi CI (csak `testDebugUnitTest`) nem futtat — ezeket NEM állítom late-futottnak; a következő körben Robolectric/instrumentation kell hozzá.

## [jupiter:0.24.0] - 2026-07-05
### Added
- **Villámgyors indexálás (MediaStore-alapú felmérés)** — a 10-ügynökös kutatás (`wf_bf589356-466`, a 10 legjobb technika közül a nyertes kombináció) alapján: a háttér-felmérés eddig a `/storage/emulated/0` fát járta be `java.io`-val (FUSE/sdcardfs → fájlonként ~6 syscall + könyvtáranként `list()` a childCounthoz + `canonicalPath` realpath), ezért 222 GB indexálása **40+ perc alatt is csak ~10%-ig** jutott. Mostantól a felmérés **egyetlen `MediaStore.Files` kurzorból** építi az indexet — ez az Android SAJÁT, előre felépített indexe a megosztott kötetről —, **nulla fájlonkénti syscall és fabejárás nélkül**: a felhasználó fájljainak túlnyomó része **másodpercek alatt** indexelődik (a kutatás szerint ~100–1000× a lefedett halmazra). Új `data/index/MediaStoreIndexSource.kt` (lapozott, batch-elt, kizárja az `Android/data|obb`/`.thumbnails`/`.trashed` szegmenseket). Ha a MediaStore semmit sem ad (provider-hiba), a régi fabejárás a biztonsági tartalék, így az index sosem marad üresen.
- **Valódi százalékos folyamatjelző**: a worker mostantól `indexed`/`total`-t publikál (a `total` a MediaStore azonnali `count()`-jából), a Beállítások index-sora pedig **"Indexing files… 45%"** + "12 345 / 27 000 files" + determinisztikus `LinearProgressIndicator`-t mutat a végtelen pörgő helyett.
### Changed
- **A felmérés NEM hash-el többé** (a `hashCollidingSizes` 2. fázis kikerült a worker kritikus útjából — gigabájtokat olvasott). A duplikátum-hash-ek továbbra is lazán, a Cleanup/Duplicate képernyő első megnyitásakor számolódnak (`hashForEntry`, cache-elve) — a felmérés így nem olvas fájltartalmat.
- `app/build.gradle.kts`: `versionName` → 0.24.0.
### Known issues
- A MediaStore-alapú felmérés fájlokat indexel (nem könyvtár-sorokat); a könyvtár-sorok böngészéskor a per-könyvtár self-heal-lel kerülnek be. Nem-MediaStore fájlok (pl. `.nomedia` mappákban, sosem böngészve) a következő böngészésig/rebuildig hiányozhatnak — a sebesség a prioritás, ahogy a vezető fájlkezelők (Files by Google stb.) is teszik.
### Planned next
- Opcionális: a worker expedited/foreground futtatása (a kutatás #3 technikája) a tartalék-bejárás gyorsítására; Room WAL/tranzakció-hangolás a bulk-inserthez.

## [jupiter:0.23.0] - 2026-07-05
### Added
- **Böngésző ↔ index bekötés**: minden könyvtárlistázás mostantól **öngyógyítja az indexet** (`FileRepositoryImpl.listFiles` → `replaceChildren` a nyers, szűretlen lemez-listával, a már nem létező gyerekeket pruneolva), így böngészés közben az index folyamatosan teljes és naprakész marad (nemcsak a háttér-felmérésből). Új `FileRepository.listFromIndex(path, sort, filter)` — az indexből szolgál ki (ugyanazzal a rendezéssel/szűréssel). Ha egy lemez-olvasás hibázik (átmeneti IO / pillanatnyi engedély-galiba), a böngésző **visszaesik az indexre**, és a korábban meglátogatott mappa utolsó ismert tartalmát mutatja üres hiba helyett.
- **Storage Analytics "Instant from index" chip**: ha a bontás azonnal az indexből jött (nem élő séta), egy kis chip jelzi ("Instant from index · N") — a Cleanup bannerrel egységesen, hogy látszódjon: az indexált felmérés dolgozik.
### Fixed
- **Duplikátum-törlés biztonság megerősítése** (adatvédelem a törlési úton): az index-alapú duplikátum-csoportosítás mostantól a tárolt tartalom-hash-t **CSAK akkor bízza meg, ha a fájl a lemezen még mindig ugyanaz** (méret ÉS mtime egyezik az indexelttel). Ha egy fájl delta nélkül megváltozott a lemezen, a rendszer **újrahash-eli a jelenlegi bájtokból** és frissíti az index sort — így két valójában különböző fájl SOHA nem kerülhet egy duplikátum-csoportba egy elavult hash miatt (ami rossz fájl törléséhez vezethetne). Az eltűnt/nem-fájl bejegyzések kimaradnak (`hashForEntry` null-t ad).
### Changed
- `FileRepository`: új `listFromIndex`; `StorageAnalyticsUiState`/`ViewModel`/`Screen`: `fromIndex`+`indexedCount`.
- `app/build.gradle.kts`: `versionName` → 0.23.0.
### Verification
- 6-lencsés adverzariális review-workflow (deletion-safety, correctness, Room/SQL, coroutines, Compose/state, regression/DI) minden találatot skeptikus-szavazással megerősítve.
### Planned next
- Browser opcionális azonnali index-snapshot megjelenítés (jelenleg lemez-elsődleges, index a fallback); widget `onNewIntent`.

## [jupiter:0.22.0] - 2026-07-05
### Added
- **Az indexált felmérés MOSTANTÓL tényleg meghajtja a képernyőket (nem kell minden megnyitáskor deep-scan)**: eddig az index fel volt építve és real-time frissült, DE a nehéz képernyők (Cleanup, Duplicate cleanup, Storage Analytics, Home) **minden megnyitáskor újra végigjárták a teljes `/storage/emulated/0` fát**. Mostantól, ha a háttér-felmérés már lefutott, ezek **azonnal az indexből** szolgálják ki az adatot, fájlrendszer-bejárás nélkül:
  - `StorageAnalyticsRepositoryImpl.storageOverview` / `observeStorageOverview` → a kategória-bontást az index sorokból aggregálja (ugyanazzal a `categoryForPath`/kizárás-logikával, mint a séta).
  - `findLargeFiles` → az index `largeFiles(minSize, limit)` lekérdezéséből (méret szerint csökkenő).
  - `findDuplicates` → a duplikátum-jelölteket az index méret-ütközéseiből (`collidingSizes`+`filesOfSize`) veszi (nincs séta), tartalom-hash-sel megerősítve.
  - Ha az index üres (első indítás előtt), automatikusan visszaesik az élő sétára — nincs regresszió.
- **A felmérés MOST már hash-eli a méret-ütköző fájlokat** (`IndexingWorker` 2. fázisa → `FileIndexRepository.hashCollidingSizes`): a duplikátumok tartalom-hash-e előre kiszámolódik, így a Cleanup/Duplicate képernyőn a duplikátum-csoportok **azonnal** megjelennek a felmérés után (nem az első megnyitáskor kell hash-elni).
- **Manuális teljes újrascan**: a Cleanup ⟳ gombja mostantól friss fájlrendszer-sétát futtat (ground-truth) ÉS elindít egy háttér-index-újraépítést (`IndexingScheduler.rebuildNow`), így a következő megnyitások megint azonnaliak és naprakészek.
- **Látható index-státusz banner** a Cleanupon: "Instant results from your file index — N indexed — downloads and edits update it automatically", benne egy "Rescan" gombbal, hogy a felhasználó lássa, hogy az index dolgozik.
### Fixed
- **Hiányzó real-time delta hook-ok** (`FileRepositoryImpl`): az **átnevezés** (`rename`) és a **mappalétrehozás** (`createFolder`) eddig NEM frissítette az indexet, így egy átnevezett fájl régi útvonala bennmaradt az indexben a következő teljes újraépítésig. Most `onMovedOrRenamed` / `indexFile` hívás (best-effort, sosem bukik el a művelet).
### Changed
- `FileIndexDao`: új lekérdezések (`fileCount`, `largeFiles`, `allFiles`, `collidingSizes`, `filesOfSize`); `FileIndexRepository`: `isPopulated`, `largeFiles`, `allFiles`, `duplicateGroups`, `hashCollidingSizes`; `StorageAnalyticsRepository` metódusai `preferIndex` (alapból true) kapcsolóval — a manuális rescan false-szal kényszerít friss sétát.
- `app/build.gradle.kts`: `versionName` → 0.22.0.
### Known issues
- Az index-alapú duplikátum a tárolt hash-t bízza meg (a real-time delták + a manuális Rescan tartják naprakészen); egy app-on-kívül, delta nélkül megváltozott fájl a következő felmérésig/rescan-ig elavult lehet.
### Planned next
- Browser könyvtárlista opcionális index-forrásból (jelenleg lemezről, ami egy könyvtárnál gyors); Home/Analytics további finomítás; widget `onNewIntent`.

## [jupiter:0.21.0] - 2026-07-04
### Changed
- **Smart Cleanup összecsukható szekciók — sokkal egyértelműbb tap-affordancia**: a "Large files" és "Duplicate files" fejlécek mostantól **kitöltött, kattintható Card**-ként jelennek meg (tonális háttér + **"Show"/"Hide"** felirat + forgó chevron), így vizuálisan is nyilvánvaló, hogy a szekció NEVÉRE koppintva nyílik/csukódik. Nyitott állapotban a kártya `secondaryContainer` színt kap. A viselkedés változatlanul helyes: alapból **összecsukva** (`mutableStateOf(false)`), a fájl-sorok CSAK `if (largeExpanded)` / `if (duplicatesExpanded)` mögött generálódnak — koppintásra jelennek meg. (A 0.20.0-ban már bekerült a logika; ez a kör a felismerhetőséget javítja, hogy a felhasználó biztosan lássa a kattintható kontrollt.)
### Fixed
- A korábbi bare `Row` fejléc (kicsi chevronnal) nem olvasódott egyértelműen kattinthatónak — ezért tűnhetett úgy, hogy "nem lehet a Large files / Duplicate files-ra kattintva összecsukni-kibontani". A Card-alapú fejléc ezt megszünteti.
### Changed
- `app/build.gradle.kts`: `versionName` → 0.21.0.
### Planned next
- Widget `onNewIntent`; több ellenőrzési faktor kikényszerített teszté; compress valós idejű progressz.

## [jupiter:0.20.0] - 2026-07-04
### Added
- **Dual pane élő ejtés-cél kiemelés**: húzás közben a mutató alatt lévő MAPPA dinamikusan besötétedik (követi az ujjat, minden mozdulatnál újraszámolva), így látszik hova esik; a cél-panel is halványan kiemelődik. Az ejtés viselkedése és minden dual-pane funkció változatlan.
- **Smart Cleanup**: a "Large files" és "Duplicate files" szekciók **összecsukhatók, alapból összecsukva** (a fejlécek látszanak, a sorok kattintásra nyílnak); a sorok valódi bélyegképet mutatnak; a "Reclaim X" alsó sáv `navigationBarsPadding`-gel a rendszer navigációs gombjai FÖLÉ került (használhatóvá vált).
- **Index kezdeti felmérés auto-indítás**: app-indításkor, ha az index üres, háttérben lefut a alapos felmérés (`ensureIndexed`, KEEP policy — nem szakít meg futó felmérést); utána a valós idejű delták + a letöltés-figyelő tartják naprakészen, így megnyitáskor nincs deep-scan várakozás. Csak `indexedCount==0`-nál indul.
### Changed
- `app/build.gradle.kts`: `versionName` → 0.20.0.
### Planned next
- Widget `onNewIntent`; több ellenőrzési faktor kikényszerített teszté; compress valós idejű progressz.

## [jupiter:0.19.0] - 2026-07-02
### Added
- **App-szintű videó-bélyegképek**: a `JupiterApp` mostantól `coil.ImageLoaderFactory`, `VideoFrameDecoder`-rel — az `AsyncImage` valódi videó-kockát mutat MINDENHOL (Compress, duplikátum-sorok, kategória-böngésző, dual-pane…), a korábbi csapó-ikon helyett.
- **Compress finomítás**: a válogatóban a **teljes fájlnév** + működő előnézet; minden Target-quality presetnél **becsült méret ("≈ N MB")** tömörítés előtt (`MediaCompressor.estimateCompressedSize` heurisztika); a részletnézet is videó-kockát mutat.
- **"Duplicate cleanup"** (a Duplicates + Smart Merge összeolvasztva): egy menüpont, ami egyesíti a kézi többszörös-kijelölés + törlés (Duplicates) és az egy-kattintásos **"Keep best"** (a legjobb példány megtartása, a többi kijelölése — Smart Merge) képességet. A duplikátum-sorok valódi bélyegképet mutatnak.
### Removed
- A redundáns **Smart Merge** képernyő/VM/UiState törölve; `Destination.SmartMerge` + NavHost útvonal + a More csempe eltávolítva. Nulla maradék hivatkozás (grep-ellenőrzött). Semmilyen képesség nem veszett el.
### Changed
- `app/build.gradle.kts`: `versionName` → 0.19.0.
### Planned next
- Több ellenőrzési faktor kikényszerített teszté; widget `onNewIntent`; compress valós idejű progressz finomítás.

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
