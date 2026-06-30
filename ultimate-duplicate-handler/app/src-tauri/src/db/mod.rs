//! Persistence layer. The SQLite index is recovery truth (CODING_RULES §3).
//!
//! Concurrency model (CODING_RULES §6): exactly ONE writer connection, serialized
//! behind a Mutex and fed through the engine's single-writer task; readers may open
//! additional connections. WAL mode lets readers proceed while the writer commits.
//!
//! Migrations are versioned and embedded (CODING_RULES §3/§4). `CREATE TABLE IF NOT
//! EXISTS` is FORBIDDEN outside this runner — schema evolution goes through numbered
//! files so the on-disk DB and the code never drift (lesson F014/GOVERNANCE-002).

pub mod repo;

use rusqlite::{Connection, OpenFlags};
use std::path::Path;

#[derive(Debug, thiserror::Error)]
pub enum DbError {
    #[error("sqlite: {0}")]
    Sqlite(#[from] rusqlite::Error),
    #[error("migration {version} failed: {source}")]
    Migration { version: i64, source: rusqlite::Error },
}

/// Embedded, ordered migrations. Index 0 => user_version 1, etc.
/// Append new files here; never edit a shipped migration (additive only — §0).
const MIGRATIONS: &[(i64, &str)] = &[
    (1, include_str!("../../migrations/0001_init.sql")),
    (2, include_str!("../../migrations/0002_folders.sql")),
    (3, include_str!("../../migrations/0003_scan_history.sql")),
    (4, include_str!("../../migrations/0004_image_hash.sql")),
    (5, include_str!("../../migrations/0005_media_meta.sql")),
    (6, include_str!("../../migrations/0006_video_hash.sql")),
];

/// Open (or create) the index DB and bring it to the latest schema version.
/// Applies the connection PRAGMAs every open — they are per-connection, not persisted.
pub fn open_writer(path: &Path) -> Result<Connection, DbError> {
    let conn = Connection::open_with_flags(
        path,
        OpenFlags::SQLITE_OPEN_READ_WRITE | OpenFlags::SQLITE_OPEN_CREATE,
    )?;
    apply_pragmas(&conn)?;
    migrate(&conn)?;
    Ok(conn)
}

/// Open a read-only connection for query workers. Fails if the DB does not exist yet.
pub fn open_reader(path: &Path) -> Result<Connection, DbError> {
    let conn = Connection::open_with_flags(path, OpenFlags::SQLITE_OPEN_READ_ONLY)?;
    apply_pragmas(&conn)?;
    Ok(conn)
}

fn apply_pragmas(conn: &Connection) -> Result<(), DbError> {
    // WAL: concurrent reads during writes; durable across crashes.
    conn.pragma_update(None, "journal_mode", "WAL")?;
    // NORMAL is the recommended WAL durability/perf balance.
    conn.pragma_update(None, "synchronous", "NORMAL")?;
    // Enforce FKs (off by default in SQLite!) — invariants live in the DB (Law 2).
    conn.pragma_update(None, "foreign_keys", "ON")?;
    // Wait instead of erroring when the single writer holds the lock briefly.
    conn.pragma_update(None, "busy_timeout", 5_000)?;
    // Reasonable memory for large-index joins without unbounded growth.
    conn.pragma_update(None, "cache_size", -65_536)?; // ~64 MiB
    Ok(())
}

/// Idempotent forward migration. Reads `PRAGMA user_version`, applies every migration
/// whose version is higher, each inside its own transaction. On failure, the failed
/// migration's transaction rolls back and we stop — never leave a half-applied schema.
pub fn migrate(conn: &Connection) -> Result<(), DbError> {
    let current: i64 = conn.pragma_query_value(None, "user_version", |r| r.get(0))?;
    for &(version, sql) in MIGRATIONS {
        if version <= current {
            continue;
        }
        conn.execute_batch("BEGIN")
            .and_then(|_| conn.execute_batch(sql))
            .and_then(|_| conn.execute_batch("COMMIT"))
            .map_err(|source| {
                let _ = conn.execute_batch("ROLLBACK");
                DbError::Migration { version, source }
            })?;
    }
    Ok(())
}

/// Flush the WAL into the main `index.sqlite` file so committed data is durable in the DB
/// file itself, not just the `-wal` sidecar. WAL already survives a crash/reopen, but this
/// guarantees the on-disk `index.sqlite` is self-contained (e.g. if the folder is copied to
/// another machine — the portability promise). Best-effort; TRUNCATE shrinks the WAL.
pub fn checkpoint(conn: &Connection) -> Result<(), DbError> {
    // PRAGMA wal_checkpoint returns a row; run it as a query and discard the result.
    let _ = conn.query_row("PRAGMA wal_checkpoint(TRUNCATE)", [], |_r| Ok(()));
    Ok(())
}

/// Lightweight integrity check exposed to the Settings → Maintenance screen.
/// Returns Ok(true) when SQLite reports "ok".
pub fn integrity_check(conn: &Connection) -> Result<bool, DbError> {
    let result: String =
        conn.query_row("PRAGMA integrity_check", [], |r| r.get(0))?;
    Ok(result == "ok")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn migrates_fresh_db_and_is_idempotent() {
        let dir = std::env::temp_dir();
        let path = dir.join(format!("sift_db_test_{}.sqlite", std::process::id()));
        let _ = std::fs::remove_file(&path);

        let conn = open_writer(&path).expect("open+migrate");
        let v: i64 = conn
            .pragma_query_value(None, "user_version", |r| r.get(0))
            .unwrap();
        // Latest baseline is the number of migrations in MIGRATIONS (0001 + 0002).
        assert_eq!(v, MIGRATIONS.len() as i64, "schema should be at the latest version");

        // Re-running migrate must be a no-op (idempotent).
        migrate(&conn).expect("second migrate");
        assert!(integrity_check(&conn).unwrap());

        // Core tables exist (incl. the folder tables added by 0002).
        let n: i64 = conn
            .query_row(
                "SELECT COUNT(*) AS n FROM sqlite_master WHERE type='table' AND name IN ('file','content','volume','audit_log','folder_cluster')",
                [],
                |r| r.get("n"), // aggregate read by alias — lesson F020
            )
            .unwrap();
        assert_eq!(n, 5);

        drop(conn);
        let _ = std::fs::remove_file(&path);
    }

    /// The product promise: an indexed file and its scan history MUST survive a full app
    /// close + reopen (this is what makes the cross-drive history-aware matching possible).
    /// Proves persistence at the storage layer, independent of any one connection.
    #[test]
    fn index_and_scan_history_survive_close_and_reopen() {
        let path = std::env::temp_dir().join(format!("sift_persist_{}.sqlite", std::process::id()));
        for suffix in ["", "-wal", "-shm"] {
            let _ = std::fs::remove_file(format!("{}{suffix}", path.display()));
        }
        // --- Session 1: write a volume, a scan_job, and a present file; then "close". ---
        {
            let conn = open_writer(&path).expect("open writer");
            conn.execute_batch(
                "INSERT INTO volume (id, fs_label, fs_type, is_online, first_seen_at, last_seen_at) \
                   VALUES (1,'TestDrive','NTFS',1,1,1);
                 INSERT INTO scan_job (id, state, stage, started_at, updated_at) \
                   VALUES (1,'done','done',1,1);
                 INSERT INTO file (volume_id, path_raw, path_key, file_name, size_bytes, status, \
                   hash_state, first_seen_at, last_seen_at, last_scan_job_id) \
                   VALUES (1,'Movies\\a.mp4','movies\\a.mp4','a.mp4',100,'present','none',1,1,1);",
            )
            .expect("seed");
            checkpoint(&conn).expect("checkpoint");
            drop(conn); // simulate the app closing
        }
        // --- Session 2: a fresh reader (simulates relaunch). Data MUST still be there. ---
        let r = open_reader(&path).expect("reopen reader");
        let files: i64 = r
            .query_row("SELECT COUNT(*) AS n FROM file WHERE status='present'", [], |x| x.get("n"))
            .unwrap();
        let jobs: i64 = r.query_row("SELECT COUNT(*) AS n FROM scan_job", [], |x| x.get("n")).unwrap();
        assert_eq!(files, 1, "indexed file must survive a close+reopen (the persistent-index promise)");
        assert_eq!(jobs, 1, "scan history must survive a close+reopen");
        drop(r);
        for suffix in ["", "-wal", "-shm"] {
            let _ = std::fs::remove_file(format!("{}{suffix}", path.display()));
        }
    }
}
