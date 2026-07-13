# Jupiter — növekedési és „Crazy Innovations” kutatási kör

**Dátum:** 2026-07-14
**Állapot:** döntés-előkészítő kutatás; ebben a körben nem történt alkalmazáskód-, manifest-, függőség- vagy UI-módosítás.
**Kiindulópont:** Jupiter 0.56.0, `main` ág.
**Nem cél:** értékelési, bevételi vagy piacméret-számok kitalálása. A méréshez szükséges események többsége még nincs instrumentálva.

## 1. Módszer és bizonyítéki szint

Az átnézett külső mappák csak módszertani minták voltak, nem Jupiterre vonatkozó követelmények és nem átvett kódok:

| Minta | Átvett módszertani elem | Jupiterre alkalmazott korlát |
| --- | --- | --- |
| `BI_FRAMEWORK` | bizonyítékalapú állítás, pontos mérőszám-definíció, adatminőség és ismeretlenek jelölése | nincs meglévő termékhasználati adat, ezért nincs konverziós vagy bevételi állítás |
| `growth_strategy` | sok ötlet generálása, hatás/moat/megvalósíthatóság szerinti rangsor, mérhető hipotézis | a pontszámok termék-döntési heurisztikák, nem piackutatási tények |
| `crazy_innovations` | konkrét képernyőre és feladatra célzott, öt iterációs kreatív kutatás | nincs öncélú látványelem; hozzáférhetőség, teljesítmény és biztonság elsődleges |
| `security-vulnerability-audit` | bizonyíték + támadási forgatókönyv + javítás + ellenőrzés szerkezet | a Jupiter-specifikus megállapítások külön, statikus auditban szerepelnek |

**Forrásbizalom:** a repóban lévő funkcióállapot közepes vagy magas bizalmú, mert statikus kódolvasásból származik. Külső versenytárs-funkciók az elsődleges gyártói dokumentációkra épülnek. A felhasználói igény, a piacméret, a megtartás és a fizetési hajlandóság jelenleg **nem bizonyított**, ezért validálandó hipotézis.

## 2. Stratégiai helyzetkép

A Jupiter már nem egy üres file-manager-váz: van indexelés, exact és vizuális duplikáció-keresés, tárolóelemzés, Recycle Bin, Vault, automatizálás, távoli források és Wi-Fi transzfer. A meglévő `PRODUCT_STRATEGY.md` helyesen a „találd meg bármit, szabadíts fel helyet biztonságosan” éket jelöli ki.

