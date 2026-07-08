// Prevent a second console window on Windows release builds.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod commands;
mod update;

use commands::AppState;
use sift_core::paths;

fn main() {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "info,sift_core=debug".into()),
        )
        .init();

    let db_path = paths::index_db_path();
    let data_dir = paths::data_dir();
    tracing::info!(path = %db_path.display(), portable = ?data_dir, "resolving index location");

    // Headless auto-rescan path: when launched by the Windows Task Scheduler job with
    // `--rescan`, run a scan of every online known volume and exit WITHOUT opening a window.
    // This keeps the persistent index fresh even when the UI is closed (initiative #3).
    if std::env::args().any(|a| a == "--rescan") {
        match sift_core::engine::scheduler::run_headless(&db_path) {
            Ok(n) => {
                tracing::info!(volumes = n, "headless auto-rescan finished");
                std::process::exit(0);
            }
            Err(e) => {
                tracing::error!(error = %e, "headless auto-rescan failed");
                std::process::exit(1);
            }
        }
    }

    let state = match AppState::open(db_path.clone(), data_dir) {
        Ok(s) => s,
        Err(e) => {
            // Fail loud — never start with a broken index (CODING_RULES §2/§3).
            tracing::error!(error = %e, path = %db_path.display(), "failed to open index DB");
            // Surface to the user with a native dialog before exiting, so a portable
            // double-click failure is never silent.
            rfd_fallback(&format!("Sift could not open its index:\n{e}\n\nPath: {}", db_path.display()));
            std::process::exit(1);
        }
    };
    tracing::info!(path = %db_path.display(), "index opened");

    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_shell::init())
        .manage(state)
        .invoke_handler(tauri::generate_handler![
            commands::get_index_stats,
            commands::list_volumes,
            commands::query_clusters,
            commands::query_folder_clusters,
            commands::detect_duplicate_folders,
            commands::detect_similar_images,
            commands::detect_similar_videos,
            commands::get_thumbnail,
            commands::get_video_thumbnail,
            commands::folder_children,
            commands::start_media_analysis,
            commands::cancel_media_analysis,
            commands::media_is_active,
            commands::ffprobe_status,
            commands::reclaim_summary,
            commands::get_protected_paths,
            commands::set_protected_paths,
            commands::license_status,
            commands::activate_license,
            commands::deactivate_license,
            commands::get_presence,
            commands::search_index,
            commands::list_audit,
            commands::list_scan_sessions,
            commands::get_scan_session,
            commands::delete_scan_session,
            commands::clear_scan_history,
            commands::mark_files,
            commands::reveal_in_explorer,
            commands::db_integrity_check,
            commands::storage_info,
            commands::apply_selection_rules,
            commands::validate_deletion_plan,
            commands::create_deletion_plan,
            commands::execute_deletion_plan,
            commands::delete_files,
            commands::preview_folder_deletion,
            commands::delete_folders,
            commands::get_bookmarks,
            commands::save_bookmark,
            commands::delete_bookmark,
            commands::get_actions,
            commands::save_action,
            commands::delete_action,
            commands::run_action,
            commands::get_update_status,
            commands::list_restorable,
            commands::restore_files,
            commands::get_schedule,
            commands::set_schedule,
            commands::run_rescan_now,
            commands::export_report,
            commands::start_scan,
            commands::cancel_scan,
            commands::scan_is_active,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Sift");
}

/// Best-effort native error box without pulling extra deps: uses the Win32 MessageBox via
/// the OS only on Windows; elsewhere logs. Kept tiny and dependency-free.
#[cfg(windows)]
fn rfd_fallback(msg: &str) {
    extern "system" {
        fn MessageBoxW(hwnd: isize, text: *const u16, caption: *const u16, utype: u32) -> i32;
    }
    let wide: Vec<u16> = msg.encode_utf16().chain(std::iter::once(0)).collect();
    let cap: Vec<u16> = "Sift".encode_utf16().chain(std::iter::once(0)).collect();
    unsafe {
        MessageBoxW(0, wide.as_ptr(), cap.as_ptr(), 0x10 /* MB_ICONERROR */);
    }
}
#[cfg(not(windows))]
fn rfd_fallback(msg: &str) {
    eprintln!("{msg}");
}
