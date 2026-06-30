//! Custom actions ("plugins") — user-defined external tools you can run on indexed files
//! (e.g. "Open in VLC", an ffmpeg convert, a PowerShell script). The pragmatic, genuinely
//! useful flavour of Everything-style extensibility for Singula.
//!
//! Persisted as `Sift-Data/actions.json` beside the index (no DB migration). An action is a
//! program + an args TEMPLATE; the template is split into tokens FIRST, then each token has
//! its placeholders substituted — so `%path%` resolving to a path WITH SPACES stays a single
//! argument (no shell, no injection: we spawn the program directly with an explicit argv).
//!
//! Safety: actions are user-authored and only ever run when the user explicitly invokes them
//! (never automatically). We spawn the process directly (no shell interpolation).

use crate::model::CustomAction;
use std::path::{Path, PathBuf};

fn path(data_dir: &Path) -> PathBuf {
    data_dir.join("actions.json")
}

pub fn load(data_dir: &Path) -> Vec<CustomAction> {
    std::fs::read_to_string(path(data_dir))
        .ok()
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_default()
}

fn save(data_dir: &Path, items: &[CustomAction]) -> Result<(), String> {
    let json = serde_json::to_string_pretty(items).map_err(|e| e.to_string())?;
    std::fs::write(path(data_dir), json).map_err(|e| format!("write actions.json: {e}"))
}

/// Upsert an action by name (case-insensitive). Validates that name + program are non-empty.
pub fn put(data_dir: &Path, action: CustomAction) -> Result<Vec<CustomAction>, String> {
    let name = action.name.trim().to_string();
    if name.is_empty() {
        return Err("action name is empty".into());
    }
    if action.program.trim().is_empty() {
        return Err("action program is empty".into());
    }
    let mut items = load(data_dir);
    items.retain(|a| !a.name.eq_ignore_ascii_case(&name));
    items.push(CustomAction { name, program: action.program.trim().to_string(), args: action.args });
    items.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
    save(data_dir, &items)?;
    Ok(items)
}

pub fn remove(data_dir: &Path, name: &str) -> Result<Vec<CustomAction>, String> {
    let mut items = load(data_dir);
    items.retain(|a| !a.name.eq_ignore_ascii_case(name.trim()));
    save(data_dir, &items)?;
    Ok(items)
}

/// Resolve an action's argv for a concrete absolute file path. Split the template into tokens
/// FIRST, then substitute placeholders per-token (so a substituted value with spaces stays one
/// argument). Supported tokens: %path% %folder% %name% %ext%.
pub fn resolve_args(args_template: &str, abs: &str) -> Vec<String> {
    let p = Path::new(abs);
    let folder = p.parent().map(|s| s.to_string_lossy().to_string()).unwrap_or_default();
    let name = p.file_name().map(|s| s.to_string_lossy().to_string()).unwrap_or_default();
    let ext = p.extension().map(|s| s.to_string_lossy().to_string()).unwrap_or_default();
    args_template
        .split_whitespace()
        .map(|tok| {
            tok.replace("%path%", abs)
                .replace("%folder%", &folder)
                .replace("%name%", &name)
                .replace("%ext%", &ext)
        })
        .collect()
}

/// Run a named action against one absolute path. Spawns the program directly (no shell) and
/// returns once it has launched (fire-and-forget — viewers/converters run on their own).
pub fn run(data_dir: &Path, name: &str, abs: &str) -> Result<(), String> {
    let action = load(data_dir)
        .into_iter()
        .find(|a| a.name.eq_ignore_ascii_case(name.trim()))
        .ok_or_else(|| format!("no action named '{name}'"))?;
    let args = resolve_args(&action.args, abs);
    std::process::Command::new(&action.program)
        .args(&args)
        .spawn()
        .map(|_| ())
        .map_err(|e| format!("could not launch '{}': {e}", action.program))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resolve_keeps_spaces_in_substituted_path() {
        // A path WITH spaces must remain a single argument.
        let argv = resolve_args("-i %path% -title %name%", "C:\\my movies\\a b.mp4");
        assert_eq!(argv, vec!["-i", "C:\\my movies\\a b.mp4", "-title", "a b.mp4"]);
    }

    #[test]
    fn folder_and_ext_tokens() {
        let argv = resolve_args("%folder% %ext%", "D:\\x\\y\\clip.mkv");
        assert_eq!(argv, vec!["D:\\x\\y", "mkv"]);
    }

    #[test]
    fn put_validates_and_roundtrips() {
        let dir = std::env::temp_dir().join(format!("sift_act_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();

        let a = CustomAction { name: "Open in VLC".into(), program: "vlc".into(), args: "%path%".into() };
        let items = put(&dir, a.clone()).unwrap();
        assert_eq!(items.len(), 1);
        // case-insensitive upsert
        assert_eq!(put(&dir, a).unwrap().len(), 1);
        // empty program rejected
        assert!(put(&dir, CustomAction { name: "x".into(), program: "  ".into(), args: String::new() }).is_err());
        // remove + persistence
        assert_eq!(remove(&dir, "open in vlc").unwrap().len(), 0);
        assert_eq!(load(&dir).len(), 0);
        let _ = std::fs::remove_dir_all(&dir);
    }
}
