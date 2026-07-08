//! NTFS MFT enumeration via `FSCTL_ENUM_USN_DATA` — "Everything-class" fast namespace
//! enumeration. Instead of recursively `readdir`-ing the tree (a syscall per directory),
//! we ask the volume for every Master File Table record in one streamed sweep, then
//! reconstruct full paths from the parent links. Sizes/timestamps are NOT in USN records,
//! so the scan still `stat`s candidates — but the enumeration itself (the expensive part
//! on huge trees) is dramatically faster and yields a flat list we can parallelize.
//!
//! Constraints (handled by graceful fallback in the scan service — never an error to the
//! user): requires Windows, a drive-letter volume, NTFS, and typically elevation. On any
//! failure the scan falls back to the portable `walker`.
//!
//! Robustness choices: we use raw `extern "system"` FFI for the three well-known kernel32
//! calls and parse USN_RECORD_V2 with explicit little-endian field offsets — no reliance
//! on windows-rs symbol paths or struct layout for this unsafe path.

/// A file discovered in the MFT. `rel_path` is volume-relative, backslash-separated,
/// original case. Directories are not returned (only leaf files). Size/timestamps are
/// filled later by `stat` in the scan service (USN records do not carry them).
#[derive(Debug, Clone)]
pub struct MftFile {
    pub rel_path: String,
}

#[cfg(windows)]
pub fn enumerate_mft(
    volume_mount: &str,
    cancel: &std::sync::atomic::AtomicBool,
) -> Result<Vec<MftFile>, String> {
    use std::collections::HashMap;
    use std::ffi::c_void;
    use std::sync::atomic::Ordering;

    const GENERIC_READ: u32 = 0x8000_0000;
    const FILE_SHARE_READ: u32 = 0x1;
    const FILE_SHARE_WRITE: u32 = 0x2;
    const OPEN_EXISTING: u32 = 3;
    const INVALID_HANDLE_VALUE: isize = -1;
    const FSCTL_ENUM_USN_DATA: u32 = 0x0009_00B3;
    const ERROR_HANDLE_EOF: i32 = 38;
    const FILE_ATTRIBUTE_DIRECTORY: u32 = 0x10;
    const ROOT_FRN: u64 = 5; // NTFS root directory MFT record
    const FIRST_USER_FRN: u64 = 16; // records 0..16 are reserved metafiles ($MFT, …)

    extern "system" {
        fn CreateFileW(
            name: *const u16,
            access: u32,
            share: u32,
            sec: *const c_void,
            disp: u32,
            flags: u32,
            template: isize,
        ) -> isize;
        fn DeviceIoControl(
            h: isize,
            code: u32,
            inbuf: *const c_void,
            insize: u32,
            outbuf: *mut c_void,
            outsize: u32,
            returned: *mut u32,
            overlapped: *mut c_void,
        ) -> i32;
    }

    // Volume must be a drive letter -> open the raw volume device \\.\X:
    let drive = volume_mount
        .chars()
        .next()
        .filter(|c| c.is_ascii_alphabetic())
        .ok_or("MFT enumeration needs a drive-letter volume")?;
    let device: Vec<u16> = format!("\\\\.\\{drive}:")
        .encode_utf16()
        .chain(std::iter::once(0))
        .collect();

    let handle = unsafe {
        CreateFileW(
            device.as_ptr(),
            GENERIC_READ,
            FILE_SHARE_READ | FILE_SHARE_WRITE,
            std::ptr::null(),
            OPEN_EXISTING,
            0,
            0,
        )
    };
    if handle == INVALID_HANDLE_VALUE || handle == 0 {
        return Err(format!(
            "cannot open volume {drive}: ({}) — admin rights are usually required",
            std::io::Error::last_os_error()
        ));
    }
    // RAII close.
    struct Handle(isize);
    impl Drop for Handle {
        fn drop(&mut self) {
            unsafe {
                extern "system" {
                    fn CloseHandle(h: isize) -> i32;
                }
                CloseHandle(self.0);
            }
        }
    }
    let _guard = Handle(handle);

    // frn -> (parent_frn, name, is_dir)
    let mut nodes: HashMap<u64, (u64, String, bool)> = HashMap::new();

    // MFT_ENUM_DATA_V0 { u64 StartFileReferenceNumber; i64 LowUsn; i64 HighUsn; }
    let mut start_frn: u64 = 0;
    let mut out = vec![0u8; 1 << 18]; // 256 KiB output buffer
    loop {
        // Cancellation escape hatch: checked once per ~256 KiB chunk so a Stop click
        // takes effect within a few thousand records instead of after the whole drive.
        if cancel.load(Ordering::Relaxed) {
            break;
        }
        let mut input = [0u8; 24];
        input[0..8].copy_from_slice(&start_frn.to_le_bytes());
        input[8..16].copy_from_slice(&0i64.to_le_bytes()); // LowUsn
        input[16..24].copy_from_slice(&i64::MAX.to_le_bytes()); // HighUsn

        let mut returned: u32 = 0;
        let ok = unsafe {
            DeviceIoControl(
                handle,
                FSCTL_ENUM_USN_DATA,
                input.as_ptr() as *const c_void,
                input.len() as u32,
                out.as_mut_ptr() as *mut c_void,
                out.len() as u32,
                &mut returned as *mut u32,
                std::ptr::null_mut(),
            )
        };
        if ok == 0 {
            let err = std::io::Error::last_os_error();
            if err.raw_os_error() == Some(ERROR_HANDLE_EOF) {
                break; // normal end of enumeration
            }
            return Err(format!("FSCTL_ENUM_USN_DATA failed: {err}"));
        }
        if returned <= 8 {
            break; // only the next-FRN header, no records left
        }

        // First 8 bytes = next StartFileReferenceNumber.
        start_frn = u64::from_le_bytes(out[0..8].try_into().map_err(|_| "short buffer")?);

        let mut pos = 8usize;
        let end = returned as usize;
        while pos + 60 <= end {
            let rec = &out[pos..];
            let reclen = u32::from_le_bytes(rec[0..4].try_into().unwrap_or([0; 4])) as usize;
            if reclen == 0 || pos + reclen > end {
                break;
            }
            let frn = u64::from_le_bytes(rec[8..16].try_into().unwrap_or([0; 8]));
            let parent = u64::from_le_bytes(rec[16..24].try_into().unwrap_or([0; 8]));
            let attrs = u32::from_le_bytes(rec[52..56].try_into().unwrap_or([0; 4]));
            let name_len = u16::from_le_bytes(rec[56..58].try_into().unwrap_or([0; 2])) as usize;
            let name_off = u16::from_le_bytes(rec[58..60].try_into().unwrap_or([0; 2])) as usize;

            if name_off + name_len <= reclen {
                let name_bytes = &rec[name_off..name_off + name_len];
                let name: String = String::from_utf16_lossy(
                    &name_bytes
                        .chunks_exact(2)
                        .map(|b| u16::from_le_bytes([b[0], b[1]]))
                        .collect::<Vec<u16>>(),
                );
                let is_dir = attrs & FILE_ATTRIBUTE_DIRECTORY != 0;
                nodes.insert(frn, (parent, name, is_dir));
            }
            pos += reclen;
        }
    }

    // Resolve full volume-relative paths for leaf files, memoizing directory paths.
    let mut dir_path_cache: HashMap<u64, String> = HashMap::new();
    let mut files = Vec::new();
    // Collect file frns first to avoid borrowing `nodes` mutably while iterating.
    let file_frns: Vec<u64> = nodes
        .iter()
        .filter(|(frn, (_, _, is_dir))| !*is_dir && **frn >= FIRST_USER_FRN)
        .map(|(frn, _)| *frn)
        .collect();

    for frn in file_frns {
        let (parent, name, _) = match nodes.get(&frn) {
            Some(v) => v.clone(),
            None => continue,
        };
        let parent_path = resolve_dir(parent, &nodes, &mut dir_path_cache, ROOT_FRN);
        let rel = if parent_path.is_empty() {
            name
        } else {
            format!("{parent_path}\\{name}")
        };
        files.push(MftFile { rel_path: rel });
    }

    Ok(files)
}

