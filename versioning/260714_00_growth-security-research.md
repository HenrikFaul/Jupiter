# Jupiter — 2026-07-14 növekedési, innovációs és mobilbiztonsági kutatási rekord

**Állapot:** dokumentációs/döntés-előkészítő kör, nem alkalmazáskiadás
**Kiinduló verzió:** Jupiter 0.56.0 (`versionCode 8`)
**Ág:** `main`
**Kódváltozás:** nincs

## Felhasználói kérés

Külső, más projektekhez tartozó BI, growth strategy, crazy innovations és security-vulnerability-audit mintamappák módszertanának átnézése után készüljön Jupiter-specifikus növekedési/innovációs kutatás és biztonsági audit. A mappák csak példák; sem a funkcióik, sem a következtetéseik nem vehetők át automatikusan. Ebben a körben kizárólag elemzés és dokumentáció készülhet, funkcionális regresszió nélkül.

## Kötelező előolvasás és scope

- `README.md`, `changelog.md`, a versioning sorozat releváns legfrissebb rekordjai és `docs/PRODUCT_STRATEGY.md`.
- `C:\Work\Github\panellako\BI_FRAMEWORK`, `crazy_innovations`, `growth_strategy`, `security-vulnerability-audit`: módszertani minták.
- Jupiter kód/manifest/Gradle/CI statikus ellenőrzése; nem futott élő hálózati támadás vagy külső célpont-szkennelés.

## Készült artefaktumok

1. `docs/research/2026-07-14-growth-and-crazy-innovations.md`
   - bizonyítékszintek, öt kreatív kutatási iteráció és 12 jelölt kezdeményezés rangsora;
   - vezető ajánlások: Explainable Cleanup, Download Arrival Lens és a csak biztonsági előfeltétel után szállítható Jupiter Relay;
   - célzott UI-koncepciók, opt-in mérési specifikáció, monetizációs és döntési kapuk.
2. `docs/security/2026-07-14-mobile-security-audit.md`
   - hatókör- és fenyegetésmodell;
   - bizonyítékkal alátámasztott P0/P1/P2 megállapítások, javítási sorrend, pozitív kontrollok és ellenőrzési mátrix.

## Auditból következő kötelező prioritás

| Prioritás | Megállapítás | Kötelező következő lépés |
| --- | --- | --- |
| P0 | `release` build debug keystore-t használ | production/upload signing elválasztása, CI secret/Play App Signing és cert-ellenőrzés |
| P0 | `CredentialStore` titkosítási hiba esetén plaintext preference-re vált | fail-closed hibaág, fallback adatok biztonságos kezelése, új hitelesítés |
| P0 | Wi-Fi Transfer hitelesítés nélküli HTTP-szerver | QR-alapú egyszeri session, szűk manifest, lejárat és titkosított transport |
| P1 | backup, saved state, plaintext remote protocol, CI, archive policy hiányai | célzott hardening + unit/instrumentációs/hálózati tesztek |

## Termékstratégiai döntés

Jupiter nem általános „cleaner” irányba bővül: az értékígéret a helyi-first, magyarázható és visszafordítható tárhely-intelligencia. A biztonság nem marketingréteg: a P0 javítások a Relay, a remote kapcsolatok és a Vault hitelességének előfeltételei. Nincs jogosultság arra, hogy ezek a javítások ebben a dokumentációs körben csendben implementálásra kerüljenek.

## Nem-regressziós szerződés

- Nem módosult `app/`, `AndroidManifest.xml`, Gradle buildlogika, dependency, UI vagy futásidejű funkcionalitás.
- Nem változott exact/visual deduplikáció, keeper-védelem, Storage Analytics, Arrival alert, Recycle Bin, Vault, remote kapcsolat vagy automatizálás.
- A dokumentumok nem állítanak nem futtatott dinamikus tesztet, CVE-vizsgálatot vagy MASVS megfelelést.

## Validáció

- A dokumentumok belső és elsődleges külső forráslinkjei ellenőrizve.
- `git diff --check` a dokumentációs patch után kötelező.
- Mivel nincs Kotlin/Gradle/Android erőforrás változás, új APK/build/test futtatása nem dokumentumkörhöz illő bizonyíték; a következő kódolási körben a módosított útvonalak teljes build/test/instrumentációs kaput kapnak.

## Lessons captured

Két hordozható Android biztonsági tanulság kerül a Forge lessons rendszerbe: debug kulcs nem lehet publikus release aláírója; kriptográfiai tároló hiba esetén credential nem eshet vissza plaintext saved state-be vagy preference-be.
