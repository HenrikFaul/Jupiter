//! Report export. Writes CSV/JSON to the portable exports directory and returns the
//! written path. CSV fields are quoted defensively (the lesson about CSV correctness
//! applies to writing too: any field with a comma/quote/newline is quoted and inner
//! quotes doubled).

use crate::db::repo;
use rusqlite::{Connection, Result as SqlResult};
use std::path::{Path, PathBuf};

pub fn export(conn: &Connection, kind: &str, out_dir: &Path) -> Result<PathBuf, String> {
    let (filename, content) = match kind {
        "duplicates" => ("duplicates", duplicates_csv(conn).map_err(sql)?),
        "audit" => ("audit", audit_csv(conn).map_err(sql)?),
        "drives" => ("drives", drives_csv(conn).map_err(sql)?),
        "folders" => ("folders", folders_csv(conn).map_err(sql)?),
        "reclaim" => ("reclaim", reclaim_csv(conn).map_err(sql)?),
        other => return Err(format!("unknown report kind '{other}'")),
    };
    std::fs::create_dir_all(out_dir).map_err(|e| e.to_string())?;
    let path = out_dir.join(format!("sift-{filename}-{}.csv", repo::now()));
    std::fs::write(&path, content).map_err(|e| e.to_string())?;
    Ok(path)
}

fn sql(e: rusqlite::Error) -> String {
    format!("query failed: {e}")
}

fn csv_field(s: &str) -> String {
    if s.contains([',', '"', '\n', '\r']) {
        format!("\"{}\"", s.replace('"', "\"\""))
    } else {
        s.to_string()
    }
}

fn row(fields: &[String]) -> String {
    let mut line = fields.iter().map(|f| csv_field(f)).collect::<Vec<_>>().join(",");
    line.push('\n');
    line
}

/// A shareable "reclaim evidence bundle": a metric,value CSV summarizing what Sift cleaned
/// up (for IT / compliance / personal records). Sourced from the persistent audit log.
fn reclaim_csv(conn: &Connection) -> SqlResult<String> {
    let s = repo::reclaim_summary(conn)?;
    fn human(b: i64) -> String {
        let units = ["B", "KB", "MB", "GB", "TB", "PB"];
        let mut v = b as f64;
        let mut i = 0;
        while v >= 1024.0 && i < units.len() - 1 {
            v /= 1024.0;
            i += 1;
        }
        format!("{v:.2} {}", units[i])
    }
    let mut out = row(&["metric", "value"].iter().map(|s| s.to_string()).collect::<Vec<_>>());
    let mut add = |k: &str, v: String| out.push_str(&row(&[k.to_string(), v]));
    add("generated_at_unix", s.generated_at.to_string());
    add("space_reclaimed", human(s.bytes_reclaimed));
    add("space_reclaimed_bytes", s.bytes_reclaimed.to_string());
    add("files_removed", s.files_removed.to_string());
    add("recycled", s.recycled.to_string());
    add("quarantined", s.quarantined.to_string());
    add("permanently_deleted", s.permanently_deleted.to_string());
    add("files_marked_renamed", s.files_renamed.to_string());
    add("files_restored", s.files_restored.to_string());
    add("still_reclaimable", human(s.still_reclaimable_bytes));
    add("still_reclaimable_bytes", s.still_reclaimable_bytes.to_string());
    add("present_files_indexed", s.present_files.to_string());
    add("duplicate_clusters_remaining", s.duplicate_clusters.to_string());
    Ok(out)
}

fn duplicates_csv(conn: &Connection) -> SqlResult<String> {
    let mut out = row(&[
        "cluster_id", "content_id", "size_bytes", "member_count", "reclaimable_bytes",
        "drive", "online", "status", "path",
    ].iter().map(|s| s.to_string()).collect::<Vec<_>>());

    let mut stmt = conn.prepare(
        "SELECT c.id AS cid, c.content_id AS coid, co.size_bytes AS sz, c.member_count AS mc, \
                c.reclaimable_bytes AS rb, COALESCE(v.fs_label,'Unknown') AS drive, \
                v.is_online AS online, f.status AS st, f.path_raw AS path \
         FROM cluster c JOIN content co ON co.id=c.content_id \
         JOIN file f ON f.content_id=c.content_id \
         JOIN volume v ON v.id=f.volume_id \
         ORDER BY c.reclaimable_bytes DESC, c.id",
    )?;
    let mut rows = stmt.query([])?;
    while let Some(r) = rows.next()? {
        out.push_str(&row(&[
            r.get::<_, i64>("cid")?.to_string(),
            r.get::<_, i64>("coid")?.to_string(),
            r.get::<_, i64>("sz")?.to_string(),
            r.get::<_, i64>("mc")?.to_string(),
            r.get::<_, i64>("rb")?.to_string(),
            r.get::<_, String>("drive")?,
            if r.get::<_, i64>("online")? != 0 { "yes".into() } else { "no".into() },
            r.get::<_, String>("st")?,
            r.get::<_, String>("path")?,
        ]));
    }
    Ok(out)
}

