//! Repository layer — all index reads/writes go through here. The engine is
//! authoritative; this module owns the SQL so column names stay in lockstep with the
//! schema (CODING_RULES §4). Every aggregate is aliased and read by alias (lesson F020).
//! All writes assume the single-writer connection (CODING_RULES §6).

use crate::engine::identity::VolumeIdentity;
use crate::engine::safety::PresenceSummary;
use crate::model::*;
use rusqlite::{params, Connection, OptionalExtension, Result as SqlResult, Row};
use std::time::{SystemTime, UNIX_EPOCH};

/// Locale-neutral current epoch seconds.
pub fn now() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

// ===========================================================================
// VOLUMES
// ===========================================================================

/// Find-or-create a volume by its stable identity, mark it online, and open a
/// presence interval if it was previously offline. Returns the volume id.
pub fn upsert_online_volume(
    conn: &Connection,
    id: &VolumeIdentity,
    mount_point: &str,
) -> SqlResult<i64> {
    let label = id
        .fs_label
        .clone()
        .unwrap_or_else(|| mount_point.to_string());
    let ts = now();

    // Match on the strongest identity we have. Label-only volumes fall through to insert.
    let existing: Option<i64> = match (&id.volume_guid, id.volume_serial) {
        (Some(guid), _) => conn
            .query_row(
                "SELECT id AS id FROM volume WHERE volume_guid = ?1",
                params![guid],
                |r| r.get("id"),
            )
            .optional()?,
        (None, Some(serial)) => conn
            .query_row(
                "SELECT id AS id FROM volume WHERE volume_serial = ?1",
                params![serial],
                |r| r.get("id"),
            )
            .optional()?,
        _ => None,
    };

    let vid = if let Some(vid) = existing {
        let was_online: i64 = conn.query_row(
            "SELECT is_online AS o FROM volume WHERE id = ?1",
            params![vid],
            |r| r.get("o"),
        )?;
        conn.execute(
            "UPDATE volume SET is_online=1, current_mount_point=?2, last_seen_at=?3, \
             fs_label=COALESCE(fs_label, ?4) WHERE id=?1",
            params![vid, mount_point, ts, label],
        )?;
        if was_online == 0 {
            open_presence(conn, vid, mount_point, ts)?;
        }
        vid
    } else {
        conn.execute(
            "INSERT INTO volume (volume_guid, volume_serial, fs_label, fs_type, total_bytes, \
             current_mount_point, is_online, is_removable, first_seen_at, last_seen_at) \
             VALUES (?1,?2,?3,?4,?5,?6,1,?7,?8,?8)",
            params![
                id.volume_guid,
                id.volume_serial,
                label,
                id.fs_type,
                id.total_bytes,
                mount_point,
                id.is_removable as i64,
                ts
            ],
        )?;
        let vid = conn.last_insert_rowid();
        open_presence(conn, vid, mount_point, ts)?;
        vid
    };
    Ok(vid)
}

fn open_presence(conn: &Connection, vid: i64, mount: &str, ts: i64) -> SqlResult<()> {
    conn.execute(
        "INSERT INTO volume_presence (volume_id, mount_point, online_at) VALUES (?1,?2,?3)",
        params![vid, mount, ts],
    )?;
    Ok(())
}

/// Mark every volume that is currently online but NOT in `online_ids` as offline,
/// closing its presence interval. Called when the set of mounted volumes changes.
pub fn reconcile_offline_volumes(conn: &Connection, online_ids: &[i64]) -> SqlResult<()> {
    let ts = now();
    let mut stmt = conn.prepare("SELECT id AS id FROM volume WHERE is_online = 1")?;
    let online: Vec<i64> = stmt.query_map([], |r| r.get("id"))?.collect::<SqlResult<_>>()?;
    for vid in online {
        if !online_ids.contains(&vid) {
            conn.execute(
                "UPDATE volume SET is_online=0, current_mount_point=NULL WHERE id=?1",
                params![vid],
            )?;
            conn.execute(
                "UPDATE volume_presence SET offline_at=?2 \
                 WHERE volume_id=?1 AND offline_at IS NULL",
                params![vid, ts],
            )?;
            // Files on an offline volume are 'missing' from a reachability standpoint,
            // but we keep their status as last known; online_copies is derived live.
        }
    }
    Ok(())
}

pub fn list_volumes(conn: &Connection) -> SqlResult<Vec<VolumeView>> {
    let mut stmt = conn.prepare(
        "SELECT v.id AS id, v.fs_label AS label, v.fs_type AS fs_type, \
                v.current_mount_point AS mp, v.is_online AS online, v.is_removable AS removable, \
                v.total_bytes AS total, v.last_seen_at AS seen, \
                (SELECT COUNT(*) AS n FROM file f WHERE f.volume_id=v.id AND f.status='present') AS fc, \
                (SELECT COALESCE(SUM(f.size_bytes),0) FROM file f WHERE f.volume_id=v.id AND f.status='present') AS ib \
         FROM volume v ORDER BY v.is_online DESC, v.fs_label",
    )?;
    let rows = stmt
        .query_map([], |r| {
            Ok(VolumeView {
                volume_id: r.get("id")?,
                label: r.get::<_, Option<String>>("label")?.unwrap_or_else(|| "Unknown".into()),
                fs_type: r.get::<_, Option<String>>("fs_type")?.unwrap_or_else(|| "unknown".into()),
                mount_point: r.get("mp")?,
                is_online: r.get::<_, i64>("online")? != 0,
                is_removable: r.get::<_, i64>("removable")? != 0,
                total_bytes: r.get("total")?,
                indexed_bytes: r.get("ib")?,
                file_count: r.get("fc")?,
                last_seen_at: r.get("seen")?,
            })
        })?
        .collect::<SqlResult<_>>()?;
    Ok(rows)
}

// ===========================================================================
// FILES & CONTENT
// ===========================================================================

/// Minimal prior state used by the incremental reconciler to decide "re-hash or reuse".
pub struct ExistingFile {
    pub id: i64,
    pub size_bytes: i64,
    pub modified_at: Option<i64>,
    pub content_id: Option<i64>,
    pub hash_state: String,
}

pub fn lookup_file(
    conn: &Connection,
    volume_id: i64,
    path_key: &str,
) -> SqlResult<Option<ExistingFile>> {
    conn.query_row(
        "SELECT id AS id, size_bytes AS sz, modified_at AS m, content_id AS c, hash_state AS hs \
         FROM file WHERE volume_id=?1 AND path_key=?2",
        params![volume_id, path_key],
        |r| {
            Ok(ExistingFile {
                id: r.get("id")?,
                size_bytes: r.get("sz")?,
                modified_at: r.get("m")?,
                content_id: r.get("c")?,
                hash_state: r.get("hs")?,
            })
        },
    )
    .optional()
}

/// New/updated file metadata produced by the walker (volume-relative path).
pub struct FileMeta<'a> {
    pub volume_id: i64,
    pub path_raw: &'a str,
    pub path_key: &'a str,
    pub file_name: &'a str,
    pub ext: Option<&'a str>,
    pub size_bytes: i64,
    pub created_at: Option<i64>,
    pub modified_at: Option<i64>,
    pub is_hidden: bool,
    pub is_system: bool,
    pub is_reparse: bool,
    pub source_id: Option<i64>,
    pub scan_job_id: Option<i64>, // the scan session that indexed/confirmed this file
}

