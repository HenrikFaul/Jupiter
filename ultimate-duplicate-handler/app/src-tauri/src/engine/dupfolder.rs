//! Duplicate-folder detection. A folder is a duplicate of another when their full
//! recursive content is identical — the same set of (relative-subpath, content-hash)
//! entries. We compute a BLAKE3 signature per folder over its sorted entry list; equal
//! folders share a signature.
//!
//! This is an ON-DEMAND analysis (triggered by the user), not part of every scan, because
//! it expands each file across its ancestor folders (O(files × depth)). We report only
//! **maximal** duplicate folders: if a folder's parent has the same signature, the parent
//! is the better representative and the child is suppressed — this avoids the nested-folder
//! explosion where every ancestor reports as duplicated.

use crate::db::repo;
use rusqlite::{params, Connection, Result as SqlResult};
use std::collections::HashMap;

struct Acc {
    entries: Vec<(String, String)>, // (relative subpath, hex content hash)
    total_bytes: i64,
    volume_id: i64,
    online: bool,
    path_raw: String,
}

/// Rebuild the folder_cluster tables from the current file index. Returns the number of
/// duplicate-folder clusters found.
pub fn detect(conn: &Connection) -> Result<usize, String> {
    conn.execute("DELETE FROM folder_cluster", [])
        .map_err(|e| format!("clear folder_cluster: {e}"))?;

    // (volume_id, folder_path_raw) -> accumulated content.
    let mut folders: HashMap<(i64, String), Acc> = HashMap::new();
    collect_folders(conn, &mut folders).map_err(|e| format!("collect: {e}"))?;

    // Compute a signature per folder.
    let mut sig_of: HashMap<(i64, String), [u8; 32]> = HashMap::new();
    let mut meta: HashMap<(i64, String), (usize, i64, i64, bool, String)> = HashMap::new();
    for (key, acc) in &folders {
        let mut entries = acc.entries.clone();
        entries.sort();
        let mut hasher = blake3::Hasher::new();
        for (rel, hash) in &entries {
            hasher.update(rel.as_bytes());
            hasher.update(b"\0");
            hasher.update(hash.as_bytes());
            hasher.update(b"\n");
        }
        sig_of.insert(key.clone(), *hasher.finalize().as_bytes());
        meta.insert(
            key.clone(),
            (entries.len(), acc.total_bytes, acc.volume_id, acc.online, acc.path_raw.clone()),
        );
    }

    // Suppress non-maximal folders: drop a folder whose parent has the same signature.
    let mut signatures: HashMap<[u8; 32], Vec<(i64, String)>> = HashMap::new();
    for (key, sig) in &sig_of {
        if let Some(parent_key) = parent_of(key) {
            if sig_of.get(&parent_key) == Some(sig) {
                continue; // nested inside an equal parent — parent represents it
            }
        }
        signatures.entry(*sig).or_default().push(key.clone());
    }

    // Persist clusters with >= 2 equal folders and >= 1 file each.
    let mut found = 0usize;
    let ts = repo::now();
    for (sig, members) in signatures {
        if members.len() < 2 {
            continue;
        }
        let (file_count, total_bytes, _, _, _) = meta.get(&members[0]).cloned().unwrap_or((0, 0, 0, false, String::new()));
        if file_count == 0 {
            continue;
        }
        let reclaimable = total_bytes * (members.len() as i64 - 1);
        conn.execute(
            "INSERT INTO folder_cluster (signature, file_count, total_bytes, member_count, reclaimable_bytes, created_at) \
             VALUES (?1,?2,?3,?4,?5,?6)",
            params![&sig[..], file_count as i64, total_bytes, members.len() as i64, reclaimable, ts],
        )
        .map_err(|e| format!("insert folder_cluster: {e}"))?;
        let cluster_id = conn.last_insert_rowid();
        for key in &members {
            let (_, _, vol, online, path_raw) = meta.get(key).cloned().unwrap_or((0, 0, key.0, false, key.1.clone()));
            conn.execute(
                "INSERT INTO folder_cluster_member (cluster_id, volume_id, folder_path_raw, folder_path_key, is_online) \
                 VALUES (?1,?2,?3,?4,?5)",
                params![cluster_id, vol, path_raw, crate::engine::pathkey::make_key(&path_raw), online as i64],
            )
            .map_err(|e| format!("insert member: {e}"))?;
        }
        found += 1;
    }
    Ok(found)
}

