# Jupiter — TOP 10 ÉRTÉKNÖVELŐ\nNÖVEKEDÉSI STRATÉGIA

### Hogyan válik a Jupiter az alapértelmezett fájlkezelővé a profi, kreatív és adatvédelem-tudatos Android-felhasználók számára

*Okos, adatvédelem-központú Android fájlkezelő valódi LAN/felhő háttérrel és eszközön futó MI-vel*

| | |
|---|---|
| Készült | 2026-06-30 |
| Repository | `HenrikFaul/Jupiter` |
| Verzió | vv0.1.0 |
| Jelenlegi értékelés (ma) | **€140k–€320k** |
| Cél-értékelés (kezdeményezések leszállítva) | **€1.4M–€3.2M** |
| Érték-szorzó | **8–10×** |
| Megbízhatóság | Medium |
| Szerző | AI-assisted Strategic Intelligence |

> Generálva a `data/project.json` + `data/growth_strategy.json` fájlokból, 2026-06-30. A PDF renderer (`generate_growth_pdf.py`) ebben a toolkit-példányban nincs csomagolva; ez a Markdown kiadás a kanonikus leszállítandó.

## Összefoglaló mátrix

| # | Kezdeményezés | Értékelési hatás | Ráfordítás | Építhető a jelenlegi appban | Elsődleges mozgatott KPI |
|---:|---|---|---|---|---|
| 1 | Jupiter Pro — Monetizációs és Jogosultságkezelő Motor | **+€350k–€700k** | L (nagy) | roadmap | Üzleti modell: Bevétel előtti -> bevételt termelő |
| 2 | Valódi Felhőtárhely — Google Drive / Dropbox / OneDrive (OAuth + REST) | **+€220k–€420k** | L (nagy) | roadmap | Funkció-paritás: Lezárja a #1 hiányt a Solid Explorer/FX/X-plore-hoz |
| 3 | Lokalizáció és Globális Elérés (12+ nyelv) | **+€120k–€260k** | M (közepes) | roadmap | Megcélozható telepítési bázis: Csak-angol -> 12+ nyelv |
| 4 | AI Pro Csomag (Claude) — Összegzés, Okos Átnevezés, Auto-Rendszerezés, Szemantikus Keresés | **+€160k–€320k** | L (nagy) | roadmap | Kategória-megkülönböztetés: Egyetlen AI-natív Android fájlkezelő |
| 5 | Kezdőképernyő Widgetek, App Parancsikonok és Gyorsbeállítás Csempe | **+€90k–€180k** | M (közepes) | roadmap | DAU / megtartás: Mindig jelenlévő kezdőképernyő-felület |
| 6 | Adatvédelem-központú Opt-in Termékanalitika és Összeomlás-jelentés | **+€80k–€170k** | S (kicsi) | roadmap | Mérhetőség: Aktiváció/megtartás/konverzió láthatóvá válik |
| 7 | Aktivációs és Megtartási Hurok (Onboarding Tölcsér, Újdonságok, Értékelés, Visszacsábítás) | **+€90k–€190k** | M (közepes) | roadmap | Aktivációs arány: Műszerezett onboarding befejezés |
| 8 | Nagy Teljesítményű Keresési Index (Room FTS) + Mentett Keresések + Tartalomkeresés | **+€110k–€220k** | L (nagy) | roadmap | Keresési késleltetés: Fájlrendszer-bejárás -> azonnali FTS keresés |
| 9 | Személyre Szabás és Témázás (Material You Kiemelőszín, Ikon/Sűrűség Témák, Egyedi Kezdőképernyő) | **+€70k–€150k** | M (közepes) | roadmap | Megtartás / identitás: Testreszabott app = alacsonyabb lemorzsolódás |
| 10 | Megosztás és Együttműködési Hub ("Mentés a Jupiterbe" Fogadás, Gyorsmegosztás, SAF DocumentsProvider) | **+€100k–€200k** | M (közepes) | roadmap | Viralitás: Megjelenik minden alkalmazás megosztási lapján |
| | **Portfólió összesen** | **+€1.39M–€2.81M** | | | |

## Tartalom