/// Insert a new file or refresh an existing one to 'present'. Returns (file_id, is_new).
/// Does NOT touch content/hash — hashing is a later stage.
pub fn upsert_file(conn: &Connection, m: &FileMeta) -> SqlResult<(i64, bool)> {
    let ts = now();
    if let Some(existing) = lookup_file(conn, m.volume_id, m.path_key)? {
        // Incremental reuse (CODING_RULES §7): if size & mtime are unchanged we PRESERVE
        // the existing content_id/hash_state so the file is not re-hashed. If either
        // changed, the stale hash is invalidated and the old content's counters refreshed.
        let changed =
            existing.size_bytes != m.size_bytes || existing.modified_at != m.modified_at;
        conn.execute(
            "UPDATE file SET path_raw=?2, file_name=?3, ext=?4, size_bytes=?5, created_at=?6, \
             modified_at=?7, is_hidden=?8, is_system=?9, is_reparse=?10, status='present', \
             last_seen_at=?11, removed_at=NULL, last_scan_job_id=COALESCE(?12, last_scan_job_id) WHERE id=?1",
            params![
                existing.id, m.path_raw, m.file_name, m.ext, m.size_bytes, m.created_at,
                m.modified_at, m.is_hidden as i64, m.is_system as i64, m.is_reparse as i64, ts,
                m.scan_job_id
            ],
        )?;
        if changed {
            conn.execute(
                "UPDATE file SET content_id=NULL, hash_state='none' WHERE id=?1",
                params![existing.id],
            )?;
            if let Some(old) = existing.content_id {
                recompute_counters(conn, old)?;
                refresh_cluster(conn, old)?;
            }
        }
        Ok((existing.id, false))
    } else {
        conn.execute(
            "INSERT INTO file (volume_id, path_raw, path_key, file_name, ext, size_bytes, \
             created_at, modified_at, is_hidden, is_system, is_reparse, status, hash_state, \
             first_seen_at, last_seen_at, source_id, last_scan_job_id) \
             VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,'present','none',?12,?12,?13,?14)",
            params![
                m.volume_id, m.path_raw, m.path_key, m.file_name, m.ext, m.size_bytes,
                m.created_at, m.modified_at, m.is_hidden as i64, m.is_system as i64,
                m.is_reparse as i64, ts, m.source_id, m.scan_job_id
            ],
        )?;
        Ok((conn.last_insert_rowid(), true))
    }
}

/// Mark files on a volume (optionally under a path-key prefix) that were not seen in
/// this scan as 'missing'. Knowledge is preserved — the row stays. Returns count.
pub fn mark_unseen_missing(
    conn: &Connection,
    volume_id: i64,
    scan_started_at: i64,
    path_prefix: Option<&str>,
) -> SqlResult<usize> {
    let ts = now();
    let n = match path_prefix {
        Some(prefix) => conn.execute(
            "UPDATE file SET status='missing', removed_at=?3 \
             WHERE volume_id=?1 AND status='present' AND last_seen_at < ?2 AND path_key LIKE ?4",
            params![volume_id, scan_started_at, ts, format!("{prefix}%")],
        )?,
        None => conn.execute(
            "UPDATE file SET status='missing', removed_at=?3 \
             WHERE volume_id=?1 AND status='present' AND last_seen_at < ?2",
            params![volume_id, scan_started_at, ts],
        )?,
    };
    Ok(n)
}

/// Find-or-create a content row for a confirmed full hash. Updates last_confirmed_at.
pub fn get_or_create_content(
    conn: &Connection,
    full_hash: &[u8; 32],
    size_bytes: i64,
    partial_hash: Option<&[u8]>,
) -> SqlResult<i64> {
    let ts = now();
    if let Some(cid) = conn
        .query_row(
            "SELECT id AS id FROM content WHERE full_hash = ?1",
            params![&full_hash[..]],
            |r| r.get("id"),
        )
        .optional()?
    {
        conn.execute(
            "UPDATE content SET last_confirmed_at=?2 WHERE id=?1",
            params![cid, ts],
        )?;
        return Ok(cid);
    }
    conn.execute(
        "INSERT INTO content (full_hash, size_bytes, partial_hash, first_indexed_at, \
         last_confirmed_at) VALUES (?1,?2,?3,?4,?4)",
        params![&full_hash[..], size_bytes, partial_hash, ts],
    )?;
    Ok(conn.last_insert_rowid())
}

/// Attach a hashed file to its content and set hash_state. Then refresh counters.
pub fn attach_content(
    conn: &Connection,
    file_id: i64,
    content_id: i64,
    full: bool,
) -> SqlResult<()> {
    let state = if full { "full" } else { "partial" };
    conn.execute(
        "UPDATE file SET content_id=?2, hash_state=?3 WHERE id=?1",
        params![file_id, content_id, state],
    )?;
    recompute_counters(conn, content_id)?;
    Ok(())
}

/// Record a per-file hashing/stat error without aborting the scan (fail loud — §2).
pub fn mark_file_error(conn: &Connection, file_id: i64, message: &str) -> SqlResult<()> {
    conn.execute(
        "UPDATE file SET status='error', hash_state='error', error_message=?2 WHERE id=?1",
        params![file_id, message],
    )?;
    Ok(())
}

/// Recompute the denormalized copy counters for a content row from ground truth.
/// known = all sightings ever; live = present; online = present on a mounted volume.
pub fn recompute_counters(conn: &Connection, content_id: i64) -> SqlResult<()> {
    conn.execute(
        "UPDATE content SET \
           known_copies  = (SELECT COUNT(*) AS n FROM file WHERE content_id=?1), \
           live_copies   = (SELECT COUNT(*) AS n FROM file WHERE content_id=?1 AND status='present'), \
           online_copies = (SELECT COUNT(*) AS n FROM file f JOIN volume v ON v.id=f.volume_id \
                            WHERE f.content_id=?1 AND f.status='present' AND v.is_online=1) \
         WHERE id=?1",
        params![content_id],
    )?;
    Ok(())
}

// ===========================================================================
// CLUSTERS
// ===========================================================================

/// Rebuild the cluster row for one content (called after counters change). A cluster
/// exists only when a content has >1 known copy. reclaimable = size * (members - 1).
pub fn refresh_cluster(conn: &Connection, content_id: i64) -> SqlResult<()> {
    let ts = now();
    let row: Option<(i64, i64)> = conn
        .query_row(
            "SELECT size_bytes AS sz, known_copies AS k FROM content WHERE id=?1",
            params![content_id],
            |r| Ok((r.get("sz")?, r.get("k")?)),
        )
        .optional()?;
    let Some((size, members)) = row else { return Ok(()) };

    if members <= 1 {
        conn.execute("DELETE FROM cluster WHERE content_id=?1", params![content_id])?;
        return Ok(());
    }
    let reclaimable = size * (members - 1);
    conn.execute(
        "INSERT INTO cluster (content_id, kind, confidence, member_count, reclaimable_bytes, \
         created_at, recomputed_at) VALUES (?1,'exact','high',?2,?3,?4,?4) \
         ON CONFLICT(content_id, kind) DO UPDATE SET \
           member_count=?2, reclaimable_bytes=?3, recomputed_at=?4",
        params![content_id, members, reclaimable, ts],
    )?;
    Ok(())
}

fn cluster_view_from_row(r: &Row) -> SqlResult<ClusterView> {
    Ok(ClusterView {
        cluster_id: r.get("cid")?,
        content_id: r.get("coid")?,
        size_bytes: r.get("sz")?,
        member_count: r.get("mc")?,
        reclaimable_bytes: r.get("rb")?,
        confidence: r.get("conf")?,
        kind: r.get("kind")?,
        members: Vec::new(),
    })
}

pub fn query_clusters(
    conn: &Connection,
    sort: ClusterSort,
    limit: i64,
    offset: i64,
) -> SqlResult<Vec<ClusterView>> {
    let order = match sort {
        ClusterSort::Reclaimable => "c.reclaimable_bytes DESC",
        ClusterSort::Size => "co.size_bytes DESC",
        ClusterSort::MemberCount => "c.member_count DESC",
    };
    let sql = format!(
        "SELECT c.id AS cid, c.content_id AS coid, co.size_bytes AS sz, c.member_count AS mc, \
                c.reclaimable_bytes AS rb, c.confidence AS conf, c.kind AS kind \
         FROM cluster c JOIN content co ON co.id=c.content_id \
         ORDER BY {order} LIMIT ?1 OFFSET ?2"
    );
    let mut stmt = conn.prepare(&sql)?;
    let mut clusters: Vec<ClusterView> = stmt
        .query_map(params![limit, offset], |r| cluster_view_from_row(r))?
        .collect::<SqlResult<_>>()?;

    for c in &mut clusters {
        c.members = members_of_content(conn, c.content_id)?;
    }
    Ok(clusters)
}