fn audit_csv(conn: &Connection) -> SqlResult<String> {
    let mut out = row(&["id", "at", "action", "outcome", "reversible", "path"]
        .iter().map(|s| s.to_string()).collect::<Vec<_>>());
    let mut stmt = conn.prepare(
        "SELECT id AS id, at AS at, action AS action, outcome AS outcome, reversible AS rev, \
                COALESCE(path_raw,'') AS path FROM audit_log ORDER BY at DESC",
    )?;
    let mut rows = stmt.query([])?;
    while let Some(r) = rows.next()? {
        out.push_str(&row(&[
            r.get::<_, i64>("id")?.to_string(),
            r.get::<_, i64>("at")?.to_string(),
            r.get::<_, String>("action")?,
            r.get::<_, String>("outcome")?,
            if r.get::<_, i64>("rev")? != 0 { "yes".into() } else { "no".into() },
            r.get::<_, String>("path")?,
        ]));
    }
    Ok(out)
}

fn drives_csv(conn: &Connection) -> SqlResult<String> {
    let mut out = row(&["volume_id", "label", "fs_type", "online", "present_files", "total_bytes"]
        .iter().map(|s| s.to_string()).collect::<Vec<_>>());
    let mut stmt = conn.prepare(
        "SELECT v.id AS id, COALESCE(v.fs_label,'Unknown') AS label, \
                COALESCE(v.fs_type,'unknown') AS fs, v.is_online AS online, \
                COALESCE(v.total_bytes,0) AS total, \
                (SELECT COUNT(*) AS n FROM file f WHERE f.volume_id=v.id AND f.status='present') AS fc \
         FROM volume v ORDER BY v.fs_label",
    )?;
    let mut rows = stmt.query([])?;
    while let Some(r) = rows.next()? {
        out.push_str(&row(&[
            r.get::<_, i64>("id")?.to_string(),
            r.get::<_, String>("label")?,
            r.get::<_, String>("fs")?,
            if r.get::<_, i64>("online")? != 0 { "yes".into() } else { "no".into() },
            r.get::<_, i64>("fc")?.to_string(),
            r.get::<_, i64>("total")?.to_string(),
        ]));
    }
    Ok(out)
}

fn folders_csv(conn: &Connection) -> SqlResult<String> {
    let mut out = row(&["cluster_id", "file_count", "total_bytes", "member_count", "reclaimable_bytes", "drive", "online", "folder"]
        .iter().map(|s| s.to_string()).collect::<Vec<_>>());
    let mut stmt = conn.prepare(
        "SELECT fc.id AS cid, fc.file_count AS files, fc.total_bytes AS tb, fc.member_count AS mc, \
                fc.reclaimable_bytes AS rb, COALESCE(v.fs_label,'Unknown') AS drive, \
                m.is_online AS online, m.folder_path_raw AS folder \
         FROM folder_cluster fc JOIN folder_cluster_member m ON m.cluster_id=fc.id \
         JOIN volume v ON v.id=m.volume_id ORDER BY fc.reclaimable_bytes DESC, fc.id",
    )?;
    let mut rows = stmt.query([])?;
    while let Some(r) = rows.next()? {
        out.push_str(&row(&[
            r.get::<_, i64>("cid")?.to_string(),
            r.get::<_, i64>("files")?.to_string(),
            r.get::<_, i64>("tb")?.to_string(),
            r.get::<_, i64>("mc")?.to_string(),
            r.get::<_, i64>("rb")?.to_string(),
            r.get::<_, String>("drive")?,
            if r.get::<_, i64>("online")? != 0 { "yes".into() } else { "no".into() },
            r.get::<_, String>("folder")?,
        ]));
    }
    Ok(out)
}
