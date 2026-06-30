-- Sift — persistent file-intelligence index. Migration 0001 (baseline).
-- Recovery truth lives here. Every table is designed so that knowledge survives
-- app restarts, reboots, and drive disconnects. History is preserved until the
-- user explicitly clears or rebuilds it.
--
-- Conventions:
--   *_at columns are unix epoch SECONDS (INTEGER). Locale-neutral.
--   ids are INTEGER PRIMARY KEY (rowid) unless a natural key is stronger.
--   "online" = the owning volume is currently mounted and reachable.
--   A file row is a *sighting of a path on a volume*. Content identity lives in `content`.

PRAGMA user_version = 1;

------------------------------------------------------------------------------
-- VOLUMES / DEVICES — so offline drives stay addressable and knowledge persists
------------------------------------------------------------------------------
CREATE TABLE volume (
  id                   INTEGER PRIMARY KEY,
  -- Stable identity. volume_guid (\\?\Volume{GUID}\) is the most stable on Windows;
  -- serial + fs label are fallbacks when GUID is unavailable.
  volume_guid          TEXT UNIQUE,            -- nullable; preferred identity
  volume_serial        INTEGER,                -- NTFS serial (GetVolumeInformation)
  fs_label             TEXT,                    -- user-facing label e.g. "Backup4TB"
  fs_type              TEXT,                    -- NTFS / exFAT / ReFS / network / unknown
  total_bytes          INTEGER,
  -- Mount state is a *current* view; history is in volume_presence.
  current_mount_point  TEXT,                    -- e.g. "E:\" when online, else NULL
  is_online            INTEGER NOT NULL DEFAULT 0,
  is_removable         INTEGER NOT NULL DEFAULT 0,
  first_seen_at        INTEGER NOT NULL,
  last_seen_at         INTEGER NOT NULL,
  user_alias           TEXT,                    -- optional human name the user assigns
  notes                TEXT
);
CREATE INDEX idx_volume_serial ON volume(volume_serial);

-- Online/offline timeline per volume (mount history).
CREATE TABLE volume_presence (
  id          INTEGER PRIMARY KEY,
  volume_id   INTEGER NOT NULL REFERENCES volume(id) ON DELETE CASCADE,
  mount_point TEXT,
  online_at   INTEGER NOT NULL,
  offline_at  INTEGER                            -- NULL while currently mounted
);
CREATE INDEX idx_vol_presence ON volume_presence(volume_id, online_at);

------------------------------------------------------------------------------
-- CONTENT — content-addressed identity; the spine of duplicate & historical intel
------------------------------------------------------------------------------
-- One row per distinct full-content hash ever observed, EVER, on ANY volume,
-- even after every physical copy is deleted. This is what powers
-- "this file still exists elsewhere" and cross-time/cross-drive dedup.
CREATE TABLE content (
  id                INTEGER PRIMARY KEY,
  full_hash         BLOB UNIQUE,                 -- BLAKE3-256; NULL until promoted to full hash
  size_bytes        INTEGER NOT NULL,
  -- Partial fingerprint identity for the size-collision narrowing stage.
  partial_hash      BLOB,                        -- BLAKE3 of head+tail sample (see hashing.rs)
  first_indexed_at  INTEGER NOT NULL,
  last_confirmed_at INTEGER NOT NULL,            -- last time a live file proved this content exists
  -- Denormalized counters maintained transactionally for fast UI (truth re-derivable from file).
  live_copies       INTEGER NOT NULL DEFAULT 0,  -- files currently present (any volume)
  online_copies     INTEGER NOT NULL DEFAULT 0,  -- files present on a currently-mounted volume
  known_copies      INTEGER NOT NULL DEFAULT 0   -- distinct (volume,path) ever seen, incl. deleted
);
CREATE INDEX idx_content_size       ON content(size_bytes);
CREATE INDEX idx_content_size_part  ON content(size_bytes, partial_hash);
-- full_hash UNIQUE already indexes it.

