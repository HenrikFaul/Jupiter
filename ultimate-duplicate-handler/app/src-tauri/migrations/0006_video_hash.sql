-- Migration 0006 — perceptual VIDEO fingerprints for similar-video detection.
-- Mirrors 0004_image_hash.sql, but a video's signature is a VECTOR of per-keyframe
-- 64-bit dHashes (not a single hash), so it is stored as a BLOB: N keyframes ×
-- 8 bytes each, little-endian (the exact byte layout videohash.rs encodes/decodes).
-- Cached per file so re-detection (e.g. moving the strictness slider) is near-instant
-- and only newly-seen videos are sampled — the same history-aware moat as the image
-- and media-meta caches. The signature degrades gracefully: state='error' rows carry a
-- NULL signature (the video could not be sampled — corrupt / unreadable / no ffmpeg).

PRAGMA user_version = 6;

CREATE TABLE video_hash (
  file_id    INTEGER PRIMARY KEY REFERENCES file(id) ON DELETE CASCADE,
  signature  BLOB,                       -- keyframes × 8 bytes (u64 LE); NULL when state='error'
  keyframes  INTEGER NOT NULL DEFAULT 0, -- number of dHashes packed into `signature`
  duration_s REAL,                       -- sampled clip duration (informational; may be NULL)
  algo       TEXT NOT NULL DEFAULT 'kf-dhash8',
  state      TEXT NOT NULL DEFAULT 'ok' CHECK (state IN ('ok','error')),
  hashed_at  INTEGER NOT NULL
);
CREATE INDEX idx_video_hash_state ON video_hash(state);