1. [Jupiter Pro — Monetizációs és Jogosultságkezelő Motor](#1-jupiter-pro-monetizációs-és-jogosultságkezelő-motor) — **+€350k–€700k**
2. [Valódi Felhőtárhely — Google Drive / Dropbox / OneDrive (OAuth + REST)](#2-valódi-felhőtárhely-google-drive-dropbox-onedrive-oauth-rest) — **+€220k–€420k**
3. [Lokalizáció és Globális Elérés (12+ nyelv)](#3-lokalizáció-és-globális-elérés-12-nyelv) — **+€120k–€260k**
4. [AI Pro Csomag (Claude) — Összegzés, Okos Átnevezés, Auto-Rendszerezés, Szemantikus Keresés](#4-ai-pro-csomag-claude-összegzés-okos-átnevezés-auto-rendszerezés-szemantikus-keresés) — **+€160k–€320k**
5. [Kezdőképernyő Widgetek, App Parancsikonok és Gyorsbeállítás Csempe](#5-kezdőképernyő-widgetek-app-parancsikonok-és-gyorsbeállítás-csempe) — **+€90k–€180k**
6. [Adatvédelem-központú Opt-in Termékanalitika és Összeomlás-jelentés](#6-adatvédelem-központú-opt-in-termékanalitika-és-összeomlás-jelentés) — **+€80k–€170k**
7. [Aktivációs és Megtartási Hurok (Onboarding Tölcsér, Újdonságok, Értékelés, Visszacsábítás)](#7-aktivációs-és-megtartási-hurok-onboarding-tölcsér-újdonságok-értékelés-visszacsábítás) — **+€90k–€190k**
8. [Nagy Teljesítményű Keresési Index (Room FTS) + Mentett Keresések + Tartalomkeresés](#8-nagy-teljesítményű-keresési-index-room-fts-mentett-keresések-tartalomkeresés) — **+€110k–€220k**
9. [Személyre Szabás és Témázás (Material You Kiemelőszín, Ikon/Sűrűség Témák, Egyedi Kezdőképernyő)](#9-személyre-szabás-és-témázás-material-you-kiemelőszín-ikonsűrűség-témák-egyedi-kezdőképernyő) — **+€70k–€150k**
10. [Megosztás és Együttműködési Hub ("Mentés a Jupiterbe" Fogadás, Gyorsmegosztás, SAF DocumentsProvider)](#10-megosztás-és-együttműködési-hub-mentés-a-jupiterbe-fogadás-gyorsmegosztás-saf-documentsprovider) — **+€100k–€200k**

---

## 1. Jupiter Pro — Monetizációs és Jogosultságkezelő Motor

**Értékelési hatás: +€350k–€700k** · Ráfordítás: L (nagy) · Építhető a jelenlegi appban: roadmap

Ez a Jupiter legnagyobb hatású értéknövelő tényezője. Ma az alkalmazás funkcionálisan kész, bevétel előtti MVP: 33 932 sornyi Kotlin valódi LAN/SMB/SFTP/FTP/WebDAV háttérrendszerekkel, EncryptedFile széffel, Wi-Fi szerverrel és Claude AI asszisztenssel — de értékkihasználási lehetőség nélkül. Egy Google Play Billingre épülő freemium Pro szint ezt a mérnöki munkát bevételt termelő termékké alakítja, ami pontosan az, ami egy építési költség + IP alapú eszközt (a 9. szakasz €140k–€320k alapértéke) bevétel-szorzós üzletté minősít át. A tervezési elv a regressziómentesség: egy EntitlementManager, amely minden Feature-t FELOLDOTT állapotra állít, amíg valódi billing termék nincs konfigurálva, így ami ma működik, soha nem romlik el.

A piac közvetlenül igazolja a modellt. A Solid Explorer 14 napos próbát majd egyszeri feloldást kínál; az X-plore és az FX File Explorer ingyenes + fizetős Pro; a MiXplorer és a Total Commander bővítményeket monetizál. A Files by Google ingyenes marad, mert a Google máshol monetizál — világos rést hagyva egy adatvédelem-központú, reklámmentes, sötét minták nélküli fizetős alkalmazásnak. Egy fogyasztói segédprogram, amely a telepítések akár 2–4%-át €4,99–€9,99 örökös feloldássá konvertálja, plusz opcionális előfizetés az AI Csomagra, jól ismert minta a Google Playen. Lényeges: a prémium felületek (Széf, NAS/távoli, AI Csomag, kétpanel, haladó tisztítás) szint mögé zárása jelzi egy felvásárlónak, hogy a bevételi sínek léteznek és funkciónként visszafordíthatók.

Technikai megközelítés ehhez a Kotlin/Compose/Hilt kódbázishoz: új core/entitlement csomag egy EntitlementManagerrel (Hilt singleton), egy Feature enummal (VAULT, REMOTE_NAS, AI_SUITE, DUAL_PANE, ADVANCED_CLEANUP) és egy Tier modellel StateFlow-ként. Új feature/billing csomag, amely a com.android.billingclient:billing-ktx-et egy BillingClient wrapperbe burkolja, Compose PaywallScreennel és UpgradeViewModellel, amely lekérdezi a termékeket, indítja a vásárlási folyamatot és nyugtázza a jogosultságokat. A feloldott állapot a meglévő data/preferences DataStore mintán (SettingsDataStore.kt) keresztül perzisztálódik. Könnyű kapuellenőrzések — egyetlen isUnlocked(Feature) hívás — a prémium belépési pontokon: feature/vault/VaultViewModel.kt, feature/cloud/NasConnectionsViewModel.kt, feature/ai/AiAssistantViewModel.kt és a feature/browser kétpaneles út, mindegyik FELOLDOTT állapotra esve, amíg a billing élesedik.

### Implementációs prompt (AI kódoló asszisztensbe beilleszthető)

1. Hozzon létre: core/entitlement/EntitlementManager.kt
2.   @Singleton Hilt osztály, alkalmazás-szintű injektálás
3.   fun isUnlocked(feature: Feature): Boolean
4.   val tier: StateFlow<Tier>  // FREE / PRO
5.   Alap: minden Feature FELOLDOTT, amíg nincs billing termék
6. 
7. Hozzon létre: core/entitlement/Feature.kt
8.   enum Feature { VAULT, REMOTE_NAS, AI_SUITE, DUAL_PANE, ADVANCED_CLEANUP }
9. Hozzon létre: core/entitlement/Tier.kt  // sealed: Free, Pro(purchaseToken)
10. 
11. Hozzon létre: feature/billing/BillingClientWrapper.kt
12.   com.android.billingclient:billing-ktx burkolása
13.   queryProductDetails, launchBillingFlow, acknowledgePurchase
14.   Vásárlások -> EntitlementManager.tier leképezése
15. Hozzon létre: feature/billing/PaywallScreen.kt  (Compose, Material 3)
16. Hozzon létre: feature/billing/UpgradeViewModel.kt
17. 
18. Feloldott állapot perzisztálása: data/preferences/SettingsDataStore.kt
19. 
20. Kapuellenőrzések (FELOLDOTT-ra esve, amíg a billing él):
21.   feature/vault/VaultViewModel.kt          -> Feature.VAULT
22.   feature/cloud/NasConnectionsViewModel.kt -> Feature.REMOTE_NAS
23.   feature/ai/AiAssistantViewModel.kt       -> Feature.AI_SUITE
24.   feature/browser (kétpaneles út)          -> Feature.DUAL_PANE
25.   feature/cleanup/SmartMergeViewModel.kt   -> Feature.ADVANCED_CLEANUP
26. 
27. Gradle: com.android.billingclient:billing-ktx hozzáadása
28. Pro termék + előfizetés regisztrálása a Play Console-ban

### Sikermetrikák

| Metrika | Cél |
|---|---|
| Üzleti modell | **Bevétel előtti -> bevételt termelő** |
| Értékelési átminősítés | **Építési költség+IP -> bevétel-szorzó** |
| Telepítés->Pro konverzió | **2–4% (segédprogram freemium referencia)** |
| Regressziós kockázat | **Nulla (alapból FELOLDOTT)** |

### Újragenerálási prompt

```
Elemezze a Jupitert (com.jupiter.filemanager, natív Kotlin/Compose/Hilt Android fájlkezelő) és készítsen Jupiter Pro monetizációs és jogosultságkezelési tervet. Fedje le: (1) miért a freemium/Play Billing a #1 bevétel előtti értéknövelő tényező, Solid Explorer/X-plore/FX/Files by Google modellek alapján, (2) core/entitlement EntitlementManager + Feature enum + Tier alapból-FELOLDOTT regressziómentes tervezéssel, (3) feature/billing BillingClient wrapper + PaywallScreen + UpgradeViewModel, kapuellenőrzések a vault/cloud/ai/kétpanel/cleanup pontokon, (4) értékelési átminősítési hatás, (5) újragenerálási meta-prompt. 1–5. pont struktúra.
```

---

## 2. Valódi Felhőtárhely — Google Drive / Dropbox / OneDrive (OAuth + REST)

**Értékelési hatás: +€220k–€420k** · Ráfordítás: L (nagy) · Építhető a jelenlegi appban: roadmap

A Jupiter már tartalmaz egy Felhő Hub vázat (feature/cloud/CloudHubScreen.kt, CloudHubViewModel.kt) és egy CloudAccount domain modellt, amelynek CloudProvider enumja már felsorolja a GOOGLE_DRIVE, DROPBOX, ONEDRIVE, ICLOUD, BOX és WEBDAV értékeket. Ennek a váznak élő felhőböngészéssé és átvitellé alakítása a második legnagyobb tényező, mert ez zárja le a leglátványosabb funkcióhiányt a kategóriavezetőkkel szemben, és pontosan azokat a vásárlókat nyitja meg, akik fizetnek: akiknek a fájljai több személyes felhőfiók között szóródnak szét. Ez additív — a LAN háttérrendszerek és a távoli absztrakció már léteznek és működnek — így egy bevált mintát bővít, nem új architektúrát talál ki.

Versenytársi szempontból ez jól megcsinált alapelvárás. A Solid Explorer fő megkülönböztetője a széles felhőszolgáltató-lista; az FX, az X-plore és a CX File Explorer mind hirdet Drive/Dropbox/OneDrive-ot. A Files by Google szándékosan kihagyja a harmadik fél felhőit. Egy adatvédelem-központú kezelő, amely Drive, Dropbox és OneDrive között böngészik és visz át — OAuth tokenekkel a meglévő titkosított CredentialStore-ban és telemetria nélkül — közvetlen, védhető válasz arra, hogy 'miért ne csak a Solid Explorert használjam'. A felhőkapcsolat egyben a leggyakoribb egyetlen ok, amiért a felhasználók fizetnek egy fájlkezelőért, ami közvetlenül táplálja a Pro szintet (#1 kezdeményezés).

Technikai megközelítés: pontosan ahogy az SMB/SFTP/FTP/WebDAV be van kötve a data/remote-ban. Új data/cloud csomag AppAuth-alapú OAuth-tal (Authorization Code + PKCE) és szolgáltatónkénti REST kliensekkel OkHttp felett (Drive v3, Dropbox v2, Microsoft Graph). Minden szolgáltató implementálja a meglévő domain/remote/RemoteFileSource interfészt (type/testConnection/list/download), így a Felhő Hub, az átviteli sor (data/transfer) és a böngésző ugyanazokat a kódutakat használja, mint a NAS. A fiókok a ConnectionRepositoryImpl.kt-n és a CloudAccount modellen keresztül kötődnek be; a refresh tokenek a data/remote/CredentialStore.kt-n keresztül tárolódnak. A CloudHubViewModel.kt helyőrző fiókokról élő RemoteFileSource hívásokra vált.

### Implementációs prompt (AI kódoló asszisztensbe beilleszthető)

1. Újrahasznosítás: domain/remote/RemoteFileSource.kt (type/testConnection/list/download)
2. Újrahasznosítás: domain/model/CloudAccount.kt (CloudProvider már tartalmaz DRIVE/DROPBOX/ONEDRIVE)
3. 
4. Hozzon létre: data/cloud/oauth/CloudOAuthManager.kt
5.   AppAuth Authorization Code + PKCE
6.   Refresh tokenek tárolása: data/remote/CredentialStore.kt (titkosított)
7. 
8. Hozzon létre: data/cloud/GoogleDriveFileSource.kt   (Drive v3 REST, OkHttp)
9. Hozzon létre: data/cloud/DropboxFileSource.kt        (Dropbox v2 REST, OkHttp)
10. Hozzon létre: data/cloud/OneDriveFileSource.kt       (Microsoft Graph, OkHttp)
11.   mind implementálja a RemoteFileSource-t (SmbFileSource.kt mintájára)
12. 
13. Bekötés: data/connection/ConnectionRepositoryImpl.kt
14. Források regisztrálása: data/remote/RemoteSourceProviderImpl.kt
15. 
16. feature/cloud/CloudHubViewModel.kt -> élő fiókok RemoteFileSource-on át
17. feature/cloud/CloudHubScreen.kt    -> csatlakozás/leválasztás, böngészés, átvitel
18. data/transfer újrahasznosítása fel-/letöltési sorhoz
19. 
20. Gradle: net.openid:appauth + okhttp (már jelen van)

### Sikermetrikák

| Metrika | Cél |
|---|---|
| Funkció-paritás | **Lezárja a #1 hiányt a Solid Explorer/FX/X-plore-hoz** |
| Pro upsell hajtóerő | **Felhő = top fizetős-konverziós kiváltó** |
| Újrahasznosítás | **A meglévő RemoteFileSource absztrakció 100%-a** |
| Új szolgáltatók | **Drive + Dropbox + OneDrive élesben** |

### Újragenerálási prompt

```
Elemezze a Jupitert és tervezzen valódi felhőtárhelyet (Google Drive/Dropbox/OneDrive) a meglévő Felhő Hub vázra. Fedje le: (1) versenyhiány a Solid Explorer/FX/Files by Google-höz és fizetős-konverziós indoklás, (2) data/cloud AppAuth PKCE OAuth + szolgáltatónkénti OkHttp REST források a domain/remote/RemoteFileSource implementálásával, az SMB/SFTP/FTP/WebDAV mintájára a data/remote-ban, (3) bekötés ConnectionRepositoryImpl + CloudAccount + CredentialStore által, CloudHubViewModel élesítése, (4) paritás/upsell értéknövelési hatás, (5) újragenerálási meta-prompt. 1–5. pont struktúra.
```

---

## 3. Lokalizáció és Globális Elérés (12+ nyelv)

**Értékelési hatás: +€120k–€260k** · Ráfordítás: M (közepes) · Építhető a jelenlegi appban: roadmap

A Jupiter UI-szövegei jelenleg a feature/* Compose képernyőkön be vannak égetve. Ezek külső res/values/strings.xml-be emelése és res/values-<lang> fordítások (hu, de, es, fr, pt, it, nl, pl, ru, tr, ja, ko) szállítása a legmagasabb megtérülésű telepítésszám-szorzó egy globális fogyasztói segédprogram számára, és ez a lista legkisebb kockázatú kezdeményezése: csak szövegeket érint, így nincs viselkedési regressziós felület. Egy fájlkezelő eleve globális — minden Android-felhasználónak vannak fájljai — így a csak-angol korlát megszüntetése közvetlenül szélesíti a megcélozható telepítési bázist.

A bizonyíték a vezetők listázásaiban van. A Files by Google több tucat nyelven jelenik meg; a Solid Explorer és az X-plore erősen közösségi fordítású; a lokalizált áruházi listázások mérhetően növelik a telepítési konverziót nem angol piacokon (a Google Play saját lokalizációs útmutatása). Egy adatvédelem-központú alkalmazás esetében a lokalizáció bizalmi jelzés is olyan régiókban (DACH, Franciaország, Japán), ahol az adatkezelési elvárások magasak. Mivel a Jupiter bevétel előtti, egy nagyobb és globálisabb telepítési bázis az a nyersanyag, amelyre minden későbbi monetizációs kezdeményezés ráépül.

Technikai megközelítés: a feature/* Compose képernyők (HomeScreen.kt, SearchScreen.kt, VaultScreen.kt, SettingsScreen.kt, CloudHubScreen.kt és a többi) literáljainak kiemelése az app/src/main/res/values/strings.xml-be, lecserélve stringResource(R.string.…) hívásokra. Nyelvenkénti res/values-<lang>/strings.xml fájlok hozzáadása. A paraméterezett szövegek megfelelő Android formátum-erőforrásként a számokhoz és méretekhez. A munka mechanikus és párhuzamosítható, tisztán integrálódik a meglévő Compose/Material 3 UI-val, és részben gépi fordítással bootstrapelhető, majd a prioritásos nyelveken emberi felülvizsgálattal.

### Implementációs prompt (AI kódoló asszisztensbe beilleszthető)

1. Hozzon létre: app/src/main/res/values/strings.xml
2.   Beégetett literálok kiemelése a feature/* Compose képernyőkről:
3.     feature/home/HomeScreen.kt, feature/search/SearchScreen.kt,
4.     feature/vault/VaultScreen.kt, feature/settings/SettingsScreen.kt,
5.     feature/cloud/CloudHubScreen.kt, feature/cleanup/*Screen.kt, ...
6.   Literálok cseréje stringResource(R.string.key)-re
7. 
8. Nyelvenkénti erőforrás-könyvtárak:
9.   res/values-hu/strings.xml, res/values-de/strings.xml,
10.   res/values-es/, -fr/, -pt/, -it/, -nl/, -pl/, -ru/, -tr/, -ja/, -ko/
11. 
12. Plurals/format argumentumok a számokhoz és fájlméretekhez
13. Play Store listázás lokalizálása prioritásos piaconként
14. Csak-szöveg változtatás: nulla viselkedési regresszió

### Sikermetrikák

| Metrika | Cél |
|---|---|
| Megcélozható telepítési bázis | **Csak-angol -> 12+ nyelv** |
| Listázási konverzió-növekedés | **Magasabb nem angol piacokon** |
| Regressziós kockázat | **Minimális (csak szövegek)** |
| Bizalmi jelzés | **DACH/FR/JP adatvédelem-érzékeny piacok** |

### Újragenerálási prompt

```
Elemezze a Jupitert és tervezzen lokalizációs tervet 12+ nyelvre. Fedje le: (1) telepítésszám-szorzó indoklás egy globális segédprogramhoz, Files by Google/Solid Explorer lokalizáció és Play listázási konverzió alapján, (2) beégetett literálok kiemelése a feature/* Compose képernyőkről a res/values/strings.xml-be stringResource refaktorral, res/values-<lang> hozzáadása hu/de/es/fr/pt/it/...-hez, plurals/format kezelés, (3) regressziómentes csak-szöveg hatókör, (4) megcélozható-bázis értéknövelési hatás, (5) újragenerálási meta-prompt. 1–5. pont struktúra.
```

---

## 4. AI Pro Csomag (Claude) — Összegzés, Okos Átnevezés, Auto-Rendszerezés, Szemantikus Keresés

**Értékelési hatás: +€160k–€320k** · Ráfordítás: L (nagy) · Építhető a jelenlegi appban: roadmap

A Jupiter már szállít egy valódi, kulccsal kapuzott Claude asszisztenst: a feature/ai/AnthropicAiAssistant.kt implementálja az AiAssistant interfészt (okos átnevezési javaslatok, AiSuggestion-ként felszínre hozott javaslatok), a NoOpAiAssistant.kt biztonságos tartalékként, ha nincs kulcs konfigurálva. Ennek négypilléres AI Pro Csomaggá bővítése — dokumentum/mappa Összegzés, AI Okos Átnevezés, egyérintéses Auto-Rendszerezési javaslatok és természetes nyelvű Szemantikus Keresés — egy hozzáértő funkciót olyan kirakat-megkülönböztetővé alakít, amely egyetlen másik Android fájlkezelőnek sincs. Ez teszi a Jupitert narratívan egyedivé egy felvásárló számára, és a Pro/előfizetéses szint természetes horgonya (#1 kezdeményezés).

Egyetlen fősodorbeli Android fájlkezelő sem — Solid Explorer, X-plore, FX, Files by Google — szállít eszközön kapuzott, saját-kulccsal működő LLM akciókat. Ez valódi nyitott sáv. A terméki elv, amely biztonságossá és megbízhatóvá teszi, a 'megerősítés-alkalmazás-előtt, hallucináció nélkül': minden AI akció konkrét változtatást javasol (átnevezés, áthelyezési terv, összegzés), amelyet a felhasználó kifejezetten jóváhagy, mielőtt bármi megérintené a fájlrendszert. Egy adatvédelem-központú márka esetében az Anthropic-kulccsal kapuzott tervezés (nincs kulcs, nincs hívás; a felhasználó saját kulcsa) maga a bizalmi történet — az átláthatatlan felhő-AI ellentéte.

Technikai megközelítés: a meglévő feature/ai/AiAssistant.kt interfész bővítése summarize(fájl/mappa), proposeOrganization(fájlok) és semanticQuery(lekérdezés) függvényekkel, amelyek strukturált, megerősíthető terveket adnak vissza; implementáció az AnthropicAiAssistant.kt-ben az Anthropic Messages API-val és Claude tool-use-szal, a NoOpAiAssistant.kt-t szinkronban tartva. Az eredmények az AiAssistantScreen.kt / AiAssistantViewModel.kt-n keresztül jelennek meg, plusz egy univerzális-akció lap horog, hogy bármely kijelölt fájl AI akcióba irányuljon. A Szemantikus Keresés a feature/search/SearchViewModel.kt-be köt be (rangsorolás az eredmények tetején), az Auto-Rendszerezés a feature/cleanup-be. Minden akció egy előnézet, amelyet a felhasználó a meglévő fájlművelet-csővezetéken (data/file) keresztül alkalmaz.

### Implementációs prompt (AI kódoló asszisztensbe beilleszthető)

1. Bővítse a feature/ai/AiAssistant.kt interfészt:
2.   suspend fun summarize(item: FileItem): AppResult<String>
3.   suspend fun proposeOrganization(items: List<FileItem>): AppResult<OrganizePlan>
4.   suspend fun semanticQuery(query: String, candidates): AppResult<List<RankedHit>>
5.   (a meglévő okos átnevezés megtartása)
6. 
7. Implementáció: feature/ai/AnthropicAiAssistant.kt
8.   Anthropic Messages API + Claude tool-use
9.   Megerősítés-alkalmazás-előtt: terveket ad vissza, soha nem mutál automatikusan
10. No-op utak tükrözése: feature/ai/NoOpAiAssistant.kt
11. 
12. feature/ai/AiAssistantScreen.kt + AiAssistantViewModel.kt: 4 akció UI
13. Univerzális-akció lap horog: kijelölt fájl -> AI akció
14. 
15. feature/search/SearchViewModel.kt: szemantikus rangsoroló réteg az eredményeken
16. feature/cleanup: Auto-Rendszerezési javaslatok megjelenítése
17. Jóváhagyott változtatások a data/file fájlművelet-csővezetéken át
18. Kapuzás Feature.AI_SUITE mögé (jogosultság, #1 kezdeményezés)

### Sikermetrikák

| Metrika | Cél |
|---|---|
| Kategória-megkülönböztetés | **Egyetlen AI-natív Android fájlkezelő** |
| Pro/előfizetés horgony | **Kirakat fizetős-szint funkció** |
| Adatvédelmi tartás | **Saját-kulcs, megerősítés-előtt, hallucináció nélkül** |
| Újrahasznosítás | **A meglévő AnthropicAiAssistant bővítése** |

### Újragenerálási prompt

```
Elemezze a Jupitert és tervezzen AI Pro Csomagot (Claude) a meglévő kulccsal kapuzott AnthropicAiAssistant bővítésével. Fedje le: (1) miért egyedi megkülönböztető az AI-natív a Solid Explorer/X-plore/FX/Files by Google-höz és horgonyozza a Pro szintet, (2) a feature/ai/AiAssistant.kt bővítése summarize/proposeOrganization/semanticQuery-vel, implementáció az AnthropicAiAssistant.kt-ben Anthropic Messages API + tool-use által, NoOp tükör, AiAssistantScreen/ViewModel + univerzális-akció lap + SearchViewModel szemantikus rangsor + cleanup auto-rendszerezés, megerősítés-előtt a data/file-on át, (3) saját-kulcs adatvédelmi tartás, (4) megkülönböztetés/upsell értéknövelési hatás, (5) újragenerálási meta-prompt. 1–5. pont struktúra.
```

---

## 5. Kezdőképernyő Widgetek, App Parancsikonok és Gyorsbeállítás Csempe

**Értékelési hatás: +€90k–€180k** · Ráfordítás: M (közepes) · Építhető a jelenlegi appban: roadmap

A Jupiter jelenleg csak a saját activityjén belül él. A kezdőképernyőn és a rendszer-UI-ban való megjelenítése — egy Glance kezdőképernyő-widget (tárhelymérő + gyors hozzáférés), dinamikus és statikus app parancsikonok (Keresés / Tisztítás / Széf) és egy Gyorsbeállítás csempe egyérintéses tisztításhoz — növeli a napi aktív használatot és a megtartást bármilyen új alapfunkció nélkül. Ezek tiszta kiegészítések új komponenseken és a manifeszten keresztül, így nincs regressziós kockázat, és a Jupitert mindig jelenlévő rendszer-segédprogrammá teszik, nem olyan alkalmazássá, amelyet a felhasználónak meg kell jegyeznie, hogy megnyissa.

Ez bevált megtartási minta a kategóriában. A Files by Google tárhelytisztító kártyát és értesítéseket jelenít meg; a Samsung My Files parancsikonokat tár fel; a widgetek jól dokumentált DAU-hajtóerő Androidon, mert az eszköz legértékesebb felületén tartják az alkalmazást. Egy segédprogramnál, amelynek alapfeladatai (hely felszabadítása, fájl keresése, széf megnyitása) gyorsak és ismétlődők, ezek egy érintésnyire helyezése a kezdőképernyőtől érdemben emeli az elköteleződést — és az elköteleződés az a metrika, amelyre minden későbbi monetizációs lépés ráépül.

Technikai megközelítés: új app/widget csomag az androidx.glance:glance-appwidget használatával egy GlanceAppWidget plusz fogadójának felépítéséhez, a tárhelyállapotot a meglévő StorageAnalyticsRepository-ból olvasva, hogy a mérő valós legyen. Új app/shortcuts csomag statikus parancsikonokkal a res/xml-ben, plusz dinamikus ShortcutManager parancsikonok a Keresés, Tisztítás és Széf számára. Új app/tile csomag egy TileService-szel egyérintéses tisztításhoz, amely újrahasznosítja a feature/cleanup logikát. A widget-fogadó, a parancsikonok és a TileService regisztrálása az AndroidManifest.xml-ben. A Glance Compose-natív, így új paradigma nélkül illeszkedik a meglévő UI stackbe.

### Implementációs prompt (AI kódoló asszisztensbe beilleszthető)

1. Hozzon létre: app/widget/JupiterGlanceWidget.kt (GlanceAppWidget)
2.   Tárhelymérő + gyors hozzáférés gombok
3.   Állapot olvasása a domain StorageAnalyticsRepository-ból
4. Hozzon létre: app/widget/JupiterWidgetReceiver.kt (GlanceAppWidgetReceiver)
5. 
6. Hozzon létre: app/shortcuts/JupiterShortcuts.kt
7.   Statikus parancsikonok: res/xml/shortcuts.xml (Keresés/Tisztítás/Széf)
8.   Dinamikus ShortcutManager parancsikonok (legutóbbi helyek)
9. 
10. Hozzon létre: app/tile/CleanupTileService.kt (TileService)
11.   Egyérintéses tisztítás; feature/cleanup logika újrahasznosítása
12. 
13. AndroidManifest.xml: fogadó, <meta-data> parancsikonok, TileService regisztrálása
14. Gradle: androidx.glance:glance-appwidget hozzáadása
15. Tiszta kiegészítések: nincs regresszió a meglévő képernyőkön

### Sikermetrikák

| Metrika | Cél |
|---|---|
| DAU / megtartás | **Mindig jelenlévő kezdőképernyő-felület** |
| Feladatig eltelt idő | **Egy érintés a Tisztítás/Keresés/Széf felé** |
| Regressziós kockázat | **Nulla (additív komponensek)** |
| Újrahasznosítás | **StorageAnalyticsRepository + feature/cleanup** |

### Újragenerálási prompt

```
Elemezze a Jupitert és tervezzen kezdőképernyő-widgeteket, app parancsikonokat és Gyorsbeállítás csempét. Fedje le: (1) DAU/megtartás indoklás a Files by Google/Samsung My Files megjelenítés alapján, (2) app/widget Glance GlanceAppWidget+fogadó a StorageAnalyticsRepository olvasásával, app/shortcuts statikus+dinamikus ShortcutManager (Keresés/Tisztítás/Széf), app/tile TileService a feature/cleanup újrahasznosításával, manifeszt regisztráció, androidx.glance:glance-appwidget hozzáadása, (3) additív nulla-regressziós hatókör, (4) elköteleződési értéknövelési hatás, (5) újragenerálási meta-prompt. 1–5. pont struktúra.
```

---

## 6. Adatvédelem-központú Opt-in Termékanalitika és Összeomlás-jelentés

**Értékelési hatás: +€80k–€170k** · Ráfordítás: S (kicsi) · Építhető a jelenlegi appban: roadmap

A Jupiter nem tudja javítani az aktivációt, megtartást vagy konverziót, ha nem tudja mérni őket — ám egy szabványos analitika SDK ráerőltetése elárulná az adatvédelem-központú ígéretet, amely az alapvető megkülönböztetője. A megoldás egy szállítósemleges, opt-in (alapból KI), PII-mentes analitika és összeomlás-absztrakció: egy vékony Analytics interfész NoOpAnalytics implementációval alapértelmezettként, egy kifejezett opt-in kapu mögött, amelyet a felhasználó a beállításokban irányít. Ez kockázatmentesíti az eszközt egy felvásárló számára (akinek tölcsér- és stabilitásadatra van szüksége az átvilágításban), miközben épségben tartja a reklámmentes, sötét minták nélküli márkát.

A piaci valóság az, hogy a Files by Google, a Solid Explorer és a legtöbb versenytárs alapból gyűjt analitikát; a Jupiter álláspontja ehelyett az lehet, hogy 'mérés csak a kifejezett beleegyezéseddel, soha semmilyen személyes adat, harmadik fél követők nélkül'. Ez az álláspont önmagában is marketingelhető, és igazodik a GDPR-by-design elvárásokhoz a Jupiter legerősebb adatvédelem-érzékeny piacain. Lényeges, hogy az absztrakció bedugaszolható: ma NoOp-ot szállít (szó szerint semmit sem mér, amíg nincs opt-in), de a varrat lehetővé teszi egy adatvédelmet tisztelő nyelő (vagy önállóan üzemeltetett végpont) későbbi hozzáadását a hívási helyek érintése nélkül.

Technikai megközelítés: új core/analytics csomag egy Analytics interfésszel, egy AnalyticsEvent modellel (típusos tölcsér-események, szabad szöveges PII nélkül), egy NoOpAnalytics alapértelmezett implementációval Hilten keresztül, és egy opt-in kapuval, amely a beleegyezést a data/preferences-ből olvassa. Egyetlen adatvédelmi kapcsoló a feature/settings-ben (SettingsScreen.kt / SettingsViewModel.kt), alapból KI. Kulcsfontosságú tölcsérpontok műszerezése — onboarding befejezése, első böngészés, széf feloldása, felhőcsatlakozás, AI akció, Pro paywall megtekintés/vásárlás — típusos eseményhívásokkal, amelyek no-op-ok, amíg a beleegyezés nem megadott. Az összeomlás-jelentés ugyanazt az opt-in, PII-mentes szerződést követi.

### Implementációs prompt (AI kódoló asszisztensbe beilleszthető)

1. Hozzon létre: core/analytics/Analytics.kt (interfész: track(event), setEnabled)
2. Hozzon létre: core/analytics/AnalyticsEvent.kt (típusos események, NINCS szabad PII)
3. Hozzon létre: core/analytics/NoOpAnalytics.kt (alapértelmezett Hilt binding)
4. Hozzon létre: core/analytics/AnalyticsConsentGate.kt (opt-in olvasása)
5. 
6. feature/settings/SettingsScreen.kt + SettingsViewModel.kt:
7.   Adatvédelmi kapcsoló, ALAPBÓL KI, data/preferences-en perzisztálva
8. 
9. Típusos tölcsér-események műszerezése (no-op a beleegyezésig):
10.   onboarding_completed, first_browse, vault_unlocked,
11.   cloud_connected, ai_action_used, paywall_viewed, pro_purchased
12. 
13. Összeomlás-jelentés: ugyanaz az opt-in + PII-mentes szerződés, bedugaszolható nyelő
14. Alapból NoOp: szó szerint semmit sem mér az opt-inig

### Sikermetrikák

| Metrika | Cél |
|---|---|
| Mérhetőség | **Aktiváció/megtartás/konverzió láthatóvá válik** |
| Felvásárlói átvilágítás | **Tölcsér + stabilitás adat igény szerint** |
| Adatvédelmi tartás | **Opt-in alapból KI, nincs PII, nincs követő** |
| Alapviselkedés | **NoOp (nulla adat) a beleegyezésig** |

### Újragenerálási prompt

```
Elemezze a Jupitert és tervezzen adatvédelem-központú opt-in analitikát és összeomlás-jelentést. Fedje le: (1) miért szükséges a mérés, mégsem szabad megtörnie az adatvédelem-központú ígéretet, kontraszt az alapból-gyűjtő versenytársakkal, (2) core/analytics Analytics interfész + AnalyticsEvent + NoOpAnalytics alapértelmezett + beleegyezési kapu a data/preferences-en át, feature/settings kapcsoló alapból KI, típusos tölcsér-műszerezés no-op a beleegyezésig, összeomlás-jelentés ugyanaz a szerződés, (3) GDPR-by-design tartás, (4) átvilágítás/mérhetőség értéknövelési hatás, (5) újragenerálási meta-prompt. 1–5. pont struktúra.
```

---

## 7. Aktivációs és Megtartási Hurok (Onboarding Tölcsér, Újdonságok, Értékelés, Visszacsábítás)

**Értékelési hatás: +€90k–€190k** · Ráfordítás: M (közepes) · Építhető a jelenlegi appban: roadmap

A Jupiternek van onboarding folyamata (feature/onboarding/OnboardingScreen.kt) és kezdőképernyője, de nincs zárt hurka, amely az első indítást aktivált, visszatérő, értékelő felhasználóvá alakítaná. Ennek a huroknak a szorosabbra húzása — műszerezett onboarding befejezés, egy Újdonságok lap verzióemeléskor, egy alkalmazáson belüli értékelési kérés a megfelelő pillanatban kiváltva, és egy opt-in WorkManager visszacsábító értesítés (pl. 'X GB visszanyerhető') — nagy hatású, additív növekedési lépés. Közvetlenül emeli a két metrikát, amely minden monetizációs kezdeményezés alatt összeadódik: az aktivációs arányt és a 7/30 napos megtartást.

Ezek a sikeres fogyasztói alkalmazások szabványos, bevált mechanikái, és a Files by Google a kézenfekvő összehasonlítás: tisztítási értesítésekkel és tárhelykártyákkal ösztönzi a felhasználókat a visszatérésre. A Jupiternek már megvan az alapja az adatvédelmet tisztelő változatához — egy WorkManager automatizációs rendszer és egy AppStateDataStore — így a visszacsábítás szigorúan opt-in és érték-vezérelt lehet ('3,2 GB-ot nyerhetsz vissza'), nem pedig spamszerű. A Google Play alkalmazáson belüli értékelési API-ja a csúcselégedettség pillanatában rögzíti az értékeléseket, ami emeli az áruházi értékelést és így az organikus telepítési konverziót.

Technikai megközelítés: az onboarding befejezésének műszerezése a feature/onboarding-ban (jelző írása a data/preferences/AppStateDataStore.kt-be) és analitikában (#6 kezdeményezés). Új feature/whatsnew csomag egy Compose lappal, amely észlelt verziókód-emeléskor jelenik meg, az AppStateDataStore-ban tárolt utoljára-látott verzióval kapuzva. com.google.android.play:review-ktx hozzáadása és az alkalmazáson belüli értékelési kérés kiváltása egy pozitív akció után (pl. sikeres tisztítás). A meglévő WorkManager automatizáció újrahasznosítása egy opt-in visszacsábító értesítés ütemezésére, amelyet a StorageAnalyticsRepository-ból számít, alapból KI és tiszteli az analitikai beleegyezési kaput.

### Implementációs prompt (AI kódoló asszisztensbe beilleszthető)

1. feature/onboarding: befejezési jelző írása + analitikai esemény kibocsátása
2.   Perzisztálás: data/preferences/AppStateDataStore.kt
3. 
4. Hozzon létre: feature/whatsnew/WhatsNewSheet.kt (Compose)
5.   Megjelenítés verziókód-emeléskor; utoljára-látott követése AppStateDataStore-ban
6.   Megjelenítés innen: feature/home/HomeScreen.kt
7. 
8. Alkalmazáson belüli értékelés: com.google.android.play:review-ktx hozzáadása
9.   Kiváltás egy pozitív akció után (sikeres tisztítás/átvitel)
10. 
11. Visszacsábító értesítés (opt-in, alapból KI):
12.   WorkManager automatizáció újrahasznosítása (data/automation)
13.   'X GB visszanyerhető' számítása a StorageAnalyticsRepository-ból
14.   Analitikai/értesítési beleegyezés tiszteletben tartása
15. Additív: nincs változás a meglévő böngészési/fájl folyamatokon

### Sikermetrikák

| Metrika | Cél |
|---|---|
| Aktivációs arány | **Műszerezett onboarding befejezés** |
| 7/30 napos megtartás | **Újdonságok + visszacsábítás növekedés** |
| Áruházi értékelés | **Alkalmazáson belüli értékelés csúcselégedettségnél** |
| Tartás | **Visszacsábítás opt-in, érték-vezérelt, alapból KI** |

### Újragenerálási prompt

```
Elemezze a Jupitert és tervezzen aktivációs és megtartási hurkot. Fedje le: (1) miért emeli a hurok zárása az aktivációt és a 7/30 napos megtartást, Files by Google összehasonlítás, (2) onboarding befejezés műszerezése AppStateDataStore + analitika által, feature/whatsnew Compose lap verzióemeléskor, com.google.android.play:review-ktx alkalmazáson belüli értékelés pozitív akció után, opt-in WorkManager visszacsábító értesítés a StorageAnalyticsRepository-ból alapból KI, (3) additív adatvédelmet tisztelő hatókör, (4) aktiváció/megtartás értéknövelési hatás, (5) újragenerálási meta-prompt. 1–5. pont struktúra.
```

---

## 8. Nagy Teljesítményű Keresési Index (Room FTS) + Mentett Keresések + Tartalomkeresés

**Értékelési hatás: +€110k–€220k** · Ráfordítás: L (nagy) · Építhető a jelenlegi appban: roadmap

A Jupiter jelenlegi keresése (feature/search/SearchViewModel.kt) igény szerint járja be a fájlrendszert — helyes, de lassú nagy tárhelynél, és nem képes azonnali vagy tartalomkeresésre. A keresés Room FTS4 indexszel való alátámasztása, WorkManageren keresztül inkrementálisan felépítve, a keresést azonnali, tárhelyeken átívelő képességgé alakítja, és valódi teljesítményi védőárkot ad a Jupiternek. A mentett/legutóbbi keresések és az opt-in szöveg-tartalomkeresés hozzáadása olyan funkcióvá kerekíti, amelyért az erős felhasználók kifejezetten választanak fájlkezelőt. Additív: az index a meglévő SearchViewModel mellett ül, amely visszaeshet fájlrendszer-bejárásra, amíg az index be nem melegszik.

A keresési minőség elsődleges tengely, amelyen az erős-felhasználói fájlkezelők versenyeznek. Az X-plore és a Solid Explorer gyors, mély keresésen különböztet meg; a Files by Google szándékosan sekélyen tartja a keresést. Egy FTS-alátámasztott azonnali keresés minden köteten, plusz tartalomkeresés szövegfájlokon belül, pontosan az a képesség, amely egy 'épp elég jó' kezelőt napi eszközzé alakít — és az azonnali keresés kézzelfogható, demonstrálható wow-pillanat az áruházi képernyőképeken és értékelésekben. A mentett keresések ezenfelül olyan tárolt felhasználói állapotot hoznak létre, amely növeli a váltási költséget és a ragadósságot.

Technikai megközelítés: új data/search csomag egy Room adatbázissal, egy FileIndexEntity-vel, egy FTS4 virtuális táblával, és egy indexelő Workerrel, amely WorkManageren keresztül inkrementálisan tölti fel és frissíti az indexet (az app már használ WorkManagert automatizációra). Domain repository-n keresztül kitéve, hogy a feature/search/SearchViewModel.kt először az indexet kérdezze le, és csak szükség esetén essen vissza élő bejárásra. A mentett/legutóbbi keresések ugyanabban a tárolóban perzisztálódnak, és a feature/search/SearchScreen.kt-ben jelennek meg. Az opt-in tartalomkeresés a támogatott fájltípusok kinyert szövegét indexeli, kapuzva és adatvédelmi szempontból egyértelműen feltüntetve. Az androidx.room függőségek hozzáadása.

### Implementációs prompt (AI kódoló asszisztensbe beilleszthető)

1. Hozzon létre: data/search/JupiterSearchDatabase.kt (Room)
2. Hozzon létre: data/search/FileIndexEntity.kt + FTS4 virtuális tábla (FileIndexFts)
3. Hozzon létre: data/search/FileIndexer.kt (WorkManager Worker, inkrementális)
4.   Index felépítése + frissítése minden tárhelykötetben
5. Hozzon létre: data/search/SavedSearchEntity.kt (mentett/legutóbbi keresések)
6. 
7. Domain repository hozzáadása (pl. SearchIndexRepository) a DB felett
8. 
9. feature/search/SearchViewModel.kt:
10.   Először az FTS index lekérdezése; visszaesés fájlrendszer-bejárásra, ha hideg
11.   Mentett/legutóbbi keresések; opt-in tartalomkeresés kapcsoló
12. feature/search/SearchScreen.kt: azonnali eredmények + mentett keresések UI
13. 
14. Opt-in szöveg-tartalomkeresés: egyértelműen feltüntetve, adatvédelmi kapuval
15. Gradle: androidx.room hozzáadása (runtime, ktx, compiler)

### Sikermetrikák

| Metrika | Cél |
|---|---|
| Keresési késleltetés | **Fájlrendszer-bejárás -> azonnali FTS keresés** |
| Erős-felhasználói védőárok | **Tartalom + mentett keresés a Files by Google-höz** |
| Ragadósság | **Mentett keresések = tárolt felhasználói állapot** |
| Újrahasznosítás | **Additív a meglévő SearchViewModel mellett** |

### Újragenerálási prompt

```
Elemezze a Jupitert és tervezzen Room FTS keresési indexet mentett keresésekkel és tartalomkereséssel. Fedje le: (1) miért erős-felhasználói védőárok az azonnali/tartalomkeresés az X-plore/Solid Explorer/Files by Google-höz, (2) data/search Room DB + FileIndexEntity + FTS4 + WorkManager indexelő Worker, domain repository, SearchViewModel először az indexet kérdezi le fájlrendszer-visszaeséssel, mentett/legutóbbi keresések + opt-in tartalomkeresés a SearchScreen-ben, androidx.room hozzáadása, (3) additív regressziómentes hatókör, (4) teljesítmény/ragadósság értéknövelési hatás, (5) újragenerálási meta-prompt. 1–5. pont struktúra.
```

---

## 9. Személyre Szabás és Témázás (Material You Kiemelőszín, Ikon/Sűrűség Témák, Egyedi Kezdőképernyő)

**Értékelési hatás: +€70k–€150k** · Ráfordítás: M (közepes) · Építhető a jelenlegi appban: roadmap

A Jupiternek már van JupiterTheme rendszere (ui/theme/Theme.kt, Color.kt). Erre építve, hogy a felhasználók testre szabhassák a megjelenést — egy kiemelőszín-választó, világos/sötét/AMOLED-fekete módok, sűrűségopciók és néhány ikontéma — erős megtartási és identitásfunkció, valamint tiszta Pro upsell, miközben teljesen additív marad a meglévő témakódhoz. A személyre szabás egy eszközt 'az én eszközömmé' alakít: akik testreszabnak egy alkalmazást, beleruháznak és kevésbé morzsolódnak le, és Androidon a témázás egy csiszolt, prémium-érzetű segédprogram elvárt jele.

A témázás elismert megkülönböztető és monetizációs felület pontosan ebben a kategóriában. A Solid Explorer jól ismert testreszabható témáiról és kiemelőszíneiről; sok fájlkezelő a fizetős szintje mögé zárja a prémium témákat. A Material You dinamikus szín szintén minőségi jelzés, amelyet a modern Android-felhasználók kifejezetten keresnek. Az AMOLED-valódi-fekete felkínálása közvetlenül vonzó az erős-felhasználói és adatvédelem-tudatos personáknak (akkumulátor, OLED, minimalizmus), és egy kis válogatott ikon/sűrűség témakészlet kézzelfogható kozmetikai upsellt ad a Pro szintnek (#1 kezdeményezés), amely semmibe sem kerül a fájlrendszer-rétegben.

Technikai megközelítés: a ui/theme/Theme.kt és Color.kt bővítése, hogy egy futásidejű témakonfigurációt (kiemelő mag-szín, sötét-mód mód az AMOLED feketét is beleértve, sűrűség) fogadjon egy Flow-ból. A választások perzisztálása a data/preferences/SettingsDataStore.kt-ben és kitétele a feature/settings/SettingsScreen.kt + SettingsViewModel.kt-n keresztül élő előnézetű kiemelőszín-választóval és Material You kapcsolóval. A konfiguráció alkalmazása a Compose fa tetején a MainActivity.kt-ben, hogy az egész app a választott témára komponálódjon újra. A prémium témák/ikoncsomagok egyszerűen Feature-rel kapuzottak (jogosultság, #1 kezdeményezés); az alaptéma ingyenes marad.

### Implementációs prompt (AI kódoló asszisztensbe beilleszthető)

1. Bővítse a ui/theme/Theme.kt-t:
2.   Futásidejű ThemeConfig fogadása (accentSeed, darkMode incl. AMOLED, density)
3.   Material You dinamikus szín támogatás
4. Bővítse a ui/theme/Color.kt-t: kiemelő-magozott paletták + valódi-fekete felületek
5. 
6. Perzisztálás: data/preferences/SettingsDataStore.kt (Flow-ként kitéve)
7. 
8. feature/settings/SettingsScreen.kt + SettingsViewModel.kt:
9.   Kiemelőszín-választó (élő előnézet), világos/sötét/AMOLED, sűrűség,
10.   ikontéma-választó, Material You kapcsoló
11. 
12. MainActivity.kt: ThemeConfig begyűjtése, alkalmazás a Compose fa tetején
13. Prémium ikon/sűrűség témák Feature-rel kapuzva (jogosultság #1)
14. Additív: az alaptéma ingyenes marad; nincs regresszió

### Sikermetrikák

| Metrika | Cél |
|---|---|
| Megtartás / identitás | **Testreszabott app = alacsonyabb lemorzsolódás** |
| Pro kozmetikai upsell | **Prémium témák/ikoncsomagok** |
| Persona illeszkedés | **AMOLED fekete az erős/adatvédelem-felhasználóknak** |
| Újrahasznosítás | **A meglévő JupiterTheme bővítése** |

### Újragenerálási prompt

```
Elemezze a Jupitert és tervezzen személyre szabást és témázást a meglévő JupiterTheme-re. Fedje le: (1) megtartás/identitás + Pro upsell indoklás a Solid Explorer témák és Material You elvárások alapján, (2) ui/theme/Theme.kt + Color.kt bővítése futásidejű ThemeConfig-hoz (kiemelő mag, sötét/AMOLED, sűrűség, Material You), perzisztálás SettingsDataStore-on át, SettingsScreen/ViewModel élő-előnézetű választó, alkalmazás a MainActivity-ben, prémium témák Feature-rel kapuzva, (3) additív regressziómentes hatókör, (4) megtartás/upsell értéknövelési hatás, (5) újragenerálási meta-prompt. 1–5. pont struktúra.
```

---

## 10. Megosztás és Együttműködési Hub ("Mentés a Jupiterbe" Fogadás, Gyorsmegosztás, SAF DocumentsProvider)

**Értékelési hatás: +€100k–€200k** · Ráfordítás: M (közepes) · Építhető a jelenlegi appban: roadmap

A Jupiter jelenleg egy célállomás, amelyet a felhasználó megnyit; egyben annak a hubnak is kell lennie, amelyen más alkalmazások átirányítanak. ACTION_SEND / SEND_MULTIPLE fogadási célként való regisztrálása ('Mentés a Jupiterbe'), egy Storage Access Framework DocumentsProvider hozzáadása, hogy bármely alkalmazás fájlválasztója böngészhesse a Jupiter tárhelyét és széfjét, valamint gyorsmegosztás/eszközre-küldés hozzáadása, megjeleníti a Jupitert minden más alkalmazás megosztási lapján és rendszer-fájlválasztójában. Ez virálissá tesz (minden megosztás felfedezési felület) és ragadóssá (más alkalmazások most a Jupitertől mint szolgáltatótól függenek), és additív — a manifeszten plusz új komponenseken keresztül megvalósítva, a tényleges munkát a meglévő data/file réteg végzi.

A mély OS-együttműködés pontosan az, ahogy a vezetők beágyazzák magukat. A Files by Google és a Samsung My Files széles körben regisztrál megosztási célként és dokumentumszolgáltatóként; egy SAF DocumentsProvider különösen lehetővé teszi, hogy a Jupiter megjelenjen Gmail mellékletekben, dokumentumszerkesztőkben és bármely alkalmazásban, amely megnyitja a rendszerválasztót — a Jupitert infrastruktúrává alakítva, nem önálló alkalmazássá. Egy adatvédelem-központú kezelő esetében a 'Mentés a Jupiterbe' fogadási célként való működés (akár egyenesen a titkosított széfbe) szintén megkülönböztetett, bizalom-vezérelt belépési pont, amelyet egyetlen reklámtámogatott versenytárs sem tud ilyen tisztán keretezni.

Technikai megközelítés: ACTION_SEND és ACTION_SEND_MULTIPLE intent-filterek és egy <provider> deklarálása az AndroidManifest.xml-ben. Új feature/share csomag egy ReceiveShareActivity / ReceiveShareScreen-nel (Compose), amely lehetővé teszi a felhasználónak egy célállomás kiválasztását — beleértve a széfet — és a meglévő data/file csővezetéken keresztül ír. Új data/saf csomag, amely a JupiterDocumentsProvider-t implementálja (a DocumentsProvider-t kiterjesztve) ugyanazon fájlréteg felett, hogy más alkalmazások böngészhessék és megnyithassák a Jupiter tartalmát a rendszerválasztón keresztül. A gyorsmegosztás / eszközre-küldés a meglévő NanoHTTPD Wi-Fi szerverre és átviteli stackre épül a közeli eszközök közötti átadáshoz.

### Implementációs prompt (AI kódoló asszisztensbe beilleszthető)

1. AndroidManifest.xml:
2.   <intent-filter> ACTION_SEND + ACTION_SEND_MULTIPLE (Mentés a Jupiterbe)
3.   <provider> a SAF DocumentsProvider-hez
4. 
5. Hozzon létre: feature/share/ReceiveShareActivity.kt + ReceiveShareScreen.kt
6.   Célállomás-választó a titkosított széfet is beleértve
7.   Bejövő elemek írása a meglévő data/file csővezetéken át
8. 
9. Hozzon létre: data/saf/JupiterDocumentsProvider.kt (DocumentsProvider kiterjesztése)
10.   Jupiter tárhely kitétele más alkalmazások rendszer-fájlválasztóinak
11.   Ugyanarra a data/file rétegre épülve
12. 
13. Gyorsmegosztás / eszközre-küldés:
14.   NanoHTTPD Wi-Fi szerver + data/transfer stack újrahasznosítása
15. Additív a manifeszten + új komponenseken át; data/file újrahasznosítva

### Sikermetrikák

| Metrika | Cél |
|---|---|
| Viralitás | **Megjelenik minden alkalmazás megosztási lapján** |
| Együttműködési ragadósság | **SAF szolgáltató a rendszer-fájlválasztókban** |
| Bizalmi belépési pont | **'Mentés a Jupiterbe' egyenesen a széfbe** |
| Újrahasznosítás | **data/file + NanoHTTPD Wi-Fi szerver + átvitel** |

### Újragenerálási prompt

```
Elemezze a Jupitert és tervezzen Megosztás és Együttműködési Hubot. Fedje le: (1) viralitás/ragadósság indoklás a Files by Google/Samsung My Files megosztási-cél és dokumentumszolgáltató beágyazás alapján, (2) AndroidManifest ACTION_SEND/SEND_MULTIPLE intent-filterek + <provider>, feature/share ReceiveShareActivity/Screen a data/file-on át írva a széfet is beleértve, data/saf JupiterDocumentsProvider a DocumentsProvider kiterjesztésével a data/file felett, gyorsmegosztás NanoHTTPD Wi-Fi szerver + átvitel újrahasznosításával, (3) additív manifeszt + új komponensek hatókör, (4) viralitás/ragadósság értéknövelési hatás, (5) újragenerálási meta-prompt. 1–5. pont struktúra.
```

---

## Portfólió összesen

A 10 kezdeményezés hatástartományának összege: **+€1.39M–€2.81M**. A(z) **€140k–€320k** kiindulási értékre vetítve a portfólió a(z) **€1.4M–€3.2M** cél-értékelést támasztja alá (8–10×).
