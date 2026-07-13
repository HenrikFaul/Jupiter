# Jupiter — Android mobilbiztonsági és kiadási audit

**Dátum:** 2026-07-14
**Vizsgált állapot:** `main`, Jupiter 0.56.0 (`versionCode 8`)
**Módszer:** forráskód-, manifest-, Gradle-, backup-konfiguráció- és GitHub Actions statikus átvizsgálás.
**Nem történt:** alkalmazáskód-módosítás, dinamikus behatolási teszt, külső célpont szkennelése, függőség-CVE adatbázis lekérdezés vagy eszközoldali tanúsítvány-ellenőrzés. Ezért minden „ellenőrzendő” jelölés valódi, még elvégzendő validációt jelent.

## 1. Vezetői összegzés

Három **P0 / stop-ship** probléma látható statikus bizonyítékkal:

1. A `release` build a repóban lévő, közismert debug keystore-ral van aláírva.
2. Titkos hitelesítőadatoknál a titkosított tároló hiba esetén sima `SharedPreferences`-re esik vissza.
3. A Wi-Fi Transfer egy hitelesítés nélküli HTTP-szervert indít, amely aktív állapotban LAN klienseknek Downloads-t szolgáltathat ki.

Ezek mellett backup-, remote-protokoll-, CI-, archivum- és Vault-hardening hiányok találhatók. A meglévő pozitív kontrollok (nem exportált `FileProvider`, titkosított Vault, fail-closed Vault PIN, Vault backup-kizárás, canonical-path ellenőrzések) nem kompenzálják a P0 tételeket.

**Kiadási döntés:** amíg `JUP-SEC-001`, `JUP-SEC-002` és `JUP-SEC-003` nincs javítva és eszközön ellenőrizve, ne legyen olyan publikus kommunikáció, amely a kiadást produkciós biztonságúként, a remote titkokat titkosítottként, vagy a Wi-Fi transzfert privátként állítja be.

## 2. Hatókör, fenyegetési modell és bizonyítéki jelölések

| Eszköz / adat | Feltételezett támadó | Releváns kár |
| --- | --- | --- |
| Távoli jelszavak, Drive token, AI API-kulcs | eszközbackuphoz, app-adatokhoz vagy memóriához hozzáférő támadó | fiókátvétel, költség, személyes adatok elérése |
| Downloads és más megosztott fájlok | azonos vagy rosszindulatú LAN kliens | jogosulatlan fájlletöltés, adatvédelmi incidens |
| Vault fájlok és metaadatok | ellopott eszköz, képernyőkép, backup vagy lokális kompromittálás | érzékeny fájl- és metaadatszivárgás |
| Archívumok | rosszindulatú küldő vagy fájl | path traversal, tárhely-/CPU-kimerítés |
| Kiadási APK és CI | kompromittált workflow/action vagy rosszindulatú build | hamis vagy manipulált alkalmazás terjesztése |

**Bizonyítékok:** `S` = közvetlen statikus forrásbizonyíték; `V` = eszközön/CI-ben még validálandó; `P` = policy/compliance bizonyítékot kell bemutatni. Súlyosság a valószínűség × hatás alapján, a felhasználói adatokhoz mért konzervatív besorolás.

## 3. Megállapítások

### JUP-SEC-001 — P0 / Kritikus: produkciós release debug kulccsal van aláírva

**Bizonyíték (S):** `app/build.gradle.kts` a `debug` signing configot a repóban tárolt `app/keystore/jupiter-debug.keystore` fájlra és az ismert debug hitelesítőadatokra állítja. A `release` buildType `signingConfig = signingConfigs.getByName("debug")` beállítást használja. A CI a release APK-t publikálja.

**Támadási forgatókönyv:** bárki előállíthat egy másik APK-t az ismert debug kulccsal. Közvetlen Play-disztribúció esetén a Play saját aláírása részben más réteg, de publikus oldalletöltésnél és „release APK” kommunikációnál nincs hiteles produkciós kiadói identitás.

