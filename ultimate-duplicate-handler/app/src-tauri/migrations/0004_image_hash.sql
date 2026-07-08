-- Migration 0004 — perceptual image hashes for similar-image detection.
-- Cached per file so re-detection is near-instant (history-aware moat). The hash is a
-- 64-bit dHash stored as INTEGER (bit-cast u64 -> i64); similarity = Hamming distance.

PRAGMA user_version = 4;

CREATE TABLE image_hash (
  file_id    INTEGER PRIMARY KEY REFERENCES file(id) ON DELETE CASCADE,
  hash       INTEGER,                 -- 64-bit dHash (NULL when state='error')
  algo       TEXT NOT NULL DEFAULT 'dhash8',
  state      TEXT NOT NULL DEFAULT 'ok' CHECK (state IN ('ok','error')),
  hashed_at  INTEGER NOT NULL
);
CREATE INDEX idx_image_hash_state ON image_hash(state);