fn members_of_content(conn: &Connection, content_id: i64) -> SqlResult<Vec<FileView>> {
    let sql = format!(
        "SELECT {FILE_VIEW_COLS} FROM file f {FILE_VIEW_JOIN} \
         WHERE f.content_id=?1 ORDER BY f.status='present' DESC, v.is_online DESC, f.path_raw"
    );
    let mut stmt = conn.prepare(&sql)?;
    let rows = stmt
        .query_map(params![content_id], file_view_from_row)?
        .collect::<SqlResult<_>>()?;
    Ok(rows)
}

/// Compose an absolute path from a volume mount + volume-relative path, with EXACTLY one
/// separator. Critically avoids the Windows "D:" drive-relative trap: "D:\" stays "D:\",
/// but a bare "D:" gets a separator so we never produce "D:Secure" (which Explorer reads as
/// a path relative to D:'s current directory, not the root).
pub fn compose_abs_path(mount: &str, rel: &str) -> String {
    let rel = rel.trim_start_matches(['\\', '/']);
    let sep = if mount.ends_with('\\') || mount.ends_with('/') { "" } else { "\\" };
    format!("{mount}{sep}{rel}")
}

/// Build the optional media block. Returns None when no media_meta row joined (mprobe NULL).
fn media_meta_from_row(r: &Row) -> SqlResult<Option<crate::model::MediaMeta>> {
    let probe_state: Option<String> = r.get("mprobe")?;
    let Some(probe_state) = probe_state else { return Ok(None) };
    Ok(Some(crate::model::MediaMeta {
        probe_state,
        integrity: r.get::<_, Option<String>>("minteg")?.unwrap_or_else(|| "suspicious".into()),
        duration_s: r.get("mdur")?,
        width: r.get("mw")?,
        height: r.get("mh")?,
        bitrate: r.get("mbr")?,
        codec: r.get("mcodec")?,
        fps: r.get("mfps")?,
        has_audio: r.get::<_, Option<i64>>("maud")?.unwrap_or(0) != 0,
        stream_count: r.get("mstream")?,
        quality_grade: r.get::<_, Option<String>>("mgrade")?.unwrap_or_else(|| "unknown".into()),
        quality_warning: r.get::<_, Option<i64>>("mwarn")?.unwrap_or(0) != 0,
        warn_reason: r.get("mreason")?,
        analyzed_at: r.get::<_, Option<i64>>("manalyzed")?.unwrap_or(0),
    }))
}

/// Build a FileView. Requires the SELECT to alias the `FILE_VIEW_COLS` set (incl. media
/// columns via `FILE_VIEW_JOIN`). The absolute path is composed from the mount + relative
/// path only when the volume is online (offline files have a known path but no live location).
fn file_view_from_row(r: &Row) -> SqlResult<FileView> {
    let online = r.get::<_, i64>("online")? != 0;
    let mount: Option<String> = r.get("mount")?;
    let path_raw: String = r.get("path")?;
    // Compose the absolute path with an EXPLICIT separator. Do NOT use Path::join after
    // trimming the trailing backslash: "D:" is a drive-RELATIVE path on Windows, so
    // Path::new("D:").join("Secure") yields "D:Secure" (wrong). Joining the raw strings
    // with one separator gives the correct "D:\Secure\…".
    let abs_path = match (online, &mount) {
        (true, Some(m)) => Some(compose_abs_path(m, &path_raw)),
        _ => None,
    };
    Ok(FileView {
        file_id: r.get("id")?,
        volume_label: r.get("label")?,
        volume_online: online,
        drive: mount,
        path_raw,
        abs_path,
        size_bytes: r.get("sz")?,
        created_at: r.get("cra")?,
        modified_at: r.get("m")?,
        first_seen_at: r.get("fsa")?,
        last_seen_at: r.get("lsa")?,
        last_scan_job_id: r.get("sjid")?,
        status: r.get("st")?,
        hash_state: r.get("hs")?,
        media: media_meta_from_row(r)?,
    })
}

// ===========================================================================
// PRESENCE / HISTORICAL INTELLIGENCE
// ===========================================================================

pub fn presence_summary(conn: &Connection, content_id: i64) -> SqlResult<PresenceView> {
    let online: u32 = conn.query_row(
        "SELECT COUNT(*) AS n FROM file f JOIN volume v ON v.id=f.volume_id \
         WHERE f.content_id=?1 AND f.status='present' AND v.is_online=1",
        params![content_id],
        |r| r.get("n"),
    )?;
    let offline: u32 = conn.query_row(
        "SELECT COUNT(*) AS n FROM file f JOIN volume v ON v.id=f.volume_id \
         WHERE f.content_id=?1 AND f.status='present' AND v.is_online=0",
        params![content_id],
        |r| r.get("n"),
    )?;
    let deleted: u32 = conn.query_row(
        "SELECT COUNT(*) AS n FROM file WHERE content_id=?1 AND status IN ('deleted_by_user','missing')",
        params![content_id],
        |r| r.get("n"),
    )?;
    let summary = PresenceSummary {
        online_copies: online,
        offline_copies: offline,
        deleted_copies: deleted,
        last_online_volume: None,
    };
    Ok(PresenceView {
        content_id,
        online_copies: online,
        offline_copies: offline,
        deleted_copies: deleted,
        verdict: summary.verdict().to_string(),
        copies: members_of_content(conn, content_id)?,
    })
}

// ===========================================================================
// INDEX EXPLORER (query the persistent index, no scan running)
// ===========================================================================

