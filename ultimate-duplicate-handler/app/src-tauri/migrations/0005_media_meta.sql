-- Migration 0005 — media integrity & technical-quality metadata.
-- Populated by the media-probe pass (ffprobe-backed) and cached per file so re-analysis is
-- incremental (history-aware moat): only newly-seen media is probed. All columns are
-- additive and nullable so the table degrades gracefully when ffprobe is unavailable.

PRAGMA user_version = 5;

CREATE TABLE media_meta (
  file_id        INTEGER PRIMARY KEY REFERENCES file(id) ON DELETE CASCADE,
  probe_state    TEXT NOT NULL,            -- ok | error | unsupported
  integrity      TEXT NOT NULL,            -- healthy | suspicious | partial | corrupted | unreadable
  duration_s     REAL,                     -- container duration in seconds
  width          INTEGER,                  -- primary video stream width (px)
  height         INTEGER,                  -- primary video stream height (px)
  bitrate        INTEGER,                  -- overall bits/sec (effective if absent in container)
  codec          TEXT,                     -- primary video codec (e.g. h264, hevc)
  fps            REAL,                     -- frames/sec of the primary video stream
  has_audio      INTEGER NOT NULL DEFAULT 0,
  stream_count   INTEGER,
  quality_grade  TEXT NOT NULL DEFAULT 'unknown', -- good | fair | poor | unknown
  quality_warning INTEGER NOT NULL DEFAULT 0,      -- 1 => surfaced as a warning badge
  warn_reason    TEXT,                     -- human-readable "likely…" explanation
  analyzed_at    INTEGER NOT NULL
);

CREATE INDEX idx_media_meta_integrity ON media_meta(integrity);
CREATE INDEX idx_media_meta_warning   ON media_meta(quality_warning);
