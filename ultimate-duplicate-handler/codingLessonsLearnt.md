
# HIBAEMEGEKŐZÉSI ARCHITEKTÚRA — Proaktív & Preventív Megközelítés

# codingLessonsLearnt.md — ÖSSZEVONT KÖZÖS TUDÁSBÁZIS

## Célja
Ez a fájl **megelőzésre** fókuszál, nem csak javításra. Az AI **tudja előre**, hogyan kerüljön el minden ismert hibatípust a generálás során.

**Beolvasás: AI session elején, ELŐBB, mint bármilyen kódolás**

# LEGFONTOSABB: SEMMILYEN MÁR JÓL MŰKÖDŐ FUNKCIÓT NEM SZABAD ELRONTANI.

# FEJLESZTÉSI MÓDSZERTAN (MINDIG EZT KÖVESD ELŐSZÖR)

**KÖTELEZŐ indító prompt minden új fejlesztéshez / hibajavításhoz:**
1. **Legfontosabb, hogy semmilyen már jól működő funkciót ne ronts el.**
2. Nyisd meg és olvasd be a `codingLessonsLearnt.md`  fájlt, olvasd végig ezt a fájlt MIELŐTT bármit kódolnál. 
3. Az újonnan megfogalmazott üzleti követelmény vagy hibajavítás érdekében **szedd össze az összes szükséges tudást elsődlegesen hivatalos forrásokból / megbízható dokumentációból**.
4. A begyűjtött tudás alapján **detektáld a valós gyökérokot** a kódban / konfigurációban / futási láncban.
5. **Tesztek vagy célzott próbák alapján** hasonlíts össze legalább 2 megoldási koncepciót, és a **leghatékonyabbat / legkisebb regressziós kockázatút** válaszd.
6. A fejlesztést checklist-alapon végezd el.A fejlesztés során is olvashatod ezt a fájlt.
7. A fejlesztés végén kötelezően ellenőrizd:
   - minden kért javítás / fejlesztés elkészült-e,
   - minden korábbi fontos funkció megmaradt-e,
   - a `codingLessonsLearnt.md`-ben felsorolt korábbi hibaminták nem tértek-e vissza,
   - ha a projekt módszertana megköveteli, a `versioning/` mappába bekerült-e az új PDF + MD dokumentumpár.
8. Ellenőrizd, hogy az új kódod nem tartalmaz-e az itt felsorolt hibamintákat.
9. Ha új hibát találsz javítsd és a hibajelenséget a javítás módjával együtt AZONNAL appendeld a megfelelő kategóriába.
10. SOHA ne töröld a meglévő tartalmat — csak hozzáadni szabad.
11. SOHA ne hozz létre új fájlt ezzel a céllal — mindig ebbe a fájlba írd.


**Kötelező végellenőrző checklist minden szállítás előtt:**
- [ ] `codingLessonsLearnt.md` beolvasva
- [ ] szükséges forráskutatás / dokumentációellenőrzés megtörtént
- [ ] gyökérok detektálva
- [ ] legalább 2 megoldási koncepció kiértékelve
- [ ] a legkisebb regressziós kockázatú megoldás kiválasztva
- [ ] korábbi működő funkciók megléte double-checkelve
- [ ] új regresszió nem maradt bent
- [ ] changelog frissítve
- [ ] projekt-specifikus átadási artefaktumok elkészítve, ha kötelezőek


**Struktúra minden hiba bejegyzésnél:**
```md
### [HIBA-XXX] Rövid cím
- **Dátum**: Mikor fordult elő
- **Fájl**: Melyik fájlban volt / melyik logikai komponenshez tartozik
- **Hibaüzenet**: Pontos TypeScript/build/runtime/API error
- **Gyökérok**: Miért történt
- **Javítás**: Hogyan lett megoldva
- **Megelőzés**: Hogyan kerüld el a jövőben
```

**Megjegyzés az összevont tudásbázishoz:**
- A duplikált tanulságok csak EGYSZER szerepelnek.
- A több alkalmazásból származó, de azonos hibaminták összevonva kerültek be.
- Az alkalmazásfüggetlen külső API / integrációs hibák is bekerültek általános mintaként.



---

## I. ÁLTALÁNOS PREVENTÍV ELVEK

### P1: "Terv Előbb, Kód Utána" — Szerződés-alapú Fejlesztés

**Elvárás**:
- **Fázis 0**: Brief + terv elkészítése
- **Fázis 1**: Fájl-szerződések definiálása (aláírások, exportok)
- **Fázis 2**: Réteg-sorrendű generálás
- **Fázis 3**: Fájl-párok szimultán validálása
- **Fázis 4**: Végpont-lista → Frontend-prompt

**Megelőzés**:

```markdown
## TERV (Before Code)

### 1. Példa Fájl-szerkezet
- src/db/db.ts (singleton DatabaseSync export-al)
- src/models/Asset.ts (Asset interface export)
- src/models/Inventory.ts (InventoryItem interface)
- src/services/assetService.ts (EXPORTOK: getAssets(), createAsset())
- src/services/inventoryService.ts (EXPORTOK: getInventory(), updateStock())
- src/routes/api/assetRoute.ts (GET /assets, POST /assets)
- src/routes/api/inventoryRoute.ts (GET /inventory, PUT /inventory/:id)

### 2. Réteg-sorrendű párhuzamosítás
- **Réteg 1** (DB layer): db.ts
- **Réteg 2** (Models): Asset.ts, Inventory.ts (Réteg 1-e függenek)
- **Réteg 3** (Services): assetService.ts, inventoryService.ts (Réteg 2-e függenek)
- **Réteg 4** (Routes): assetRoute.ts, inventoryRoute.ts (Réteg 3-e függenek)
- **Réteg 5** (Server/Frontend): server.ts, public/app.js

### 3. Modulhatárok (Szerződések)

#### src/services/assetService.ts EXPORTÁL:
- getAssets(): Asset[]
- getAssetById(id: number): Asset | null
- createAsset(data: {name, type, location}): {changes: number, asset: Asset}

#### src/routes/api/assetRoute.ts IMPORTÁL innen:
- getAssets, getAssetById, createAsset
- Útvonalak: GET /assets, GET /assets/:id, POST /assets

### 4. Frontend Szerződés

#### public/index.html KELL:
- #app-header (fejléc)
- #app-main (fő tartalom)
- #asset-list (eszközök lista)
- #api-status (státusz jelvény)

#### public/app.js IMPORTÁL:
- Végpont: GET /api/assets → display asset list
- Végpont: GET /api/health → display status
```

**Előny**: A kód generálása után nincs meglepetés — a szerződések
már zölden validálódnak.

---

### P2: "Lint-Gate ELSŐ, Prompt-Szabály Másodlagos"

**Elvárás**: Az ismert hibatípusok **determinisztikus kapuval** kerülnek ki, mielőtt a generálás lezárulna.

**Megelőzés**: Minden generálási lépéshez hozzáadódik:

```javascript
// agent-core.js — LINT GATE
async function lintGateCheck(code, fileName) {
  const errors = [];
  const lines = code.split('\n');
  
  // 1. Python-komment (#) tiltása TS/JS-ben
  for (const [i, line] of lines.entries()) {
    if (/^\s*#[^!]/.test(line)) {  // # de nem #!/
      errors.push(`Line ${i+1}: Python-komment (#) TS/JS-ben — cseréld //`);
    }
  }
  
  // 2. Zárójel-egyensúly
  const opens = (code.match(/\{/g) || []).length;
  const closes = (code.match(/\}/g) || []).length;
  if (opens !== closes) {
    errors.push(`Brace mismatch: { × ${opens} vs } × ${closes}`);
  }
  
  // 3. node:sqlite paraméter-tömb tiltása
  for (const [i, line] of lines.entries()) {
    if (/\.(?:all|get|run)\s*\(\s*\[/.test(line)) {
      errors.push(`Line ${i+1}: node:sqlite paraméter array-ba burkolva — javítsd: .all([x]) → .all(x)`);
    }
  }
  
  // 4. Export-import konzisztencia (local fájloknál)
  if (fileName.includes('route')) {
    const imports = code.match(/import\s+\{([^}]+)\}/g) || [];
    for (const imp of imports) {
      const names = imp.match(/\w+/g);
      if (names && !names.some(n => ['default', 'export'].includes(n))) {
        // Exportok ellenőrzése majd
      }
    }
  }
  
  // ... további ellenőrzések
  
  if (errors.length > 0) {
    throw new Error(`LINT GATE HIBA:\n${errors.join('\n')}`);
  }
}
```

**Előny**: A model semmilyen úton sem tudja elkerülni az alapvető szintaktikai hibákat.

---

### P3: "Szerződés-Kapu Fájl-Párnál"

**Elvárás**: Ha egy route-fájl egy service-ből importál, az import ELŐBB validálódik, mielőtt a route generálódna.

**Megelőzés**:

```javascript
// Réteg 3 (Services) után
const assetServiceExports = parseExports('src/services/assetService.ts');
// Kimenete: { getAssets: true, getAssetById: true, createAsset: true, ... }

// Réteg 4 (Routes) generálásánál
const assetRoutePrompt = `
...
KÖTELEZŐ IMPORTOK Az assetService-ből:
${Object.keys(assetServiceExports).map(e => `- ${e}`).join('\n')}

TILOS: Másfajta function-okat importálni.
TILOS: Másodlagos nevek (pl. getAssets2, getAssetsLegacy).

Ha az import nem ezekből áll, a route NEM generálódhat.
...
`;
```

**Előny**: A szerződés-eltérés (TS2339) előre blokkolódik.

---

## II. SZINTAKTIKAI MEGELŐZÉS

### P4: TypeScript Strict Mode Kötelező

**Elvárás**: A `tsconfig.json` kötelezően tartalmazza:

```json
{
  "compilerOptions": {
    "strict": true,
    "noImplicitAny": true,
    "strictNullChecks": true,
    "strictFunctionTypes": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noImplicitReturns": true,
    "noFallthroughCasesInSwitch": true
  }
}
```

**Megelőzés**: Ezzel **előre** kiderül a kódban:
- Type-cast eltérések (TS2352)
- Hiányzó export-ok (TS2339)
- Null-reference hibák

**AI Lépés**:
```bash
# Session kezdésénél
npx tsc --noEmit  # Ha 0 error nem OK, új szekcióban javítódnak hibák
```

---

### P5: ESLint Konfigurációs Szabályok

**Elvárás**: Az ESLint konfigurációba bekerülnek az ismert hibamintákra:

```javascript
// .eslintrc.js
module.exports = {
  rules: {
    'no-comments': ['error', { pattern: '^\\s*#', flags: '' }], // Python-komment tiltás
    'no-restricted-syntax': [
      'error',
      {
        selector: "CallExpression[callee.property.name='all'][arguments.0.type='ArrayExpression']",
        message: 'node:sqlite .all([x]) tiltva — használd .all(x)',
      },
      {
        selector: "CallExpression[callee.property.name='run'][arguments.0.type='ArrayExpression']",
        message: 'node:sqlite .run([x,y]) tiltva — használd .run(x, y)',
      }
    ],
  },
};
```

**AI Lépés**:
```bash
npx eslint src/**/*.ts --fix  # Auto-fix ahol lehetséges
npx eslint src/**/*.ts        # Hátralévő hibák vizsgálata
```

---

### P6: Fájl-Befejezettség Kapu

**Elvárás**: Minden generált fájl végén explicit "EOF" vagy "zárójel-egyensúly"-ellenőrzés.

**Megelőzés**:

```javascript
function validateFileCompletion(code) {
  if (!code.trim()) {
    throw new Error('Üres fájl — regenerálás szükséges');
  }
  
  // Zárójel-mérleg
  const { '{': open, '}': close } = 
    code.split('').reduce((m, ch) => { m[ch]++; return m; }, {'{'0, '}'0});
  if (open !== close) {
    throw new Error(`Csonka fájl: ${open} nyit, ${close} záró zárójel`);
  }
  
  // Utolsó érvényes token?
  const trimmed = code.trim();
  if (!/[;}\]`"']$/.test(trimmed)) {
    throw new Error('Fájl csonka véget — regenerálás');
  }
}
```

---

## III. FÜGGŐSÉG-MEGELŐZÉS

### P7: npm ci (Clean Install) Kötelező

**Elvárás**: Minden futás kezdésénél friss, reprodukálható environment.

**Megelőzés**:

```bash
# Pipeline ELEJE
npm ci  # package-lock.json alapján
npm run build  # TypeScript fordítás
```

**Előny**: Nincs "de nálam működik" jelenség.

---

### P8: Dependency-Verzió Pinelése

**Elvárás**: A `package.json`-ben KELL:

```json
{
  "dependencies": {
    "express": "^4.19.2"
  },
  "devDependencies": {
    "typescript": "^5.5.0",
    "@types/node": "^22.0.0",
    "@types/express": "^4.17.21"
  }
}
```

**Megelőzés**: A major-verzió-frissülés (v4→v5) **eltörhet** dolgokat.

---

### P9: Séma & Type Definíciók Szinkronban

**Elvárás**: A Supabase/DB séma és a TypeScript interface-ek **azonosak**.

**Megelőzés**:

```typescript
// src/models/Asset.ts
export interface Asset {
  id: number;
  name: string;
  type: string;
  location: string;
  criticality: number;
  status: 'running' | 'stopped' | 'maintenance' | 'offline';
  created_at: string;
  updated_at: string;
}

// src/db/schema.sql
CREATE TABLE IF NOT EXISTS assets (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT UNIQUE NOT NULL,
  type TEXT NOT NULL,
  location TEXT NOT NULL,
  criticality INTEGER CHECK(criticality BETWEEN 1 AND 5) DEFAULT 3,
  status TEXT CHECK(status IN ('running', 'stopped', 'maintenance', 'offline')) DEFAULT 'running',
  created_at DATETIME DEFAULT datetime('now'),
  updated_at DATETIME DEFAULT datetime('now')
);
```

**AI Validation**:
```bash
# Model mezők vs. DB oszlopok
grep "^\s*\w*:" src/models/Asset.ts | \
  sed 's/.*:\s*\([^;]*\);.*/\1/' | sort > model_fields.txt

sqlite3 data/app.db ".schema assets" | \
  grep -o '^\s*\w*\s' | sed 's/\s//g' | sort > db_columns.txt

diff model_fields.txt db_columns.txt  # Üresnek kell lennie
```

---

## IV. FRONTEND MEGELŐZÉS

### P10: DOM-Szerződés Kötött Fájl

**Elvárás**: Az HTML és JS-generálás **kötelezően** egy közös szerződés-fájlból indul.

**Megelőzés**:

```json
// dom-contract.json (generálódik az HTML után)
{
  "required_ids": ["app-header", "app-main", "asset-list", "status"],
  "required_classes": ["container", "card"],
  "api_endpoints": [
    { "method": "GET", "path": "/api/health", "response_type": "HealthStatus" },
    { "method": "GET", "path": "/api/assets", "response_type": "Asset[]" }
  ],
  "external_libs": [
    { "name": "Chart.js", "cdn": "https://cdn.jsdelivr.net/npm/chart.js@4", "global": "Chart" }
  ]
}
```

**JS-generálási prompt**:
```
KÖTELEZŐ: Az alábbi id-k CSAK a HTML-ben vannak meghatározva, ezek léteznek garantáltan:
${requiredIds.map(id => `- #${id}`).join('\n')}

Az alábbi DOM-elemekre CSAK ezek közül kereshetsz: ${requiredIds.join(', ')}

Az alábbi API-végpontok érhetők el:
${apiEndpoints.map(e => `- ${e.method} ${e.path}`).join('\n')}
```

---

### P11: CSS/Stílus Függőség Előre Definiálva

**Elvárás**: Az `index.html`-ben a `<link>` és `<script>` sorrendje determinisztikus.

**Megelőzés**:

```html
<!-- public/index.html — kötött sorrend -->
<head>
  <!-- 1. CSS előbb -->
  <link rel="stylesheet" href="./style.css">
  
  <!-- 2. Külső lib-ek (CDN) -->
  <script src="https://cdn.jsdelivr.net/npm/chart.js@4"></script>
  
  <!-- 3. App JS (defer, így HTML után fut) -->
  <script defer src="./app.js"></script>
</head>
```

**AI Validálás**:
```bash
# App.js által használt global-ok
GLOBALS=$(grep -o "new [A-Z]\w*\|window\.[a-zA-Z_]*" public/app.js | \
  sed 's/new \|window\.//' | sort -u)

# Ezek a lib-ek betöltve vannak-e?
for lib in $GLOBALS; do
  grep -q "<script.*$lib\|<link.*$lib" public/index.html || \
    echo "⚠️ $lib nem betöltve az index.html-ben"
done
```

---

### P12: API-Végpont Lista Szinkronban

**Elvárás**: A frontend és backend API-végpontlistája **szinkronban kell lennie**.

**Megelőzés**:

```bash
# Backend generálása után: összes route export
find src/routes -name "*.ts" -exec grep -h "router\.\(get\|post\|put\|delete\)" {} \; | \
  grep -o "'[^']*'" | tr -d "'" | sort -u > backend_endpoints.txt

# Frontend: összes fetch/axios hívás
grep -o "fetch('[^']*'\|axios\(\(get\|post\|put\|delete\)('http://[^']*" public/app.js | \
  sed "s/.*'//" | sed "s/'$//" | sort -u > frontend_endpoints.txt

# Ellenőrzés
comm -13 backend_endpoints.txt frontend_endpoints.txt | \
  while read ep; do
    echo "⚠️ Frontend hívja, de backend NEM exports: $ep"
  done
```

---

## V. ADATBÁZIS MEGELŐZÉS

### P13: SQLite-Specifikus Szintaxisok Kötöttek

**Elvárás**: A séma KIZÁRÓLAG SQLite-syntaxiszt használ, NEM MySQL-t.

**Megelőzés — Tilos Minták**:

```sql
-- ❌ TILOS — MySQL-izmus
DEFAULT CURRENT_TIMESTAMP + INTERVAL 24 HOUR
DATETIME NOW()
AUTO_INCREMENT id

-- ✅ HELYES — SQLite
DEFAULT (datetime('now', '+1 day'))
datetime('now')
INTEGER PRIMARY KEY AUTOINCREMENT
```

**Kapu**:
```javascript
function validateSQLiteSyntax(sqlCode) {
  const forbiddenPatterns = [
    /INTERVAL\s+\d+\s+(HOUR|DAY|MONTH)/i,  // MySQL
    /NOW()/i,  // MySQL
    /AUTO_INCREMENT/i,  // MySQL
    /ON UPDATE CURRENT_TIMESTAMP/i,  // MySQL
  ];
  
  for (const pattern of forbiddenPatterns) {
    if (pattern.test(sqlCode)) {
      throw new Error(`SQLite-be MySQL-ism sneaked in: ${pattern}`);
    }
  }
}
```

---

### P14: Séma-Migráció Verziókezelve

**Elvárás**: Minden DB-módosítás `supabase/migrations/` alá kerül.

**Megelőzés**:

```bash
# Egyedi timestamp
VERSION=$(date +%Y%m%d%H%M%S)

# Migráció fájl
cat > "supabase/migrations/${VERSION}_add_assets_table.sql" << 'EOF'
-- Add Assets Table
CREATE TABLE IF NOT EXISTS assets (
  ...
);

-- Seed initial data
INSERT INTO assets (name, type, location) VALUES (...);
EOF

git add "supabase/migrations/${VERSION}_*.sql"
```

---

### P15: Seed-Adatok Újraindításánál Frissek

**Elvárás**: Az adatbázis szándékos DELETE-je után a seed automatikusan regenerálódik.

**Megelőzés**:

```bash
# Pipeline ELEJE
rm -f data/app.db  # Friss állapot

# Server indulás → séma + seed automatikusan
npm run start

# DB-állapot-ellenőrzés
sqlite3 data/app.db "SELECT COUNT(*) FROM assets" | \
  [ $(cat -) -gt 0 ] && echo "✓ Seed OK" || echo "✗ Seed FAILED"
```

---

## VI. VÉGPONT-SZERZŐDÉS MEGELŐZÉS

### P16: Útvonal-Duplikáció Nélkül

**Elvárás**: Egy útvonal-szegmens pontosan **egy helyen** (route-fájlban VAGY mount-pointban).

**Megelőzés**:

```typescript
// ✅ HELYES — relatív útvonal
// src/routes/apiAssets.ts
router.get('/assets', ...);           // ← belső, relatív
router.post('/assets', ...);

// src/server.ts
app.use('/api', router);              // ← mount-pont prefixszel
// Végeredmény: GET /api/assets

// ❌ TILOS — dupla prefix
// src/routes/apiAssets.ts
router.get('/api/assets', ...);       // ← már van a prefixet!
// src/server.ts
app.use('/api', router);              // ← megduplázódik!
// Végeredmény: GET /api/api/assets (HIBÁS)
```

**Kapu**:
```bash
# Keress dupla prefixet a route-fájlban
grep -n "router\.\(get\|post\|put\|delete\).*'/api\|'/data\|'/v1" src/routes/**/*.ts && \
  echo "⚠️ Route-fájlban már van prefix — ez dupla útvonalat okoz!"
```

---

### P17: Paraméter-Validáció

**Elvárás**: URL paraméterek típusa **kötött** (szám, string, uuid).

**Megelőzés**:

```typescript
// ❌ TILOS — típus-konverziót a route-ban végezni
router.get('/assets/:id', (req, res) => {
  const id = Number(req.params.id);  // ← user-hiba lehetséges
  const asset = getAssetById(id);
  if (!asset) return res.status(404).json(...);
  res.json(asset);
});

// ✅ HELYES — validációs middleware
const paramValidation = (schema) => (req, res, next) => {
  const { error, value } = schema.validate(req.params);
  if (error) return res.status(400).json({ error: error.message });
  req.params = value;
  next();
};

router.get('/assets/:id', 
  paramValidation(joi.object({ id: joi.number().required() })),
  (req, res) => { ... }
);
```

---

## VII. TELJES PIPELINE CHECKLISTSA

### P18: Pre-Build Checklist (amit az AI minden lépés előtt validál)

```bash
#!/bin/bash
# pre_build_checklist.sh

# 1. Szintaktika
npx tsc --noEmit || { echo "❌ Type-error"; exit 1; }
npx eslint src/**/*.ts || { echo "❌ Lint-error"; exit 1; }

# 2. Fájlok
[ -f src/db/db.ts ] || { echo "❌ db.ts hiányzik"; exit 1; }
[ -f package.json ] || { echo "❌ package.json hiányzik"; exit 1; }
[ -f tsconfig.json ] || { echo "❌ tsconfig.json hiányzik"; exit 1; }

# 3. Függőségek
npm ls 2>&1 | grep UNMET && { echo "❌ UNMET dependencies"; exit 1; }

# 4. Séma
sqlite3 data/app.db ".schema" > /dev/null 2>&1 || { echo "⚠️ DB nincs init"; }

# 5. Szerver-indítás test
timeout 3s npm run start || { echo "⚠️ Szerver nem indult"; }

echo "✅ PRE-BUILD CHECK PASS"
```

---

### P19: Post-Build Checklist

```bash
#!/bin/bash
# post_build_checklist.sh

# 1. Fordítás
npm run build || exit 1

# 2. Adatbázis
sqlite3 data/app.db "SELECT COUNT(*) FROM assets" | \
  [ $(cat -) -gt 0 ] && echo "✓ Assets table OK" || { echo "❌ Assets empty"; exit 1; }

# 3. API-Health
curl -s http://localhost:3000/api/health | jq -e '.status == "healthy"' || \
  { echo "❌ Health check failed"; exit 1; }

# 4. Tesztek
npm run test || exit 1

echo "✅ POST-BUILD CHECK PASS"
```

---

## VIII. GYORS REFERENCIA — PREVENTÍV SZABÁLYTÁBLÁZAT

| **Hibatípus** | **Tiltó Minta** | **Megelőzés** | **Kapu** |
|---|---|---|---|
| Python-komment | `# comment` | ESLint | Lint-gate |
| Csonka fájl | `function foo() { ... ]` | Brace-count | Build-hiba |
| Array-param | `.all([x, y])` | Prompt-injekció | Lint-gate |
| Dupla /api | `/api/api/...` | Prompt-injekció | HTTP-test |
| Hiányzó export | `import { X } from './Y'` (de Y nem exportál X-et) | Szerződés-kapu | Type-check |
| Nem létező DOM-id | `getElementById('xyz')` (de `#xyz` nincs HTML-ben) | DOM-szerződés | Frontend-test |
| MySQL syntax | `INTERVAL 24 HOUR` | Tiltó regex | Lint-gate |
| Null return | `getAsset(): Asset` (de null lehet) | TypeScript strict | Type-check |

---

## IX. ESZKÖZ-TELEPÍTÉS ELŐRE VALIDÁLT

**AI Session Eleje**:

```bash
#!/bin/bash
# verify_environment.sh

REQUIRED_TOOLS=("node" "npm" "git" "sqlite3")
for tool in "${REQUIRED_TOOLS[@]}"; do
  if ! command -v $tool &> /dev/null; then
    echo "❌ $tool nincs telepítve"
    exit 1
  fi
done

node_version=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
[ $node_version -ge 18 ] || { echo "❌ Node 18+ szükséges"; exit 1; }

npm ci
npm run build

echo "✅ Environment OK"
```

---

## X. ÖSSZEFOGLALÁS — A Legjobb Megelőzés

| **Lépés** | **Mit Csinálja** | **Hiba nélkül Igazi/Hamis** |
|---|---|---|
| **1. Terv** | Szerződések + file-struktúra | ✓ Igazi |
| **2. Lint-gate** | Szintaktikai alaphibák tiltása | ✓ Igazi |
| **3. Type-check** | TypeScript strict mode | ✓ Igazi |
| **4. Build** | Kompilációs hibák | ✓ Igazi |
| **5. DB-init** | Séma + seed | ✓ Igazi |
| **6. HTTP-test** | API-végpont validálása | ✓ Igazi |
| **7. Integration-test** | Frontend ↔ Backend | ✓ Igazi |

### [LESSON-BUILD-004] typedRoutes: true — widened `string` type breaks `<Link href>` — cast required
- **Dátum**: 2026-05-23
- **Érintett fájlok**: 13 oldal + 2 komponens (`app/arak`, `app/funkciok`, `app/page`, `app/levegominoseg-budapest`, `app/tarsashaz-kezeles/*`, `app/tarsashazi-jog`, `app/tomegkozlekedes-elemzes`, `app/zold-tarsashaz`, `components/public-footer`, `components/public-nav`)
- **Gyökérok**: `next.config.js`-ben `experimental.typedRoutes: true` van bekapcsolva. Ez generál egy `RouteImpl<string>` union típust az összes érvényes route-ból. A `<Link href>` prop ennek megfelelően `RouteImpl<string>` típust vár — **nem** sima `string`-et. Amikor egy object tömb `href` mezőjét TypeScript `string`-re szélesíti (type widening), a `<Link href={item.href}>` pattern TS2322 hibát dob.
- **Hiba**: `Type 'string' is not assignable to type 'UrlObject | RouteImpl<string>'`
- **Eltérés**: Hardkódolt string literálok (pl. `href="/kapcsolat"`) a Vercel buildben NEM okoznak hibát — TypeScript ezeket a generált route-listában keresi. Csak a `string`-re szélesített objektum-mezők hibásak.
- **Fix**: `import type { Route } from 'next'` hozzáadása a fájl elejéhez, majd `href={item.href as Route}` cast az összes érintett `<Link>` propnál.
- **Megelőzés**: Minden új oldalon ahol dinamikus `href` kerül `<Link>`-be (objektum tömbből), azonnal add hozzá az `as Route` castot ÉS az importot. Lokálisan ne futtasd csak `tsc --noEmit`-et build nélkül — a `.next/types` hiánya hamis pozitív hibákat ad.

### [LESSON-BUILD-003] Unused lucide-react imports + JSX unescaped entities — Vercel build blocker
- **Dátum**: 2026-05-23
- **Érintett fájlok**: 23 SEO-tartalom oldal
- **Gyökérok**: Tartalom-oldalak generálásakor az import blokkba kerültek lucide-react ikonok (`Phone`, `ArrowRight`, `Users`, `UserCheck`, `ClipboardList`, `ReceiptText`, `PiggyBank`, `Building2`, `FileText`, `AlertTriangle`, `BarChart2`, `MapPin`, `CheckCircle`, `Droplets`, `Menu`, `X`) amelyek végül nem kerültek használatra a JSX-ben. Emellett JSX szöveg-tartalomban szerepeltek `"` (ASCII dupla idézőjel) karakterek amelyeket az ESLint `react/no-unescaped-entities` szabály tilt.
- **Két hibatípus**:
  1. `@typescript-eslint/no-unused-vars` — importált ikon / változó, amit soha nem használtunk
  2. `react/no-unescaped-entities` — `"` karakter JSX szövegben (pl. `„szó"` → a záró `"` ASCII, amit `&quot;`-re kell cserélni)
- **Megelőzés**:
  - Tartalom-oldal generálás után MINDIG futtasd le: `npx next build 2>&1 | grep "Error:"` vagy `npx eslint app/UJ-OLDAL/page.tsx`
  - Import blokkba csak TÉNYLEGESEN HASZNÁLT ikonokat írj — ha kész a JSX, ellenőrizd hogy minden import szerepel-e a return-ben
  - JSX szövegben `"` helyett mindig `&quot;` (ASCII idézőjel), ill. a magyar tipografikus `„"` nyitó-pár nem flaggelt, csak a záró `"` (U+0022) ASCII karakter
- **Fix**: importok eltávolítása az import sorból; `"` → `&quot;` csere JSX text content-ben

### [LESSON-DEAD-CODE-002] signalNav eltávolítása után maradó orphan változók — 3 lépéses cleanup
- **Dátum**: 2026-05-22
- **Fájl**: `components/dashboard-client.tsx`
- **Gyökérok**: A `signalNav` tömb eltávolítása (ami az `<aside>` sávból lett kitörölve) nem volt egylépéses — az eltávolítás 3 külön Vercel build hiba sorozatot okozott, mert az orphan kód részekben maradt:
  - 1. pass: `Terminal`, `roleLabels`, `kommandOpen`, `kommandQuery`, `kommandItems`, `kommandResults` (Kommand palette)
  - 2. pass: `Flame`, `Leaf`, `Recycle`, `TrendingUp`, `Volume2` (signalNav env-ikonok), `criticalTickets`, `unacknowledgedDocs`, `upcomingMeetings` (signalNav count változók)
- **Eltávolított importok** (kizárólag signalNav-ban használtak, más helyen nem): `Terminal`, `Flame`, `Leaf`, `Recycle`, `TrendingUp`, `Volume2`
- **Eltávolított state/változók**: `kommandOpen`, `kommandQuery`, `kommandItems`, `kommandResults`, `signalNav`, `criticalTickets`, `unacknowledgedDocs`, `upcomingMeetings`
- **Megelőzés**: Ha egy nagy kód-blokkot (pl. `<aside>`, `signalNav`) távolítasz el, **MINDIG** futtasd le a következő grep-et ELŐTTE és UTÁNA is, hogy minden függőséget megtalálj:
  ```
  grep -n "signalNav\|kommand\|Terminal\|Flame\|Leaf\|Recycle\|TrendingUp\|Volume2\|criticalTickets\|unacknowledgedDocs\|upcomingMeetings" components/dashboard-client.tsx
  ```
  Egy lépésben távolíts el minden orphan import-ot, state-et és változót.

### [LESSON-UI-BRAND-001] TILTOTT SZÖVEGEK — soha ne jelenjenek meg újra a felületen
- **Dátum**: 2026-05-22
- **Érintett fájlok**: `components/workspace-sidebar.tsx`, `components/dashboard-client.tsx`
- **Gyökérok**: Az AI agent korábban „Operációs központ" és „Digitális műveleti központ" feliratokat helyezett el a sidebar logó alatt és a hero szekcióban. A felhasználó VÉGLEGESEN eltávolíttatta ezeket.
- **TILALOM (visszavonhatatlan)**: A következő szövegeket **TILOS** bármelyik UI-komponensbe, page-be vagy layout-ba beírni:
  - `Operációs központ`
  - `Digitális műveleti központ`
  - Ezek semmilyen változata, rövidítése, fordítása
- **Megelőzés**: Minden PR review előtt grep-pel ellenőrizni: `grep -rn "Operációs\|műveleti" components/ app/`. Ha találat van → AZONNALI eltávolítás, no-exception.

### [LESSON-TYPO-HU-001] Magyar helyesírás: „hősziget" (NEM „hőszigat")
- **Dátum**: 2026-05-22
- **Gyökérok**: Az „urban heat island" (városi hősziget) szót rendszeresen helytelenül írta az AI: „hőszigat" az összes felületen.
- **Helyes alak**: `hősziget`, `Hősziget`, `hősziget-hatás`, `hősziget kockázat`
- **Hibás alak** (TILOS): `hőszigat`, `Hőszigat`
- **Megelőzés**: Minden klíma/környezet témájú szövegnél grep: `grep -rin "hőszigat" components/ app/`. Ha találat → azonnal javítani.

### [LESSON-TRANSIT-081] Vercel Cron nem küld custom Authorization Bearer tokent
- **Dátum**: 2026-05-19
- **Fájl**: `app/api/transit/sync/route.ts`
- **Gyökérok**: A sync endpoint csak `Authorization: Bearer <secret>` ellenőrzést fogadott el. Vercel Cron trigger esetén tipikusan `x-vercel-cron: 1` fejléc érkezik, ezért a scheduled futások 401-re estek.
- **Javítás**: Authorization ellenőrzés bővítve Vercel Cron fejlécre és opcionális query secretre.
- **Megelőzés**: Minden cron endpointnál platform-specifikus auth módot is támogatni kell, és a 401 válaszban operatív hintet kell adni.

# codingLessonsLearnt.md — Kapakka PubApp

## ⚠️ UTASÍTÁSOK (MINDIG OLVASD EL ELŐSZÖR!)

**KÖTELEZŐ MUNKAFOLYAMAT — Minden fejlesztés előtt:**
1. Nyisd meg és olvasd végig ezt a fájlt MIELŐTT bármit kódolnál
2. Ellenőrizd, hogy az új kódod nem tartalmaz-e az itt felsorolt hibamintákat
3. Ha új hibát találsz/javítasz, AZONNAL appendeld a megfelelő kategóriába
4. SOHA ne töröld a meglévő tartalmat — csak hozzáadni szabad
5. SOHA ne hozz létre új fájlt ezzel a céllal — mindig ebbe a fájlba írd

**Struktúra minden hiba bejegyzésnél:**
```
### [HIBA-XXX] Rövid cím
- **Dátum**: Mikor fordult elő
- **Fájl**: Melyik fájlban volt
- **Hibaüzenet**: Pontos TypeScript/build error
- **Gyökérok**: Miért történt
- **Javítás**: Hogyan lett megoldva
- **Megelőzés**: Hogyan kerüld el a jövőben
```

---

## 🔴 KATEGÓRIA 1: TypeScript típus hibák

### [HIBA-001] Hiányzó property az interface-ből
- **Dátum**: 2026-03-30 (v1.1.0)
- **Fájl**: `src/app/admin/menu/templates/page.tsx:157`
- **Hibaüzenet**: `Type error: Property 'item_sort' does not exist on type 'TemplateItem'.`
- **Gyökérok**: A `TemplateItem` interface-ben nem volt definiálva az `item_sort` property, miközben a kód hivatkozott rá (`sort_order: item.item_sort`). Az interface-t kézzel írtam, és kifelejtettem egy mezőt amit az SQL tábla tartalmaz.
- **Javítás**: Hozzáadtam `item_sort: number` a `TemplateItem` interface-hez.
- **Megelőzés**: **MINDIG** hasonlítsd össze az interface mezőket az SQL tábla oszlopaival. Ha az SQL-ben van `item_sort`, az interface-ben is KELL lennie. Checklist: minden SQL oszlop = egy interface property.

### [HIBA-002] Supabase FK reláció típusozás — `.table.number` hiba
- **Dátum**: 2026-03-30 (v1.2.0)
- **Fájl**: `src/app/admin/reports/page.tsx:61`
- **Hibaüzenet**: `Type error: Property 'number' does not exist on type '{ number: any; }[]'.`
- **Gyökérok**: Supabase `.select('table:tables(number)')` esetén a TypeScript a relációt **tömbként** (`{ number: any }[]`) típusozza, nem objektumként. Ezért `o.table.number` helyett `o.table[0].number` kellene, de valójában futásidőben objektumot ad vissza (nem tömböt).
- **Javítás**: A `.map()` callback-ben `(o: any)` típust használtam: `.map((o: any) => [...])` — ez megkerüli a Supabase TS típus problémát.
- **Megelőzés**: **MINDIG** használj `(item: any)` cast-ot amikor Supabase `.select()` eredményt iterálsz és FK relációkat (`table:tables(...)`, `venue:venues(...)`, `menu_item:menu_items(...)`) használsz. VAGY használj `useState<any[]>([])` a state-hez. A kettő közül az egyik KÖTELEZŐ.

### [HIBA-003] Supabase FK — új oszlopok nem ismertek a TS típusokban
- **Dátum**: 2026-03-30 (v1.2.0)
- **Fájl**: `src/app/admin/reports/page.tsx:137`
- **Hibaüzenet**: Potenciális — `total_orders`, `total_spent` nem létezik a `profiles` Supabase típusban
- **Gyökérok**: Ha ALTER TABLE-lel új oszlopot adsz hozzá (`total_orders`, `total_spent`), a Supabase TS generált típusok nem frissülnek automatikusan. A `.select()` eredmény típusa nem tartalmazza az új mezőket.
- **Javítás**: `(c: any)` cast a `.map()` callback-ben.
- **Megelőzés**: Ha SQL migrációval új oszlopokat adsz egy meglévő táblához, az adott tábla select eredményeit MINDIG `(row: any)` casttal kezeld, amíg a típusok nem lesznek újragenerálva (`supabase gen types`).

### [HIBA-018] Implicit `any` a chained `.map().filter()` callbackben
- **Dátum**: 2026-03-31 (v1.3.1)
- **Fájl**: `src/lib/place-search.ts:98`
- **Hibaüzenet**: `Type error: Parameter 'row' implicitly has an 'any' type.`
- **Gyökérok**: A `rows` tömb `any[]` típusú volt, és a `rows.map(...).filter((row) => row.external_id)` láncban a `filter` callback paramétere nem kapott explicit típust. `noImplicitAny` mellett ez build hibát okozott.
- **Javítás**: A nyers API választ `const rows: any[]` formában explicitáltam, külön `normalizedRows: ExternalPlace[]` tömbbe mapeltem, majd a filter callbacket `ExternalPlace` típussal adtam meg.
- **Megelőzés**: Ha `any[]` tömbből több lépéses `.map().filter().reduce()` lánc készül, a köztes eredményt MINDIG nevezd el és adj neki explicit típust. A végső callback paramétereknél ne hagyatkozz implicit inference-re `strict` TypeScript beállítás mellett.

---

## 🟡 KATEGÓRIA 2: SQL / RLS / Adatbázis hibák

### [HIBA-004] SQL szintaxis hiba — RLS policy zárójelezés
- **Dátum**: 2026-03-29 (v1.0.0)
- **Fájl**: `supabase/migrations/001_initial_schema.sql:47`
- **Hibaüzenet**: `syntax error at or near "or" LINE 47: ) or is_active = true;`
- **Gyökérok**: Az RLS policy USING() zárójelén kívül volt egy `or is_active = true` feltétel. A helyes szintaxis: `USING ((feltétel1) OR (feltétel2))` — minden feltétel a USING() BELSEJÉBE kerül.
- **Javítás**: Az egész policy-t újraírtam helyes zárójelezéssel.
- **Megelőzés**: RLS policy írásakor MINDIG ellenőrizd, hogy MINDEN feltétel a `USING(...)` zárójelen BELÜL van. Soha ne legyen logikai operátor a zárójelen kívül.

### [HIBA-005] RLS policy circular dependency — profil olvasás blokkolva
- **Dátum**: 2026-03-29 (v1.0.1)
- **Fájl**: Profiles RLS policies
- **Hibaüzenet**: Profil lekérdezés sikertelen admin felhasználóknál
- **Gyökérok**: A profiles SELECT policy JOIN-t tartalmazott a `venues` táblára, ami maga is RLS-sel volt védve. Ha a venues policy is hivatkozott a profiles-ra → circular dependency. Az admin felhasználó nem tudta olvasni a saját profilját.
- **Javítás**: Egyszerű policy: `CREATE POLICY "profiles_select_authenticated" ON public.profiles FOR SELECT USING (auth.uid() IS NOT NULL);` — minden bejelentkezett felhasználó olvashat minden profilt.
- **Megelőzés**: **SOHA** ne legyen RLS SELECT policy-ban JOIN más RLS-védett táblára. Ha kell cross-table check, használj egyszerű `auth.uid()` alapú feltételt, vagy SECURITY DEFINER funkciót.

### [HIBA-006] Profil email NULL — role update 0 rows
- **Dátum**: 2026-03-29 (v1.0.1)
- **Hibaüzenet**: `UPDATE public.profiles SET role = 'admin' WHERE email = 'x@y.com'` → 0 rows affected
- **Gyökérok**: A `handle_new_user()` trigger nem másolta át az email-t az `auth.users` táblából a `profiles` táblába. A `profiles.email` mező NULL volt, ezért a WHERE feltétel nem talált sort.
- **Javítás**: JOIN-os UPDATE: `UPDATE profiles p SET role = 'admin' FROM auth.users u WHERE p.id = u.id AND u.email = 'x@y.com';`
- **Megelőzés**: A `handle_new_user()` trigger MINDIG másolja át az email-t: `NEW.raw_user_meta_data->>'email'` VAGY `(SELECT email FROM auth.users WHERE id = NEW.id)`. Soha ne feltételezd, hogy a profiles.email ki van töltve.

### [HIBA-007] Supabase FK constraint név — törékeny hivatkozás
- **Dátum**: 2026-03-30 (v1.1.0)
- **Fájl**: `src/app/siteadmin/venues/page.tsx`
- **Hibaüzenet**: Potenciális — `profiles!venues_owner_id_fkey` nem létezik
- **Gyökérok**: `.select('*, owner:profiles!venues_owner_id_fkey(full_name, email)')` — a constraint név adatbázisonként eltérhet. A Supabase automatikusan generálja a FK constraint nevet, és nem garantált, hogy mindig `venues_owner_id_fkey`.
- **Javítás**: Lecseréltem `.select('*, owner:profiles(full_name, email)')` — constraint név nélkül, a Supabase automatikusan feloldja.
- **Megelőzés**: **SOHA** ne használj explicit FK constraint nevet a `.select()` relációkban. Használd a szimpla `table_name(columns)` szintaxist. Ha ambiguous, használd a `table_name!column_name(columns)` formátumot (oszlop nevet, NEM constraint nevet).

---

## 🟠 KATEGÓRIA 3: Auth / Redirect / Session hibák

### [HIBA-008] Auth redirect loop — 4 helyen konkurens redirect
- **Dátum**: 2026-03-29 (v1.0.0 → v1.0.1)
- **Fájl**: middleware.ts + page.tsx + customer/page.tsx + admin/layout.tsx
- **Hibaüzenet**: Végtelen loading screen — az alkalmazás sosem jutott túl az „Átirányítás..." képernyőn
- **Gyökérok**: 4 különböző helyen volt routing logika, és egymásba irányítottak: middleware → /admin → admin/layout ellenőrzi → /customer → customer/page ellenőrzi → /admin → ∞ loop
- **Javítás**:
  1. `middleware.ts` — CSAK cookie frissítés, NULLA redirect
  2. `page.tsx` — Egyetlen auth check 4s timeout-tal, `hasRedirected` ref a dupla redirect ellen
  3. `customer/page.tsx` — Admin felhasználóknak "Admin panel megnyitása" GOMB, nem redirect
  4. `admin/layout.tsx` — Nem-admin felhasználóknak error screen, nem redirect
- **Megelőzés**: **EGY SZABÁLY**: Routing döntés KIZÁRÓLAG client-side, egyetlen helyen. Middleware SOHA ne redirecteljen. Ha jogosultsági hiba van, mutass error screen-t, ne redirectelj másik oldalra.

### [HIBA-009] getSession() vs getUser() — elavult session
- **Dátum**: 2026-03-29
- **Gyökérok**: `getSession()` a helyi cache-ből olvas, ami elavult lehet. `getUser()` mindig a Supabase szerverhez fordul.
- **Megelőzés**: Auth ellenőrzésnél MINDIG `getUser()` a megbízható módszer, NEM `getSession()`.

### [HIBA-014] Venue JOIN a profil lekérdezésben blokkolja az auth-ot
- **Dátum**: 2026-03-30 (v1.2.0)
- **Fájl**: `src/app/admin/layout.tsx`
- **Hibaüzenet**: "Nincs hozzáférésed" — admin felhasználó nem tud belépni az admin panelre
- **Gyökérok**: A profil lekérdezés `select('*, venue:venues(*)')` formában volt, ami FK JOIN-t csinál a venues táblára. Ha a `profiles.venue_id` NULL (nincs venue hozzárendelve), VAGY ha nincs explicit FK constraint a DB-ben, VAGY ha az RLS policy blokkolja a venues lekérést, az EGÉSZ lekérdezés hibával tér vissza (`profileError` != null). Emiatt a kód a `no-permission` ágra futott, pedig a felhasználó valójában admin role-lal rendelkezett.
- **Javítás**: A profil és venue lekérdezést SZÉTVÁLASZTOTTAM:
  1. Először: `select('*')` a profiles-ból (FK JOIN nélkül) — ez az auth check
  2. Utána: külön `select('*')` a venues-ból venue_id alapján — ez már NEM blokkolja az auth-ot
- **Megelőzés**: **SOHA** ne legyen FK JOIN egy auth-kritikus lekérdezésben! Az auth ellenőrzés (profil + role check) MINDIG egyszerű, single-table query legyen. Ha kiegészítő adatok kellenek (venue, orders stb.), azokat KÜLÖN, NEM-BLOKKOLÓ lekérdezésben szerzd be MIUTÁN az auth check sikeres.

---

## 🔵 KATEGÓRIA 4: Build / Import / Kompatibilitás hibák

### [HIBA-010] Next.js fájlnév konvenció — page.tsx kötelező
- **Dátum**: 2026-03-29
- **Gyökérok**: A felhasználó a letöltött fájlokat `admin-layout.tsx` és `customer-page.tsx` néven mentette el `layout.tsx` és `page.tsx` helyett. Next.js App Router CSAK a `page.tsx`, `layout.tsx`, `loading.tsx` stb. pontos neveket ismeri fel.
- **Megelőzés**: Fájlok MINDIG a pontos Next.js konvenció szerinti nevekkel készüljenek. A letöltési/mentési utasításokban MINDIG jelöld meg a cél fájlnevet.

### [HIBA-011] Lucide React ikon import — nem létező ikon név
- **Dátum**: Általános (megelőző figyelmeztetés)
- **Megelőzés**: Lucide React ikonokat MINDIG a hivatalos listáról importáld. Ha nem biztos, hogy létezik, használj olyan ikont ami biztosan megvan (pl. `Settings`, `User`, `Search`, `Plus`, `Check`, `X`). A `lucide-react@0.363.0` verzióban ezek biztosan elérhetők: Zap, ClipboardList, UtensilsCrossed, Package, BarChart3, Settings, HelpCircle, Menu, Bell, LogOut, Shield, ChevronRight, X, Monitor, CalendarClock, FileDown, Plus, Pencil, Trash2, Search, CheckCircle, XCircle, Volume2, VolumeX, Maximize, Minimize, RefreshCw, Check, Clock, AlertTriangle, Download, FileSpreadsheet, Calendar, TrendingUp, ShoppingBag, Users, Phone, Mail, User, ChevronLeft, ChevronRight, ChevronDown, ChevronUp, ArrowLeft, Sparkles, Star, MapPin, Store, ScrollText, LayoutDashboard, Activity, Info, AlertCircle, ToggleLeft, ToggleRight, Save, Filter, Bug, Send.

### [HIBA-015] Lucide React redesign patch — `House` ikon build hibát okozott
- **Dátum**: 2026-03-30 (v1.2.1)
- **Fájl**: `src/app/customer/page.tsx:15`
- **Hibaüzenet**: `Type error: "lucide-react" has no exported member named 'House'. Did you mean 'Mouse'?`
- **Gyökérok**: A redesign patch-ben olyan Lucide ikont importáltam (`House`), ami a projektben használt verzióban nem exportált. Ráadásul több más ikon is a "biztosan elérhető" listán kívül volt, ezért a patch nem követte a kötelező ikon-import szabályt.
- **Javítás**: A `House` importot `LayoutDashboard`-ra cseréltem, és a redesign patch összes új Lucide importját átnéztem. Az összes bizonytalan ikont lecseréltem a codingLessonsLearnt-ben felsorolt, biztosan elérhető ikonokra.
- **Megelőzés**: **MINDIG** ellenőrizd a redesign patch összes Lucide importját a `codingLessonsLearnt.md` [HIBA-011] pontja alapján. Új UI csomag kiadása előtt kötelező grep-pel végignézni az összes `from 'lucide-react'` importot, és csak a whitelistelt ikonok maradhatnak.



---

## 🟢 KATEGÓRIA 5: CSS / UI hibák

### [HIBA-012] Admin `.input` class hiányzik
- **Dátum**: 2026-03-30 (v1.1.0)
- **Gyökérok**: Az admin oldalak `.input` CSS class-t használnak az input mezőkhöz, de ez nem volt definiálva a globals.css-ben. A Tailwind nem generálja automatikusan.
- **Javítás**: `.input` class hozzáadása a globals.css-hez explicit CSS-ként.
- **Megelőzés**: Ha egyedi CSS class-t használsz (`.input`, `.status-badge`, `.animate-slide-up`), MINDIG ellenőrizd, hogy definiálva van-e a globals.css-ben.

### [HIBA-013] Admin sidebar mobil nézet — nem jelenik meg
- **Dátum**: 2026-03-30
- **Gyökérok**: A `display: none` `@media(max-width:768px)` felülírta a JavaScript-ből adott `translate-x-0` class-t.
- **Javítás**: CSS override: `.admin-sidebar.translate-x-0 { display: flex !important; }`
- **Megelőzés**: Ha egy elem CSS-ből `display:none`, a JS class hozzáadás NEM elég — `!important` kell a CSS-ben is.

---

### [HIBA-015] Patch-only csomagból kimaradt új supporting fájlak
- **Dátum**: 2026-03-30 (v1.2.1)
- **Fájl**: patch csomag / `src/app/layout.tsx`, `src/app/admin/config/page.tsx`
- **Hibaüzenet**: Build/import hiba, mert az újonnan hivatkozott `@/components/AppShellProviders` és `@/lib/themes` fájlok nem voltak benne a patch-only zipben.
- **Gyökérok**: A patch-only csomagolásnál nem csak a módosított meglévő fájlakat, hanem az újonnan BEVEZETETT supporting fájlokat is csomagolni kell. Ezek kimaradtak.
- **Javítás**: A patch-only csomag listáját úgy kell összeállítani, hogy minden új import célfájlja bekerüljön.
- **Megelőzés**: Patch készítés előtt **MINDIG** futtasd le ezt a checklistet: minden `import '@/...'` útvonalhoz létezik fájl ÉS a zipben is benne van, ha újonnan lett bevezetve.

### [HIBA-016] Design patch buildbiztonság — csak syntax-ellenőrzött fájl csomagolható
- **Dátum**: 2026-03-30 (v1.3.0)
- **Fájl**: összes új / módosított `.tsx` fájl
- **Hibaüzenet**: Potenciális — reszponzív redesign közben könnyű szintaktikai hibát vagy félbehagyott importot hagyni.
- **Gyökérok**: Nagy redesignnál sok fájl változik egyszerre, ezért megnő a hibázás esélye.
- **Javítás**: A patch csomagolás előtt a módosított TS/TSX fájlakat legalább TypeScript parser szinten ellenőrizni kell.
- **Megelőzés**: **MINDIG** legyen build-safety lépés: ha teljes `npm build` nem futtatható, akkor minimum parser/syntax ellenőrzést kell végezni minden módosított TS/TSX fájlra.

### [HIBA-017] Új adatbázis tábla / migráció még nincs fent — UI ne omoljon össze
- **Dátum**: 2026-03-30 (v1.3.0)
- **Fájl**: `src/app/customer/page.tsx`, `src/app/admin/config/page.tsx`, új social/place feature lekérdezések
- **Hibaüzenet**: Potenciális — ha a `place_favorites`, `friendships`, `place_lists`, `app_settings` vagy `reservations` migráció még nincs lefuttatva, a featurelekérdezések hibát dobhatnak.
- **Gyökérok**: A frontend hamarabb kerülhet fel, mint az új migráció.
- **Javítás**: A lekérdezések `maybeSingle()` / `|| []` fallback mintával készültek, és a feature nem auth-kritikus ágon fut.
- **Megelőzés**: **SOHA** ne legyen új opcionális feature táblára épített lekérdezés auth-kritikus vagy page-blocking. Új feature tábla = null-safe, fallbackes, nem-blokkoló betöltés.

## 📋 ELLENŐRZŐ LISTA (Minden commit előtt)

- [ ] Auth-kritikus lekérdezésben NINCS FK JOIN? (profiles select = egyszerű `select('*')`)
- [ ] Minden interface/type property megegyezik az SQL tábla oszlopaival?
- [ ] Supabase `.select()` FK relációk használatánál van `(row: any)` cast?
- [ ] Nincs explicit FK constraint név a Supabase select-ben?
- [ ] Nincs middleware-ben redirect?
- [ ] Auth check `getUser()`-t használ, nem `getSession()`-t?
- [ ] Fájlnevek Next.js konvenciónak megfelelnek (`page.tsx`, `layout.tsx`)?
- [ ] Egyedi CSS class-ok definiálva vannak a globals.css-ben?
- [ ] Lucide ikonok a hivatalos listáról importálva?
- [ ] Minden új import célfájlja benne van a patch-only csomagban?
- [ ] Parser/syntax ellenőrzés lefutott a módosított TS/TSX fájlakon?
- [ ] RLS policy-kban nincs cross-table JOIN más RLS-védett táblára?
- [ ] Új SQL oszlopok esetén a kód `(: any)` castot használ?

---

*Utoljára frissítve: 2026-04-11 — v2.0.0*
*Ez egy FOLYAMATOSAN BŐVÜLŐ fájl. Új hibákat MINDIG appendelj, SOHA ne törölj!*

## ➕ APPEND — 2026-05-10 Org chart navigation + Bővebb adatok view

### [LESSON-UI-076]: Drawer / pan-zoom canvas belsejében a kattintható elemekre `onMouseDown stopPropagation` is kell
- **Context**: `OrgChartPremiumView` jobb oldali drawerében új interaktív gombok (`Vezető` + `Közvetlen beosztott` badge-ek, `Adatlap megnyitása` gomb) — egy pán-/zoom-os canvas mellett.
- **Problem**: Az ős `<div>` `onMouseDown` indít drag-et; a drag a `DRAG_THRESHOLD` (6 px) átlépésekor elnyeli a click-et a `onClickCapture`-ben. Ha a belső gomb csak `onClick`-et kap, a felhasználó kattintása drag-re változhat finommotorikus mozgásnál → a click sosem tüzel.
- **Fix**: Minden interaktív elem KAP `onMouseDown` handlert, ami `stopPropagation()`-t hív. Ezzel a drag NEM indul, és a `onClick` garantáltan tüzel. A `MiniPersonRow`, `DrawerLinkRow`, és az „Adatlap megnyitása" gomb mind ezt a mintát követi.
- **Pattern**:
  ```tsx
  <button
    onClick={onSwitchTo}
    onMouseDown={(e) => e.stopPropagation()}  // pan-zoom canvasban kötelező
  >
    …
  </button>
  ```

### [LESSON-SUPABASE-SDK-077]: Új opcionális tábla lekérdezésénél kezeljük explicit a "relation does not exist" (42P01) hibát
- **Context**: A `MemberExtendedDetails` „Meghatározott célok" szekciója az új `enterprise_member_goals` táblát olvassa, de a tábla még nem feltétlenül létezik (a migráció lehet külön deployolva).
- **Problem**: Ha az SDK egy nem-létező táblára hív `select`-et, az error.code === '42P01' (Postgres "relation does not exist"). A normál hibakezelés `toast.error(error.message)` mintával dobálná a piros toaszt minden render-nél.
- **Fix**: A goals query külön try-catch + error.code ellenőrzés. Ha 42P01 vagy a `message` "does not exist" stringet tartalmaz → `goalsTableMissing = true` flag-et állítunk. A UI inline figyelmeztetést mutat ("A célok modul még nincs telepítve. Futtasd le a legújabb migrációt"), nem dobja össze a többi szekciót, és nem ugrál a toast.
- **Pattern**:
  ```ts
  const res = await (supabase as any).from('enterprise_member_goals').select('id').limit(1);
  if (res.error) {
    if (String(res.error.code) === '42P01' || /does not exist/i.test(res.error.message ?? '')) {
      setTableMissing(true);
    } else {
      console.warn('[X] load error:', res.error.message);
    }
  }
  ```

### [LESSON-SEED-078]: Done jegyek `external_updated_at` mezőjét a seedben backdate-elni kell, hogy időbeli vizualizációk működjenek
- **Context**: A demo seed `enterprise_agile_issues` rekordokat hoz létre, részük `Done` státusszal. A MemberProfileSheet teljesítmény-diagramja az utolsó 12 hónap havi `story_points` összegét mutatja Done jegyekre az `external_updated_at` (vagy fallback `due_date`) alapján.
- **Problem**: A seed eredetileg nem állította az `external_updated_at` mezőt, és a Done jegyek `due_date`-je is csak az utolsó 1-2 hét volt — emiatt a 12 hónapos diagram szinte mindig üres, a demo nem mutatja értelmesen a feature-t.
- **Fix**: A seed insert előtt minden Done jegyhez számolunk egy backdated `external_updated_at` timestampet az utolsó 180 napra szétterítve, deterministic hash-szel (`(doneCounter * 37) % 175`). Így újra-seedelésnél is azonos eloszlást kapunk, és a chart a teljes 12 hónapos időablakot lefedi.
- **Pattern**:
  ```ts
  let doneCounter = 0;
  const issueRows = AGILE_ISSUE_DEFS.map(({ startOff, dueOff, ...rest }) => {
    const isDone = (rest.status ?? '').toLowerCase() === 'done';
    let externalUpdatedAt: string | null = null;
    if (isDone) {
      const offset = -180 + Math.round(((doneCounter * 37) % 175));
      doneCounter += 1;
      externalUpdatedAt = addDays(today, offset).toISOString();
    }
    return { ...rest, ...(externalUpdatedAt ? { external_updated_at: externalUpdatedAt } : {}) };
  });
  ```

### [LESSON-ROUTING-SPA-079]: Deep-link tabváltáshoz külső callback prop, NEM közvetlen URL manipuláció
- **Context**: A MemberProfileSheet „Bővebb adatok" szekcióiból deep-link gombokkal lehet a Resources / Workflows tabokra ugrani.
- **Problem**: A `MemberProfileSheet` mélyen a komponensfa belsejében ül (Sheet portal-ban renderelődik), így nincs natív hozzáférése sem a `useSearchParams`-hoz, sem a `WorkspaceDashboard.setActiveTab`-hez. Korábbi `LeaveCalendar` patternünk már `onNavigateTab?: (tab: string) => void` propot használ.
- **Fix**: Ugyanezt a prop pattern-t alkalmaztuk az új modulokra is. Az `onNavigateTab`-ot WorkspaceDashboard → MemberList / OrganizationModule → OrgChart → MemberProfileSheet láncon adjuk át. Minden köztes komponens csak forward-olja, és a végpontnál (MemberProfileSheet) a deep-link kattintáskor a helyi sheet bezárul, majd hívja `onNavigateTab(tab)` — így a setActiveTab dispatch + URL-szinkron a szülőben történik.
- **Pattern**:
  ```tsx
  // Top: WorkspaceDashboard
  <OrganizationModule onNavigateTab={setActiveTab} userId={userId} />
  // Mid: OrganizationModule → OrgChart, MemberList → MemberProfileSheet
  onNavigateTab={(tab) => { setSelectedMember(null); onNavigateTab?.(tab); }}
  ```

---

## ➕ APPEND — 2026-05-10 Import/Export Center implementáció

### [LESSON-IMPORT-081]: Config-driven entity registry > entity-specific UI komponensek
**Context**: Bulk import/export rendszer 7 entitásra (Members, Leave, Offices, Work Categories, Job Roles, Positions, Skills). Naiv megközelítés: minden entitásra külön panel komponens.
**Problem**: Külön komponens / entitás megduplikálja az UI logikát (mezőkijelölő, validátor, oszlopleképezés, error preview), és minden új entitás kódfejlesztést igényelne.
**Fix**: Egyetlen config tömb `ENTITY_REGISTRY[]` definiálja az összes entitást field-szinten (`FieldDefinition`: key, label, type, required, importable, exportable, computed, group, importAlias, templateExample, protected). A wizard, field picker, mapper, validátor mind ebből olvas. Új entitás = 1 sor a configban + Edge Function handler — UI komponens érintetlen.
**Pattern**:
```ts
export const ENTITY_REGISTRY: EntityConfig[] = [
  { key: 'members', label: 'Tagok', icon: Users, fields: MEMBER_FIELDS, ... },
  // ...további entitások
];

// UI:
const entity = getEntityConfig(entityKey);
entity.fields.filter(f => f.exportable).forEach(...)
```
**Megelőzés**: Mielőtt entitás-specifikus komponenst írsz: kérdezd meg, hogy egy config-driven approach megoldaná-e. Ha 3+ hasonló entitás van, KÖTELEZŐ a config-pattern.

### [LESSON-CSV-082]: RFC 4180-kompatibilis CSV parser saját kézzel — embedded comma + quote support
**Context**: A korábbi `CsvImportPanel.parseCSV` csak `line.split(',')`-ot használt. Ez töri a `"Kovács, Béla"` típusú quoted mezőket, és nem kezeli a `""` escape-et.
**Problem**: Felhasználói exportok gyakran tartalmaznak vesszőt (címek, megjegyzések) vagy idézőjelet (cégnevek). Naiv splitter `,`-nél töri, validációs hiba helyett rossz adat kerül a DB-be.
**Fix**: State-machine alapú parser `inQuotes` flag-el, kezeli a `""` escape-et, CRLF / LF line endings, BOM strip. Lásd `import-export/utils/file-parser.ts → parseCSV()`.
**Pattern**:
```ts
let inQuotes = false;
while (i < text.length) {
  const ch = text[i];
  if (inQuotes) {
    if (ch === '"' && text[i + 1] === '"') { cell += '"'; i += 2; continue; }
    if (ch === '"') { inQuotes = false; i++; continue; }
    cell += ch; i++; continue;
  }
  if (ch === '"') { inQuotes = true; i++; continue; }
  // ... comma, newline, default
}
```
**Megelőzés**: Soha ne használj `string.split(',')` CSV parsoláshoz. Ha nem akarsz library-t hozni (papaparse), írj state-machine parsert vagy regex-mentes karakter-szintű loopot.

### [LESSON-IMPORT-083]: Excel XML (.xls) format > .xlsx ZIP format browser környezetben library nélkül
**Context**: A felhasználók Excel-ben szerkesztett fájlokat töltenek fel. .xlsx valódi formátum: ZIP archívum XML fájlokkal — JSZip vagy SheetJS kell hozzá.
**Problem**: Külső library hozzáadása növeli a bundle-t (~300KB SheetJS). Sandbox környezetben nem mindig telepíthető.
**Fix**: Excel XML Spreadsheet 2003 formátum (`.xls`-ként mentve) — egy single XML fájl, semmilyen ZIP, könnyen olvasható/írható kézzel. Excel és LibreOffice natívan megnyitja. Generálás: `<Workbook><Worksheet><Table><Row><Cell><Data>...</Data></Cell></Row>...`. Olvasás: regex `/<Row[^>]*>([\s\S]*?)<\/Row>/g`.
**Megelőzés**: Ha XLSX-fertőző alkalmazást fejlesztesz library nélkül: használd az Excel XML Spreadsheet 2003 formátumot. Ha valódi .xlsx kell, hozz be SheetJS-t.

### [LESSON-IMPORT-084]: Auto-detect + skip guidance row template-ekben
**Context**: Az import-kompatibilis sablon második sora egy útmutató sor (`kovacs.bela@ceg.hu`, `Kovács Béla`, `Backend`...). Ha a felhasználó nem törli ki, az importba kerül mint adat.
**Problem**: Ha a wizard automatikusan elsőnek ezt importálja, hibát dob ("kovacs.bela@ceg.hu" nem létezik a profiles táblában). Vagy létrehoz egy hamis "Kovács Béla" tagot.
**Fix**: Auto-detect heurisztika a feltöltés után: ha a 2. sor email mezője nem érvényes email VAGY tartalmazza a `@ceg.hu` placeholder domaint → automatikusan kihagyjuk. Toast: "Útmutató sor automatikusan kihagyva".
**Pattern**:
```ts
function detectGuidanceRow(entity, mapping, firstRow) {
  const emailField = entity.fields.find(f => f.type === 'email' && f.required);
  if (!emailField) return false;
  const v = firstRow[mappedHeader].trim();
  if (v.includes('@ceg.hu')) return true;
  if (v && !EMAIL_RE.test(v)) return true;
  return false;
}
```
**Megelőzés**: Bármilyen template formátumnál, ahol vannak nem-adat sorok (header, guidance, totals): auto-detect logikát építs az importerbe. Soha ne hagyd a felhasználóra a manuális tisztogatást.

### [LESSON-IMPORT-085]: Members import = invitation flow új email-ekhez, közvetlen update meglévőkhöz
**Context**: Tagok bulk importálásánál két eset van: (a) új email cím nincs a `profiles` táblában (új user), (b) meglévő email a profiles-ban (létező user, esetleg másik workspace-ben).
**Problem**: Ha új user-t közvetlenül `enterprise_memberships`-be írunk be `user_id` nélkül, FK constraint sérül. Ha megpróbálunk auth admin API-val accountot létrehozni, az kötelez jelszó-stratégia + jelszó visszaállítás emailre küldést.
**Fix**:
- Új email (nincs profile): `enterprise_invitations`-be insert (létező pattern!) — a workspace owner később jóváhagyja, a felhasználó tudja regisztrálni az emailen kapott linkkel.
- Meglévő user, nincs membership: közvetlen `enterprise_memberships` insert.
- Meglévő user, van membership: create módban skip, upsert módban update.
**Megelőzés**: Bulk import felhasználó-kapcsolatos entitásoknál SOHA ne kerüld meg az auth flow-t. Új user-eket invitációval hozz be — ez biztonságos, audit-elhető, és a meglévő UX-szel konzisztens.

---

## ➕ APPEND — 2026-05-10 Sticky navigáció regresszió-javítás

### [LESSON-UI-080]: Sticky tab-sáv / almenü-sáv — CSS custom property alapú top-offset
**Context**: Többszintű navigáció (főmenü + almenü sávok) sticky pozicionálásánál a `top` értéket az összes sticky ancestor magasságának összegéből kell számítani. Az alkalmazásban két layout mode van: `sidebar` (a főmenü TabsList `sr-only`) és `tabs` (látható főmenü sáv).
**Problem**: A `TabsList` (főmenü sáv) és az almenü sávok (Naptár, Erőforrások) nem kaptak `sticky` pozicionálást. Görgetéskor eltűntek — felhasználó elvesztette a navigációs kontextust.
**Fix**: CSS custom property-k a közös ancestor container `style` prop-ján:
```tsx
style={{
  '--ws-header-h': '53px',                               // header magassága
  '--ws-main-tabs-h': layout === 'sidebar' ? '0px' : '65px',  // 0 ha sidebar mode
} as any}
```
- **Főmenü TabsList** (tabs mode): `sticky top-[var(--ws-header-h)] z-20`
- **Almenü TabsList** (Calendar, Resources): `sticky top-[calc(var(--ws-header-h)_+_var(--ws-main-tabs-h))] z-10 bg-background border-b rounded-none`
- Az almenük maguk öröklik a CSS változókat a DOM-on keresztül — nincs szükség új propra `ResourcesTab`-ban.
- A `_` Tailwind arbitrary értékben szóköznek felel meg: `calc(... + ...)` lesz a generált CSS-ben.
**Megelőzés**: Minden új sticky navigációs sávhoz:
1. Mérd fel a stacking sorrendet (hány sticky réteg van felette?)
2. Adj `bg-background` + megfelelő `z-index`-et (ne legyen 0)
3. Sidebar mode + tabs mode top-offset különbség KÖTELEZŐ (CSS var-ral kezeld)

---

## ➕ APPEND — 2026-05-10 demo seed regresszió

### [HIBA-074] Edge seedben csendben elnyelt részleges insert-hiba → „kész” demo workspace, üres naptár
- **Dátum**: 2026-05-10
- **Fájl**: `supabase/functions/seed-demo-workspace/index.ts`
- **Hibaüzenet**: Frontenden tünetként jelentkezett: a demo workspace létrejött, de a `leave_requests` rekordok nem jelentek meg a Naptár / Idővonal / Kérelmek nézetekben.
- **Gyökérok**: A seed függvény a `leave_requests` insert eredményéből csak a `data` mezőt vette ki, az `error` mezőt nem kezelte. Ha a beszúrás bármely új séma-/policy-/trigger-változás miatt elbukott, a seed ettől még továbbfutott és „sikeres” workspace-et adott vissza, csak éppen szabadságadatok nélkül.
- **Javítás**: A `leave_requests` insert most már explicit `error` ellenőrzést kapott; hiba esetén a függvény logol és `throw`-val megszakítja a demo seedet. Emellett a seed-elágazás feltétele az lett, hogy valóban létezzenek seedelt szabadságtípusok, ne egy félrevezető, nem használt ID-változó.
- **Megelőzés**: Edge function seedekben **SOHA** ne hagyj részleges, üzletileg kritikus insertet fail-soft módban. Minden core demóadat-blokk (`memberships`, `leave_requests`, `projects`, stb.) esetén kötelező az `error` explicit kezelése; ha a blokk a felület működéséhez szükséges, fail-fast kell, nem csendes fallback.

### [HIBA-075] Demo leave seed csak részben kész, ha a kapcsolódó táblák nem követik a tényleges olvasási láncot
- **Dátum**: 2026-05-10
- **Fájl**: `supabase/functions/seed-demo-workspace/index.ts`
- **Hibaüzenet**: Frontenden tünetként jelentkezett: a demo workspace-ben voltak kvóták és egyéb leave entitások, de a napi szabályok üresek maradtak, az éves nézetben pedig a felhasznált napok / maradék nem tükrözte a demo szabadságokat.
- **Gyökérok**: A seed nem a modul teljes adatolvasási láncára készült. Az `enterprise_daily_rules` insertből hiányzott a kötelező `created_by`, az `enterprise_quota_transactions` pedig egyáltalán nem seedelődött, pedig az éves nézet ezt olvassa a quota felhasználás kiszámításához.
- **Javítás**: A daily rules seed már `created_by` mezővel fut, és a jóváhagyott demo `leave_requests` rekordokhoz automatikusan létrejönnek a kapcsolódó `enterprise_quota_transactions` sorok is.
- **Megelőzés**: Seed fejlesztésnél **MINDIG** a teljes UI adatforrás-láncot ellenőrizd, ne csak a „fő” táblát. Ha egy nézet több táblából áll össze (`leave_requests` + `enterprise_leave_quotas` + `enterprise_quota_transactions` + szabálytáblák), akkor a demo seed csak akkor tekinthető késznek, ha mindegyik workspace-scope forrás kap használható rekordokat.

## ➕ APPEND — 2026-03-31 build hiba kiegészítés

### [HIBA-023] Supabase Edge Function `Deno` globál — Next.js build alatti típushiba
- **Dátum**: 2026-03-31
- **Fájl**: `supabase/functions/place-search/index.ts:110`
- **Hibaüzenet**: `Type error: Cannot find name 'Deno'.`
- **Gyökérok**: A Next.js root build / TypeScript ellenőrzés belefutott a `supabase/functions/...` alatti Supabase Edge Function fájlba, ami **Deno runtime-ra** íródott (`Deno.serve(...)`). A Next/Node oldali TypeScript környezet nem ismeri automatikusan a `Deno` globált, ezért a build megállt. A probléma nem maga a business logika, hanem a runtime-keveredés: a Deno-s Edge Function ugyanabban a TypeScript ellenőrzési körben maradt, mint a Next app.
- **Javítás**: A stabil megoldás két részből áll:
  1. A Next app `tsconfig.json` fájljából ki kell zárni a `supabase/functions/**/*` útvonalat, hogy a Next build ne próbálja Node/Next környezetben típusellenőrizni a Deno Edge Functionöket.
  2. Az Edge Function saját Deno/Supabase runtime típusreferenciát kapjon, és külön Supabase / Deno folyamatban legyen ellenőrizve (például a projektben használt Supabase edge runtime type importtal).
- **Megelőzés**: **SOHA** ne hagyd a Deno runtime-ra írt Supabase Edge Function fájlokat a Next.js root typecheck hatókörében. Node/Next build és Supabase Edge Function typecheck legyen külön kezelve. Ha új Edge Function készül, azonnal ellenőrizd, hogy:
  - a `supabase/functions/**` mappa ki van-e zárva a root `tsconfig.json`-ból;
  - az adott function rendelkezik-e a szükséges Deno / Supabase edge runtime típusreferenciával;
  - az ellenőrzése külön Supabase / Deno parancsból történik-e, nem `npm run build` alatt.

## 📋 ELLENŐRZŐ LISTA — új buildbiztonsági pontok

- [ ] A `supabase/functions/**` mappa ki van zárva a Next.js root `tsconfig.json` typecheckjéből?
- [ ] A Deno runtime-os Edge Function saját runtime típusreferenciával rendelkezik?
- [ ] A Supabase Edge Function ellenőrzése külön történik a Next app buildtől?

*Appendelve: 2026-03-31 — v1.3.3*

### [HIBA-017] Supabase enum típus nem illeszkedik string filterhez
- **Dátum**: 2026-04-11 (v2.1.0)
- **Fájl**: `src/components/enterprise/ApprovalInbox.tsx:61`
- **Hibaüzenet**: `Argument of type 'string' is not assignable to parameter of type 'NonNullable<"approved" | ...>'`
- **Gyökérok**: A Supabase SDK generált típusai szűk uniót várnak, de a React state `string`-ként tárolta a filter értéket
- **Javítás**: `as any` cast alkalmazása a `.eq('status', statusFilter as any)` hívásban
- **Megelőzés**: Supabase `.eq()` hívásokban ha a filter értéke React state-ből jön és az enum típus szűk, használj explicit castot

*Appendelve: 2026-04-11 — v2.1.0*

---

## 🟢 KATEGÓRIA — Architektúra: Single Source of Truth (2026-04-22, v2.5.0)

### [LESSON-SSOT-001] Pozíció ↔ tag allokáció listázása JOIN-on keresztül
- **Dátum**: 2026-04-22
- **Fájl**: `src/components/enterprise/BusinessRoleManager.tsx`
- **Probléma**: A pozíció kártya csak 1 tagot mutatott, miközben a Member Profile %-os allokációt is támogat → adat-dissonance.
- **Gyökérok**: A korábbi lekérdezés a `business_role` mezőre szűrt a `enterprise_memberships`-en (régi 1:1 modell), figyelmen kívül hagyva az `enterprise_member_role_allocations` junction táblát.
- **Javítás**: Always read az allokációt a junction táblából, és join-old a `enterprise_memberships` + `profiles` adatokkal. UI-ban listázd MINDEN tagot %-os értékkel és számolt napi órával (`base_working_hours * pct / 100`).
- **Megelőzés**: Ha létezik junction tábla bármilyen N:M kapcsolatra, sose dolgozz a denormalizált, régi szűrőmezővel — a junction table az igazság forrása.

### [LESSON-SSOT-002] Dropdown / szűrő hardcoded listák tilosak
- **Dátum**: 2026-04-22
- **Fájl**: `src/components/enterprise/LeaveCalendar.tsx`
- **Probléma**: A csapat-szűrő hardcoded értékeket mutatott, eltérve a Settings → Teams modultól.
- **Javítás**: Minden szűrő opciólistát ugyanabból a Supabase táblából tölts (`enterprise_teams`), amit a CRUD-ot végző modul is használ.
- **Megelőzés**: Ha egy entitásnak van CRUD UI-ja, sose duplikáld statikusan máshol — egyetlen forrás (DB tábla / view) létezzen.

### [LESSON-PERMISSIONS-001] Hierarchikus jogosultság-fa katalógus táblából
- **Dátum**: 2026-04-22
- **Fájl**: `src/hooks/useEnterprisePermissions.ts`, `RolePermissionManager.tsx`
- **Probléma**: Statikus, lapos `FEATURE_GROUPS` array nehezen tartható szinkronban a tényleges navigációs fával.
- **Javítás**: `enterprise_feature_catalog` tábla bevezetése `parent_key` self-FK-val. A hook `featureTree`-t épít, az UI rekurzív komponenssel rendereli (`FeatureTreeRow`). Fallback: ha a katalógus üres, marad a régi flat lista.
- **Megelőzés**: Bármi, ami "tükrözi az alkalmazás struktúráját", legyen DB-ben tárolt fa, ne forrásban kódolt enum.

### [LESSON-CAPACITY-001] Kapacitás óra-egységben, ne csak százalékban
- **Dátum**: 2026-04-22
- **Fájl**: `src/lib/capacityEngine.ts`
- **Probléma**: Csak %-ban számolt kapacitás félrevezető részmunkaidős (4–6 órás) tagoknál.
- **Javítás**: Új `base_working_hours` mező a membership-en; minden ouputba (PositionSummary) számolj `total_available_hours = base_working_hours * (pct / 100)`-ot is.
- **Megelőzés**: Ha valós erőforrás-tervezést támogatunk, mindig legyen abszolút mértékegység (óra/nap), ne csak relatív arány.

### [LESSON-DASH-001] Új enterprise panelek integrálása csak meglévő tab/section alá
- **Dátum**: 2026-04-25
- **Fájl**: `src/components/enterprise/WorkspaceDashboard.tsx`
- **Probléma**: Új funkciók gyors beépítésekor könnyű külön tabot/szekciót nyitni, ami duplikált navigációt és regressziós kockázatot okoz.
- **Javítás**: Az új panelek (`AllowanceManager`, `WorkspaceGeneralSettings`, `BrandingManager`, `CsvImportPanel`) kizárólag meglévő `SettingsSection` blokkokba kerültek.
- **Megelőzés**: Dashboard bővítésnél elsődleges szabály: meglévő tab-hierarchiát bővíts, ne hozz létre párhuzamos felületet.

### [LESSON-CALENDAR-002] Éves nézetet sub-tabként kell integrálni, nem külön route-on
- **Dátum**: 2026-04-25
- **Fájl**: `src/components/enterprise/WorkspaceDashboard.tsx`, `src/components/enterprise/AnnualLeaveGrid.tsx`
- **Probléma**: Az éves naptár nézet külön route/tab esetén szétszórja a felhasználói flow-t.
- **Javítás**: A `Calendar` fülön belül másodlagos sub-tab struktúra (`Naptár`, `Éves nézet`) került bevezetésre.
- **Megelőzés**: Naptár-funkciók bővítésekor maradj a meglévő Calendar kontextusban.

### [LESSON-TEAMS-003] Team policy mezők szerkesztését validációval és blur-commit mintával kezeld
- **Dátum**: 2026-04-25
- **Fájl**: `src/components/enterprise/TeamManager.tsx`
- **Probléma**: Számmező közvetlen onChange mentése túl sok írást és hibás értéket okozhat.
- **Javítás**: `max_absent` mező draft state-ben tárolódik, és csak `onBlur` eseménykor mentődik validáció után; `approval_mode` explicit select opciókkal állítható.
- **Megelőzés**: Policy típusú mezőknél használj draft + commit mintát, ne per-keystroke adatbázis frissítést.

---

## ➕ APPEND — 2026-04-27 v2.5.1 Calendar/Capacity regression hotfix

### [LESSON-SCHEMA-002] Supabase schema-cache safe frontend payload kötelező új opcionális oszlopoknál
- **Dátum**: 2026-04-27
- **Fájl**: `src/components/enterprise/OfficeCoverageRuleManager.tsx`
- **Probléma**: A telephelyi szabály mentése eldobta a felületet `Could not find the 'business_roles' column of 'enterprise_office_coverage_rules' in the schema cache` hibával.
- **Gyökérok**: A frontend azonnal írta az új `business_roles` / `skill_ids` tömb oszlopokat, de a production Supabase/PostgREST schema cache vagy az aktuális adatbázis még a régi sémát látta.
- **Javítás**: Az új tömb oszlopos payload megmaradt elsődleges útnak, de PGRST/schema-cache hiányzó oszlop hibánál automatikus legacy fallback fut csak a régi `business_role` / `skill_id` oszlopokkal.
- **Megelőzés**: Új opcionális DB oszlop bevezetésekor a frontend írás legyen backward-compatible: régi payload fallback, célzott error-detekció, és csak az érintett schema-cache hibák kezelése fallbackként.

### [LESSON-UI-REGRESSION-004] Navigációs shortcutot ne hagyj bent, ha nem garantáltan jó célra visz
- **Dátum**: 2026-04-27
- **Fájl**: `src/components/enterprise/calendar/CoveragePlannerView.tsx`, `src/components/enterprise/WorkspaceDashboard.tsx`
- **Probléma**: A Kapacitástervező `Szabályok szerkesztése` gombja rossz helyre navigált.
- **Gyökérok**: A shortcut nem konkrét szekcióra/deep-linkre vitt, hanem túl általános tabváltást végzett.
- **Javítás**: A gomb eltávolításra került a Kapacitástervezőből; a szabálykezelés továbbra is a saját meglévő beállítási/szabálykezelő felületén érhető el.
- **Megelőzés**: Shortcut csak akkor maradhat, ha célzottan és validáltan a megfelelő modulrészhez visz. Ellenkező esetben jobb eltávolítani, mint félrenavigálni.

### [LESSON-CALENDAR-003] Havi gridben az üres állapot nem növelheti soronként a cellamagasságot
- **Dátum**: 2026-04-27
- **Fájl**: `src/components/enterprise/calendar/CoveragePlannerView.tsx`
- **Probléma**: Havi Kapacitástervező nézetben a `Nincs szabály — adj hozzá...` szöveg tördelődött és túl magas sorokat okozott.
- **Gyökérok**: A dinamikus `col-span-${colCount}` Tailwind class nem garantáltan generálódik, ezért a szöveg nem valódi többoszlopos cellaként viselkedett.
- **Javítás**: Szöveges üres állapot helyett napcellánként visszafogott szürke jelölés és vékony `border-border/70` elválasztó vonalak jelennek meg.
- **Megelőzés**: Dinamikus Tailwind class helyett inline style vagy explicit renderelt grid cellák használata szükséges változó oszlopszámnál.

### [LESSON-LAYOUT-002] Timeline szűrőpanel desktopon oldalsáv, nem teljes szélességű blokk
- **Dátum**: 2026-04-27
- **Fájl**: `src/components/enterprise/calendar/TimelineView.tsx`
- **Probléma**: Az Idővonal szűrők túl szélesen, teljes sorban jelentek meg.
- **Javítás**: Desktopon kétoszlopos grid layout: keskeny sticky bal oldali szűrősáv + mellette naptárterület. Mobilon megmarad az egymás alatti elrendezés.
- **Megelőzés**: Nagy naptár/grid nézetnél a konfigurációs/szűrőpanelt külön `aside` régióban kell kezelni, hogy ne nyomja le a fő tartalmat.

---

## ➕ APPEND — 2026-04-30 Produkció-stabilizálási audit (audit-stabilize-production)

### [LESSON-CONFLICT-001] officeRuleApplies ne hagyja ki a legacy `day_of_week` (singular) oszlopot
- **Dátum**: 2026-04-30
- **Fájl**: `src/lib/conflictEngine.ts`
- **Probléma**: A `ruleApplies` (daily rules) és az `officeRuleApplies` (coverage rules) függvény eltérően kezelte a `days_of_week` / `day_of_week` mezőket. A `officeRuleApplies` csak a tömb verziót (`days_of_week`) vizsgálta, ha az üres volt, visszaesett `[]`-re, ami miatt a rule minden napra érvényesnek számított (nem csak a konfigurált napra).
- **Gyökérok**: A multi-position/skill migrációkor az `officeRuleApplies` nem kapta meg a `ruleApplies`-ból már ismert legacy fallback logikát.
- **Javítás**: `officeRuleApplies` átírva: ha `days_of_week` tömb üres/null, fallback a `day_of_week` (singular) mezőre — azonos logika, mint `ruleApplies`.
- **Megelőzés**: Ha két kódhely azonos adatmodellt értelmez (rule applies), legyen egyetlen helper, ne duplikált, eltérő implementáció.

### [LESSON-QUERY-SCOPE-001] Supabase query-t mindig szűkítsd a szükséges dátumtartományra
- **Dátum**: 2026-04-30
- **Fájl**: `src/lib/conflictEngine.ts`
- **Probléma**: Az `enterprise_holidays` és `leave_requests` lekérdezések a TELJES workspace-rekordhalmazt hozták le dátumszűrés nélkül, majd JavaScript-szinten szűrtek.
- **Gyökérok**: A feature gyors fejlesztésekor a legegyszerűbb query-t írták meg, és a JS-oldali szűrés elfedi a felesleges adatátvitelt.
- **Javítás**: Mindkét lekérdezésbe bekerültek a `.gte` / `.lte` feltételek a kért napok tartományára.
- **Megelőzés**: Minden Supabase lekérdezésnél gondold végig: van-e dátum- vagy ID-alapú határoló feltétel, amit a DB-nek kell elvégeznie? Ne hagyd a szűrést csak JS-oldalra.

### [LESSON-CAPACITY-ERROR-001] Párhuzamos Supabase-hívások hibáit mindig logold
- **Dátum**: 2026-04-30
- **Fájl**: `src/lib/capacityEngine.ts`
- **Probléma**: A `computeWorkspaceCapacity` a `Promise.all`-ból érkező Supabase válaszokat destrukturáló módon kezelte (`{ data: allocs }`), az `.error` mezőt teljesen figyelmen kívül hagyva. Ha a lekérdezés meghiúsult, `allocs = null` lett, és a kapacitásszámítás némán nulla-eredményt adott vissza.
- **Gyökérok**: A Supabase SDK nem dob kivételt hálózati/RLS hibánál, hanem `{ data: null, error: ... }` formát ad vissza, amit explicit kezelni kell.
- **Javítás**: Minden párhuzamos hívás eredménye `allocsResult / membershipsResult` névvel van szétválasztva, `.error` mező ellenőrzött és konzolra logolt.
- **Megelőzés**: **MINDIG** destrukturáld ki az `.error` mezőt is, ne csak a `.data`-t. Engine-szintű üzleti logikánál egyetlen néma hiba elfedi az egész számítás helytelenségét.

### [LESSON-ADO-UPDATE-001] ADO update előtt ellenőrizd, hogy van-e módosítandó mező
- **Dátum**: 2026-04-30
- **Fájl**: `supabase/functions/jira-devops-proxy/index.ts`
- **Probléma**: Az `adoUpdate` függvény üres `ops = []` tömböt küldhetett Azure DevOps API-nak, ha a payload egyetlen szerkeszthető mezőt sem tartalmazott. Az ADO API üres JSON-patch-et visszautasítja, és félrevezető hibát ad.
- **Gyökérok**: Nem volt guard a `ops` tömb összerakása előtt.
- **Javítás**: `if (ops.length === 0) throw new Error(...)` kerül a `fetch` előtt.
- **Megelőzés**: Bármely patch/update API hívásnál, ahol a payload opcionális mezőkből épül fel, ellenőrizd, hogy legalább egy mező ki van töltve, mielőtt elküldenéd a kérést.

### [LESSON-JIRA-PROJECTKEY-001] sync_project_config ne fusson üres project_key-jel
- **Dátum**: 2026-04-30
- **Fájl**: `supabase/functions/jira-devops-proxy/index.ts`
- **Probléma**: A `jiraSyncProjectConfig` `integ.project_key ?? ''`-vel dolgozott. Ha a project_key nincs kitöltve az integrációs rekordban, az API-hívás üres project key-jel fut le, ami vagy rossz adatot hoz, vagy félrevezető Jira API hibát ad.
- **Gyökérok**: Nem volt korai validáció a project_key meglétére.
- **Javítás**: Explicit `if (!integ.project_key) throw new Error(...)` a függvény elején.
- **Megelőzés**: Minden külső API-hívás előtt validáld az összes kötelező paramétert, ne hagyd a null/empty értéket „csendesen" az API-nak.

---

## ➕ APPEND — 2026-05-09 Demo workspace seeder v8 tanulságok

### [LESSON-SEED-001] Supabase Auth Admin API — közvetlen fetch Deno edge functionban
- **Dátum**: 2026-05-09 (v8 seeder)
- **Fájl**: `supabase/functions/seed-demo-workspace/index.ts`
- **Probléma**: A `@supabase/supabase-js` SDK `auth.admin.createUser()` metódusa nem mindig érhető el megbízhatóan Deno edge function kontextusból — vagy hiányzó header, vagy undefined visszatérési érték, vagy runtime-version-függő viselkedés.
- **Gyökérok**: A Supabase JS SDK service-role auth.admin wrapperje edge function futtatásnál nem feltétlenül küldi el a szükséges `Authorization: Bearer <service_role_key>` headert, ami néma hibát okoz.
- **Javítás**: Közvetlen `fetch` hívás a Supabase REST Admin API-ra: `fetch(\`\${SUPABASE_URL}/auth/v1/admin/users\`, { method: 'POST', headers: { Authorization: \`Bearer \${SERVICE_ROLE_KEY}\`, apikey: SERVICE_ROLE_KEY, 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })`. Az érkező JSON-ból `resp.json()` kell, és a `id` mező az auth user UUID.
- **Megelőzés**: Ha edge functionből service role-lal kell auth user-t létrehozni, MINDIG közvetlen REST API fetch mintát használj, ne a JS SDK `auth.admin.*` metódusaira bízd. Documéntáld a fejléceket kommentben a kódban.

### [LESSON-SEED-002] PERSONA_ORG_ASSIGNMENTS — adatvezérelt lookup tábla az összes personára
- **Dátum**: 2026-05-09 (v8 seeder)
- **Fájl**: `supabase/functions/seed-demo-workspace/seed-data.ts`
- **Probléma**: A v7-es seeder hardcoded if-else blokkokban csak 7/22 personának rendelt org_unit / manager / leadership_level / contract_type értéket. A maradék 15 tag hiányos org-struktúrával jött létre — SQL-ellenőrzés igazolta: `has_org_unit: 7/23`.
- **Gyökérok**: Személynév-alapú hardcoded hozzárendelés nem skálázható; ha új persona kerül be, a B8 blokkot kézzel kell bővíteni, és könnyen kihagyható valaki.
- **Javítás**: `PERSONA_ORG_ASSIGNMENTS: Record<string, PersonaOrgAssignment>` lookup objektum minden persona `display_name`-jéhez hozzárendeli az `{orgUnit, llCode, contractCode, leadershipCategory, seniority, managerName?}` struktúrát. A B8 seeding blokk egyszerűen iterál minden `demoUserId`-n, megkeresi a display_name-t, majd lookup-ol.
- **Megelőzés**: Összetett hierarchikus adat (org-struktúra, manager-lánc, több mező kombinációja) SOHA ne legyen hardcoded if-else mint — mindig `Record<string, T>` lookup tábla vagy konfigurációs objektum a `seed-data.ts`-ben.

### [LESSON-SEED-003] Min-enforced slice pattern a katalógus insert blokkokban
- **Dátum**: 2026-05-09 (v8 seeder)
- **Fájl**: `supabase/functions/seed-demo-workspace/index.ts`
- **Probléma**: Ha a felhasználó a seed config UI-ban pl. `leadership_levels=1`-et állít be, de a B8 blokk 4 különböző `llCode`-ot próbál feloldani (`strategic`, `operational`, `technical`, `execution`), az FK lookup `undefined`-ot ad vissza, és az UPDATE meghiúsul.
- **Gyökérok**: A seed config mennyiségeket a felhasználó szabadon csökkenthetné, ami letörheti a downstream FK-függőségeket.
- **Javítás**: `DEFS.slice(0, Math.max(MIN_COUNT, seedQty.key))` — a `MIN_COUNT` értékét minden katalógusnál a downstream FK-függőségek alapján kell meghatározni (pl. leadership levels min=4, contract types min=2).
- **Megelőzés**: Ha egy katalógus INSERT blokk más blokkok FK-forrása, MINDIG adj hozzá `Math.max(min, qty)` védelmet a slice-ba. Kommentben dokumentáld a min értéket és a függőség okát.

### [LESSON-SEED-004] Map-alapú FK-feloldás multi-entity seeder blokkokban
- **Dátum**: 2026-05-09 (v8 seeder)
- **Fájl**: `supabase/functions/seed-demo-workspace/index.ts`
- **Probléma**: A katalógus INSERT után UUID-k szükségesek a downstream FK mezőkhöz (`leadership_level_id`, `contract_type_id`, stb.). Iteratív DB lekérdezés minden egyes member-hez O(n) extra round-trip-et jelent.
- **Gyökérok**: Az INSERT eredmény UUID-jai elvesznek, ha nem tároljuk el; a következő blokk már nem tudja FK-ként feloldani őket anélkül, hogy újra lekérdezné.
- **Javítás**: Az INSERT válasz után azonnal Map-et építsünk: `const llByCode = new Map(llRows.map(r => [r.code, r.id]))`. A downstream blokk egyszerűen `llByCode.get(assignment.llCode)` — nulla extra DB round-trip.
- **Megelőzés**: Multi-entity seederben minden katalógus INSERT után AZONNAL építsd fel a `code→UUID` (vagy `name→UUID`) Map-et. A teljes seeder futás O(1) lookup-okkal dolgozzon, ne O(n) extra query-kel. Kövesd ezt a sorrendet: INSERT → `new Map(rows.map(...))` → downstream block uses Map.

### [LESSON-GOVERNANCE-001] entity-creation-inventory.md — governance source of truth az összes seedelt entitáshoz
- **Dátum**: 2026-05-09 (v8 seeder)
- **Fájl**: `.governance/entity-creation-inventory.md`
- **Probléma**: Az új katalógus entity típusok (job families, leadership levels, contract types, industries, work categories) be lettek illesztve a seederbe, de a governance dokumentum nem tükrözte ezt — audit trail hiány, és a következő fejlesztő nem látja, mi van seedelve.
- **Gyökérok**: A fejlesztés a governance dokumentum frissítése nélkül haladt.
- **Javítás**: Az `entity-creation-inventory.md` 2.1–2.5 szekciói frissítve ✅ markerekkel és seed config kulcsokkal.
- **Megelőzés**: Minden új seeded entity type bevezetésekor ELŐSZÖR frissítsd az `entity-creation-inventory.md`-t, MAJD implementáld a seeder kódot. A governance dokumentum a source of truth — a kód azt kövesse, ne fordítva.

---

## ➕ APPEND — 2026-05-09 Auth, routing, branding, katalógus és UI tanulságok (PRs #1–28)

### [LESSON-AUTH-OAUTH-001] Google OAuth URL fragment session restoration
- **Dátum**: 2026-05-04 (PR #1, #2)
- **Fájl**: `src/pages/Auth.tsx`, `src/hooks/useAuth.tsx`, `src/pages/Landing.tsx`
- **Probléma**: Google OAuth visszatérés után a `access_token` és `refresh_token` a `window.location.hash`-ben (`#access_token=...`) érkezett, de az app nem parseolta a hash-t — session nem lett visszaállítva, a UI az `/auth` oldalon fagy.
- **Gyökérok 1**: `Auth.tsx` nem olvasta a `window.location.hash` fragmentet `?oauth=google` esetén.
- **Gyökérok 2**: A `redirectTo: '/auth'` az OAuth provider-nél a Cloudflare Pages-en hard-404-ot okoz, mert az SPA shell nem töltődik be közvetlen navigációnál.
- **Javítás**: (a) Explicit hash-token feldolgozás `Auth.tsx`-ben: `?oauth=google` + nem hydratált user esetén `window.location.hash`-ből kiolvasni az access/refresh token-t, `setSessionFromTokens()` hívás, majd `history.replaceState` a hash törlésére. (b) `redirectTo` átírva `/`-re (mindig kiszolgált). (c) `AuthProvider`-ben centralizált fragment-feloldás app bootstrap-kor. (d) Landing page navigál a `redirect` targetbe miután session + `?oauth=google` detektálva.
- **Megelőzés**: OAuth `redirectTo` SOHA ne mutasson `/auth`-ra SPA-ban — mindig a root `/` az, ami biztosan kiszolgált. Az `#access_token=...` URL fragmentet a kliens oldalon kell kézzel kezelni; `history.replaceState`-tel töröld a feldolgozás után.

### [LESSON-ROUTING-SPA-001] Cloudflare Pages SPA fallback — `_redirects` formátum kritikus
- **Dátum**: 2026-05-04 (PR #6, #7, #8)
- **Fájl**: `public/_redirects`, `public/404.html`, `src/App.tsx`
- **Probléma**: Direkt URL navigáció (`/auth`, `/app?tab=resources` stb.) 404-et adott Cloudflare Pages-en.
- **Gyökérok**: A `_redirects` fájlnak PONTOSAN egyszeri szóközzel kell elválasztani a mezőket: `/*  /index.html  200`. Kettős szóköz vagy tab elvétheti a Cloudflare / Netlify parsert.
- **Háromrétegű megoldás**:
  1. `public/_redirects`: `/*  /index.html  200` (szimpla szóköz formátum)
  2. `public/404.html`: eltárolja a teljes path+query+hash-t `sessionStorage`-ban, átirányít `/?r=...`-re
  3. `src/App.tsx` `SpaRedirectHandler`: `sessionStorage`/`?r=` alapján kliens oldali navigáció
- **Megelőzés**: Új Cloudflare Pages / Netlify SPA deploymentnél MINDIG ellenőrizd a `_redirects` szóköz-formátumát. A `404.html` fallback + `SpaRedirectHandler` páros a legellenállóbb megoldás mert statikus hostoknál is működik.

### [LESSON-ROUTING-SPA-002] URL UUID-mentesítés: workspace ID localStorage-ban, ne URL-ben
- **Dátum**: 2026-05-04 (PR #3)
- **Fájl**: `src/pages/Enterprise.tsx`, `src/App.tsx`, `src/hooks/useAuth.tsx`
- **Probléma**: A `?ws=<uuid>` URL param lehetővé tette a workspace UUID kiszivárgását a böngésző history-ban, megosztott linkekben, analytics toolokban.
- **Gyökérok**: Az aktív workspace azonosítását az URL-re bízta a kód, ahelyett hogy kliens-oldali state-ben tárolná.
- **Javítás**: Workspace feloldási sorrend: (1) `?ws=` egyszer, backward compat → (2) `localStorage.active_workspace_id` → (3) első elérhető workspace. A `?ws=` paraméter `history.replaceState`-tel el lesz távolítva a feloldás után. `/enterprise` backward-compat redirect alias → `/app`.
- **Megelőzés**: Munkamenethez kötött belsős azonosítók (UUID-k, session ID-k) NE kerüljenek az URL-be. Használj `localStorage` vagy `sessionStorage` persistent state-et. Deep-link-ek csak olvasható, nem szenzitív paramétereket tartalmazzanak (pl. `?tab=`).

### [LESSON-SUPABASE-SDK-VERSION-001] Supabase JS verzió kompatibilitás Deno edge function-ökben
- **Dátum**: 2026-05-09 (PR #23)
- **Fájl**: `supabase/functions/seed-demo-workspace/index.ts`
- **Probléma**: Supabase JS `v2.45.0`-val a service role admin client minden insert operációt némán eldobott Deno edge function runtime-ban — sem hiba, sem log, sem beillesztett sor.
- **Gyökérok**: A `v2.45.0` SDK auth layer a Deno runtime-ban nem kezelte megfelelően a service role token-t a schema-szintű insert hívásokban.
- **Javítás**: Upgrade `v2.98.0`-ra (amely a többi működő edge functionben, pl. `create-instant-enterprise-member`, már jelen volt). Explicit auth options: `autoRefreshToken: false, persistSession: false, detectSessionInUrl: false`. Smoke test a startup-ban.
- **Megelőzés**: Edge function SDK verziót MINDIG az összes többi működő edge functionnel egységesítsd. Ha egy edge function néma hibával meghiúsul (no error, no rows), ELŐSZÖR az SDK verziót ellenőrizd — ez a leggyakoribb rejlő ok Deno futtatásnál.

### [LESSON-SELECTITEM-EMPTY-001] Radix UI `SelectItem` üres string `value` crash
- **Dátum**: 2026-05-09 (PR #21)
- **Fájl**: `src/components/enterprise/*` — minden Radix `Select` komponens
- **Probléma**: `<SelectItem value="">` futásidejű hibát dob Radix UI-ban — az üres string mint "nincs kiválasztva" sentinel érték nem támogatott.
- **Gyökérok**: A Radix UI Select component belső logikája az üres stringet érvénytelen értékként kezeli és kivételt dob.
- **Javítás**: Minden `value=""` helyettesítve nem-üres sentinel értékkel (pl. `value="__none__"`, `value="unselected"`).
- **Megelőzés**: Radix UI `Select` és `SelectItem` komponenseknél SOHA ne használj üres string `value`-t. Az "üres / nincs kiválasztva" állapothoz mindig használj nem-üres placeholder értéket, pl. `"__none__"` vagy a domain-specifikus sentinel.

### [LESSON-EMAIL-DIACRITIC-001] Magyar ékezetes karakterek slugify-álása email-generálásban
- **Dátum**: 2026-05-09 (PR #25)
- **Fájl**: `supabase/functions/seed-demo-workspace/index.ts`
- **Probléma**: Magyar nevekből generált email-ek (pl. `"Viktor Mátyás"` → `"viktor.matyas@demo.test"`) ékezetes karaktereket tartalmaztak, amelyek email-validáción elbuknak.
- **Gyökérok**: Közvetlen lowercase + `.replace(' ', '.')` transzformáció az ékezetes betűket megtartja.
- **Javítás**: `slugify()` helper: unicode normalizer (`NFD`) + `/[̀-ͯ]/g` regex strip + alphanum-only szűrő. `"Mátyás"` → `"matyas"`, `"Ádám"` → `"adam"`.
- **Megelőzés**: Névből generált email-eknél, username-eknél, slug-oknál MINDIG alkalmazz diacritic normalizálást. A magyar ábécé problémás karakterek: á→a, é→e, í→i, ó/ö/ő→o, ú/ü/ű→u. Email domain: `.local` helyett `.test` (RFC 2606 szerint a `.test` az ajánlott tesztkörnyezeti TLD).

### [LESSON-CATALOG-RLS-001] Globális katalógus táblák: RLS engedélyezve, de policy nélkül = 0 sor
- **Dátum**: 2026-05-09 (PR #22)
- **Fájl**: `supabase/migrations/` — `enterprise_catalog_categories`, `enterprise_catalog_roles`, `enterprise_catalog_skills`, `enterprise_catalog_role_skills`
- **Probléma**: A globális katalógus táblák RLS-sel lettek létrehozva, de egyetlen SELECT policy sem volt definiálva. Eredmény: minden `authenticated` user lekérdezése 0 sort adott vissza — `PositionPickerDialog` mindig üres volt.
- **Gyökérok**: A migráció hozzáadta az `ENABLE ROW LEVEL SECURITY`-t, de elfelejtette hozzáadni az olvasási policy-t a megosztott (nem workspace-scoped) globális táblákhoz.
- **Javítás**: `CREATE POLICY ... FOR SELECT USING (auth.uid() IS NOT NULL)` minden globális katalógus táblán.
- **Megelőzés**: Ha egy táblán `ENABLE ROW LEVEL SECURITY` szerepel, MINDIG adj hozzá legalább egy olvasási policy-t. Globális, workspace-független adatoknál (`enterprise_catalog_*`) a `authenticated` role számára egyszerű `auth.uid() IS NOT NULL` policy elegendő. Checklist: minden `ENABLE ROW LEVEL SECURITY` mellé kell legalább egy USING feltétel.

### [LESSON-POSITION-SOURCE-001] Pozíció dropdown: mindkét forrás szükséges (legacy + junction tábla)
- **Dátum**: 2026-05-04 (PR #6)
- **Fájl**: `src/components/enterprise/TeamManager.tsx`, `src/components/enterprise/InviteMemberDialog.tsx`
- **Probléma**: A pozíció dropdownok kizárólag az `enterprise_memberships.business_role` (legacy text oszlop) alapján épültek. Az `enterprise_member_role_allocations` junction táblában létrehozott pozíciók láthatatlanok maradtak minden dropdownban.
- **Gyökérok**: A junction tábla volt a kanonikus forrás, de a UI csak a régi denormalizált oszlopot nézte.
- **Javítás**: Mindkét forrás párhuzamos lekérdezése + halmazegyesítés: `[...new Set([...legacyRoles, ...allocationRoles])]`.
- **Megelőzés**: Ha létezik junction tábla bármely N:M kapcsolatra, a UI MINDIG innen olvassa az opciókat — nem a denormalizált legacy oszlopból. Ha backward compat miatt mindkettő létezik, MINDIG mergeld a kettőt.

### [LESSON-ORGCHART-FLATTEN-001] Org fa pre-flattening premium rendererhez
- **Dátum**: 2026-05-09 (PR #28)
- **Fájl**: `src/components/enterprise/organization/OrgChartPremiumView.tsx`
- **Probléma**: Rekurzív fa-renderelés során minden kártya megnyitásakor újra kellett bejárni a fát a manager/gyerek relációk feloldásához — O(n²) komplexitás nagy csapatoknál.
- **Gyökérok**: A fa-struktúra nem volt indexelve, mindig traversal kellett.
- **Javítás**: A render előtt egyszer `flatNodes: Map<string, OrgNode>` map buildek a teljes fából — ezután minden lookup O(1). A drawer adatai (manager neve, közvetlen beosztottak listája) egy `Map.get(id)` hívással elérhetők.
- **Megelőzés**: Bármely fa-alapú UI rendernél (org chart, comment thread, category tree) MINDIG prepare-elj egy `Map<id, node>` flat lookup structure-t a render előtt. A rekurzív traversal csak az initial tree build-hez szükséges, utána minden lookup O(1) legyen.

### [LESSON-THEME-CSS-001] CSS változó token alapú téma rendszer — 6 sablon
- **Dátum**: 2026-05-04 (PR #5)
- **Fájl**: `src/hooks/useTheme.tsx`, `src/styles/themes.css`
- **Probléma**: Komponensekbe kódolt szín- és stílusértékek témaváltást lehetetlenné tesznek, vagy minden egyes komponenst módosítani kell.
- **Gyökérok**: Közvetlenül Tailwind szín-class-ok komponens szinten, nincs CSS változó réteg.
- **Javítás**: `themes.css`-ben CSS változók definiálva minden témához (`enterprise`, `nebula`, `aurora`, `graphite`, `sunrise`, `mono`). `<html>` root osztályváltás (`document.documentElement.classList`) + `localStorage` perzisztencia. Komponensek ugyanazon token neveket használják — csak a gyökerükön definiált értékek változnak.
- **Megelőzés**: Témát igénylő alkalmazásoknál MINDIG CSS változó token réteget vezess be. A komponensek ne hard-coded Tailwind színeket (`bg-purple-600`) használjanak, hanem semantic token osztályokat (`bg-primary`). A témaváltás egyetlen osztálycsere legyen a `<html>` elemen.

---

## ➕ APPEND — 2026-05-09 Versioning fájlokból kinyert mélyebb technikai tanulságok

### [LESSON-JIRA-PROJECTID-001] Jira API: numeric project ID vs. project key — különböző endpointok különbözőt várnak
- **Dátum**: 2026-05-08 (v3.1.1, versioning: 08052601)
- **Fájl**: `supabase/functions/jira-devops-proxy/index.ts`
- **Probléma**: `jiraSyncProjectConfig` a `/rest/api/3/issuetype/project?projectId=SYN` formát használta. Az `issuetype/project` endpoint a `projectId` query paramétereként **numerikus ID-t** vár (pl. `10000`), nem projekt kulcsot (pl. `SYN`) — Jira 500-at dobott.
- **Gyökérok**: A Jira API különböző endpointokon különböző azonosítótípust vár: `projectId` (numerikus) vs. `projectIdOrKey` (mindkettő elfogadott). A project key ≠ project ID.
- **Javítás**: `GET /rest/api/3/project/{projectIdOrKey}` — ez visszaadja a projektet annak `issueTypes` tömbjével együtt. Fallback idősebb tenant-okhoz: `GET /rest/api/3/issue/createmeta?projectKeys={key}`.
- **Megelőzés**: Minden Jira API hívásnál ellenőrizd az endpoint dokumentációját: `projectId` (numerikus) vs. `projectIdOrKey` (string kulcs is elfogadott). Ha nem vagy biztos, a `/project/{projectIdOrKey}` az univerzálisan biztonságos form.

### [LESSON-JIRA-SEARCH-CASCADE-001] Jira search API: háromszintű endpoint cascade a kompatibilitáshoz
- **Dátum**: 2026-05-06 (v3.0.1, versioning: 06052601)
- **Fájl**: `supabase/functions/jira-devops-proxy/index.ts`
- **Probléma**: A Jira Cloud tenant-ok különböző API verziókra vannak, és nem minden tenant válaszol ugyanazon a search endpoint-on. Egyetlen fixen beégetett endpoint 410 Gone vagy üres eredmény hibát adott.
- **Gyökérok**: Az Atlassian a `/rest/api/3/search`-t deprecálta a `/rest/api/3/search/jql` javára, de a migráció tenant-szintű és lépcsőzetes — nem minden tenant frissített.
- **Javítás**: Cascade sorrend: `POST /rest/api/3/search/jql` → `GET /rest/api/3/search/jql` → `GET /rest/api/3/search` (legacy). Az első 2xx válasz nyer.
- **Megelőzés**: Külső SaaS API hívásoknál, ahol a provider fokozatosan deprecál endpointokat, mindig implementálj fallback cascade-et. Ne feltételezd, hogy minden tenant azonos API verziót futtat.

### [LESSON-ADF-PLAINTEXT-001] Jira description Atlassian Document Format (ADF) — plain text konverzió kötelező
- **Dátum**: 2026-05-06 (v3.0.1, versioning: 06052601)
- **Fájl**: `supabase/functions/jira-devops-proxy/index.ts`
- **Probléma**: A Jira `fields.description` mező **nem plain text** — Atlassian Document Format (ADF) JSON objektum érkezik vissza. Közvetlen mentés az `enterprise_agile_issues.description`-be ADF JSON-t tárol, ami az UI-ban megjeleníthetetlen.
- **Gyökérok**: Az ADF a Jira modern rich-text formátuma: `{ "type": "doc", "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "..." }] }] }`. Nem kompatibilis plain text mezőkkel.
- **Javítás**: Rekurzív `adfToText(node)` walker implementáció, amely kezeli: `text`, `paragraph`, `heading`, `bulletList`, `listItem`, `codeBlock`, `inlineCard` node típusokat. Minden egyéb node-ot figyelmen kívül hagy.
- **Megelőzés**: Jira leírás mező feldolgozásakor MINDIG `adfToText()` konverziót alkalmazz. A raw ADF JSON-t SOHA ne mentsd közvetlenül felhasználói felületre szánt szöveges mezőbe.

### [LESSON-DEMO-PERSONA-STRATEGY-001] Demo felhasználók: valódi auth.users + app_metadata tag = legkisebb schema változás
- **Dátum**: 2026-05-08 (v3.1.1, versioning: 08052601)
- **Fájl**: `supabase/functions/seed-demo-workspace/index.ts`
- **Probléma**: Demo munkaterület seedelésekor szükség van valódi felhasználószerű entitásokra (profilok, tagságok, szabadságkérelmek, skill assignmentek) — de anélkül hogy új sémát kellene bevezetni vagy meglévő componenseket módosítani.
- **Gyökérok**: Ha "fake" user ID-kat használsz, az összes meglévő komponens (profil lookup, leave_request user display, member-skill join) meghibásodik vagy nullt ad vissza.
- **Javítás**: Valódi `auth.users` rekordok létrehozása `admin.createUser({ email_confirm: true, app_metadata: { is_demo_persona: true } })` hívással. Az `is_demo_persona: true` app_metadata tag azonosítja a demo usereket a cleanup folyamathoz. Minden meglévő komponens transzparensen működik velük.
- **Megelőzés**: Demo/teszt usereket MINDIG valódi auth.users rekordként hozz létre, `app_metadata.is_demo_persona: true` taggel. Ne vezess be mock user entity típust — ez schema változást és component módosítást igényel. A cleanup a tag alapján azonosítja és törli őket.

### [LESSON-WEBHOOK-HMAC-SKIP-001] Webhook HMAC verifikáció: skip ha nincs secret beállítva (ne fail)
- **Dátum**: 2026-05-07 (v3.2.0, versioning: 07052602)
- **Fájl**: `supabase/functions/help-regenerator/index.ts`
- **Probléma**: Ha a HMAC webhook secret env var nincs beállítva, a strict verify meghiúsítja az összes manuális teszthívást (`curl`) — még fejlesztés/debug közben is.
- **Gyökérok**: A szigorú "ha nincs secret → fail" policy blokkolja a fejlesztési workflow-t és a manuális regenerálást.
- **Javítás**: `if (!GITHUB_RELEASE_WEBHOOK_SECRET) { /* skip verification */ }` — ha a secret nincs beállítva, a verifikáció ki van hagyva. Ha be van állítva, HMAC-SHA256 kötelező.
- **Megelőzés**: Webhook HMAC verifikációnál kövesd ezt a mintát: SECRET_SET → verify (fail ha mismatch), SECRET_NOT_SET → skip (allow). Ez teszi lehetővé a manuális tesztelést secret nélkül, miközben production-ban a webhook biztonságos.

### [LESSON-AI-STRUCTURED-OUTPUT-001] Gemini 2.0 Flash: strukturált JSON output generálás help rendszerhez
- **Dátum**: 2026-05-07 (v3.2.0, versioning: 07052602)
- **Fájl**: `supabase/functions/help-regenerator/index.ts`
- **Probléma**: Az AI-generált help cikkeket konzisztens struktúrában (title, summary, body_md, taxonomy, tags, anchor_id stb.) kell tárolni — szabad szöveges AI output nem parseable.
- **Gyökérok**: Szabad szöveges AI output variábilis formátumot produkál, amely nehezen parseable és megbízhatatlanul illeszkedik a DB sémához.
- **Javítás**: `responseMimeType: "application/json"` + `temperature: 0.3` + strukturált JSON array system prompt. A model `[{ "topic_key": "...", "locale": "en", "title": "...", ... }]` formátumban ad vissza eredményt, amely közvetlenül upsertálható.
- **Megelőzés**: Ha AI-t használsz strukturált adatok generálásához (DB insert, API payload), MINDIG `responseMimeType: "application/json"`-t alkalmazz, és add meg a pontos JSON struktúrát a system promptban. `temperature: 0.3` → konzisztens, alacsony variabilitású output.

### [LESSON-GIT-REBASE-MAIN-FIRST-001] CHANGELOG conflict + verzió-ütközés: mindig sync `origin/main`-nel a munka előtt
- **Dátum**: 2026-05-09 (v3.2.7)
- **Fájl**: `CLAUDE.md`, `.governance/controller.md`, `AI_EXECUTION_PROMPTS.md`
- **Probléma**: A feature branch (`claude/org-chart-menu-development-mQEhU`) ágon `v3.2.5` CHANGELOG bejegyzést írtam, miközben időközben más PR-ek mergelődtek `main`-be és ott már `v3.2.5` (Seeder v8) és `v3.2.6` (Premium Org Chart) verziók voltak. Ezért a PR mergelhetetlen lett: két `v3.2.5` szakasz ütközött a CHANGELOG.md tetején.
- **Gyökérok**: A munka megkezdése előtt nem futtattam `git fetch origin main && git rebase origin/main`-t, így nem láttam, hogy a verziószámaim már foglaltak. Emellett a CHANGELOG bejegyzés írásakor sem ellenőriztem újra a `main` aktuális tetejét.
- **Javítás**: Branch `--hard reset`-elve `origin/main`-re (a redundáns commit eldobva, mert a kódváltozások már main-ben voltak egy korábbi merge-ből). Új CHANGELOG bejegyzés **csak az új RLS hardening tartalommal**, a következő szabad verziószámon (`v3.2.7`).
- **Megelőzés**:
  1. **Minden session elején**: `git fetch origin main && git rebase origin/main` (vagy `git pull --rebase origin main`)
  2. **Minden CHANGELOG.md edit előtt MÉG EGYSZER**: re-fetch + re-rebase, hogy biztosan a legfrissebb `main`-ről indulj
  3. **Verziószám választás**: olvasd el a `CHANGELOG.md` aktuális tetejét `origin/main`-en (`git show origin/main:CHANGELOG.md | head -3`) és a következő SZABAD verziót használd
  4. **Soha ne tételezd fel**, hogy a saját branched egy verzió-számot lefoglalhat — más PR-ek párhuzamosan haladhatnak

---

## ➕ APPEND — 2026-05-09 GiGanttIc flagship Gantt board (v3.3.0)

### [LESSON-GANTT-STICKY-001] Single-container sticky scroll: no JS needed for synced Gantt axes
- **Dátum**: 2026-05-09 (v3.3.0)
- **Fájl**: `src/components/enterprise/agile/GiGanttIcBoard.tsx`
- **Probléma**: A classic two-panel Gantt (left grid + right chart) requires synchronized vertical scroll (left follows right) and synchronized horizontal scroll (header follows chart). JS-based scroll sync via `onScroll` + `ref.scrollLeft =` is fragile and causes visual lag/jitter.
- **Javítás**: Single `overflow: auto` container holding the full content width (`LEFT_W + chartWidth`). Left task cells use `position: sticky; left: 0; z-index: 10` — they stay pinned during horizontal scroll. Timeline header uses `position: sticky; top: 0; z-index: 20` — it stays pinned during vertical scroll. The top-left intersection cell uses `position: sticky; top: 0; left: 0; z-index: 30`. All rows scroll together naturally.
- **Megelőzés**: For any split-pane board (Gantt, spreadsheet, timeline), prefer this single-container sticky approach over dual-container JS scroll sync. Requirements: (1) inner content `width = leftW + chartW`, (2) left cells sticky-left, (3) header row sticky-top, (4) both cells carry a solid background color so scrolled content doesn't bleed through.

### [LESSON-GANTT-SVG-OVERLAY-001] Absolute SVG overlay for cross-row dependency lines
- **Dátum**: 2026-05-09 (v3.3.0)
- **Fájl**: `src/components/enterprise/agile/GiGanttIcBoard.tsx`
- **Probléma**: Dependency lines in a Gantt connect rows at different vertical positions. Rendering them per-row (one SVG per row div) can't span across rows. Using `position: fixed` won't scroll with the content.
- **Javítás**: Place a single SVG with `position: absolute; left: LEFT_W; top: 0; pointer-events: none; z-index: 6` inside the rows wrapper div. The SVG is `width = chartWidth; height = totalRows * ROW_H`. Since it's inside the single scroll container (not fixed), it scrolls naturally with the content. Row y-positions are computed as `rowIndex * ROW_H + BAR_Y + BAR_H/2`.
- **Megelőzés**: Any overlay spanning multiple rows (dependency lines, highlight bands, today marker) should be an absolutely positioned SVG/div inside the scroll container — NOT position:fixed, NOT one element per row.

### [LESSON-GANTT-CYCLE-GUARD-001] Dependency cycle prevention with recursive CTE BFS
- **Dátum**: 2026-05-09 (v3.3.0)
- **Fájl**: `supabase/migrations/20260509030000_giganttIc_scheduling_fields.sql`
- **Probléma**: Allowing circular dependencies in a Gantt breaks topological sort, critical path computation, and can cause infinite loops in rendering logic.
- **Javítás**: `ganttIc_has_dependency_cycle(workspace, integration, predecessor, successor)` PL/pgSQL function uses a `WITH RECURSIVE reachable AS (...)` CTE BFS starting from `successor`, checking if `predecessor` is reachable. If yes → cycle detected → return true → caller blocks the INSERT.
- **Megelőzés**: Any scheduling system with dependency edges MUST implement cycle detection before INSERT. The recursive CTE BFS in PostgreSQL is the idiomatic, set-based approach — avoid application-side graph traversal for this guard (it races with concurrent writes). Use SECURITY DEFINER + `SET search_path` to prevent injection.

### [LESSON-GANTT-BRANDING-001] Premium flagship tab: teal accent + data-[state=active] class for branded active state
- **Dátum**: 2026-05-09 (v3.3.0)
- **Fájl**: `src/components/enterprise/agile/AgileBoards.tsx`
- **Probléma**: Standard Radix `TabsTrigger` active state uses the theme's default ring/underline, which looks identical to other tabs — no visual hierarchy for flagship features.
- **Javítás**: Add `data-[state=active]:bg-teal-500/15 data-[state=active]:text-teal-300 data-[state=active]:border-teal-500/30` to the flagship tab's className. Inside the trigger, use span elements with alternating color classes (`text-teal-400` for "Gi" and italic "Ic", neutral for "Gantt") to create a branded typographic treatment. A `Sparkles` icon preceding the text signals premium status.
- **Megelőzés**: For any flagship or premium-tier tab/nav item, use `data-[state=active]` variant classes to apply custom active styling without overriding the global tab component. Branded typography (colored portions of a product name) is more tasteful than heavy badges — use it for feature-level identity.

### [LESSON-GANTT-PROGRESS-001] Multi-source progress: hours → status fallback → manual override
- **Dátum**: 2026-05-09 (v3.3.0)
- **Fájl**: `src/components/enterprise/agile/GiGanttIcBoard.tsx`
- **Probléma**: Agile issues may have progress data from multiple sources with inconsistent coverage: some have `completed_hours/original_estimate_hours`, some only have `status`, some have neither.
- **Javítás**: Priority cascade: (1) status = Done/Closed → 100%, (2) `completed_hours` + `original_estimate_hours` both present → ratio (clamped 0–1), (3) `In Review` → 65%, `In Progress` → 40%, (4) else 0. The new `progress_pct` DB column (added in migration) provides a manual override path for future use.
- **Megelőzés**: Any progress/completion indicator in a planning tool should implement a multi-source cascade like this. Never assume a single field will always be populated — use the richest available signal with graceful fallbacks.

### [LESSON-ROUTING-HASH-001] Static host + React Router: a hash routing az egyetlen biztos „örök” refresh-safe megoldás
- **Dátum**: 2026-05-09
- **Fájl**: `src/App.tsx`, `src/pages/Auth.tsx`, `src/hooks/useAuth.tsx`, `src/components/enterprise/InviteMemberDialog.tsx`
- **Probléma**: Published környezetben a `/app?tab=organization` és más belső útvonalak frissítéskor vagy közvetlen megnyitáskor időnként nyers szerveroldali `Not Found` választ adtak, tehát az app shell el sem indult.
- **Gyökérok**: A `BrowserRouter` arra épít, hogy a host minden belső route-ra az SPA entrypointot szolgálja ki. Ha a hosting réteg vagy a preview/publish infrastruktúra ezt csak részben vagy intermittensen teszi meg, a kliensoldali router már nem tud helyreállni, mert a böngésző még az app betöltése előtt 404-et kap.
- **Javítás**:
  1. `BrowserRouter` → `HashRouter`, így a szerver mindig csak a `/` oldalt kapja meg.
  2. Minden auth callback URL hash-alapú lett (`/#/auth?...`, `/#/reset-password`).
  3. A query-param olvasást a router aktuális `location.search` értékére kell kötni, nem a `window.location.search`-re, mert hash-routernél a keresőparaméterek a hash-részben élnek.
  4. Meghívó- és email-linkeknél is hash-alapú belső linket kell generálni, különben a felhasználó ismét szerveroldali 404-re eshet.
- **Megelőzés**: Ha egy React Router app static/published hoston **akár csak egyszer is** intermittens refresh-404-ot produkál belső route-okon, ne told tovább rewrite/404 fallback hackekkel. A tartós megoldás: `HashRouter`, és minden külső callback / email / OAuth redirect URL-t ehhez kell igazítani.

### [LESSON-ORGCHART-PANZOOM-001] CSS transform for diagram pan/zoom — single state, no scroll container
- **Dátum**: 2026-05-09 (v3.3.1)
- **Fájl**: `src/components/enterprise/organization/OrgChartPremiumView.tsx`
- **Probléma**: An org chart with hundreds of nodes can't be scrolled with `overflow: auto` alone — the diagram is too wide/tall for a fixed viewport. Adding drag/zoom requires either a complex scrollable canvas or a transform layer.
- **Javítás**: Use `overflow: hidden` on the outer container + an absolutely positioned inner div with `transform: translate(${offsetX}px, ${offsetY}px) scale(${scale})`. Pan state `(offsetX, offsetY)` updated in `onMouseMove`; zoom `scale` updated in `onWheel` / button clicks. This entirely avoids scroll infrastructure and gives pixel-perfect control.
- **Megelőzés**: For diagrams (org charts, flowcharts, mind maps), prefer a `transform`-based pan/zoom over scroll containers — it supports infinite canvas semantics and allows zoom-in-place via `transform-origin`.

### [LESSON-ORGCHART-DRAG-CLICK-001] Distinguishing drag vs click with a pixel threshold + capture-phase stop
- **Dátum**: 2026-05-09 (v3.3.1)
- **Fájl**: `src/components/enterprise/organization/OrgChartPremiumView.tsx`
- **Probléma**: When the user finishes a pan drag, the `mouseup` event fires and immediately triggers the card's `onClick` handler — unintentionally opening the employee drawer.
- **Javítás**: Track `hasDragged` ref (`useRef(false)`). In `onMouseMove`, set `hasDragged.current = true` only once the total displacement from `dragStart` exceeds `DRAG_THRESHOLD` (6 px). In a capture-phase `onClickCapture` handler on the container, call `e.stopPropagation()` and reset `hasDragged` if it was true — the card's bubbled click never fires.
- **Megelőzés**: Any draggable canvas with clickable children MUST use capture-phase interception to block click after drag. The 6 px threshold prevents false drag detection from accidental mouse jitter.

### [LESSON-ORGCHART-POPUP-001] Near-fullscreen popup via Radix Dialog + containerHeight prop
- **Dátum**: 2026-05-09 (v3.3.1)
- **Fájl**: `src/components/enterprise/organization/OrgChart.tsx`
- **Probléma**: The inline org chart view is constrained to 520 px height. Users need a way to see the full hierarchy without leaving the page.
- **Javítás**: Add `containerHeight?: string` prop (default `'520px'`) to `OrgChartPremiumView`. In `OrgChart`, add a `Maximize2` button (visible only in premium view) that opens a Radix `Dialog` (`max-w-[95vw]`). Inside the dialog, render `<OrgChartPremiumView ... containerHeight=”calc(90vh - 80px)” />` — same data, same functionality, but 90 % of the viewport height. The dialog overlay handles close on backdrop click.
- **Megelőzés**: For any complex visualization (charts, diagrams, boards), design a `containerHeight` escape hatch from the start. Reusing the existing component inside a Dialog is zero-duplication fullscreen — no separate “fullscreen component” needed.

### [LESSON-TIMELINE-INFINITE-LOOP-001] Inline callback prop → végtelen React újrarenderelés + skeleton freeze
- **Dátum**: 2026-05-09 (v3.3.3)
- **Fájl**: `src/components/enterprise/WorkspaceDashboard.tsx`, `src/components/enterprise/calendar/TimelineView.tsx`
- **Hibaüzenet**: "Maximum update depth exceeded" (React, runtime); UI: az Idővonal örökre skeleton állapotban marad.
- **Gyökérok**: `WorkspaceDashboard` JSX-ben inline arrow fn volt az `onFilteredUsersChange` propba: `(userIds, range) => setTimelineReport(...)`. Minden szülő-render új fn referenciát adott. `TimelineView`-ban `useEffect([..., onFilteredUsersChange])` ezt a referenciát dep-ként figyelte → effect újrafut → `setTimelineReport` → szülő újrarendel → új fn → loop → ~50 iteráció után React kidobja a hibát → az összetevő megfagy, `setLoading(false)` sohasem hívódik meg → skeleton.
- **Javítás**:
  1. **Szülőben**: `useCallback(() => setTimelineReport(...), [])` — stabil referencia, mert `setTimelineReport` (useState setter) maga is stabil.
  2. **Gyermekben**: `useRef` pattern a callbackre: külön mellékhatás frissíti `ref.current = prop`; a notify-effect `ref.current?.()`-t hív, és **nem** sorolja fel a prop-ot a dep-tömbben. Guard: `if (loading || members.length === 0) return` — megakadályozza a korai (üres adattal való) tüzelést mount után.
- **Megelőzés**: Ha egy gyermek-komponens szülőnek `callback` propot hív `useEffect`-ből, a propot SOHA ne listázd a dep-tömbben közvetlenül — ez garantált végtelen loop, ha a szülő inline-ban adja át. Mindig: `useCallback` a szülőben VAGY `useRef`-es indirection a gyermekben (mindkettő), soha egyik sem önmagában nem elégséges, ha a szülő nem stabilan adja át.

### [LESSON-TIMELINE-FETCH-001] Promise.allSettled + debounce a hónapváltás "Failed to fetch" bug ellen
- **Dátum**: 2026-05-09 (v3.3.2)
- **Fájl**: `src/components/enterprise/calendar/TimelineView.tsx`
- **Probléma**: Az Idővonal nézet `Promise.all`-ba csomagolt 7 párhuzamos Supabase queryt. Gyors hónapváltásnál egyszerre futó kérések terhelték a kapcsolatot (vagy a böngésző abortálta a régi kéréseket), ami "TypeError: Failed to fetch" hibát okozott még az aktuális kérésnél is.
- **Javítás**:
  1. `Promise.all` → `Promise.allSettled` + `toRes()` helper: nem-kritikus queryek (leaves, holidays, skills) hálózati hibán is üres tömbbel degradálnak, nem dobnak.
  2. 250 ms debounce a `useEffect`-ben: `loadTimerRef`-ből futtató `setTimeout(load, 250)` — gyors navigálásnál csak az utolsó klikk indít tényleges hálózati kérést.
- **Megelőzés**: Bármelyik nézetben, ahol hónapváltás → új lekérdezés, mindig debounce-old a triggert (250–300 ms) és használj `allSettled`-et a resilience miatt. Soha ne feltételezd, hogy párhuzamos `Promise.all` stabil — különösen Supabase pooler limites környezetben.

---

## [LESSON-REDESIGN-SHELL-001] Adaptive shell + density tokens (Phase 1)

**Context:** A teljes app eddig `max-w-5xl mx-auto` köré szorult — ultrawide
(1536px+) képernyőn üres oldalsávok, density tablet és 4K-n is azonos. A
redesign Phase 1 bevezeti a shell és density rendszert úgy, hogy a meglévő
`WorkspaceDashboard` (1076 sor) **érintetlen marad** — zero regresszió.

**Új architektúra:**
- `src/styles/density.css` — három density tier (`compact` / `comfortable` /
  `expansive`) + `auto` (viewport-alapú). Tokenek: `--density-row-h`,
  `--density-pad-x/y`, `--density-gap`, `--density-card-pad`,
  `--density-section-gap`, `--density-page-pad-x/y`. A `<html data-density>`
  attribútum felülírja a media query-ket.
- `src/hooks/useDensity.tsx` — `DensityProvider` + `useDensity()`. Munkaterület
  scope-olt preferencia (`effectime.density.ws.<id>` localStorage), fallback
  globális (`effectime.density`), végül `auto`. **Soha nem `auto`-t alkalmaz**
  a DOM-ra — mindig feloldja viewportból.
- `src/components/shell/AppShell.tsx` — root layout primitív. Soha nem ad
  `max-width`-et a `<main>`-re. `SkipToContent` a11y-hez.
- `src/components/shell/PageHeader.tsx` — title + description + crumbs +
  actions, density-token alapú padding.
- `src/components/shell/DensityToggle.tsx` — fejlécbe ágyazható toggle 4
  opcióval (Auto/Tömör/Kényelmes/Tágas).

**Use-on-page:**
- `Enterprise.tsx` workspace picker átállt: full-bleed grid (`shell-grid-bento`
  → 1/2/3/4/5 col 640/1024/1536/1920px-en), modern kártyák gradient ikon
  badge-dzsel, billentyűzettel navigálható (Enter/Space).
- `Landing.tsx` — full-bleed hero `clamp()` típusskálával, feature grid 4
  oszlopra megy 2xl-en, benefit szekció 1400px-ig nyúlik ultrawide-on.
- `App.tsx` — `<DensityProvider>` a gyökér szinten.

**KRITIKUS REGRESSZIÓ-VÉDELEM:**
1. `WorkspaceDashboard.tsx` **nem módosult** — minden tab content, integráció,
   Supabase query, RLS hívás, edge function, audit log érintetlen.
2. URL search params (`?tab=`, `?ws=`, `?invite=`, `?select=1`) viselkedése
   bit-pontosan megegyezik az eredetivel.
3. Auth flow (HashRouter, OAuth callback, invite token, password reset)
   érintetlen.
4. A density tokenek **csak akkor hatnak** ha egy komponens explicit
   `var(--density-*)`-ot használ. A meglévő tailwind paddingek (`p-4`, stb.)
   változatlanul működnek — semmi nem törik attól, hogy a `data-density`
   attribútum megjelenik a `<html>`-en.

**Phase 2 előkészítve:** a shell komponensek készek arra, hogy a
`WorkspaceDashboard` belsejét körülöleljék — modul-szintű sidebar nav (ami
ugyanazt a `?tab=` paramétert hajtja, így zero state-loss), TopBar az
értesítésekkel/profil menüvel, és a bento grid widgetekkel a dashboard
áttekintő nézethez.

---

## [LESSON-REDESIGN-SHELL-002] Persistent collapsible sidebar a workspace nézethez (zero functional regression)

**Dátum:** 2026-05-09  
**Kontextus:** A teljes Effectime Enterprise redesign Phase 2 — a horizontális, túlcsorduló tab-csík lecserélése egy összecsukható oldalsávra a /app munkaterület-nézetben, miközben minden meglévő tab-érték (members, organization, calendar, requests, workflows, resources, reports-audit, settings) változatlanul működik.

### Probléma
- A WorkspaceDashboard 1076 soros monolit; a tabok URL paraméteren keresztül vezéreltek (?tab=...).
- A horizontális TabsList kis viewporton túlcsordul, és nem skálázódik ultrawide-ra (max-w-5xl mx-auto).
- Funkcionalitás-regresszió tilos: a Tabs/TabsContent kontraktnak változatlanul kell maradnia.

### Megoldás (minimálisan invazív)
1. Új `src/components/shell/WorkspaceSidebar.tsx`: shadcn `Sidebar collapsible="icon"` — kapja az `activeTab`-ot és `onTabChange`-t props-ként, plus per-permission visible flageket. Csak `setActiveTab(value)`-t hív, semmilyen Tabs-belső API-hoz nem nyúl.
2. WorkspaceDashboard outer wrapper: `<SidebarProvider><WorkspaceSidebar/><SidebarInset>…</SidebarInset></SidebarProvider>`. A header SidebarTrigger-rel kibővítve, max-w-5xl korlát eltávolítva, full-bleed padding density tokenből (`--shell-pad-x`/`--shell-pad-y` fallback 1rem).
3. A régi horizontális TabsList nem törölve, hanem `className="sr-only"` — Radix Tabs továbbra is megtalálja a triggereket, screen reader/keyboard-flow megmarad, vizuálisan a sidebar veszi át.
4. DensityToggle bekerült a workspace headerbe `workspaceId` propszal — workspace-onként mentett preferencia (`effectime.density.ws.<id>` localStorage), auto fallback a viewportra.

### Tanulság
- **Sose töröld a meglévő Tabs-triggereket**, ha külső navigációval cseréled le őket — `sr-only`-vel rejtsd el; így a Radix value-mapping és a billentyűzet-flow változatlan, nincs regresszió.
- A shadcn `Sidebar` `collapsible="icon"` mód mind desktopon, mind tableten egyaránt használható; mobilra `offcanvas` automatikusan érvényesül a komponens belső breakpointja miatt — nem kell külön mobile drawer.
- A sidebar gyökérblokk **kötelezően** `<div className="min-h-screen flex w-full">` — `w-full` nélkül a Tailwind 4 + sidebar layout összeomlik (ld. tailwind4-sidebar-width-fix).
- Ne nest-elj `<main>` elemeket: `SidebarInset` már main; a belső skip-target div legyen `id="main-content"`.

### [LESSON-REDESIGN-SHELL-003] Rules of Hooks — useState above early returns
A workspace-picker `useState('')` került a `if (selectedWorkspaceId) return <WorkspaceDashboard/>` early return UTÁN. Amikor a felhasználó belépett egy workspace-be (early return aktív), a hook nem futott le; visszanavigáláskor lefutott — eltérő hook-szám → React production error #300 (Too many re-renders / hook mismatch), teljes app-blank.
**Szabály:** minden useState/useEffect/useMemo HÍVÁS a komponens TETEJÉN, BÁRMILYEN feltételes return ELŐTT, kivétel nélkül. Új state-et SOHA ne tegyél conditional return alá.

---

### [HIBA-076] Demo seed schema drift — kötelező leave mezők + megszűnt seed oszlopok 500-as hibát okoztak
- **Dátum**: 2026-05-10
- **Fájl**: `supabase/functions/seed-demo-workspace/index.ts`, `supabase/functions/seed-demo-workspace/seed-data.ts`
- **Hibaüzenet**: `null value in column "is_half_day" of relation "leave_requests" violates not-null constraint`, plus schema-cache figyelmeztetések a nem létező `enterprise_daily_rules.is_active` és `enterprise_job_families.sort_order` mezőkre.
- **Gyökérok**: A demo seed részben régi DB-sémát követett. A szabadságkérelmek egy részénél a jelenlegi kötelező mezők nem lettek explicit kitöltve, miközben egyes seed definíciók még olyan oszlopokat is tartalmaztak, amelyek már nem léteznek az aktuális táblákban.
- **Javítás**: A `leave_requests` seed minden rekordja normalizálva lett (`is_half_day`, `half_day_period`, `is_private`, `cancellation_reason`), a daily rule és job family seed-definíciókból pedig kikerültek a megszűnt mezők.
- **Megelőzés**: Demo / edge seed módosítás előtt **mindig** a jelenlegi `src/integrations/supabase/types.ts` Insert-sémát vagy az aktuális migrációkat kell forrásigazságnak tekinteni; a seed-manifestben tilos legacy mezőt bent hagyni.

---

### [LESSON-EXPORT-086] Supabase profiles táblában nincs email oszlop — SECURITY DEFINER RPC a megoldás
- **Dátum**: 2026-05-10
- **Fájl**: `src/components/enterprise/import-export/utils/data-fetcher.ts`
- **Gyökérok**: A Supabase `profiles` (public schema) táblának nincs `email` oszlopa. Az auth email kizárólag `auth.users.email`-ben él, amit az anon key frontend kliens nem érhet el direktben.
- **Javítás**: `SECURITY DEFINER` PostgreSQL függvények: `get_workspace_members_for_export` és `get_workspace_leave_for_export` — ezek `auth.users`-hez csatlakoznak, de csak `has_enterprise_role` ellenőrzés után futnak le. A frontend `supabase.rpc()` hívással éri el őket.
- **Megelőzés**: Ha bármilyen exporthoz/listázáshoz user email kell, SOHA ne próbáld `profiles.email`-ből olvasni — mindig SECURITY DEFINER függvényt írj, amely `auth.users`-t joinol.

### [LESSON-EXPORT-087] Edge Function: profiles.email import-hoz szintén nem létezik — get_user_ids_by_emails RPC
- **Dátum**: 2026-05-10
- **Fájl**: `supabase/functions/import-entity-data/index.ts`
- **Gyökérok**: Az Edge Function service role klienssel is hiába queryczi `profiles.select('user_id, email')` — az oszlop egyszerűen nem létezik, üres eredményt ad.
- **Javítás**: Új `get_user_ids_by_emails(p_emails text[])` SECURITY DEFINER függvény, amelyet az Edge Function `serviceClient.rpc()`-vel hív. Ez `auth.users`-ből `ANY(p_emails)` szűréssel adja vissza a user_id↔email párokat.
- **Megelőzés**: Import flow-ban email → user_id feloldásnál mindig a `get_user_ids_by_emails` RPC-t használd.

### [LESSON-EXPORT-088] has_enterprise_role paraméternevei: _ prefix (nem p_ prefix)
- **Dátum**: 2026-05-10
- **Fájl**: `supabase/functions/import-entity-data/index.ts`
- **Gyökérok**: A `has_enterprise_role` függvény paraméterei: `_workspace_id`, `_user_id`, `_roles` (underscore prefix). A v3.5.1 Edge Function tévesen `p_workspace_id`, `p_user_id`, `p_roles` névvel hívta.
- **Javítás**: `_workspace_id`, `_user_id`, `_roles` névvel hívva.
- **Megelőzés**: Ismeretlen RPC hívás előtt mindig ellenőrizd a függvény szignaturát: `SELECT pg_get_function_arguments(oid) FROM pg_proc WHERE proname = 'function_name'`.

### [LESSON-FLEX-089] Tailwind truncate + flex-1 testvér: min-w-0 a szülőre kötelező
- **Dátum**: 2026-05-10
- **Fájl**: `src/components/enterprise/MemberExtendedDetails.tsx`
- **Tünet**: Egy Jira-jegy sor utolsó eleme (külső link ikon) levágva a kártya jobb szélén — pedig a sor `flex items-center` és a középső span `flex-1 truncate`, a többi `shrink-0`.
- **Gyökérok**: A flex container alapértelmezett `min-width: auto`-val nem zsugorítja le az tartalmát a `max-content` alá. A `truncate` szülőjének is kell `min-w-0` — különben a `flex-1` span megtartja a teljes szöveg szélességét, és a `shrink-0` testvér elemek a szülő bounding box-án kívül renderelődnek.
- **Javítás**: `min-w-0` hozzáadva a sor `<div>`-re ÉS a `<span>`-re (mindkettő szükséges többszintű flex esetén).
- **Megelőzés**: Bármikor amikor `truncate flex-1`-t teszel egy flex item-re, a szülő flex container-nek MINDIG kell `min-w-0` — így a shrink-0 testvérek nem fognak túlfolyni.

### [LESSON-RPC-090] Komplex calc engine: server SECURITY DEFINER + client mirror
- **Dátum**: 2026-05-10
- **Fájl**: `supabase/migrations/.._create_time_attendance_rpcs.sql`, `src/components/enterprise/time-attendance/calculations.ts`
- **Probléma**: Egy bonyolult bér-számítómotor (regular/overtime/weekend OT/night/standby×0.20/intervention/leave-adjusted) dolgozhat-e csak kliens-oldalon? Nem — az export bizonyítható-helyesnek kell lennie és nem szabad UI állapotból következtetni.
- **Megoldás**: Hiteles számítás SQL függvényben (`attendance_recompute_totals`) ami a periódus minden mutációja után fut és cache-eli az eredményt jsonb-ben. A kliens (`previewTotals`) 1:1 tükrözi ezt a logikát az UX preview-hoz, és 18 unit teszt szigorúan pin-eli a két oldal egyenlőségét.
- **Megelőzés**: Bármilyen pénzügyi vagy bér-releváns számítás server-side hiteles legyen, a kliens-oldali változat csak preview. Mindkét oldalt fedjük le egyszerű unit tesztekkel.

### [LESSON-AUDIT-091] Append-only audit: csak SELECT policy + SECURITY DEFINER írás
- **Dátum**: 2026-05-10
- **Fájl**: `supabase/migrations/.._create_time_attendance_module.sql`
- **Megoldás**: `enterprise_attendance_audit` táblának CSAK egy SELECT policy van. Nincs INSERT/UPDATE/DELETE policy, így anon és authenticated szerepkör nem tud közvetlenül írni. Az írás minden mutáció során a `SECURITY DEFINER` RPC-kből történik (pl. `attendance_upsert_segment` `INSERT INTO enterprise_attendance_audit`-tal zár).
- **Megelőzés**: Audit / immutable history tábláknál mindig vegyük figyelembe — RLS nincs INSERT policy = nincs direkt írás, kontrollált audit-emit a hivatalos RPC-kből.

### [LESSON-DUAL-SOURCE-092] Forward-compatible field-ek a jövőbeli integrációkhoz
- **Dátum**: 2026-05-10
- **Fájl**: `enterprise_attendance_segments` séma
- **Logika**: A v1 csak manuális idő-rögzítést szállít, de a `source text DEFAULT 'manual'` és `device_event_id uuid` oszlopok már a sémában vannak. Amikor a jövőben hardver-alapú attendance esemény ingestion bekerül, csak egy új `enterprise_attendance_device_events` tábla és egy új edge function kell — sem séma-migrációra, sem UI átalakításra nincs szükség.
- **Megelőzés**: Ha tudjuk hogy a v2 új írási forrást fog hozzáadni, már a v1 sémájában legyen ott a `source` flag és FK-helyek. Az alábbi belső költség ezt később már nehéz hozzáadni törés nélkül.

### [LESSON-WF-093] HR workflow engine: pre-built template bank + runtime instance pattern
- **Dátum**: 2026-05-11
- **Fájl**: `src/components/enterprise/workflows/HRWorkflowTemplates.tsx`, `supabase/migrations/20260511000001_create_hr_workflows.sql`
- **Logika**: HR folyamatsablonok (orvosi vizsgálat, előleg-igény, szerződésmódosítás stb.) a DB-ben tárolva (`enterprise_hr_workflow_templates` + `steps` jsonb tömb), fut-time instance-ok az `enterprise_hr_workflow_instances` táblában. Az admin "6 alapértelmezett betöltése" gombbal egyszer feltölti a sablonokat, utána folyamatokat indít belőlük. A template `steps[]` tömbből az `hr_workflow_create_instance` SECURITY DEFINER RPC automatikusan létrehozza a `enterprise_hr_workflow_tasks` sorokat `due_date = instance.due_date + offset_days` logikával.
- **Megelőzés**: Komplex workflow engine esetén válaszd el: (1) sablonok (statikus definíciók), (2) instance-ok (futó folyamatok), (3) feladatok (lépések). Ez biztosítja a visszakereshetőséget és az auditálhatóságot.

### [LESSON-SELFSERVICE-094] Employee Self-Service portal: aggregált view meglévő táblákból, új UI entrypoint
- **Dátum**: 2026-05-11
- **Fájl**: `src/components/enterprise/self-service/EmployeeDashboard.tsx`
- **Logika**: Az "Önkiszolgáló portál" (Saját portál tab) nem igényel új DB táblákat — a meglévő `enterprise_attendance_periods`, `enterprise_leave_quota_balances`, `leave_requests`, `enterprise_hr_workflow_tasks` lekérdezések összerakva adják a komplett employee dashboard-ot. Kulcs: `membership_id` alapján szűr, ami az aktuális user saját membershipje a workspaceben.
- **Megelőzés**: Új "összesítő" dashboardhoz elsőként nézd meg, hogy a szükséges adatok már megvannak-e különálló táblákban — nagy valószínűséggel igen, és csak egy aggregáló UI kell hozzá.

### [LESSON-DRAGSELECT-095] Drag-select naptár cellákon: pointer events + data-attribute + elementFromPoint
- **Dátum**: 2026-05-11
- **Fájl**: `src/components/enterprise/time-attendance/EmployeeMonthView.tsx`
- **Probléma**: A felhasználó egér-húzással vagy érintéssel akar több naptárcellát kijelölni (Mac trackpad lasso-szerűen). A naïv `onMouseEnter` megoldás nem működik mobilon (nincs hover).
- **Megoldás**: Pointer events (`onPointerDown`/`onPointerUp` a cellára, `onPointerMove` a gridre), minden cella `data-day-cell data-date={key}` attribútummal. Touch-on érintés alatt `document.elementFromPoint(e.clientX, e.clientY).closest('[data-day-cell]')` adja vissza az aktuális cellát. A `useRef` tárolja a drag állapotot (active, hovered Set, moved flag, pointerId) hogy ne triggereljen re-rendert minden pointer-move-on. `touch-action: none` a gridre amíg edit mode aktív, különben a mobil scroll-jitter zavarja a húzást. Globális `pointercancel` listener resetolja az állapotot.
- **Kulcs UX szabály**: Single click (no movement) → per-day editor; drag (moved=true, hovered.length > 1) → batch dialog `[min, max]`-szal pre-populated.
- **Megelőzés**: Bármilyen drag-to-select naptáron / gridre — soha ne csak `onMouseEnter`-rel oldd meg (mobil halott). Pointer events + data-attribute + elementFromPoint a kompatibilis recept.

### [LESSON-EDITMODE-096] Explicit edit-mode gate komoly mutációkhoz: véletlen szerkesztés elkerülése
- **Dátum**: 2026-05-11
- **Fájl**: `src/components/enterprise/time-attendance/EmployeeMonthView.tsx`
- **Probléma**: Időnyilvántartás közvetlenül kattintható volt — egy véletlen tap is megnyitotta a napi szerkesztőt. A user nem kapott egyértelmű "kész vagyok" commit pontot, ami megkülönböztette volna a piszkozati változásokat a hivatalos benyújtástól.
- **Megoldás**: `editMode` UI-state (default `false`). „Szerkesztés" ceruza gomb → `setEditMode(true)` → sárga „Szerkesztésre megnyitva" badge + helper banner. Cellák `cursor: pointer` és reagálnak. „Módosítások mentése" save ikonra → `setEditMode(false)`. A „Benyújtás" gomb egy SEPARATE záró művelet (server-side state transition). A two-tier gate: server-side `period.status` (állapotgép) + client-side `editMode` flag. Ha bármelyik nem engedélyez, a UI nem szerkeszthető.
- **Megelőzés**: Bármilyen formanyomtatvány / time-tracker / pénzügyi modul, ahol az adatok módosítása következményekkel jár — explicit „edit / save / submit" three-stage flow, nem one-click direct mutation. A user mindig tudja, hogy most miben van.

## ➕ APPEND — 2026-05-14 v3.33.1 stabilization findings

### [LESSON-GOVERNANCE-002]: MCP-applied migrations must be committed to disk in the same session
- **Context**: v3.17.0 → v3.33.0 — schema objects (`superadmin_change_workspace_tier`, `validate_password_policy`, `workspace_permission_catalog`, `enterprise_feature_catalog.tier_feature_keys`, and ~30 other functions/tables) were applied via Supabase MCP `apply_migration` and never persisted to `supabase/migrations/`.
- **Problem**: Repo↔DB drift. Rebuilding from disk regresses every MCP-only fix — including the v3.17.1 silent-freemium-fallback fix in `create_workspace_with_owner`. Audit, code review, and rollback all become unreliable.
- **Fix**: After EVERY `apply_migration` call, immediately persist the SQL to `supabase/migrations/YYYYMMDDHHMMSS_<slug>.sql` with the same body and commit alongside the code change. The disk file is the source of truth for disaster recovery.
- **Warning**: Most dangerous when MCP is used to FIX a previous on-disk migration — the bug remains in version control while the live DB is patched. Anyone who resets the dev DB from disk regresses the fix without noticing.

### [LESSON-TIER-001]: Tier-id immutability is a DB invariant, not a code convention
- **Context**: `tenant_subscriptions.tier_id` must change only via `superadmin_change_workspace_tier` per v3.17.0.
- **Problem**: The original `tenant_subscriptions_admin_all` RLS policy permitted any platform admin to `UPDATE tier_id` directly. Audit-event write was therefore bypassable.
- **Fix**: BEFORE-UPDATE trigger `enforce_tier_id_immutability` raises unless `current_setting('app.tier_change_rpc_active', true) = 'true'`. The RPC sets the marker via `set_config(..., true)` (txn-local) inside its body. `create_workspace_with_owner` also sets it before the initial INSERT path.
- **Pattern**:
  ```sql
  -- inside the privileged RPC:
  PERFORM set_config('app.tier_change_rpc_active', 'true', true);
  UPDATE tenant_subscriptions SET tier_id = _new_id WHERE tenant_id = _tenant;
  -- the trigger checks this marker; reset_config not needed (txn-local).
  ```

### [LESSON-CATALOG-002]: `text[]` columns mapping to another table's primary key need a delta-validation trigger
- **Context**: `enterprise_feature_catalog.tier_feature_keys text[]` references `features.feature_key`. Same pattern with `features.dependencies`.
- **Problem**: Postgres CHECK can't subquery. Without a trigger, a typo silently hides a UI permission slot (because the EXISTS-check in the visibility computation evaluates to false). Undetectable except by manual inspection.
- **Fix**: BEFORE INSERT OR UPDATE trigger that validates only NEWLY-ADDED elements:
  ```sql
  IF TG_OP = 'INSERT' THEN _added := COALESCE(NEW.col, '{}');
  ELSE
    SELECT array_agg(k) INTO _added FROM unnest(NEW.col) AS k
    WHERE k <> ALL(COALESCE(OLD.col, '{}'));
  END IF;
  -- then check _added against the target table
  ```
  Delta-validation is critical: full validation would reject any future UPDATE on a row containing a pre-existing typo even if the UPDATE doesn't touch the bad column.

### [LESSON-RPC-091]: Empty array from RPC ≠ RPC failure — keep them distinguishable
- **Context**: `useEnterprisePermissions` calls `workspace_permission_catalog` and previously fell back to a legacy unfiltered SELECT when the result was empty.
- **Problem**: The fallback condition `visibleCatalog.length === 0` cannot tell a successful-but-empty response from an error. On error the fallback returned the unfiltered catalog — defeating tier filtering.
- **Fix**: Inspect the response's `error` field explicitly. Fall back only when `catalogRes.error` is truthy. A legitimately-empty result is rendered as an empty tree with the existing `hiddenByTier` notice.
- **Pattern**:
  ```ts
  if (catalogRes.error) {
    // legitimate fallback path (older workspace, RPC doesn't exist)
    const fallback = await supabase.from('legacy_table').select(...);
    setTree(buildTree(fallback.data ?? []));
  } else {
    // empty array is a real answer; render it
    setTree(buildTree(catalogRes.data ?? []));
  }
  ```

### [LESSON-CLEANUP-001]: `useEffect` firing 3+ Supabase queries needs cancellation
- **Context**: `InviteMemberDialog` fires 8 parallel queries on open with no cleanup. Closing mid-fetch caused React state-on-unmounted-component warnings.
- **Problem**: Without a cancellation flag, every awaited setState fires regardless of whether the component is still mounted. Beyond the React warning, any subsequent audit-log write or toast triggered by the post-fetch code path can leak.
- **Fix**:
  ```tsx
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    Promise.all([...]).then(([...]) => {
      if (cancelled) return;
      // safe setState calls
    });
    return () => { cancelled = true; };
  }, [open, workspaceId]);
  ```

### [LESSON-AUDIT-092]: Every privileged edge function must write its own audit row
- **Context**: `superadmin-hub` performed 10 different platform-admin actions (workspace-action, toggle-feature-flag, trigger-edge-function, etc.) with only the role check; no audit row was written for any of them.
- **Problem**: Compliance gap — ISO 27001 A.12.4 / GDPR Art. 5 require traceability for privileged operations. Convention said "track in platform_audit_events"; convention was not enforced.
- **Fix**: Immediately after the role-check passes and the action is identified, fire-and-forget an `insert` to `platform_audit_events` with `actor_id = user.id`, `action = '<edge_fn_name>.<action>'`, `target_id` derived from body, and `metadata: { body }`. Log (don't throw) on insert failure so the action doesn't fail because of audit latency.

### [LESSON-PARTIAL-FAIL-001]: "Partial success returned as success" recurs every quarter — protect against it explicitly
- **Context**: Documented twice already (HIBA-074 demo seed, HIBA-075 demo leave seed). Now resurfaced in `send-scheduled-reports` (emails) and `cleanup-demo-workspace` (auth user deletes).
- **Problem**: A loop that catches per-item errors but reports overall success is the canonical fail-soft anti-pattern. The downstream observer (UI, ops, audit) sees green where there is yellow.
- **Fix**: Count successes and failures explicitly; emit three states (success / partial_failure / error) and include the list of failed items in the error payload. HTTP 207 Multi-Status is the right code for partial-success HTTP responses.
- **Pattern**:
  ```ts
  const failed: string[] = [];
  for (const item of items) {
    try { await doIt(item); } catch (e) { failed.push(item); }
  }
  const status = failed.length === 0 ? 'success'
              : failed.length === items.length ? 'error' : 'partial_failure';
  ```

### [LESSON-DEAD-SQL-001]: Delete dead raw-SQL strings — they become live injection vectors
- **Context**: `run-report/index.ts` contained a `wrapped = \`SELECT * FROM (${sql}) WHERE workspace_id = '${workspaceId}'\`` string that was never executed (the function always took the safer JS-builder fallback path).
- **Problem**: The string was an SQL-injection template waiting to be activated by anyone who later wired up an `exec_sql` RPC. Dead-code injection footguns are common in evolving codebases.
- **Fix**: Delete dead raw-SQL strings entirely. If a future feature needs raw SQL, it must be designed with parameter binding from the start, not by activating dormant string-concat code.

## ➕ APPEND — 2026-05-15 v3.33.2 hotfix lessons

### [LESSON-TRIGGER-PAIR-001]: Never ship an enforcement trigger without verifying every legitimate writer arms its guard
- **Context**: v3.33.1 added an `enforce_tier_id_immutability` trigger on `tenant_subscriptions` that requires `current_setting('app.tier_change_rpc_active', true) = 'true'`. The expectation was that `superadmin_change_workspace_tier` (the sole legitimate writer) sets that marker.
- **Problem**: The RPC body in the remote DB never set the marker. The trigger would have blocked every legitimate tier change in production once v3.33.1 went live. We assumed the RPC sets it because we agreed it would; we never read the live body.
- **Fix**: Always pull `pg_get_functiondef()` of the writer RPC and grep for the `set_config` call BEFORE shipping the trigger. Better: write a migration-invariant test that asserts the call exists, so the assumption is enforced forever.
- **Pattern**: When a trigger + an RPC form a "trigger-RPC pair" (trigger blocks, RPC arms guard), treat them as one atomic deliverable. Read both function bodies during PR review. Write a regression test that asserts both halves of the pair.

### [LESSON-PG-SEARCH-PATH-001]: Every new SECURITY DEFINER / trigger function must declare SET search_path
- **Context**: The 4 functions added in v3.33.1 (`enforce_tier_id_immutability`, `validate_tier_feature_keys`, `validate_feature_dependencies`, `require_feature_id`) all lacked `SET search_path TO 'public'`. Caught by Supabase `function_search_path_mutable` advisor.
- **Problem**: Without `SET search_path`, an attacker who can manipulate session search_path can shadow `public.features` / `public.tenant_subscriptions` from inside a trigger or DEFINER context. The Supabase advisor flags this as a security warning.
- **Fix**: Every `CREATE OR REPLACE FUNCTION public.foo() ... LANGUAGE plpgsql` block must include `SET search_path TO 'public'` (or another explicit schema list) BEFORE the `AS $$`. This applies to triggers and helpers too, not just SECURITY DEFINER RPCs.
- **Pattern**:
  ```sql
  CREATE OR REPLACE FUNCTION public.foo()
  RETURNS trigger
  LANGUAGE plpgsql
  SET search_path TO 'public'   -- mandatory
  AS $$
    ...
  $$;
  ```
- **Enforcement**: After applying any migration, run `mcp__supabase__get_advisors({type: 'security'})` and grep the result for the new function names. If any new function appears in `function_search_path_mutable`, it must be patched before merging.

### [LESSON-MIGRATION-INVARIANTS-001]: Migration-invariant tests are the right level for "convention enforcement"
- **Context**: The two v3.33.2 hotfixes (trigger-pair marker + search_path) were exactly the kind of bug a vitest test could catch by scanning `supabase/migrations/` text.
- **Problem**: When invariants live as conventions ("the RPC sets the marker", "all functions set search_path"), they drift silently across PRs. By the time a Supabase advisor or a production failure surfaces them, several PRs have shipped on top.
- **Fix**: For any DB invariant we write down, also write a test in `src/test/migrationInvariants.test.ts` that scans the migrations corpus and asserts the LATEST `CREATE OR REPLACE` block of the protected object satisfies the invariant. Tests run on every commit; convention drift becomes a red CI signal.
- **Pattern** — see `src/test/migrationInvariants.test.ts`:
  ```ts
  // Find the LATEST CREATE OR REPLACE FUNCTION block (later migrations override earlier).
  // Don't assert against historical snapshots — only the current shipped definition.
  // Use $$ ... $$ matching for un-tagged dollar quotes; backreferences over empty
  // captures fail in JS regex, so prefer two simple regexes (one for $$, one for $function$).
  ```

---

## ➕ APPEND — 2026-05-15 Password-policy split + audit-log silent failure

### [LESSON-SECURITY-001]: Dual password-validator split — always keep a single source of truth for validation rules
**Context**: Any time a validation rule (min length, regex, etc.) exists in both a frontend helper and a server-side function, they must agree exactly.
**Problem**: v3.33.0 introduced `src/lib/security/passwordPolicy.ts` (min 10 chars) alongside the older `src/lib/passwordValidation.ts` (min 8 chars). `InviteMemberDialog` consumed the new 10-char policy; `ChangePasswordCard` + `PasswordRequirements` silently kept the old 8-char policy. Users saw green UI checkmarks for 8- or 9-char passwords that violated company policy. The test suite remained green because it only tested the old file.
**Fix**: Raised the `minLength` threshold in `passwordValidation.ts` from `>= 8` to `>= 10`. Updated `password_req.min_length` i18n key in all 8 locale files. Updated `passwordValidation.test.ts` boundary assertions.
**Pattern**: After introducing a canonical validation rule anywhere in the stack, immediately grep the codebase for every other validator covering the same domain and update them in the same commit. Never leave two implementations with different thresholds.
**Regression trap**: A test suite that only tests the OLD validator file will stay green even as the app silently violates the new policy. Add an explicit cross-check test, or better, consolidate into a single validator.

### [LESSON-SUPABASE-SDK-086]: `logAuditEvent` — Supabase insert errors are not thrown; they must be destructured
**Context**: Any Supabase JS `.insert()` / `.update()` / `.delete()` call.
**Problem**: `logAuditEvent()` used `await supabase.from(...).insert(...)` inside a `try/catch`. The Supabase JS client returns `{ data, error }` on DB-level failures (RLS rejection, constraint violation) — it does NOT throw. The `catch` block therefore never fired for real failures. Audit events were silently lost with no log entry and no return signal to callers.
**Fix**: Destructure `{ error }` from every Supabase mutating call. Check `if (error)` explicitly. `logAuditEvent` now returns `Promise<boolean>` so callers can react to failures.
**Pattern**:
```ts
const { error } = await supabase.from('table').insert([...]);
if (error) { console.warn('insert failed:', error); return false; }
```
**Regression trap**: A `try/catch` around a Supabase call gives false confidence. The only network-level failure that throws is a complete fetch rejection. All application-layer errors come via the `error` field.

## ➕ APPEND — 2026-05-15 Edge-function data integrity, TOCTOU hardening, localization

### [LESSON-SCHEMA-001]: GDPR exports — always join through membership_id, not user_id, for workspace-scoped tables
**Context**: `enterprise_attendance_periods`, `wellbeing_scores`, and similar workspace-scoped tables that link to users via `enterprise_memberships`.
**Problem**: `security-admin` GDPR export queried both tables with `.eq('user_id', targetUserId)`. Both tables use `membership_id` (a FK to `enterprise_memberships`) not a direct `user_id`. The queries returned empty arrays silently — PostgREST does not error on a WHERE clause that matches no rows.
**Fix**: Use `targetMembership.id` (already fetched for the workspace-guard check) as `.eq('membership_id', ...)`.
**Pattern**: Before writing any query on a workspace-scoped table, grep the migration for the actual column name. Never assume `user_id` — many tables use `membership_id` as the user link to preserve referential integrity through the membership layer.

### [LESSON-SCHEMA-002]: Always verify select columns against migration DDL before shipping a hook
**Context**: `useWellbeing.ts` select query included `period_start`, `period_end` (wellbeing_scores) and `metadata` (wellbeing_alerts).
**Problem**: The `wellbeing_scores` migration has no `period_start` or `period_end` columns. `wellbeing_alerts` has `message`, not `metadata`. PostgREST silently returns `undefined` for unknown select columns — no error, no warning, just missing data at runtime.
**Fix**: Removed the non-existent columns from both the select string and the TypeScript interface. `metadata` → `message`.
**Pattern**: Run `grep "CREATE TABLE.*wellbeing_scores" migrations/` and read the DDL before writing a hook. Never trust an interface that was written without checking the migration.

### [LESSON-SECURITY-002]: Never accept actor identity from the request body — always derive it from the JWT
**Context**: `payroll-export` lock-period action.
**Problem**: `locked_by` was set from `body.userId`. Any authenticated workspace admin could claim to lock on behalf of any user by sending a different userId. The JWT is already verified; `user.id` is the single source of truth for the caller's identity.
**Fix**: Removed `userId` from body destructuring; replaced all uses with `user.id`.
**Pattern**: For any audit trail, ownership, or attribution field, always use the identity derived from the verified JWT. Never trust client-supplied identity.

### [LESSON-TOCTOU-001]: Use atomic WHERE clauses on UPDATE to prevent TOCTOU races, then check row count
**Context**: `payroll-export` lock-period: check status = 'open', then update status = 'locked'.
**Problem**: Between the SELECT (status check) and the UPDATE (lock), another concurrent request could lock the same period, resulting in a double-lock acknowledgment.
**Fix**: Add `.eq('status', 'open')` to the UPDATE WHERE clause and `.select('id')` to detect 0-row results. If `lockedRows.length === 0`, the period was already locked — return 409.
**Pattern**:
```ts
const { data: rows } = await admin.from('table')
  .update({ status: 'locked', locked_by: user.id })
  .eq('id', id)
  .eq('status', 'open')  // ← atomic guard
  .select('id');
if (!rows || rows.length === 0) return jsonRes({ error: 'Already locked' }, 409);
```

### [LESSON-ORDER-001]: Write audit rows BEFORE destructive operations, not after
**Context**: `superadmin-hub` workspace-delete action.
**Problem**: The audit INSERT happened after the workspace DELETE. If the workspace FK cascades to `enterprise_audit_events`, the insert could fail (FK no longer exists) and the audit event is silently lost.
**Fix**: Insert the audit row before the DELETE. If the DELETE fails, the audit row is a false positive (harmless); if the INSERT fails, we log it and proceed with the DELETE (audit failure is non-fatal).
**Pattern**: For any hard-delete action, always write the audit trail before the delete, not after.

### [LESSON-LOCALIZATION-001]: Module-level constants cannot use t() — move them inside the component
**Context**: `LeaveCalendar.tsx` `WEEKDAY_LABELS` array defined outside the component.
**Problem**: Constants defined at module level cannot call `t()` because `t()` requires the React context. The array was hardcoded in Hungarian, violating the localization mandate for all 7 other locales.
**Fix**: Removed the module-level constant. Added `weekdayLabels` as a `useMemo` inside the component that calls `t('leave_calendar.weekday_*')` for each of 7 keys. Added all 9 new i18n keys to all 8 locale files.
**Pattern**: Any user-visible string that appears in a module-level constant is a localization gap. Always move such strings inside the component scope where `t()` is available, or define them as i18n keys.

## ➕ APPEND — 2026-05-15 Supabase error-visibility sweep (v3.33.6)

### [LESSON-SUPABASE-SDK-087]: Every `.select()` destructure in a component must include `.error` and early-return
**Context**: AuditLog, ApprovalInbox, LeaveCalendar, WorkspaceDashboard fetchData.
**Problem**: All four components used `const { data } = await supabase.from(...)...` without checking `.error`. On any DB-level failure (RLS rejection, constraint, network), `data` is `null`, items array becomes `[]`, and the UI silently shows "no results" instead of an error state.
**Fix**: `const { data, error } = await ...; if (error) { console.error(...); return; }` in every case.
**Pattern**: The Supabase JS client contract is explicit: errors come via `{ error }`, not via thrown exceptions. ALWAYS destructure both.

### [LESSON-SUPABASE-SDK-088]: Check `.error` on all mutation operations (insert/update/delete) even fire-and-forget paths
**Context**: RolePermissionManager insert/update/delete, ApprovalInbox bulk operations.
**Problem**: `await supabase.from('table').insert(...)` with no error check means RLS rejections, constraint violations, and schema mismatches are completely invisible — no log, no user feedback.
**Fix**: Destructure `{ error }`, log on failure. For UX-critical paths, surface to the user via toast.

### [LESSON-POLLING-001]: Extract interval callback to a named function and check errors in all paths
**Context**: WorkspaceDashboard recovery_mode polling.
**Problem**: Two copies of the same async code (initial fetch + setInterval callback) had inconsistent error handling and relied on shared state. Copying code into interval callbacks is a regression trap.
**Fix**: Extract to a named `pollX()` function. Both the initial call and the interval call invoke the same function. Errors are checked in one place.

### [LESSON-RPC-001]: Distinguish RPC operational failure from authorization denial
**Context**: sync-holidays `has_enterprise_role` RPC.
**Problem**: `const { data: roleCheck } = await supabaseAdmin.rpc(...)` without error check means RPC unavailability is treated the same as "user is not authorized" (returns falsy). The user gets a 403 Forbidden when the real issue is a 500 server error.
**Fix**: Check `{ error: roleErr }` on RPC calls. Return 500 on RPC failure, 403 on explicit denial.

### [LESSON-LOCALE-002]: Never use Hungarian as an English fallback — hardcode 'Unknown', not 'Ismeretlen'
**Context**: capacityEngine.ts, run-report edge function.
**Problem**: `'Ismeretlen'` is the Hungarian word for "Unknown". Using it as a fallback in a shared library means all non-Hungarian users see Hungarian text in display_name fallbacks.
**Fix**: Use `'Unknown'` (English) as the universal fallback for missing display names in library/engine code. Let UI layers apply i18n on top.

# Coding Lessons Learnt

## 2026-04-27
- A Supabase kapcsolatot mindig opcionálisra kell tervezni (`hasSupabaseConfig`), így demo környezetben is működik az oldal.
- Dashboard oldalon a dátum- és számformázást lokális (`hu-HU`) formában érdemes kezelni a jobb felhasználói élményért.
- MVP-ben az adatforrást a felületen explicit jelezni kell (Supabase vs mock), hogy diagnosztikánál egyértelmű legyen.
- Szerepkörös társasházi appnál a demo-üzemmódhoz érdemes URL paraméteres role-váltót adni, mert így backend-auth nélkül is validálható a jogosultsági UI.
- A login oldalt külön route-ra kell szervezni (`/login`), így a fő dashboard komplexitása nem növekszik és a belépési flow deploy után önállóan tesztelhető.
- A Supabase sémában a role kezelést célszerű `profiles` + `memberships` bontással megoldani, mert így a felhasználó több házban eltérő szerepkört kaphat.

## 2026-04-27 – AWS Location és Next.js env tanulság
- Next.js App Routerben a böngészőben futó komponens csak `NEXT_PUBLIC_*` változókat lát. A Vercel `VITE_*` változók nem jelennek meg automatikusan a client bundle-ben, ezért a client-side címkereső „AWS Location API kulcs hiányzik” hibát dobott.
- Külső API kulcsot, ha nem muszáj publikussá tenni, server-side API route mögé kell tenni. A frontend `/api/location/autocomplete` endpointot hív, a route pedig `AWS_LOCATION_API_KEY` / `AWS_LOCATION_REGION` env-ből dolgozik.
- Vercel env módosítás után mindig új redeploy kell, különben a serverless route és a buildelt client továbbra is a régi env snapshotot használhatja.
- Magic link authnál a Supabase redirect URL és a kódbeli `emailRedirectTo` csak akkor működik stabilan, ha a production domain szerepel a Supabase Authentication → URL Configuration allowlistában.

---

## 2026-05-16 – PanelLakó v0.4.0–v0.5.0 fejlesztési tanulságok

### [HIBA-077] `'use client'` hiányzik — onClick Server Componentben
- **Dátum**: 2026-05-16 (v0.3.6)
- **Fájl**: `app/offline/page.tsx`
- **Hibaüzenet**: `Error: Event handlers cannot be passed to Client Component props. {onClick: function onClick}` + build timeout a `/offline` statikus generálásakor
- **Gyökérok**: Az offline PWA page `onClick={() => window.location.reload()}` gombot tartalmazott, de nem volt `'use client'` direktíva. Next.js 14 App Routerben minden page.tsx alapértelmezetten Server Component — onClick, useState, useEffect ezekben tilos.
- **Javítás**: `'use client'` direktíva hozzáadva az oldal elejéhez.
- **Megelőzés**: Minden page/component, amely onClick, useState, useEffect, vagy `window.*` objektumot használ, **kötelező** `'use client'` direktívával kell kezdeni. A build error üzenet expliciten mondja: "Event handlers cannot be passed to Client Component props" — ilyenkor azonnal add hozzá a `'use client'`-et.

### [HIBA-078] Env var névkonfliktus — SUPABASE_SERVICE_ROLE_KEY két projektre mutat
- **Dátum**: 2026-05-16 (v0.5.0)
- **Fájl**: `.env`, `app/api/location/autocomplete/route.ts`
- **Gyökérok**: `.env`-ben `SUPABASE_SERVICE_ROLE_KEY` a GeoData service role kulcsát tárolta (mert az autocomplete route ezt olvassa), míg a PanelLakó billing/tickets kód ugyanezt a nevet várta a PanelLakó admin client kulcsaként. Eredmény: az admin műveletek a GeoData project-re futottak volna.
- **Javítás**: GeoData kulcs → `GEODATA_SUPABASE_SERVICE_ROLE_KEY`; PanelLakó kulcs → `SUPABASE_SERVICE_ROLE_KEY`. Az autocomplete route fallback-kel olvassa mindkét nevet.
- **Megelőzés**: Ha egy projektben több Supabase backend van (pl. GeoData + fő app), mindig **projekt-prefix**-el néveld a kulcsokat: `GEODATA_`, `PANELLAKO_`, stb. A generikus `SUPABASE_SERVICE_ROLE_KEY` csak az app fő backend-jéhez tartozhat.

### [HIBA-079] Stripe SDK v22 — breaking changes a subscription és invoice típusokban
- **Dátum**: 2026-05-16 (v0.5.0)
- **Fájlok**: `app/api/stripe/webhook/route.ts`
- **Hibaüzenet**: `Property 'current_period_start' does not exist on type 'Response<Subscription>'`, `Property 'subscription' does not exist on type 'Invoice'`
- **Gyökérok**: Stripe Node.js SDK v22 (API version `2026-04-22.dahlia`) két breaking change-t hozott: (1) `subscription.current_period_start/end` átkerült → `subscription.items.data[0].current_period_start/end`; (2) `invoice.subscription` átkerült → `invoice.parent?.subscription_details?.subscription`.
- **Javítás**: Helper függvények (`getPeriodDates(sub)`, `getInvoiceSubscriptionId(invoice)`) az új elérési utakkal.
- **Megelőzés**: Stripe SDK major verziónál (pl. v17→v22) **mindig** olvasd el a CHANGELOG-ot. A legtöbb breaking change a Subscription és Invoice típusokat érinti. TypeScript-tel ezek kompile-time kimutathatók — soha ne használj `any` cast-ot Stripe típusokon.

### [HIBA-080] Unused import lint hiba — ESLint no-unused-vars build-time error Next.js-ben
- **Dátum**: 2026-05-16 (v0.5.0)
- **Fájlok**: `app/actions/announcements.ts`, `app/auth/signout/route.ts`, `components/dashboard-client.tsx`
- **Hibaüzenet**: `'generateUnsubscribeUrl' is defined but never used`, `'_request' is defined but never used`, `'submitVoteAction' is defined but never used`
- **Gyökérok**: Importáláskor nem lett végigkövetve, hogy a függvény tényleg használatban van-e. A Next.js production build ESLint-et is futtat (nem csak TypeScript-et).
- **Javítás**: Unused importok törlése. Unused param → `_` prefix helyett teljes elhagyás (ha nem kell a request objekt, a POST handler paramétere kihagyható).
- **Megelőzés**: Minden PR előtt `npx tsc --noEmit && npm run build` futtatása (vagy legalább `eslint --ext .ts,.tsx .`). Ha egy importot `// TODO` kommentként hagyunk, azt is törölni kell ha nem kerül végül felhasználásra.

### [LESSON-OPS-082] Manuális operátori UI-hoz külön auth/session réteg kell
- **Dátum**: 2026-05-19
- **Fájlok**: `lib/superadmin-auth.ts`, `app/superadmin/*`, `app/api/superadmin/*`
- **Gyökérok**: Ha cron és integrációs jobok csak API endpointon érhetők el, a nem-fejlesztő operátorok nehezen tudnak diagnosztizálni és kézi újrafuttatást végezni.
- **Javítás**: Külön superadmin belépés + session cookie + dashboard és manuális job-trigger API készült.
- **Megelőzés**: Integrációs rendszerekhez mindig legyen legalább minimál operátori vezérlőfelület státusszal és futtatási visszajelzéssel.

### [LESSON-TRANSIT-083] Operátori "full sync" ne fusson párhuzamosan külső API burst mellett
- **Dátum**: 2026-05-19
- **Fájlok**: `app/api/superadmin/jobs/run/route.ts`, `app/api/transit/sync/route.ts`
- **Gyökérok**: A `stops-routes` és `building-stops` párhuzamos indítása plusz több grid lekérés könnyen BKK `LIMIT_EXCEEDED` hibát okoz, és elfedheti a valódi hibaképet.
- **Javítás**: Szekvenciális futtatás + rate-limit detektálás + retry/backoff + részleges futás 207 státusszal.
- **Megelőzés**: Külső feedeknél az operátori "run all" mindig vegye figyelembe API limitet és függőségi sorrendet.

### [HIBA-081] Supabase auth trigger user_id=NULL — generált felhasználók user_origin='real' maradtak
- **Dátum**: 2026-04-12
- **Fájl**: `supabase/functions/mass-create-users/index.ts`, `handle_new_user_profile` trigger
- **Hibaüzenet**: Generált felhasználók "Igazi"-ként jelennek meg; city és hobbies mezők üresek.
- **Gyökérok**: Az `auth.users` INSERT triggerek (`handle_new_user`, `handle_new_user_profile`) profilt hoztak létre `id=auth_id` de `user_id=NULL` értékkel. A `persistProfile` függvény `user_id` alapján keresett → nem találta → INSERT-et próbált → konflikt `id`-n → csendesen meghiúsult → a profil megtartotta a trigger-alapértelmezett értékeket (`user_origin='real'`, `city=null`, `hobbies='{}'`).
- **Javítás**: (a) `persistProfile` átírva: upsert `id` konflikt alapján; (b) trigger javítva `user_id=NEW.id`-vel; (c) meglévő törött profilok backfill migrációval javítva.
- **Megelőzés**: **MINDIG** feltételezd, hogy auth triggerek futnak és részleges adattal írnak profilokat. Profile upsert-nél MINDIG `id`-t használj konflikt kulcsként, ne `user_id`-t. Az auth trigger kódját olvasd el mielőtt profile-kezelési logikát írsz.

### [HIBA-082] Supabase Edge Function alapértelmezetten verify_jwt=true — 401 Invalid JWT
- **Dátum**: 2026-04-12
- **Fájl**: `supabase/functions/eventbrite-import/index.ts`
- **Hibaüzenet**: `401 Invalid JWT` a fejlesztői konzolban; a funkció soha nem fut le.
- **Gyökérok**: A Supabase gateway alapértelmezés szerint ellenőrzi a JWT-t (`verify_jwt=true`). A `config.toml`-ban nem szereplő funkciók ezzel az alapértelmezéssel futnak. Az `eventbrite-import` soha nem igényelt felhasználói JWT-t (csak Eventbrite API kulcsot), de az admin UI-ból hívták lejárt/érvénytelen session tokennel.
- **Javítás**: A funkció újratelepítve `verify_jwt=false`-szal; hozzáadva a `config.toml`-hoz.
- **Megelőzés**: **MINDIG** ellenőrizd a `supabase/config.toml`-t minden edge function esetén. Ha egy funkció nem igényel felhasználói azonosítást (pl. admin-only, service-role alapú, API-key alapú), állítsd be `verify_jwt=false`. Ha a JWT ellenőrzés szükséges, gondoskodj róla, hogy az admin UI mindig friss session tokent küld.

---

## 📋 ELLENŐRZŐ LISTA (Minden commit előtt)

- [ ] Auth-kritikus lekérdezésben NINCS FK JOIN? (`profiles` select = egyszerű `select('*')`)
- [ ] Minden interface/type property megegyezik az SQL tábla oszlopaival?
- [ ] Supabase `.select()` FK relációk használatánál van `(row: any)` cast?
- [ ] Nincs explicit FK constraint név a Supabase select-ben?
- [ ] Nincs middleware-ben redirect?
- [ ] Auth check `getUser()`-t használ, nem `getSession()`-t?
- [ ] Fájlnevek Next.js konvenciónak megfelelnek (`page.tsx`, `layout.tsx`)?
- [ ] Egyedi CSS class-ok definiálva vannak a `globals.css`-ben?
- [ ] Lucide ikonok a hivatalos listáról importálva?
- [ ] RLS policy-kban nincs cross-table JOIN más RLS-védett táblára?
- [ ] Új SQL oszlopok esetén a kód `(row: any)` castot használ?
- [ ] Places / Search API hívásnál a provider-specifikus paraméterek validak?
- [ ] `lat/lon` sorrend, `radius`, `bbox`, `bias`, `filter` stratégia explicit ellenőrizve?
- [ ] Van throttling / retry / concurrency limit a külső kereső API-k körül?
- [ ] Nincs olyan utólagos hard filter, ami lenullázhatja a provider által már visszaadott érvényes listát?
- [ ] Az ismeretlen / `null` állapotokat (pl. `open_now`) nem kezeli-e túl szigorúan a szűrés?

### [HIBA-F009] Ollama-hívás timeout (300s) párhuzamos fájl-generálásnál
- **Dátum**: 2026-06-12
- **Fájl**: src/services/userService.ts, src/services/index.ts (kimaradt fájlok)
- **Hibaüzenet**: `gen hiba: Ollama hívás timeout (300s) — a hívás megszakítva, a folyamat továbblép.`
- **Gyökérok**: az Ollama alapból kevés kérést szolgál ki párhuzamosan (OLLAMA_NUM_PARALLEL); 3 egyidejű generálásnál a sorban várakozó kérés várakozási ideje + generálási ideje együtt átlépte a 300s-ot — a hívás nem "halt meg", csak torlódott.
- **Javítás**: (1) adaptív párhuzamosság: timeout észlelésekor a build SOROS módra vált és a kimaradt fájlokat újra sorra veszi; (2) OLLAMA_NUM_PARALLEL=4 környezeti változó beállítva (az Ollama következő indulásától él); (3) a 2c kör amúgy is újrapróbálja a kimaradt fájlokat.
- **Megelőzés**: párhuzamos LLM-hívásoknál a timeout a SOR VÉGI kérésre is vonatkozik — vagy emeld a szerver párhuzamosságát (OLLAMA_NUM_PARALLEL), vagy adaptívan ess vissza sorosra. A timeout utáni továbblépés helyes; a fájlt KÉSŐBB ÚJRA kell próbálni, nem elhagyni.

### [HIBA-F010] Lint-kapu bevezetve: ismert hibaosztály be sem kerülhet fájlba
- **Dátum**: 2026-06-12
- **Fájl**: agent-core.js (lintGate)
- **Hibaüzenet**: (megelőző mechanizmus, nem hiba)
- **Gyökérok**: a 7B modellek ismétlődő szintaktikai hibaosztályai (#-komment, .as cast, zárójel-egyensúly, érvénytelen JSON) prompttal csökkenthetők, de determinisztikus kapuval ZÁRHATÓK ki teljesen ("a linter mint agent-korlát" elv).
- **Javítás**: minden generált tartalom ÍRÁS ELŐTT determinisztikus ellenőrzésen megy át; hibánál a modell a konkrét lint-hibákkal újrageneráltat kap; második hibánál agent-fallbackre kerül a fájl.
- **Megelőzés**: új ismétlődő szintaktikai hibaosztálynál NE (csak) promptot írj — bővítsd a lintGate-et (agent-core.js), az determinisztikusan véd.

---

## 🔧 KATEGÓRIA: HENRIS FORGE — lokális (7-9B) kódgenerálási hibaminták (újra-rögzítve a fájl-összevonás után)

### [HIBA-F001] Python-stílusú # komment TypeScript/JavaScript fájlban
- **Dátum**: 2026-06-11
- **Fájl**: generált *.ts / *.js (pl. workOrders.service.ts)
- **Hibaüzenet**: `error TS1109: Expression expected` a `#`-os soroknál
- **Gyökérok**: a kis modell a Python komment-szintaxist keveri TS/JS kódba
- **Javítás**: minden `#` komment cseréje `//`-re; determinisztikus lint-kapu (agent-core.js lintGate) írás előtt fogja
- **Megelőzés**: TS/JS-ben komment KIZÁRÓLAG `//` vagy `/* */`.

### [HIBA-F002] `.as any[]` a helyes `as any[]` helyett
- **Dátum**: 2026-06-11
- **Hibaüzenet**: `error TS1003: Identifier expected`
- **Gyökérok**: a cast operátort metódusként írja a modell
- **Javítás**: `kifejezés as any[]`; lint-kapu fogja írás előtt
- **Megelőzés**: TypeScript cast: `expr as Tipus`. A `.as` NEM létezik.

### [HIBA-F003] Lezáratlan kapcsos zárójelek a generált fájlban
- **Dátum**: 2026-06-11
- **Hibaüzenet**: `error TS1005: '}' expected`
- **Gyökérok**: hosszú fájlnál a modell elveszti a blokk-egyensúlyt
- **Javítás**: zárójel-számláló a lint-kapuban; lapos szerkezet, kis függvények
- **Megelőzés**: max ~40 soros függvények, max 3 szint beágyazás.

### [HIBA-F004] node:sqlite lastInsertRowid bigint típushiba
- **Hibaüzenet**: `TS2322: Type 'number | bigint' is not assignable to type 'number'`
- **Megelőzés**: a lastInsertRowid-ot MINDIG Number()-rel konvertáld.

### [HIBA-F005] Sync DatabaseSync API Promise-ként kezelve
- **Hibaüzenet**: `TS2739: ... is missing the following properties from type 'Promise<...>'`
- **Megelőzés**: node:sqlite SZINKRON — nincs async/await/Promise a DB-rétegben.

### [HIBA-F006] Hiányzó export — "File ... is not a module"
- **Hibaüzenet**: `TS2306` / `TS2459`
- **Megelőzés**: minden modul exportáljon, amire import mutat; db.ts végén `export default db;` típusok csak a models-ből.

### [HIBA-F007] Python: hiányzó import a használó modulban
- **Hibaüzenet**: `name 'sqlite3' is not defined`
- **Megelőzés**: MINDEN python modul a saját elején importálja az ÖSSZES használt modult.

### [HIBA-F008] http.server send_error → HTML-lap JSON helyett + szerverhalál
- **Megelőzés**: API-hibaválasz mindig send_response + JSON; minden do_* try/except-ben; "/" a static/index.html-t adja.

---

## ⚡ FORGE-INJEKT: KÓDGENERÁLÁSI ALAPSZABÁLYOK (promptba injektálva — tömör, kötelező)

- TS/JS komment KIZÁRÓLAG `//` vagy `/* */` — `#` TILOS.
- TypeScript cast: `expr as Tipus` — a `.as` NEM létezik.
- A `{` és `}` száma egyezzen; max 3 szint beágyazás, függvény max ~40 sor, early return.
- node:sqlite SZINKRON: nincs async/await/Promise a DB-rétegben; lastInsertRowid → Number(...).
- Minden importált modul LÉTEZŐ exportra mutasson; db.ts végén `export default db;` típusok csak a models-ből.
- Python: minden modul importálja amit használ (sqlite3, json, os) — sose támaszkodj más modul importjára.
- Python http.server: send_response + JSON hibatörzs (send_error TILOS API-ra); minden do_* try/except-ben; "/" → static/index.html.
- PORT mindig env-ből; induláskor: `listening on <PORT>`.
- Paraméternek SOHA undefined/None — `?? null` ill. explicit ellenőrzés.
- Csak a kért EGY fájlt add vissza, EGY fenced blokkban, teljes tartalommal.
- SOHA ne módosíts tesztet azért, hogy átmenjen — a kódot javítsd.
- Tagadás helyett pozitív minta: "X helyett MINDIG Y-t használj".
- Hardcode-olt titok/API-kulcs TILOS — mindig env-változó.
- Frontend: a render a fetch UTÁN fusson (await/then), adat-frissítés után újra-render kötelező.
- appendChild-nak KIZÁRÓLAG Node-ot — HTML-sztringhez insertAdjacentHTML.
- Az app.js CSAK olyan elem-id-t használjon, ami az index.html-ben LÉTEZIK.
- SQL aggregátnál MINDIG alias: `SELECT COUNT(*) AS cnt` — és a kód az aliast (`.cnt`) olvassa.
- DASHBOARD-MINŐSÉG: a felület PRÉMIUM, többnézetes (sidebar `.app`+`.sidebar`+`.nav-item` + kliens-oldali nézet-váltás), NEM egyetlen lapos lista. A fő nézet tetején KPI-kártyasor (`.kpi-row`+`.kpi`, nagy szám + ikon + delta-trend + magyarázat). Vizualizáció Chart.js-szel (area-line trend, doughnut/gauge arány, vízszintes bar kategória) szemantikus színekkel (solar arany/peak piros/grid kék/saving zöld). NINCS mock-adat; a KPI-számok a VALÓDI API-listából aggregálva (sum/avg/max), Number()-coercióval.
- pg (Postgres) a NUMERIC/DECIMAL/BIGINT oszlopot STRINGként adja → a db.ts-ben `types.setTypeParser(1700, parseFloat)` + `(20, parseInt)`; a frontend renderelés előtt MINDIG `Number(x)`.
- Postgres CREATE TABLE sorrend: a hivatkozott (REFERENCES) tábla ELŐBB jöjjön létre (FK előre-hivatkozás → "relation X does not exist").
- Több erőforrás-router KÜLÖN prefixre: `app.use('/api/eszkoz', eszkozRoute)` — NE mind `/api`-ra (különben `/api/x` a `/:id`-re illeszkedik → NaN/500).
- A frontend KIZÁRÓLAG a valódi API-válasz MEZŐNEVEIT olvassa (élő mintából); kitalált mező (pl. item.hour) tilos.
- Scope-fegyelem: NINCS auth/JWT/login, ha a brief nem kéri explicit (a "célcsoport"/"szerepkör" közönség-szegmens, nem bejelentkezés).

# Lokális AI-kódolás — összegyűjtött tanulságok (50 forrás kutatása)

Cél: a HENRIS Forge (Electron + Ollama, 7-9B modellek) továbbfejlesztéséhez releváns,
azonnal hasznosítható tudás. A [FORGE] jelölés = beépítve vagy beépítendő a pipeline-ba.
Kapcsolódik: [MULTI_AGENT_DOKTRINA.md](MULTI_AGENT_DOKTRINA.md), ../codingLessonsLearnt.md.

---

## 1. Infrastruktúra és modellválasztás

- **[FORGE/beépítve] VRAM-szabály**: a modellméret 2-3-szorosát hagyd szabadon kontextusra; a RAM-ba
  átcsorduló modell nagyságrendekkel lassul. 7-9B + 16 GB VRAM = jó párosítás.
- **[FORGE] Kvantálás: a pontosság nyer** — kódhoz egy kisebb modell Q6_K/Q8_0-on gyakran jobb,
  mint nagyobb modell Q4-en. Próbáld: `ollama pull qwen2.5-coder:7b-instruct-q8_0`, és a saját
  feladatsoron mérd (ne benchmarkon).
- **Kvantálási csapdák**: flash-attention, KV-cache-kvantálás, mixed-precision csendben ronthat
  minőséget vagy sebességet — pipeline-loopokban kerülendő az agresszív KV-cache-kvant.
- **[FORGE/beépítve] 20B alatt nincs megbízható "autonóm agent"**: gyenge strukturált kimenet és
  tool-calling. Ezért helyes a Forge fix, lépésenkénti pipeline-ja (terv → fájlonkénti generálás →
  kapuk), NEM a szabadon felfedező agent.
- **Kontextus-realizmus**: a hirdetett nagy ablakok ellenére 4-8k token a megbízható munkatartomány;
  a prompt-feldolgozás nemlineárisan lassul nagy kontextusnál.
- **[FORGE/beépítve] Kód-specialista modellt** válassz általános helyett (qwen2.5-coder > qwen általános).
- **[FORGE/beépítve] OLLAMA_NUM_PARALLEL**: a párhuzamos hívásokhoz a szerver-oldali párhuzamot is
  engedni kell (4-re állítva); különben a sorban álló kérés torlódik és timeoutol.

## 2. Kontextus-építés (a kis modell legdrágább erőforrása)

- **[FORGE/beépítve] Whitelist-elv**: csak az kerül a promptba, ami a tervben az adott fájl deklarált
  függősége — "a kontextus nem méret, hanem releváncia".
- **[FORGE/beépítve] Friss, minimális prompt minden fix-iterációban** (hibaüzenet + érintett fájl) —
  előzmény-halmozás tilos; a "kontextusvámpír" agent ugyanazt a fájlt olvasgatja és tokent éget.
- **[FORGE/beépítve] Single-file modulok**: a fájlonkénti generálás természetes egysége az önállóan
  generálható fájl, minimális kereszt-függőséggel; vertical-slice bontás réteg-dömping helyett.
- **grep > RAG**: lokális kódagenshez sima szövegkeresés hatékonyabb, mint embedding-RAG —
  a Forge-nak nem kell embedding-infrastruktúra.
- **[FORGE] Szignatúra-kontextus**: a függő fájlokból elég a függvényszignatúra + interfész, nem a
  teljes tartalom (tovább csökkenthető a kontextus-budget).

## 3. Prompt- és szabályrendszer-tervezés

- **[FORGE/beépítve] Direktív nyelv**: "MINDIG X / SOHA Y" — a 7-9B a feltételes-udvarias megfogalmazást
  ignorálja. A FORGE-INJEKT szekció pontosan így épül.
- **[FORGE/beépítve] Nevesített tilalom-lista** (anti-pattern lista) a promptban — a homályos
  "írj szép kódot" hatástalan.
- **[FORGE/beépítve] Tömörség = token-budget**: bőbeszédű/önellentmondó szabály fókuszvesztést okoz;
  rövid szabályblokk minden promptban + részletes referencia csak hivatkozásként.
- **[FORGE/beépítve] Kétszintű dokumentáció**: (1) rövid kötelező szabályfájl (codingLessonsLearnt
  kivonat), (2) részletes architektúra-referencia (recreation_prompts/) csak komplex esetre.
- **[FORGE] Csak ismételhető tudás kerüljön a perzisztens szabályfájlba** — eseti fixek a
  prompt-kontextusban maradjanak (a session_log őrzi őket historikusan).
- **[FORGE] A terv kényszerítse ki fájlonként**: cél + bemenet/kimenet + elfogadási feltétel —
  a homályos brief homályos kódot szül.

## 4. Pipeline-architektúra és kapuk

- **[FORGE/beépítve] "Egy komponenst generálj, integráld, teszteld, csak utána a következőt"** —
  kisebb lépés = kisebb hiba.
- **[FORGE/beépítve] Rétegzett, költségtudatos eval**: determinisztikus ellenőrzés (lint, típus,
  szintaxis, grep) ELŐBB és ingyen; LLM-bíráló CSAK ahol tényleg ítélet kell, szűkített kimenettel
  (PASS/FAIL + 1 mondat).
- **[FORGE/beépítve] "Linter mint agent-korlát"**: az ismert hibaosztály determinisztikus kapuval
  zárandó ki (lintGate), nem (csak) prompttal.
- **[FORGE/beépítve] Hallucinált hivatkozás determinisztikus fogása**: import-kapu — lokális import
  csak a fájltervben létező modulra mutathat.
- **[FORGE/beépítve] Validációs lánc elfogadás előtt**: syntax → típus → indulás → HTTP-QA → tesztek;
  egy fájl/projekt sem "kész" zöld kapuk nélkül.
- **[FORGE/beépítve] Visszafordulás**: N sikertelen iteráció után állj le és kérj emberi terelést
  (MAXFIX/MAXQA korlátok), ne pörögj tovább.
- **[FORGE/beépítve] Párhuzamos read-only, soros mutáció**: generálás/olvasás párhuzamosítható,
  az írás/kompozíció soros — single-writer szabály fájlonként.
- **[FORGE] Kereszthivatkozás-ellenőrzés lezárás előtt**: átnevezett/exportált szimbólumokra grep
  a fix-kör után.
- **Biztonsági réteg**: statikus elemzés (eslint/semgrep-szintű) a compile-kapu után — SQL-injection,
  beégetett kredenciálok ellen (későbbi bővítés).

## 5. Munkamódszer és tudás-hurok

- **[FORGE/beépítve] Plan-first, modell-megosztás**: erősebb modell tervez, gyorsabb végrehajt —
  a Forge-ban a modellválasztó ezt lehetővé teszi (pl. qwen3:8b terv, coder végrehajtás).
- **[FORGE/beépítve] AGENTS.md minden projektbe**: pontos parancsok, struktúra-térkép, konvenciók,
  NE-MÓDOSÍTSD lista ("térkép, nem enciklopédia") — minden későbbi AI-session ebből tájékozódik.
- **[FORGE/beépítve] "Agent-hiba = repó-ticket" hurok**: minden futási hibánál kérdés: megelőzhette
  volna a repó/szabály? → codingLessonsLearnt append (automatizálva), lintGate-bővítés, hint-erősítés.
- **[FORGE/beépítve] Session-szétválasztott munka**: terv / implementálás / javítás külön, mentett
  állapotú lépések (folytatható sessionök).
- **Eval = unit teszt a pipeline-ra**: prompt-változtatások regressziós tesztelése a tesztharnessen
  (test_stack.js / test_lora.js futtatása minden orchestrátor-módosítás után).

---

*(A 2. fele — a fennmaradó ~29 forrás kutatási eredménye — a párhuzamos kutatófutás végén kerül ide.)*

---

# 2. RÉSZ — A fennmaradó 29 forrás tanulságai (4 párhuzamos kutató, workflow)

## Köteg A — Szabályfájlok és review-agentek (daily.dev, Nexapp, Zenn, Cursor Rules, workweave, digitalchild)

## 1. 10 Lessons for Agentic Coding (daily.dev)
(nem elérhető) — az oldal csak a címet adja vissza, a tartalom JS mögött van.

## 2. AI Coding Assistants: Complete Guide and Best Practices (Nexapp, 2026)
- **[FORGE] Kontextusfájl 500 sor alatt:** a promptba injektált szabály/lessons fájl maradjon ~500 sor alatt, különben telíti a kis modell "azonnali memóriáját" — a Forge lessons-fájljára vágási limitet (sor- vagy tokenplafont) érdemes tenni, a legrégebbi/legritkábban triggerelt tanulságok kiesnek.
- **[FORGE] Magas hozamú feladattípusokra fókuszálj:** refaktor, unit-teszt generálás, hibaanalízis stack trace alapján, docstring — a 7-9B modellek ezekben erősek; nyílt végű "találd ki" feladatokban gyengék.
- **[FORGE] AI öntesztelés a pipeline-ban:** a generálás után a modell maga generáljon unit-tesztet, és a teszt lefutása legyen kapu (a Forge "generated tests" lépése pontosan ez — a tanulság: a tesztgenerálást a kódgenerálástól KÜLÖN promptban/hívásban futtasd, ne ugyanabban).
- **[FORGE] Függőség-hallucináció ellenőrzés:** automatikus kapu, ami ellenőrzi, hogy a generált import/require/package.json-függőség létezik-e (npm registry lookup vagy lokális whitelist) — kis modelleknél ez az egyik leggyakoribb hibamód.
- **[FORGE] Secret-szűrés a kimeneten:** Gitleaks/TruffleHog-szerű regex-kapu a lint-gate mellé, hogy a modell ne írjon ki API-kulcs mintázatú stringeket.
- **Tesztrobusztusság arányosan nőjön a generálási sebességgel:** minél több fájlt generál párhuzamosan a pipeline, annál szigorúbb legyen a QA-kapu — a sebesség önmagában nem érték.
- **Mérj DORA-szerűen, ne "generált sorokban":** a Forge dashboardján a hasznos metrika: első-próbára-átmenő fájlok aránya, compile-fix iterációk száma, pipeline-átfutási idő.

## 3. Lessons Learned from Having AI Agents Review Code — Claude Code Action (Zenn / azuma317)
- **[FORGE] A brief minősége ~ a kimenet minősége:** "az output minősége majdnem arányos azzal, ahogy az Issue meg van írva" — a Forge brief-lépésébe tegyél kötelező mezőket: scope, constraints, elkészültségi kritérium. Hiányos brief → a pipeline kérdezzen vissza, ne induljon el.
- **[FORGE] Kétlépcsős Plan→Do szinte kiküszöböli a teljes újramunkát:** a terv fázis után legyen jóváhagyási/korrekciós pont, mielőtt a párhuzamos generálás elindul — a Forge plan-lépése után egy olcsó validáló kör (akár ugyanazzal a modellel: "ellenőrizd a tervet a brief ellen") sokat hoz.
- **[FORGE] Automatikus feladat-darabolás:** ha a terv >10 implementációs lépést vagy több, egymástól független feature-t tartalmaz, a pipeline automatikusan bontsa több futásra — kis modellnél ez még kritikusabb, mint Claude-nál.
- **[FORGE] Konkrét mintákat dokumentálj a szabályfájlban, ne elveket:** egy konkrét pattern leírása ("X helyett `as unknown as never`-t használj") megszüntette a visszatérő lint-hibát — a Forge lessons-fájlba pontosan ilyen, kód szintű "ezt így írd" bejegyzések kerüljenek.
- **[FORGE] Build/install a modellfutás ELŐTT:** futtasd le a `npm install`/build-et mielőtt a generálás indul, hogy a compile-fix kör valódi kódhibákat lásson, ne környezeti hibákat; cache-elt node_modules drámaian gyorsít.
- **[FORGE] Eszközjogosultság fázisonként:** plan-fázis = csak olvasás; do-fázis = csak a whitelistelt parancsok — a Forge orchestrator szintjén érdemes fázisonkénti tool-szűkítést bevezetni.
- **Nyílt végű nyomozós feladatok buknak:** "miért lassú?" típusú diagnosztika a futási környezet ismerete nélkül célt téveszt — az ilyet a HTTP QA lépés konkrét, mérhető ellenőrzéseire kell lefordítani.
- **Emberi review fókusz:** formázást/importokat a gép intézi (lint-gate), az ember az üzleti logikát és a scope-tartást nézze.

## 4. Cursor Rules: The Complete Guide (skillsplayground.com)
- **[FORGE] Fix fejléc-struktúra a szabályfájlban:** "Tech Stack / Architecture / Conventions / Testing / Avoid" szekciók — a Forge minden promptba injektált kontextusát érdemes erre a sémára normalizálni, a kis modell jobban parsolja a kiszámítható szerkezetet.
- **[FORGE] Explicit "Avoid" szekció:** külön blokk a tipikus AI-hibákra ("silent fail tilos", "deprecated API X tilos") — a Forge lessons-fájl hibái ide aggregálódjanak.
- **[FORGE] Kódblokk = sablon:** a szabályok közé tett kódrészleteket a modell template-ként értelmezi — adj 1-1 mintafájlt (komponens, endpoint, teszt) a generáló promptba; kis modellnél a few-shot példa többet ér, mint 20 sor szöveges szabály.
- **[FORGE] Konkrét, mérhető szabályok:** "early return, max 40 sor/függvény" típusú, gépileg is ellenőrizhető szabályok — ezek egy részét a lint-gate-be is be lehet kódolni, így a szabály + a kapu ugyanazt mondja.
- **Moduláris szabályok kontextus szerint:** ne egy óriásfájl, hanem fájltípus/feladattípus szerint betöltött darabok — a Forge per-file generálásánál csak az adott fájlhoz releváns szabályszeletet injektáld.
- **Élő dokumentumként kezeld:** verziókezeld, és frissítsd, amikor a konvenció változik; a 2000 soros fájl rontja a kimenetet.

## 5. What to Put in My Team's Cursor Rules File (workweave.dev)
- **[FORGE] Pozitív megfogalmazás:** "írd le, MIT csinálj, ne azt, mit NE" — a kis modellek rosszul kezelik a tagadást; a Forge lessons-bejegyzéseket tárolás előtt érdemes pozitív formára átírni (akár automatikusan, egy átfogalmazó prompttal).
- **[FORGE] Könyvtárszerkezet + mappa-célok a promptban:** "src/components = X, src/api = Y" — a per-file generálásnál a fájl helye + a szomszédos mappák szerepe legyen a prompt része, így a modell jó helyre importál.
- **[FORGE] Verziószámok a szabályokban:** a függőségek pontos verzióját add meg, különben a modell elavult szintaxist generál (kis modellnél a training-cutoff miatt ez hatványozottan igaz).
- **[FORGE] Ha a modell ismétli a hibát → a szabály rossz:** a Forge compile-fix loopja számolja, melyik hibatípus ismétlődik; ismétlődés = a lessons-fájl adott bejegyzése elégtelen, írd át konkrétabbra (ez automatizálható trigger lehet).
- **Konkrét példa > absztrakt leírás:** API-hívás, error handling, state-kezelés valós kódpéldával.
- **Rövid indoklás a minták mögé:** egy mondat "miért", hogy a modell új helyzetben is jól általánosítson.
- **Sok kis fájl egy óriás helyett:** nyelv-/mappa-specifikus szabályfájlok, csak a releváns töltődik be.

## 6. digitalchild/cursor-best-practices (GitHub)
- **[FORGE] `instructions.md` a munka megkezdése ELŐTT:** teljes projektspecifikáció (feature-ök, technológiák, struktúra, build-lépések) generálása mielőtt bármi kód készül — a Forge brief→plan átmenetekor ezt az artefaktumot érdemes explicit fájlként előállítani és minden további prompthoz csatolni.
- **[FORGE] "Erősítsd meg, hogy érted a feladatot":** a generálás előtt kérd vissza a modelltől saját szavaival a feladatot — kis modellnél ez olcsó és hatékony félreértés-detektor; ha a visszamondás eltér a brieftől, állj meg.
- **[FORGE] Szabály-precedencia definiálása:** manuális > auto-attach > agent-kért > mindig-érvényes — a Forge promptösszeállítójában legyen determinisztikus sorrend: brief > plan-kivonat > fájlspecifikus szabály > globális lessons, és ütközésnél a specifikusabb nyer.
- **[FORGE] Indexelési ignore-lista:** `.cursorignore`-megfelelő a Forge-ban: a kontextusgyűjtés zárja ki a node_modules/build/dist tartalmat, hogy a szűkös kontextusablak ne szemetelődjön.
- **Komponálható, fókuszált szabályblokkok:** <500 sor/szabály, konkrét név + leírás minden blokknak, újrafelhasználás duplikálás helyett.
- **Domain szerinti kontextusbontás:** frontend/adatbázis/API külön kontextusfájl — per-file generálásnál csak az érintett domain töltődik.
- **`roadmap.md` a hosszú távú irányhoz:** mérföldkő-terv, amihez a tervező lépés igazodhat több futás között.

## Köteg B — Workflow-minták (Cursor hivatalos, Aider+Ollama, ericmjl, Softcery, jameschambers)

# Kutatási összefoglaló: best practice-ek kis lokális modelles kód-orkesztrációhoz

## 1. Cursor — Agent Best Practices (cursor.com/blog/agent-best-practices)

- **Terv előbb, kód utána** — a tervezési fázist külön lépésként futtasd, a tervet fájlba mentve (`plans/` mappa), hogy a generálás determinisztikusan kövesse. [FORGE] — a brief→plan lépés kimenetét érdemes perzisztálni és a per-file prompt­okba csak a releváns terv-szeletet injektálni.
- **Ha az iteráció elakad, ne foltozgass — állj vissza a tervhez** — revert + terv pontosítása + újrafuttatás gyorsabb és tisztább, mint sokadik follow-up prompt. [FORGE] — compile-fix loopba max-retry után „rollback a plan-hez és regenerálás" ág beépítése.
- **Hosszú beszélgetés = zaj-akkumuláció** — sok kör és összegzés után az agent fókuszt veszít; logikai egységek után indíts friss kontextust. [FORGE] — a Forge per-file friss kontextusa pont ezt csinálja; a fix-loopban se görgessétek a teljes előzményt, csak az aktuális hibát + fájlt.
- **Verifikálható célokat adj** — „az agent nem tudja kijavítani, amiről nem tud": típusos nyelv, linter, tesztek = automatikus korrektségi jel. [FORGE] — a lint-gate + compile-fix + HTTP QA pipeline pontosan ez; minél több gépi jelet visszacsatolni a promptba.
- **TDD-loop**: teszt először, igazold hogy bukik, aztán implementálj — explicit kimondva, hogy ne mock-implementáció szülessen.
- **Rules/lessons fájl frissítése ismétlődő hibáknál** — ha az agent ugyanazt rontja el, a szabályfájlba kerüljön be; fájlokra hivatkozz, ne másold be a tartalmat. [FORGE] — a lessons-fájl injektálás megvan; automatizálható, hogy a fix-loop ismétlődő hibamintái lesson-jelöltként rögzüljenek.
- **Párhuzamos próbálkozás ugyanarra a feladatra** — ugyanaz a prompt több modellel/példánnyal, legjobb kimenet kiválasztása. [FORGE] — nehéz fájloknál N-jelöltes generálás + lint/compile alapú best-of kiválasztás 7-9B modellnél sokat hozhat.
- **Debug-mód mintázat**: hipotézis → loggolás beszúrása → futtatás → runtime adat elemzése — race conditionökre, regressziókra.

## 2. Zero-Cost AI Pair Programming: Aider + Ollama (dev.to/ryoryp)

- **`num_ctx` explicit beállítása (pl. 32768)** — az Ollama default kontextusablak „csendes adatvesztést" okoz: a modell elfelejti a korábbi tartalmat hibaüzenet nélkül. [FORGE] — minden Ollama-hívásnál explicit `num_ctx`, és a prompt-méret előzetes token-becslése, hogy ne lógjon ki.
- **`edit_format: whole` kis modelleknél** — ha a modell nem bírja a diff-alapú szerkesztést, teljes fájl-regenerálás megbízhatóbb: „tiszta, működő fájl, még ha lassabb is". [FORGE] — 7-9B-nél a compile-fix loopban teljes fájl újragenerálása biztonságosabb, mint patch-elés.
- **Csak a módosítandó fájlokat add a kontextusba**, a referencia-anyagot read-only-ként jelöld — csökkenti a véletlen módosítást és a zajt. [FORGE] — per-file generálásnál a többi fájlból csak interfész/aláírás-szintű kivonatot injektálni.
- **Minden változás után azonnali commit + olcsó undo** — git-alapú rollback minden generálási lépéshez. [FORGE] — pipeline-lépésenkénti snapshot/commit, hogy a fix-loop visszaléphessen.
- **Architect/Editor felosztás**: erősebb modell tervez, gyengébb/olcsóbb végrehajt. [FORGE] — a plan lépéshez nagyobb modell (pl. 14B quantized vagy felhő opcionálisan), a per-file generáláshoz a gyors 7B.
- **Ismert kis-modell hibamódok**: törött markdown-kerítés, utasítás elfelejtése feladat közben — kontextus-tuning nélkül ezek garantáltak. [FORGE] — output-parser legyen toleráns (kerítés nélküli kód, csonka fence), és a kulcs-utasításokat a prompt VÉGÉN ismételd meg.

## 3. How to Use Coding Agents Effectively (ericmjl.github.io)

- **Tervezés és végrehajtás szétválasztása külön modellerősségek mentén** — a tervező modell elemzésben jó, a végrehajtó olcsóbb/megbízhatóbb implementálásban; a terv markdown-dokumentum, amit a végrehajtó követ. [FORGE] — a plan kimenete legyen gépileg parsolható feladatlista (fájl + cél + elfogadási kritérium).
- **Meta-workflow: Plan → Execute → Test → újrafuttatás zöldig → Audit a terv ellen** — „nem kell fancy prompt, a világosság számít". [FORGE] — a pipeline végére „audit a plan ellen" lépés: a kész kódot a tervvel összevetni (akár a 7B modellel checklist-ként).
- **Az első zöld teszt után is futtasd a TELJES suite-ot** — ne állj meg az első sikernél, amíg az egész rendszer nem stabil. [FORGE] — fix-loop után teljes regressziós futtatás, ne csak az érintett fájl tesztje.
- **AGENTS.md mint külső memória** — architekturális preferenciák, tool-minták, standardok dokumentálása, hogy az agent „megtanulja" a konvenciókat. [FORGE] — ez a lessons-fájl koncepció validálása; érdemes szétválasztani „projekt-konvenciók" és „hibákból tanult szabályok" szekcióra.
- **`/remember` jellegű parancs**: tanulság azonnali beírása a memóriafájlba. [FORGE] — egykattintásos „lesson hozzáadása" gomb a Forge UI-ban a QA-fázis hibáinál.
- **Issue-tracker mint állapot-memória** — jövőbeli munkák perzisztens backlogja, az agent hivatkozhat rá.
- **Kis scope-pal indulj, fokozatosan növeld** — előbb kis, reviewolható darabok, ahogy kiismered a modell hibamintáit.
- **2-3 gyors prototípus-iteráció architektúra-felderítésre** — szándékos „eldobható" futások a határvonalak megtalálására, mielőtt véglegesítesz.

## 4. A-GNT: Aider agent-oldal (a-gnt.com/agents/aider)

(nem elérhető — az oldal betöltött, de csak marketing-szintű leírást ad, érdemi technikai tartalom nélkül; a 2 megerősített tény: Aider a teljes kódbázis „repo map"-jét használja koherens többfájlos módosításhoz, és minden változásról git commitot készít a review/revert érdekében — mindkettő a 2. forrásban részletesebben szerepel)

## 5. Building with AI Coding Agents (medium.com/@elisheba.t.anderson)

- **Plan–Act–Reflect keretrendszer** — kód előtt tervjavaslat, kis és moduláris scope, végén reflexió. [FORGE] — a generálás után rövid ön-ellenőrző kör („Reflect": a modell saját kimenetét checklist ellen értékeli) olcsó minőségjavítás 7B-nél is.
- **`agent.md` szerep- és korlát-definíció** — a modell szerepe, direktívái, tilalmai egy „blueprint" fájlban, minden sessionben konzisztensen. [FORGE] — a lessons-fájl mellé statikus persona/szabály-blokk, ami sosem változik (stabil prefix → Ollama prompt-cache barát).
- **Explicit guardrail-ek**: mely fájlok módosíthatók, dependency-telepítés tiltása, infrastruktúra-változáshoz megerősítés. [FORGE] — a generátor írási jogát whitelist-tel korlátozni a plan-ben kiosztott fájlokra; minden máshoz emberi jóváhagyás.
- **„Képzett junior fejlesztőként" kezeld** — hatékony, de mindig felügyeletet igényel; logika-ellenőrzés + tesztfuttatás + design-cél egyezés.
- **Verziókövetési fegyelem**: kis logikai commitok, AI-generált branch-ek megjelölése, hogy ne legyen „néma sodródás" követetlen AI-szerkesztésekből. [FORGE] — Forge-commitok címkézése (pl. `[forge:gen]`, `[forge:fix]`) a későbbi auditálhatósághoz.
- **Gyenge vs. erős prompt**: „Build a dashboard" helyett technológiák + komponensek + követelmények felsorolása.

## 6. Softcery — Agentic Coding Best Practices (softcery.com/lab)

- **TRD (Task Requirement Document) elfogadási kritériumokkal** kód előtt: függőségek, implementációs stratégia, mérhető siker („/api/users 1000+ találatnál timeout-ol → lapozás 100/oldal, válasz <200ms" a „javítsd a teljesítményt" helyett). [FORGE] — a brief→plan lépés kimenetébe kötelező mezőként: fájlonkénti elfogadási kritérium, amit a QA-fázis gépileg ellenőriz.
- **`progress.md` feladatkövetés** — az agent folytatni tudja a munkát a teljes scope újraelemzése nélkül. [FORGE] — pipeline-állapot perzisztálása fájlba, hogy megszakadt futás folytatható legyen.
- **Determinisztikus hook-ok, nem prompt-szabályok** — a PreToolUse-jellegű hook shell-kódként fut, „nem kerülhető meg ügyes beszélgetéssel". [FORGE] — kulcstanulság: a lint-gate/írás-whitelist KÓDBAN legyen kikényszerítve, ne a promptban kérve — kis modellnél ez különösen kritikus.
- **5 prompt-szabály**: (1) adj másolható mintát („mint a TeamService osztályunk"), (2) explicit határok, (3) mérhető siker, (4) hivatkozz a source-of-truth fájlra, (5) követelj tervet kód előtt. [FORGE] — a per-file prompthoz mindig egy létező, hasonló fájl mint minta — kis modellek mintakövetésben sokkal jobbak, mint szabálykövetésben.
- **Session-memória**: `memory.md` + session-logok kimenetekkel és tanulságokkal; minden hiba → preventív szabály, minden siker → újrafelhasználható minta. [FORGE] — a lessons-fájl mellett sikeres minták könyvtára, amiből a generátor példát kap.
- **Tipikus hibamódok és ellenszereik**: context blindness (→ dokumentált architektúra), scope creep (→ „csak ezeket a fájlokat módosítsd"), hallucinált standardok (→ source-of-truth hivatkozás), context-overflow (→ friss session feladatonként). [FORGE] — mind a négy közvetlenül leképezhető a Forge prompt-sablonjaira.
- **Subagent izolált kontextusban nehéz felderítéshez** — csak összegzést ad vissza a fő folyamatnak. [FORGE] — a plan-fázis előtti kódbázis-elemzést külön hívásban futtatni, és csak a tömör kivonatot továbbadni.
- **Mérj**: PR-átfutási idő, első-körös CI-siker arány, hibaarány — állítások helyett hatás. [FORGE] — Forge-metrikák: első-körös compile-siker %, fix-loop iterációszám, QA-bukások — ezekből látszik, melyik prompt/lesson-változtatás működik.

## 7. Coding with LLMs 101 (jameschambers.co.uk)

- **„Tudnod kell, hogy néz ki a jó"** — ha nem ismered fel a hibát, nem tudod kiszűrni; az emberi review nem kiváltható.
- **„Nagyon olvasott junior fejlesztő" mentális modell** — részletes követelmény-doksi, kis, jól definiált feladatok a nyílt végű promptok helyett. [FORGE] — a plan-lépés bontsa a feladatokat olyan kicsire, amit egy junior egy ülésben hibátlanul megoldana — ez a 7-9B modellek reális kapacitása.
- **Terv-review FRISS sessionben** — a generált tervet külön, előzmény nélküli kontextusban értékeltesd, így a hibák nem kompaundálódnak. [FORGE] — plan-validátor lépés: a tervet egy friss modellhívás bírálja el (konzisztencia, hiányzó fájlok, körkörös függőségek), mielőtt a generálás indul.
- **Implementáció-review a terv ellen, megint külön sessionben** — a kész kód tervhez mérése független kontextusban.
- **Commit korán, commit gyakran, feature branch-ek** — teljes commit minden új feladat előtt, gyors rollback a rossz javaslatokra.
- **Párhuzamos példányok pipeline-szerűen**: egyik tervez, másik végrehajt, közben te írod a következő feladatleírást — a Forge párhuzamos per-file generálása ennek automatizált formája.

---

### Átfogó minták (több forrás egybehangzóan)

1. **Terv és végrehajtás szigorú szétválasztása, a terv mint perzisztált, gépileg ellenőrizhető artefakt** (1, 3, 5, 6, 7). [FORGE]
2. **Friss, minimális kontextus feladatonként — a kontextus-felhalmozás a fő ellenség** (1, 2, 6, 7). [FORGE]
3. **Gépi visszacsatolás (lint/compile/teszt) > prompt-szabály; kikényszerítés kódban, nem kérésben** (1, 3, 6). [FORGE]
4. **Lessons/memóriafájl folyamatos, hibavezérelt frissítése — minden ismétlődő hiba szabállyá válik** (1, 3, 6). [FORGE]
5. **Kis modellnél: teljes fájl regenerálás diff helyett, explicit num_ctx, toleráns output-parser, minta-fájl a promptban, kulcs-utasítás a prompt végén** (2, 6). [FORGE]

## Köteg C — Minőség-kapuk és eval-vezérelt fejlesztés (raphaelstaebler, thepromptshelf, agentclash, 0xdevalias, agentpatterns, alanwest, haboshi)

# Forrás-összefoglalók — HENRIS Forge kutatás

## 1. What I Learned Building Software With AI Agents (raphaelstaebler.info)

- **Szigorú típusrendszer = kevesebb AI-hiba.** Minél szigorúbb a típusellenőrzés (TS strict mode), annál kevesebb hibát termel a modell — a compile-fix loop bemenete legyen `tsc --strict`, ne csak sima fordítás. [FORGE]
- **A tesztek végrehajtható specifikációk ÉS minták.** A generált tesztfájlokat tedd be a következő generálási promptba kontextusként — a kis modell lemásolja a mintát. [FORGE]
- **Lint mint korlát, nem vita.** ESLint+Prettier konfigurációval a stílust a tooling kényszeríti ki, nem a prompt — a lint-gate pontosan ez, de auto-fix (`eslint --fix`) fusson ELŐBB, csak a maradék menjen vissza a modellnek. [FORGE]
- **Hibaelemzés gyökérok szerint:** ha a generálás elromlik, döntsd el: hiányzó kontextus vagy egymásnak ellentmondó minták a kódbázisban — a lessons fájlba a gyökérokot írd, ne a tünetet. [FORGE]
- **Modulhatárok explicit definiálása a tervben** (mi a felelőssége, mi NEM) csökkenti a fájlok közti logika-szivárgást a párhuzamos per-fájl generálásnál. [FORGE]
- **Kontextus-optimalizálás:** tiszta, fókuszált modulok → kevesebb kontextus kell fájlonként, ami 7-9B modellnél kritikus.

## 2. AGENTS.md for OpenAI Codex Guide (thepromptshelf.dev)

- **Számozott lépések, ne beágyazott feltételek** — a kis modellek a lineáris folyamatleírást követik megbízhatóan. A Forge pipeline-promptok legyenek számozott listák. [FORGE]
- **Negatív szabály mellé mindig pozitív alternatíva:** ne "ne használj X-et", hanem "X helyett mindig Y-t használj" — a lessons fájl bejegyzéseit így fogalmazd át. [FORGE]
- **A formátum-specifikációkat követik legjobban a modellek:** explicit output-formátum (JSON séma, fájlstruktúra) a prompt legerősebb eszköze. [FORGE]
- **Csak ténylegesen kikényszerítendő szabályokat írj,** ne "aspirációsakat" — a modell szó szerint értelmez.
- **Szabálytesztelés megfigyelhető outputtal:** 3 egyszerű szabállyal ellenőrizd, hogy a lessons-injektálás működik-e (pl. fájlnév-konvenció), mielőtt bővíted. [FORGE]

## 3. AI Agent Evaluation Needs Regression Testing (agentclash.dev)

- **"Az út is számít":** ne csak a végeredményt logold, hanem a teljes futást (input, akciók, artefaktok, metrikák) — a Forge run-okat mentsd strukturált JSON-ba újrafuttathatóan. [FORGE]
- **Production-hibákból regressziós csomag:** minden valós pipeline-hibát alakíts permanens tesztesetté ("challenge pack": brief + elvárt validáció) — a lessons fájl mellé egy futtatható eval-készlet. [FORGE]
- **Baseline vs. kandidát összehasonlítás:** modell- vagy promptváltás előtt ugyanazt a brief-készletet futtasd a régi és új beállítással, és csak mérhető javulásnál válts. [FORGE]
- **Metrikák dimenziónként külön:** helyesség / költség (token) / latencia / artefakt-minőség külön pontszám, hogy egyik se maszkolja a másikat. [FORGE]
- **Release-gate:** prompt-sablon módosítás csak akkor mehet be, ha az eval-suite nem romlik.

## 4. AI Agent Rule/Instruction Files gyűjtemény (gist, 0xdevalias)

- **Hierarchikus szabály-merge:** általános szabályok a gyökérben, könyvtár-specifikusak mélyebben, a mélyebb felülír — a Forge lessons fájl is lehet rétegzett: globális + projekt-szintű. [FORGE]
- **Glob-szkópolt szabályok:** YAML frontmatter `globs` mintával csak az érintett fájltípusra injektálj szabályt (TS-konvenció csak .ts fájlok generálásánál) — token-spórolás 7-9B modellnél. [FORGE]
- **`@fájl` import duplikáció helyett:** moduláris guidance, egy törzsfájl + hivatkozások.
- **Környezeti furcsaságok dokumentálása** (fordító, dependency-quirk) az instrukciós fájlban csökkenti az agent próbálkozásait — pont a Forge "hard-won lessons" elve.
- **Denylist érzékeny fájlokra** (.env, secrets) — a generátor soha ne olvassa/írja ezeket. [FORGE]
- **Szabályok iteratív mérése:** az instrukciós fájl is prompt — mérd a betartást, finomítsd a megfogalmazást ("IMPORTANT" jelölők kis modellnél is segítenek).

## 5. Eval-Driven Development (agentpatterns.ai)

- **Eval ELŐBB, feature utána:** a sikerkritériumot a fejlesztés előtt írd le, különben a rendszer aktuális (hibás) viselkedéséből visszafejted — a bugok beégnek.
- **20-50 reprezentatív feladat elég kezdésnek:** valós hibákból, edge case-ekből, manuális tesztekből — a Forge-hoz egy fix brief-készlet (CRUD app, API kliens, parser stb.). [FORGE]
- **Egyértelmű pass/fail kritérium:** ha két ember nem ért egyet az értékelésben, a feladat rossz — előbb manuális iteráció, utána formalizálás.
- **Grader-választás:** determinisztikus kimenethez automatikus check (teszt lefut, séma validál, HTTP 200) — pontosan a Forge HTTP-QA lépése; szubjektívhez LLM-as-judge explicit rubrikával. Kerüld az exact-match string-összehasonlítást. [FORGE]
- **Baseline pass-rate futtatás fejlesztés előtt:** a kiinduló hibaarány mutatja a valódi rést.
- **Eval-suite = gyors modellváltás:** új Ollama-modell (pl. qwen3-coder) kipróbálása napok helyett órák, ha van suite. [FORGE]

## 6. Local LLM Code Completions Are Slow (dev.to/alanwest)

- **Q5_K_M a kvantálási sweet spot:** ~1-2% HumanEval-veszteség FP16-hoz képest, ~60% memória-megtakarítás; 7B-nél Q6_K is belefér a 16GB VRAM-ba. [FORGE]
- **KV-cache kvantálás:** `--cache-type-k q8_0 --cache-type-v q8_0` → 30-50% VRAM-megtakarítás inferencia alatt, kódnál elhanyagolható minőségromlás (Ollama: `OLLAMA_KV_CACHE_TYPE=q8_0` + flash attention). [FORGE]
- **Ne túlméretezd a kontextusablakot:** csak akkora `num_ctx`, amennyi a feladathoz kell — a nagyobb ablak VRAM-ot eszik és lassítja az attention-t; lépésenként eltérő méret (plan: nagy, per-fájl gen: kisebb). [FORGE]
- **Spekulatív dekódolás:** kis draft modell 1.8-2.5x gyorsulást ad, mert "a kód nagyon kiszámítható" — llama.cpp-server alatt érdemes tesztelni.
- **Teljesítménycélok:** min. 30 tok/s generálás, <500 ms time-to-first-token — mérőszámként építsd be a Forge dashboardba. [FORGE]
- **Flash attention + teljes GPU-offload** (`-fa`, `-ngl 99`) alapbeállításként.
- **Prompt-prefix stabilitás a cache-újrahasznosításhoz:** a statikus részek (lessons fájl, rendszerprompt) a prompt ELEJÉN, a változó rész a végén — így a KV-cache prefix újrahasznosul kérések között. [FORGE]

## 7. Making AGENTS.md the Source of Truth (zenn.dev/haboshi)

- **Egy mesterfájl, importtal:** közös szabályok egyetlen AGENTS.md-ben, tool-specifikus fájlok `@AGENTS.md`-t importálnak — "amint duplikálsz, megindul a drift". A Forge lessons fájl legyen az egyetlen igazságforrás, minden pipeline-lépés ugyanazt injektálja. [FORGE]
- **Szöveges szabály ≠ kikényszerítés:** amit lehet, konfiggal/permission-nel tilts (pl. .env olvasás fájlrendszer-szinten), ne csak prompt-szövegben — kis modellnél különösen, mert gyengébben követi a tiltást. [FORGE]
- **Minimális, akció-orientált szabályok:** parancsok, standardok, tiltások, "kész" kritériumok — semmi filozófia. A lessons fájl bejegyzései legyenek 1-2 soros, végrehajtható utasítások. [FORGE]
- **Hiba-kategorizálás javításnál:** ismétlődő hiba → közös szabályfájlba; tool-specifikus rés → tool-fájlba; permission-sértés → konfigba, ne szöveges figyelmeztetésbe. [FORGE]
- **Drift-detektálás mechanikusan:** CI-check fogja a széttartó szabálymásolatokat — vagy inkább eleve egyetlen fájlba konszolidálj.

*(Mind a 7 forrás elérhető volt, nem volt sikertelen letöltés.)*

## Köteg D — Hardver, lint-korlátok, TDD (explainx, deployhq, code.claude, factory.ai, bonaroo, vchalyi, failingfast, joshrising)

## 1. eval-harness — AI Agent Testing Framework (explainx.ai)

- **Evalok definiálása implementáció ELŐTT**: a pass/fail kritériumokat a kódírás előtt rögzítsd — a brief→plan fázisban a plan tartalmazza a sikerkritériumokat. [FORGE]
- **Három grader-típus**: determinisztikus kód-alapú check (grep/exit code), modell-mint-bíró rubrika, emberi review csak kritikus döntésekhez. A Forge HTTP QA fázisa kód-alapú graderként formalizálható. [FORGE]
- **pass@k metrikák**: mérd a sikerarányt újrapróbálkozások között (pass@1, pass@3); regressziós eval cél: pass^3 = 100%. A compile-fix loop iterációszámának logolása ennek nyers adata. [FORGE]
- **Capability vs. regression eval szétválasztás**: új funkció tesztje ≠ meglévő funkciók védelme; a generált tesztek két kategóriába sorolandók.
- **Eval-artefaktumok verziózva a repóban** (pl. `.claude/evals/feature.md` mintára): a QA-definíciók legyenek first-class fájlok a projektben, ne csak futásidejű promptok. [FORGE]
- **Anti-pattern**: ne illeszd túl a promptot ismert eval-példákra, és ne csak happy-path-t mérj; a flaky grader release-gate-ben tilos.

## 2. Local AI Coding Models Hardware Guide (deployhq.com)

- **Ollama alapértelmezett kontextusa 2k és csendben csonkol** — explicit `OLLAMA_CONTEXT_LENGTH=32768` (vagy `num_ctx` per-request) beállítás kötelező, különben a pipeline promptok vége levágódik. [FORGE]
- **Kvantálás > paraméterszám**: 4-bit kvant fele annyi VRAM-ot eszik, mint a 8-bit — 16GB-on a 14B Q4 is befér, érdemes tier-elni a modelleket fázisonként. [FORGE]
- **Modelfile verziózás Gitben**: temperature, kontextushossz, system prompt a kóddal együtt verziózva — a Forge pipeline-konfig így reprodukálható. [FORGE]
- **Modellszűrési kritérium**: min. 32k kontextus + tool-calling támogatás (Qwen Coder, DeepSeek-Coder variánsok).
- **Feladat-eszkaláció**: rutin refaktor/boilerplate → lokális modell; greenfield, többfájlos érvelés → erősebb modell. A Forge-ban a plan fázis kaphat nagyobb/okosabb modellt, a per-file generálás kisebbet. [FORGE]
- **Konfiguráció IaC-ként terítve**, ne kézi gépbeállításként — drift ellen.

## 3. Claude Code Best Practices (code.claude.com hivatalos doksi)

- **A kontextusablak a legfontosabb erőforrás**: a teljesítmény degradálódik, ahogy telik — kis modellnél ez hatványozottan igaz; minden pipeline-fázis kapjon friss, minimális kontextust, ne kumulatív transcriptet. [FORGE]
- **Adj futtatható verifikációt**: teszt, build exit code, linter, fixture-diff — e nélkül a "kész"-nek tűnés az egyetlen jel. A Forge lint-gate + compile-fix + HTTP QA pontosan ez; a kulcs: a check kimenete kerüljön vissza a modell promptjába iterációhoz. [FORGE]
- **Determinisztikus stop-gate korláttal**: a Stop-hook minta 8 egymást követő blokkolás után felad — a Forge fix-loopjának is legyen kemény iterációs plafonja (pl. 5-8), utána eszkaláció/abort. [FORGE]
- **Explore → Plan → Implement → Commit** fázisszétválasztás; kis scope-ú fixnél a plan fázis átugorható ("ha egy mondatban leírható a diff, hagyd ki a tervet"). [FORGE]
- **CLAUDE.md/lessons-fájl könyörtelen kurtítása**: "ha ez a sor kiesne, hibázna a modell?" — ha nem, töröld; a túl hosszú instrukciófájl miatt a modell a fontos szabályokat is ignorálja. A Forge lessons-fájljára kritikus: max. rövid, csak hibamegelőző szabályok. [FORGE]
- **2 sikertelen korrekció után**: töröld a kontextust és írj jobb kezdőpromptot a tanultakkal — a fix-loopban N kudarc után friss kontextusú újragenerálás jobb, mint további foltozás. [FORGE]
- **Writer/Reviewer minta friss kontextussal**: külön (friss kontextusú) hívás reviewolja a diffet — a saját kódját generáló modell elfogult; olcsó plusz fázis a Forge-ban. [FORGE]
- **Bizonyíték, ne állítás**: a modell mutassa a tesztkimenetet/parancseredményt, ne csak mondja, hogy sikerült — a QA-jelentés tartalmazza a nyers outputot. [FORGE]
- **Adversarial reviewer figyelmeztetés**: a hibakeresésre kért reviewer akkor is talál "hibát", ha nincs — csak korrektséget érintő findingokat fogadj el, a stílusjavaslatokat dobd el.

## 4. Aider — Using files and repos (opendeep.wiki)

(nem elérhető — HTTP 404)

## 5. Using Linters to Direct Agents (factory.ai)

- **Lint-szabály = végrehajtható spec**: a kódolási konvenciókat lint-szabályként kódold, így az agent "self-heal until clean" emberi beavatkozás nélkül — a Forge lint-gate-je bővíthető projekt-specifikus custom szabályokkal. [FORGE]
- **Hét lint-kategória agenteknek**: grep-elhetőség (named exports), kiszámítható fájlstruktúra, architektúra-határok, security, tesztelhetőség, observability, dokumentáció.
- **Determinisztikus kódelhelyezés**: abszolút importok, named exportok, kiszámítható fájlnevek (`types.ts`, `enums.ts`) — a plan fázis adjon kötött fájlnév-konvenciót, hogy a párhuzamos per-file generálás ne ütközzön. [FORGE]
- **AGENTS.md + lint páros**: az ember-olvasható "miért" a doksiban, a gépi "mit" a lint-szabályban — a Forge lessons-fájl mellé determinisztikus lint-megfelelő. [FORGE]
- **Hot-path enforcement**: a lint fusson minden ponton (generálás után, commit előtt, CI-ben) — a Forge-ban a lint-gate eredménye azonnal menjen vissza a generáló promptba. [FORGE]
- **Migráció lintként**: deprecated minta = failing rule; az agent batch-fixeli a sérelmeket — kész recept Forge-os tömeges refaktorra.

## 6. Enforced AI Test-Driven Development (bonaroo.nl)

- **Izolált modul-tesztelés**: minden agent csak a saját kódját debuggolja, a függőségek determinisztikusan mockolva — így a hiba egyértelműen az agent kódjára mutat, nem külső zajra. A párhuzamos per-file generálásnál a fájlok közti függőséget interfész-stubokkal érdemes elvágni. [FORGE]
- **Primitív be/kimenetű, funkcionális stílusú modulok**: kis modell nem tud bonyolult objektumgráfot konzisztensen kezelni — a plan fázis írjon elő primitív-alapú szignatúrákat. [FORGE]
- **Deklarált függőségek kikényszerítése**: az agent fizikailag ne férjen hozzá nem-deklarált modulokhoz — a Forge generálási promptja csak a plan-ben deklarált fájlokat/API-kat kapja meg. [FORGE]
- **TDD-gate eszközszinten** (`setFunctionAndTest` minta): a teszt először FAIL-eljen, aztán jön a kód, aztán minden teszt PASS — eszköz kényszeríti ki, nem prompt. A Forge generated-tests fázisa megfordítható: teszt előbb, kód utána. [FORGE]
- **Séma-validáció a modulhatárokon**: input/output sémaellenőrzés azonnal elkapja az interfész-eltérést — párhuzamos generálásnál ez fogja meg a fájlok közti inkonzisztenciát. [FORGE]
- **Külön agent az integrációs tesztre** valódi modulokkal (csak infra mockolva) — megakadályozza a unit-teszt "csalást".
- **Teljes kezdő kontextus**: app-struktúra + stílusguide + doksik az első promptban csökkenti a hallucinációt — de kis modellnél a kontextusbüdzsével egyensúlyozva.

## 7. Claude Code Best Practices (vchalyi.com)

- **Perzisztens memória manuális szerkesztés helyett**: a felfedezett mintákat/architektúra-tudást a rendszer maga írja memóriafájlba — a Forge lessons-fájlja automatikusan bővüljön a fix-loop tanulságaiból. [FORGE]
- **Workflow-láncolás egy parancsba**: lint→test→build→commit egyetlen automatizált lánc, hibakezeléssel — a Forge pipeline-filozófia megerősítése.
- **PRD/spec a kódolás előtt**: a modell előbb írjon követelménydoksit, azt reviewold, csak utána implementáljon — a brief→plan fázisban emberi jóváhagyási pont. [FORGE]
- **MCP/tool-regisztráció kontextust eszik**: minden bekötött eszköz tokeneket fogyaszt — kis modellnél a promptba csak az adott fázishoz kellő eszköz/instrukció kerüljön. [FORGE]
- **Teszt-módosítási tilalom**: "soha ne módosítsd a tesztet, hogy átmenjen" — explicit szabály a lessons-fájlba; a fix-loop diffelje, hogy a modell nem nyúlt-e a teszthez. [FORGE]

## 8. Local AI Models for Coding 2026 (failingfast.io)

- **Hardver-modell párosítás**: 16GB VRAM-ra a Qwen 2.5 Coder 14B Q4 (~9GB) az édes pont — az RTX 5060 Ti 16GB-on a 7B helyett a 14B Q4 is futtatható, érdemes benchmarkolni a Forge pipeline-ban. [FORGE]
- **Q4/Q5 kvantálás**: fele VRAM minimális minőségvesztéssel — FP16-ot lokálban felejtsd el.
- **A rés a multi-concern feladatoknál nyílik**: lokális modell egy-szempontú feladatban jó, több egyidejű szempontnál bukik — a Forge per-file, egy-felelősségű feladatbontása pont ezt kerüli meg; a plan darabolja a feladatot egyetlen-concern egységekre. [FORGE]
- **Reasoning vs. instruct modell**: R1-típus lassú, bőbeszédű — komplex debughoz; instruct (qwen2.5-coder) gyors, direkt — generáláshoz. A compile-fix loop makacs hibáinál érdemes reasoning modellre váltani. [FORGE]
- **Lokális erőssége a nagy volumen**: nulla per-task költség — a párhuzamos per-file generálás és a sok iterációs fix-loop pont a lokális modell komparatív előnye.
- **Supply-chain kockázat**: a lokális modell kimenetét ugyanúgy reviewold, mint nem megbízható külső kódot — a lint+compile+QA gate-ek biztonsági funkciót is ellátnak.
- **Hibrid megközelítés**: architektúra/többfájlos refaktor → felhő/erősebb modell; rutinmunka → lokális.

## 9. I Built a Local AI Coding Assistant on Consumer Hardware (joshrising.com)

- **Kontextus-VRAM csere nem lineáris**: 9B kvantált modell 87k kontextussal ~12GB; a kontextushossz a legdrágább állítható paraméter — a Forge állítson fázisonként eltérő `num_ctx`-et (plan: nagy, per-file gen: kicsi). [FORGE]
- **Ollama szerver-workflow-ban törékeny**: frissítések felülírták a service-konfigot — a Forge pin-elje az Ollama verziót és tartson konfig-backupot, vagy hosszú távon llama.cpp + saját wrapper. [FORGE]
- **Spec-dokumentum veri a vágópromptot**: "a constraint-doksi nélkül a modell dönt" — Requirements.md + Testing Strategy.md jellegű kötött dokumentumok a plan fázis kimeneteként. [FORGE]
- **Munkamegosztás képesség szerint**: erős modell írja a specet/promptokat, a lokális modell implementál — a lokális modellek a saját promptjaik megírásában gyengék.
- **Dokumentum-alapú memória**: Plans/Updates/Testing markdown fájlok hidalják át a kontextuslimitet sessionök között — a Forge lessons-fájl mintájának validációja. [FORGE]
- **Sampling-paraméterek hangolása loopok ellen**: temperature 0.7, repeat-penalty 1.2, presence-penalty 0.2 állította meg a logikai ismétlődést egy 9B modellnél — a Forge Ollama-hívásaiban explicit repeat/presence penalty beállítás. [FORGE]
- **Alulspecifikált tesztinstrukciók exponenciális fájlduplikációt okoztak** — a tesztgeneráló prompt sorolja fel, mely fájlok léteznek és mely minták tiltottak. [FORGE]
- **Struktúra > modellméret**: 6.47GB-os kvantált modell is megold komplex feladatot, ha a feladat jól darabolt és dokumentált — a Forge pipeline-filozófia független megerősítése.

### [HIBA-F011] Kereszt-fájl szerződés-hibák párhuzamos generálásnál (a 26-fájlos Homemeter build bukásának oka)
- **Dátum**: 2026-06-12
- **Fájl**: src/routes/* ↔ src/services/* (TS1192 default-export hiánya, TS2339 nem létező service-függvény hívása, TS2552 nem importált típus, TS2307 rossz relatív útvonal-mélység)
- **Hibaüzenet**: `Property 'getStockLevels' does not exist on type 'typeof import(.../inventoryService)'` és társai — 22 hiba, mind szerződés-eltérés
- **Gyökérok**: a route-ok és service-ek UGYANABBAN a párhuzamos hullámban készültek, egyik sem látta a másik valódi API-ját — mindkettő kitalálta a sajátját. 26 fájlnál a kontextus-budget sem fért hozzá a service-fájlokhoz.
- **Javítás**: (1) RÉTEG-SORRENDŰ hullámok: models→db→services→middleware→routes→server — párhuzam csak rétegen belül; (2) SZIGNATÚRA-KONTEXTUS: az importáló réteg a kész exportáló réteg export-szignatúráit kapja ("KIZÁRÓLAG ezeket a neveket hívd"); (3) a javító a hibában hivatkozott modul TÉNYLEGES exportjait is megkapja; (4) javítási sorrend réteg-rendű (exportáló előbb).
- **Megelőzés**: több-fájlos generálásnál a modulhatár-szerződés az első: az importáló SOHA ne készüljön az exportáló kész szignatúrái nélkül. Párhuzamosítani csak azonos rétegen belül szabad.

### [HIBA-F012] MySQL-szintaxis SQLite-sémában — a szerver indulásnál hal meg
- **Dátum**: 2026-06-12
- **Fájl**: src/db/db.ts (beágyazott séma) + schema.sql
- **Hibaüzenet**: `Error: near "+": syntax error` a db.exec-nél induláskor
- **Gyökérok**: a modell MySQL-idiómát írt: `DEFAULT CURRENT_TIMESTAMP + INTERVAL 24 HOUR` — az SQLite-ban nincs INTERVAL
- **Javítás**: `DEFAULT (datetime('now', '+1 day'))`
- **Megelőzés**: SQLite DEFAULT-ban kifejezés csak zárójelben; dátum-aritmetika KIZÁRÓLAG datetime('now', '+N day') formával. INTERVAL/NOW()/AUTO_INCREMENT MySQL-izmusok — SQLite-ban TILOS.

### [HIBA-F013] Destruktív QA-ellenőrzések szennyezik a perzisztens adatbázist
- **Dátum**: 2026-06-12
- **Hibaüzenet**: GET/PUT/DELETE /api/x/1 → 404 "not found" a 2. futástól (az 1. futás DELETE-je törölte a seed-rekordot)
- **Gyökérok**: a QA-suite fix id-kre (id=1) megy, miközben a DB futások közt megmarad
- **Javítás**: a pipeline minden lánc-futás elején törli a saját scratch data/app.db-t (friss seed-állapot); plusz "legjobb-állapot őr": ha egy javítókör RONT a QA-n, fájl-visszaállítás és leállás a legjobb állapotban
- **Megelőzés**: QA fix id-vel csak friss seed-állapoton; éles DB-hez destruktív ellenőrzés TILOS.

### [HIBA-F014] SQLite DEFAULT érték sérti a saját CHECK-listáját
- **Dátum**: 2026-06-12
- **Hibaüzenet**: `CHECK constraint failed: status IN (...)` POST-nál
- **Gyökérok**: a generált séma: `DEFAULT 'open' CHECK(status IN ('draft','assigned',...))` — az 'open' nincs a listában; ráadásul a CREATE TABLE IF NOT EXISTS a RÉGI táblát nem frissíti, így a kód és a perzisztens séma széttarthat
- **Javítás**: DEFAULT mindig a CHECK-lista eleme legyen; séma-változásnál a scratch-db törlése
- **Megelőzés**: CHECK-listás oszlopnál a DEFAULT-ot a listából válaszd; a kódban használt státusz-értékek a CHECK-listából jöjjenek.

### [HIBA-F015] Async adatbetöltés await nélkül — a render üres state-tel fut le
- **Dátum**: 2026-06-12
- **Fájl**: public/app.js (Homemeter — "üres fekete oldal" 1. ok)
- **Hibaüzenet**: nincs hiba — a felület üres, "Monitoring 0 Assets"
- **Gyökérok**: `loadData()` async, de await nélkül hívva, a `renderDashboard()` az adat megérkezése ELŐTT fut; az időzített frissítés csak adatot tölt, nem renderel újra
- **Javítás**: a loadData a state-frissítés UTÁN maga hívja a renderelést
- **Megelőzés**: SPA-ban a render a fetch .then/await UTÁN fusson; minden adat-frissítés után kötelező újra-render.

### [HIBA-F016] HTML-sztring appendChild-nak adva
- **Dátum**: 2026-06-12
- **Fájl**: public/app.js (Homemeter — "üres fekete oldal" 2. ok)
- **Hibaüzenet**: `TypeError: Failed to execute 'appendChild' on 'Node': parameter 1 is not of type 'Node'`
- **Gyökérok**: a kártya-generátor template-literál SZTRINGET ad vissza, a kód Node-ként appendeli
- **Javítás**: htmlToNode helper (template elem) vagy insertAdjacentHTML
- **Megelőzés**: sztring-építésnél insertAdjacentHTML/innerHTML; appendChild-hoz KIZÁRÓLAG createElement/template.content.

### [HIBA-F017] index.html ↔ app.js DOM-id szerződés-eltérés
- **Dátum**: 2026-06-12
- **Fájl**: public/index.html + app.js (Homemeter — "üres fekete oldal" 3. ok)
- **Hibaüzenet**: `DOM elements not found for rendering.` (console.warn) — a látogató üres oldalt lát
- **Gyökérok**: az index.html csak üres `#app`-ot adott, az app.js viszont #asset-grid/#system-status/#work-order-list elemekbe renderelt — a két fájl külön hullámban készült, nem látta egymás szerződését
- **Javítás**: a hiányzó id-jú elemek pótlása az index.html-ben; motorszinten: DOM-SZERZŐDÉS KAPU (determinisztikus — az app.js által keresett id-k kötelezően léteznek a HTML-ben, különben HTML-újragenerálás)
- **Megelőzés**: a frontend-pár (html+js) közös id-szerződéssel készüljön; a Forge kapuja ezt automatikusan kikényszeríti.

### [HIBA-F018] Dupla út-prefix: a route-fájl teljes utat definiál, a server prefix alá mountolja
- **Dátum**: 2026-06-12
- **Fájl**: src/routes/apiStats.ts + server.ts (Intelligent household)
- **Hibaüzenet**: működő végpont: `/api/stats/api/stats`; minden értelmes út 404
- **Gyökérok**: a route-fájl `router.get('/api/stats')`-ot definiál, miközben a server `app.use('/api/stats', router)`-rel mountolja — az utak összeadódnak
- **Javítás**: a route-fájl belső útjai RELATÍVAK ('/' , '/forecasts'), a prefixet KIZÁRÓLAG a mount adja
- **Megelőzés**: route-fájlban SOHA ne ismételd a mount-prefixet; egy út-szegmens pontosan egy helyen (mount VAGY route) jelenik meg.

### [HIBA-F019] POST-végpont 400-zal utasít el üres törzset, pedig az app saját adataiból ki tudná szolgálni
- **Dátum**: 2026-06-12
- **Fájl**: src/routes/apiScenarios.ts (Intelligent household)
- **Hibaüzenet**: `POST /api/scenarios -> 400 {"error":"Missing required fields"}`
- **Gyökérok**: a route kötelezővé tette az összes bemenetet, miközben az adatok a DB-ben rendelkezésre állnak
- **Javítás**: hiányzó bemenetnél a tárolt adatok az alapértelmezés (graceful default), explicit body továbbra is tisztelt
- **Megelőzés**: szimulációs/számító végpontnál a bemenet OPCIONÁLIS legyen, ha az app maga is elő tudja állítani — 400 csak akkor, ha tényleg értelmezhetetlen a kérés.

### [HIBA-F020] Aggregát-oszlop alias nélkül olvasva — a seed/ellenőrzés némán nem fut le
- **Dátum**: 2026-06-12
- **Fájl**: src/db/db.ts (Intelligent household — "undefined kWh" a felületen)
- **Hibaüzenet**: nincs hiba — a `SELECT COUNT(*)` eredményének kulcsa `COUNT(*)`, a kód `.COUNT`-ot olvas → undefined === 0 sosem igaz → a seed sosem fut, a felület undefined-okat mutat
- **Javítás**: `SELECT COUNT(*) AS cnt` + `.cnt`
- **Megelőzés**: aggregát lekérdezésnél MINDIG alias (`COUNT(*) AS cnt`, `AVG(x) AS avg_x`) és az aliast olvasd.

### [HIBA-F021] Külső könyvtár (Chart.js) használata betöltés nélkül + frontend↔API alak-eltérés
- **Dátum**: 2026-06-12
- **Fájl**: public/index.html + app.js + src/routes/apiStats.ts (Intelligent household — "undefined kWh")
- **Hibaüzenet**: `ReferenceError: Chart is not defined` + a felület undefined-okat mutat
- **Gyökérok**: (1) az app.js `new Chart(...)`-ot hív, de az index.html nem tölti be a Chart.js-t; (2) a frontend összesítő-objektumot várt ({totalConsumption}), az API nyers sorokat adott; (3) rossz végpont-út (/api/cost vs /api/cost/forecasts)
- **Javítás**: CDN-script a html-be; az API értelmes összesítőt ad; az út javítva
- **Megelőzés**: külső lib CSAK betöltött <script>-tel (vagy window.Chart guard); a frontend-fetch alakja = az API tényleges válasz-alakja (a generáláskor a végpont-lista MELLÉ a válasz-alak is kerüljön a promptba).

### [HIBA-F022] Párhuzamos állapot-mentés versenyhelyzete + néma újratervezés folytatáskor
- **Dátum**: 2026-06-12
- **Fájl**: agent-core.js (saveState + resume-ág)
- **Hibaüzenet**: a resume "Nincs folytatható mentett állapot" → ÚJRATERVEZETT más fájlnevekkel → felülírta a working projektet
- **Gyökérok**: (1) a párhuzamos generáló-hullámok egyszerre írták a build_state.json-t → sérült JSON; (2) a resume érvénytelen állapotnál csendben friss tervezésre esett vissza
- **Javítás**: sorosított (mutex-láncolt) + atomi (tmp+rename) állapot-mentés; folytatásnál érvénytelen állapot = HIBA és STOP, soha nem újratervezés
- **Megelőzés**: megosztott állapot-fájlt párhuzamos ágakból CSAK sorosítva+atomian írj; a "folytatás" művelet SOHA nem degradálódhat "új projekt"-té.

### [HIBA-F023] SQLite-paraméter tömbként átadva (.all([x])) — 9 körön át reprodukált makacs hiba
- **Dátum**: 2026-06-12
- **Fájl**: src/services/energy_consumption_service.ts
- **Hibaüzenet**: `TS2769: No overload matches this call` a stmt.all([user_id]) hívásnál
- **Gyökérok**: a node:sqlite a paramétereket EGYENKÉNT várja; a 7B következetesen tömb-formát ír, és újragenerálásnál is reprodukálja
- **Javítás**: determinisztikus lint-szabály (.all([ / .get([ / .run([ minta → írás előtt elutasítva) + makacs hibánál (3+ kör ugyanarra a fájlra) MULTI-LLM eszkaláció az erősebb érvelő modellre
- **Megelőzés**: node:sqlite paraméterek: .all(x), .get(a, b), .run(...values) — tömb-burkolás TILOS; a lint-kapu fogja.

### [HIBA-FORGE-mqb7jpju] új fordítási hibaosztály (node-ts)
- **Dátum**: 2026-06-12
- **Fájl**: src/services/energy_consumption_service.ts
- **Hibaüzenet**: src/services/energy_consumption_service.ts(6,12): error TS2352: Conversion of type 'Record<string, SQLOutputValue>[]' to type 'EnergyConsumption[]' may be a mistake because neither type sufficiently overlaps with the other. If this was intentional, convert the expression to 'unknown' first.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqb7jpjx] új fordítási hibaosztály (node-ts)
- **Dátum**: 2026-06-12
- **Fájl**: -
- **Hibaüzenet**: Overload 1 of 2, '(...anonymousParameters: SQLInputValue[]): Record<string, SQLOutputValue>[]', gave the following error.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqb897i4] új fordítási hibaosztály (node-ts)
- **Dátum**: 2026-06-12
- **Fájl**: src/routes/solar_production_routes.ts
- **Hibaüzenet**: src/routes/solar_production_routes.ts(7,21): error TS2352: Conversion of type 'string | ParsedQs | (string | ParsedQs)[]' to type 'number' may be a mistake because neither type sufficiently overlaps with the other. If this was intentional, convert the expression to 'unknown' first.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqb94qy7] új fordítási hibaosztály (node-ts)
- **Dátum**: 2026-06-12
- **Fájl**: src/routes/energy_consumption_routes.ts
- **Hibaüzenet**: src/routes/energy_consumption_routes.ts(7,21): error TS2352: Conversion of type 'string | ParsedQs | (string | ParsedQs)[]' to type 'number' may be a mistake because neither type sufficiently overlaps with the other. If this was intentional, convert the expression to 'unknown' first.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqb94qy9] új fordítási hibaosztály (node-ts)
- **Dátum**: 2026-06-12
- **Fájl**: src/routes/solar_production_routes.ts
- **Hibaüzenet**: src/routes/solar_production_routes.ts(7,21): error TS2352: Conversion of type 'string | ParsedQs | (string | ParsedQs)[]' to type 'number' may be a mistake because neither type sufficiently overlaps with the other. If this was intentional, convert the expression to 'unknown' first.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-F024] A 7B-plafon: szabály-ellenálló minták (kötelező query-param, Vue-fixáció) regenerálási kockadobással
- **Dátum**: 2026-06-12
- **Fájl**: Intelligent household routes/frontend — több cikluson át
- **Hibaüzenet**: a route a hint explicit tiltása ellenére is kötelező user_id-t követel; a frontend Vue-ra fixált a vanilla-szabály ellenére
- **Gyökérok**: a qwen2.5-coder:7b bizonyos betanult mintákat a prompt-szabályokkal szemben is reprodukál — minden újrageneráció kockadobás; a determinisztikus kapuk fogják a hibát, de jó változatot a modellnek kell adnia
- **Javítás**: modell-eszkaláció a Model-Ops rétegen: qwen2.5-coder:14b (Q4, ~9GB — belefér a 16GB VRAM-ba) a kódoló/javító szerepre; a kutatás ("local models 2026") ajánlása pontosan ez volt
- **Megelőzés**: ha egy hibaosztály 2+ regenerálási cikluson át visszatér a szabály ellenére, NE további szabályt írj — válts erősebb modellre az adott szerepben (ez a multi-LLM doktrína értelme).

--

## 🔴 KATEGÓRIA: Frontend-generálási kritikus hibák (2026-06-12)

### [HIBA-FRONTEND-001] Frontend-pár (HTML+JS) párhuzamos generálása szerződés-eltérésre esik

**Dátum**: 2026-06-12 (v0.5.0)

**Fájlok**: `public/index.html` + `public/app.js`

**Hibaüzenet**:
```
FRONTEND-TARTALOM KAPU: a főoldal HTML-je üres/tartalmatlan 
| nem használja a design-alap vázszerkezetét (app-header + app-main kötelező) 
| a frontend JS nem hív /api/ végpontot (nem jelenít meg adatot)
```

**Gyökérok**:
- A HTML és JS párhuzamosan készült (réteg 7. lépésben).
- Mindkettő **függetlenül** találgatta meg az API-végpont-neveket, az elem-id-kat és a renderelési struktúrát.
- Az HTML nem tartalmazott `app-header`, `app-main` id-kat, a JS pedig az ezeket használó renderelési kódot.
- Sem az egyik sem látta a másik szerződését.

**Tünet**:
- A webapp fekete képernyő.
- Konzolban: `Cannot read properties of null (reading 'appendChild')` vagy `getElementById(...) is null`.
- Függőségi furcsamajátékra esik: mindkettő újragenerálódik, de továbbra sem kommunikálnak.

**Javítás**:
1. Az HTML és JS **SOHA nem párhuzamos** — az HTML előbb készül (statikus struktúra definiálása), majd a JS abban hivatkozik.
2. Az HTML-ből exportált **DOM-szerződés fájl** (`dom-contract.json`) tartalmazza az összes kötelező id-kat, class-okat, és az alapvázszerkezetet.
3. A JS-generáló prompt **kötött** ezt a szerződés-fájlt kapja meg: `"EZEK az id-k garantáltan léteznek, CSAK EZEKET használd"`.
4. A kapu ellenőrzi:
   - (a) az HTML tartalmazza az összes szerződés-id-ot
   - (b) a JS csak ezekre hivatkozik
   - (c) az API-hívások megfelelnek a tényleges végpont-neveknek

**Megelőzés**:

**Réteg-szintű szétválasztás**: A frontend HTML és JS **nem azonos párhuzamosítási szintben** lehet.
- Sorrend: `HTML (rang 7) ⊃ JS (rang 8)` — szigorú függőség.

**DOM-szerződés file**:
- Az előkészítő lépés legyen: `DESIGN-PHASE` → HTML generálás → DOM-szerződés export → JS generálás.

**Kötött prompt-injektálás**:
- A JS prompt tartalmazzon konkrét id-listaként:
  ```
  "A HTML-ben szükséges id-k: app-header, app-main, energy-grid, solar-grid, status. 
  Ezeken kívül TILOS DOM-elemeket keresni."
  ```

**Kapu-lánc**:
1. HTML-szintaxis + DOM-id-jelenlét
2. JS-szintaxis
3. API-hívás validálás a tényleges backend-végpont-lista ellen (HTTP-QA)
4. Frontend-elindítás + screenshot/network-trace

---

### [HIBA-FRONTEND-002] API-végpont szinkronizáció hiánya — frontend és backend dupla /api prefix

**Dátum**: 2026-06-12 (v0.5.0) — session_log sor 17:36

**Fájl**: `public/app.js` (frontend hívás) ↔ `src/server.ts` (mount pont)

**Hibaüzenet**:
```
LINT: ⛔ lint-kapu (public/app.js): NEM LÉTEZŐ API-végpontot hívsz: /api/energy-consumption 
— a backend KIZÁRÓLAG ezeket adja: /api/api/energy-consumption, /api/api/solar-production
```

**Gyökérok**:
- A route-fájl (`src/routes/apiStats.ts`) definiálta: `router.get('/api/energy-consumption')`
- A server mountot csinált: `app.use('/api/stats', router)` — **PATH-DUPLIKÁCIÓ!**
- A frontend a logikus `/api/energy-consumption` útvonalra hivatkozott.
- **Tényleges URL lett**: `/api/stats/api/energy-consumption` (dupla /api prefix)

**Javítás**:
- A route-fájlban a belső utak **RELATÍVAK**: `router.get('/energy-consumption')`
- Az mount adja az `/api` prefixet: `app.use('/api', router)`
- Így a tényleges URL: `/api/energy-consumption` (helyes)

**Megelőzés**:

**Kapu a route-fájlokra**:
- Ellenőrizd, hogy a route definíciók **NEM** tartalmazzák a mount-prefix-et.
- Regex: `/router\.(get|post|put|delete)\s*\(\s*['"]\/api\//` — újragenerálás.

**Tényleges API-lista kapu**:
- A pipeline az összes route-fájl alapján **tényleges url-listát** buildel (`/api/energy-consumption`).
- Ezt injektálja a frontend-prompt-ba:
  ```
  "A backend ezeket a végpontokat adja: 
  - GET /api/energy-consumption (params: user_id)
  - GET /api/solar-production (params: user_id)
  [...]"
  ```

**Auto-fix**:
- A dupla /api prefix automatikusan levágható (pl. `route.get('/api/x')` → `route.get('/x')` lint-auto-fix)

**HTTP-QA**:
- Az összes frontend API-hívás tényleges HTTP-get előtt ellenőrizve van: 2xx/404 válasz vs. 400/5xx.

---

### [HIBA-FRONTEND-003] JS szintaktikai hibák (unclosed braces, unexpected identifier)

**Dátum**: 2026-06-12 (v0.5.0)

**Fájl**: `public/app.js`

**Hibaüzenet**:
```
LINT: ⚠ frontend-pár maradék jelzésekkel írva: 
JS szintaktikai hiba: Unexpected end of input
JS szintaktikai hiba: Unexpected identifier 'renderData'
```

**Gyökérok**:
- A 7B kódoló modell hosszú JS-fájlban lezáratlan `{` vagy `}` kapcsos zárójelet hagyott.
- Vagy félbehagyott egy függvénydefiníciót a fájl vége felé.
- Ez a **determinisztikus JavaScript parser** (nem interpreter) az `node -c` paranccsal azonnal detektálja.

**Javítás**:
- Az auto kapu futtatja `node -c public/app.js`-t minden generálás után.
- Szintaktikai hiba esetén a fájl **újragenerálódik** friss kontextussal (előzmények törlésével).

**Megelőzés**:

**JS-szintaxis kapu**: 
- Minden frontend JS `npm run build` vagy `node -c` előtt **KÖTELEZŐ**.

**Apróbb korlátok a JS-promptban**:
```
"Egy függvény max 40 sor, max 3 szint beágyazás, 
függvényt `function foo() {...}` vagy `const foo = () => {...}` 
formában írj. Minden { zárójel után gondoskodj a } lezárásáról."
```

**JSHint vagy ESLint**:
- A kapu futtathat `npx eslint public/app.js` parancsot is (ha .eslintrc megvan).

**Token-limit**: 
- A prompt végén ismételd meg a kulcs-szabályokat: 
  - "Zárójel-egyensúly: minden `{` után kell `}`"
  - "Függvényt `function` vagy `const` forma"

---

### [HIBA-FRONTEND-004] Framework-szintaxis vanilla-JS helyett (Vue/React import/export)

**Dátum**: 2026-06-12 (v0.5.0) — session_log sor 17:40, 17:41

**Fájl**: `public/app.js`

**Hibaüzenet**:
```
LINT: ⛔ lint-kapu (public/app.js): 
böngésző-script modul-szintaxissal (import/export) — 
vanilla JS kötelező, framework (Vue/React) TILOS
```

**Gyökérok**:
- A 7B modell (qwen2.5-coder) betanított módban Vue/React-es kódot írhatott.
- Még akkor is, ha a prompt explicit tilotta.
- A modell a betanított kód-gyakoriságban következtetett: 
  - `"frontend JS = import/export = Vue minta"`

**Tünet**:
- A böngésző: `Uncaught SyntaxError: Cannot use import statement outside a module`
- Vagy halott és nem működő interface.

**Javítás**:
1. A JavaScript Parser-kapu detektálja:
   ```regex
   import\s+.*\s+from
   export\s+(default\s+)?(function|const|class|{)
   ```
   Hibánál újragenerálás.

2. A prompt újra hangsúlyozza:
   ```
   "**VANILLA JAVASCRIPT KIZÁRÓLAG**: 
   - NO import
   - NO export
   - NO npm modules
   - Laikus fájl: <script> tag közt fut
   - Globális függvények, window.API = {}"
   ```

3. Opcionálisan: modellek közötti eszkaláció.
   - Ha 2x ugyanaz a hibaminta → nagyobb modellre váltás.

**Megelőzés**:

**Determinisztikus tilt**:
- A lint-gate regex-szel fogja ki az `import`/`export` szó-mintákat, még fordítás előtt.

**Lessons-fájl szócikk**:
```
"Böngészős vanilla JS nem használ import/export vagy npm. 
Ha külső lib kell, <script src=CDN> tag-gel."
```

**Mandatory szabály a promptban (3-4 soros, NAGY BETŰK)**:
```
"VANILLA JAVASCRIPT: 
- No import/export/require
- Egyetlen fájl, függvények globálisak
- Ha lib kell: <script src=CDN> tag
- HTTP hívás: fetch() API-val"
```

---

### [HIBA-FRONTEND-005] HTML üres vagy hiányzik a kötelező struktúra

**Dátum**: 2026-06-12 (v0.5.0)

**Fájl**: `public/index.html`

**Hibaüzenet**:
```
FRONTEND-TARTALOM KAPU: 
a főoldal HTML-je üres/tartalmatlan 
| nem használja a design-alap vázszerkezetét (app-header + app-main kötelező)
```

**Gyökérok**:
- Az HTML-generáló prompt "intelligens" szabadságot kapott az index.html megtervezésénél.
- De a 7B modellnél nem kellően specifikus a korlát.
- Az eredmény: egy teljesen üres `<html><head></head><body></body></html>`.
- Vagy hiányzott az `<div id="app-main">` vázszerkezet.

**Javítás**:

**HTML-sablon injektálása**:
- A prompt egy konkrét HTML-vázat kap, amiben csak a tartalom-részet (kártyák, diagramok) kell feltölteni:

```html
<!DOCTYPE html>
<html lang="hu">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Intelligent Household</title>
  <style>
    body { font-family: sans-serif; margin: 0; background: #f5f5f5; }
    #app-header { background: #333; color: #fff; padding: 1rem; }
    #app-main { max-width: 1200px; margin: 1rem auto; }
    .card { background: #fff; border-radius: 8px; padding: 1rem; margin-bottom: 1rem; }
  </style>
</head>
<body>
  <header id="app-header">
    <h1>Intelligent Household</h1>
  </header>
  <main id="app-main">
    <!-- IDE KERÜLNEK A KÁRTYÁK STB -->
  </main>
  <script src="app.js"></script>
</body>
</html>
```

**Kapu-ellenőrzés**:
- Az HTML tartalmaz-e:
  - `<title>` elem
  - `<meta charset="UTF-8">`
  - `<main id="app-main">` (vagy `<div id="app-main">`)
  - `<script src="app.js">` vagy `<script>...</script>` tag
  - Legalább 1 `<div id="...">` tartalom-elem
- Ha valamelyik hiányzik → újragenerálás.

**Eszkaláció**:
- Ha az első generálás üres vagy csonka → a prompt friss kontextussal **teljes HTML-t** kér (nem patchelés).

**Megelőzés**:

**Sablon-injektálás kötelező**:
- Soha ne `"írj HTML-t"` — mindig `"egészítsd ki ezt a sablont: [...]"`.

**Kapu-lista**:
- Min. 5 kötelező elem azonosítása az HTML-ben; hiányzik → újragenerálás.
- Ellenőrző szöveg: `grep -c '<main\|<div id="app-main"' public/index.html`

**Screenshot-validáció**:
- Ha van böngésző: a QA-fázis HTML-t betölti és screenshotot készít.
- Üres vagy hiányzó tartalom akkor nyilvánvaló.

---

### [HIBA-FRONTEND-006] API-válasz alak eltérése frontend-elvárástól

**Dátum**: 2026-06-12 (v0.5.0)

**Fájl**: `public/app.js` (fetch+JSON.parse) ↔ `src/routes/apiStats.ts` (végpont)

**Hibaüzenet**:
```
Uncaught TypeError: Cannot read property 'totalConsumption' of undefined
(frontend) 
vagy 
a felületen "undefined kWh" jelenik meg
```

**Gyökérok**:
- A frontend `response.data.totalConsumption` mezőt várt.
- Az API `{ consumption: [...], summary: {...} }` alakot adott.
- Az alakok **soha nem voltak szinkronizálva** — mindkettő tippelt.

**Javítás**:

**ÉLŐ API-mintavétel fázis** (session_log: 17:45, 17:47):
- A pipeline az összes route-fájl alapján **ténylegesen meghívja** a végpontokat.
- Az eredményt `API_RESPONSES.json` fájlba menti.

**JS-generáló prompt injektálása**:
- Konkrét JSON-válasz-mintákat kap:
  ```
  "A `/api/energy-consumption` végpont ezt adja vissza: 
  { 
    user_id: '...', 
    consumption: [100, 150, 200, ...], 
    timestamp: '2026-06-12T...'
  }
  Pont ebből az alakból olvasd a `consumption` tömböt."
  ```

**Kapu-ellenőrzés**:
- A fetch hívások JSON-parsálása valódi válaszok ellen tesztelve.
- Regex: `response\.(.+?)\.(.+?)` — ellenőrizni, hogy a JSON-path létezik-e a mintában.

**Megelőzés**:

**ÉLŐ mintavétel kapu**: 
- Minden frontend-generálás **UTÁN és ELŐTT**, mint a QA-lánc része.
- Az API-végponton teszthívás, az eredmény strukturális validálása.

**Válasz-alak-export**:
- `API_RESPONSES.json` a prompt-kontextusba:
  ```json
  {
    "GET /api/energy-consumption": {
      "sample": { "user_id": "1", "consumption": [...] },
      "required_fields": ["consumption"]
    }
  }
  ```

**Type-checking hint**:
- A promptban kódminta:
  ```javascript
  const data = response.json();
  if (!data.consumption) {
    throw new Error('API error: no consumption field');
  }
  ```

---

### [HIBA-FRONTEND-007] Globális elem-ID-szerződés hiánya

**Dátum**: 2026-06-12 (v0.5.0)

**Fájl**: `public/index.html` + `public/app.js`

**Hibaüzenet**:
```
LINT: ⚠ frontend-pár maradék jelzésekkel írva: 
a js olyan elem-id-kat használ, amik nincsenek a html-ben: status
```

**Gyökérok**:
- A JS `document.getElementById('status')` hívásokon keresztül elemeket keresett.
- Amelyek az HTML-ben nem voltak definiálva.
- Ez a **kereszt-fájl szerződés-hiba**.
- Futásidőben: `Cannot read properties of null`

**Javítás**:

**DOM-szerződés fájl** (`dom-contract.json`):
```json
{
  "required_ids": [
    "app-header",
    "app-main",
    "energy-grid",
    "solar-grid",
    "status"
  ],
  "required_classes": [
    "card",
    "card-title",
    "card-body"
  ],
  "root_element": "app-main"
}
```

**HTML-generáló prompt KÖTÖTT**:
```
"Az alábbi id-kat KÖTELEZŐEN tartalmazd: 
- app-header
- app-main
- energy-grid
- solar-grid
- status

Ezek nélkül a JS nem működik."
```

**JS-generáló prompt KÖTÖTT**:
```
"CSAK az alábbi id-kat használd a HTML-ből: 
- app-header
- app-main
- energy-grid
- solar-grid
- status

Más id-kat SOHA ne keress."
```

**Kapu**:
- HTML-ben hiányzó id → újragenerálás.
- JS olyan id-tat használ ami nem a listában → újragenerálás.

**Megelőzés**:

**DOM-szerződés generátor**:
- A design/plan fázisban egy tanulságfüggvény összeállítja az összes szükséges id-t a terv alapján.
- Exportálja `dom-contract.json`-ként.

**Prompt-injektálás**:
- Mind HTML, mind JS megkapja ezt a listát.

**Kapu-validáció**:
- HTML parser ellenőrzi, hogy az összes id létezik.
- Regex-scanner a JS-ben ellenőrzi: `getElementById\s*\(\s*['"]([^'"]+)` — az id-nak szerződésben kell lennie.

---

### [HIBA-FRONTEND-008] Async adatbetöltés feltöltés nélkül — üres képernyő

**Dátum**: 2026-06-12 (v0.5.0) — korábban már leírva [HIBA-F015]

**Fájl**: `public/app.js`

**Hibaüzenet**:
```
"Monitoring 0 Assets" 
vagy 
teljesen üres felület; konzolban sokszor nincs hiba
```

**Gyökérok**:
- Az adatbetöltés `loadData()` aszinkron.
- A renderelés pedig szinkron és **előtte** futt le.
- Az adat `undefined` marad.

**Javítás**:

Helyes minta:
```javascript
async function init() {
  await loadData();      // wait for data
  renderDashboard();     // render AFTER data loaded
}
init();
```

Vagy az egyszerűbb (Promise-alapú):
```javascript
loadData().then(() => renderDashboard());
```

**Megelőzés**:

**JS-prompt szabály**:
```
"Adatbetöltés MINDIG `await` vagy `.then()` mögött történik, 
a renderelés UTÁNA. 

ROSSZ: loadData(); renderDashboard();
JÓ: await loadData(); renderDashboard();
JÓ: loadData().then(() => renderDashboard());"
```

**Szintaxis-kapu**:
- Grepeli `loadData()` sima hívásait (await vagy .then nélkül).
- Regex: `loadData\s*\(\s*\);` (nem követi `await` vagy `\.then`)
- Ha talál → újragenerálás.

**Végrehajtási tesztelés**:
- A QA-fázis böngészőben betölti az oldalt.
- Vizuálisan ellenőrzi, hogy megjelenik-e az adat (pl. screenshot).
- Vagy API network trace: van-e GET `/api/energy-consumption` hívás és 200-as válasz.

---

### [HIBA-FRONTEND-009] HTML-sztring appendChild helyett insertAdjacentHTML

**Dátum**: 2026-06-12 (v0.5.0) — korábban már leírva [HIBA-F016]

**Fájl**: `public/app.js`

**Hibaüzenet**:
```
TypeError: Failed to execute 'appendChild' on 'Node': 
parameter 1 is not of type 'Node'.
```

**Gyökérok**:
- A JS `element.appendChild(htmlString)` mintát próbál meg.
- Ez érvénytelen — a `appendChild` Node-ot vár, nem sztringet.

**Javítás**:

ROSSZ:
```javascript
element.appendChild("<div>...html...</div>");
```

JÓ (1. mód):
```javascript
element.insertAdjacentHTML('beforeend', "<div>...html...</div>");
```

JÓ (2. mód):
```javascript
const temp = document.createElement('div');
temp.innerHTML = "<div>...html...</div>";
element.appendChild(temp.firstChild);
```

**Megelőzés**:

**Szabály a promptban**:
```
"HTML-sztringek: MINDIG `insertAdjacentHTML()` vagy `innerHTML`. 
`appendChild()` CSAK Node-okra, nem sztringre.

ROSSZ: element.appendChild('<div>...</div>');
JÓ: element.insertAdjacentHTML('beforeend', '<div>...</div>');
JÓ: element.innerHTML = '<div>...</div>';"
```

**Regex-kapu**:
- Detektálja: `appendChild\s*\(\s*["'].*["']\)` minta.
- Újragenerálás.

---

### [HIBA-FRONTEND-010] Aggregát-lekérdezés alias nélkül — "undefined" az eredményben

**Dátum**: 2026-06-12 (v0.5.0) — korábban már leírva [HIBA-F020]

**Fájl**: `src/db/db.ts` (seed lekérdezés) ↔ `public/app.js` (adat felhasználás)

**Hibaüzenet**:
```
"undefined kWh" a felületen; 
konzolban: data.totalConsumption === undefined
```

**Gyökérok**:
- A seed `SELECT SUM(consumption) FROM ...` lekérdezést futtatta.
- Az eredmény kulcsa: `SUM(consumption)` (literál, nem alias).
- A kód `.totalConsumption` vagy `.sum` mezőt keresett → nem talált.

**Javítás**:

ROSSZ:
```sql
SELECT SUM(consumption) FROM consumption_log;
-- eredmény: { "SUM(consumption)": 1234 }
```

JÓ:
```sql
SELECT SUM(consumption) AS total_consumption FROM consumption_log;
-- eredmény: { "total_consumption": 1234 }
```

**Megelőzés**:
- (Már felsorolva más fájlban, de ismétlendő.)
- **Minden agregátum lekérdezésben KÖTELEZŐ az alias**.
- SQL-prompt szabály: `"Agregáló függvények (COUNT, SUM, AVG, MIN, MAX) MINDIG alias-t kapnak: AS cnt, AS total_sum, stb."`

---

## 🟡 KATEGÓRIA: Backend-frontend szinkronizáció (2026-06-12)

### [HIBA-INTEGRATION-001] Route-végpont lista szinkronizálása hiánya

**Dátum**: 2026-06-12 (v0.5.0)

**Gyökérok**:
- Az összes route-fájl independ definiálta az URL-eket.
- A frontend pedig guesselt — nincs "single source of truth".

**Javítás**:
- A pipeline **generálása után** összegyűjti az összes route-fájlból az exportált URL-eket.
- Kiírja: `generated/ROUTES.json`

Minta:
```json
[
  { 
    "method": "GET", 
    "path": "/api/energy-consumption", 
    "params": ["user_id"],
    "description": "Get energy consumption data for user"
  },
  { 
    "method": "GET", 
    "path": "/api/solar-production", 
    "params": ["user_id"],
    "description": "Get solar production data for user"
  }
]
```

- Ezt az frontend-generáló prompt megkapja, és **csak ezekre** hivatkozhat.

**Megelőzés**:

**Route-export kapu**:
- Minden route-fájl `export const ROUTES = [...]` tömböt ad.
- Amit a központi aggregátor összeszed: `routes-aggregate.json`.

**Frontend-prompt injektálás**:
- Az összes végpont lista explicit a promptban.

**HTTP-QA**:
- Az összes frontend fetch-hívás valóban 2xx-et ad vissza.

---

### [HIBA-INTEGRATION-002] Seed-adatok hiánya — API-végpont üres választ ad

**Dátum**: 2026-06-12 (v0.5.0) — session_log 17:57 `"SEED-KAPU: minden adat-végpont üres"`

**Fájl**: `src/db/db.ts` (seed függvény)

**Hibaüzenet**:
```
Az API `[]` tömböt ad vissza; 
a frontend "No data" üzenetet mutat
```

**Gyökérok**:
- A seed lekérdezés nem futott le.
- Vagy az INSERTek csendben meghiúsultak (RLS, constraint, típushiba).

**Javítás**:

1. A pipeline a db inicializálása után ellenőrzi:
   ```
   "Van-e legalább 1 sor az összes adattáblában?"
   ```
   Ha nem → seed újrafutása vagy explicit hibajelzés.

2. **SEED-KAPU**: a HTTP-QA lépésben az összes adat-végpont **minimum 1 sort** kell visszaadjon.
   - Ha 0 → újra seed + hiba-log.

**Megelőzés**:

**Seed-validáció után**:
```sql
SELECT COUNT(*) FROM energy_consumption; 
SELECT COUNT(*) FROM solar_production;
-- Ha valamelyik 0 → hiba
```

**QA-lánc része**:
```
"GET /api/X → min 1 elem válasz, max 1000 elem.
GET /api/X?user_id=1 → konkrét user adatai vagy 200-empty array ha nincs."
```

---

## 🟢 KATEGÓRIA: Mérési és korrekciós stratégia

### [LESSON-QA-METRICS-001] Frontend-generálási siker-metrikák

**Definiáció**:

- **Syntax pass**: 
  - JS: `node -c` zöld
  - HTML: parser zöld
  
- **Contract pass**: 
  - DOM-id-szerződés teljesülve
  - HTML ⊇ contract.ids
  - JS ⊆ contract.ids
  
- **API alignment pass**: 
  - Frontend fetch-utak = ROUTES.json útvonalak
  
- **Runtime pass**: 
  - Böngészőben betöltés 0 konzolos hiba
  - Adatok megjelennek
  
- **Visual pass**: 
  - Screenshot-alapú
  - Legalább 1 kártya + adat-szöveg látható

### [LESSON-QA-GATES-001] Frontend-generálás kapu-lánca

**1. Syntax gate**:
```bash
node -c public/app.js
npx htmlhint public/index.html  # ha van
```

**2. Contract gate**:
- DOM-id validálás
- API endpoint validálás

**3. Compile gate**:
- Teljes backend compile (TypeScript)

**4. Runtime gate**:
- Server indítás
- HTTP-QA (3-5 végpont tesztelése)

**5. Visual gate** (opcionális):
- Puppeteer/Playwright böngészőben screenshotok
- OCR text detection ("kWh", számok megjelennek-e)

---

### [LESSON-FALLBACK-001] Ha a frontend N-szer bukik: eszkaláció

**1-2x szinkron bukás**:
- Újragenerálás friss kontextussal (előzmények törlése)

**3-4x szinkron bukás**:
- Modell-eszkaláció: coder → nagyobb (14B Q4 vagy felhő-modell terv-fázishoz)

**5x+ szinkron bukás**:
- Emberi review
- Az orchestrator **STOP**-ot ír
- A végpont-lista / adatminta / dom-szerződés **manuális ellenőrzés** alatt kerül
- Újra startup friss, validált adatokkal

---

## 📋 KITERJESZTETT ELLENŐRZŐ LISTA — Frontend generáláshoz

### Pre-generálás

- [ ] Backend KOMPILÁLVA és FUTVA — route-lista / válasz-mintavétel elérhető
- [ ] DOM-szerződés (`dom-contract.json`) megvan — összes id-szerződés definiálva
- [ ] API-végpont lista (`ROUTES.json`) megvan — mind a HTML, mind a JS megkapja

### HTML generálás

- [ ] HTML-sablon injektálva a prompt-ba (nem üres sheet)
- [ ] HTML-generáló prompt tartalmazza: "Kötelezően add meg ezeket az id-kat: [...]"
- [ ] HTML-kapu: szintaxis OK, HTML parser zöld
- [ ] HTML-kapu: tartalmazza az összes dom-contract id-kat
- [ ] HTML-kapu: `<script src="app.js">` vagy `<script>...</script>` tag jelen

### JS generálás

- [ ] JS-prompt KÖTÖTT a DOM-szerződéshez: "CSAK ezeket az id-kat használd: [...]"
- [ ] JS-prompt KÖTÖTT a route-listához: "Végpontok: [...]"
- [ ] JS-prompt KÖTÖTT az API-válasz-mintákhoz: "Válasz-alak: {...}"
- [ ] JS-prompt szabályok: **no import/export, vanilla only, await-based fetch, insertAdjacentHTML**
- [ ] JS-szintaxis kapu: `node -c public/app.js` zöld

### Validálás

- [ ] Contract gate: HTML tartalmaz-e összes dom-contract id-kat?
- [ ] Contract gate: JS csak a szerződés-id-kat használ-e?
- [ ] API-alignment gate: frontend fetch-path-jai = ROUTES.json-ban felsorolt útvonalak?
- [ ] ÉLŐ API-mintavétel: valódi végpont-válaszok `API_RESPONSES.json`-ba, a JS-prompt megkapja
- [ ] Seed-kapu: legalább 1 sor az összes adat-táblában az indítás után
- [ ] HTTP-QA: 2-3 főbb adat-végpont 2xx választ ad, nem 4xx/5xx

### Runtime

- [ ] Server start: `node dist/server.js` indul, portot slussal nem lövi le, 3 mp alatt ready
- [ ] Böngésző betöltés: nincs konzolos hiba (0 red errors)
- [ ] Adatok megjelennek: legalább 1 szám / érték "undefined" helyett
- [ ] Visual kapu (ha böngésző): kártyák + fejléc + számok láthatók (screenshot)

### Post-generálás

- [ ] Hibamódsagyűjtés: minden generálási bukás rögzítésre kerül
- [ ] Lesson-append: bukás → `codingLessonsLearnt.md`-be új bejegyzés
- [ ] Versioning: a sikeres frontend-verzió git-commitba kerül

---

## 📚 Integrációs dokumentumok

### `dom-contract.json` minta:
```json
{
  "version": "1.0.0",
  "generated_at": "2026-06-12T18:00:00Z",
  "required_ids": {
    "app-header": "Main header element",
    "app-main": "Main content container",
    "energy-grid": "Energy consumption grid",
    "solar-grid": "Solar production grid",
    "status": "Status indicator",
    "loading-spinner": "Loading animation"
  },
  "required_classes": [
    "card",
    "card-title",
    "card-body",
    "grid-row",
    "btn",
    "btn-primary"
  ],
  "root_element": "app-main",
  "constraints": {
    "no_framework": "vanilla JS only",
    "no_module_syntax": "no import/export",
    "must_load_async": "data loaded via fetch with await"
  }
}
```

### `ROUTES.json` minta:
```json
[
  {
    "method": "GET",
    "path": "/api/energy-consumption",
    "params": ["user_id"],
    "response": {
      "schema": {
        "user_id": "string",
        "consumption": "number[]",
        "timestamp": "ISO8601"
      },
      "example": {
        "user_id": "user-123",
        "consumption": [100, 150, 200],
        "timestamp": "2026-06-12T00:00:00Z"
      }
    }
  },
  {
    "method": "GET",
    "path": "/api/solar-production",
    "params": ["user_id"],
    "response": {
      "schema": {
        "user_id": "string",
        "production": "number[]",
        "timestamp": "ISO8601"
      },
      "example": {
        "user_id": "user-123",
        "production": [50, 75, 100],
        "timestamp": "2026-06-12T00:00:00Z"
      }
    }
  }
]
```

---

## 🔧 Orchestrációs szabályok

### Réteg-szintű függőség (SZIGORÚ SZ. SORRENDBEN)

1. **Backend compile + route-lista export** (rang 5-6)
2. **API-mintavétel + ROUTES.json, API_RESPONSES.json** (rang 6)
3. **DOM-szerződés-export** (rang 6-7)
4. **HTML generálás** (rang 7)
5. **JS generálás** (rang 8) ← **CSAK HTML után**
6. **Server runtime + HTTP-QA** (rang 9)
7. **Visual validation** (rang 10, opcionális)

### Paralelizálás csak ugyanazon rang-en belül engedélyezett!

- Rang 5-6 (backend + API): párhuzam OK
- Rang 7 (HTML): egyszeres
- Rang 8 (JS): egyszeres, **mindig HTML után**
- Rang 9 (runtime): egyszeres




---

## [PanelLakó újraépítés — 2026-06-13] Új tanulságok

### [HIBA-NPM-VERZIO] Fiktív/nem létező npm verziók a specifikációban
- **Dátum**: 2026-06-13
- **Fájl**: package.json
- **Hibaüzenet**: `npm error notarget No matching version found for lucide-react@^1.16.0` (illetve stripe@^22, @stripe/stripe-js@^9 stb.)
- **Gyökérok**: A helyreállító prompt-készlet rekonstruált, de nem létező verziókat rögzített (pl. lucide-react legmagasabb valós verziója ~0.4xx, nem 1.16). A „verbatim reprodukáld" elv ütközik az npm registry valóságával.
- **Javítás**: Telepítés előtt minden függőség verzióját valós, telepíthető tartományra kell igazítani (lucide-react ^0.460.0, next 14.2.30, react 18.3.1, tailwind ^3.4.16, @supabase/* aktuális). A build CSAK valós verziókkal fut le.
- **Megelőzés**: Specifikációból generált package.json esetén SOHA ne bízz a megadott verziószámokban — ellenőrizd `npm view <pkg> version`-nel, vagy használj caret-tartományt ismert valós kiadásra. A lockolt, de fiktív verzió néma `npm install` bukást okoz a generálás legelején.

### [HIBA-JSX-IDEZOJEL] Egyenes idézőjel JSX attribútum-stringben (magyar szövegben)
- **Dátum**: 2026-06-13
- **Fájl**: app/adatforrasok/page.tsx (MarketingArticle lead="…")
- **Hibaüzenet**: `TS1002: Unterminated string literal`, `TS1003: Identifier expected`
- **Gyökérok**: A `lead="… „becsült adat" jelöléssel …"` szövegben a magyar nyitó `„` után egyenes ASCII `"` (U+0022) zárás került, ami a JSX attribútum stringjét idő előtt lezárta.
- **Javítás**: JSX attribútumban kerülni kell az egyenes `"`-t; magyar idézetnél tipográfiai `„ "` (U+201E/U+201D) párt használj, vagy fogalmazd át, vagy `{' '}`/`&quot;`-tal escape-eld. Ha a szöveg úgyis változhat, a legbiztosabb attribútum helyett gyermek-szövegként (JSX text) átadni.
- **Megelőzés**: Magyar UI-szövegek generálásakor a `"` és `'` karaktereket JSX attribútumon belül mindig tipográfiai megfelelővel vagy escape-pel helyettesítsd; futtass `tsc --noEmit`-et korán és gyakran, hogy az ilyen parse-hiba ne halmozódjon.

### [HIBA-NEXTFONT-DEVCACHE] next/font módosítás után „Times New Roman" — szükséges a dev szerver újraindítása
- **Dátum**: 2026-06-13
- **Fájl**: app/layout.tsx (next/font/google: Inter, Fraunces)
- **Hibajelenség**: A `<html className={inter.variable + ' ' + fraunces.variable}>` és `.font-display` ellenére a böngészőben minden szöveg a böngésző alapértelmezett serifjére (Times New Roman) esett vissza; `getComputedStyle(body).fontFamily` nem a generált `__Inter_*` / `__Fraunces_*` nevet adta, és 0 `@font-face` volt a kliensben.
- **Gyökérok**: A futó `next dev` szerver az ELSŐ fordításkor (a font hozzáadása ELŐTT) gyorsítótárazta a root layoutot. HMR a layout/next-font változást nem töltötte újra a fontot, így üres `--font-*` változókkal szolgált ki. (A `next build` ezzel szemben helyesen self-hostolta a fontokat: `.next/static/media/*.woff2` + `@font-face` a CSS-ben.)
- **Javítás**: A dev szerver TELJES újraindítása (stop→start) után friss fordítás lefutott, a `--font-inter` és `--font-display` feloldódott, a heading Fraunces, a body Inter lett. Verifikáció `getComputedStyle(el).fontFamily`-vel (a generált `__Fraunces_*` név jelenléte bizonyítja a betöltést).
- **Megelőzés**: next/font (vagy bármilyen root-layout szintű) módosítás után indítsd újra a dev szervert, ne csak HMR-re hagyatkozz. A self-hostolt fontot a buildben a `.next/static/media` woff2 jelenlétével ellenőrizd. Ellenőrzéskor vedd figyelembe, hogy az `innerText` a CSS `text-transform: uppercase`-t IS visszaadja (pl. „AKTIVITÁS NAPTÁR"), ezért a kisbetűs `includes('Aktivitás naptár')` téves negatívot ad — a renderelt (nagybetűs) szövegre keress.

### [HIBA-NEXT-BUILD-DEV-EGYUTT] „Cannot find module ./vendor-chunks/*.js" — `next build` futott a `next dev` mellett
- **Dátum**: 2026-06-13
- **Fájl**: .next (megosztott build cache)
- **Hibaüzenet**: Runtime 500 a böngészőben: `Cannot find module './vendor-chunks/lucide-react.js' ... .next/server/webpack-runtime.js`. A dev oldal üresen/_error-ral jött vissza, miközben a `next build` sikeres volt.
- **Gyökérok**: A `next build`-et a `next dev` szerver FUTÁSA közben indítottam. A kettő ugyanazt a `.next/` mappát írja; a production build felülírta a futó dev szerver webpack-chunkjait, így a dev szerver már nem találta a vendor-chunkokat. NEM kódhiba (a build maga lefordult).
- **Javítás**: dev szerver leállítása → `rm -rf .next` → dev szerver újraindítása (tiszta dev fordítás). Verifikáláshoz: ha buildet akarsz futtatni, ELŐBB állítsd le a dev szervert.
- **Megelőzés**: Soha ne futtass `next build`-et és `next dev`-et egyszerre ugyanazon a projekten. Sorrend: dev stop → (rm -rf .next) → build → (rm -rf .next) → dev start. A „blank/500 dev oldal sikeres build mellett" tünet szinte mindig ez.

### [HIBA-NAPTAR-TIMEZONE] Kliens-komponens dátumrácsa lokális idővel → hidratációs eltérés + rossz hét
- **Dátum**: 2026-06-13
- **Fájl**: components/dashboard/activity-calendar.tsx
- **Hibajelenség**: A naptárrács `new Date(2026,5,8)` + `getDay()/getDate()/setDate()` (lokális idő) alapján számolt cellákat, miközben az események ISO-dátum stringekből (`'2026-06-13'`, UTC) jönnek. Negatív UTC-eltolású időzónában az események rossz hétre kerülhetnek, és — mivel 'use client' komponens SSR-ezik is — a szerver (egy időzóna) és a kliens (másik időzóna) eltérő rácsot renderel → React hidratációs eltérés.
- **Gyökérok**: Lokális idejű Date-műveletek időzóna-függő eredményt adnak; az SSR és a kliens időzónája eltérhet.
- **Javítás**: Minden dátumművelet UTC-ben: `Date.UTC(...)` a kezdődátumhoz, és `getUTCDay/getUTCDate/getUTCMonth/getUTCFullYear` + `setUTCDate/setUTCHours` a helperekben és a renderben. Így a rács időzóna-független, SSR=kliens.
- **Megelőzés**: Dátum-alapú UI-ban (naptár, heatmap), ami SSR-ezik VAGY ISO/UTC dátumokhoz illeszt, KIZÁRÓLAG UTC-metódusokat használj (vagy tisztán string-aritmetikát). Lokális `getDay()/getDate()` csak akkor, ha a megjelenítés szándékosan a néző helyi idejéhez kötött, és nem SSR-ezik.

## ➕ ÖSSZEVONÁS — `codingLessonsLearnt2.md` egyedi tanulságai (HENRIS Forge fordítási hibaosztályok & pipeline)

### [HIBA-FORGE-mqbhr7tt] új fordítási hibaosztály (node-ts)
- **Dátum**: 2026-06-12
- **Fájl**: src/routes/energy_consumption_routes.ts
- **Hibaüzenet**: src/routes/energy_consumption_routes.ts(17,22): error TS2352: Conversion of type 'Record<string, SQLOutputValue>[]' to type 'EnergyConsumption[]' may be a mistake because neither type sufficiently overlaps with the other. If this was intentional, convert the expression to 'unknown' first.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-guardfix] A QA-javító modell MŰKÖDŐ fájlokat rontott el (export-vesztés, API-hallucináció)
- **Dátum**: 2026-06-13
- **Fájl**: src/services/solar_production_service.ts, src/db/db.ts
- **Hibaüzenet**: TS2306 "is not a module" (eltűnt exportok); TS2339 "Property 'serialize' does not exist on type 'DatabaseSync'"
- **Gyökérok**: a javító LLM kimenete ellenőrzés nélkül íródott ki — a "javítás" exportokat törölt, és a régi sqlite3 (callback) API-t hallucinálta a node:sqlite-be; a futás a legjobb állapotnál (QA 2/3, futó app) ROSSZABBUL ért véget (nem indult)
- **Javítás**: (1) guardFix kapu MINDEN modell-javítás írása elé: lint + modul-szerződés (export nem tűnhet el) + csonkítás-őr (>65% méretvesztés tilos) — sérülésnél a javítás ELVETVE, a régi tartalom marad; (2) node:sqlite lint-szabály: serialize/parallelize/each és db.run/all/get(SQL-string) TILOS — DatabaseSync: exec()/prepare(), StatementSync: run()/get()/all(); (3) ZÁRÓ BEST-STATE ŐR: ha a futás vége rosszabb a legjobb pillanatképnél (nem fordul / nem indul / kevesebb QA), automatikus visszaállítás a legjobb állapotra + újraindítás + újra-QA
- **Megelőzés**: modell-kimenet SOHA nem írható működő fájl fölé kapuzás nélkül; a pipeline végállapota mindig a legjobb ismert állapot

### [HIBA-FORGE-ts-det] A fordító által MEGMONDOTT javítások modellkört égettek (9 sikertelen próba)
- **Dátum**: 2026-06-13
- **Fájl**: src/routes/energy_consumption_routes.ts
- **Hibaüzenet**: TS2614 "Did you mean to use 'import db from ...' instead?", TS2352 "convert the expression to 'unknown' first"
- **Gyökérok**: a fordító a hibaüzenetben szó szerint megadja a megoldást, mégis LLM-javítókör pörgött rajta (qwen3:8b 9 próbán át nem oldotta meg)
- **Javítás**: determinisztikus fordítóhiba-javító (autoRepairTsFromErrors) a javítókör 0. lépcsőjeként: TS2614 (named→default import), TS2613 (default→named), TS2352 (as → as unknown as), TS7006 (param: any), TS6133 (nem használt import törlése) — modell nélkül, azonnali újrafordítással; élesben: 1 mp alatt ZÖLD fordítás
- **Megelőzés**: ha a fordító megmondja a fixet, azt GÉP alkalmazza, nem LLM

### [HIBA-FORGE-pull-stall] Ollama modell-letöltés csendben megakadt (3,5 óra 25%-on)
- **Dátum**: 2026-06-13
- **Fájl**: — (ollama pull qwen2.5-coder:14b)
- **Hibaüzenet**: nincs — a progress 25%-on állt, a folyamat élt, de nem haladt
- **Gyökérok**: hálózati elakadás; az ollama pull nem időtúllépik magától
- **Javítás**: a letöltés megszakítása és újraindítása — az Ollama a részleges blobokról folytatja (nem vész el a megvolt rész)
- **Megelőzés**: hosszú letöltésnél frissesség-figyelés (változik-e a progress N perc alatt); Model-Ops ütemező: stagnáló pull → automatikus újraindítás

### [HIBA-FORGE-mqbi8fxx] új fordítási hibaosztály (node-ts)
- **Dátum**: 2026-06-12
- **Fájl**: src/db/db.ts
- **Hibaüzenet**: src/db/db.ts(35,98): error TS2554: Expected 1 arguments, but got 2.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqbi8fxz] új fordítási hibaosztály (node-ts)
- **Dátum**: 2026-06-12
- **Fájl**: src/db/db.ts
- **Hibaüzenet**: src/db/db.ts(6,6): error TS2304: Cannot find name 'fs'.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqbi8fy1] új fordítási hibaosztály (node-ts)
- **Dátum**: 2026-06-12
- **Fájl**: src/db/db.ts
- **Hibaüzenet**: src/db/db.ts(39,11): error TS2339: Property 'finalize' does not exist on type 'StatementSync'.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqc2av1e] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/routes/solarRoute.ts
- **Hibaüzenet**: src/routes/solarRoute.ts(36,45): error TS2345: Argument of type '{ production: any; }' is not assignable to parameter of type 'Omit<Solar, "id">'.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqc2av1h] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/utils/alerts.ts
- **Hibaüzenet**: src/utils/alerts.ts(4,29): error TS2307: Cannot find module 'nodemailer' or its corresponding type declarations.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqc3apey] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/config/db/seeds.ts
- **Hibaüzenet**: src/config/db/seeds.ts(1,10): error TS2614: Module '"../../db/db"' has no exported member 'pool'. Did you mean to use 'import pool from "../../db/db"' instead?
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqc3apf2] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/routes/deviceRoutes.ts
- **Hibaüzenet**: src/routes/deviceRoutes.ts(119,41): error TS2554: Expected 1 arguments, but got 2.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqc3apf5] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/services/costService.ts
- **Hibaüzenet**: src/services/costService.ts(3,10): error TS2614: Module '"../db/db"' has no exported member 'pool'. Did you mean to use 'import pool from "../db/db"' instead?
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-supabase] SUPABASE-BACKEND nem volt használva (SQLite-tal próbálkozott)
- **Dátum**: 2026-06-13
- **Fájl**: agent-core.js (STACKS, runProject fej)
- **Hibaüzenet**: — (a gépen futó lokális Supabase Postgrest figyelmen kívül hagyta)
- **Gyökérok**: nem volt Supabase-stack; a node-ts mindig SQLite-ra épült
- **Javítás**: új `node-supabase` stack (pg Pool + DATABASE_URL + projekt-séma forge_<projekt>); SUPABASE-FIRST: TCP-próba (probeTcp) a configból (henris.config.json) → ha fut, node-ts→node-supabase emelés; stackEnv minden startApp-nak; dbcheck/dbreset determinisztikus szkriptek
- **Megelőzés**: a backend a gépen ELÉRHETŐ szolgáltatásra épüljön (config-vezérelt), ne feltételezett SQLite-ra

### [HIBA-FORGE-fk-order] Postgres "relation X does not exist" induláskor (FK előre-hivatkozás)
- **Dátum**: 2026-06-13
- **Fájl**: src/db/db.ts (CREATE TABLE blokk)
- **Hibaüzenet**: error: relation "devices" does not exist (a consumption FK-ja előbb jött, mint a devices tábla)
- **Gyökérok**: a CREATE TABLE-ök rossz sorrendben; a hivatkozott táblának ELŐBB kell léteznie
- **Javítás**: reorderPgCreateTables — topologikus rendezés (REFERENCES-függőség szerint); írás-időben (autoRepairJs) ÉS futásidőben (a "relation does not exist" indulási hibára) is fut
- **Megelőzés**: a hivatkozott tábla mindig a hivatkozó ELŐTT jöjjön létre

### [HIBA-FORGE-route-mount] Több router EGY /api prefixen → /api/x a /:id-re illeszkedik (NaN→500)
- **Dátum**: 2026-06-13
- **Fájl**: src/server.ts (app.use('/api', xRoute) többször)
- **Hibaüzenet**: GET /api/consumption → 500 invalid input syntax for type integer: "NaN" (a /:id route kapta a "consumption"-t)
- **Gyökérok**: minden router ugyanazon a prefixen, bare '/'+'/:id' utakkal → az erőforrásnév kiesik
- **Javítás**: autoRepairJs route-mount granularitás — erőforrás-prefixre bontás a router-változó nevéből (/api/consumption, /api/solar...)
- **Megelőzés**: minden erőforrás-router a SAJÁT prefixére (/api/<erőforrás>) mountolva

### [HIBA-FORGE-pg-numeric] pg a NUMERIC/BIGINT oszlopot STRINGként adja → frontend toFixed() hasal
- **Dátum**: 2026-06-13
- **Fájl**: src/db/db.ts, public/app.js
- **Hibaüzenet**: TypeError: ...toFixed is not a function (a "50.25" string + reduce → string-konkatenáció)
- **Gyökérok**: a 'pg' a NUMERIC(1700)/BIGINT(20) típust stringként adja vissza
- **Javítás**: db.ts-ben types.setTypeParser(1700, parseFloat)+(20, parseInt) injektálva (stack-hint + autoRepairJs); a frontend-prompt Number()-coerciót ír elő renderelés előtt
- **Megelőzés**: a numerikus DB-típusokat számmá kell parse-olni; a frontend defenzíven Number()-rel coercál

### [HIBA-FORGE-fe-mock] A frontend MOCK/talált-ki-mező adatot használt a valódi API helyett
- **Dátum**: 2026-06-13
- **Fájl**: public/app.js
- **Hibaüzenet**: — (a felület "Hiba: Adatok betöltése sikertelen" minden kártyán; item.hour/savings nem létező mezők)
- **Gyökérok**: a sampleApi a frontend-generálás ELŐTT futott, de a backend még nem fordult tisztán → üres minta → a modell találgatta a mezőneveket; + setTimeout-os mock-adat
- **Javítás**: (1) sampleApi MOST determinisztikus javítókkal tisztára fordít mintavétel előtt → valós minta; (2) frontend-kapu MINDIG fut (nem csak QA=100%-nál) + MOCK-tilalom + FIELD-szerződés (a futó appot mintázza, ha a JS a valós mezők egyikét sem használja → regen friss mintával genFrontendPair-rel); (3) prompt: mező-hűség + Number-coerció
- **Megelőzés**: a frontend KIZÁRÓLAG a valós API-minta mezőit használja; mock/szimulált adat tilos; a kapu a futó app valós válaszához validál

### [HIBA-FORGE-design-gap] A generált felület silány volt egy prémium referenciához képest (ugyanaz a brief+FORGE)
- **Dátum**: 2026-06-13
- **Fájl**: design-base.js (BASE_STYLE_CSS, UI_GUIDE), agent-core.js (frontend-kapuk)
- **Hibaüzenet**: — (vizuális: egyetlen lapos, sivár lista hibás kártyákkal, szemben egy többoldalas, sidebaros, KPI+chart prémium dashboarddal)
- **Gyökérok**: a design-réteg csak egy egyszerű egyoldalas vázat (app-header/app-main) írt elő; nem volt sidebar/nézet-architektúra, gazdag KPI-kártya, többféle chart, szemantikus színrendszer; a frontend-kapu nem kényszerítette ki ezeket
- **Javítás**: a design-base.js PRÉMIUM dashboard-rendszerre emelve — (1) BASE_STYLE_CSS: sidebar-shell (.app/.sidebar/.nav-item) + kliens-oldali .view váltás, KPI-kártyák delta-trenddel (.kpi.solar/peak/grid/saving), glass-kártyák, .bars sáv-lista, gauge-konténer, szemantikus energia-színek (solar #fdb913, peak #ff6b6b, grid #4db8ff, saving #31c784, primary #2dd4bf), Sora/Inter font, hover-emelés, reszponzív sidebar; (2) UI_GUIDE: KONKRÉT sidebar+nézetváltó+KPI-sor+Chart.js (area/doughnut/bar) váz; (3) frontend-kapuk: sidebar+nav-item+KPI+Chart.js jelenléte kötelező (különben regen)
- **Megelőzés**: a vizuális minőség-PADLÓT a determinisztikus design-alap adja (a modell csak feltölti); dashboard-briefnél többnézetes sidebar + KPI-sor + Chart.js KÖTELEZŐ; a kapu kikényszeríti

### [HIBA-FORGE-ts-crossfile] A fordító megnevezte a kereszt-fájl hibát, de az LLM körökön át pörgött rajta
- **Dátum**: 2026-06-13
- **Fájl**: agent-core.js (autoRepairTsCrossFile), src/routes/*, src/models/*, src/services/*
- **Hibaüzenet**: TS2304 Cannot find name 'EnergyConsumption' · TS2305 Module '"../services/roleService"' has no exported member 'getRoleVariants' · TS18047 'r.rowCount' is possibly 'null' · TS2345 Property 'timestamp' is missing ... required in type 'Omit<Alert,"id">'
- **Gyökérok**: ezek a hibák PONTOSAN megmondják a javítást, de a megoldás MÁSIK fájlban van (hiányzó import / rossz export-név / nullable / kötelező interfész-mező); a modell ezeket lassan és hibásan javította, a fordítás 5+ körön át vörös maradt, dist/ sosem készült el, az app nem indult
- **Javítás**: autoRepairTsCrossFile (determinisztikus, modell NÉLKÜL) — TS2304→hiányzó típus-import beszúrása a models-ből helyes relatív úttal; TS2305→a modul TÉNYLEGES exportjából alias ({ getRoles as getRoleVariants } — a hívások változatlanok); TS18047/18048/2531/2532→non-null assertion a megnevezett kifejezésre (r.rowCount!); TS2345/2741/2739→a hivatkozott interfész hiányzó mezőjét opcionálissá tesszük (timestamp?:). Bekötve a fő compile-fix körbe (0c lépcső) ÉS a sampleApi-ba. Regressziós teszt: test_autorepair.js (19/19 zöld)
- **Megelőzés**: ha a fordító megnevezi a hiányt/megoldást, determinisztikusan javítjuk — a modellt rá sem engedjük; minden új ilyen hibaosztály a javítóláncba kerül, nem a promptba

### [HIBA-FORGE-build-config] A dist/ sosem készült el → "node dist/server.js" indítás elbukott
- **Dátum**: 2026-06-13
- **Fájl**: agent-core.js (ensureBuildConfig), tsconfig.json, package.json (projekt-gyökér)
- **Hibaüzenet**: Cannot find module 'dist/server.js' (a tsc a .js-t a src mellé emittálta, mert a gyökér-tsconfig-ban nem volt outDir)
- **Gyökérok**: a 7B a JÓ configot a src/config/ alá tette, a projekt-gyökérbe pedig hiányosat (target ES6, nincs outDir/rootDir; a package.json-ból hiányzott a build/start script) — a fordítás (-p tsconfig.json) a gyökér-configot használja
- **Javítás**: ensureBuildConfig — a fő fordítás ELŐTT determinisztikusan kiírja a kanonikus gyökér-tsconfig-ot (outDir:dist, rootDir:src) és garantálja a package.json build/start scriptjeit + kötelező függőségeit (express, supabase-nél pg), a meglévőket megtartva. A sampleApi is meghívja (mert elindítja az appot)
- **Megelőzés**: a build-kimenet helye (dist/) és az indító scriptek NEM a modellre vannak bízva — determinisztikusan garantáltak

### [HIBA-FORGE-static-path] express.static('public') a futtatási cwd-től függött → más mappából indítva nem találta a frontendet
- **Dátum**: 2026-06-13
- **Fájl**: agent-core.js (ensureStaticPath), src/server.ts / src/config/expressConfig.ts
- **Hibaüzenet**: Cannot GET / (a / útra nem jött HTML, mert a relatív 'public' nem oldódott fel)
- **Gyökérok**: express.static('public') relatív a process cwd-hez; ha a programot nem a projektgyökérből indítják, a public/ nem található
- **Javítás**: ensureStaticPath — a fájl dist-beli mélységéből számolt __dirname-alapú abszolút útra írja: express.static(require('path').join(__dirname, '..'[, '..'], 'public')) — bárhonnan indítva kiszolgálja a frontendet
- **Megelőzés**: statikus mappát mindig __dirname-alapú abszolút úttal kell kiszolgálni, sosem a cwd-függő relatívval

### [HIBA-FORGE-fe-gate-compile-dep] A frontend-minőség kapu csak ZÖLD build után futott → backend-elakadásnál a silány UI átcsúszott
- **Dátum**: 2026-06-13
- **Fájl**: agent-core.js (statikus frontend-minőség kapu a build-loop után)
- **Hibaüzenet**: — (vizuális: hiányzó sidebar/KPI/Chart.js, mock-adat, nem létező végpont-hívás — mind észrevétlen, mert a futó-app alapú kapu sosem futott le)
- **Gyökérok**: a meglévő frontend-kapu a futó appot mintázza, ezért CSAK sikeres fordítás+indítás után futott; ha a backend a fordításnál elakadt, a frontend ellenőrizetlen maradt
- **Javítás**: ÚJ statikus frontend-minőség kapu a build-loop után, a FORDÍTÁSTÓL FÜGGETLENÜL — a generált html+js fájlon determinisztikus red-flag vizsgálat (sidebar/nav-item, KPI-sor, Chart.js, /api/ fetch, mock-tilalom, nem létező végpont), hiba esetén koherens genFrontendPair-újragenerálás. A futó-app alapú kapu (élő minta + mező-hűség) megmarad mellette
- **Megelőzés**: a felület minőségét a backend állapotától FÜGGETLENÜL is ellenőrizni kell; statikus + dinamikus kapu együtt

### [HIBA-FORGE-mqc7ncpv] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/config/alerts.ts
- **Hibaüzenet**: src/config/alerts.ts(31,26): error TS2552: Cannot find name 'createNotification'. Did you mean 'notification'?
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqc7ncpz] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/config/alerts.ts
- **Hibaüzenet**: src/config/alerts.ts(32,11): error TS2552: Cannot find name 'sendEmailNotification'. Did you mean 'sendAlertNotification'?
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqc7ncq2] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/config/alerts.ts
- **Hibaüzenet**: src/config/alerts.ts(33,5): error TS2739: Type '{ user_id: number; message: string; }' is missing the following properties from type 'Notification': id, timestamp, read
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqc7ncq5] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/db/db.ts
- **Hibaüzenet**: src/db/db.ts(47,47): error TS2307: Cannot find module '../../config/db/seeds' or its corresponding type declarations.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqc7ncq9] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/db/db.ts
- **Hibaüzenet**: src/db/db.ts(47,47): error TS2307: Cannot find module './seeds' or its corresponding type declarations.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqc7ncqc] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/db/db.ts
- **Hibaüzenet**: src/db/db.ts(47,47): error TS2307: Cannot find module './seeds/seeds' or its corresponding type declarations.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqc7ncqf] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/db/db.ts
- **Hibaüzenet**: src/db/db.ts(46,15): error TS2304: Cannot find name 'seedData'.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqc8if1q] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/routes/costRoutes.ts
- **Hibaüzenet**: src/routes/costRoutes.ts(3,3): error TS2724: '"../services/costService"' has no exported member named 'getCostForecast'. Did you mean 'getCostForecasts'?
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqc8if1x] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/routes/reportRoutes.ts
- **Hibaüzenet**: src/routes/reportRoutes.ts(11,7): error TS2554: Expected 1 arguments, but got 3.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqc8if1z] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/routes/reportRoutes.ts
- **Hibaüzenet**: src/routes/reportRoutes.ts(25,39): error TS2339: Property 'rows' does not exist on type 'unknown'.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqc8if20] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/routes/costRoutes.ts
- **Hibaüzenet**: src/routes/costRoutes.ts(66,10): error TS1345: An expression of type 'void' cannot be tested for truthiness.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqc8if22] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/routes/reportRoutes.ts
- **Hibaüzenet**: src/routes/reportRoutes.ts(20,38): error TS2551: Property 'device_id' does not exist on type 'EnergyConsumption'. Did you mean 'deviceId'?
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-seed-truncation] A 14B levágta a nagy SQL seed-fájlt → "Unterminated template literal" → app nem indult
- **Dátum**: 2026-06-13
- **Fájl**: src/db/db.ts; agent-core.js (determinisztikus seeder + SEED-KAPU)
- **Hibaüzenet**: TS1160: Unterminated template literal (a db.ts a created_at sornál levágva); elindult=false, QA=0/0
- **Gyökérok**: a SEED-KAPU a modellre bízta a teljes, minden-táblás seedet EGY nagy template literalban; a 14B token-limitnél levágta a kimenetet → csonka db.ts → a fordítás eltört a QA-indításkor
- **Javítás**: DETERMINISZTIKUS SEEDER (ensureDeterministicSeed) — a Forge a sémából (CREATE TABLE) MAGA generálja a seedet (parsePgSchema → topoSortTables FK-sorrend → buildDeterministicSeed: típus-helyes értékek, CHECK-enum-érvényes, FK round-robin a szülő 1..N-re, SERIAL/IDENTITY/DEFAULT oszlop kihagyva). Minden INSERT KÜLÖN egysoros statement, BACKTICK template literalban (a nyers SQL aposztrófjai — NOW()-INTERVAL '0 day', '{}'::jsonb — egy-idézőjeles stringben törnének). A SEED-KAPU ELSŐ rétege ez (a modellt meg sem kérdezzük); a modell-regen csak fallback, balansz/compile-restore burokban. A stack-hint mostantól: a modell CSAK a sémát + üres if(cnt===0){} blokkot írja, a seedet a Forge tölti.
- **Megelőzés**: amit a séma teljesen determinál (seed-adat), azt determinisztikusan generáljuk, NEM a modellel — mint a style.css-t; a modellre csak a séma marad

### [HIBA-FORGE-truncation-guard] A csonka (végén levágott) modell-kimenet átcsúszott a guardFix-en
- **Dátum**: 2026-06-13
- **Fájl**: agent-core.js (isLikelyTruncated, isBalanced, guardFix, genFile truncGate)
- **Hibaüzenet**: — (a guardFix csak <35% méret-zsugorodásra vétózott; egy a VÉGÉN levágott ~70%-os fájl átment, és lemezre került a csonka db.ts)
- **Gyökérok**: nem volt strukturális csonkaság-ellenőrzés (lezáratlan template-backtick / nyitva maradt {}/()/[]); a lintGate sem nézte a backtick-paritást; a genFile toleráns fence-parsere guardFix nélkül írta ki a db.ts-t
- **Javítás**: (1) isLikelyTruncated — heurisztikus detektor (backtick-paritás + zárójel-egyensúly, regex-literál-tudatosan, hogy a /[(]/ ne legyen fals pozitív); (2) isBalanced — template-literal-TUDATOS parser (a template-törzs opaque, így az aposztrófos SQL-seed és a http:// NEM téveszt meg); (3) mindkettő beépítve a guardFix-be ÉS a genFile írás-kapujába (truncGate) — csonka kimenetnél a működő fájl marad / újragenerál
- **Megelőzés**: minden modell-kimenetet strukturálisan ellenőrizni kell írás előtt; a balansz-ellenőrzés legyen template-tudatos (a db.ts nagy SQL-template miatt), különben fals pozitív a működő fájlra

### [HIBA-FORGE-beststate-compile] Egy késői regen eltörte a fordítást, és a best-state őr nem állította vissza
- **Dátum**: 2026-06-13
- **Fájl**: agent-core.js (lastGoodSnapshot + ZÁRÓ ŐR)
- **Hibaüzenet**: — (elindult=false a futás végén, pedig korábban már fordult+elindult)
- **Gyökérok**: a bestSnapshot CSAK QA-javuláskor mentődött; ha a QA-hurok el sem jutott sikeres QA-ig (mert egy regen eltörte a fordítást), nem volt mit visszaállítani; ráadásul a `compiled` a fő fázis óta stale-true maradt, így a ZÁRÓ ŐR `!compiled` ága sosem ütött
- **Javítás**: (1) lastGoodSnapshot — az ELSŐ fordul+elindult állapot mentése (nem csak QA-javuláskor); (2) a frontend-regen és a QA-fix UTÁN recompile; ha eltört és van last-known-good → azonnali restore (CSAK restore, nincs újabb regen → nincs ciklus); (3) a ZÁRÓ ŐR a döntés ELŐTT frissíti a `compiled`-et egy recompile-lal, és recoverSnap = bestSnapshot || lastGoodSnapshot
- **Megelőzés**: a "last known good"-ot az ELSŐ működő állapotnál rögzíteni kell; minden késői regen után fordítást ellenőrizni és törésnél visszaállni; a recovery-döntéshez friss állapot kell, nem a fő fázis stale értéke

### [HIBA-FORGE-mqcb7wmb] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/models/costForecast.ts
- **Hibaüzenet**: src/models/costForecast.ts(3,10): error TS2440: Import declaration conflicts with local declaration of 'CostForecast'.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqcb7wmf] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/models/deviceBreakdown.ts
- **Hibaüzenet**: src/models/deviceBreakdown.ts(3,10): error TS2440: Import declaration conflicts with local declaration of 'DeviceBreakdown'.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqcb7wmi] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/models/energyConsumption.ts
- **Hibaüzenet**: src/models/energyConsumption.ts(3,10): error TS2440: Import declaration conflicts with local declaration of 'EnergyConsumption'.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqcb7wmm] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/services/costService.ts
- **Hibaüzenet**: src/services/costService.ts(4,10): error TS2724: '"../models"' has no exported member named 'CostForecast'. Did you mean 'ICostForecast'?
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqcb7wmo] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/services/deviceService.ts
- **Hibaüzenet**: src/services/deviceService.ts(4,10): error TS2724: '"../models"' has no exported member named 'DeviceBreakdown'. Did you mean 'IDeviceBreakdown'?
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqcb7wmq] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/routes/energyRoutes.ts
- **Hibaüzenet**: src/routes/energyRoutes.ts(55,7): error TS2353: Object literal may only specify known properties, and 'timestamp' does not exist in type 'EnergyConsumption'.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqcb7wmv] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/services/energyService.ts
- **Hibaüzenet**: src/services/energyService.ts(20,34): error TS2551: Property 'energy_usage' does not exist on type 'EnergyConsumption'. Did you mean 'energy_used'?
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqcb7wmy] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/services/energyService.ts
- **Hibaüzenet**: src/services/energyService.ts(20,16): error TS2339: Property 'timestamp' does not exist on type 'EnergyConsumption'.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqcdgodv] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/services/cost-forecast.service.ts
- **Hibaüzenet**: src/services/cost-forecast.service.ts(11,1): error TS1128: Declaration or statement expected.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqcdgoe0] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/services/cost-forecast.service.ts
- **Hibaüzenet**: src/services/cost-forecast.service.ts(11,8): error TS1434: Unexpected keyword or identifier.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqcdgoe4] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/services/user-profile.service.ts
- **Hibaüzenet**: src/services/user-profile.service.ts(3,10): error TS2614: Module '"../db/db"' has no exported member 'pool'. Did you mean to use 'import pool from "../db/db"' instead?
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqcdgoe8] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/utils/device-breakdown.util.ts
- **Hibaüzenet**: src/utils/device-breakdown.util.ts(4,20): error TS2307: Cannot find module 'moment' or its corresponding type declarations.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqcdgoed] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/utils/schema-validator.util.ts
- **Hibaüzenet**: src/utils/schema-validator.util.ts(3,37): error TS2307: Cannot find module 'ajv' or its corresponding type declarations.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqcdgoeg] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/utils/schema-validator.util.ts
- **Hibaüzenet**: src/utils/schema-validator.util.ts(10,7): error TS2322: Type '{ type: string; properties: { id: { type: string; nullable: boolean; }; timestamp: { type: string; format: string; }; device_id: { type: string; }; energy_usage: { type: string; }; cost: { type: string; }; }; required: string[]; additiona
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqchjl2f] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/routes/energyRoutes.ts
- **Hibaüzenet**: src/routes/energyRoutes.ts(34,26): error TS2551: Property 'GET_CONSUMPTION_BY_DEVICE_ID' does not exist on type '{ GET_ALL_CONSUMPTION: string; GET_CONSUMPTION_BY_ID: string; }'. Did you mean 'GET_CONSUMPTION_BY_ID'?
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqchjl2j] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/utils/analyticsLayout.ts
- **Hibaüzenet**: src/utils/analyticsLayout.ts(33,21): error TS2339: Property 'style' does not exist on type 'Element'.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

## ➕ ÖSSZEVONÁS — `codingLessonsLearnt3.md` egyedi tanulságai (multi-agent fejlesztési hullámok)

## ➕ APPEND — 2026-06-12 API Workbench Pro: multi-agent fejlesztési hullám (stratégia → implementáció → QA)

### [LESSON-AGENT-090] Háttérben futó multi-agent workflow MEGHAL a session bezárásakor — folytatás célzott gap-fill hullámmal
- **Dátum**: 2026-06-12
- **Tünet**: 7 párhuzamos kód-agent workflow ~15:22-kor megszakadt (session bezárás); 0 bájtos eredményfájl, 4 óra fájl-inaktivitás, a git status "befagyott" félkész állapotot mutatott.
- **Gyökérok**: A háttér-workflow a session gyermekfolyamata — a session/app bezárása megöli, az agentek pedig fájlírás KÖZBEN halnak meg.
- **Tipikus félbevágott-állapot minták** (mindet megtaláltuk):
  1. **Lógó refaktor**: lib-be kiemelt függvény + a fájlban maradt eredeti = `Duplicate declaration` build-hiba (api-explorer.tsx `resolveRef`).
  2. **Import-van-JSX-nincs**: a komponensek elkészültek és importálva vannak, de a route JSX-be soha nem kerültek be (execution.tsx, api-explorer.tsx).
  3. **Kész-de-nem-ellenőrzött** workstreamek (shell, portability) — működtek, de senki nem auditálta őket.
- **Fix/eljárás**: NEM újrafuttatni az egész hullámot! (1) mtime + git status + `npm run build` alapján kárfelmérés; (2) workstreamenként célzott "finisher" agent: ELŐSZÖR integritás-audit a saját fájljain (kiegyensúlyozott JSX, duplikált deklarációk, bekötetlen komponensek), UTÁNA gap-fill; (3) végén teljes integrátor-QA (build+tsc+lint zöldre).
- **Megelőzés**: hosszú implementációs hullám alatt a session maradjon nyitva; a workflow-eredmény fájl 0 bájtos = SOSEM fejeződött be, akkor se higgy a "completed" látszatnak.

### [LESSON-CSS-091] A design tokenek TELJES oklch() színértéket hordoznak — tilos újabb oklch()-ba csomagolni
- **Dátum**: 2026-06-12
- **Fájl**: `src/components/common/primitives.tsx` (ReadinessBar)
- **Hiba**: `background: oklch(${tone})` ahol a token már maga `oklch(0.65 0.14 155)` → érvénytelen beágyazott `oklch(oklch(...))` → a readiness-sáv SOHA nem festett (néma vizuális bug, build zöld!).
- **Fix**: a CSS-változót közvetlenül használd: `background: var(--color-success)` vagy a tone-string maga a var-hivatkozás.
- **Megelőzés**: TSX-ben SOHA ne építs oklch()/hsl() wrapper-t token köré; grep minta: `oklch(\$\{` és `oklch(var` → mindkettő gyanús.

### [LESSON-SHADCN-093] shadcn CommandDialog: kötelező sr-only DialogTitle a DialogContent-ben
- **Dátum**: 2026-06-12
- **Tünet**: minden palette-nyitásnál Radix a11y console error: "DialogContent requires a DialogTitle".
- **Fix**: `src/components/ui/command.tsx` CommandDialog-jába: `<DialogTitle className="sr-only">Command palette</DialogTitle>` közvetlenül a DialogContent alá + DialogTitle import.
- **Megelőzés**: minden fejléc nélküli Dialog/Sheet kapjon sr-only címet.

### [LESSON-VITE-094] Lovable + TanStack Start: 8080-as port, vite build NEM típusellenőriz, új route-id-k codegen-ig tsc-hibásak
- **Dátum**: 2026-06-12
- **Tények**: (1) `@lovable.dev/vite-tanstack-config` a dev szervert a **8080**-as porton indítja (nem az alap 5173-on) — preview/launch.json bekötés előtt nézd meg a szerver logot. (2) `vite build` esbuild-del transzpilál, NEM futtat típusellenőrzést — a zöld build mellé KÖTELEZŐ `npx tsc --noEmit`. (3) Új route fájl `createFileRoute("/x")` hívása tsc-hibát ad, amíg a `routeTree.gen.ts` újra nem generálódik (build/dev futtatja a codegen-t) — ez VÁRT hibaosztály, ne "javítsd" kézzel a routeTree.gen.ts szerkesztésével (tilos!).

### [LESSON-PARALLEL-095] Párhuzamos kód-agentek konfliktusmentes receptje (7 agent, 0 merge-ütközés)
- **Dátum**: 2026-06-12
- **Recept**: (1) **Diszjunkt fájl-tulajdon**: minden fájlnak PONTOSAN egy gazdája; megosztott hotspotok (store.ts, types.ts, seed.ts, package.json, routeTree.gen.ts) MINDENKINEK tiltottak — a szükséges types-bővítést (1 opcionális mező) a hullám ELŐTT, kézzel vittük be. (2) **Laza szerződések** fájl-szerkesztés helyett: `data-tour` attribútumok, `window CustomEvent ("awp:start-tour")`, verziózott localStorage kulcsok (`awp.*`) — a fogyasztó graceful-skip-pel tűri a hiányt. (3) **Self-contained duplikáció > kereszt-import**: párhuzamos agent NE importáljon másik agent még-nem-létező fájljából (scoring képletek duplikálva, integrátor dedupe-ol később). (4) Hard-rule blokk minden promptban (useShallow, SSR-guard, no-new-deps, unused-import) + végső integrátor-QA agent build+tsc+lint zöldre.
- **Eredmény**: 12 módosított + ~45 új fájl, 0 ütközés, build zöld, tsc 0 hiba, lint 0 error.

---

## ➕ APPEND — 2026-06-13 Governance & Readiness platform hullám

### [LESSON-VITE-096] Új import-ok mid-session beszúrása megtörheti a React-dedupe-ot dev módban → "Invalid hook call / more than one copy of React"
- **Dátum**: 2026-06-13
- **Tünet**: új lucide ikon / komponens import hozzáadása után a futó dev szerveren `Invalid hook call` + `Cannot read properties of null (reading "useMemo")` a Radix komponensekben (AlertDialog/useScope). A production `vite build` VÉGIG zöld volt, az SSR HTML hibamentes.
- **Gyökérok**: a Vite menet közben újra-optimalizálja a deps-et, és átmenetileg két React-példány keletkezik (a régi optimalizált chunk + az új) — a Radix `useScope`/`createContextScope` az egyikkel renderel, a hook a másikat látja.
- **Fix**: töröld a `node_modules\.vite` mappát (Vite dep cache) + dev szerver újraindítás. Utána tiszta.
- **Megelőzés**: NE ítéld a kódot hibásnak dev-konzol "Invalid hook call" alapján, amíg a `vite build` zöld és az SSR hibamentes — előbb ürítsd a `.vite` cache-t és indítsd újra. Vizuális ellenőrzés ELŐTT mindig `.vite` cache-ürítés, ha új top-level importok kerültek be.

### [LESSON-AGENT-097] Megszakított multi-agent hullám: a "failed" builder gyakran KÉSZ van — a session-limit a záró structured-output return-t öli meg
- **Dátum**: 2026-06-13
- **Tünet**: a workflow `[tmf] failed / [i18n] failed` státuszt jelez (session limit), de a fájlok a lemezen MEGVANNAK és a build zöld.
- **Gyökérok**: az agent a tényleges fájlírást már befejezte, csak a végső structured-output tool-hívás akadt el a limiten → "failed" látszat.
- **Fix/eljárás**: NE indítsd újra az egész hullámot. (1) `Get-ChildItem`-mel ellenőrizd a "failed" workstream fájljait a lemezen + méretüket; (2) `npm run build` + `npx eslint --fix`; (3) csak a TÉNYLEGESEN hiányzó integrációs lépést pótold (itt: nav-bekötés + prettier-fix). A megszakított integrátor munkáját (nav + verzióbump) gyakran kézzel gyorsabb befejezni, mint új agentet indítani.

### [LESSON-ARCH-098] Párhuzamos check-pack agentekhez: közös "engine + stub" szerződés egyetlen soros foundation agenttel
- **Dátum**: 2026-06-13
- **Context**: 7 párhuzamos agentnek egy egységes readiness-scorecardba kellett pontoznia (governance/security/trace/drift/contract/resilience/schema).
- **Recept**: (1) egy SOROS foundation agent létrehozza a `src/lib/checks/types.ts` szerződést (`Finding`/`CheckResult`/`ReadinessReport`/`CheckFn`), az `engine.ts` aggregátort (fix import-lista + súlyok), ÉS 7 STUB pack fájlt (mindegyik `emptyResult`-tal tér vissza, fix export-névvel + `<NAME>_WEIGHT` konstanssal). (2) A párhuzamos agentek KIZÁRÓLAG a saját pack-fájljuk arrow-fn TÖRZSÉT cserélik — az export-nevet, signatúrát, súlyt NEM. (3) Az engine-t/types-ot SENKI nem szerkeszti a foundation után. (4) A scorecard agent az engine interfészét fogyasztja, így stub-okkal is renderel (a pontszámok "feltöltődnek", ahogy a packek landolnak). Eredmény: 0 ütközés, a UI azonnal demo-zható, a packek inkrementálisan élesednek.
- **Megelőzés**: ha N agent egy aggregátorba táplál, a foundation fázis adja a contract+stub csontvázat; a builderek csak törzset cserélnek. Soha ne hagyd, hogy két agent ugyanazt az aggregátor/registry fájlt írja.

### [LESSON-CONCURRENCY-099] Konkurens fájl-edit külön régióban túlél, ha targeted Edit (nem full Write)
- **Dátum**: 2026-06-13
- **Context**: a felhasználó kérésére kézzel beszúrtam egy sort a `trust.tsx` storage-táblába, MIKÖZBEN egy párhuzamos i18n-agent ugyanazt a fájlt szerkesztette (t() bekötés).
- **Eredmény**: mindkét szerkesztés túlélt — az enyém (data-array bővítés) és az agenté (copy → t()), mert KÜLÖNBÖZŐ string-régiókat céloztak, és mindkettő targeted Edit (nem full-file Write) volt. Az integrátor utólag még két sort hozzátett, duplikáció nélkül.
- **Megelőzés**: konkurens szerkesztésnél targeted Edit + diszjunkt régió biztonságos; full-file Write klobberol. Adat-tömb bővítése (nem fordítandó copy) alacsony ütközési kockázatú egy i18n-hullám alatt. Mindig verifikáld a végén, hogy a sor megmaradt.

### [LESSON-PREVIEW-100] Route-fájl HMR + cold-compile hamis "broken" leolvasást ad — tiszta restart + warm route kell a megbízható preview-teszthez
- **Dátum**: 2026-06-13
- **Context**: a /docs viewer deep-linkjét teszteltem; egy route-fájl (docs.tsx) szerkesztése UTÁN a preview vegyes/ellentmondásos eredményeket adott (egyik doc-gomb váltott, másik nem; deep-link a defaultra esett), pedig a kód helyes volt.
- **Gyökérok**: (1) route-fájl szerkesztése után a Vite HMR a TanStack route-nál zavaros köztes állapotba kerülhet (régi closure + új state). (2) Restart után az ELSŐ /route kérés cold-compile-t indít; ha túl korán olvasol (a route chunk + a mount-effekt lefutása előtt), a default állapotot látod — hamis "deep-link broken" leolvasás.
- **Fix/eljárás**: route-logika preview-tesztelésénél: dev szerver leállítás → `.vite` cache ürítés → újraindítás → ELŐSZÖR egy "warming" navigáció a route-ra (várj, amíg fordít) → CSAK UTÁNA mérj, async `setTimeout` várakozással (≥1.5–4s a cold-route első kérésnél). A React state-frissítés async, ezért `setActiveId`/click UTÁN MINDIG várj egy frame-nél többet, mielőtt a DOM-ot olvasod.
- **Megelőzés**: ne ítélj route-logikát hibásnak egyetlen, közvetlenül edit/HMR utáni leolvasásból. SSR-route deep-link mintája: SSR-biztos default `useState`, majd mount-`useEffect` ami a `?param`-ot honorálja (id ÉS path feloldással) — így nincs hidratációs mismatch, és a deep-link a kliensen érvényesül.

## ➕ ÖSSZEVONÁS — `HenrisForge/codingLessonsLearnt.md` egyedi Forge-leckéi (2026-06-13)

### [HIBA-FORGE-give-up-partial] A build részlegesen feladta (5/20 fájl) timeout/üres modell-válasz után
- **Dátum**: 2026-06-13
- **Fájl**: agent-core.js (genFile + 2c hiányzó-pótlás)
- **Hibaüzenet**: — (PROJEKT KÉSZ: 5/20 fájl · fordult: nem · elindult: nem · 41 újraírás; csak 1 src fájl került lemezre)
- **Gyökérok**: a genFile egyetlen retry után eldobta a fájlt (content=null), ha a kapuk jeleztek VAGY a modell timeoutolt/üreset adott (GPU-terhelés); a 2c fallback pedig CSAK 2 kört futott, majd a build feladta undone fájlokkal → a fordítás eleve esélytelen volt. A hiányzó modell/service fájlok importjai miatt ráadásul a fordítás-javító végtelen ping-pongba került (lógó import törlése ↔ modell újra-hozzáadja)
- **Javítás**: KITARTÓ HIÁNYZÓ-PÓTLÁS — a 2c fallback helyett egy loop, ami addig pótolja az undone fájlokat (soros, timeout-rezisztens újrapróbálással), amíg VAN haladás; meddő körben agent-fallback; csak akkor lép tovább, ha az agent-fallback is 2x egymás után 0 új fájlt hoz. Így a build NEM áll meg részlegesen — "addig ne álljon meg, amíg kész nincs". A hiányzó fájlok elkészülve megszüntetik az import-ping-pongot is.
- **Megelőzés**: a fájl-generálás sosem adhatja fel részlegesen; a timeout/üres válasz nem végállapot, hanem újrapróbálandó; a feladás csak tartós (agent-fallback utáni) 0-haladásnál engedélyezett

### [HIBA-FORGE-archetype-negation] A recept-tervező DASHBOARD-nak lett osztályozva → rossz frontend-kapuk
- **Dátum**: 2026-06-13
- **Fájl**: design-base.js (classifyAppType)
- **Hibaüzenet**: — (a frontend-kapu KPI-kártyasort és Chart.js-t követelt egy receptes webappra, végtelen újragenerálás)
- **Gyökérok**: (1) a dashboard-osztályozó a lone "áttekint/statisztik" szavakra is illeszkedett, amik sok appban előfordulnak (a recept-brief "áttekintő statisztika"-ja → dashboard); (2) a tagadott említések ("Ez egy webshop, NEM dashboard") a "dashboard" szóra illeszkedtek a tagadás ellenére → a webshop/blog dashboardnak látszott
- **Javítás**: classifyAppType átírva — a dashboard CSAK explicit, erős jelre üt (dashboard/irányítópult/kpi/analitik/adatvizualiz/monitoring/admin panel), a lone "áttekint/statisztik/kimutatás" NEM triggerel; a page-típus jelek (ecommerce/content/marketing) elsőbbséget élveznek a feature-szavak felett (egy marketing-landing EMLÍTHET "Kanban" funkciót, attól még marketing); ÉS tagadás-szűrés ("nem/not/nincs + archetípus" eltávolítása osztályozás előtt). 6/6 valós briefen helyes
- **Megelőzés**: az archetípus-felismerés csak erős, page-szintű jelekre épüljön (ne incidentális feature-szavakra), és kezelje a tagadott említéseket

### [HIBA-FORGE-field-synonym] A frontend rossz szinonim mezőnevet kötött → "undefined" a felületen
- **Dátum**: 2026-06-13
- **Fájl**: agent-core.js (frontend-tartalom kapu, FIELD-SZERZŐDÉS)
- **Hibaüzenet**: — (a receptkártyán a cím "undefined", mert a JS recipe.name-et olvasott, de az API mezője 'title')
- **Gyökérok**: a mező-hűség kapu csak akkor jelzett, ha a JS a valódi mezők EGYIKÉT SEM használta; ha a többségét eltalálta, de a CÍM-mezőt rossz szinonimával kötötte (name vs title), átcsúszott → "undefined"
- **Javítás**: SZINONIMA-ÜTKÖZÉS detektálás — fogalom-csoportokra (title/name/label, description/desc/summary, price/cost/amount, image/img/photo, quantity/qty, email/mail, phone/tel): ha az API az egyik szinonimát adja, de a JS egy MÁSIK, nem létező szinonimát olvas → kitalált mező → újragenerálás a valódi mezőnévvel. Tesztelve 7/7 (a 'name API + .name' eset NEM ad fals jelzést)
- **Megelőzés**: a mező-hűség ne csak a "használ-e valódi mezőt"-et nézze, hanem a szinonima-tévesztést is (ez okozza a leggyakoribb "undefined"-ot)

### [HIBA-FORGE-array-seed] A tömb-oszlopos tábla (ingredients[]/steps[]) üresen maradt a seedelés után
- **Dátum**: 2026-06-13
- **Fájl**: agent-core.js (buildLiveSeederScript)
- **Hibaüzenet**: — (recipes 0 sor, /api/recipes [], pedig a seed "14 sor"-t írt; a NOT NULL ARRAY oszlopba a string-beszúrás elhasalt)
- **Gyökérok**: az élő-séma seeder nem kezelte a Postgres tömb-típust (data_type='ARRAY', udt_name=_text/_int4); a NOT NULL tömb-oszlopba szöveget próbált szúrni → a sor INSERT-je elhasalt (csendben, per-sor try/catch), a tábla üres maradt, a függő FK-táblák is
- **Javítás**: a seeder lekéri az udt_name-et is, és a tömb-oszlopra JS-tömböt ad (pg natívan kezeli): számtömb (_int*) → [1,2,3]; szövegtömb kontextus szerint (steps → ['1. lépés…'], ingredients → ['2 dl…'], tag → ['cimke1','cimke2']). Eredmény: recipes 7 sor, minden tábla feltöltve (35 sor)
- **Megelőzés**: a determinisztikus seedernek MINDEN oszloptípust kezelnie kell (tömb is); a csendes per-sor hiba elfedi a hiányzó adatot — a típus-lefedettség kritikus

### [FORGE-INJEKT-always-seed] A kész app MINDIG induljon példaadattal (AppLauncher auto-seed)
- **Dátum**: 2026-06-13
- **Fájl**: AppLauncher/main.js (runApp auto-seed)
- **Elv**: a felhasználó kérése — a Forge által készített szoftver MINDIG legyen feltöltve példaadattal, a későbbi indításokkor is. A Forge a QA-hurokban seedel (az átadott futó példány tele van), de egy újraindítás üres sémát hozhat létre.
- **Megoldás**: az AppLauncher node-supabase appoknál MINDEN sikeres indítás után lefuttatja a bundle-olt .henris/seed.js-t (idempotens — csak az üres táblákat tölti). Így az app a launcherből indítva SOHA nem üres. A seed.js a build során már elkészül és az app mellé kerül.
- **Megelőzés**: a "mindig legyen adat" garanciát az indító rétegben (launcher) is biztosítani kell, nem csak a build alatt

### [HIBA-FORGE-mqctnhrs] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/routes/filter.routes.ts
- **Hibaüzenet**: src/routes/filter.routes.ts(9,65): error TS2345: Argument of type 'string' is not assignable to parameter of type '"easy" | "medium" | "hard"'.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [FORGE-INJEKT-multi-ai-dispatcher] Diszpécser + több AI párhuzamos munka + kereszt-validáció
- **Dátum**: 2026-06-13
- **Fájl**: agent-core.js (workers pool, CONC, genFile(t,worker), peerReview, hullám-leosztás)
- **Elv (felhasználói NOT-NEGOTIABLE parancs)**: legalább 3 AI, legalább 6 párhuzamos agent; helyes sorrend; semmi ki ne maradjon; megfelelő minőség; NINCS véletlen párhuzamosítás; a munkás-AI-k validálják/javítják egymás munkáját.
- **Megoldás**: (1) DISZPÉCSER — a réteg-rang hullám-scheduler független fájlokat (azonos rang) PÁRHUZAMOSAN, egymástól ELTÉRŐ munkás-AI-hoz rendelve (körbe-osztás) generál; eltérő rang = függőség → sosem fut egyszerre (nincs véletlen párhuzamosítás). (2) WORKER-POOL — min. 3 (alapból 4) elérhető modell az Ollamából (qwen2.5-coder:14b, uncensored-14b, qwen3.5:9b, qwen2.5-coder:7b); CONC=max(6, pool*2). (3) PEER-VALIDÁCIÓ — minden logikai (backend) fájlt EGY MÁSIK AI néz át a szerződés (függőségek valódi exportjai) + brief + tanulságok ellen, és javítja; a javaslat csak guardFix+lint+balansz-kapun átengedve íródik felül (működőt elrontani tilos). (4) a kitartó hiányzó-pótló loop is más-más AI-val próbálja a megakadt fájlt.
- **Megelőzés/megjegyzés**: egy GPU-n a párhuzamos modell-hívásokat az Ollama VRAM szerint sorosítja (swap) — a sebesség hardver-függő, de a MINŐSÉG (eltérő modellek + kereszt-ellenőrzés) és a befejezés-biztonság valós nyereség.

### [HIBA-FORGE-frontend-undefined] Determinisztikus mező-átkötő ("undefined" cím + törött kép végleges megszüntetése)
- **Dátum**: 2026-06-13
- **Fájl**: agent-core.js (remapFrontendFields + a frontend-kapuba kötve)
- **Hibaüzenet**: — (a kártyákon "undefined" cím, törött `<img>`, mert a frontend recipe.name/recipe.image-et köt, az API title-t ad és nincs kép-mező)
- **Gyökérok**: a 14B kitalált mezőneveket köt; a kapu jelez, de az újragenerálás is melléköthet → a hiba megmarad. A modellre bízott javítás nem megbízható.
- **Javítás**: remapFrontendFields — a kitalált item-mezőt DETERMINISZTIKUSAN a VALÓDI kulcsra írja: (1) szinonima-csoport (name→title, category→cuisine, preparationTime→prep_time…), (2) camel/snake normalizálás (prepTime↔prep_time), (3) ha nincs valódi megfelelő ÉS kép-jellegű → a teljes `<img>` eltávolítása. A frontend-kapuban fut, MODELL NÉLKÜL; ami marad, azt a regen kezeli. Élőben igazolva: a "undefined" címek valódira váltak, a törött képek eltűntek.
- **Megelőzés**: a mező-kötés helyességét ne a modellre bízzuk — a valódi API-kulcsokra determinisztikusan átkötjük; nem létező kép-mezőnél nincs `<img>`

### [HIBA-FORGE-mqcwhjw9] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/routes/favorites.route.ts
- **Hibaüzenet**: src/routes/favorites.route.ts(31,47): error TS2345: Argument of type '{ user_id: any; recipe_id: any; }' is not assignable to parameter of type 'Favorite'.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqcwhjwc] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/services/weekly-plan.service.ts
- **Hibaüzenet**: src/services/weekly-plan.service.ts(15,29): error TS2339: Property 'user_id' does not exist on type 'WeeklyPlan'.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqcwhjwe] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/routes/weekly-plans.route.ts
- **Hibaüzenet**: src/routes/weekly-plans.route.ts(27,58): error TS2554: Expected 1 arguments, but got 2.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát

### [HIBA-FORGE-mqcwhjwg] új fordítási hibaosztály (node-supabase)
- **Dátum**: 2026-06-13
- **Fájl**: src/routes/weekly-plans.route.ts
- **Hibaüzenet**: src/routes/weekly-plans.route.ts(60,13): error TS1345: An expression of type 'void' cannot be tested for truthiness.
- **Gyökérok**: generálási hiba
- **Javítás**: célzott fájl-újragenerálás a javítókörben (automatikusan)
- **Megelőzés**: lásd a hibaüzenetet — kerüld ezt a mintát