pub fn search_index(conn: &Connection, q: &IndexQuery) -> SqlResult<Vec<FileView>> {
    // Build a parameterized WHERE — never string-concat user input (security).
    let mut where_sql = String::from("1=1");
    let mut binds: Vec<Box<dyn rusqlite::ToSql>> = Vec::new();
    if let Some(t) = &q.text {
        where_sql.push_str(" AND (f.file_name LIKE ?  OR f.path_key LIKE ?)");
        let like = format!("%{}%", t.to_lowercase());
        binds.push(Box::new(like.clone()));
        binds.push(Box::new(like));
    }
    if let Some(v) = q.volume_id {
        where_sql.push_str(" AND f.volume_id = ?");
        binds.push(Box::new(v));
    }
    // File-type filter: prefer the checkbox `exts` list (IN), fall back to legacy `ext`.
    if !q.exts.is_empty() {
        let placeholders = q.exts.iter().map(|_| "?").collect::<Vec<_>>().join(",");
        where_sql.push_str(&format!(" AND f.ext IN ({placeholders})"));
        for e in &q.exts {
            binds.push(Box::new(e.to_lowercase()));
        }
    } else if let Some(e) = &q.ext {
        where_sql.push_str(" AND f.ext = ?");
        binds.push(Box::new(e.to_lowercase()));
    }
    if let Some(s) = &q.status {
        where_sql.push_str(" AND f.status = ?");
        binds.push(Box::new(s.clone()));
    }
    if let Some(sz) = q.min_size_bytes {
        where_sql.push_str(" AND f.size_bytes >= ?");
        binds.push(Box::new(sz));
    }
    if let Some(sz) = q.max_size_bytes {
        where_sql.push_str(" AND f.size_bytes <= ?");
        binds.push(Box::new(sz));
    }
    if let Some(t) = q.scanned_after {
        where_sql.push_str(" AND f.last_seen_at >= ?");
        binds.push(Box::new(t));
    }
    if let Some(t) = q.scanned_before {
        where_sql.push_str(" AND f.last_seen_at <= ?");
        binds.push(Box::new(t));
    }
    // Media filters (LEFT JOIN media_meta mm). These match only files that have been
    // analyzed; `only_media` narrows to analyzed media, the others narrow further.
    // Integrity multi-select: prefer the `integrities` list (IN), fall back to legacy single.
    if !q.integrities.is_empty() {
        let placeholders = q.integrities.iter().map(|_| "?").collect::<Vec<_>>().join(",");
        where_sql.push_str(&format!(" AND mm.integrity IN ({placeholders})"));
        for s in &q.integrities {
            binds.push(Box::new(s.clone()));
        }
    } else if let Some(integ) = &q.integrity {
        where_sql.push_str(" AND mm.integrity = ?");
        binds.push(Box::new(integ.clone()));
    }
    if q.only_warnings {
        where_sql.push_str(" AND mm.quality_warning = 1");
    }
    if q.only_media {
        where_sql.push_str(" AND mm.file_id IS NOT NULL");
    }
    if q.only_duplicates {
        where_sql.push_str(" AND f.content_id IN (SELECT content_id FROM cluster)");
    }
    // Sort: map the requested key to a FIXED column (whitelist — never interpolate raw
    // user input into SQL). Default = size DESC. A stable tiebreak on id keeps paging sane.
    let order_col = match q.sort_key.as_deref().unwrap_or("size") {
        "name" => "f.file_name",
        "path" => "f.path_key",
        "created" => "f.created_at",
        "modified" => "f.modified_at",
        "scanned" => "f.last_seen_at",
        "duration" => "mm.duration_s",
        "bitrate" => "mm.bitrate",
        "resolution" => "(COALESCE(mm.width,0)*COALESCE(mm.height,0))",
        // Integrity/quality are TEXT; rank them by severity (mirrors INTEG_RANK in
        // MediaCells.tsx) so sorting is meaningful, not alphabetical. NULL (unanalyzed,
        // via the LEFT JOIN) ranks 0 → parks below every graded row.
        "integrity" => "CASE mm.integrity WHEN 'healthy' THEN 4 WHEN 'suspicious' THEN 3 WHEN 'partial' THEN 2 WHEN 'corrupted' THEN 1 WHEN 'unreadable' THEN 0 ELSE 0 END",
        "quality" => "CASE mm.quality_grade WHEN 'good' THEN 4 WHEN 'fair' THEN 3 WHEN 'poor' THEN 2 WHEN 'unknown' THEN 1 ELSE 0 END",
        _ => "f.size_bytes",
    };
    let order_dir = if q.sort_dir.as_deref() == Some("asc") { "ASC" } else { "DESC" };
    let sql = format!(
        "SELECT {FILE_VIEW_COLS} \
         FROM file f {FILE_VIEW_JOIN} \
         WHERE {where_sql} ORDER BY {order_col} {order_dir}, f.id {order_dir} LIMIT ? OFFSET ?"
    );
    binds.push(Box::new(q.limit.max(1).min(5000)));
    binds.push(Box::new(q.offset.max(0)));

    let mut stmt = conn.prepare(&sql)?;
    let params_ref: Vec<&dyn rusqlite::ToSql> = binds.iter().map(|b| b.as_ref()).collect();
    let rows = stmt
        .query_map(params_ref.as_slice(), file_view_from_row)?
        .collect::<SqlResult<_>>()?;
    Ok(rows)
}

pub fn index_stats(conn: &Connection) -> SqlResult<IndexStats> {
    conn.query_row(
        "SELECT \
          (SELECT COUNT(*) AS n FROM file) AS total, \
          (SELECT COUNT(*) AS n FROM file WHERE status='present') AS present, \
          (SELECT COUNT(*) AS n FROM content) AS contents, \
          (SELECT COUNT(*) AS n FROM cluster) AS clusters, \
          (SELECT COALESCE(SUM(reclaimable_bytes),0) AS n FROM cluster) AS reclaim, \
          (SELECT COUNT(*) AS n FROM volume) AS vols, \
          (SELECT COUNT(*) AS n FROM volume WHERE is_online=1) AS onvols",
        [],
        |r| {
            Ok(IndexStats {
                total_files: r.get("total")?,
                present_files: r.get("present")?,
                distinct_contents: r.get("contents")?,
                duplicate_clusters: r.get("clusters")?,
                reclaimable_bytes: r.get("reclaim")?,
                known_volumes: r.get("vols")?,
                online_volumes: r.get("onvols")?,
            })
        },
    )
}

// ===========================================================================
// AUDIT (append-only; write intent BEFORE the action — lesson ORDER-001)
// ===========================================================================

pub fn audit_intent(
    conn: &Connection,
    action: &str,
    file_id: Option<i64>,
    content_id: Option<i64>,
    volume_id: Option<i64>,
    path_raw: Option<&str>,
    reversible: bool,
    detail_json: Option<&str>,
) -> SqlResult<i64> {
    conn.execute(
        "INSERT INTO audit_log (at, action, outcome, file_id, content_id, volume_id, path_raw, \
         detail_json, reversible) VALUES (?1,?2,'intent',?3,?4,?5,?6,?7,?8)",
        params![now(), action, file_id, content_id, volume_id, path_raw, detail_json, reversible as i64],
    )?;
    Ok(conn.last_insert_rowid())
}

pub fn audit_outcome(conn: &Connection, audit_id: i64, outcome: &str) -> SqlResult<()> {
    conn.execute(
        "UPDATE audit_log SET outcome=?2 WHERE id=?1",
        params![audit_id, outcome],
    )?;
    Ok(())
}

// ===========================================================================
// FOLDER CLUSTERS (duplicate folders)
// ===========================================================================

pub fn query_folder_clusters(conn: &Connection, limit: i64) -> SqlResult<Vec<FolderClusterView>> {
    let mut stmt = conn.prepare(
        "SELECT id AS id, file_count AS fc, total_bytes AS tb, member_count AS mc, \
                reclaimable_bytes AS rb FROM folder_cluster \
         ORDER BY reclaimable_bytes DESC LIMIT ?1",
    )?;
    let mut clusters: Vec<FolderClusterView> = stmt
        .query_map(params![limit], |r| {
            Ok(FolderClusterView {
                cluster_id: r.get("id")?,
                file_count: r.get("fc")?,
                total_bytes: r.get("tb")?,
                member_count: r.get("mc")?,
                reclaimable_bytes: r.get("rb")?,
                members: Vec::new(),
            })
        })?
        .collect::<SqlResult<_>>()?;
    for c in &mut clusters {
        c.members = folder_members(conn, c.cluster_id)?;
    }
    Ok(clusters)
}

fn folder_members(conn: &Connection, cluster_id: i64) -> SqlResult<Vec<FolderMemberView>> {
    let mut stmt = conn.prepare(
        "SELECT COALESCE(v.fs_label,'Unknown') AS label, m.is_online AS online, \
                m.folder_path_raw AS folder \
         FROM folder_cluster_member m JOIN volume v ON v.id=m.volume_id \
         WHERE m.cluster_id=?1 ORDER BY m.is_online DESC, m.folder_path_raw",
    )?;
    let rows = stmt
        .query_map(params![cluster_id], |r| {
            Ok(FolderMemberView {
                volume_label: r.get("label")?,
                volume_online: r.get::<_, i64>("online")? != 0,
                folder_path_raw: r.get("folder")?,
            })
        })?
        .collect::<SqlResult<_>>()?;
    Ok(rows)
}

// ===========================================================================
// SELECTION INPUTS — every file that belongs to an exact cluster, for the rule engine
// ===========================================================================

