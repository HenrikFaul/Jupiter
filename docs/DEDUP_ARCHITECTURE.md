# Jupiter — Deduplication & Similarity Detection Subsystem

**Status:** living blueprint. Sections marked _(shipped)_ describe code already in the repo;
_(planned)_ describes the target end-state this document commits the team to.

This is the engineering design for Jupiter's file-intelligence engine: a persistent, multi-layer,
type-aware system that continuously indexes all observable storage and immediately tells the user
when a newly arrived file already exists on the device or is highly similar to something they have.

---

## 1. Executive summary

Jupiter must behave like a **persistent local file-intelligence system, not a one-time scanner**.
A file that is downloaded, captured, received, copied, or moved must be checked against everything
already on the device _as soon as it is observable_, and the user notified with a confidence-graded,
explainable verdict.

Why the naive approaches fail:

- **A single hash is insufficient.** SHA-256 answers exactly one question — "are these byte streams
  identical?" It says nothing about the same photo re-saved as JPEG, a PDF re-exported, or an APK
  bumped one version. Re-compression changes every byte, so a content hash reports "unrelated."
  Near-duplicate detection is a fundamentally different problem and needs different descriptors
  (perceptual hashes, structural fingerprints, embeddings).
- **Type-specific logic is required.** The strongest duplicate signal for an image (perceptual
  hash) is meaningless for an APK (where signer + package + versionCode dominate) and wrong for a
  PDF (where rendered-page + text-layer hashes matter). One comparison policy across all types is
  guaranteed to be simultaneously too loose and too strict.
- **The system must be continuously indexed.** If descriptors are computed only when the user opens
  a "find duplicates" screen, then (a) the first query is catastrophically slow, and (b) a file that
  arrived while the app was closed is invisible. Descriptors must be **precomputed before any query
  occurs**, kept warm, and updated incrementally.
- **New files must be checked immediately against the existing index.** The product promise is "you
  already have this" _at arrival time_. That requires an always-current index to compare against and
  a low-latency arrival pipeline.
