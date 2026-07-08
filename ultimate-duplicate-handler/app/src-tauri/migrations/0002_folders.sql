-- Migration 0002 — duplicate-folder equivalence.
-- Two folders are "duplicate" when their full recursive content is identical: the same
-- set of (relative-subpath, content-hash) entries. We store a folder signature (BLAKE3
-- over the sorted entry list) so equal folders share a signature. This is an on-demand
-- analysis (the file index remains the source of truth); these tables are a materialized
-- view that `detect_duplicate_folders` rebuilds.

PRAGMA user_version = 2;

CREATE TABLE folder_cluster (
  id                INTEGER PRIMARY KEY,
  signature         BLOB NOT NULL UNIQUE,        -- BLAKE3 of the sorted content set
  file_count        INTEGER NOT NULL,            -- files per folder (all members equal)
  total_bytes       INTEGER NOT NULL,            -- bytes per folder
  member_count      INTEGER NOT NULL,            -- how many equal folders
  reclaimable_bytes INTEGER NOT NULL,            -- total_bytes * (member_count - 1)
  created_at        INTEGER NOT NULL
);
CREATE INDEX idx_folder_cluster_reclaim ON folder_cluster(reclaimable_bytes DESC);

CREATE TABLE folder_cluster_member (
  id              INTEGER PRIMARY KEY,
  cluster_id      INTEGER NOT NULL REFERENCES folder_cluster(id) ON DELETE CASCADE,
  volume_id       INTEGER NOT NULL REFERENCES volume(id) ON DELETE CASCADE,
  folder_path_raw TEXT NOT NULL,                 -- volume-relative, original case
  folder_path_key TEXT NOT NULL,
  is_online       INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_folder_member ON folder_cluster_member(cluster_id);