Az alap elvárás azonban már magas: a Samsung My Files tárhelyelemzést és duplikáció-/nagy fájl jellegű takarítást kínál, a Google Files az eredeti példány jelölésével segít a duplikátumok felülvizsgálatában, a Solid Explorer pedig eszköz-, felhő- és hálózati elérést, valamint titkosítást ad. [Samsung My Files](https://www.samsung.com/uk/support/mobile-devices/how-to-use-my-files/), [Google Files duplicate cleanup](https://support.google.com/files/answer/9764075?hl=en), [Solid Explorer](https://play.google.com/store/apps/details?id=pl.solidexplorer2)

**Következtetés:** a „még egy cleaner” nem védhető pozíció. A Jupiter megkülönböztető ígérete legyen: **ellenőrizhető, helyi-first tárhely-intelligencia, amely megmagyarázza a javaslatát, megóvja a megtartandó példányt és minden kockázatos műveletet visszafordíthatóvá tesz.** Ezt megelőzi a biztonsági és kiadási bizalom helyreállítása; lásd a kapcsolódó [mobilbiztonsági auditot](../security/2026-07-14-mobile-security-audit.md).

## 3. Öt kreatív kutatási iteráció

| Iteráció | Kérdés | Eredmény | Miért nem elég önmagában? |
| --- | --- | --- | --- |
| 1. Feladat tisztázása | Mit akar a felhasználó valójában? | „Tudjam, mi foglal helyet, mi ismétlődik, és biztonságosan szabadítsak fel helyet.” | Egy diagram vagy fájllista nem mondja meg, mi a biztonságos következő lépés. |
| 2. Bizalom | Mi állítja meg a törlést? | A keeper-választás, a hasonlóság oka, a visszaállítás és a hatás bizonytalansága. | A százalékos „junk score” fekete doboz és bizalomromboló. |
| 3. Idő | Mikor értékes az index? | Közvetlenül új letöltés, képrögzítés vagy tárolófigyelmeztetés után. | Háttérben önállóan törölni vagy fájlt átküldeni nem elfogadható. |
| 4. Eszközök között | Melyik frikció visszatérő? | Telefonról laptopra/NAS-ra juttatás és archiválás. | A jelenlegi nyílt LAN-megosztás nem lehet termékígéret; előbb párosított, védett átvitellé kell váljon. |
| 5. Hosszú távú érték | Mitől nem egyszeri cleaner? | A felhasználó saját fájltörténete, biztonságos szabályai és kereshető helyi indexe. | Csak explicit jóváhagyással, lokálisan és auditálhatóan építhető. |

## 4. Kandidátusok és rangsor

A pontszám 1–5: **H** = felhasználói/üzleti hatás, **M** = megkülönböztethetőség (moat), **F** = megvalósíthatóság a jelenlegi Android-alapon, **B** = bizalmi/biztonsági illeszkedés. Nem pénzügyi előrejelzés.

| # | Kezdeményezés | H | M | F | B | Döntés |
| --- | --- | ---:| ---:| ---:| ---:| --- |
| 1 | Kiadási és adatvédelmi bizalmi kapu | 5 | 4 | 3 | 5 | **Előfeltétel / P0** |
| 2 | Jupiter Relay: QR-párosított, időkorlátos helyi átvitel | 5 | 5 | 3 | 5 | **Első építési jelölt** |
| 3 | Download Arrival Lens: új fájl azonnali duplikáció-/hasonlóság-ellenőrzése | 5 | 4 | 4 | 5 | **Első építési jelölt** |
| 4 | Explainable Cleanup: keeper-ok, bizonyíték, előnézet és visszaállítás | 5 | 4 | 4 | 5 | **Első építési jelölt** |
| 5 | Storage Truth Ledger: platform-kapacitás, elemzett terület és nem elemezhető terület külön | 4 | 4 | 4 | 5 | Következő |
| 6 | Tárhely-előrejelzés és „mi változott?” idővonal | 4 | 4 | 3 | 4 | Következő |
| 7 | Privacy Health Dashboard | 4 | 4 | 3 | 5 | Következő |
| 8 | Helyi OCR + dokumentum-keresés | 5 | 5 | 2 | 4 | Későbbi, kutatandó |
| 9 | Projektcsomag / provenance ledger | 3 | 5 | 2 | 4 | Későbbi |
| 10 | Cross-device workspace sync | 5 | 5 | 1 | 2 | Későbbi; előbb konfliktus- és biztonsági modell |
| 11 | Szemantikus, on-device index | 5 | 5 | 1 | 4 | Későbbi; akkumulátor- és privátadat-korlátokkal |
| 12 | Bővített automatizálás természetes nyelvből | 3 | 3 | 3 | 2 | Csak a műveleti napló/szimuláció után |

## 5. A három ajánlott következő termékfogadás

### 5.1 Jupiter Relay — biztonságos „telefonról gépre” átadás

**Felhasználói mondat:** „Ezt a kiválasztott anyagot gyorsan át akarom vinni a gépemre, anélkül hogy bárki más a Wi-Fi-n látná.”

**Javasolt élmény:** a Transfer képernyő QR-kódos egyszeri párosítást indít. A felhasználó kiválasztja a fájlokat, beolvassa a QR-t a desktop segédben/PWA-ban, majd lát egy konkrét fájllistát, lejárati időt, eszköznevet és átviteli előrehaladást. Minden átviteli session külön jóváhagyott, megállítható, naplózott és rövid idő után lejár.

**Kötelező védőkorlát:** ez nem a jelenlegi nyílt HTTP-szerver újracímkézése. Előfeltétel a biztonsági audit P0 javítása: erős egyszeri titok, kifejezett párosítás, szűk fájlmanifest, lejárat, alapértelmezett listaelrejtés, titkosított kapcsolat és átvitel-lezárás. Addig a funkció nem kommunikálható „private” vagy „secure” átviteli megoldásként.

**MVP-sikerfeltétel:** a kiválasztott fájl sikeresen átmegy, a kapcsolat lejár, a nem párosított kliens nem listáz és nem tölthet le semmit; megszakított átvitel után az állapot egyértelmű.

### 5.2 Download Arrival Lens — az új fájl jelentése azonnal

**Felhasználói mondat:** „Most töltöttem le egy képet; mondd meg azonnal, hogy már megvan-e, és melyiket érdemes megtartani.”

**Javasolt élmény:** az értesítés nem csak azt mondja, hogy „duplicate found”, hanem a saját kézben tartott kártyára visz: `Exact duplicate`, `Very similar photo`, vagy `No safe match yet`. A kártya megmutatja a megtalált példányokat, a keeper indokát (azonos hash / felbontás / dátum / hely), és **csak** `Review` / `Keep both` / `Move selected to Recycle Bin` után történhet fájlművelet.

**Technikai termékszerződés:** exact duplikáció csak teljes tartalomhash-egyezés. Vizuális egyezés csak felülvizsgálati jelzés; nem törlési döntés. A MediaStore érkezési eseménynek idempotens, perzisztált döntési állapotot kell létrehoznia, hogy újraindítás után se legyen értesítési spam vagy elveszett riasztás.

**MVP-sikerfeltétel:** új fájl után a delta-indexelő legfeljebb egyszer hoz létre döntési rekordot; ha a hash még nem kész, a jelzés „checking” állapotú és később feloldódik, nem hamis negatív.

### 5.3 Explainable Cleanup — nem „takarító”, hanem bizonyítható döntéstámogató

**Felhasználói mondat:** „Nem akarom, hogy a rendszer helyettem döntsön; érteni akarom, mit nyerek és mit tartok meg.”

**Javasolt élmény:** a Duplicates képernyő első blokkja a teljes, összesített darabszámot mutatja (exact + visual review), de elkülöníti a biztonsági erősséget. Minden klaszterben a keeper zárolt, a kiválasztás csak a másolatokra vonatkozik, a `Select all` / `Deselect all` az aktív, látható scope-ra érvényes. Törlés előtt szimuláció látszik: darabszám, felszabaduló hely, megőrzött példányok, Recycle Bin-visszaállítási határidő.

**Kötelező védőkorlát:** nincs automatikus törlés, nincs csak név-, méret- vagy perceptuális hash alapján végleges törlés, és a minőségi keeper-feltétel mindig látható.

## 6. Célzott „Crazy Innovations” UI-koncepciók

Az alábbiak a meglévő designnyelvbe illesztendő, konkrét képernyő-szintű koncepciók. Nem új, dekoratív vizuális stílus és nem implementációs utasítás.

| Célképernyő | Koncepció | Érték | Hozzáférhetőségi és teljesítmény-korlát |
| --- | --- | --- | --- |
| Duplicate cleanup | **Similarity Constellation**: a csoportok világos klaszterkártyák; exact és visual jelzés külön jelölve, keeper „miért?” panellel | csökkenti a téves törlést és az algoritmusbizalmatlanságot | lista-alapú, nem Canvas-kötelező; TalkBack-sorrend és szöveges alternatíva; nincs folyamatos animáció |
| Storage Analytics | **Storage Truth Ledger**: `platform total`, `platform free`, `analysed shared files`, `outside accessible analysis` külön sorokban | megszünteti azt a látszatot, hogy a kategóriaösszeg a telefon teljes tárhelye | a számadat elsődleges, a chart másodlagos; a kapacitás forrása és mérési ideje látható |
| Transfer | **Relay Pairing Deck**: QR-párosítás, fájlmanifest, lejárat és megszakítás egy kártyán | az IP-címes megosztás helyett érthető és ellenőrizhető átadás | QR mellett rövid numerikus kód; nagy érintési célok; session állapot olvasható képernyőolvasóval |
| Home / Downloads | **Arrival Radar**: egyetlen csendes kártya az új, még ellenőrzés alatt álló fájlra | a duplikáció-ellenőrzés a megfelelő pillanatban jelenik meg | alapértelmezésben nem tolakodó; értesítési engedély nélkül az appon belül is elérhető |
| Vault | **Quiet View**: célzott képernyővédelem, bizalmi állapot és visszaállítási figyelmeztetés | a Vault valós védelemnek érződik, nem csak elrejtett mappának | csak Vault route-on aktív képernyővédelem; egyértelmű magyarázat a képernyőkép-korlátról |

## 7. Növekedési mérési terv — előbb adatminőség, utána optimalizálás

Jelenleg nincs elegendő, auditált használati adat. A következő események **tervezett specifikációk**, nem meglévő telemetria. Csak opt-in, aggregált formában, fájlnév, útvonal, tartalomhash, fájltartalom, e-mail és IP-cím nélkül gyűjthetők. A felhasználónak előbb érthető adatkezelési cél és kikapcsolási lehetőség kell.

| Metrika | Definíció | Előfeltétel / tiltott adat | Döntés, amelyet támogat |
| --- | --- | --- | --- |
| Aktiválási ráta | első futásból a sikeres első index-snapshotig jutó, opt-in kliensarány | nincs fájllista vagy útvonal | onboarding és permission-érthetőség |
| Első bizalomérték | első 24 órában legalább egy `Review` vagy visszafordítható Recycle Bin művelet | nincs törölt fájlnév vagy hash | Cleanup UX biztonsága |
| Arrival Lens lefedettség | új média-delta, amelyhez határidőn belül exact/visual/no-match döntés készült | csak számláló és késleltetési bucket | háttér-index és riasztási megbízhatóság |
| Relay sikerarány | explicit párosításból lezárt, ellenőrzött átvitel aránya | fájlnév, peer IP és QR-titok tilos | transfer stabilitása |
| 7/28 napos visszatérés | opt-in telepítések közül legalább egy értelmes helyi akciót végző eszközarány | nincs személyazonosító profil; törlés támogatott | megtartás, nem „screen time” |
| Pro vásárlási tölcsér | termék megtekintése → Play által visszaigazolt entitlement | csak Play által engedett, pszeudonim vásárlási állapot | ár és value-proposition; csak valódi Billing után |

Minden metrikának tartalmaznia kell `app_version`, `feature_flag_version`, eseményséma-verzió és adatminőségi státusz mezőt. A hibásan vagy részlegesen indexelt eszközt nem szabad a duplikációs termékminőség nevezőjébe tenni anélkül, hogy ezt jelölnénk.

## 8. Monetizációs döntés

**Most ne legyen fizetőfal a file-műveleteken, a biztonságos törlésen, a Recycle Binen vagy az alap indexen.** Ezek a bizalmi ék részei. A későbbi Jupiter Pro csak akkor indítható, ha a Google Play Billing valódi terméklekérdezést, vásárlásindítást, szerveroldali vagy megbízható entitlement-ellenőrzést és acknowledge folyamatot kap; a jelenlegi preview-felület nem elég. [Google Play Billing integration](https://developer.android.com/google/play/billing/integrate.html)

Potenciális, értékalapú Pro csomagok: több biztonságosan csatlakoztatott forrás, haladó on-device dokumentumindex, Relay desktop features, hosszabb műveleti előzmény és professzionális viewer. Az ár, csomagforma és konverziós elvárás későbbi, felhasználói kutatást igényel; ebben a dokumentumban nincs bevételi ígéret.

## 9. Döntési kapuk és nyitott kérdések

1. **Stop-ship kapu:** a security audit P0 tételei nélkül nincs új publikus release-ígéret a Vault, távoli hitelesítő vagy Wi-Fi transfer „secure” jelzőjével.
2. **Kutatási kapu:** 5–8 moderált, készülékkel végzett usability teszt kell a keeper-magyarazat, az Arrival Lens és a Relay-párosítás előtt. A cél a megértés és a hibaarány, nem a marketing-visszajelzés.
3. **Teljesítménykapu:** valós eszközön kell validálni a MediaStore-delta késleltetését és a 40k képes korpusz 95/99 percentilisét, mielőtt a „instant” és „brutál erős” kommunikáció megjelenik.
4. **Adatvédelmi kapu:** bármely telemetria, OCR, szemantikus index vagy AI előtt adatfolyam-térkép, megőrzési idő, kikapcsolás és export/törlés terv szükséges.
5. **Platform-kapu:** All files access és QUERY_ALL_PACKAGES használatát a Play Console-ban a valós core file-manager funkcióhoz kell deklarálni; a policy által engedett file-manager use case önmagában nem helyettesíti a pontos nyilatkozatot. [Google Play All files access policy](https://support.google.com/googleplay/android-developer/answer/10467955?hl=en-GB)

## 10. Javasolt sorrend

1. A biztonsági P0 hibák javítására külön, regressziótesztelt fejlesztési kör és release-processz.
2. Storage Truth Ledger + Explainable Cleanup állapot- és bizonyítékmodell; ez közvetlenül javítja a ma látható bizalomproblémákat.
3. Arrival Lens end-to-end, idempotens MediaStore döntési naplóval és valódi eszközteszttel.
4. Jupiter Relay biztonságos párosítási protokollja, csak utána a desktop/PWA élmény.
5. Opt-in mérési alap és kvalitatív usability kutatás.
6. Csak ezután OCR/szemantikus index, összetett automatizálás és cross-device sync.

Ez a sorrend nem írja felül a már leszállított funkciókat: a cél azok bizonyíthatóságának, érthetőségének és biztonságának mélyítése, nem új, kockázatos funkciók halmozása.