------------------------------------------------------------------------------
-- FILE — a path sighting on a volume. Survives deletion (status flips, row stays).
------------------------------------------------------------------------------
CREATE TABLE file (
  id                INTEGER PRIMARY KEY,
  volume_id         INTEGER NOT NULL REFERENCES volume(id) ON DELETE CASCADE,
  content_id        INTEGER REFERENCES content(id) ON DELETE SET NULL, -- NULL until hashed
  -- Path stored losslessly. path_raw is the original; path_key is case-folded + NFC for matching.
  path_raw          TEXT NOT NULL,               -- volume-relative, original case, e.g. "Photos\IMG.JPG"
  path_key          TEXT NOT NULL,               -- normalized for equality/search
  file_name         TEXT NOT NULL,
  ext               TEXT,                          -- lowercased, no dot; NULL if none
  size_bytes        INTEGER NOT NULL,
  created_at        INTEGER,                       -- fs ctime
  modified_at       INTEGER,                       -- fs mtime (primary change signal)
  -- NTFS file id (64/128-bit) when available — survives rename, detects moves.
  fs_file_id        BLOB,
  is_hidden         INTEGER NOT NULL DEFAULT 0,
  is_system         INTEGER NOT NULL DEFAULT 0,
  is_reparse        INTEGER NOT NULL DEFAULT 0,    -- junction/symlink — not recursed by default
  -- Lifecycle status. Knowledge persists across all of these.
  status            TEXT NOT NULL DEFAULT 'present'
                    CHECK (status IN ('present','missing','deleted_by_user','moved','error')),
  hash_state        TEXT NOT NULL DEFAULT 'none'
                    CHECK (hash_state IN ('none','partial','full','error')),
  error_message     TEXT,                          -- populated when status/hash_state = 'error'
  first_seen_at     INTEGER NOT NULL,
  last_seen_at      INTEGER NOT NULL,              -- last scan that confirmed it present
  removed_at        INTEGER,                        -- when it went missing / was deleted
  source_id         INTEGER REFERENCES scan_source(id) ON DELETE SET NULL,
  UNIQUE (volume_id, path_key)
);
CREATE INDEX idx_file_content  ON file(content_id);
CREATE INDEX idx_file_volume   ON file(volume_id, status);
CREATE INDEX idx_file_size     ON file(size_bytes);
CREATE INDEX idx_file_name     ON file(file_name);
CREATE INDEX idx_file_ext      ON file(ext);
CREATE INDEX idx_file_fsid     ON file(volume_id, fs_file_id);
CREATE INDEX idx_file_status   ON file(status, hash_state);

------------------------------------------------------------------------------
-- DUPLICATE CLUSTERS — materialized grouping for the review workspace
------------------------------------------------------------------------------
-- A cluster == a content row with >1 known copy. Kept as its own table so we can
-- attach user decisions, confidence, and "duplicate folder" semantics.
CREATE TABLE cluster (
  id               INTEGER PRIMARY KEY,
  content_id       INTEGER NOT NULL REFERENCES content(id) ON DELETE CASCADE,
  kind             TEXT NOT NULL DEFAULT 'exact'
                   CHECK (kind IN ('exact','name_size','folder')),
  confidence       TEXT NOT NULL DEFAULT 'high'
                   CHECK (confidence IN ('high','medium','low')),
  member_count     INTEGER NOT NULL,
  reclaimable_bytes INTEGER NOT NULL,             -- size * (member_count - keepers)
  created_at       INTEGER NOT NULL,
  recomputed_at    INTEGER NOT NULL,
  UNIQUE (content_id, kind)
);
CREATE INDEX idx_cluster_reclaim ON cluster(reclaimable_bytes DESC);

------------------------------------------------------------------------------
-- SCAN SOURCES & PROFILES — reusable, saved selections (incl. offline drives)
------------------------------------------------------------------------------
CREATE TABLE scan_source (
  id            INTEGER PRIMARY KEY,
  kind          TEXT NOT NULL CHECK (kind IN ('volume','folder')),
  volume_id     INTEGER REFERENCES volume(id) ON DELETE CASCADE,
  rel_path      TEXT,                              -- folder within volume, or NULL for whole volume
  display_name  TEXT NOT NULL,
  created_at    INTEGER NOT NULL
);