**Javítás:** külön, nem repóban tárolt release/upload kulcs; CI secret vagy Play App Signing; a release build debug keystore-ra mutató útja buildben hibázzon; változatlan signing cert fingerprintet és provenance-ot dokumentálni kell. A publikus release workflow csak védett `main`/tag után fusson.

**Ellenőrzés (V):** `apksigner verify --print-certs` a CI-artifakton, tanúsítvány-fingerprint allowlist, release build sikertelen titok nélkül, valamint reprodukálható release attestation.

### JUP-SEC-002 — P0 / Kritikus: titkos credential tárolás fail-open módon plaintextre esik vissza

**Bizonyíték (S):** `data/remote/CredentialStore.kt` titkosított preference létrehozási hiba esetén `getSharedPreferences("jupiter_remote_credentials_fallback", ...)` tárolót használ. Ugyanez a komponens kezeli a távoli jelszavakat, OAuth tokeneket és az AI API-kulcsot. A `VaultPinRecordStore` ezzel szemben helyesen fail-closed.

**Támadási forgatókönyv:** Android Keystore / EncryptedSharedPreferences hiba vagy eszközállapot-változás után a jelszó vagy token titkosítás nélkül marad perzisztálva; ezt backup, root, hibakeresés vagy más kompromittálási lánc könnyebben kiolvashatja.

**Javítás:** a fallbackot törölni; hiba esetén visszaadott, kezelhető `AppResult.Failure` + új hitelesítés szükséges. A régi fallback store-t biztonságosan fel kell deríteni, törölni/migrálni és felhasználót új belépésre kérni; a folyamat nem logolhat titkot.

**Ellenőrzés (V):** Keystore-hibát szimuláló teszt: nem jön létre plaintext preference és nincs elmentett secret; backup-ellenőrzés; log-szkennelés; Drive/AI/remote reconnect regresszióteszt.

### JUP-SEC-003 — P0 / Kritikus: Wi-Fi Transfer hitelesítés nélküli, titkosítatlan LAN fájlkiszolgáló

**Bizonyíték (S):** `data/transfer/WifiTransferServer.kt` a helyi hálózaton HTTP-kiszolgálót indít, directory listinget és közvetlen child-fájl streamet szolgál ki. A statikus útvonal-kanonikalizálás véd traversal ellen, de nincs párosítási titok, session, felhasználói hitelesítés vagy TLS. `WifiTransferViewModel` Downloads-alapú gyökeret adhat át.

**Támadási forgatókönyv:** ugyanazon Wi-Fi-n lévő kliens az aktív szervert eléri, böngészheti és letöltheti a kiszolgált gyökér fájljait. Nyílt/megfertőzött LAN-on ez azonnali adatvédelmi kockázat.

**Javítás:** a jelenlegi szervert ne nevezze a termék „secure transfernek”. Új protokoll: QR-ből származó egyszeri, nagy entrópiájú párosítási titok; szűk fájlmanifest; rövid lejárat; nem listázható alapértelmezés; session-szintű jóváhagyás; titkosított transport; megszakításkor és timeoutkor azonnali lezárás; auditálható session-állapot. A desktop segéd/PWA csak ezt használhatja.

**Ellenőrzés (V):** két külön LAN klienssel negatív teszt: párosítatlan kliens 401/403; lejárt token használhatatlan; manifesten kívüli path nem olvasható; forgalom nem tartalmaz fájlnevet vagy byte-ot plaintextben; timeout leállítja a portot.

### JUP-SEC-004 — P1 / Magas: backup szabályok nem zárnak ki minden credential- és indexadatot

**Bizonyíték (S):** `AndroidManifest.xml` backup engedélyezett és `dataExtractionRules`/`fullBackupContent` beállított. Az XML-ek a Vaultot kizárják, de a remote credential/DataStore/index tárolókra nincs explicit kizárás a statikus konfigurációban.