/// For every present, fully-hashed file, contribute its content to each ancestor folder.
fn collect_folders(conn: &Connection, folders: &mut HashMap<(i64, String), Acc>) -> SqlResult<()> {
    let mut stmt = conn.prepare(
        "SELECT f.volume_id AS vid, f.path_raw AS path, f.size_bytes AS sz, \
                hex(c.full_hash) AS h, v.is_online AS online \
         FROM file f \
         JOIN content c ON c.id = f.content_id \
         JOIN volume v ON v.id = f.volume_id \
         WHERE f.status='present' AND f.hash_state='full'",
    )?;
    let mut rows = stmt.query([])?;
    while let Some(r) = rows.next()? {
        let vid: i64 = r.get("vid")?;
        let path: String = r.get("path")?;
        let size: i64 = r.get("sz")?;
        let hash: String = r.get("h")?;
        let online: bool = r.get::<_, i64>("online")? != 0;

        let comps: Vec<&str> = path.split('\\').filter(|s| !s.is_empty()).collect();
        if comps.len() < 2 {
            continue; // file at volume root has no enclosing folder to dedupe
        }
        // Each ancestor folder = comps[0..i] for i in 1..len; rel = comps[i..].
        for i in 1..comps.len() {
            let folder = comps[..i].join("\\");
            let rel = comps[i..].join("\\");
            let acc = folders.entry((vid, folder.clone())).or_insert_with(|| Acc {
                entries: Vec::new(),
                total_bytes: 0,
                volume_id: vid,
                online,
                path_raw: folder,
            });
            acc.entries.push((rel, hash.clone()));
            acc.total_bytes += size;
        }
    }
    Ok(())
}

/// The parent folder key of a (volume, folder) key, or None if it's a top-level folder.
fn parent_of(key: &(i64, String)) -> Option<(i64, String)> {
    let idx = key.1.rfind('\\')?;
    Some((key.0, key.1[..idx].to_string()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use rusqlite::Connection;

    fn mem() -> Connection {
        let c = Connection::open_in_memory().unwrap();
        crate::db::migrate(&c).unwrap();
        c
    }

    fn add_file(conn: &Connection, id: i64, content_id: i64, path: &str) {
        let key = crate::engine::pathkey::make_key(path);
        conn.execute(
            "INSERT INTO file (id, volume_id, content_id, path_raw, path_key, file_name, \
             size_bytes, status, hash_state, first_seen_at, last_seen_at) \
             VALUES (?1, 1, ?2, ?3, ?4, ?3, 10, 'present', 'full', 0, 0)",
            rusqlite::params![id, content_id, path, key],
        )
        .unwrap();
    }

    #[test]
    fn detects_two_identical_folders() {
        let conn = mem();
        conn.execute("INSERT INTO volume(id,fs_label,fs_type,is_online,is_removable,first_seen_at,last_seen_at,current_mount_point) VALUES (1,'V','NTFS',1,0,0,0,'Z:\\')", []).unwrap();
        // Two distinct contents, each 10 bytes.
        conn.execute("INSERT INTO content(id,full_hash,size_bytes,first_indexed_at,last_confirmed_at) VALUES (1,X'AA',10,0,0)", []).unwrap();
        conn.execute("INSERT INTO content(id,full_hash,size_bytes,first_indexed_at,last_confirmed_at) VALUES (2,X'BB',10,0,0)", []).unwrap();
        // FolderA and FolderB hold the SAME two files (same content, same relative names).
        add_file(&conn, 1, 1, r"FolderA\f1.bin");
        add_file(&conn, 2, 2, r"FolderA\f2.bin");
        add_file(&conn, 3, 1, r"FolderB\f1.bin");
        add_file(&conn, 4, 2, r"FolderB\f2.bin");

        let found = detect(&conn).unwrap();
        assert_eq!(found, 1, "exactly one duplicate-folder group");

        let (members, files, total): (i64, i64, i64) = conn
            .query_row(
                "SELECT member_count AS m, file_count AS f, total_bytes AS t FROM folder_cluster",
                [],
                |r| Ok((r.get("m")?, r.get("f")?, r.get("t")?)),
            )
            .unwrap();
        assert_eq!(members, 2, "two identical folders");
        assert_eq!(files, 2, "two files per folder");
        assert_eq!(total, 20, "20 bytes per folder");
    }

    #[test]
    fn different_folders_are_not_duplicates() {
        let conn = mem();
        conn.execute("INSERT INTO volume(id,fs_label,fs_type,is_online,is_removable,first_seen_at,last_seen_at,current_mount_point) VALUES (1,'V','NTFS',1,0,0,0,'Z:\\')", []).unwrap();
        conn.execute("INSERT INTO content(id,full_hash,size_bytes,first_indexed_at,last_confirmed_at) VALUES (1,X'AA',10,0,0)", []).unwrap();
        conn.execute("INSERT INTO content(id,full_hash,size_bytes,first_indexed_at,last_confirmed_at) VALUES (2,X'BB',10,0,0)", []).unwrap();
        add_file(&conn, 1, 1, r"FolderA\f1.bin");
        add_file(&conn, 2, 2, r"FolderB\f2.bin"); // different content AND name
        let found = detect(&conn).unwrap();
        assert_eq!(found, 0, "no duplicate folders");
    }
}