CREATE TABLE scan_profile (
  id            INTEGER PRIMARY KEY,
  name          TEXT NOT NULL UNIQUE,
  filters_json  TEXT NOT NULL,                     -- serialized ScanFilters (size/date/ext/excludes)
  mode          TEXT NOT NULL DEFAULT 'thorough'
                CHECK (mode IN ('lightweight','thorough')),
  created_at    INTEGER NOT NULL,
  last_used_at  INTEGER
);
CREATE TABLE scan_profile_source (
  profile_id INTEGER NOT NULL REFERENCES scan_profile(id) ON DELETE CASCADE,
  source_id  INTEGER NOT NULL REFERENCES scan_source(id) ON DELETE CASCADE,
  PRIMARY KEY (profile_id, source_id)
);

------------------------------------------------------------------------------
-- SCAN JOBS — resumable, checkpointed long-running work (recovery truth)
------------------------------------------------------------------------------
CREATE TABLE scan_job (
  id              INTEGER PRIMARY KEY,
  profile_id      INTEGER REFERENCES scan_profile(id) ON DELETE SET NULL,
  state           TEXT NOT NULL DEFAULT 'created'
                  CHECK (state IN ('created','enumerating','hashing','clustering','done','paused','failed','cancelled')),
  stage           TEXT,
  files_seen      INTEGER NOT NULL DEFAULT 0,
  files_hashed    INTEGER NOT NULL DEFAULT 0,
  bytes_hashed    INTEGER NOT NULL DEFAULT 0,
  candidate_groups INTEGER NOT NULL DEFAULT 0,
  -- Resume cursor (opaque JSON: remaining roots, last enumerated path, queue head).
  checkpoint_json TEXT,
  started_at      INTEGER NOT NULL,
  updated_at      INTEGER NOT NULL,
  finished_at     INTEGER,
  error_message   TEXT
);
CREATE INDEX idx_scan_job_state ON scan_job(state);

------------------------------------------------------------------------------
-- AUDIT LOG — append-only record of destructive & privileged actions
------------------------------------------------------------------------------
-- Written BEFORE the action (intent) and updated with outcome. Never deleted.
CREATE TABLE audit_log (
  id            INTEGER PRIMARY KEY,
  at            INTEGER NOT NULL,
  action        TEXT NOT NULL,                     -- 'delete','recycle','quarantine','rebuild_index',...
  outcome       TEXT NOT NULL DEFAULT 'intent'
                CHECK (outcome IN ('intent','success','partial','failed')),
  file_id       INTEGER,                            -- soft ref (file row may be repurposed); no FK
  content_id    INTEGER,
  volume_id     INTEGER,
  path_raw      TEXT,                               -- captured at action time (survives row changes)
  detail_json   TEXT,                               -- plan id, reason, failed-item list, etc.
  reversible    INTEGER NOT NULL DEFAULT 1          -- 1 = recycle/quarantine, 0 = permanent
);
CREATE INDEX idx_audit_at      ON audit_log(at DESC);
CREATE INDEX idx_audit_action  ON audit_log(action);

------------------------------------------------------------------------------
-- DELETION PLANS — explicit, reviewable, exportable before execution
------------------------------------------------------------------------------
CREATE TABLE deletion_plan (
  id            INTEGER PRIMARY KEY,
  name          TEXT,
  created_at    INTEGER NOT NULL,
  committed_at  INTEGER,                            -- NULL until executed
  mode          TEXT NOT NULL DEFAULT 'recycle'
                CHECK (mode IN ('recycle','quarantine','permanent')),
  state         TEXT NOT NULL DEFAULT 'draft'
                CHECK (state IN ('draft','committed','partial','failed','rolled_back'))
);
CREATE TABLE deletion_plan_item (
  id            INTEGER PRIMARY KEY,
  plan_id       INTEGER NOT NULL REFERENCES deletion_plan(id) ON DELETE CASCADE,
  file_id       INTEGER NOT NULL REFERENCES file(id) ON DELETE CASCADE,
  decision      TEXT NOT NULL CHECK (decision IN ('keep','remove')),
  reason        TEXT,                               -- which selection rule chose this
  result        TEXT CHECK (result IN ('pending','removed','failed','skipped')),
  result_detail TEXT
);
CREATE INDEX idx_plan_item ON deletion_plan_item(plan_id, decision);

------------------------------------------------------------------------------
-- KEY-VALUE app/meta (schema version is in PRAGMA user_version; this is for the rest)
------------------------------------------------------------------------------
CREATE TABLE app_meta (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
INSERT INTO app_meta(key, value) VALUES ('schema_baseline', '0001');
