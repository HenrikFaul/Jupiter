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

> **A bizonyíték forrását mindig nevezd meg.** Ebben a munkatérben a helyi Android SDK/Gradle build is
> elérhető, ezért a lokális APK- és teszteredmény kötelező kapu. A GitHub Actions
> (`.github/workflows/android.yml`) külön távoli kapu: CI-zöldet csak konkrét sikeres run alapján szabad
> állítani. A két eredmény nem helyettesíti és nem hamisíthatja egymást.

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

## [jupiter:0.36.0] - 2026-07-08
### Added — A dedup-blueprint HIÁNYZÓ magja kódban: fúziós pontozó + döntési szintek + magyarázhatóság + új típusrétegek
- **Fúziós pontozó motor** (`data/index/dedup/SimilarityScorer` + `SimilarityModel`): a független hasonlósági rétegeket (IDENTITY, METADATA, STRUCTURAL, SAMPLE, PERCEPTUAL, SEMANTIC) egyetlen [0,100] pontszámmá, **6 döntési szintté** (UNRELATED→WEAK→POSSIBLE→PROBABLE→VERY_LIKELY→EXACT), konfidenciává és **ember-olvasható magyarázattá** olvasztja. Szabályok: az exact tartalom-hash MINDIG dominál (EXACT); **típusfüggő súlyok** (kép→perceptuális 55, APK→struktúra 60, szöveg/kód→szemantikus 60, bináris→minta 55…, mind 100-ra normálva); **hard veto**-k (aláíró-, méret-, hossz-eltérés) LEfelé korlátozzák a pontot (sosem fel). Tiszta, teljes igazságtáblával tesztelve (`SimilarityScorerTest`).
- **Szöveg/kód réteg — SimHash** (`TextSimHash`): 64 bites, formázás-érzéketlen (kisbetűs, tokenizált) ujjlenyomat; a Hamming-távolság ≤ 3 → közeli duplikátum. Túléli az újraformázást/kis szerkesztést (a tartalom-hash nem). Tesztelve.
- **APK réteg** (`ApkIdentity` + `ApkComparator`): csomagnév + aláíró-tanúsítvány + verziókód alapján megkülönbözteti: SAME_EXACT / SAME_APP_UPDATE (verzió-család, NEM törlendő) / DIFFERENT_SIGNER (átcsomagolt — veto, sosem „ugyanaz az app") / UNRELATED. Tesztelve.
- **Minta/chunk réteg** (`SampleOffsets`): nagy bináris fájlok determinisztikus, méret-magvú mintavételi tartományai (első/utolsó/közép + belső pozíciók) — gyors előszűrő. Tesztelve.
- **Magyarázható riasztások**: a `DuplicateAlert` mostantól `tier` + `explanation` mezőt hordoz; az észlelő a fúziós pontozón át tölti ki (EXACT → „Identical content — same bytes as N files"; SIMILAR → perceptuális rétegből számolt szint + „Same picture … different size or format").
### Changed
- `docs/DEDUP_ARCHITECTURE.md`: a fúziós pontozó, a szöveg/kód és APK réteg „shipped"-re jelölve; `DuplicateAlert`/`DuplicateDetector` a tier+magyarázat kitöltésével; `versionName` → 0.36.0.
### Known issues / megjegyzés
- A videó/PDF/audió/archívum deszkriptor-kinyerés és a szemantikus embeddingek + ANN-jelölt-lekérdezés továbbra is a tervrajz szerinti következő körök; a mostani kör a pontozó/szint/magyarázhatóság gerincét és a kép/szöveg-kód/APK/bináris réteg-logikát szállítja (tesztelve). A pontozó jelenleg az elérhető rétegekből dolgozik; a hiányzó extraktorok bekötése további körökben történik.

## [jupiter:0.35.0] - 2026-07-08
### Fixed — A DUPLIKÁTUM-FELISMERÉS VALÓDI GYÖKÉR-OKA: az észlelés csak élő process alatt futott
- **Gyökér-ok (végre a valódi)**: a duplikátum-észlelés KIZÁRÓLAG a `DownloadIndexObserver`-en át futott, ami CSAK amíg az app process él. Amikor Chrome-ból/kameráből/üzenetküldőből érkezik fájl **zárt Jupiter mellett** (a tipikus eset!), semmi nem figyeli, és a következő app-megnyitáskor a felmérés csak metaadatot indexel — dedup-ellenőrzést NEM futtat az új fájlra. Ráadásul élő process alatt is törékeny volt: sok eszközön a `ContentObserver` a puszta gyűjtemény-URI-val tüzel, így a `resolvePath` egy TETSZŐLEGES sort oldott fel, nem az új fájlt. Ezért nem jelzett egyik teszt-esetben sem.
- **Javítás — checkpoint-alapú MediaStore delta-rekoncilátor** (`DedupReconciler` + `MediaStoreIndexSource.queryNewSince/maxObservedId` + `dedupCheckpointId` DataStore-ban): a rendszer a checkpointnál (MediaStore `_id`) ÚJABB MINDEN fájlt lekérdez a MediaStore-ból, és mindegyiket átfuttatja az észlelőn, majd monoton előrelépteti a checkpointot. Ez elkapja a zárt app mellett érkezett fájlokat is, és sosem old fel rossz sort. A checkpoint-kulcs a MediaStore `_id` (egyedi + szigorúan növekvő), nem a másodperc-granularitású dátum — így egy másodpercen belül érkező több száz fájl (tömeges import) is hézagmentesen lapozódik. Kick-pontok: minden előtérbe kerülés (`MainActivity.onStart`), minden `ContentObserver`-jel (az observer mostantól TISZTA TRIGGER, nem adatforrás), és a 12 órás periodikus worker.
- **Baseline-szabály**: az első futáskor (checkpoint 0) NEM riaszt a meglévő könyvtárra — csak rögzíti a jelenlegi max MediaStore-dátumot alapvonalként; csak az AZUTÁN érkező fájlok riasztanak (a meglévő duplikátumokat a Duplicates-képernyő + a perceptuális backfill kezeli).
- **Egységes észlelő** (`DuplicateDetector`): az exact (tartalom-hash) és a perceptuális (dHash) észlelés + értesítés egy helyre került; a valós idejű observer ÉS a rekoncilátor is ezt hívja, így azonosan viselkednek. „Duplicate detected" (bájt-azonos) és „Similar image detected" (ugyanaz a kép más formátumban) külön értesítésként.
### Added
- Teljes **mérnöki tervrajz**: `docs/DEDUP_ARCHITECTURE.md` — 20 szekciós, implementáció-orientált dedup/hasonlóság-alrendszer terv (rétegek, típus-taxonómia, 7 hasonlósági réteg, deszkriptor-pipeline, jelölt-lekérdezés, fúziós pontozás + küszöbmátrixok, Room-séma, WorkManager-lánc, teljesítmény/skálázás, FP/FN, UX, megfigyelhetőség, tesztmátrix, anti-minták) — a jelenlegi kódra és a mostani rekoncilátorra alapozva.
- `NewFileSource` + `DedupCheckpointStore` + `ArrivalInspector` + `StorageAccessGate` seam-interfészek → a rekoncilátor logikája TISZTA JVM-teszttel (determinisztikus, nincs SharedFlow-időzítés), az észlelés pedig `onFileArrived` visszatérési értékén (nincs flow) bizonyítható.
### Changed
- `DownloadIndexObserver` teljes egyszerűsítés (törékeny URL-feloldás + saját értesítés törölve, mostantól csak trigger); `MediaStoreIndexSource` delta-lekérdezések; `SettingsDataStore.dedupCheckpointSeconds` (monoton); `IndexingScheduler.reconcileDedupNow` + `DedupReconcileWorker`; `IndexRefreshKickWorker`/`MainActivity` kick; `IndexModule` bindingek; a régi `ChangeDebounce(+Test)` törölve (a checkpoint-modell kiváltotta); `versionName` → 0.35.0.
### Adversarial review — 3 megerősített hiba javítva még commit előtt (8 agent, AOSP/WorkManager-szintű ellenőrzés)
- **HIGH — baseline-mérgezés**: friss telepítéskor, tárhely-engedély ELŐTT a `maxObservedId()` 0-t ad (SecurityException/üres), a régi `coerceAtLeast(1)` pedig 1-re rögzítette a checkpointot → engedély megadása UTÁN a `_id > 1` a TELJES könyvtárat „újként" riasztotta volna. Javítva: baseline CSAK valós id-nél (`> 0`) áll be, különben 0 marad és később újrapróbál; + a rekoncilátor `StorageAccessManager.hasAllFilesAccess()`-re kapuzva (hozzáférés nélkül no-op).
- **HIGH — expedited worker API 26-30-on halott**: a `DedupReconcileWorker` `setExpedited` volt `getForegroundInfo()` override nélkül → API < 31-en a WorkManager előtérszolgáltatásként futtatná és elbukik → a dedup ott sosem futott volna. Javítva: a kis, gyakori reconcile NEM expedited (sima KEEP munka, minden API-n fut).
- **MEDIUM — azonos-másodperc lapozási hézag**: >200 fájl azonos `date_added` másodpercben a laphatáron kimaradt. Javítva a `_id`-alapú kulcsolással (egyedi, nincs holtverseny).
### Verifikáció (valódi, CI-ben)
- `DedupCheckpointTest` (tiszta): a checkpoint monoton előrelép, holtversenynél +1 (nincs végtelen ciklus).
- **`DedupReconcilerTest` (Robolectric + in-memory Room + VALÓDI fájlok + VALÓDI `DuplicateDetector` + VALÓDI `StorageAccessManager`)**: bizonyítja a fixet — egy „zárt app mellett érkezett" bájt-azonos másolatot a rekoncilátor elkap és EXACT-ként jelez az eredetire; a baseline nem riaszt; a checkpoint előrelép; ugyanarra a fájlra nincs újra-riasztás; kikapcsolt indexelésnél nem csinál semmit; ÉS az üres/olvashatatlan MediaStore NEM mérgezi a baseline-t (checkpoint 0 marad).
### Known issues / megjegyzés
- A rekoncilátor a MediaStore-ból érkező fájlokat fedi (letöltés/kamera/média/dokumentum) — az `Android/data` app-privát fájljait Android 11+ alatt továbbra sem lát (platform-korlát). A videó/PDF/audió/APK/archívum deszkriptorok a tervrajz szerinti következő körök; a mostani kör a kép (exact + perceptuális) és minden típus bájt-azonos érkezés-észlelését szállítja.

## [jupiter:0.34.0] - 2026-07-07
### Added — Perceptuális (dHash) képfelismerés: ugyanaz a kép MÁS formátumban/felbontásban is felismerhető (#13 leszállítva)
- **`PerceptualHash` (tiszta dHash-mag)**: 64 bites difference-hash — a kép 9×8-as szürkeárnyalatos rácsán a szomszédos fényerő-viszonyokat kódolja, így az újratömörítés/átméretezés/formátumváltás alig változtatja; két kép „ugyanaz a kép", ha a Hamming-távolság ≤ 8/64. Android-mentes, teljes JVM-igazságtáblával tesztelve (növekvő gradiens = mind a 64 bit; kis perturbáció ≤ 2 bit; invertált kép = 64 távolság; UNHASHABLE sosem egyezik; méret-validálás).
- **`PerceptualHashSource` (dekóder)**: bounds-only decode → inSampleSize-os kicsinyített decode → 9×8 skálázás (pár ms/kép); Rec.601 luma; sérült/nem-kép fájl → `UNHASHABLE` szentinel (sosem próbálkozik újra végtelenül); OOM-védett. **Robolectric round-trip teszt BIZONYÍTJA a felhasználói kontraktust**: ugyanaz a jelenet PNG 240×180-ban és JPEG 120×90 q70-ben → near; invertált jelenet → nem near; szöveg-fájl .jpg néven → UNHASHABLE.
- **Érkezéskori felismerés** (`DownloadIndexObserver`): új képnél előbb bájt-azonos ellenőrzés (mint eddig), ha nincs találat → dHash számítás + tárolás + perceptuális összevetés a TELJES ujjlenyomat-készlettel → **„Similar image detected" értesítés** („same picture, different size or format"). Külön notification-id só, hogy az exakt és a hasonló riasztás ne üsse egymást.
- **Háttér-backfill a meglévő könyvtárra** (`PerceptualHashBackfillWorker`): kötegelt (100/db, max 2000/futás), battery-not-low, kooperatívan megszakítható; minden megpróbált sor garantáltan megjelölődik (hash vagy UNHASHABLE) → a munkalista mindig fogy, korrupt kép sosem okoz végtelen ciklust; ha marad munka, retry-jal folytatja. Kick-pontok: app-indulás, minden előtérbe kerülés, minden sikeres felmérés után (lánc), + a 12 órás periodikus frissítő közvetve.
- **Ujjlenyomat-megőrzés minden újraírási úton**: a `perceptualHash` a `contentHash`-sel azonos szabállyal él túl (identitás — méret+mtime — változatlan → megmarad; változott → törlődik és újraszámolódik) a `writePreservingHashes`/`indexFile`/`replaceChildren`/`onMovedOrRenamed` utakon — e nélkül minden 12 órás felmérés kinullázta volna az összes ujjlenyomatot (40k kép újra-dekódolása naponta kétszer). Room-teszt bizonyítja (megőrzés változatlan identitásnál + törlés változásnál; near-lekérdezés: küszöbön belül igen, önmaga/messzi/UNHASHABLE kizárva).
### Changed
- `FileIndexEntry.perceptualHash` (új oszlop) + DB **version 3** (destruktív fallback: az index cache, frissítés után egyszer újraépül — dokumentált viselkedés); `FileIndexDao` (`updatePerceptualHash` célzott UPDATE — generáció-biztos, `allPerceptualHashes` lean projekció, `imagesMissingPerceptualHash`); `FileIndexRepository(+Impl)` (put/findNear/needing); `IndexingScheduler.ensurePerceptualBackfill` + `cancel` kiegészítés; `IndexingWorker` (siker után backfill-lánc); `JupiterApp`/`MainActivity` kick; `versionName` → 0.34.0.
### Known issues / megjegyzés
- A DB-frissítés miatt az első indításkor az index egyszer újraépül, majd a backfill ujjlenyomatozza a képtárat (~40k kép, pár perc háttérben, akku-kímélő feltétellel). A „hasonló kép" értesítéshez az ELSŐ példánynak már ujjlenyomatozottnak kell lennie — a backfill lefutása után a teljes könyvtárra működik. Erősen kivágott/elforgatott képek (pl. státuszsoros screenshot egy fotóról) kívül eshetnek a küszöbön — ez a dHash ismert korlátja, nem hiba.

