-- Sift — Migration 0006: Cloud / NAS & network-drive awareness.
-- Additive only (§0 in db/mod.rs): new nullable / defaulted columns, no rewrites of
-- shipped tables, no data migration. Brings the schema to user_version = 6.
--
-- What this enables:
--   * file.is_placeholder  — the file is a cloud-only "Files On-Demand" placeholder
--                            (FILE_ATTRIBUTE_RECALL_ON_DATA_ACCESS / FILE_ATTRIBUTE_OFFLINE).
--                            Such a file is indexed by METADATA only and is deliberately
--                            EXCLUDED from the hashing candidate set so dedup never forces a
--                            multi-gigabyte hydration (download) just to fingerprint it.
--   * file.storage_kind    — coarse provenance of the sighting: 'local' | 'network' |
--                            'placeholder' (free-form; UI hint only, never a hard invariant).
--   * volume.provider      — coarse provider of the owning volume so the Sources screen can
--                            show where sprawl lives: 'local' | 'network' | 'onedrive' |
--                            'dropbox' | 'gdrive'. NULL = unknown / not yet classified.

PRAGMA user_version = 6;

ALTER TABLE file   ADD COLUMN is_placeholder INTEGER NOT NULL DEFAULT 0;
ALTER TABLE file   ADD COLUMN storage_kind   TEXT;
ALTER TABLE volume ADD COLUMN provider        TEXT;

-- Cheap partial index so "give me the real, hydrated files" stays fast even when a OneDrive
-- folder contributes hundreds of thousands of placeholder rows.
CREATE INDEX idx_file_placeholder ON file(is_placeholder);