pub fn selection_inputs(conn: &Connection) -> SqlResult<Vec<crate::engine::selection::Candidate>> {
    let mut stmt = conn.prepare(
        "SELECT f.id AS id, f.content_id AS cid, f.volume_id AS vid, v.is_online AS online, \
                (f.status='present') AS present, f.path_raw AS path, f.modified_at AS m \
         FROM file f JOIN volume v ON v.id=f.volume_id \
         WHERE f.content_id IN (SELECT content_id FROM cluster)",
    )?;
    let rows = stmt
        .query_map([], |r| {
            Ok(crate::engine::selection::Candidate {
                file_id: r.get("id")?,
                content_id: r.get::<_, Option<i64>>("cid")?.unwrap_or(0),
                volume_id: r.get("vid")?,
                online: r.get::<_, i64>("online")? != 0,
                present: r.get::<_, i64>("present")? != 0,
                path_raw: r.get("path")?,
                modified_at: r.get("m")?,
            })
        })?
        .collect::<SqlResult<_>>()?;
    Ok(rows)
}

pub fn list_audit(conn: &Connection, limit: i64) -> SqlResult<Vec<AuditView>> {
    let mut stmt = conn.prepare(
        "SELECT id AS id, at AS at, action AS action, outcome AS outcome, path_raw AS path, \
                reversible AS rev FROM audit_log ORDER BY at DESC LIMIT ?1",
    )?;
    let rows = stmt
        .query_map(params![limit], |r| {
            Ok(AuditView {
                id: r.get("id")?,
                at: r.get("at")?,
                action: r.get("action")?,
                outcome: r.get("outcome")?,
                path_raw: r.get("path")?,
                reversible: r.get::<_, i64>("rev")? != 0,
            })
        })?
        .collect::<SqlResult<_>>()?;
    Ok(rows)
}

// ===========================================================================
// SCAN SESSIONS · TIMELINE LOG · FOLDER TRAVERSAL (observability & history)
// ===========================================================================

/// Append one timeline entry to a scan session (minute-tick or lifecycle/stage event).
pub fn log_event(
    conn: &Connection,
    job_id: i64,
    elapsed_ms: i64,
    kind: &str,
    message: &str,
    files_processed: i64,
    detail_json: Option<&str>,
) -> SqlResult<()> {
    conn.execute(
        "INSERT INTO scan_log_event (scan_job_id, at, elapsed_ms, kind, message, files_processed, detail_json) \
         VALUES (?1,?2,?3,?4,?5,?6,?7)",
        params![job_id, now(), elapsed_ms, kind, message, files_processed, detail_json],
    )?;
    Ok(())
}

pub fn folder_enter(
    conn: &Connection,
    job_id: i64,
    volume_id: i64,
    folder_path_raw: &str,
    depth: i64,
) -> SqlResult<()> {
    conn.execute(
        "INSERT INTO folder_traversal (scan_job_id, volume_id, folder_path_raw, depth, state, started_at) \
         VALUES (?1,?2,?3,?4,'in_progress',?5) \
         ON CONFLICT(scan_job_id, folder_path_raw) DO UPDATE SET state='in_progress', started_at=?5",
        params![job_id, volume_id, folder_path_raw, depth, now()],
    )?;
    Ok(())
}

pub fn folder_complete(
    conn: &Connection,
    job_id: i64,
    folder_path_raw: &str,
    file_count: i64,
    duration_ms: i64,
) -> SqlResult<()> {
    conn.execute(
        "UPDATE folder_traversal SET state='completed', file_count=?3, completed_at=?4, duration_ms=?5 \
         WHERE scan_job_id=?1 AND folder_path_raw=?2",
        params![job_id, folder_path_raw, file_count, now(), duration_ms],
    )?;
    Ok(())
}

/// Mark a folder skipped/failed/unavailable (upsert; used for reparse skips & read errors).
pub fn folder_mark(
    conn: &Connection,
    job_id: i64,
    volume_id: i64,
    folder_path_raw: &str,
    depth: i64,
    state: &str,
) -> SqlResult<()> {
    conn.execute(
        "INSERT INTO folder_traversal (scan_job_id, volume_id, folder_path_raw, depth, state) \
         VALUES (?1,?2,?3,?4,?5) \
         ON CONFLICT(scan_job_id, folder_path_raw) DO UPDATE SET state=?5",
        params![job_id, volume_id, folder_path_raw, depth, state],
    )?;
    Ok(())
}

/// Inputs the scan service supplies when finalizing a session; derived metrics
/// (folders/subfolders/total_bytes/duplicates) are computed here from ground truth.
pub struct SessionMetrics<'a> {
    pub job_id: i64,
    pub started_at: i64,
    pub state: &'a str,
    pub mode: &'a str,
    pub drive_label: Option<&'a str>,
    pub sources_json: Option<&'a str>,
    pub filters_json: Option<&'a str>,
    pub profile_name: Option<&'a str>,
    pub skipped_count: i64,
    pub error_count: i64,
    pub scanning_ms: i64,
    pub hashing_ms: i64,
    pub resumable: bool,
}

pub fn finalize_scan_session(conn: &Connection, m: &SessionMetrics) -> SqlResult<()> {
    let folders: i64 = conn.query_row(
        "SELECT COUNT(*) AS n FROM folder_traversal WHERE scan_job_id=?1 AND state='completed'",
        params![m.job_id], |r| r.get("n"))?;
    let subfolders: i64 = conn.query_row(
        "SELECT COUNT(*) AS n FROM folder_traversal WHERE scan_job_id=?1 AND state='completed' AND depth>0",
        params![m.job_id], |r| r.get("n"))?;
    let total_bytes: i64 = conn.query_row(
        "SELECT COALESCE(SUM(size_bytes),0) AS n FROM file WHERE last_scan_job_id=?1",
        params![m.job_id], |r| r.get("n"))?;
    let dups: i64 = conn.query_row(
        "SELECT COUNT(DISTINCT c.id) AS n FROM cluster c \
         JOIN file f ON f.content_id=c.content_id WHERE f.last_scan_job_id=?1",
        params![m.job_id], |r| r.get("n"))?;
    conn.execute(
        "UPDATE scan_job SET mode=?2, drive_label=?3, sources_json=?4, filters_json=?5, \
         profile_name=?6, folders_traversed=?7, subfolders_traversed=?8, skipped_count=?9, \
         error_count=?10, duplicates_found=?11, total_bytes=?12, scanning_ms=?13, hashing_ms=?14, \
         resumable=?15, build_version=?16 WHERE id=?1",
        params![
            m.job_id, m.mode, m.drive_label, m.sources_json, m.filters_json, m.profile_name,
            folders, subfolders, m.skipped_count, m.error_count, dups, total_bytes,
            m.scanning_ms, m.hashing_ms, m.resumable as i64, env!("CARGO_PKG_VERSION")
        ],
    )?;
    Ok(())
}

fn session_view_from_row(r: &Row) -> SqlResult<ScanSessionView> {
    let started: i64 = r.get("started")?;
    let finished: Option<i64> = r.get("finished")?;
    let duration_ms = finished.map(|f| (f - started).max(0) * 1000).unwrap_or(0);
    Ok(ScanSessionView {
        job_id: r.get("id")?,
        state: r.get("state")?,
        mode: r.get("mode")?,
        drive_label: r.get("drive")?,
        sources_json: r.get("sources")?,
        started_at: started,
        finished_at: finished,
        duration_ms,
        files_seen: r.get("seen")?,
        files_hashed: r.get("hashed")?,
        bytes_hashed: r.get("bytes")?,
        total_bytes: r.get("total")?,
        folders_traversed: r.get("folders")?,
        subfolders_traversed: r.get("subfolders")?,
        skipped_count: r.get("skipped")?,
        error_count: r.get("errs")?,
        duplicates_found: r.get("dups")?,
        scanning_ms: r.get("scanms")?,
        hashing_ms: r.get("hashms")?,
        resumable: r.get::<_, i64>("resumable")? != 0,
        profile_name: r.get("profile")?,
        build_version: r.get("build")?,
        error_message: r.get("errmsg")?,
    })
}