/// Resolve a directory frn to its volume-relative path (memoized). Iterative to avoid
/// deep recursion on pathological trees. Root resolves to "".
#[cfg(windows)]
fn resolve_dir(
    mut frn: u64,
    nodes: &std::collections::HashMap<u64, (u64, String, bool)>,
    cache: &mut std::collections::HashMap<u64, String>,
    root: u64,
) -> String {
    let mut chain: Vec<u64> = Vec::new();
    loop {
        if frn == root {
            break;
        }
        if let Some(cached) = cache.get(&frn) {
            // Prepend cached path and stop.
            let mut path = cached.clone();
            for f in chain.iter().rev() {
                if let Some((_, name, _)) = nodes.get(f) {
                    if path.is_empty() {
                        path = name.clone();
                    } else {
                        path = format!("{path}\\{name}");
                    }
                }
            }
            return path;
        }
        match nodes.get(&frn) {
            Some((parent, _, _)) => {
                chain.push(frn);
                frn = *parent;
                if chain.len() > 4096 {
                    break; // safety against cycles/corruption
                }
            }
            None => break, // orphan — best-effort partial path
        }
    }
    // Build from root down.
    let mut path = String::new();
    for f in chain.iter().rev() {
        if let Some((_, name, _)) = nodes.get(f) {
            if path.is_empty() {
                path = name.clone();
            } else {
                path = format!("{path}\\{name}");
            }
            cache.insert(*f, path.clone());
        }
    }
    path
}

#[cfg(not(windows))]
pub fn enumerate_mft(
    _volume_mount: &str,
    _cancel: &std::sync::atomic::AtomicBool,
) -> Result<Vec<MftFile>, String> {
    Err("MFT enumeration is Windows-only".into())
}
