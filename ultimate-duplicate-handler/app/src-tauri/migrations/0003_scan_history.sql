-- Migration 0003 — scan observability & history (additive, non-regression).
-- Enriches the existing scan_job into a full scan-session record, links indexed files to
-- the scan that last touched them, and adds a timeline-event log + folder-traversal state.
-- All ALTERs use constant defaults so existing rows remain valid.

PRAGMA user_version = 3;

-- scan_job becomes the scan-session record (we keep the existing columns).
ALTER TABLE scan_job ADD COLUMN sources_json        TEXT;
ALTER TABLE scan_job ADD COLUMN drive_label         TEXT;
ALTER TABLE scan_job ADD COLUMN mode                TEXT;
ALTER TABLE scan_job ADD COLUMN profile_name        TEXT;
ALTER TABLE scan_job ADD COLUMN filters_json        TEXT;
ALTER TABLE scan_job ADD COLUMN folders_traversed   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE scan_job ADD COLUMN subfolders_traversed INTEGER NOT NULL DEFAULT 0;
ALTER TABLE scan_job ADD COLUMN skipped_count       INTEGER NOT NULL DEFAULT 0;
ALTER TABLE scan_job ADD COLUMN error_count         INTEGER NOT NULL DEFAULT 0;
ALTER TABLE scan_job ADD COLUMN duplicates_found    INTEGER NOT NULL DEFAULT 0;
ALTER TABLE scan_job ADD COLUMN total_bytes         INTEGER NOT NULL DEFAULT 0;
ALTER TABLE scan_job ADD COLUMN scanning_ms         INTEGER NOT NULL DEFAULT 0;
ALTER TABLE scan_job ADD COLUMN hashing_ms          INTEGER NOT NULL DEFAULT 0;
ALTER TABLE scan_job ADD COLUMN resumable           INTEGER NOT NULL DEFAULT 0;
ALTER TABLE scan_job ADD COLUMN build_version       TEXT;

-- Link each file row to the scan session that last indexed/confirmed it (for the Index
-- Explorer "which scan produced this" relation, and the scanned-at timestamp story).
ALTER TABLE file ADD COLUMN last_scan_job_id INTEGER;
CREATE INDEX idx_file_scan_job ON file(last_scan_job_id);

-- Append-only timeline of a scan: minute-progress ticks + lifecycle/stage events.
CREATE TABLE scan_log_event (
  id              INTEGER PRIMARY KEY,
  scan_job_id     INTEGER NOT NULL REFERENCES scan_job(id) ON DELETE CASCADE,
  at              INTEGER NOT NULL,           -- unix seconds
  elapsed_ms      INTEGER NOT NULL DEFAULT 0, -- since scan start
  kind            TEXT NOT NULL,              -- progress|started|source|folder|stage|paused|resumed|interrupted|completed|failed|cancelled|warning
  message         TEXT NOT NULL,
  files_processed INTEGER NOT NULL DEFAULT 0,
  detail_json     TEXT
);
CREATE INDEX idx_scan_log_job ON scan_log_event(scan_job_id, at);

-- Per-folder traversal state for completion visibility + interruption/resume awareness.
CREATE TABLE folder_traversal (
  id              INTEGER PRIMARY KEY,
  scan_job_id     INTEGER NOT NULL REFERENCES scan_job(id) ON DELETE CASCADE,
  volume_id       INTEGER,
  folder_path_raw TEXT NOT NULL,              -- volume-relative, original case
  depth           INTEGER NOT NULL DEFAULT 0, -- 0 = top-level source root
  state           TEXT NOT NULL DEFAULT 'in_progress'
                  CHECK (state IN ('pending','in_progress','completed','skipped','failed','unavailable')),
  file_count      INTEGER NOT NULL DEFAULT 0,
  started_at      INTEGER,
  completed_at    INTEGER,
  duration_ms     INTEGER NOT NULL DEFAULT 0,
  UNIQUE (scan_job_id, folder_path_raw)
);
CREATE INDEX idx_folder_traversal_job ON folder_traversal(scan_job_id, state);
