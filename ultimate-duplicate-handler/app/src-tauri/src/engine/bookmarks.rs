//! Bookmarks — named, recallable Index Explorer searches (an Everything-style convenience:
//! save a filter set once, jump back to it any time). Persisted as `Sift-Data/bookmarks.json`
//! beside the index, exactly like protected.json / schedule.json — no DB migration needed.

use crate::model::{Bookmark, IndexQuery};
use std::path::{Path, PathBuf};

fn path(data_dir: &Path) -> PathBuf {
    data_dir.join("bookmarks.json")
}

/// All saved searches (empty if none / unreadable).
pub fn load(data_dir: &Path) -> Vec<Bookmark> {
    std::fs::read_to_string(path(data_dir))
        .ok()
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_default()
}

fn save(data_dir: &Path, items: &[Bookmark]) -> Result<(), String> {
    let json = serde_json::to_string_pretty(items).map_err(|e| e.to_string())?;
    std::fs::write(path(data_dir), json).map_err(|e| format!("write bookmarks.json: {e}"))
}

/// Upsert a bookmark by name (case-insensitive — re-saving a name overwrites it). Returns the
/// updated, alphabetically-sorted list.
pub fn put(data_dir: &Path, name: &str, query: IndexQuery) -> Result<Vec<Bookmark>, String> {
    let name = name.trim();
    if name.is_empty() {
        return Err("bookmark name is empty".into());
    }
    let mut items = load(data_dir);
    items.retain(|b| !b.name.eq_ignore_ascii_case(name));
    items.push(Bookmark { name: name.to_string(), query });
    items.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
    save(data_dir, &items)?;
    Ok(items)
}

/// Remove a bookmark by name (case-insensitive). Returns the updated list.
pub fn remove(data_dir: &Path, name: &str) -> Result<Vec<Bookmark>, String> {
    let mut items = load(data_dir);
    items.retain(|b| !b.name.eq_ignore_ascii_case(name.trim()));
    save(data_dir, &items)?;
    Ok(items)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn upsert_and_remove_roundtrip() {
        let dir = std::env::temp_dir().join(format!("sift_bm_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();

        let q = IndexQuery::default();
        let items = put(&dir, "videos on D", q.clone()).unwrap();
        assert_eq!(items.len(), 1);
        // Case-insensitive upsert must not duplicate.
        let items = put(&dir, "VIDEOS ON D", q.clone()).unwrap();
        assert_eq!(items.len(), 1);
        let items = put(&dir, "big files", q).unwrap();
        assert_eq!(items.len(), 2);
        // Sorted alphabetically.
        assert_eq!(items[0].name, "big files");
        // Remove + persistence.
        let items = remove(&dir, "videos on d").unwrap();
        assert_eq!(items.len(), 1);
        assert_eq!(load(&dir).len(), 1);

        // Empty name is rejected.
        assert!(put(&dir, "   ", IndexQuery::default()).is_err());

        let _ = std::fs::remove_dir_all(&dir);
    }
}