**Hatás:** a Vault jó irányban védett, de a csatlakozási metaadatok, tokenek vagy index egy része eszközátvitelbe/backupba kerülhet; ez különösen súlyos a JUP-SEC-002 plaintext fallback mellett.

**Javítás:** adatfolyam-leltár alapján explicit exclude vagy Android által támogatott end-to-end-encrypted backup-követelmény minden titokhoz és érzékeny indexhez. API 31+ `dataExtractionRules` és régebbi `fullBackupContent` párban karbantartandó. [Android backup best practices](https://developer.android.com/privacy-and-security/risks/backup-best-practices?authuser=14)

**Ellenőrzés (V):** device-to-device transfer és cloud backup restore teszt; ellenőrizni kell, hogy Vault, token, fallback és index nem jelenik meg jogosulatlan helyen.

### JUP-SEC-005 — P1 / Magas: remote jelszó Compose saved state-be kerülhet

**Bizonyíték (S):** `feature/remote/NasConnectionsScreen.kt` jelszómezőt `rememberSaveable` állapotban tart. A saved instance state konfiguráció- vagy folyamatújraindításkor Bundle-be kerülhet.

**Javítás:** jelszó csak átmeneti `remember` állapotban legyen; felhasználás után törlendő, ne kerüljön navigation argumentbe, ViewModel `SavedStateHandle`-be, logba vagy screenshotba. Titok tárolása kizárólag JUP-SEC-002 javítása utáni, fail-closed credential store-on keresztül.

**Ellenőrzés (V):** rotate/process death teszt, Bundle-inspection és log-szkennelés; a kapcsolat létrehozása továbbra is működik, de a jelszó nem restaurálódik.

### JUP-SEC-006 — P1 / Magas: nem biztonságos remote protokollok és első-kapcsolati bizalom

**Bizonyíték (S):** FTP kapcsolat létrehozható, WebDAV HTTP is elérhető egyes konfigurációkban; ezek Basic Authot és fájladatot lehetséges plaintexten visznek. FTPS `PROT P` használata pozitív kontroll. SFTP TOFU ismert host fájlt használ, de az első kulcs elfogadása még MITM kockázat.

**Javítás:** plaintext FTP/HTTP WebDAV alapértelmezésben tiltott vagy explicit, tartós vörös kockázati megerősítés mögött; biztonságos alapértelmezés SFTP/FTPS/HTTPS. SFTP-nél az első fingerprintet jelenítse meg és kérjen tudatos megerősítést; SMB signing/encryption policy külön forrás- és eszköztesztet igényel.

**Ellenőrzés (V):** proxyval ellenőrizni a TLS, tanúsítvány-hostname és FTPS adatcsatorna viselkedését; downgrade és host-key változás negatív tesztje.

### JUP-SEC-007 — P1 / Magas: CI/release pipeline nem biztonsági kiadási kapu

**Bizonyíték (S):** `.github/workflows/android.yml` minden jobnak `contents: write` jogosultságot ad, action tageket használ teljes SHA-pin helyett, és push esetén olyan ágakon is publikus release útvonalat futtat, amelyek nem kizárólag védett main release-ek. A release build `continue-on-error` környezetben is épülhet; nincs kötelező secret scan, dependency/CVE, SBOM, SAST, lint-siker vagy instrumentációs kapu.

**Javítás:** legkisebb jogosultság jobonként; commit-SHA-ra pinelt actionök; release csak védett tag/main + jóváhagyott environment; `continue-on-error` eltávolítása release-útról; kötelező secret scanning, dependency review/SBOM, Android Lint, SAST és tesztkapuk; release provenance és artifact-hash publikálása.

**Ellenőrzés (V):** szándékosan hibás release build nem publikálható; nem-main push nem ír release-t; policy check blokkol; artifact hash és signing cert ellenőrizhető.

### JUP-SEC-008 — P1 / Magas: archive extraction kimerítés és RAR validáció nincs lezárva

**Bizonyíték (S):** ZIP/tar/7z útvonalai canonical-path ellenőrzést használnak; RAR esetén a backend extraction közvetlenebb hívása látható. Nincs egységes, dokumentált maximum entries / kibontott byte / tömörítési arány korlát minden formátumra.

**Hatás:** archive bomb CPU-, tárhely- vagy memória-kimerítést okozhat; RAR útvonalbiztonsága backend-feltételezés marad, amíg nem bizonyított.

**Javítás:** közös `ArchiveSafetyPolicy`: célgyökér canonical ellenőrzése minden entry után, maximum entry-szám, kibontott bájt, arány, nested depth és timeout; részleges eredmény rollback/karantén. Format- és támadási-fixture tesztmátrix.

**Ellenőrzés (V):** Zip Slip, TAR symlink, RAR traversal, 7z bomb és nagy expansion ratio fixturek; nincs gyökéren kívüli fájl, a limit elérése kontrollált hibát ad.

### JUP-SEC-009 — P2 / Közepes: Vault metaadat és képernyővédelmi hardening hiányos

**Bizonyíték (S):** Vault bytes `EncryptedFile`/`MasterKey` alatt vannak, a PIN rekord fail-closed módon védett és a Vault backupból kizárt. A `.meta` fájl viszont app-private plaintext metaadatot tarthat; nem látható route-szintű `FLAG_SECURE` alkalmazás a Vault/PIN képernyőn.

**Javítás:** Vault metaadatok a titkosított konténerben vagy külön titkosítva; Vault route aktív állapotában képernyőkép/recents capture tiltása, route elhagyásakor visszaállítva; accessibility kommunikációval és teszttel.

**Ellenőrzés (V):** screenshot/recents viselkedés, metaadat offline olvashatóság, import/export/visszaállítás regresszió.

### JUP-SEC-010 — P2 / Közepes: széles FileProvider path scope felülvizsgálandó

**Bizonyíték (S):** a FileProvider nem exportált és URI grantot használ, ami jó. `file_paths.xml` ugyanakkor a teljes internal `files` és external path gyökeret regisztrálja.

**Javítás:** ideiglenes, dedikált share-cache/exports könyvtárra szűkíteni, ahol ez a megosztási funkcióval összeegyeztethető; grant visszavonási és Vault-path negatív tesztek.

**Ellenőrzés (V):** minden megosztási út működik; Vault és nem kiválasztott belső fájlra nem állítható elő URI; receiver csak explicit granttal olvas.

### JUP-SEC-011 — P2 / Közepes: függőség- és SBOM-kontroll hiányzik

**Bizonyíték (S):** nincs ellenőrzött CVE/SBOM pipeline, az `androidx.security.crypto` alpha verziót használ. A statikus audit nem állít konkrét CVE-t, mert erre nem futott függőség-adatbázis vizsgálat.

**Javítás:** Gradle dependency locking/verification, CycloneDX SPDX SBOM, rendszeres dependabot/renovate vagy kontrollált felülvizsgálat, OSV/Google Play SDK Index és licence scan; security library stabil támogatási állapotának felülvizsgálata.

**Ellenőrzés (V):** buildben generált SBOM, ismert kritikus CVE-k release-blocking szabálya, verziójóváhagyási napló.

### JUP-SEC-012 — P1 / Compliance: Android érzékeny engedélyekhez policy-bizonyíték szükséges

**Bizonyíték (S/P):** a manifest többek között `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS` és médiaengedélyeket kér. A file-manager use case engedélyezett lehet, ha ez a core funkció, de a statikus repó nem bizonyítja a Play Console deklarációt, a valós használati indoklást vagy az aktuális privacy disclosure-t.

**Javítás:** engedélyenként adatminimálási leltár; a nem core engedély eltávolítása vagy funkcionalitás szerinti késleltetett kérés; Play declaration, privacy policy és in-app indoklás összehangolása. [Google Play All files access policy](https://support.google.com/googleplay/android-developer/answer/10467955?hl=en-GB)

**Ellenőrzés (P/V):** Play Console nyilatkozat, valós API-útvonal és UI indoklás audit, clean install engedély-kérés teszt.

## 4. Meglévő pozitív kontrollok

| Kontroll | Statikus állapot | Korlát |
| --- | --- | --- |
| Vault fájlbájtok | `EncryptedFile` + `MasterKey` | metaadat és képernyőkép-hardening külön szükséges |
| Vault PIN | Keystore-backed, PBKDF2, random salt, constant-time összevetés, fail-closed | eszközteszt továbbra szükséges |
| Android exportált komponensek | Launcher `MainActivity` exportált; `FileProvider`, receiver és service nem exportált | manifest merge és futásidejű intent teszt legyen része CI-nek |
| File sharing | `FileProvider`, nem raw `file://` | a path scope túl széles lehet |
| Backup | Vault explicit kizárva | credential/index kizárása nincs lezárva |
| Archívum | ZIP/tar/7z canonical-path védelem | egységes limit és RAR igazolás hiányzik |
| FTPS/SFTP | FTPS privát adatcsatorna; SFTP known-hosts/TOFU alap | downgrade és első-kulcs UX megoldandó |

Az exportált komponensek döntéseinek tudatosnak kell lenniük; Android szerint az exportált komponens más app által pontos class névvel indítható. [Android exported components guidance](https://developer.android.com/privacy-and-security/risks/android-exported)

## 5. Prioritásos javítási terv

| Időablak | Kötelező eredmény | Elfogadási bizonyíték |
| --- | --- | --- |
| P0, következő kódolási kör | release signing leválasztása a debug kulcsról; fail-closed CredentialStore; Wi-Fi Transfer session-párosítás és titkosítási terv/implementáció | release cert, Keystore-hibateszt, kétklienses LAN negatív teszt |
| P1, utána | backup/saved-state/remote protocol/CI/archive policy | backup restore, proxy, archive fixture és workflow policy teszt |
| P2, hardening | Vault screenshot/metaadat, FileProvider scope, dependency/SBOM, permission audit | célzott UI/instrumentációs és pipeline evidence |

**Kötelező regressziós invariánsok:** Vault import/export és biometric unlock; meglévő SFTP/FTPS/SMB/Drive kapcsolatok; fájlmegosztás; archive kezelés; Recycle Bin; background indexing; exact dedup. A titkosítási hiba soha nem fordulhat át csendes, gyengébb tárolásba vagy háttérben továbbfutó fájlműveletbe.

## 6. Javasolt ellenőrzési mátrix

| Réteg | Kötelező ellenőrzés |
| --- | --- |
| Build/release | release keystore absence test, `apksigner`, artifact checksum, provenance, protected-tag workflow |
| Unit | CredentialStore fail-closed, password state policy, archive safety policy, URL/protocol allowlist |
| Instrumentation | backup/restore, permission flows, Vault `FLAG_SECURE`, FileProvider grants, WorkManager/session timeout |
| Hálózat | Wi-Fi pairing, TLS/transport, FTP/HTTP downgrade block, SFTP fingerprint, FTPS protected data channel |
| Adatvédelem | log secret scan, local-store inventory, consent/retention/deletion review |
| Supply chain | SBOM, licence, CVE/SCA, SHA-pinned Actions, least-privilege workflow review |

## 7. Szabvány- és policy-kapcsolat

Az audit háttérmodellje az OWASP MASVS Storage, Crypto, Auth, Network, Platform, Code, Resilience és Privacy kategóriái. Ez nem MASVS tanúsítási állítás; az ahhoz szükséges teljes teszt- és bizonyítékcsomag nincs elvégezve. [OWASP MASVS](https://mas.owasp.org/MASVS/)

## 8. Következő engedélykérés

Ez a dokumentum szándékosan nem módosította a termékkódot. A következő fejlesztési kör előtt külön jóváhagyás szükséges arra, hogy a P0 javításokat és a szükséges teszt-/CI-változásokat implementáljuk. A javítások release- és credential-migrációs kockázatot érintenek, ezért egyetlen „design” körbe nem szabad őket csendben belekeverni.