- **Thresholds and decision tiers are essential.** Similarity is a continuum. Collapsing it to a
  boolean produces either false-positive spam (deleting a user's distinct photos) or silent misses.
  Per-type, per-tier thresholds turn a raw score into a safe action.
- **Explainability matters.** "These match because their dHash Hamming distance is 3 and sizes are
  within 2%" is debuggable and earns user trust; a bare "duplicate" is neither.

**Key principle:** _exact-duplicate detection and semantic-similarity detection are different
problems and are modeled separately._ Exact identity is a lookup (hash equality). Similarity is a
scored, fused, thresholded decision. The engine keeps them as distinct layers whose outputs are
combined only in the fusion stage, with exact identity always able to dominate.

**Current state in Jupiter (shipped):** Room-backed metadata index with a generation/state machine
(`FileIndexEntry`, `IndexState`), a foreground survey (`IndexingWorker`), exact content-hash dedup
(`FileIndexRepository.findContentDuplicates`, SHA-1), perceptual dHash for images
(`PerceptualHash` + `PerceptualHashBackfillWorker`), and — the fix that makes arrival detection
actually fire — a **checkpoint-based MediaStore delta reconciler** (`DedupReconciler`) plus a shared
`DuplicateDetector`. The sections below both document that and lay out the full target design.

---

## 2. System architecture overview

The engine is a layered pipeline. Each layer has one responsibility and a typed contract with its
neighbours; data flows forward, control (retry/backpressure) flows via WorkManager and the job table.

| Layer | Responsibility | Input | Output | Failure mode | Retry | Concurrency |
|---|---|---|---|---|---|---|
| Storage observation | Notice that _something_ changed | `ContentObserver` / `FileObserver` / periodic tick | debounced "reconcile now" signal | observer dies with process | reconcile catch-up on next foreground | single observer + WM-coalesced |
| File discovery | Enumerate what is new/changed since checkpoint | checkpoint (epoch s) | list of `FileItem` deltas | provider hiccup → 0 rows | next tick | one cursor, IO dispatcher |
| Descriptor extraction | Compute hashes/fingerprints/embeddings | `FileItem` + bytes | descriptor bundle | corrupt file → `UNHASHABLE` sentinel | bounded; sentinel prevents re-loop | batched, IO-bound pool |
| Type normalization | Map extension/MIME → canonical `NormalizedType` | metadata | enum + type-config | unknown → `BINARY` fallback | n/a | pure |
| Candidate retrieval | Produce a _small_ candidate set | descriptor | ≤K candidates | over-broad bucket → slow | n/a | indexed DB reads |
| Similarity scoring | Per-layer similarity for a pair | two descriptors | `LayerScores` | one layer errors → omit + lower confidence | per-layer | CPU pool |
| Decision engine | Fuse → tier + confidence + explanation | `LayerScores` + type weights | `DedupDecision` | veto conflict → REVIEW | n/a | pure |
| Index persistence | Durable store of all of the above | records | Room rows | DB full/corrupt → destructive rebuild | WM | Room transactions |
| Background processing | Schedule/retry/split all work | triggers | worker runs | Doze/kill | WM backoff | unique-work coalescing |
| Notification / UI | Surface alerts + status | decisions | notification + `Flow` | notif blocked → UI only | n/a | main-safe |
| Recovery / reconciliation | Detect + repair index drift | checkpoints | corrective jobs | drift undetected → stale | periodic | serialized |
| Backend extension _(optional)_ | Cross-device sync | descriptors | remote index | offline | queue | out of band |

**Record separation (canonical vocabulary).** These are distinct rows with distinct lifecycles;
conflating them is the #1 source of dedup bugs:

- **Canonical storage record** (`FileRecordEntity` / today's `FileIndexEntry`): identity + metadata
  of a file that exists. One per path.
- **Raw descriptor record** (`DescriptorEntity` + hash/fingerprint tables): computed features. May
  lag the storage record (extraction is async); carries a schema version.
- **Similarity score record** (`SimilarityScoreEntity`): the scored comparison of a _pair_.
  Derived, cacheable, invalidated when either descriptor changes.
- **Dedup decision record** (`DedupDecisionEntity`): the tiered verdict for a pair + action taken +
  user feedback. Survives even if a file is later deleted (audit).
- **User-visible dedup event**: the notification/alert. Ephemeral, de-duplicated against the
  decision record so the same pair never re-alerts without cause.

---

## 3. File and file-type taxonomy

Canonical types (`NormalizedType`), resolved from MIME first, extension second, magic-byte sniff
for ambiguous binaries:

`IMAGE, VIDEO, PDF, OFFICE_DOC, PLAIN_TEXT, CODE, APK, AUDIO, ARCHIVE, BINARY`.

| Type | Exact-dup signal | Near-dup signal | Semantic signal | Key metadata | Structural descriptor | Fingerprint family | Expensive fallback | Unreliable metric | Common false positive |
|---|---|---|---|---|---|---|---|---|---|
| Image | content hash | **dHash/pHash**, histogram | CLIP-style embedding | dims, EXIF, orientation | dims+orientation | dHash 64-bit | full pixel diff | filename, mtime | same subject ≠ same file; burst shots |
| Video | content hash | keyframe hashes, audio fp | frame-sequence embed | duration, codec, WxH, fps | container/track table | sampled-frame dHash + chromaprint | ffmpeg frame walk | size (re-encode changes it) | same scene, different cut |
| PDF | content hash | rendered-page hash, text SimHash | doc embedding of text | page count, title/author | page tree + object count | per-page render dHash + text MinHash | OCR of scanned pages | text length | template docs (invoices) |
| Office doc | content hash (zip is unstable) | canonical text SimHash | doc embedding | author, title, page/word count | OOXML part tree | text MinHash | headless render | zip byte hash (re-save reorders) | shared template |
| Plain text | content hash | SimHash/MinHash on normalized text | sentence embedding | encoding, line count | canonicalized text | 64-bit SimHash | full diff | whitespace/CRLF | boilerplate/licenses |
| Code | content hash | token SimHash (comments/format-insensitive) | code embedding | language | AST-lite token stream | token MinHash | tree diff | line count | generated files, vendored deps |
| APK | **signer + packageName + versionCode** | manifest + resource fp | — (usually versioning, not semantic) | packageName, versionCode, signer | manifest + dex/arsc structure | signer SHA + manifest hash | dex method-set jaccard | file size | split APKs of same app |
| Audio | content hash | **chromaprint** acoustic fp | — | duration, codec, bitrate, tags | container + track | chromaprint + duration bucket | full decode fp | bitrate/size (re-encode) | same song, different master |
| Archive | content hash | member-tree fingerprint (name+size+CRC set) | — | archive type, entry count | central directory | sorted (path,size,crc) MinHash | nested descriptor extraction | archive byte hash (recompress) | same files, different compression |
| Binary | content hash | multi-position sample hashes | — | size, magic | header bytes | first/mid/last chunk hashes | full hash | extension | none (conservative) |

**Transformation policy** (how the engine _classifies_ common relationships):

| Situation | Exact? | Relationship | Engine behavior |
|---|---|---|---|
| Same file, different name | yes | Tier 5 | content hash matches → alert "duplicate" |
| Recompressed image (JPEG↔PNG, quality change) | no | Tier 3–4 near | dHash near → "similar image" alert |
| Screenshot of a photo (crops, adds UI chrome) | no | Tier 1–2 | usually below dHash threshold → REVIEW at most, never auto |
| Re-encoded video | no | Tier 3 | keyframe+audio fp → "similar", never exact |
| Exported/re-saved document | no (bytes differ) | Tier 3–4 | text SimHash + render hash |
| Document version update | no | Tier 2–3 "related family" | flagged as version, not duplicate |
| App version update (same signer/pkg, higher versionCode) | no | "versioned family" | never auto-duped; grouped |
| Archived copy of same content | depends | Tier 4–5 | member-tree fingerprint |

---

## 4. Multi-layer similarity model

Seven layers. No single layer decides; fusion (Layer 7) combines them with type-aware weights and
hard vetoes.

| # | Layer | Measures | Input | Output | Cost | Stability under transforms | FP risk | FN risk | Contribution |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Binary identity | byte equality | full bytes | hash equality | med (streamed) | brittle (any edit breaks) | ~0 | high for near-dups | dominant when present |
| 2 | Metadata | attribute agreement | index row | normalized field matches | ~0 | medium | high alone | low | prefilter + soft evidence |
| 3 | Structural | format-internal layout | parsed structure | structure similarity | low–med | high | low | med | strong type-specific evidence |
| 4 | Chunk/sample | sampled-byte agreement | N sampled ranges | sample-hash overlap | low | medium | med | med | fast approximation only |
| 5 | Perceptual | visual/acoustic structure | decoded media | fingerprint distance | med | **high** | med | low | high for media |
| 6 | Semantic | meaning/content family | embeddings/OCR | cosine similarity | high | high | med | low | "same content, different form" |
| 7 | Fusion | final verdict | all above | tier + confidence + why | ~0 | — | tunable | tunable | the decision |

### 4.1 Binary identity layer

- **xxHash64** — fast, non-crypto; a **prefilter** over same-size candidates to cheaply reject
  non-identical files before a crypto hash.
- **SHA-256** — canonical exact-identity key (Jupiter ships SHA-1 today; SHA-256 is the migration
  target — collision-safe and the industry default).
- **BLAKE3** — optional faster crypto hash for large files (parallel, tree-structured).

Store the strong hash as the exact-dup lookup key (indexed column). Content hashing alone is
insufficient for near-dups because it is a discontinuous function of the bytes: a 1-byte change or a
re-encode yields a completely different hash, so it can only answer "identical," never "similar."

### 4.2 Metadata layer

Compared fields: size, MIME, extension, timestamps, EXIF (image), resolution, duration, codec, page
count, packageName, signer, author/title, camera model, GPS (where lawfully available), OCR
confidence, language. **Normalization before comparison** is mandatory: lowercase extension/MIME;
size compared as a ratio (`min/max`) not equality (re-encodes shift it); timestamps bucketed
(second-vs-millisecond mismatches are endemic — normalize to millis); strip volume prefix from
paths; canonicalize EXIF orientation. Metadata is a **prefilter and soft evidence**, never a sole
decider — filename and mtime are the two least trustworthy signals and must never gate a decision.

### 4.3 Structural layer

Format-aware, compares internal layout not bytes:

- **Image:** normalized EXIF + orientation + dimensions.
- **PDF:** page-tree shape + object count + presence/length of text layer + per-page render hash.
- **Video:** container + track layout + sample-table shape (duration, WxH, codec, fps).
- **Audio:** codec + normalized tags + duration bucket.
- **APK:** manifest (package, versionCode, min/target SDK, permission set) + signer cert digest +
  dex/arsc structural digest.
- **Text/code:** canonicalized structure (normalized whitespace, stripped comments for code).

### 4.4 Chunk / sample fingerprint layer

Sample each file at stable offsets and hash the samples — a fast approximation of identity for large
binaries without reading the whole file:

- first chunk, last chunk, middle chunk, plus 3–8 pseudo-random offsets **seeded by file size** (so
  the same file always samples the same positions).
- chunk size: 4–64 KiB scaled by file size; for files ≤ 3× chunk size, hash the whole file (sampling
  buys nothing).
- convert each sample to xxHash64; the fingerprint is the ordered set.
- **type-safe offsets:** for structured formats, avoid sampling only headers (which are similar
  across unrelated files of a type) — offset into the payload region.

Use as a **candidate prefilter**, never a final decision: matching samples with a differing size is a
veto, not a confirmation.

### 4.5 Perceptual layer

For images/video/PDF-render/screenshots: **dHash** (difference hash — Jupiter's shipped primary),
**pHash** (DCT-based, more crop/scale tolerant), **aHash** (cheap average hash, weak), color
histograms, edge/shape descriptors, video keyframe hashes, PDF rendered-page hashes. Visual
similarity survives resizing/recompression/format conversion because it is computed from the
**luminance structure** after downscaling to a tiny grid — exactly the information those transforms
preserve. Perceptual **complements** binary identity: identity catches exact copies with zero false
positives; perceptual catches the re-encodes identity misses. Combine methods by requiring agreement
(e.g. dHash near **and** histogram correlation > 0.9) for higher-confidence tiers.

### 4.6 Semantic layer _(planned)_

Text/OCR embeddings (documents, code), image embeddings (MobileCLIP-class, on-device), frame-sequence
embeddings (video), APK resource/manifest similarity. Semantic similarity is **not** duplicate
detection — it detects "same topic / same content family / same document in a different form." It
feeds REVIEW tiers and grouping, never silent auto-dedup.

### 4.7 Decision fusion layer

Combines all signals: normalize each layer to `[0,1]`, apply **type-specific weights**, sum to a
`[0,100]` raw score, map to a tier, calibrate confidence. **Hard vetoes** (e.g. APK signer mismatch,
size ratio far from 1 for a claimed exact match) override any raw score down to REVIEW/unrelated.
**Soft votes** (metadata agreement) nudge within a tier. Conflicts (strong layer vs several weak) are
resolved by a fixed precedence: `exact-hash > type-primary-layer > structural > perceptual >
sample > metadata`. Unresolved conflict → REVIEW (never silent auto-action).

---

## 5. Descriptor generation pipeline

Runs on: initial full scan, incremental delta, new-file arrival, modification, copy/move, download,
and schema-version reindex. Every file gets a descriptor bundle.

### 5.1 Descriptor fields

canonical file key · storage URI · path (if lawful) · size · MIME · extension · created/modified ·
crypto hash · fast hash · sample hashes · normalized type · structural metadata · perceptual features
· OCR/text summary · embeddings (where applicable) · checksum status · **descriptor schema version** ·
extraction timestamp · **extraction confidence** · last-verified timestamp · **extraction state**
(`PENDING/PARTIAL/COMPLETE/UNHASHABLE/FAILED`).

The **extraction state + schema version** are load-bearing: they let the pipeline resume partial work,
skip up-to-date descriptors, and mass-reindex when the descriptor format evolves — without ever
reprocessing everything blindly.

### 5.2 Type-specific extraction

- **Images:** dimensions, orientation, EXIF, color histogram, dHash (shipped) → pHash/aHash, optional
  image embedding.
- **Videos:** duration, codec, container, WxH, fps, bitrate, keyframe fingerprints, audio fingerprint,
  scene-change features, thumbnail fingerprints. (`MediaMetadataRetriever` for frames/metadata.)
- **PDFs:** page count, text layer, OCR (ML Kit) when no text layer, rendered-page fingerprints
  (`PdfRenderer`), layout fingerprints, doc metadata.
- **Text/code:** normalized text, language, token stats, n-grams, SimHash/MinHash, embeddings.
- **APKs:** packageName, versionCode, signer cert digest, manifest features, permissions, dex/resource
  fingerprints, icon dHash. (`PackageManager.getPackageArchiveInfo` + `PackageInfo.signingInfo`.)
- **Audio:** duration, codec, bitrate, tags, chromaprint acoustic fingerprint.
- **Archives:** archive type, member listing, per-member `(path,size,crc)`, archive-tree MinHash;
  nested descriptors only for small/safe members.
- **Misc binaries:** safe generic metadata, chunk fingerprints, conservative fallback (exact + sample
  only; never claim "similar").

Jupiter today ships metadata + SHA-1 content hash + image dHash. The remaining extractors are the
prioritized backlog (videos and PDFs next, per user-visible value).

---

## 6. Storage indexing strategy

### 6.1 Index lifecycle

| Phase | Trigger | Behavior |
|---|---|---|
| Initial scan | first launch / index EMPTY | MediaStore seed + filesystem reconciliation walk → full metadata index (`IndexingWorker`) |
| Incremental scan | survey re-run | re-stamp generation; only changed rows re-extract |
| Delta update | ContentObserver / arrival | reconciler processes files past checkpoint |
| Periodic reconciliation | 12 h `IndexRefreshKickWorker` | re-ensure survey + dedup reconcile |
| Stale cleanup | end of successful survey | generation sweep deletes rows not re-seen |
| Schema migration | descriptor schemaVersion bump | targeted reindex of stale-schema rows |
| Partial reindex | per-type feature added | reindex only that type (e.g. dHash backfill) |
| Full reindex | corruption / DB version bump | destructive rebuild (index is a cache) |

Full rescans are avoided by (a) the generation model (only re-stamp, don't re-hash unchanged rows),
(b) identity-preserved hashes/fingerprints (`identityUnchanged` keeps a hash when size+mtime match),
and (c) the checkpoint (only files newer than the high-water mark are examined for dedup). Drift is
detected by the periodic reconcile comparing MediaStore's max-date to the checkpoint and the survey's
`lastCompleteGeneration`. App upgrades bump `scannerVersion`/descriptor `schemaVersion`, triggering
targeted reindex, not a wipe.

### 6.2 Full storage indexing requirement _(shipped)_

The app indexes everything it may observe, builds descriptors **proactively** (not on first query),
keeps the index warm via the periodic kicker + foreground ensure, and **does not wait for the user to
open a dedup screen**. Completeness is a Room state (`IndexState`: EMPTY/RUNNING/COMPLETE/…), never a
row count, so a partial index is never treated as done.

---

## 7. Continuous update and observation strategy

**No single observer catches every case**, so Jupiter uses a hybrid of _signals_ + a _reconciler_:

| Mechanism | Catches | Misses | Role |
|---|---|---|---|
| MediaStore query (delta since checkpoint `_id`) | anything MediaStore scanned (media, downloads, docs) | app-private dirs; not-yet-scanned files | **source of truth** for arrivals |
| `ContentObserver` on `MediaStore.Files` | changes while process alive | changes while process dead; exact URI unreliable | **trigger** only (kick reconcile) |
| `FileObserver` _(targeted, planned)_ | writes to specific watched dirs (e.g. Download) | recursive cost; killed with process | low-latency hint for hot dirs |
| Periodic reconcile (12 h) | anything added while closed for long | — | safety net |
| On-foreground reconcile | anything since last foreground | — | primary catch-up |
| On-boot/unlock _(optional)_ | early catch-up | battery cost | opt-in only |

**The critical design decision (the fix):** the ContentObserver is a **pure signal**, never the data
source. On many devices it fires with the bare collection URI (or no URI), so resolving "the changed
file" from it picks an arbitrary row. Instead, any signal — observer, foreground, periodic — kicks
`DedupReconciler`, which asks MediaStore for **everything newer than the persisted checkpoint** and
processes exactly those. This is what makes "you already have this" fire for a file downloaded while
Jupiter was closed — the previous design could not, because the observer wasn't even running.

### 7.1 Event-driven workflow (shipped)

```
onSignal (observer | foreground | periodic):
    enqueue DedupReconcileWorker (unique KEEP, expedited)   // coalesces bursts

DedupReconciler.reconcile():
    if !indexingEnabled: return
    if !hasAllFilesAccess(): return              // never baseline before storage access
    ck = checkpointStore.getId()
    if ck == 0:                                  // baseline: never retro-alert the library
        maxId = newFileSource.maxObservedId()
        if maxId > 0: checkpointStore.setId(maxId)  // 0 = empty/unreadable → retry later, no poison
        return
    since = ck
    loop:
        batch = newFileSource.queryNewSince(since, BATCH=200)   // _id-ascending
        if batch empty: break
        for f in batch:
            detector.onFileArrived(f.item)       // index + exact + perceptual + alert
        since = advance(since, max(batch ids))   // monotonic; ids unique → gap-free
        checkpointStore.setId(since)
        if batch < BATCH or inspected >= 2000: break
```

The checkpoint is keyed on MediaStore `_id` (unique, strictly increasing) rather than a
1-second-granularity date, so a bulk import of hundreds of files in the same second paginates with
`_id > checkpoint` cleanly — no straddle-the-page-boundary skip. Baseline is established only when a
real id is observed (never pinned to a low value before storage permission, which would later
re-alert the whole library).

`DuplicateDetector.onFileArrived` = index the file → exact content-hash check (hash same-size
candidates on demand) → if none and image, perceptual dHash near-check → emit alert + notification on
the first match kind; otherwise the file is now indexed for future comparisons.

### 7.2 Change handling

| Change | Handling |
|---|---|
| Creation | reconciler delta (`_id` > checkpoint) → index + dedup |
| Modification (in place) | picked up by the survey re-stamp (identity changed → hashes cleared); NOT by the `date_added`-keyed reconciler — edited-in-place dedup is a documented future refinement |
| Rename | path change; `onMovedOrRenamed` rewrites subtree, preserves hashes |
| Move | same as rename (atomic subtree rewrite) |
| Delete | survey generation sweep or explicit `removeByPath` |
| Permission change | reconcile no-ops safely (0 rows) |
| Metadata-only change | date_modified delta; cheap re-stamp |
| App-generated new file | reconciler catches on next signal/foreground |
| Third-party app write | MediaStore scan → reconciler delta |

---

## 8. Candidate retrieval strategy

Never compare all-vs-all (O(n²) over 40k+ files is fatal). Reduce to a tiny candidate set:

1. **Type partition** — only compare within `NormalizedType` (image↔image).
2. **Exact-hash bucket** — indexed lookup by content hash → O(1) exact dups (shipped via `byHash`).
3. **Size bucket** — exact dups share byte size; `filesOfSize(size)` narrows same-size candidates
   before hashing (shipped in `findContentDuplicates`).
4. **Perceptual bucket / LSH** — for near-dups, bucket dHashes by high-order bit prefixes (LSH bands)
   so only hashes in the same band are Hamming-compared. Today Jupiter scans the full fingerprint set
   in memory (fine at ≤ tens of thousands of 64-bit longs); LSH banding is the scale-up.
5. **ANN / vector search** _(planned, with embeddings)_ — on-device approximate nearest neighbour
   (e.g. HNSW/ScaNN-lite) for semantic candidates.
6. **Recent-change priority** — the reconciler only ever considers files past the checkpoint, so
   arrival comparisons are naturally scoped.

Balance recall/precision: cheap buckets (type+size+hash-prefix) maximize recall of the candidate set;
expensive per-pair scoring restores precision. A newly indexed file is inserted into all buckets at
index time so it is immediately findable.

---

## 9. Scoring and fusion engine

_(shipped: `data/index/dedup/SimilarityScorer` + `SimilarityModel` — the fusion, tiers, per-type
weights, vetoes, confidence, and explanation described below are implemented and unit-tested;
`TextSimHash` and `ApkComparator` provide the text/code and APK layer signals. What remains is
wiring the per-type descriptor EXTRACTORS that feed the structural/perceptual layers for video /
PDF / audio / archive.)_

### 9.1 Score formula

```
score(a, b, type):
    L = {}                                  // per-layer similarity in [0,1]
    L[identity]   = 1 if hash(a)==hash(b) else 0
    L[metadata]   = normalizedFieldAgreement(a, b)      // size-ratio, mime, etc.
    L[structural] = typeStructuralSim(a, b, type)
    L[sample]     = sampleHashOverlap(a, b)
    L[perceptual] = 1 - hamming(dHash(a), dHash(b)) / 64
    L[semantic]   = cosine(embed(a), embed(b))          // 0 if absent

    // Hard veto: exact identity dominates.
    if L[identity] == 1: return (score=100, tier=EXACT, conf=1.0, why="content hash equal")

    W = weightsFor(type)                    // Σ nonzero weights normalized to 100
    raw = Σ_layer W[layer] * L[layer]
    // Vetoes pull down, never up.
    if typeVetoViolated(a, b, type): raw = min(raw, REVIEW_CAP)   // e.g. APK signer mismatch
    conf = calibrate(raw, availableLayers, agreementCount)
    return (raw, tierFor(type, raw), conf, explain(L, W))
```

Base weight envelope (per the brief, refined): identity = dominant/override; metadata 0–20;
structural 0–20; sample 0–15; perceptual 0–30; semantic 0–20 — **redistributed per type** (§9.3).

### 9.2 Conflict handling

| Conflict | Resolution |
|---|---|
| exact hash differs, perceptual high | NOT exact; near-dup tier (correct for re-encodes) |
| metadata matches, structure differs | structure wins; drop to REVIEW (metadata is weak) |
| sample matches, size differs | **veto** — cannot be exact; near at most |
| one strong layer vs many weak | precedence order decides; log the conflict |
| type veto vs high raw | veto caps the score (signer mismatch never "same app") |

### 9.3 Type-aware weighting

| Type | identity | metadata | structural | sample | perceptual | semantic | Hard veto |
|---|---|---|---|---|---|---|---|
| Image | override | 10 | 10 | 5 | **55** | 20 | size ratio wildly off for "exact" |
| Video | override | 15 | 20 | 5 | **45** (keyframe+audio) | 15 | duration mismatch > 2% |
| PDF | override | 10 | 25 | 5 | **35** (render) | 25 | page-count mismatch for "exact" |
| Text/Code | override | 10 | 20 | 10 | 0 | **60** | — |
| APK | override | 20 | **60** (signer+manifest) | 5 | 15 (icon) | 0 | **signer or package mismatch** |
| Audio | override | 20 | 25 | 5 | **45** (chromaprint) | 5 | duration mismatch > 2% |
| Archive | override | 15 | **55** (member tree) | 15 | 0 | 15 | member-set disjoint |
| Binary | override | 20 | 20 | **55** | 0 | 5 | size mismatch |

---

## 10. Decision tiers and thresholds

| Tier | Name | Score | Confidence | UI label | Auto-flag | User confirm | Silent index | Review queue |
|---|---|---|---|---|---|---|---|---|
| 0 | Unrelated | 0–19 | any | — | no | no | yes | no |
| 1 | Weakly related | 20–39 | low | "maybe related" | no | no | yes | no |
| 2 | Possibly similar | 40–59 | med | "possibly similar" | no | optional | yes | yes |
| 3 | Probable duplicate | 60–74 | med–high | "probably a duplicate" | soft | yes | yes | yes |
| 4 | Very likely duplicate | 75–89 | high | "very likely duplicate" | yes | yes (before delete) | yes | yes |
| 5 | Exact duplicate | 90–100 (or identity) | ~1.0 | "exact duplicate" | yes | yes (before delete) | yes | optional |

Auto-**flag** never means auto-**delete**: destructive actions always require confirmation and always
route through the Recycle Bin.

### 10.1 Type-specific threshold tables

Perceptual thresholds are **Hamming distance / 64** (lower = more similar); score thresholds are the
fused `[0,100]`.

| Type | Exact | Near-dup | Review | Deep-verify | Mismatch veto |
|---|---|---|---|---|---|
| Image | content hash eq | dHash ≤ 8 | dHash 9–14 | dHash ≤ 6 → confirm w/ pHash+histogram | size ratio < 0.2 |
| Video | content hash eq | keyframe-set ≥ 0.85 & audio fp ≥ 0.9 | 0.7–0.85 | full keyframe walk | duration Δ > 2% |
| PDF | content hash eq | render-page ≥ 0.9 & text ≥ 0.85 | 0.7–0.9 | OCR + per-page render | page count differs |
| Text/Code | content hash eq | SimHash ≤ 3 (of 64) | 4–8 | full token MinHash | language differs |
| APK | signer+pkg+versionCode eq | signer+pkg eq, version differs → "update" | manifest ≥ 0.9 | dex jaccard | **signer differs → never** |
| Audio | content hash eq | chromaprint ≥ 0.95 | 0.85–0.95 | full-decode fp | duration Δ > 2% |
| Archive | content hash eq | member-tree ≥ 0.95 | 0.8–0.95 | nested descriptor | member-set jaccard < 0.5 |
| Binary | content hash eq | sample-set eq & size eq | — | full hash | size differs → not exact |

---

## 11. Persistent data model and Room schema

Current shipped schema: `FileIndexEntry` (+ `perceptualHash`), `IndexState`, DB version 3. The target
normalized model below separates the five record kinds (§2). Migration is destructive-fallback today
(index is a cache); once descriptors become expensive to recompute (embeddings), switch to real
`Migration`s keyed on `schemaVersion`.

| Entity | PK | FKs | Unique | Indexes | Update freq | Retention | Migration notes |
|---|---|---|---|---|---|---|---|
| FileRecordEntity | fileId | — | storageUri | path, size, type, mediaStoreId, lastObservedAt | on observe | until deleted+swept | stable core |
| DescriptorEntity | descriptorId | fileId | fileId+schemaVersion | fileId, extractionState, schemaVersion | on extract | with file | bump schemaVersion → reindex |
| MetadataEntity | fileId | fileId | — | normalized fields | on extract | with file | additive columns |
| HashEntity | fileId | fileId | — | **sha256**, xxhash64 | on hash | with file | add BLAKE3 additively |
| SampleFingerprintEntity | fileId | fileId | — | sampleSetHash | on hash | with file | offset-scheme versioned |
| PerceptualFingerprintEntity | fileId | fileId | — | **dhash prefix (LSH)** | on extract | with file | multi-hash columns |
| SemanticFingerprintEntity | fileId | fileId | — | ANN index sidecar | on embed | with file | model-versioned |
| CandidateEntity | (fileId,candidateId) | both | pair | fileId | transient | short TTL | derived, disposable |
| SimilarityScoreEntity | scoreId | fileIdA, fileIdB | (A,B,scorerVersion) | A, B, tier | on score | until either descriptor changes | scorerVersion invalidates |
| DedupDecisionEntity | decisionId | fileIdA, fileIdB | (A,B) | tier, userReviewed | on decision | **audit — keep** | never destructive-drop |
| ScanCheckpointEntity | checkpointId | — | — | — | per reconcile | keep latest | scannerVersion |
| ObservationEventEntity | eventId | — | — | observedAt, state | per event | ring-buffer TTL | debug only |
| ProcessingJobEntity | jobId | fileId | — | state, priority, nextAttemptAt | per job | until done | resume after death |
| DescriptorSchemaVersionEntity | schemaVersion | — | — | — | on upgrade | keep | drives reindex |
| UserFeedbackEntity | feedbackId | fileIdA,fileIdB | (A,B) | — | on feedback | **keep — training** | never drop |
| IgnorelistEntity | id | — | (fileId or hash) | — | on user action | keep | honored by decision engine |

### 11.1 Example entity field sets

The brief's field sets are adopted verbatim as the target (`FileRecordEntity`, `DescriptorEntity`,
`HashEntity`, `SimilarityScoreEntity` with `layerScores` JSON + `explanation`, `DedupDecisionEntity`
with `suppressAlert`, `ScanCheckpointEntity` with `lastMediaStoreVersion`/`scannerVersion`). The
schema supports: **incremental update** (per-file rows, generation stamp), **fast lookup** (indexed
hash + dHash-prefix + size), **candidate retrieval** (buckets are indexed columns), **confidence
scoring** (`layerScores` persisted for re-fusion without recompute), and **historical audit**
(decision + feedback rows survive file deletion).

---

## 12. WorkManager / Coroutine pipeline

`CoroutineWorker` is preferred: work is suspendable and cancellable, integrates with Room's suspend
DAOs and Flow, and cooperates with structured concurrency (a cancelled worker cancels its whole
subtree). Work is **chunked** (batches of 100–200) so each run is a bounded slice; **retried** with
exponential backoff on transient failure; **split** across workers so no single job is unbounded;
**resumed** after process death via the checkpoint + `ProcessingJobEntity` state (idempotent by
file identity).

| Worker | Trigger | Input | Output | Retry | Constraints | Runs when | Never when |
|---|---|---|---|---|---|---|---|
| InitialIndexWorker (`IndexingWorker`, shipped) | first launch / EMPTY | volume root | full metadata index | retry on IO | foreground service | index EMPTY/DIRTY | already COMPLETE & fresh |
| IncrementalReindexWorker | survey re-run | generation | re-stamped index | retry | none | periodic/manual | disabled |
| FileDescriptorWorker (`PerceptualHashBackfillWorker`, shipped for images) | after survey / app start | missing-descriptor rows | descriptors | retry (bounded) | battery-not-low | descriptors missing | disabled |
| SimilarityScoringWorker _(planned)_ | new descriptor | candidate pairs | scores+decisions | retry | none | candidates pending | — |
| ReconciliationWorker (`DedupReconcileWorker` + `IndexRefreshKickWorker`, shipped) | foreground/observer/12h | checkpoint | alerts + advanced checkpoint | retry | none (expedited) | signal received | disabled |
| CleanupWorker | end of survey | generation | swept stale rows | retry | none | after COMPLETE | mid-survey |
| AlertDispatchWorker | decision emitted | decision | notification | no-retry | POST_NOTIFICATIONS | new decision | suppressed pair |

### 12.1 Coroutine pipeline

```
suspend fun processArrival(item):                 // per new file
    val norm = normalize(item)                     // discover → normalize
    indexRepository.indexFile(item)                // persist canonical record
    val desc = extractDescriptors(norm)            // hash · sample · perceptual · (embed)
    val candidates = retrieveCandidates(desc)      // type+size+hash+LSH buckets
    val scored = candidates.map { score(desc, it) }
    val decision = fuse(scored)                    // tier + confidence + why
    persist(decision)
    if (decision.tier >= PROBABLE && !suppressed(pair)) alert(decision)
```

Stages compose as suspend functions; batch stages use `flow { }` + `flowOn(io)` with a bounded
`buffer()` for backpressure.

### 12.2 Cancellation and resumption

Cancellation propagates via structured concurrency (`ensureActive()` in every loop — shipped in the
survey, backfill, and reconciler). Partial work resumes because progress is durable: the **checkpoint**
(reconciler), the **generation stamp** (survey), and the **`UNHASHABLE`/state markers** (extraction)
mean a re-run continues rather than restarts. Idempotency is by file identity (path + size + mtime):
re-processing the same file yields the same descriptor and the same decision, and the monotonic
checkpoint prevents re-alerting.

---

## 13. Android implementation details

Stack: Kotlin, Coroutines/Flow/StateFlow, Room (KSP), WorkManager, MediaStore/ContentResolver,
ContentObserver, FileObserver (targeted), ExifInterface, `MediaMetadataRetriever`/`PdfRenderer`/ML Kit
for media/PDF (planned), Hilt DI, scoped storage.

### 13.1 Storage & observation on Android

- **Query MediaStore collections** (`MediaStore.Files`) for the delta since the checkpoint — the
  robust arrival source (shipped: `MediaStoreIndexSource.queryNewSince`).
- **Register a ContentObserver** as a _trigger_ only (shipped: `DownloadIndexObserver`).
- **Rescan checkpoints** persisted in DataStore (shipped: `dedupCheckpointSeconds`).
- **Handle delayed MediaStore indexing** — a just-written file may not appear immediately; the
  periodic + foreground reconcile re-checks, and `IS_PENDING` rows are skipped until published.
- **Handle other-app writes** — MediaStore scans them; the reconciler catches them regardless of who
  wrote them.
- **Messaging/camera/download flows** — all land in scanned shared dirs, so all are caught by delta
  reconciliation without per-source hooks.

MediaStore is Android's own indexed provider for shared media; it is the fast enumerator, but because
it can lag and cannot see app-private dirs, it is **paired with reconciliation** — no single observer
is trusted alone.

### 13.2 Scoped storage behavior

- **App-private** (`getExternalFilesDir`): fully readable by Jupiter; index directly.
- **Shared** (`MediaStore`, All-Files-Access on R+): the main surface; browse + index.
- **Downloads/Images/Video/Documents**: covered by `MediaStore.Files`.
- **Other apps' `Android/data` & `Android/obb`**: **unreadable by any file manager on Android 11+**
  (platform limit; documented to the user) — never indexed, only size-accounted via
  `StorageStatsManager`.
- **Read/write restrictions**: writes to shared media may throw `RecoverableSecurityException` →
  handle with `MediaStore.createWriteRequest`/`createDeleteRequest` consent intents.

API-level notes: MANAGE_EXTERNAL_STORAGE governs broad access on R+; READ_MEDIA_* on 33+;
POST_NOTIFICATIONS runtime grant on 33+ (required for alerts — shipped request); QUERY_ALL_PACKAGES for
app enumeration; REQUEST_DELETE_PACKAGES for uninstall intents (shipped); FOREGROUND_SERVICE_DATA_SYNC
for the survey on 34+.

---

## 14. Performance and scaling design

- **Index up to millions of files** via MediaStore seed (zero per-file syscalls) + reconciliation
  walk only for the gaps.
- **Avoid memory spikes**: stream cursors in batches (`forEachBatch`), never materialize the whole
  set; fingerprints are 8 bytes each.
- **Reduce I/O**: hash lazily/on-demand for same-size candidates only; sample large binaries instead
  of full reads; preserve hashes/fingerprints across rescans when identity is unchanged.
- **Batch extraction**: 100–200/run; per-run caps (2000) keep any single run bounded.
- **Cache descriptors**: they are the durable Room rows; recompute only on identity change or schema
  bump.
- **Compress fingerprints**: 64-bit longs; embeddings quantized (int8) if/when added.
- **Parallelize safely**: IO-bound extraction on a bounded dispatcher; DB writes serialized via Room.
- **Avoid full rescans**: generation model + checkpoint + identity-preserved descriptors.
- **Keep UI responsive**: all work off-main; UI reads Flows from Room; index-served reads stay usable
  during a rescan (`isUsable`).
- **Throttle / respect battery**: `battery-not-low` on backfill + periodic; expedited only for small
  arrival reconciles.
- **Layered prefiltering**: type → size → hash-prefix/LSH before any expensive per-pair compare.

---

## 15. False positive and false negative mitigation

**Why one metric is never enough:** every single signal has a failure mode — hash misses re-encodes,
perceptual over-matches similar scenes, metadata is trivially coincidental. Only fusion with vetoes is
safe.

- **Same subject ≠ duplicate**: two photos of the same person differ structurally; dHash distance is
  moderate, not tiny → stays below the near threshold (REVIEW at most, never auto).
- **Screenshot vs original**: related but cropped + UI chrome → usually Tier 1–2 → not auto-actioned.
- **App versions**: same signer+package, higher versionCode → "versioned family," never auto-dup.
- **Similar PDFs (templates/invoices)**: high text overlap but different values → require render-page
  agreement too; otherwise REVIEW.
- **Same-scene videos**: same footage, different cut/encode → keyframe overlap high but duration/audio
  differ → "similar," not exact.

**Reduce FPs without missing TPs:** require multi-layer agreement for high tiers (dHash **and**
histogram for Tier 4 images); apply hard vetoes (signer, size ratio, duration). **Lower FNs:** layered
checks — if identity misses, perceptual catches; if perceptual misses a heavy crop, semantic (planned)
or REVIEW surfaces it. **Calibrate over time:** per-type thresholds tuned against labeled corpora +
`UserFeedbackEntity` ("not a duplicate" demotes that pair and nudges the type threshold);
allowlist/ignorelist suppress known-good pairs; a **quarantine/review queue** holds Tier 2–3 for user
judgment; **staged escalation** promotes a pair to deep verification only when cheap layers agree.

---

## 16. UI/UX behavior for dedup results

Presented states: exact duplicate · probable duplicate · near-duplicate · related family · needs
review. Each result shows: **confidence**, **score breakdown** (per-layer contributions), **why it
matched** (human sentence: "dHash distance 3; sizes within 4%"), which layers contributed, whether the
match is **exact (hash)** vs **perceptual/semantic**, whether the file is already on device, and
whether it is automatic or needs review.

Behavior: **alert immediately** on a newly observed duplicate (shipped: notification + `Flow`);
**de-duplicate alerts** via the decision record (same pair never re-alerts without a reason — shipped:
monotonic checkpoint + distinct notification ids per kind); show **background indexing status** (survey
progress, "still fingerprinting your library"); explain **partial confidence** while descriptors build
("checking… perceptual pass pending"); **offline** is the normal case — everything is on-device.
Exact and "similar" alerts are visually distinct so the user knows byte-identical vs same-picture.

---

## 17. Observability and debugging

Structured JSON logs with a **correlation id per file arrival** threaded through discover → extract →
score → decide → alert. Event schema: `{corrId, fileId, stage, type, layerScores, tier, confidence,
durationMs, outcome}`. Metrics: descriptor-generation throughput + failure rate, scan/reconcile
latency, candidate-set size distribution, score distribution, per-tier hit rates, FP rate (from user
feedback), manual-review outcomes, descriptor schema-version spread, checkpoint lag (now − checkpoint).

**Debug record** (per decision, persisted in `SimilarityScoreEntity.explanation` + a debug sink): the
two file ids, every layer's raw + weighted score, which layers agreed/conflicted, which veto (if any)
fired, the type weight vector used, and the exact threshold comparison that set the tier — so any "why
was this (not) a duplicate?" question is answerable from stored data without reproduction.

---

## 18. Testing strategy

| Category | Real | Mocked |
|---|---|---|
| Per-layer unit (hash, dHash, SimHash, fusion) | the algorithm + fixtures | — (pure) |
| Room schema/migration | in-memory Room, real DAOs | — |
| WorkManager | `WorkManagerTestInitHelper`, real workers | time |
| Coroutine pipeline | real suspend fns, `runTest` | dispatchers (test) |
| Storage observation | real reconciler + checkpoint logic | `NewFileSource` (scripted), `DedupCheckpointStore` (in-memory) |
| Image dedup | real dHash over real encoded fixtures (Robolectric NATIVE graphics) | — |
| Video/PDF fingerprint | real decode over small fixtures | — |
| Text similarity | real SimHash/MinHash | — |
| APK versioning | real manifest/signer parse over fixture APKs | — |
| Archive similarity | real member-tree over fixture zips | — |
| Process death/recovery | real checkpoint + generation resume | killed mid-run |
| Large-storage stress | synthetic 100k-row index | file bytes (generated) |
| Incremental indexing | real delta + generation | clock |
| FP/FN regression | labeled corpus, assert tier | — |
| Alert de-dup | real detector + checkpoint | notifier |

Shipped tests exercising this design: `PerceptualHashTest`, `PerceptualHashSourceTest` (Robolectric
native graphics, real encode/decode round-trip), `IndexStateMachineTest` (generation/sweep/hash +
fingerprint preservation, near-dup lookup), `GrantSurveyPolicyTest`, `DedupCheckpointTest`,
**`DedupReconcilerTest`** (the arrival-while-closed end-to-end proof with a scripted source + real
detector + in-memory Room).

---

## 19. Operational rules and anti-patterns

**Anti-patterns (never do):** rely on filename only · size only · one hash only · rescan everything on
every launch · scan only on app open · compare all-vs-all · ignore type-specific logic · ignore schema
versioning · ignore other-app write events · ignore process death · trust one observer type · let the
UI be the source of truth.

**Operational rules (always):** never infer completion from a single metric (completeness is a durable
state, not a row count) · never delete/overwrite a descriptor before its replacement is durably written
(the targeted `updateHash` preserves the generation stamp for exactly this reason) · never emit a
duplicate alert for the same pair without cause · never block the main thread with scanning · never
treat every similarity as a duplicate · never ignore user feedback · never hard-delete bypassing the
Recycle Bin.

---

## 20. Final recommended end-to-end architecture

Build it as: a **MediaStore-seeded, generation-stamped Room index** kept warm by a foreground survey
and a 12-hour periodic kicker; a **hybrid observation layer** where ContentObserver/FileObserver are
_triggers_ and a **checkpoint-based `DedupReconciler`** is the source of truth for arrivals (so files
added while the app is dead are always caught); a **descriptor pipeline** that computes, per type,
crypto+fast+sample hashes, structural fingerprints, perceptual dHash (→ pHash/video-keyframe/PDF-render
next), and embeddings (later), all versioned and identity-preserved across rescans; **candidate
retrieval** by type→size→exact-hash→dHash-LSH(→ANN) buckets; a **fusion scorer** with type-aware
weights, hard vetoes, and six confidence tiers; a **`DuplicateDetector`** that on each arrival indexes
→ exact-checks → perceptual-checks → emits an explainable, de-duplicated alert; **Room persistence**
separating storage/descriptor/score/decision/feedback records; a **WorkManager fabric** of small,
resumable, battery-aware `CoroutineWorker`s recoverable across process death via checkpoints and
generation stamps; a **notification+Flow UI** that distinguishes exact vs similar and explains itself;
and an **observability layer** where every decision is reconstructable from stored data. The mobile
system is self-sufficient and offline; an optional backend (vector search, cross-device dedup,
telemetry) can layer on later without changing the on-device contracts.

This is the architecture Jupiter is converging on. The arrival-detection reconciler, the exact +
perceptual layers, the generation/state index, and the observability of decisions are the load-bearing
pieces — shipped or in this change — and the remaining extractors (video, PDF, audio, APK, archive,
semantic) plug into the same pipeline without re-architecting it.