const SESSION_COLS: &str = "id AS id, state AS state, mode AS mode, drive_label AS drive, \
    sources_json AS sources, started_at AS started, finished_at AS finished, files_seen AS seen, \
    files_hashed AS hashed, bytes_hashed AS bytes, total_bytes AS total, folders_traversed AS folders, \
    subfolders_traversed AS subfolders, skipped_count AS skipped, error_count AS errs, \
    duplicates_found AS dups, scanning_ms AS scanms, hashing_ms AS hashms, resumable AS resumable, \
    profile_name AS profile, build_version AS build, error_message AS errmsg";

pub fn query_scan_sessions(conn: &Connection, limit: i64) -> SqlResult<Vec<ScanSessionView>> {
    let sql = format!("SELECT {SESSION_COLS} FROM scan_job ORDER BY started_at DESC LIMIT ?1");
    let mut stmt = conn.prepare(&sql)?;
    let rows = stmt
        .query_map(params![limit], session_view_from_row)?
        .collect::<SqlResult<_>>()?;
    Ok(rows)
}

pub fn scan_session(conn: &Connection, job_id: i64) -> SqlResult<Option<ScanSessionView>> {
    let sql = format!("SELECT {SESSION_COLS} FROM scan_job WHERE id=?1");
    conn.query_row(&sql, params![job_id], session_view_from_row).optional()
}

pub fn scan_session_events(conn: &Connection, job_id: i64, limit: i64) -> SqlResult<Vec<ScanLogEvent>> {
    let mut stmt = conn.prepare(
        "SELECT scan_job_id AS jid, at AS at, elapsed_ms AS ems, kind AS kind, message AS msg, \
                files_processed AS files FROM scan_log_event \
         WHERE scan_job_id=?1 ORDER BY at DESC, id DESC LIMIT ?2",
    )?;
    let rows = stmt
        .query_map(params![job_id, limit], |r| {
            Ok(ScanLogEvent {
                job_id: r.get("jid")?,
                at: r.get("at")?,
                elapsed_ms: r.get("ems")?,
                kind: r.get("kind")?,
                message: r.get("msg")?,
                files_processed: r.get("files")?,
            })
        })?
        .collect::<SqlResult<_>>()?;
    Ok(rows)
}

pub fn scan_session_folders(conn: &Connection, job_id: i64, limit: i64) -> SqlResult<Vec<FolderTraversalView>> {
    let mut stmt = conn.prepare(
        "SELECT folder_path_raw AS path, depth AS depth, state AS state, file_count AS fc, \
                started_at AS started, completed_at AS completed, duration_ms AS dms \
         FROM folder_traversal WHERE scan_job_id=?1 ORDER BY folder_path_raw LIMIT ?2",
    )?;
    let rows = stmt
        .query_map(params![job_id, limit], |r| {
            Ok(FolderTraversalView {
                folder_path_raw: r.get("path")?,
                depth: r.get("depth")?,
                state: r.get("state")?,
                file_count: r.get("fc")?,
                started_at: r.get("started")?,
                completed_at: r.get("completed")?,
                duration_ms: r.get("dms")?,
            })
        })?
        .collect::<SqlResult<_>>()?;
    Ok(rows)
}

// ===========================================================================
// PERCEPTUAL IMAGE HASHES (similar-image detection)
// ===========================================================================

/// Image extensions the `image` crate can decode (for the similar-image pass).
const IMAGE_EXTS: &str = "'jpg','jpeg','jfif','png','gif','bmp','tiff','tif','webp'";

/// Aliased column list that `file_view_from_row` expects (single source of truth).
/// Any FROM using these columns MUST also include `{FILE_VIEW_JOIN}` so the media columns
/// resolve (LEFT JOIN — files without media metadata simply yield NULLs => `media: None`).
const FILE_VIEW_COLS: &str = "f.id AS id, COALESCE(v.fs_label,'Unknown') AS label, \
    v.is_online AS online, v.current_mount_point AS mount, f.path_raw AS path, \
    f.size_bytes AS sz, f.created_at AS cra, f.modified_at AS m, f.first_seen_at AS fsa, \
    f.last_seen_at AS lsa, f.last_scan_job_id AS sjid, f.status AS st, f.hash_state AS hs, \
    mm.probe_state AS mprobe, mm.integrity AS minteg, mm.duration_s AS mdur, mm.width AS mw, \
    mm.height AS mh, mm.bitrate AS mbr, mm.codec AS mcodec, mm.fps AS mfps, mm.has_audio AS maud, \
    mm.stream_count AS mstream, mm.quality_grade AS mgrade, mm.quality_warning AS mwarn, \
    mm.warn_reason AS mreason, mm.analyzed_at AS manalyzed";

/// The join that backs the media columns in `FILE_VIEW_COLS`.
const FILE_VIEW_JOIN: &str = "JOIN volume v ON v.id=f.volume_id \
    LEFT JOIN media_meta mm ON mm.file_id=f.id";

/// Present, online image files that don't yet have a perceptual hash (incremental).
pub fn images_needing_hash(conn: &Connection) -> SqlResult<Vec<(i64, String)>> {
    let sql = format!(
        "SELECT f.id AS id, v.current_mount_point AS mount, f.path_raw AS path \
         FROM file f JOIN volume v ON v.id=f.volume_id \
         WHERE f.status='present' AND v.is_online=1 AND v.current_mount_point IS NOT NULL \
           AND lower(f.ext) IN ({IMAGE_EXTS}) \
           AND f.id NOT IN (SELECT file_id FROM image_hash)"
    );
    let mut stmt = conn.prepare(&sql)?;
    let rows = stmt
        .query_map([], |r| {
            let mount: String = r.get("mount")?;
            let path: String = r.get("path")?;
            Ok((r.get::<_, i64>("id")?, compose_abs_path(&mount, &path)))
        })?
        .collect::<SqlResult<_>>()?;
    Ok(rows)
}

pub fn store_image_hash(conn: &Connection, file_id: i64, hash: Option<i64>, state: &str) -> SqlResult<()> {
    conn.execute(
        "INSERT OR REPLACE INTO image_hash (file_id, hash, algo, state, hashed_at) \
         VALUES (?1,?2,'dhash8',?3,?4)",
        params![file_id, hash, state, now()],
    )?;
    Ok(())
}

/// All cached image hashes for present, online files: (file_id, hash, size_bytes).
pub fn load_image_hashes(conn: &Connection) -> SqlResult<Vec<(i64, i64, i64)>> {
    let mut stmt = conn.prepare(
        "SELECT f.id AS id, ih.hash AS h, f.size_bytes AS sz \
         FROM image_hash ih JOIN file f ON f.id=ih.file_id JOIN volume v ON v.id=f.volume_id \
         WHERE ih.state='ok' AND ih.hash IS NOT NULL AND f.status='present' AND v.is_online=1",
    )?;
    let rows = stmt
        .query_map([], |r| Ok((r.get("id")?, r.get("h")?, r.get("sz")?)))?
        .collect::<SqlResult<_>>()?;
    Ok(rows)
}

// --- Similar-VIDEO perceptual signatures (initiative #4) — mirrors the image-hash helpers ---