## [jupiter:0.33.0] - 2026-07-07
### Fixed — Uninstall gomb: eddig NÉMÁN semmit se csinált
- **Gyökér-ok**: Android 9 (API 28) óta az `ACTION_DELETE` rendszer-uninstall dialógushoz **`REQUEST_DELETE_PACKAGES`** engedély kell a manifestben — e nélkül a rendszer csendben elutasítja (a `runCatching` elnyelte). Hozzáadva (normál, install-time engedély, nincs user-prompt) → az App storage → app → Uninstall mostantól megnyitja a rendszer törlés-dialógusát.
### Fixed — Duplikátum-értesítés: 2 valódi blokkoló, ezért nem jelzett SOSEM
- **1. blokkoló — a POST_NOTIFICATIONS engedélyt SOHA senki nem kérte el**: Android 13+ alatt futásidejű engedély kell BÁRMILYEN értesítéshez; e nélkül a `notificationsAllowed()` false → minden „Duplicate detected" (és az „Indexing storage" előtér-értesítés is!) némán eldobódott. A `MainActivity` mostantól induláskor elkéri (egyszeri rendszer-dialógus; elutasítás esetén az app ugyanúgy működik, csak értesítés nincs).
- **2. blokkoló — csúszó debounce lenyelte a letöltés BEFEJEZŐ eseményét**: a MediaStore letöltés közben többször jelez (<1,5 mp-enként), és a csak-útvonal-alapú csúszóablak minden következő eseményt eldobott — a VÉGLEGES tartalom sosem lett hash-elve. Mostantól a debounce (útvonal+méret+mtime) IDENTITÁSRA kulcsol (`ChangeDebounce`, tiszta + JVM-tesztelt): az azonos állapotról szóló zaj összevonódik, de a kész fájl (más méret) mindig feldolgozódik. + `IS_PENDING` sorok kihagyása (félig írt fájl hash-elése kárba veszett IO).
- Fontos, őszinte megjegyzés: a mostani felismerés **bájtra azonos** másolatra jelez (újra letöltött ugyanaz a fájl). Ha a teszt-képpárjaid a megosztó app (pl. Facebook/Instagram) újra-tömörítésén estek át, a bájtok eltérnek — arra a perceptuális hash (#13, tervezett következő kör) kell.
### Added — Folyamatos háttér-frissesség (visszatérő fő kérés)
- **Periodikus háttér-frissítő** (`IndexRefreshKickWorker`, 12 óránként, battery-not-low): az `ensureIndexed`-en (KEEP) keresztül újra-felméri a tárhelyet, így az app zárva tartása alatt történt változásokat (más appok törlései/mozgatásai) is bedolgozza — az index magát tartja frissen. Szándékosan „kicker" worker: minden munka EGY unique néven fut át, két felmérés sosem versenyezhet (generáció-korrupció kizárva). Kikapcsolt indexelésnél a `cancel()` ezt is leállítja; visszakapcsolás újraütemezi.
- **Frissesség-ellenőrzés MINDEN előtérbe kerüléskor** (`MainActivity.onStart`): ha a felmérés még sosem ért COMPLETE-ig (menet közben megölték, engedély később jött), újra-biztosítja — nem csak process-induláskor. KEEP: futó felmérést sosem indít újra.
### Changed
- `AndroidManifest.xml` (`REQUEST_DELETE_PACKAGES`); `MainActivity` (POST_NOTIFICATIONS kérés + onStart ensure); `DownloadIndexObserver` (identitás-debounce, IS_PENDING) + `ChangeDebounce(+Test)`; `IndexingScheduler.schedulePeriodicRefresh` + `IndexRefreshKickWorker`; `JupiterApp`/`SettingsViewModel` (periodikus ütemezés); `versionName` → 0.33.0.
### Known issues / megjegyzés
- Az értesítés-teszthez: az új APK ELSŐ indításakor engedélyezd az értesítéseket a felugró rendszer-dialógusban, majd tölts le újra egy már meglévő fájlt (bájtra azonosat). Perceptuális (átméretezett/újratömörített) egyezés: #13, következő kör.

## [jupiter:0.32.0] - 2026-07-07
### Fixed — Újra-scan alatt NEM esik vissza az Analytics 3 GB-os részleges nézetre
- **Gyökér-ok (a felhasználó képernyőképéről)**: manuális újra-scan (⟳ / rebuild) alatt az index státusza RUNNING → a képernyők „nem kész index" gátja élő, részleges sétára váltott (3,0 GB analyzed a 120 GB helyett), a „App storage" becslés pedig used−analyzed alapon ~212 GB képtelenséget mutatott.
- **Javítás — `IndexStateRepository.isUsable()`**: az index HASZNÁLHATÓ olvasásra, ha COMPLETE, VAGY ha egy korábbi felmérés lezárult (`lastCompleteGeneration > 0`) és felette fut az újra-scan — a stale-sweep csak sikerkor fut, tehát a korábbi teljes generáció sorai érintetlenek. A Search/Analytics/Cleanup olvasó-gátjai erre váltottak; az ütemezési döntések (JupiterApp auto-start, GrantSurveyPolicy) továbbra is `isMetadataComplete()`-t nézik. A legelső, még sosem teljes scan és a reset-elt index továbbra sem „usable". Robolectric-teszt bizonyítja (`indexStaysUsableDuringRescanOverPriorCompleteGeneration`).
- **Analytics „App storage" becslés-őszinteség**: a used−analyzed becslés csak akkor jelenik meg, ha megbízható (index-kiszolgálás VAGY befejezett séta); élő séta közben semleges felirat. Az isUsable-váltással rebuild alatt amúgy is a teljes indexből jön az analyzed.
### Fixed — Duplikátum-értesítés VÉGRE elsül (a #5 rés zárva)
- Az azonnali index + „ez már megvan" értesítés eddig is élt (`DownloadIndexObserver`: a teljes MediaStore-t figyeli, új fájlt azonnal indexel, tartalom-duplikátumnál notification) — DE a `findContentDuplicates` csak a MÁR TÁROLT hash-ű sorok közt keresett, miközben a felmérés csak metaadatot ír → az eredeti példánynak sosem volt hash-e → az értesítés a leggyakoribb esetben (újra letöltött fájl) SOSEM sült el. Mostantól az azonos MÉRETŰ, hash nélküli jelölteket igény szerint behash-eli (olcsó: méret-ütközés ritka), és úgy keres. Robolectric-teszt VALÓDI fájlokkal bizonyítja (`newFileDuplicateIsFoundEvenWhenOriginalWasNeverHashed`: metaadat-only eredeti + azonos méretű eltérő tartalmú decoy).
### Added — Az App storage sorai kattinthatók (kérés: „nyissa meg és törölhessem")
- App-sorra koppintva akció-lap: **App info** (rendszer-Beállítások: cache/adat törlés, uninstall), **Fájlok böngészése** (`Android/media/<pkg>` — pl. WhatsApp-média — ha létezik, Jupiter-böngészőben nyílik), **Uninstall** (rendszer-dialógus; nem rendszer-appnál). Őszinte megjegyzés a lapon: másik app PRIVÁT adatát csak a rendszer-Beállítások törölheti — a Jupiter a megfelelő képernyőre visz (platform-szabály, minden fájlkezelőre érvényes).
### Adversarial review — 2 megerősített hiba javítva még commit előtt
- **4 KiB-os riasztási alsóhatár** (`MIN_ALERT_SIZE_BYTES`): e nélkül egy 0 bájtos érkező fájl (éppen INDULÓ letöltés placeholder-e, `.nomedia`) a kötet ÖSSZES üres fájljára hamis „Duplicate detected" riasztást adott volna. Robolectric-teszt fedi.
- **Generáció-megőrző hash-visszaírás** (`FileIndexDao.updateHash` célzott UPDATE): a korábbi teljes-soros upsert a hash-számítás ELŐTTI pillanatképet írta vissza — futó újra-scan alatt ez visszavonta volna a friss generáció-bélyeget és a záró sweep egy ÉLŐ fájl sorát törli. A `hashForEntry` és `putHash` már nem nyúl a `lastSeenGeneration`-höz. Robolectric-teszt fedi (`hashBackfillPreservesGenerationStamp`).
### Changed
- `IndexStateRepository(+Impl).isUsable`; `SearchViewModel`/`CleanupViewModel`/`StorageAnalyticsViewModel`/`StorageAnalyticsRepositoryImpl` gátak; `FileIndexRepositoryImpl.findContentDuplicates`/`putHash`/`hashForEntry` + `FileIndexDao.updateHash`; `AppStorageScreen` (akció-lap + kattintható sorok) + `JupiterNavHost` (`onOpenPath`); `StorageAnalyticsScreen.AppStorageEntry` (`estimateReliable`); `versionName` → 0.32.0.
### Known issues / megjegyzés
- A perceptuális közeli-duplikátum (ugyanaz a kép MÁS formátumban/felbontásban, pl. screenshot) még hátra van (#13): terv — 64-bites dHash az új képekre érkezéskor + háttér-backfill worker a meglévőkre, Hamming-távolság összevetéssel. A mostani javítás a bájtra azonos duplikátumot (újra letöltés, másolat más néven) fedi le értesítéssel.

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

## [jupiter:0.37.0] - 2026-07-08
### Fixed
- **Perceptuális near-dup most már valóban jelez a meglévő könyvtár ellen is** (data/index): a hiba az volt, hogy a `findNearDuplicateImages` CSAK a már ujjlenyomatolt sorokkal hasonlított, így egy hónapok óta a telefonon lévő, még nem backfillelt EREDETI kép láthatatlan volt → az újonnan letöltött/újratömörített másolat sosem talált egyezést. A `DuplicateDetector` most a near-összehasonlítás ELŐTT igény szerint (on-demand) ujjlenyomatol minden még hiányzó képet (kötegelt, megszakítható, perzisztált — az első képérkezés fizet egyszer, utána ingyen), így a felhasználó "ugyanaz a nő két képen" esete végre SIMILAR riasztást ad.
- **Duplikált fájl megnyitása nem ír többé "Not found" errort** (data/index): a Samsung `Android/.Trash/com.sec.android.app.myfiles/...` kuka-útvonalak (és a `.trashed`/`.thumbnails`) most már kizártak az indexelésből (MediaStore + IndexingWorker, kis-nagybetű-független szegmens-egyezés). Emellett a dedup-felületek (`findContentDuplicates`, `findNearDuplicateImages`, `duplicateGroups`) olvasáskor kiszűrik és a lomtárból/eltűnt fájlok sorait törlik az indexből, így egy riasztás vagy duplikátum-lista SOSEM mutat kuka-beli vagy már nem létező fájlra (a stale index olvasáskor öngyógyul).
- **App-storage lista azonnal frissül uninstall után** (feature/apps): az `AppStorageScreen` most minden RESUME-kor újralekérdez (az első resume-ot az init load már fedi, azt kihagyja), így a rendszer uninstall-párbeszédből visszatérve a most eltávolított app rögtön eltűnik a listából (a lekérdezés csak a jelenleg telepített csomagokat sorolja).
### Changed
- `app/build.gradle.kts`: `versionName` → 0.37.0.
### Verification
- `DuplicateDetectorImageTest` (Robolectric NATIVE graphics): egy METAADAT-ONLY (ujjlenyomat nélküli) eredeti ellen egy más formátumú+felbontású másolat SIMILAR riasztást vált ki, és az eredeti utána ujjlenyomatolt; egy fordított (más) kép NEM ad riasztást. `DedupResultPruningTest`: kuka-beli és eltűnt duplikátum nem jelenik meg és törlődik az indexből, míg egy élő duplikátum továbbra is megjelenik.
### Regression guard
- A meglévő `DuplicateDetectorTest` (EXACT), `PerceptualHashSourceTest`, reconciler/fusion tesztek változatlanul futnak; a dedup pontszám/tier/explainability réteg érintetlen.

## [jupiter:0.38.0] - 2026-07-08
### Added
- **Élő szöveg/kód near-duplikátum észlelés** (data/index): a `StructuralFingerprintSource` egy formázás-független `TextSimHash`-t számol `FileType.CODE` fájlokra, és a `DuplicateDetector` érkezéskor összeveti a könyvtárral (Hamming ≤ 3 / 64) — így egy újraformázott, újra-behúzott vagy kicsit szerkesztett másolat is "hasonló" riasztást ad, holott a content-hash szétesett. Igény szerinti (on-demand) backfill mint a képeknél: az első kód-fájl érkezés ujjlenyomatolja a meglévő könyvtárat, utána ingyen.
- **Élő archívum azonos-tartalom észlelés** (data/index): ZIP-családú archívumokra (`FileType.ARCHIVE`/`APK`) a member-tree ujjlenyomat (rendezett `(név,méret,crc)` halmaz FNV-1a-ja); két azonos tartalmú, de más tömörítéssel újracsomagolt archívum (más bájtok → más content-hash) most "azonos tartalom" riasztást ad. A nem-ZIP konténerek (tar/gz/7z/rar) konzervatívan UNHASHABLE (sosem egyeznek).
- Új `FileIndexEntry.structuralHash` oszlop (DB v3→v4, destruktív fallback — az index cache) + DAO/repository lekérdezések (`structuralHashesOfTypes`, `filesMissingStructuralHash`, `putStructuralHash`, `findNearDuplicateText`, `findSameArchiveContents`), a meglévő létezés-szűréssel (kuka/eltűnt fájl sosem jelenik meg). A `structuralHash` a `perceptualHash`-hoz hasonlóan megmarad a survey-k között, ha az identitás (méret+mtime) változatlan.
### Changed
- A SIMILAR értesítés szövege most az alert saját (típus-specifikus) magyarázatát használja, így kép/szöveg/archívum near-dup egyaránt helyesen olvasható ("Similar file detected").
- `docs/DEDUP_ARCHITECTURE.md`: a szöveg/kód (SimHash) és archívum (member-tree) rétegek "shipped/live"-ra jelölve (§1, §4.6, §5.2, §7.1, §9, §20); a hátralévő extraktorok (videó keyframe, PDF render, audió chromaprint, embeddingek) explicit backlog, mert eszköz-szintű média-dekódolást igényelnek.
- `app/build.gradle.kts`: `versionName` → 0.38.0.
### Verification
- `StructuralFingerprintSourceTest` (tiszta JVM): újraformázott kód near, független kód far, bináris tartalom EMPTY; azonos tagok más tömörítéssel egyenlő fingerprint, más tagok különböznek, nem-ZIP UNHASHABLE. `DuplicateDetectorStructuralTest` (Robolectric + Room): metaadat-only (ujjlenyomat nélküli) kód-eredeti ellen újraformázott másolat → SIMILAR; újracsomagolt archívum → SIMILAR; független kód/archívum → nincs riasztás.
### Known issues / backlog
- Videó/PDF/audió near-dup extraktorok és on-device embeddingek továbbra sem szállítottak — ezek eszköz-szintű média/ML-dekódolást igényelnek, amit az emulátor nélküli CI nem tud őszintén igazolni; nem állítjuk késznek. A fusion scorer + tierek + magyarázat már készen fogadja őket.

## [jupiter:0.39.0] - 2026-07-08
### Added
- **Videó near-duplikátum** (data/index): `MediaMetadataRetriever`-rel a videó közepéhez közeli reprezentatív keyframe → dHash (`BitmapDHash`), a `structuralHash` oszlopban tárolva, VIDEO típuson belül Hamming ≤ 10 összevetéssel. Egy újrakódolt/újratömörített másolat ugyanarról a felvételről "hasonló" riasztást ad.
- **PDF near-duplikátum** (data/index): `PdfRenderer`-rel az első oldal renderelése (fehér háttér, arány-tartó, ≤320px) → dHash, PDF típuson belül Hamming ≤ 8. Ugyanaz a dokumentum újra-exportálva/szkennelve más felbontáson egyezik.
- **Audió near-duplikátum** (data/index): `MediaExtractor` + `MediaCodec` PCM-dekódolás (≤12 s), 64 ablakos hangosság-burkológörbe → 64-bites ujjlenyomat (relatív energia → hangerő-független), AUDIO típuson belül Hamming ≤ 10. Egy újrakódolt felvétel egyezik.
- **`MediaFingerprintSource` seam** + `AndroidMediaFingerprintSource` (valós dekóderek) + Hilt `@Binds`. A dekódolás csak eszközön fut (valós kodekek kellenek); a pipeline-huzalozás fake-kel CI-ben bizonyított.
- Közös `BitmapDHash` (bitmap → dHash) a kép/videó/PDF perceptuális ujjlenyomathoz — a `PerceptualHashSource` most ezt használja (DRY, egy helyen validált).
### Changed
- `DuplicateDetector`: VIDEO/PDF/AUDIO ágak az exact + kép + szöveg/archívum után, közös `mediaNearCheck` helperrel; az on-demand `ensureStructuralFingerprinted` most a média típusokat is ujjlenyomatolja. A `structuralHash` backfill-hatóköre kibővült VIDEO/PDF/AUDIO-ra.
- `docs/DEDUP_ARCHITECTURE.md`: a videó/PDF/audió rétegek "shipped/live (device-verified)"-re jelölve; egyetlen hátralévő extraktor az on-device embedding.
- `app/build.gradle.kts`: `versionName` → 0.39.0.
### Verification
- `DuplicateDetectorMediaTest` (Robolectric + Room, scriptelt `FakeMediaFingerprintSource`): metaadat-only (ujjlenyomat nélküli) videó/PDF/audió-eredeti + küszöbön belüli másolat → SIMILAR; küszöbön kívüli → nincs; két dekódolhatatlan (UNHASHABLE) fájl sosem egyezik. `StructuralFingerprintSourceTest`, `DuplicateDetectorStructuralTest`, `PerceptualHashSourceTest` (refaktor után is) változatlanul zöld.
### Known issues / backlog
- A valós média-dekódolás (`MediaMetadataRetriever`/`PdfRenderer`/`MediaCodec`) helyessége csak eszközön igazolható — az emulátor nélküli CI-ben nincs valós kodek, ezért ott a pipeline-t fake-kel bizonyítjuk, a dekódert device-on teszteld. A videó jelenleg egyetlen reprezentatív keyframe-et használ (temporális több-frame ujjlenyomat későbbi finomítás). On-device szemantikus embeddingek továbbra is backlog.

## [jupiter:0.40.0] - 2026-07-08
### Fixed
- **Duplicate cleanup: azonnali újranyitás fehér várakozás nélkül** (feature/cleanup): új `DuplicateScanCache` (@Singleton) őrzi az utolsó lefutott elemzés eredményét a folyamat élettartamára; a `DuplicatesViewModel` újranyitáskor EGYBŐL megjeleníti a mentett állapotot (üres eredmény = "nincs duplikátum" is), majd a háttérben CSENDESEN újraszkennel (`scan(silent=true)`) és a kész friss eredményt becseréli — így nincs többé sok-másodperces üres képernyő. (Teljes process-kill után a következő hidegindítás egyszer újraszkennel és újratölti a cache-t.)
- **✓✓ gomb most kapcsoló: összes kijelölése ↔ kijelölés törlése** (feature/cleanup): ha nincs kijelölés, mindent kijelöl (a legjobb példány kivételével); ha van kijelölés, törli az egészet (a `RemoveDone` ikon jelzi az állapotot). Eddig csak kijelölni tudott.
- **Delete gomb nem csúszik a navigációs sáv mögé** (feature/cleanup): a törlő sáv (`DeleteBar`) most `navigationBarsPadding()`-et alkalmaz, így a Delete gomb minden eszközön a rendszer navigációs sávja FÖLÖTT jelenik meg és kattintható marad (gesztus-pill és 3-gombos sáv esetén is).
### Changed
- `DuplicatesViewModel.scan(silent)`: nem-csendes szkennkor a "scanning" állapot szinkron beáll (nincs villanó "nincs duplikátum" üres állapot indulás előtt); a törlés utáni lista is elmentődik a cache-be, hogy újranyitáskor a friss (törlés utáni) állapot jöjjön.
- `app/build.gradle.kts`: `versionName` → 0.40.0.
### Known issues
- A cache a folyamat élettartamára szól (nem lemezre perzisztált) — teljes app-kill után az első megnyitás újraszkennel; ez tudatos, alacsony kockázatú kompromisszum, a képernyőn-belüli/navigációs újranyitást (a bejelentett esetet) teljesen lefedi.

## [jupiter:0.41.0] - 2026-07-08
### Fixed
- **Törlés után NEM kerül random rossz kép a helyére** (feature/cleanup): a duplikátum-sorok mostantól stabil `key(file.path)`-al renderelődnek, így a Compose sosem használja újra egy sor (és a benne lévő Coil `AsyncImage`) node-ját EGY MÁSIK fájlhoz, amikor a lista törlés után átrendeződik. Eddig az újrahasznált `AsyncImage` a korábbi fájl gyorsítótárazott bélyegképét mutatta, ezért úgy tűnt, mintha a törölt kép helyére egy oda nem illő ("random") kép került volna. Ráadásul a Coil `memoryCacheKey`/`diskCacheKey` most fájl-útvonal-alapú (öv+nadrágtartó).
- **Újranyitás app-kill után is azonnali (lemezre perzisztált cache)** (feature/cleanup): a `DuplicateScanCache` mostantól egy kis lemez-pillanatképet (`filesDir/duplicate_scan_snapshot.tsv`, atomikus temp+rename írás) is ír az utolsó elemzésről, nem csak memóriában tartja. Így teljes folyamat-leállítás (az app bezárása) után is EGYBŐL a mentett listával nyílik a képernyő, majd a háttérben csendben frissül — nincs többé sok-másodperces fehér képernyő a hidegindításnál sem. A ViewModel a betöltést a háttérszálon végzi (a lemezolvasás pár ms), a betöltés alatt loading-állapot van, nem villan fel a "nincs duplikátum".
### Changed
- `app/build.gradle.kts`: `versionName` → 0.41.0.
### Notes
- Csak a csoportlista perzisztálódik (elég a megjelenítéshez és a törléshez); a média-minőség címkék hidegindításkor újra-probolódnak. Elavult pillanatkép nem okoz kárt: a csendes újraszkennelés korrigálja, és törléskor a fájl létezését a Lomtárba-helyezés újraellenőrzi (ha nincs meg, a forrás érintetlen marad, hibaként jelezve).

## [jupiter:0.42.0] - 2026-07-10
### Fixed
- **Törölt duplikátum-csoportok NEM jelennek meg újra a listában** (feature/cleanup): a valódi ok egy versenyhelyzet volt — a képernyő megnyitásakor futó CSENDES háttér-újraszkennelés ("Still scanning…" a képernyőn) a befejezésekor `groups = eredmény`-t állított be, FELÜLÍRVA a törlés utáni listát azokkal a csoportokkal, amiket a szkennelés még a törlés ELŐTT gyűjtött be. Így a törölt szekciók visszakerültek. Mostantól a ViewModel egy `deletedPaths` halmazban jegyzi a session során törölt útvonalakat, és MINDEN helyen (szkennelés közbeni streamelés, szkennelés befejezése, törlés) kiszűri őket egy közös `publishGroups` funnelen keresztül — így egy futó szkennelés befejezése SOSEM tudja feltámasztani a felhasználó által épp törölt csoportokat. A `deletedPaths` egy felhasználó által indított friss szkennelésnél (Refresh) nullázódik, mert az index onnantól már nem tartalmazza a törölt fájlokat.
### Changed
- `app/build.gradle.kts`: `versionName` → 0.42.0.
### Notes
- A törlés maga eddig is működött (a fájlok a Lomtárba kerültek), a hiba a lista-állapot felülírása volt egy párhuzamos szkennelés által. A javítás után a törölt csoportok azonnal eltűnnek és eltűnve is maradnak, függetlenül attól, hogy épp fut-e háttér-szkennelés.

## [jupiter:0.43.0] - 2026-07-10
### Fixed
- **A Samsung `Android/.Trash/…` (és más kuka/rendszer) fájlok többé nem jelennek meg SEHOL, így nem nyílnak "Not found"-dal** (core/data): a v0.37.0-ban a `.trash` kizárás CSAK az index-forrásokba került be, de a fájlokat több MÁS enumerátor is listázta kizárás nélkül — a **kategória-böngésző** (`MediaStoreCategorySource`: APK-k/Letöltések/Dokumentumok/…), az **albumok** (`AlbumsSource`), a **böngészés-idejű önjavító index** (`FileRepositoryImpl`), és a **tárhely-analitika** (`StorageAnalyticsRepositoryImpl` — ebből hiányzott a `.trash`). Ezek listázták a Samsung MyFiles kukában lévő `.apk`/kép fájlokat, amik megnyitáskor "Not found"-ot adtak.
- **Egyetlen közös kizárási szabály** (`core/util/StorageExclusions`): minden fájl-felsoroló határon EZT használja (MediaStore kategória/album lekérdezés, index-survey, fájlrendszer-bejárás, analitika, index-olvasás). Teljes, kis-nagybetű-független útvonal-SZEGMENS egyezés (`android/data`, `android/obb`, `.thumbnails`, `.trashed`, `.trash`), így egy fájl, aminek csak a NEVÉBEN szerepel a token (`my.trash.notes`), nem záródik ki tévedésből; a content:// URI-k (scoped storage) nyithatók maradnak.
- **A már indexelt, elavult kuka-sorok azonnal eltűnnek** a keresésből és a böngészésből is (olvasás-idejű szűrés a `search()`/`observeChildren()`-ben), nem kell megvárni a következő teljes survey-t.
### Changed
- `MediaStoreIndexSource`, `IndexingWorker`, `StorageAnalyticsRepositoryImpl`, `FileRepositoryImpl` a saját duplikált kizárási listáikat a közös `StorageExclusions`-re cserélték (egy igazságforrás).
- `app/build.gradle.kts`: `versionName` → 0.43.0.
### Verification
- `StorageExclusionsTest` (tiszta JVM): a valós Samsung `Android/.Trash/com.sec.android.app.myfiles/…/app-debug.apk` útvonal (kis-nagybetűtől függetlenül), valamint `android/data`, `android/obb`, `.thumbnails`, `.trashed` szegmensek KIZÁRVA; szokásos fájlok, a nevükben tokent tartalmazó mappák (`my.trash.notes`), és a content:// URI-k NEM.

## [jupiter:0.44.0] - 2026-07-10
### Fixed
- **App storage: az engedély megadása után KILÉPÉS/BELÉPÉS nélkül azonnal indul a scan** (feature/apps): a Usage-access megadása után visszatérve az `AppOpsManager` állapota egy pillanatot késhet, ezért a képernyő a "Grant Usage access" promptnál maradt, amíg a felhasználó ki-be nem lépett. A ViewModel `onResume()`-ja most ~3 másodpercig POLLOZZA a hozzáférést (250 ms-onként), és amint megjelenik, magától betölt és elindítja a scannelést — re-entry nélkül.
- **Cleanup → Large files: a fájlok megnyitása nem fut "Not found"-ra** (data/index): a Large-files lista (index-alapú `largeFiles()` és az `allFiles()`) mostantól kiszűri a kuka/thumbnail (Samsung `Android/.Trash/…`) sorokat (és törli azokat az indexből), így nem lehet rájuk kattintva "Not found"-ot kapni.
### Changed
- **Duplikátumok képernyő letisztítva** (feature/cleanup): a felesleges **"Keep best"** gomb (és a hozzá tartozó ✨ AI-jellegű ikon) törölve — a ✓✓ kapcsoló (összes kijelölése ↔ kijelölés törlése) már úgyis lefedi. Helyette **méret-szűrő** chip-sor: `All sizes / ≥100 KB / ≥1 MB / ≥10 MB / ≥100 MB` — egy csoport akkor látszik, ha legalább az egyik példánya eléri a méretet, így a sok pár-kilobájtos kép elrejthető és a több-megás/gigás duplikátumokra lehet fókuszálni.
- **AI-jelölő ikonok és az "Explain with AI" eltávolítva a takarítás-felületekről** (feature/cleanup): a Cleanup hubból az "Explain with AI" gomb + AI-magyarázó kártya, valamint a Duplikátumok és az index-státusz kártya ✨ (AutoAwesome) ikonjai törölve.
- `app/build.gradle.kts`: `versionName` → 0.44.0.
### Verification
- `DuplicatesUiStateSizeFilterTest` (tiszta JVM): a méret-szűrő a legnagyobb példány alapján szűri a csoportokat (All → mind; ≥1 MB elrejti az 50 KB-os csoportot; ≥100 MB csak a 200 MB-os példányt tartalmazó csoportot hagyja). `StorageExclusionsTest` változatlanul zöld (a Large-files szűrés ugyanazt a kizárást használja).
### Notes
- Az App-storage poll viselkedése (AppOps grant-késleltetés) valós eszközön igazolható; a méret-szűrő és a listatisztítás Compose-UI, amit szintén eszközön látsz.

## [jupiter:0.45.0] - 2026-07-11
### Fixed
- **A Samsung `Android/.Trash/…` fájlok TÖBBÉ a Recent fülön és a keresésben SEM jelennek meg → nincs "Not found"** (data/file): az utolsó enumerátor, ami kihagyta a `StorageExclusions` szűrést, a fájlrendszer-bejáró **keresés** volt (`FileRepositoryImpl.search()` → `walkTopDown`). Ez táplálja a **Recent fület** (`RecentViewModel` a legutóbb módosított fájlokat listázza) ÉS a globális keresés bejárós ágát. A frissen kukába helyezett `.apk`-nak friss a módosítási ideje, ezért a Recent lista TETEJÉRE került, és rákattintva "Not found"-dal nyílt a Preview. A `search()` mostantól kihagy minden kizárt-szegmensű útvonalat (`Android/.Trash`, `.trashed`, `.thumbnails`, `Android/data`, `Android/obb`). A kukafájl NEM rejtett (nincs pont-prefix), ezért csak a szegmens-kizárás tudta eltávolítani — a `showHidden=false` nem.
- **Cleanup → Large files: a live (index nélküli) ág is kizárja a kukát** (data/storage): a `StorageAnalyticsRepositoryImpl.findLargeFiles()` bejárós ága — a testvéreivel (`findDuplicates`, `storageOverview`) ellentétben — nem hívta az `isExcludedPath`-ot; most igen, így ha az index épp nem használható, a Large-files lista akkor sem hoz fel kukafájlt.
### Changed
- **App storage: az engedély után AZONNAL "Scanning…" látszik, és az első ~5 app rögtön megjelenik, majd folyamatosan a többi** (feature/apps + data/apps): a régi `query()` egyben végigmérte mind a ~740 appot, és CSAK a végén adott vissza bármit — közben 9–15 mp-ig a "Grant Usage access" prompt maradt a képernyőn (indokolatlanul lassú és félrevezető). Az új `AppStorageSource.queryStream()` Flow: (1) ha nincs hozzáférés → egy `permissionRequired` keret; (2) amint van hozzáférés, AZONNAL egy üres `scanning=true` keret, amitől a prompt eltűnik és megjelenik a "Scanning app storage…" nézet; (3) az első 5 app után, majd 25-önként egy-egy részleges, méret szerint rendezett pillanatkép — a lista fokozatosan telik; (4) a végén a teljes, rendezett eredmény. A `AppStorageViewModel.load()` a grant-promptot SZINKRON (a scan indulása előtt) kezeli: ha van hozzáférés, egyből "Scanning…", ha nincs, egyből a grant-prompt (nincs félrevezető "Scanning…" felvillanás). Refreshkor/visszatéréskor a meglévő TELJES lista marad a képernyőn a "Scanning…" jelzővel, és csak a kész scan cseréli le atomikusan (nem esik vissza 5 appra és nő vissza).
- `app/build.gradle.kts`: `versionName` → 0.45.0.
### Verification
- **`FileRepositorySearchExclusionTest`** (Robolectric + valós temp fájlrendszer + valós in-memory Room): a `FileRepositoryImpl.search()` egy VALÓS könyvtárfa felett (a bejelentett Samsung `Android/.Trash/com.sec.android.app.myfiles/…/mediadownload (9).apk` szerkezettel) visszaadja a normál `report.apk`-t, de NEM a kukafájlt és NEM a `.thumbnails` fájlt; egyik találat sem esik kizárt szegmensbe.
- **`AppStorageOverviewResolveTest`** (tiszta JVM): a streamelő scan megjelenítési szabálya (`resolveOverview`) — első scan az összes részletet átveszi (lista fokozatosan telik); RE-load (Refresh/resume) közben a meglévő teljes lista MARAD, és csak a kész scan cseréli le atomikusan; a permission-keret mindig felülír.
- `StorageExclusionsTest` változatlanul zöld (a pontos bejelentett kuka-útvonalat is fedi). `AppStorageInfoTest` fedi a méret-szerinti rendezés matekját, amit a streamelő pillanatkép is használ.
- **Merge előtti adverzariális diff-review** (3 lencse → verifikálás) 2 valós UX-regressziót fogott a streamelő változásban — a Refresh/resume-kori lista-összeomlást és az engedély-nélküli első indításkori félrevezető "Scanning…" felvillanást; mindkettő javítva még merge előtt (`resolveOverview` + `load()` szinkron `permissionRequired = !granted`).
### Notes
- A streamelő App-storage scan valós eszközön látható (StorageStatsManager/AppOps device-only); a batch-küszöbök (első 5, majd 25-önként) tisztán a felhasználói észlelhetőséget szolgálják. A nem használt egylövetű `query()` törölve (nincs két, elváló másolat a scan-ciklusból).
### Known issues
- A fájlrendszer-bejáró keresés még belép a kizárt könyvtárakba (majd elemenként kihagyja őket) — a kukafa általában kicsi, a Recent scan pedig 60 találatnál megáll, így a hatás elhanyagolható. A kézi böngészés (`listFiles`) továbbra is megmutatja a mappa TÉNYLEGES tartalmát, ha a felhasználó szándékosan belép egy `.Trash`/`Android` mappába (ez normál fájlkezelő-viselkedés).

## [jupiter:0.46.0] - 2026-07-11
### Added
- **Kategória gyorshozzáférés-rács a Home képernyőn** (feature/home): színes ikoncsempék (Photos / Videos / Audio / Documents / APKs / Archives / Downloads), mindegyiken a kategória teljes MÉRETE; egy csempére koppintva megnyílik az adott kategória fájllistája (`Destination.CategoryBrowse`). A méretek a már betöltött `uiState.categories`-ból jönnek (nincs új számítás), a szín/ikon a tárhely-analitika paletta.
- **Recycle Bin auto-törlés (retenció) beállítás** (feature/settings + data/trash): új "Recycle Bin" szekció a Beállításokban — „Open Recycle Bin" gyorslink + „Auto-delete trashed items" választó (Never / 7 / 15 / 30 / 60 nap; alap: Never = soha). A `TrashPurgeWorker` (@HiltWorker) naponta lefut (és a beállítás módosításakor azonnal is), és véglegesen törli a kukában a beállított napnál régebbi elemeket (`TrashRepository.purgeOlderThan`). A kuka-képernyő minden elemén megjelenik az „Auto-deletes in N days" visszaszámláló, ha a retenció be van kapcsolva. (Maga a Kuka-nézet eddig is létezett: More → Recycle Bin.)
### Changed
- **App storage scan: valódi progress-sáv + AZONNALI eredmény kézi érintés nélkül + gyorsabb** (feature/apps + data/apps): a scan mostantól **párhuzamos** (`SCAN_CONCURRENCY = 8` egyidejű `queryStatsForPackage` binder-hívás), ami a ~700 appos mérést több-szörösére gyorsítja; a `queryStream()` `channelFlow`-vá alakult, ami folyamatos, **determinisztikus haladást** ad (`scannedCount` / `totalCount`), így a képernyőn egy valódi `LinearProgressIndicator` + „X / N apps (P%)" megy végig, és a lista magától frissül (a sűrű, egyenletes állapotfrissítések miatt nem kell a képernyőhöz érni, hogy megjelenjen a kész eredmény). Az app-ikonok betöltése a fő szálról háttérszálra került (`produceState` + `Dispatchers.IO`), így a lista első megjelenése nem akad meg.
- `app/build.gradle.kts`: `versionName` → 0.46.0.
### Verification
- **`TrashPurgeTest`** (Robolectric + valós in-memory Room + valós temp fájlok): a `purgeOlderThan(cutoff)` a cutoffnál RÉGEBBI elemeket (sor + lemez-tartalom) véglegesen törli, a cutoff-ot elérőket/újabbakat MEGTARTJA (szigorúan-korábbi határ), és a már eltűnt tartalmú „szellem" sort is kitakarítja; ha minden újabb, semmit nem töröl.
- **`AppStorageOverviewResolveTest`** változatlanul zöld (a streamelő megjelenítési szabály). Az új progress-mezők (`scannedCount`/`totalCount`/`progress`) tiszta adatszámítások.
- A párhuzamos scan és a Compose-progress valós eszközön látható (StorageStatsManager/AppOps device-only); a kategória-rács Compose-UI, szintén eszközön nézhető.
### Notes
- Az auto-törlés alapból KI van kapcsolva (Never) — explicit opt-in nélkül semmi nem törlődik magától, összhangban a „NO DATA LOSS" elvvel. A napi worker akkuval-nem-alacsony feltétellel fut, és no-op, amíg a beállítás Never.

## [jupiter:0.47.0] - 2026-07-11
### Fixed
- **Home „Categories" csempék: az APKs és Archives többé nem 0 bájt** (feature/home + data/media): a Home kategória-méretek eddig a tárhely-analitika EGY-KATEGÓRIÁS besorolásából jöttek, ami a Downloads-útvonal heurisztikát ELŐBB alkalmazta — így minden letöltött `.apk`/`.zip` a DOWNLOADS vödörbe került, az APKs és Archives csempe pedig 0 B-t mutatott, HIÁBA listázott a böngésző 48 APK-t (1,7 GB) és 31 archívumot (236 MB). A csempék mostantól a böngészővel AZONOS, típus-alapú `MediaStoreCategorySource`-ból számolják a méretet/darabszámot (új `summarize(category)` egy sovány projekció-lekérdezéssel, kizárva a kuka-fájlokat), így a csempe pontosan azt mutatja, amit megnyitva látsz. (Egy Download-mappában lévő `.apk` mostantól — helyesen — az APKs ÉS a Downloads kategóriában is számít, ahogy a böngészőben is.) Mellékhatásként a Home betöltése is gyorsabb (nincs teljes fájlrendszer-bejárás).
- **A perceptuális (kép-összehasonlító) duplikátumkereső VÉGRE megjelenik a Duplicate cleanup listában** (data/index + feature/cleanup): a nagyon jó dHash-alapú near-duplicate KÉP detektor eddig KIZÁRÓLAG az érkezés-értesítési ághoz volt bekötve (`DuplicateDetector.onFileArrived` → rendszerértesítés + egy Flow, amit SEMMILYEN képernyő nem figyelt) — a takarítás-lista viszont csak PONTOS (SHA-1 bájtazonos) csoportokat mutatott, ezért az átméretezett/újrakódolt (bájtban eltérő, de vizuálisan azonos) fotó-duplikátumok SEHOL nem látszottak. Új `FileIndexRepository.nearDuplicateImageGroups(threshold)`: a már ujjlenyomatozott képeket dHash Hamming-távolság szerint klaszterezi (union-find; 1. menet pontos-hash O(n), 2. menet a különböző hashek közti közeli összevonás, korláttal a nagy könyvtárakra), és a takarítás-szkennelés befejeztével ezeket a „Similar photos" csoportokat HOZZÁADJA a listához (a pontos csoportokban már szereplő képeket kiszűrve, hogy semmi ne duplázódjon). A képernyő minden szkennkor továbbpörgeti a perceptuális-hash feltöltő workert, hogy egyre több kép legyen lefedve.
### Changed
- `DuplicateGroup` új `similar: Boolean` mezővel (vizuális near-dup csoport jelölése; a kártya „N similar photos" feliratot ír „N copies" helyett, a legnagyobb példány elöl → „keep best"/„extra kijelölése" a legjobb felbontásút tartja meg).
- `app/build.gradle.kts`: `versionName` → 0.47.0.
### Verification
- **`NearDuplicateImageGroupTest`** (Robolectric + valós in-memory Room + valós temp fájlok): a `nearDuplicateImageGroups(8)` az azonos ÉS a 2-bit-eltérésű (küszöbön belüli) dHash-eket EGY klaszterbe vonja, egy külön azonos-hash párt külön csoportba, a >8-bit-távolságú hasheket pedig magányosként KIHAGYJA; a csoportok `similar=true`-k és legnagyobb-példány-elöl rendezettek; near-szomszéd nélkül üres.
- `AppStorageOverviewResolveTest`, `TrashPurgeTest`, `StorageExclusionsTest`, `FileRepositorySearchExclusionTest` változatlanul zöldek.
### Notes
- A near-image klaszterezés 2. menete (különböző hashek közti közeli összevonás) egy ~50M összehasonlításos korláttal van bekötve; egy nagyon nagy, teljesen egyedi képkönyvtárnál e fölött csak a pontos-dHash csoportosítás fut (a re-mentett/átméretezett fotók dHash-e jellemzően azonos, így ezt is elkapja). A megjelenő near-image csoportok köre a perceptuális-hash feltöltő előrehaladtával bővül; a takarítás-képernyő minden szkennkor továbbpörgeti azt.
- A tárhely-analitika (pie) továbbra is egy-kategóriás (bájtonként egy vödör) marad — ez a Home csempéktől szándékosan eltérő nézet; a bejelentett hiba a Home csempéket érintette, az javítva.

## [jupiter:0.48.0] - 2026-07-11
### Fixed
- **A képduplikátum-kereső VÉGRE megtalálja a galéria több száz vizuális duplikátumát** (data/index + feature/cleanup): a v0.47-ben bekötött perceptuális (dHash) képcsoportosítás önmagában helyes volt, de a valódi ok a **lefedettség** volt — a near-dup csoportok CSAK ujjlenyomatozott képeket látnak, a meglévő könyvtárat pedig egyedül a `PerceptualHashBackfillWorker` ujjlenyomatozta, ami **futásonként 2000 képre volt korlátozva + exponenciális WorkManager retry-backoff**-fal, így egy ~40 000 képes galéria lefedése ~20 óra-távolságú futást igényelt volt. A takarítás-képernyő pedig AZONNAL, várakozás nélkül kérdezte le a near-dup csoportokat → a gyakorlatilag üres ujjlenyomat-halmaz miatt **0 képcsoport**. Három javítás:
  1. **A backfill gyorsan, teljesen lefut** (`PerceptualHashBackfillWorker`): egy futásban lemeríti a teljes hiányzó-ujjlenyomat listát (nagy, ~20 000-es futásonkénti kerettel a 2000 helyett), és nagy hátralék esetén **előtér-szolgáltatásként** fut (mint az index-survey), így a Doze/háttér-korlátok nem fojtják meg — egy 40k-s galéria így 1–2 futásban, percek alatt lefedve, nem napok alatt.
  2. **Skálázható near-összevonás** (`FileIndexRepository.nearDuplicateImageGroups`): a korábbi O(d²) páronkénti (nagy könyvtárnál kihagyott) menetet **LSH sávozás** váltja (8 db 1-bájtos sáv), így a néhány-bites eltérésű újrakódolt/átméretezett másolatok is összeállnak 40k+ képnél is — nem csak a bitre azonos dHash-ek.
  3. **A takarítás-lista magától frissül** (`DuplicatesViewModel`): a szkennelés után is figyeli a hátralévő ujjlenyomatozást, és ~8 mp-enként újrakérdezi a képcsoportokat, amíg a teljes galéria elemzése be nem fejeződik — kézi újraszkennelés nélkül jönnek elő a fotó-duplikátumok. A képernyő „Analyzing your photos…" jelzést mutat, amíg tart.
### Changed
- Új `FileIndexRepository.imagesNeedingPerceptualHashCount()` (elemzési előrehaladás/hátralék), `DuplicatesUiState.analyzingPhotos` mező + „Analyzing photos" nézet/sáv a Duplicate cleanup képernyőn.
- `app/build.gradle.kts`: `versionName` → 0.48.0.
### Verification
- **`NearDuplicateImageGroupTest`** kibővítve: az azonos + 2-bit-eltérésű dHash-ek egy klaszterbe kerülnek, a külön azonos pár külön, a >8-bit-távolságúak magányosak; ÉS egy új eset bizonyítja, hogy az **LSH sávozás elkapja a bájt-határon átnyúló (byte0/byte3/byte7) 3-bites eltérésű** near-párt is, a távoli képet pedig nem.
- `AppStorageOverviewResolveTest`, `TrashPurgeTest`, `StorageExclusionsTest`, `FileRepositorySearchExclusionTest` változatlanul zöldek.
### Notes
- A fotó-elemzés (dekódolás + dHash) valós eszközön fut; a WorkManager előtér-viselkedés device-only. Az elemzés egyszeri költség — a lefutás után az ujjlenyomatok megmaradnak (az azonosság — méret+mtime — változatlanságáig), így a következő megnyitáskor a képcsoportok azonnal ott vannak.
- Az EGYEZŐ bájtú (SHA-1) képduplikátumok eddig is megjelentek, ha az index tartalmazta őket; a most javított eset a VIZUÁLISAN azonos, de bájtban eltérő (újrakódolt/átméretezett) fotók — ezekre való a perceptuális dHash.

## [jupiter:0.49.0] - 2026-07-11
### Fixed
- **Fájl-identitás normalizálás — a hashek többé nem törlődnek mtime-kerekítés miatt (valódi, auditált hiba)** (data/index): a MediaStore egész MÁSODPERCES mtime-ot ad (`DATE_MODIFIED`×1000), a fájlrendszer-stat nyers ezredmásodpercest — ugyanaz az ÉRINTETLEN fájl két „különböző" mtime-mal érkezett attól függően, melyik enumerátor indexelte. A pontos egyenlőség ezt módosításnak vette, és minden újraindexeléskor NÉMÁN TÖRÖLTE a kiszámolt content/perceptuális/strukturális ujjlenyomatokat — teljes könyvtárnyi újra-hashelést/újra-dekódolást kényszerítve. Minden identitás-összehasonlítás (upsert-megőrzés, `hashIfUnchanged` SQL, `hashForEntry`, mozgatás/átnevezés) mostantól MÁSODPERC-pontosságú.
- **Egyetlen autoritatív rescan** (feature/cleanup): a `CleanupViewModel.rescan()` eddig PÁRHUZAMOSAN indította az index-újraépítő workert ÉS egy második, teljes élő fájlrendszer-bejárást — kétszer ugyanaz a munka, egymásnak ellentmondható eredményekkel. Mostantól egy csővezeték: újraépítés → a worker befejezésének megvárása → kiszolgálás a friss indexből.
- **Indexelés kikapcsolása most már TELJES leállítás** (feature/settings + JupiterApp): a MediaStore élő figyelő (`DownloadIndexObserver`) eddig kikapcsolás után is regisztrálva maradt; most a kikapcsolás leállítja, a bekapcsolás újraindítja, és az app-indítás is csak bekapcsolt indexelésnél regisztrálja.
- A halott, sorszám-alapú `isPopulated()` eltávolítva az interfészről — a teljesség KIZÁRÓLAG az `IndexStateRepository` állapotgépe (COMPLETE/isUsable), soha nem sorszám.
### Added
- **Rétegzett perceptuális kép-ujjlenyomat: dHash + pHash (DCT) + aHash, kombinált súlyozott pontozással** (data/index): soha nem dönt egyetlen hash — a near-merge jelöltjeit a `w1·dHash + w2·pHash + w3·aHash` súlyozott Hamming-pontszám erősíti meg (szigorú=5.0 / megengedő=10.0 küszöbtier), a régi (csak-dHash) sorokra automatikus dHash-fallbackkel (nincs regresszió). Egy dekódolás → mindhárom réteg (`PerceptualHashSource.computeAll`, `BitmapPerceptual`); a feltöltő worker a régi sorokat is felfejleszti a teljes stackre.
- **Kaszkád exact-dedup: MÉRET → GYORS fej+farok hash → ERŐS teljes hash** (data/index): az index-alapú `duplicateGroups` az azonos méretű jelölteket előbb egy olcsó, perzisztált fej+farok (64 KiB + 64 KiB) `quickHash`-sel szűri; a drága teljes-tartalom hash csak quickHash-ütközésnél fut. A csoportosítást MINDIG a teljes hash mondja ki (a quick hash csak előszűrő).
- **Valódi, adatmegőrző Room-migráció (v4→v5) + forró lekérdezési indexek** (data/index + di): `quickHash`/`phash`/`ahash` oszlopok ALTER TABLE-lel, és indexek a `sizeBytes`/`contentHash`/`perceptualHash`/`lastSeenGeneration`/`typeName` oszlopokra — a készüléken már kiszámolt (39k képes) ujjlenyomat-készlet a frissítést TÚLÉLI (nincs destruktív wipe ezen az ugráson), és a dedup/nagy-fájl/sweep lekérdezések nem táblapásztáznak.
### Verification
- **`PerceptualStackTest`** (tiszta JVM): aHash/pHash determinizmus; kis perturbáció → kis távolság; eltérő struktúra → nagy; a kombinált pontszám súlyképlete + tierek; azonos dHash + távoli pHash/aHash → NEM hasonló (egy hash sosem dönt); legacy-fallback; UNHASHABLE sosem egyezik.
- **`IdentityPrecisionTest`** (Robolectric + valós Room): másodpercen belüli mtime-eltérés MEGŐRZI mind a négy hash-t (és a generation újra-pecsételődik); valódi (≥1 s) módosítás törli őket; `hashIfUnchanged` precízió-független.
- **`QuickHashCascadeTest`** (Robolectric + valós fájlok + valós Room): bájtazonos fájlok csoportosulnak; azonos méretű, csak KÖZÉPEN eltérő fájlok (ütköző quick hash!) szétválnak a teljes hash-en — bizonyítva, hogy a quick hash előszűrő, nem döntő; a quickHash perzisztálódik.
- Meglévő tesztek (NearDuplicateImageGroupTest, DuplicateDetector*, IndexStateMachineTest, …) változatlanul zöldek — a dHash-fallback garantálja, hogy a stack bevezetése nem regresszió.
### Notes
- A 15 pontos spec ellen teljes auditált állapottábla a versioning fájlban: mi volt már kész korábbról (index_state Room-tábla, generation-sweep, MediaStore+reconciliation survey, exact/perceptual szétválasztás), mit javít ez a kör (identitás, rescan, enable/disable, stack, quick hash, migráció+indexek), és mi marad őszintén hátra (multi-volume, resume-checkpoint, delete-delta-sync, IndexMutationCoordinator, videó-szekvencia/audio-chroma, bájt-verifikáció törlés előtt).
- `app/build.gradle.kts`: `versionName` → 0.49.0.

## [jupiter:0.50.0] - 2026-07-12
### Added
- **Egységes, adatvezérelt sötét–türkiz design rendszer** (ui/theme + ui/components + fő képernyők): a mellékelt képernyőtervek vizuális nyelve alapján közös kártya-, ikonjelvény-, pill-, tárhelygyűrű- és lebegő navigációs komponensek készültek. A Home, fájlböngésző, kategóriák/fotók, duplikátumok, app-tárhely, Kuka és Settings ezeket valódi eszközadatokkal használja; a terméknév továbbra is **Jupiter** maradt.
- **Használható keresési előzmények és scope-szűrők** (feature/search + data/preferences): az utolsó 8 elküldött keresés csak helyben, deduplikálva tárolódik; All / Files / Folders / PDFs / Images / AI search chip-ek ugyanúgy szűrnek indexelt és élő találatot. Fájlútvonal, találati kivonat vagy AI-válasz nem kerül az előzményekbe.
- **Valós fotó-kategória finomítások** (feature/categories): Camera / Screenshots / Downloads útvonal-alapú szűrők, `lastModified` szerinti dátumcsoportos négyrácsos galéria és stale-query védelem. Nem került hamis „Similar” galéria-szűrő a felületre.
- **Biztonságos Vault-import** (feature/vault): a korábbi nem működő Add helyett Android Storage Access Framework dokumentumválasztó nyílik; a kiválasztott `content://` adatfolyam titkosítva kerül a Vaultba, az eredeti forrás megmarad.
- **Kuka és App storage jobb vezérlése**: Restore all összesítő, egyedi végleges törlés megerősítése, valamint valódi app-lista-szűrők (All / Largest / Cache-heavy) és mért app-tárhelyet mutató állapotkártyák.

### Fixed
- **Copy / Move célütközés immár nem írhat felül adatot** (data/file): a teljes átviteli terv az első írás előtt elutasít minden meglévő vagy egymással ütköző célt; az egyedi írás atomikus `CREATE_NEW`, ezért versenyhelyzetben sem csonkíthat egy korábbi fájlt. Sikertelen/cancelled művelet csak az általa létrehozott részleges célt takarítja el, Move esetén a forrás megmarad.
- **Samsung `.Trash` kizárás platformfüggetlen** (core/util + data/index): a közös `StorageExclusions` már Windows/Robolectric `\\` útvonalakat is normalizál, és a deduplikáció is ezt az egy policy-t használja. Így a keresés és a dedup nem ajánl fel Kuka- vagy thumbnail-fájlt megnyitásra.
- **Navigációs és műveleti zsákutcák**: Search/Home mappák a böngészőbe nyílnak; a Home tényleges fájlra a típushoz tartozó nézőt indítja; a fájlsor hárompontos menüje működik; a Duplicate cleanup kiválasztása a minőségi „keep best” sorrendet tartja meg, és a megerősítés helyesen Kuka-mozgatást jelez.
- **Settings útvonalak és őszinte beállítások**: a meglévő Storage analysis / Transfer center / Vault / Cloud hub / Automation sorok tényleges route-ot kapnak; az inert Cache sort eltávolítottuk, nem tettünk mögöttes funkció nélküli App language, PIN vagy auto-lock kapcsolót a képernyőre.

### Changed
- Az első indítás alapértelmezése a márkázott sötét téma, a dinamikus Material You szín pedig explicit opt-in; a korábbi felhasználói beállítások továbbra is megmaradnak.
- A README a valós működést és a külső/eszözfüggő korlátokat írja le; a félrevezető NEXUS, Smart Merge és „minden UI-complete” állítások kikerültek.
- `app/build.gradle.kts`: `versionCode` → 2, `versionName` → 0.50.0.

### Verification
- `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest` — **BUILD SUCCESSFUL**; a v0.50.0 debug APK elkészült, a teljes JVM tesztcsomag **274 teszt / 0 hiba / 0 failure** eredménnyel zöld.
- A tesztkör először két valós `.Trash` kizárási hibát talált Windows-os útvonalszeparátorral; a közös policy javítása és a célzott Windows/content-URI tesztek után a teljes csomag zöld.
- `git diff --check` tiszta. Eszköz/emulátor- és GitHub Actions-futtatás ebben a körben nem történt, ezért ezekre nem állítunk zöld CI-eredményt.

## [jupiter:0.51.0] - 2026-07-12

**Scope / miért:** a `Generatedscreens/` nyolc referenciájának teljesebb, konzisztens alkalmazása a már létező Jupiter alkalmazásra, a referencia nélküli képernyők azonos design-nyelvre emelése, valamint a designban jelzett, de korábban hiányzó interakciók valós és adatbiztonságos bekötése. A kör elsődleges korlátja a funkcionális regresszió tilalma.

### Added
- **[ui/theme, ui/components]** A midnight/teal rendszer 49 meglévő Compose képernyőre kiterjesztett szemantikus token-, tipográfia-, kártya-, fájljelvény-, állapotnézet- és navigációs készlete; világos témában szemantikus surface-eket használ a fix sötét festés helyett.
- **[feature/browser, navigation]** Valós FileProvider-alapú Share, Details route, valamint kép/videó esetén a meglévő Compress folyamat fájlt előválasztó útvonala.
- **[feature/search]** Valós Recent és Suggested keresési szekció-policy a már meglévő helyi előzmények és típus-scope-ok mellett; nincs generált találati metaadat.
- **[feature/categories, feature/preview, navigation]** Kijelölt fotók mozgatása valós mappaválasztóval, célútvonal-validációval, no-overwrite védelemmel, megszakítható progresszel és siker utáni MediaStore-frissítéssel; közvetlen galéria- és Similar-photos navigáció; továbbá a látható szűrt/rendezett képkészletből valódi, 3 másodperces autoplay Slideshow pause/resume, előző/következő és körbeforduló vezérléssel.
- **[data/vault, feature/vault]** Sózott PBKDF2-HMAC-SHA256 PIN-rekord kizárólag Android-Keystore-backed encrypted store-ban, plaintext fallback nélkül; device-auth/PIN runtime session, konfigurálható auto-lock és egyszer használható, friss újrahitelesítést kérő SAF import-folyamat.
- **[data/preferences, feature/settings, build]** Perzisztált alapértelmezett rendezés, fájltípus-csoportosítás, törlés előtti megerősítés, AI engedélyezés, alkalmazás-locale, Vault biometria/PIN és auto-lock beállítás; Android locale-konfiguráció.
- **[test]** Célzott policy/állapot tesztek a photo move, slideshow időzítés/wrap, visible-only duplicate selection, search result sections, Vault PIN/security/session, Settings PIN-input ownership és Trash szűrés/rendezés magas kockázatú útvonalaira.

### Changed
- **[ui/feature]** Home, Files, Search, Photos, Duplicates, App storage, Recycle Bin és Settings a nyolc referencia hierarchiájához és sűrűségéhez igazodik; a további meglévő képernyők ugyanazt a közös vizuális rendszert kapják, a mögöttes valódi állapotok és route-ok megtartásával.
- **[feature/main, navigation]** A fő tabok külső shell-váltása visszaverem-stackelő módon történik; Photos → Similar közvetlenül a Duplicates Similar prezentációját nyitja. A nyilvános screen-paraméterek additív, alapértékes bővítések maradnak.
- **[feature/cleanup]** A duplikátum-kijelölés mindig csak a látható szűrési scope-ban működik, a rejtett kijelölést levágja, és törlés előtt ismét megvédi minden csoport minőségi `BEST` keeperét.
- **[feature/settings, feature/ai]** Az AI-kapcsoló élő végrehajtási gate; az alkalmazásnyelv és a fájlkezelési/Vault sorok valós DataStore-beállításokat módosítanak, nem dekoratív kontrollok.
- **[feature/vault]** Külső dokumentumválasztó indításakor a Vault session bezár; konfiguráció-/process-újralétrehozáskor csak a nem titkos pending-marker marad a `SavedStateHandle`-ben, a visszaadott URI kizárólag memóriában vár, navigáció/cancel/stale eredmény törli, sikeres friss auth után legfeljebb egyszer importálható.
- **[build/docs/governance]** `versionCode` 3, `versionName` 0.51.0; README, changelog és versioning a valós képesség-/bizonyítékhatárokat, valamint a kötelező `main` fetch + `pull --ff-only` + közvetlen main commit/push szerződést rögzíti.

### Fixed
- **[feature/browser]** A korábban inert vagy kerülő fájl-akciók tényleges Share/Details/Compress végpontra kerülnek, miközben a normál delete továbbra is kizárólag a Recycle Binbe visz.
- **[feature/categories]** A fotómozgatás nem írhat azonos forrás/cél útvonalra vagy létező célra; hiba/cancel esetén a kijelölés és a forrás megmarad, siker esetén a stale régi útvonal nem tér vissza a listába.
- **[feature/cleanup]** Szűrőváltás után rejtett duplikátum nem maradhat észrevétlenül törlésre kijelölve; a `BEST` elem közvetlen toggle-lal sem választható ki.
- **[feature/vault, security]** Megszűnik az UI-ból elérhető hitelesítés nélküli feloldás és a SAF-visszatérés session-öröklése; biometria csak már konfigurált PIN mellett kapcsolható ki, a PIN törlése visszaállítja a biometrikus policyt.
- **[feature/vault, security]** PIN/jelszó többé nem kerül `rememberSaveable`/SavedState állapotba; a ViewModel csak saját másolatot ellenőriz, majd azt és a UI inputot is nullázza. Keystore-, crypto- vagy IO-hiba zárt állapotot eredményez.
- **[ui/home]** A referencia-kompakt Tool kártyák a teljes direkt elnevezéseket mutatják; a `Duplicate cleanup` többé nem csonkolódik ellipszissel a validált Pixel 8 Pro emulátor-méreten.
- **[ui/navigation]** A What's New nem takarja el a splash/onboarding/permission kapukat, és a státusz-/navigációs sáv ikon-kontrasztja a tényleges app-témát követi.
- **[runtime/workmanager]** A `JupiterApp` on-demand `Configuration.Provider` + Hilt `WorkerFactory` konfigurációja mellől eltávolítottuk az automatikus `WorkManagerInitializer` startup-metaadatot. Így a WorkManager nem indulhat el korábban a default factoryval; az AndroidX Startup provider többi inicializálója változatlanul megmarad.

### Known issues
- **[verification/ui]** A 49-screen forráslefedettség és a v0.51 Pixel 8 Pro emulátoros nyolcképernyős összevetése nem azonos minden kijelzőméret, font scale, OEM és foldable posture vizuális tanúsításával; ezt minden release gate-ben reprezentatív eszközökön ismételni kell.
- **[platform]** All-Files-Access, Usage Access, device/biometrikus auth, SAF picker-visszatérés, locale recreation, valós MediaStore-mozgatás, codec és hálózati/remote működés platform- és tesztadat-függő.
- **[service]** Az Anthropic AI csak érvényes konfigurációval működik; kikapcsolt vagy konfigurálatlan állapotban őszinte unavailable választ ad. Külső cloud/remote szolgáltatás nem szimulált.

### Verification
- `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon` — **BUILD SUCCESSFUL**; az XML riportok összesítése **318 teszt / 0 failure / 0 error / 0 skipped**.
- `git diff --check` — tiszta; a publikus screen/UiState paramétersorrend, a navigation/Hilt graph, a normál-delete→Recycle Bin út, valamint a tiltott Smart Merge/cleanup-AI visszatérés diff-review-ja elkészült.
- A debug APK sikeresen települt az `emulator-5554` `Pixel_8_Pro` AVD-profilra (`sdk_gphone64_x86_64`, Android 14 / API 34, 1344×2992, density 480). A Home, Files, Search, Photos, Duplicates, App storage, Recycle Bin és Settings referenciaképernyők installált APK-ból készített képei össze lettek vetve; a végső Home-hierarchia mind a hat Toolt, a Recent szekciót és a teljes `Duplicate cleanup` nevet tartalmazza.
- Runtime próbák: valós 225 alkalmazásos App storage összesítés; normál Delete → Recycle Bin → Restore sikeres és a forrás visszaállt; exact duplicate csoport valós adaton renderelt; PIN-alapú Vault unlock csak helyes PIN-nel működött; a Slideshow 3 másodpercenként lépett, pause után legalább 5 másodpercig ugyanazon a képen maradt.
- Eszközhatár: a rendszer SAF picker konfiguráció-újralétrehozásos visszatérése, valódi biometrikus/device credential, locale recreation, remote/cloud és több OEM/font-scale/foldable kombináció ebben a körben nem lett teljes integrációban tanúsítva; ezekre nincs túlzó állítás.
- Az első implementációs push `10bf1fb61d7d9dc44dfe4bd23ffb93cad41e0540` SHA-jához tartozó [Android CI run 29206763733](https://github.com/HenrikFaul/Jupiter/actions/runs/29206763733) **success** conclusionnel zárult: Unit tests 3m42s, Build APKs 11m19s, debug/release artifact és GitHub Release publikálás sikeres. A run WorkManager on-demand annotationje feltárt egy régi manifest-init hibát, ezért elkészült a célzott `fd56becaf9ffd611f402b468cd7732c7459d88f4` follow-up.
- A WorkManager follow-up után `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon` ismét **BUILD SUCCESSFUL** (50s, up-to-date cache használatával); 53 suite / 318 teszt / 0 failure/error/skipped. A merged debug manifestben `WorkManagerInitializer=0`, `EmojiCompatInitializer=1`, a lint XML-ben nincs `RemoveWorkManagerInitializer` issue-példány.
- A follow-up [Android CI run 29207505758](https://github.com/HenrikFaul/Jupiter/actions/runs/29207505758) szintén **success**: Unit tests 1m42s, Build APKs 2m50s, debug/release artifact és GitHub Release publikálás sikeres. A WorkManager annotation eltűnt; csak a GitHub Actions Node 20→24 infrastruktúra-deprecáció maradt. A teljes kör közvetlenül `origin/main`-re került.

## [jupiter:0.52.0] - 2026-07-12

**Scope / miért:** a csatolt követelmény-extrakt P0/P1 indexelési és duplikációs hiányainak célzott pótlása: az app neve és márkajelzése maradjon **Jupiter**, az index folyamatosabban frissüljön, az új fájlok foreground/observer/periodic úton is hamar bekerüljenek, és a Similar duplikáció ne csak fotókra korlátozódjon.

### Added
- **[data/index]** `StructuralFingerprintBackfillWorker`: a meglévő könyvtár text/code SimHash, archive/APK member-tree, video keyframe, PDF render és audio envelope fingerprintjeit háttérben tölti fel, így a nem-kép near-duplicate rétegek nem csak újonnan érkező fájlokra működnek.
- **[data/index, feature/cleanup]** `nearDuplicateStructuralGroups()` repository API + Similar tab bekötés: a Duplicates képernyő most a perceptuális fotócsoportok mellett text/code, archive/APK, video, PDF és audio hasonló csoportokat is megjelenít, `similar = true` scope-ban.
- **[test]** `NearDuplicateStructuralGroupTest`: Robolectric + in-memory Room bizonyíték arra, hogy text/archive/video strukturális/media csoportok ténylegesen bekerülnek a review-listába, nem csak arrival notificationként léteznek.

### Changed
- **[data/index/runtime]** A MediaStore observer többé nem csak dedup-reconcile triggert ad: változásjelre `ensureIndexed()` is fut, tehát az index-metaadat survey is azonnal kap kick-et. Foregroundon szintén mindig `ensureIndexed()` fut KEEP policyvel, így a korábbi komplett generáció olvasható marad, miközben a háttérben frissül.
- **[data/index/runtime]** A periodikus index-frissítő 12 óráról a WorkManager minimumához igazított 15 perces battery-not-low ütemre váltott. Ez a zárt app mellett létrejött/törölt/módosított fájlokat sokkal hamarabb reconciliálja.
- **[data/index]** Sikeres authoritative full scan után a rendszer előmelegíti az exact dedup jelölt-hasheket (`hashCollidingSizes(4 KiB)`), majd mind a kép-perceptuális, mind a nem-kép strukturális/media backfillt láncolja.
- **[build/docs]** `versionCode` 4, `versionName` 0.52.0; README, changelog, versioning és lessons frissítve.

### Fixed
- **[branding]** Ellenőrizve: `app_name`, launcher/activity label, package és látható wordmark továbbra is **Jupiter**; nincs futó app-szintű `Jupiscan` névhasználat. A csatolt követelmény-extrakt Jupiscan elnevezése követelményforrásként kezelt, nem terméknévként.
- **[dedup/ui]** A Similar tab korábban érdemben fotóközpontú volt; a kód már tartalmazott text/archive/video/PDF/audio fingerprint forrásokat, de ezek meglévő könyvtárra nem futottak proaktívan és nem kerültek általános csoportlistába. Most a háttér-backfill és a repository grouping ezt lezárja.
- **[freshness]** Egy MediaStore jel vagy app-foreground nem hagyatkozik kizárólag a 12 órás frissítésre; az index azonnal kap KEEP survey-kicket, miközben a dedup reconciler továbbra is checkpointból dolgozik.

### Known issues
- **[platform]** Android nem garantál valódi, azonnali process-wakeupot minden zárt-app fájlváltozásra. A megvalósított modell: élő processben ContentObserver, app-foregroundon azonnali catch-up, és 15 perces WorkManager periodic; OEM/Doze ezt késleltetheti.
- **[dedup]** A Similar tab strukturális/media csoportjai a háttér-fingerprint lefedettségétől függenek; undecodable vagy DRM/encrypted media `UNHASHABLE`, és nem kerül automatikus törlési bizonyítékként használatba. Exact törlési döntés továbbra is teljes-tartalom hash alapján történik.

### Verification
- `git pull --ff-only origin main` — már naprakész `main`, munka közben meglévő untracked `versioning.zip` érintetlen.
- `.\gradlew.bat :app:compileDebugKotlin --no-daemon` — **BUILD SUCCESSFUL**.
- `.\gradlew.bat :app:testDebugUnitTest --tests "com.jupiter.filemanager.data.index.NearDuplicateStructuralGroupTest" --no-daemon` — **BUILD SUCCESSFUL**.
- `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon` — **BUILD SUCCESSFUL**; XML összesítés: **54 suite / 319 teszt / 0 failure / 0 error / 0 skipped**.
- `git diff --check` — tiszta (csak CRLF-normalizációs figyelmeztetések); `rg "Jupiscan|JupiScan|jupiscan" app` — nincs találat az app-forrásban.

## [jupiter:0.53.0] - 2026-07-13
### Added
- **[data/index/schema]** Room index DB v6: új `dedup_decision` tábla canonical döntéskulccsal, valamint `index_state` delta/checkpoint mezők (`checkpointJson`, `mediaStoreVersion`, `lastMediaStoreGeneration`, `lastDeltaSyncAt`). A v5→v6 migráció in-place, nem dobja el a drágán újraszámolható index/hashing adatokat.
- **[data/index/readiness]** Egységes `StorageReadiness` read model és `IndexReadinessRepository`: metadata, exact-hash, image-descriptor és structural-descriptor coverage külön `Coverage` státusszal. Ez a közös szerződés váltja ki a képernyőnként eltérő „kész-e az index?” logikát a következő UI-kötéseknél.
- **[core/path-policy]** DI-zott `PathPolicy` / `DefaultPathPolicy`, amely a `.Trash`, app-private és temporary útvonalakat ugyanazon classifierrel osztályozza; a legacy `StorageExclusions` ugyanarra a policy-ra delegál.

### Changed
- **[data/index/migration]** Az index adatbázis már nem használ általános `fallbackToDestructiveMigration()`-t. Az ismert v4→v5 és v5→v6 utak explicit migrációval mennek, hogy a descriptor/hash cache ne törlődjön frissítéskor.
- **[data/file-ops]** Sikeres COPY után a teljes létrejött célfa indexelődik, nem csak a gyökér. MOVE továbbra is a meglévő atomi subtree path rewrite-ot használja, így hash-ek megmaradnak.
- **[data/trash]** Restore után a teljes visszaállított subtree indexelődik, nem csak a root rekord.
- **[data/delta]** A dedup reconciler a sikeres baseline / checkpoint lapozás után a Room `index_state` sorba is rögzíti a delta marker állapotot.
- **[build]** `versionCode` 5, `versionName` 0.53.0.

### Fixed
- **[dedup/notifications]** Ugyanaz a duplikációs döntés nem küld újra alertet/notificationt observer burst, WorkManager replay vagy process restart után: a `DuplicateDetector` előbb perzisztálja a canonical pair/group + decision type + algorithm version kulcsot, és csak új insert esetén emittál.
- **[subtree/index]** Copy/restore esetén megszűnt az a rés, hogy a fájlrendszeren létező leszármazottak csak a következő teljes scan után jelentek meg az index-alapú Search/Cleanup/Analytics felületeken.

### Known issues
- **[device-bound]** A dokumentumban kért 40k képes, 95/99 percentilis eszközbenchmark és reboot/process-kill instrumentációs bizonyítás továbbra is készülék/emulátor-farm adatot igényel. A repo jelenleg unit/Robolectric regresszióval és LSH/pair-budget kódvédelemmel bizonyít; a fizikai benchmark külön release gate marad.
- **[delta]** A v0.53 kör rögzíti a delta marker state-et és idempotenssé teszi a dedup-arrival útvonalat, de a teljes MediaStore `getVersion`/generation alapú metadata delta + delete/rename/move reconciliation még következő körös architektúra-feladat.
- **[mutation]** Copy/restore subtree indexelés megvan, de a teljes operation journal/saga crash-recovery még nincs végigvezetve minden fájlműveleten.

### Verification
- `git pull --ff-only origin main` — already up to date; meglévő untracked `versioning.zip` érintetlen.
- `.\gradlew.bat :app:compileDebugKotlin --no-daemon` — **BUILD SUCCESSFUL**.
- `.\gradlew.bat :app:testDebugUnitTest --no-daemon` — **BUILD SUCCESSFUL**.
- `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon` — **BUILD SUCCESSFUL**.
- XML összesítés: **54 suite / 320 teszt / 0 failure / 0 error / 0 skipped**.
- `git diff --check` — tiszta (csak CRLF-normalizációs figyelmeztetések).

## [jupiter:0.54.0] - 2026-07-13

**Scope / miért:** a felhasználó által készüléken visszamért négy bizalmi/funkcionális rés lezárása regresszió nélkül: a rendszer tárhelykezelőjével egyező teljes kapacitás, érthető és biztonságosan kipróbálható Automation, exact+similar összesített duplikátumszám és visszaállított méret-/kijelölési vezérlők, valamint az újra letöltött kép megbízható érkezési értesítése.

### Added
- **[automation/domain,data,ui]** Öt stabil azonosítójú, upgrade-safe és alapból suspended példa: letöltött PDF/APK/ZIP/MP3 rendezés és screenshot-kedvencelés. Meglévő user rule-ok megmaradnak; a presetek egyszer kerülnek melléjük, törlés után nem élednek újra.
- **[automation/ui]** Részletes ötlépéses útmutató, szabályonkénti módosításmentes `Try safely` előnézet, szerkesztés/átnevezés, aktív–suspended kapcsoló és megerősített szabálytörlés. A szabály törlése nem töröl telefonfájlt.
- **[automation/safety]** Közös `AutomationSafety`: a delete/erase/remove/wipe/trash jellegű action authoringkor és a végrehajtó motorban is tiltott; régi vagy manipulált destructive rule no-op. A `move to` és `move it to` forma támogatott.
- **[test]** Új storage-capacity, Automation preset/migráció/no-delete/gateway, duplicate summary és generation-arrival regressziós tesztek.

### Changed
- **[storage/data]** A primary volume total/free kijelzés API 26+ alatt a `StorageStatsManager.getTotalBytes/getFreeBytes(StorageManager.UUID_DEFAULT)` rendszerforrást használja. Ha az OEM ezt nem adja vissza, a `StatFs` user-data fájlrendszerből biztonságos retail-tier fallback készül.
- **[storage/ui]** A készülék-/volume-kapacitás külön decimális formattert kapott, így 256 000 000 000 byte `256 GB`; a fájlméretek meglévő bináris formattere változatlan. Home, Storage analytics, Cleanup és a közös storage bar ugyanazt a szemantikát használja.
- **[automation/data]** Az Automation move a normál `FileRepository.move()` gateway-en fut, ezért a már leszállított no-overwrite, progress és index-rewrite védelem érvényben marad.
- **[dedup/ui]** A hero az exact és similar scope összes egyedi fájlútvonalát `duplicate items` néven összegzi. Az Exact/Similar fülek itemszámot mutatnak; a méretküszöb, Largest/Smallest rendezés, Select all és Deselect all mindig látható.
- **[build/ui]** `versionCode` 6, `versionName` 0.54.0; a What's New tartalma a tényleges 0.54 képességeket mutatja.

### Fixed
- **[dedup/arrival]** Gyökérok-javítás: a régi `_ID` checkpoint már a letöltés pending/0-byte INSERT során túlléphetett a soron, a kész fájl pedig ugyanazt az `_ID`-t UPDATE-elte, ezért soha nem került újra ellenőrzésre. Android 11+ alatt a reconciler MediaStore version + `GENERATION_MODIFIED` deltára váltott és csak `IS_PENDING = 0` sort dolgoz fel; a finalizálás új generationje így biztosan látszik.
- **[dedup/scheduling]** A leading-edge 1,5 másodperces observer gate helyett trailing-edge reconcile fut a változásburst után. A unique WorkManager policy `APPEND_OR_REPLACE`, ezért egy futó reconcile alatt érkező completion-jel hagy maga után új passzt.
- **[dedup/selection]** A közvetlen Select all kizárólag az aktív tab + méretszűrő látható csoportjain dolgozik, és a meglévő globális quality-ranked keeper policy minden csoport őrzendő példányát továbbra is kizárja.

### Known issues
- **[platform/storage]** Harmadik féltől származó Android app nem tudja fájlonként bejárni az OEM/system által védett partíciókat. A teljes fizikai kapacitás és a total−free alapján minden foglalt byte elszámolt, de a védett rész kategorizálása rendszer/egyéb összeg marad; nem gyártunk hozzá hamis fájllistát.
- **[platform/delta]** `GENERATION_MODIFIED` Android 11+-os. Android 10-en `_ID` + `IS_PENDING=0` fallback marad; a teljes delete/rename/move catalog reconciliationt továbbra is az authoritative survey végzi.
- **[platform/notification]** A riasztás csak engedélyezett Android notification permission és aktív `Duplicate alerts` csatorna mellett lehet látható; az indexelés és a perzisztált dedup döntés ettől függetlenül lefut.
- **[automation/safety]** Az aktív szabályok továbbra is csak az Automation képernyőn indított, megerősített `Run rules now` során futnak; nincs csendes háttérbeli fájlmozgatás. Ez a meglévő adatbiztonsági szerződés tudatos megtartása.

### Verification
- `git pull --ff-only origin main` — already up to date; közvetlen `main`, a meglévő untracked `versioning.zip` érintetlen.
- `StorageCapacityPolicyTest`, `FormattersTest`, `AutomationSafetyTest`, `RuleEngineGatewayTest`, `DedupReconcilerTest`, `DuplicatesUiStateSizeFilterTest`, `DuplicatesUiStateSummaryTest` — cache nélküli tiszta célzott kapu **BUILD SUCCESSFUL**.
- `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon` — **BUILD SUCCESSFUL**; XML összesítés: **58 suite / 344 teszt / 0 failure / 0 error / 0 skipped**.
- API 34 `emulator-5554`: APK streamed install sikeres, cold launch `Status: ok`, `MainActivity` top-resumed, FATAL/AndroidRuntime találat nincs.
- Runtime UI: Home `of 8.0 GB` retail-total; mind az öt suspended Automation preset renderelt; `Try safely` → `Safe preview: 0 files match. Nothing was changed.`; Duplicates → `duplicate items`, Exact/Similar, size filter/order, Select all/Deselect all renderelt.
- Valós arrival próba: meglévő PNG bájtazonos másolata MediaStore scan után `Duplicate detected` notificationt adott (`you already have 2 copies`); a kör által létrehozott proof fájl utána eltávolítva.
- `git diff --check` — whitespace-hiba nincs; csak a repository CRLF-normalizációs figyelmeztetései.
- **[remote]** Implementációs commit `3c6e9a9132d535d8341d413b992aef91aa76c4ba`; [Android CI run 29216712702](https://github.com/HenrikFaul/Jupiter/actions/runs/29216712702) **success**. Unit tests és Build APKs job zöld; debug/release artifact, build archive és `build-latest` GitHub Release publikálás sikeres.
- **[release]** `app-debug.apk` 36 126 106 byte, SHA-256 `f346811d6abb7030e5335dd90738b25566c13e0c80946bd4b460ad7f9da0461a`; `app-release.apk` 7 870 103 byte, SHA-256 `9ed24c2c43a7cdeb730df88f747cad14f476ee5d016fb72517f0cf63e3f880e7`.

## [jupiter:0.55.0] - 2026-07-13

**Scope / miért:** a valós készülékes visszajelzés szerint az ismét letöltött kép továbbra sem adott riasztást. Ez a kör az érkezés→index→full-hash→döntés→Android-értesítés teljes útját erősíti meg, különösen a frissítés utáni checkpoint-race, OEM fájl-/MediaStore-időzítés és korábban letiltott notification helyzetekben. Funkcionális regresszió tiltott.

### Added

- **[dedup/notification-outbox]** A `dedup_decision` rekord tartós delivery-életciklust kapott (`PENDING`, `DELIVERING`, `BLOCKED`, `FAILED`, `DELIVERED`), claim-olt retry-jal és összevont summary notificationnel. A Room v6→v7 explicit, adatmegőrző migrációja nem dobja el az indexet vagy a korábbi döntéseket.
- **[dedup/observation]** Bounded, rekurzív `FileObserver` safety net figyeli a Downloads, DCIM, Pictures, Movies, Documents és Music fákat; a CREATE/CLOSE_WRITE/MOVED_TO jelre azonnal tartós WorkManager backup is készül, majd két stabil méret/mtime ablak után processbeli ellenőrzés fut.
- **[dedup/ui]** A Duplicate cleanup menü `Duplicate alert settings` eleme a rendszer alkalmazás-értesítés beállításaihoz vezet, így a felhasználó a permissiont és a `Duplicate alerts` csatornát közvetlenül helyre tudja állítani.
- **[test]** Retryable arrival, blocked/failed notification delivery retry, 2001 elemű legacy-backlog, MediaStore probe/query hiba, same-generation lapozás és FileObserver policy regressziós lefedettség.

### Changed

- **[dedup/arrival]** Az `ArrivalInspector` már typed `Unique` / `Alerted` / `Retry` eredményt ad. Átmeneti index-, hash-, decode-, Room- vagy provider-hiba nem azonos többé az egyedi fájllal, ezért a checkpoint nem léphet át egy még nem olvasható letöltésen.
- **[dedup/delta]** Android 11+ alatt a MediaStore version/generation probe vagy delta-query hibája retryable marad; nincs veszélyes `_ID` fallback és nincs „üres siker” miatti generation-settle. Az azonos generationhöz tartozó page-határ minden kötött sort együtt tart.
- **[dedup/upgrade]** v0.54 `_ID` checkpointból generation modellre frissítéskor az új érkezések legacy-gapje teljesen lefut, és csak utána kerül quiet generation baseline. A 2000-es fairness cap így nem nyelheti le a 2001. fájlt.
- **[app-lifecycle]** Permission callback és minden foreground újrapróbálja a korábban Android által blokkolt, már biztonságosan meghozott dedup-döntések kézbesítését; az új indexelés indításától függetlenül.
- **[build/ui]** `versionCode` 7, `versionName` 0.55.0; What's New az új helyreálló arrival-alert viselkedést írja le.

### Fixed

- **[real-device false negative]** Egy frissítés után, a generation baseline felvétele előtt letöltött kép többé nem kerülhet csendes baseline-ba.
- **[notification delivery]** Permission-, app-level notification- vagy channel-tiltás többé nem jelenti azt, hogy a canonical dedup-decision örökre „már értesített” állapotba kerül. Engedélyezés után egyszeri összesített riasztás kézbesíthető.
- **[process/OEM resilience]** A processben élő ellenőrzés nem csak WorkManagerre vár, de process death esetén a jel pillanatában sorba tett tartós backup és a meglévő periodic/foreground catch-up konvergál.

### Regression checks

- Exact duplikációt továbbra is kizárólag teljes content hash dönt el; a perceptuális/strukturális rétegek nem válhatnak törlési bizonyítékká.
- A canonical döntéskulcs, keeper-védelem, Exact/Similar scope, méretszűrő, Select all/Deselect all és Recycle Bin workflow változatlanul megmaradt.
- A meglévő v4→v5→v6 Room migrációk mellett v6→v7 is explicit; nincs `fallbackToDestructiveMigration()`.
- A navigáció, Hilt graph és a korábbi képernyő-API-k csak additívan változtak; a Duplicates menü új eleme nem módosít törlési vagy kiválasztási műveletet.

### Verification

- `main` frissítése munka előtt `git pull --ff-only origin main`; a korábbi untracked `versioning.zip` érintetlen.
- Cache nélküli tiszta `:app:clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --no-build-cache` kör után az XML: **59 suite / 354 teszt / 0 failure / 0 error / 0 skipped**.
- `lintDebug` újragenerálva: az új publisherből származó MissingPermission jelzés javítva; a reportban csak a korábban meglévő 40 Media3 opt-in és 2 Compose finding maradt, `abortOnError=false` meglévő build-policy mellett.
- API 34 `emulator-5554`: All Files Access + notification permission mellett egy új `/Download/jupiter_arrival_copy.png` bájtazonos másolat ténylegesen `Duplicate detected — you already have 1 copy` system notificationt adott; az SQLite outbox rekord `DELIVERED|1`.
- Ugyanezen emulátoron upgrade után a már függő durable döntések `Duplicate files detected` összesített riasztásként megjelentek a notification permission engedélyezésekor.
- `git diff --check`: whitespace-hiba nincs (csak repository CRLF figyelmeztetés).

### Remaining risk

- Android/OEM nem garantál tetszőleges zárt-app folyamat azonnali felébresztését; a direct observer csak élő processben gyorsít. A WorkManager, foreground és periodic reconciliation a tartós convergence rétegek, ezért Doze/OEM throttle mellett a riasztás késhet, de nem maradhat a transient checkpoint-advance miatt végleg elveszett.
- Nincs fizikai Samsung/Chrome teljes körös instrumentációs bizonyíték ebben a repo-környezetben; a fenti API 34 runtime proof és a célzott JVM/Robolectric tesztek nem helyettesítik azt.
