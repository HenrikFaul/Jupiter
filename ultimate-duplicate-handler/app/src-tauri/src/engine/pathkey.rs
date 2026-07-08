//! Path normalization — closing the Windows path/Unicode gap the lessons file did
//! NOT cover (CODING_RULES §5).
//!
//! We store TWO forms of every path:
//!   * path_raw — the original, lossless, case-preserved, volume-relative path.
//!     Used for display and for the actual delete/move syscall.
//!   * path_key — a normalized key used for equality and search: NFC-normalized,
//!     case-folded, separators unified. Used for the UNIQUE(volume,path_key) index.
//!
//! Why both: NTFS is case-insensitive but case-preserving, and Unicode allows the
//! same visual filename to be encoded multiple ways (NFC vs NFD). Comparing raw
//! paths would create false "distinct" rows for the same file; deleting via a
//! normalized path could target the wrong (or no) file. So we match on path_key but
//! always act on path_raw.

use unicode_normalization::UnicodeNormalization;

/// Strip a verbatim long-path prefix if present, so stored paths are uniform.
pub fn strip_verbatim_prefix(p: &str) -> &str {
    p.strip_prefix(r"\\?\").unwrap_or(p)
}

/// Add the `\\?\` verbatim prefix for raw Win32 calls that must tolerate >260 chars.
/// Idempotent. UNC paths get the `\\?\UNC\` form.
pub fn to_verbatim(p: &str) -> String {
    if p.starts_with(r"\\?\") {
        return p.to_string();
    }
    if let Some(unc) = p.strip_prefix(r"\\") {
        return format!(r"\\?\UNC\{unc}");
    }
    format!(r"\\?\{p}")
}

/// Build the normalized match key from a volume-relative path.
/// NFC + lowercase (Unicode-aware) + backslash separators + no trailing slash.
pub fn make_key(rel_path: &str) -> String {
    let unified = rel_path.replace('/', "\\");
    let trimmed = unified.trim_end_matches('\\');
    // NFC first (compose), then Unicode-aware lowercase for case-insensitive match.
    let nfc: String = trimmed.nfc().collect();
    nfc.to_lowercase()
}

/// Lowercased extension without the dot; None when there is no extension.
pub fn extension_of(file_name: &str) -> Option<String> {
    let dot = file_name.rfind('.')?;
    if dot == 0 || dot + 1 >= file_name.len() {
        return None; // dotfile or trailing dot — treat as no extension
    }
    Some(file_name[dot + 1..].to_lowercase())
}

/// Reserved Windows device names that must never be treated as ordinary files.
const RESERVED: &[&str] = &[
    "con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6",
    "com7", "com8", "com9", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7",
    "lpt8", "lpt9",
];

pub fn is_reserved_name(file_name: &str) -> bool {
    let stem = file_name.split('.').next().unwrap_or(file_name).to_lowercase();
    RESERVED.contains(&stem.as_str())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn key_is_case_insensitive() {
        assert_eq!(make_key(r"Photos\IMG.JPG"), make_key(r"photos\img.jpg"));
    }

    #[test]
    fn key_unifies_separators_and_trailing() {
        assert_eq!(make_key("a/b/c\\"), make_key(r"A\B\C"));
    }

    #[test]
    fn key_normalizes_unicode_equivalents() {
        // "é" as precomposed (U+00E9) vs decomposed (e + U+0301) must match.
        let precomposed = "Caf\u{00e9}";
        let decomposed = "Cafe\u{0301}";
        assert_eq!(make_key(precomposed), make_key(decomposed));
    }

    #[test]
    fn extension_handles_dotfiles_and_none() {
        assert_eq!(extension_of("photo.JPG"), Some("jpg".into()));
        assert_eq!(extension_of(".gitignore"), None);
        assert_eq!(extension_of("README"), None);
        assert_eq!(extension_of("archive.tar.gz"), Some("gz".into()));
    }

    #[test]
    fn verbatim_roundtrip() {
        assert_eq!(strip_verbatim_prefix(&to_verbatim(r"C:\x\y")), r"C:\x\y");
        assert_eq!(to_verbatim(r"\\?\C:\x"), r"\\?\C:\x"); // idempotent
    }

    #[test]
    fn detects_reserved_names() {
        assert!(is_reserved_name("CON"));
        assert!(is_reserved_name("nul.txt"));
        assert!(!is_reserved_name("contacts.csv"));
    }
}