/// Present, online video files without a perceptual signature yet (incremental). (id, abs_path).
pub fn videos_needing_hash(conn: &Connection) -> SqlResult<Vec<(i64, String)>> {
    let sql = format!(
        "SELECT f.id AS id, v.current_mount_point AS mount, f.path_raw AS path \
         FROM file f JOIN volume v ON v.id=f.volume_id \
         WHERE f.status='present' AND v.is_online=1 AND v.current_mount_point IS NOT NULL \
           AND lower(f.ext) IN ({VIDEO_EXTS}) AND f.id NOT IN (SELECT file_id FROM video_hash)"
    );
    let mut stmt = conn.prepare(&sql)?;
    let rows = stmt
        .query_map([], |r| {
            let mount: String = r.get("mount")?;
            let path: String = r.get("path")?;
            Ok((r.get::<_, i64>("id")?, compose_abs_path(&mount, &path)))
        })?
        .collect::<SqlResult<_>>()?;
    Ok(rows)
}

/// Persist (or clear, on error) one video's keyframe-signature blob.
pub fn store_video_signature(conn: &Connection, file_id: i64, signature: Option<&[u8]>, keyframes: i64, state: &str) -> SqlResult<()> {
    conn.execute(
        "INSERT OR REPLACE INTO video_hash (file_id, signature, keyframes, algo, state, hashed_at) \
         VALUES (?1,?2,?3,'kf-dhash8',?4,?5)",
        params![file_id, signature, keyframes, state, now()],
    )?;
    Ok(())
}

/// All cached signatures for present, online videos: (file_id, signature_blob, size_bytes).
pub fn load_video_signatures(conn: &Connection) -> SqlResult<Vec<(i64, Vec<u8>, i64)>> {
    let mut stmt = conn.prepare(
        "SELECT f.id AS id, vh.signature AS sig, f.size_bytes AS sz \
         FROM video_hash vh JOIN file f ON f.id=vh.file_id JOIN volume v ON v.id=f.volume_id \
         WHERE vh.state='ok' AND vh.signature IS NOT NULL AND f.status='present' AND v.is_online=1",
    )?;
    let rows = stmt
        .query_map([], |r| Ok((r.get("id")?, r.get::<_, Vec<u8>>("sig")?, r.get("sz")?)))?
        .collect::<SqlResult<_>>()?;
    Ok(rows)
}

pub fn files_by_ids(conn: &Connection, ids: &[i64]) -> SqlResult<Vec<FileView>> {
    if ids.is_empty() {
        return Ok(Vec::new());
    }
    let placeholders = ids.iter().map(|_| "?").collect::<Vec<_>>().join(",");
    let sql = format!(
        "SELECT {FILE_VIEW_COLS} FROM file f {FILE_VIEW_JOIN} WHERE f.id IN ({placeholders})"
    );
    let mut stmt = conn.prepare(&sql)?;
    let params_ref: Vec<&dyn rusqlite::ToSql> = ids.iter().map(|i| i as &dyn rusqlite::ToSql).collect();
    let rows = stmt
        .query_map(params_ref.as_slice(), file_view_from_row)?
        .collect::<SqlResult<_>>()?;
    Ok(rows)
}

// ===========================================================================
// FOLDER TREE (lazy, collapsible TreeSize-style view of the indexed structure)
// ===========================================================================

/// Immediate child folders of `parent_rel` on a volume, each with its RECURSIVE size,
/// file count and subfolder count — the data behind the collapsible folder tree. Lazy:
/// the UI calls this once per expanded node, so we only aggregate the requested subtree.
/// `parent_rel` is the volume-relative display path ("" = volume root).
pub fn folder_children(
    conn: &Connection,
    volume_id: i64,
    parent_rel: &str,
) -> SqlResult<Vec<crate::model::FolderNode>> {
    use crate::engine::pathkey::make_key;
    use std::collections::{HashMap, HashSet};

    // Bound the scan to the subtree via the normalized key prefix (path_key is lowercased,
    // backslash-separated, no trailing slash — so a child's key starts with "parent\").
    let parent_key = make_key(parent_rel);
    let (where_extra, like): (&str, String) = if parent_key.is_empty() {
        ("", String::new())
    } else {
        // Escape LIKE metacharacters so the prefix genuinely BOUNDS the scan to this subtree
        // (a folder like `My_Photos` would otherwise widen the match via `_`). '|' is the
        // escape char — it can never appear in a Windows path, so it is unambiguous here.
        let esc = parent_key.replace('|', "||").replace('%', "|%").replace('_', "|_");
        (" AND f.path_key LIKE ?2 ESCAPE '|'", format!("{esc}\\%"))
    };
    let sql = format!(
        "SELECT f.path_raw AS path, f.size_bytes AS sz \
         FROM file f WHERE f.volume_id=?1 AND f.status='present'{where_extra}"
    );

    // Split a path into non-empty segments, tolerating either separator / a leading slash.
    fn segs(p: &str) -> Vec<&str> {
        p.split(['\\', '/']).filter(|s| !s.is_empty()).collect()
    }
    let parent_segs: Vec<String> = segs(parent_rel).iter().map(|s| s.to_lowercase()).collect();

    struct Agg {
        name: String,
        bytes: i64,
        files: i64,
        subfolders: HashSet<String>, // lowercased relative-to-child folder paths
    }
    let mut map: HashMap<String, Agg> = HashMap::new();

    let mut stmt = conn.prepare(&sql)?;
    let mut handle = |path: String, sz: i64| {
        let parts = segs(&path);
        // The file must live strictly below parent (>= one child folder + the filename).
        if parts.len() < parent_segs.len() + 2 {
            return;
        }
        // Verify the parent prefix matches case-insensitively (path_key prefix already did
        // the heavy filtering; this guards the in-memory split).
        for (i, ps) in parent_segs.iter().enumerate() {
            if parts[i].to_lowercase() != *ps {
                return;
            }
        }
        let rem = &parts[parent_segs.len()..]; // [child, sub.., filename]
        let child_disp = rem[0].to_string();
        let child_key = child_disp.to_lowercase();
        let entry = map.entry(child_key).or_insert_with(|| Agg {
            name: child_disp,
            bytes: 0,
            files: 0,
            subfolders: HashSet::new(),
        });
        entry.bytes += sz;
        entry.files += 1;
        // Folders strictly under child = cumulative joins of rem[1..len-1].
        let last = rem.len() - 1; // filename index
        let mut acc = String::new();
        for s in &rem[1..last] {
            if !acc.is_empty() {
                acc.push('\\');
            }
            acc.push_str(&s.to_lowercase());
            entry.subfolders.insert(acc.clone());
        }
    };
    if parent_key.is_empty() {
        let mut rows = stmt.query(params![volume_id])?;
        while let Some(r) = rows.next()? {
            handle(r.get("path")?, r.get("sz")?);
        }
    } else {
        let mut rows = stmt.query(params![volume_id, like])?;
        while let Some(r) = rows.next()? {
            handle(r.get("path")?, r.get("sz")?);
        }
    }

    let sep = if parent_rel.trim_end_matches(['\\', '/']).is_empty() { "" } else { "\\" };
    let parent_disp = parent_rel.trim_end_matches(['\\', '/']);
    let mut out: Vec<crate::model::FolderNode> = map
        .into_values()
        .map(|a| {
            let subfolder_count = a.subfolders.len() as i64;
            crate::model::FolderNode {
                rel_path: format!("{parent_disp}{sep}{}", a.name),
                name: a.name,
                total_bytes: a.bytes,
                file_count: a.files,
                subfolder_count,
                has_children: subfolder_count > 0,
            }
        })
        .collect();
    // Largest first — the TreeSize convention that surfaces the space hogs.
    out.sort_by(|a, b| b.total_bytes.cmp(&a.total_bytes).then(a.name.to_lowercase().cmp(&b.name.to_lowercase())));
    Ok(out)
}

// ===========================================================================
// MEDIA METADATA (integrity + technical quality; ffprobe-backed probe pass)
// ===========================================================================

/// Video extensions the probe pass analyzes (the quality heuristics target video).
pub const VIDEO_EXTS: &str =
    "'mp4','m4v','mkv','avi','mov','wmv','flv','webm','mpg','mpeg','m2ts','ts','mts','3gp','vob','ogv','divx','asf','rm','rmvb'";

