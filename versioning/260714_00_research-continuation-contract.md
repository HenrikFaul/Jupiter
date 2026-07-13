# Jupiter — folytatási szerződés a növekedési és biztonsági kutatás után

**Cél:** a 2026-07-14 kutatási dokumentumokból csak külön jóváhagyott, regressziómentes fejlesztési körben legyen implementáció.

## Kötelező sorrend

1. A `docs/security/2026-07-14-mobile-security-audit.md` P0 tételeit külön változáscsomagban zárd le.
2. Csak ezek után implementáld a Storage Truth Ledger / Explainable Cleanup felületet és állapotmodellt.
3. Utána az Arrival Lens valódi MediaStore-delta, hash, idempotens decision outbox és értesítési folyamata jöhet.
4. Jupiter Relay csak hitelesített, rövid életű és titkosított sessionnel épülhet; a régi névtelen HTTP directory server nem lehet a végállapot.

## P0 implementációs minimum

### Release signing

- A `release` nem hivatkozhat debug signing confignak vagy repóba commitolt release titokra.
- A titok hiánya build-time hiba legyen, ne debug fallback.
- CI release csak védett main/tag és jóváhagyott környezet után publikálhat.
- Bizonyíték: `apksigner --print-certs`, workflow-policy teszt és artifact hash/provenance.

### CredentialStore

- EncryptedSharedPreferences/Keystore hiba esetén fail closed: nincs sima SharedPreferences fallback, nincs automatikus titokmásolás, nincs titok logban.
- A korábbi fallback fájl jelenlétét kezeld dokumentált, felhasználónak érthető re-auth folyamattal.
- Remote jelszó Compose-ban csak átmeneti memóriaállapot lehet, soha `rememberSaveable`/SavedStateHandle/navigation argument.
- Bizonyíték: kényszerített crypto-hiba unit/instrumentációs teszt, backup és process-death negatív teszt.

### Wi-Fi Transfer / Jupiter Relay

- Új sessionhez explicit helyi felhasználói jóváhagyás, QR-ből származó nagy entrópiájú egyszeri titok, lejárat és fájlmanifest kell.
- Párosítatlan/lejárt kliens sem listázni, sem letölteni nem tud; manifesten kívüli fájl soha nem elérhető.
- Transport védett; nem elegendő egy rejtett URL vagy statikus PIN.
- A session leállítására, timeoutjára, alkalmazás háttérbe kerülésére és hibára a port/hozzáférés egyértelműen záródjon.
- Bizonyíték: két klienssel hálózati negatív teszt, timeout teszt, packet-inspection és megszakítási regresszió.

## Funkcionális invariánsok

- Exact deduplikációt csak teljes content hash dönthet el. Vizuális egyezés review-only; nem törlési felhatalmazás.
- A keeper mindig védett; Select all/Deselect all csak az aktív, látható duplikátum-scope eltávolítható példányait érintheti.
- A platform `total`/`free` kapacitás nem kategóriaösszegből képzett becslés; az elemzett shared-file terület külön kommunikálandó.
- Recycle Bin, restore, Vault import/export/biometria, SFTP/FTPS/SMB/Drive, meglévő fájlmegosztás és background indexelés regressziótesztet kap.
- Soha nincs háttérben, csendben végzett törlés, áthelyezés vagy feltöltés.

## Kötelező bizonyíték fejlesztési körönként

1. Friss `main`: `git pull --ff-only origin main`.
2. Régi changelog/versioning dokumentumok és releváns Forge lessonök áttekintése.
3. Célzott unit tesztek + `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --no-build-cache`.
4. Kockázatos adatfolyamokhoz eszköz/instrumentációs bizonyíték: backup, process death, release certificate, két LAN kliens, permission flow.
5. `git diff --check`; csak a kör fájljainak stage-elése; append-only `changelog.md` és új részletes `versioning/` rekord; commit és push közvetlenül `main`-re.

## Tiltások

- Nincs destructive Room fallback, plaintext secret fallback, csendes protokoll-downgrade, automatikus duplikátumtörlés vagy nem párosított LAN share.
- Ne állíts „MASVS compliant”, „end-to-end secure”, „instant duplicate alert” vagy „production signed” címkét konkrét, megismételhető bizonyíték nélkül.
- Ne keverd ugyanabba a release-be a security migrációt és a látványos UI redesignot, ha ez elfedi a regressziós okokat.