/// Present, online files that have NOT yet been analyzed (incremental, cached). Returns
/// (file_id, absolute_path, ext, size_bytes) so the engine can branch ffprobe-vs-generic.
/// When `ids` is `Some` the work is scoped to those rows (the UI passes the filtered/selected
/// result set) and ALL file types are eligible — the user can analyze whatever they picked.
/// When `ids` is `None` (the unscoped background pass) we restrict to VIDEO_EXTS so a global
/// run stays bounded and meaningful. `Some(empty)` => no work.
pub fn media_needing_probe_scoped(
    conn: &Connection,
    ids: Option<&[i64]>,
) -> SqlResult<Vec<(i64, String, String, i64)>> {
    let mut sql = format!(
        "SELECT f.id AS id, v.current_mount_point AS mount, f.path_raw AS path, \
                COALESCE(lower(f.ext),'') AS ext, f.size_bytes AS sz \
         FROM file f JOIN volume v ON v.id=f.volume_id \
         WHERE f.status='present' AND v.is_online=1 AND v.current_mount_point IS NOT NULL \
           AND f.id NOT IN (SELECT file_id FROM media_meta)"
    );
    let row = |r: &Row| -> SqlResult<(i64, String, String, i64)> {
        let mount: String = r.get("mount")?;
        let path: String = r.get("path")?;
        Ok((
            r.get::<_, i64>("id")?,
            compose_abs_path(&mount, &path),
            r.get::<_, String>("ext")?,
            r.get::<_, i64>("sz")?,
        ))
    };
    match ids {
        Some(ids) => {
            if ids.is_empty() {
                return Ok(Vec::new());
            }
            // Scoped: analyze every selected row regardless of extension.
            let ph = ids.iter().map(|_| "?").collect::<Vec<_>>().join(",");
            sql.push_str(&format!(" AND f.id IN ({ph})"));
            let mut stmt = conn.prepare(&sql)?;
            let params_ref: Vec<&dyn rusqlite::ToSql> = ids.iter().map(|i| i as &dyn rusqlite::ToSql).collect();
            let rows = stmt.query_map(params_ref.as_slice(), row)?.collect::<SqlResult<Vec<_>>>()?;
            Ok(rows)
        }
        None => {
            // Unscoped background pass: video only (stays bounded).
            sql.push_str(&format!(" AND lower(f.ext) IN ({VIDEO_EXTS})"));
            let mut stmt = conn.prepare(&sql)?;
            let rows = stmt.query_map([], row)?.collect::<SqlResult<Vec<_>>>()?;
            Ok(rows)
        }
    }
}

/// Persist one file's media metadata (idempotent; re-analysis overwrites).
pub fn store_media_meta(conn: &Connection, file_id: i64, m: &crate::model::MediaMeta) -> SqlResult<()> {
    conn.execute(
        "INSERT OR REPLACE INTO media_meta (file_id, probe_state, integrity, duration_s, width, \
            height, bitrate, codec, fps, has_audio, stream_count, quality_grade, quality_warning, \
            warn_reason, analyzed_at) \
         VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15)",
        params![
            file_id, m.probe_state, m.integrity, m.duration_s, m.width, m.height, m.bitrate,
            m.codec, m.fps, m.has_audio as i64, m.stream_count, m.quality_grade,
            m.quality_warning as i64, m.warn_reason, m.analyzed_at,
        ],
    )?;
    Ok(())
}

/// Cumulative cleanup evidence from the audit log + index (Reclaim Report, initiative #8).
pub fn reclaim_summary(conn: &Connection) -> SqlResult<crate::model::ReclaimSummary> {
    let one = |sql: &str| -> SqlResult<i64> { conn.query_row(sql, [], |r| r.get(0)) };
    Ok(crate::model::ReclaimSummary {
        bytes_reclaimed: one("SELECT COALESCE(SUM(size_bytes),0) FROM file WHERE status='deleted_by_user'")?,
        files_removed: one("SELECT COUNT(*) FROM file WHERE status='deleted_by_user'")?,
        recycled: one("SELECT COUNT(*) FROM audit_log WHERE action='recycle' AND outcome='success'")?,
        quarantined: one("SELECT COUNT(*) FROM audit_log WHERE action='quarantine' AND outcome='success'")?,
        permanently_deleted: one("SELECT COUNT(*) FROM audit_log WHERE action='permanent' AND outcome='success'")?,
        files_renamed: one("SELECT COUNT(*) FROM audit_log WHERE action='rename' AND outcome='success'")?,
        files_restored: one("SELECT COUNT(*) FROM audit_log WHERE action='restore' AND outcome='success'")?,
        still_reclaimable_bytes: one("SELECT COALESCE(SUM(reclaimable_bytes),0) FROM cluster")?,
        present_files: one("SELECT COUNT(*) FROM file WHERE status='present'")?,
        duplicate_clusters: one("SELECT COUNT(*) FROM cluster")?,
        generated_at: now(),
    })
}

/// Headline media counters: (total analyzed, quality warnings, integrity-compromised).
pub fn media_counts(conn: &Connection) -> SqlResult<(i64, i64, i64)> {
    conn.query_row(
        "SELECT \
           (SELECT COUNT(*) FROM media_meta) AS total, \
           (SELECT COUNT(*) FROM media_meta WHERE quality_warning=1) AS warns, \
           (SELECT COUNT(*) FROM media_meta WHERE integrity IN ('corrupted','unreadable','partial')) AS bad",
        [],
        |r| Ok((r.get("total")?, r.get("warns")?, r.get("bad")?)),
    )
}

/// Absolute path of a file when its volume is online (for thumbnails / reveal).
pub fn file_abs_path(conn: &Connection, file_id: i64) -> SqlResult<Option<String>> {
    conn.query_row(
        "SELECT v.current_mount_point AS mount, f.path_raw AS path \
         FROM file f JOIN volume v ON v.id=f.volume_id WHERE f.id=?1 AND v.is_online=1",
        params![file_id],
        |r| {
            let mount: Option<String> = r.get("mount")?;
            let path: String = r.get("path")?;
            Ok(mount.map(|m| compose_abs_path(&m, &path)))
        },
    )
    .optional()
    .map(|o| o.flatten())
}

/// Delete one scan-history session. FK CASCADE removes its log events + folder traversal;
/// indexed files keep their (now dangling, harmless) `last_scan_job_id`.
pub fn delete_scan_session(conn: &Connection, job_id: i64) -> SqlResult<()> {
    conn.execute("DELETE FROM scan_job WHERE id=?1", params![job_id])?;
    Ok(())
}

/// Clear the entire scan history. Returns the number of sessions removed.
pub fn clear_scan_history(conn: &Connection) -> SqlResult<usize> {
    conn.execute("DELETE FROM scan_job", [])
}

#[cfg(test)]
mod tests {
    use super::compose_abs_path;

    #[test]
    fn abs_path_handles_drive_root_separator() {
        // The reported bug: a drive root must keep its backslash, never become "D:Secure".
        assert_eq!(compose_abs_path("D:\\", "Secure\\f.bin"), "D:\\Secure\\f.bin");
        assert_eq!(compose_abs_path("D:\\", "\\Secure\\f.bin"), "D:\\Secure\\f.bin");
        // A bare "D:" (no trailing slash) must gain a separator (the drive-relative trap).
        assert_eq!(compose_abs_path("D:", "Secure\\f.bin"), "D:\\Secure\\f.bin");
        // A deeper mount keeps a single separator.
        assert_eq!(compose_abs_path("C:\\Users\\x", "a\\b.txt"), "C:\\Users\\x\\a\\b.txt");
        // Forward-slash mount (UNC-ish) tolerated.
        assert_eq!(compose_abs_path("\\\\server\\share\\", "dir\\f"), "\\\\server\\share\\dir\\f");
    }
}
