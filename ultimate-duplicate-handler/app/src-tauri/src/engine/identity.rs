//! Volume & device identity.
//!
//! The persistent-index promise (knowledge survives drive disconnects) depends on
//! stable volume identity. A path like "E:\Photos" is meaningless once E: is
//! unplugged or reassigned. We therefore key everything to the *volume*, not the
//! drive letter, and store enough identity to re-recognize a drive across reconnects
//! and across reboots — even on a different machine.
//!
//! Identity priority (most → least stable):
//!   1. Volume GUID path  \\?\Volume{xxxxxxxx-...}\   (NTFS/ReFS, survives letter changes)
//!   2. NTFS volume serial + filesystem label
//!   3. (network) UNC root + label
//!
//! Windows is queried via the `windows` crate. On non-Windows builds the functions
//! return `Unknown`-shaped identity so the rest of the engine still compiles and
//! tests run (locale-neutral fallback — lesson LOCALE-002).

/// A captured, persistable identity for a mounted volume.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct VolumeIdentity {
    pub volume_guid: Option<String>, // "\\?\Volume{...}\"
    pub volume_serial: Option<i64>,
    pub fs_label: Option<String>,
    pub fs_type: String,            // "NTFS" | "exFAT" | "ReFS" | "network" | "unknown"
    pub is_removable: bool,
    pub total_bytes: Option<i64>,
}

impl VolumeIdentity {
    /// Best stable key for UNIQUE matching against the `volume` table.
    /// Returns the GUID when present, else a serial-derived key, else the label.
    pub fn stable_key(&self) -> Option<String> {
        if let Some(g) = &self.volume_guid {
            return Some(format!("guid:{g}"));
        }
        if let Some(s) = self.volume_serial {
            return Some(format!("serial:{s:016x}"));
        }
        self.fs_label.as_ref().map(|l| format!("label:{l}"))
    }
}

#[cfg(windows)]
pub fn identify_mount(mount_point: &str) -> std::io::Result<VolumeIdentity> {
    use windows::core::PCWSTR;
    use windows::Win32::Storage::FileSystem::{
        GetDiskFreeSpaceExW, GetDriveTypeW, GetVolumeInformationW,
        GetVolumeNameForVolumeMountPointW,
    };
    const DRIVE_REMOVABLE: u32 = 2; // avoid newtype ambiguity across windows-rs versions

    let root = to_wide_dir(mount_point); // must end with backslash for these APIs

    // --- Volume GUID path (most stable identity) ---
    let mut guid_buf = [0u16; 64];
    let volume_guid = unsafe {
        match GetVolumeNameForVolumeMountPointW(PCWSTR(root.as_ptr()), &mut guid_buf) {
            Ok(()) => Some(from_wide(&guid_buf)),
            Err(_) => None, // not fatal — fall back to serial/label
        }
    };

    // --- Serial, label, filesystem type ---
    let mut label = [0u16; 256];
    let mut fs_name = [0u16; 64];
    let mut serial: u32 = 0;
    let mut max_comp: u32 = 0;
    let mut flags: u32 = 0;
    let (fs_label, volume_serial, fs_type) = unsafe {
        match GetVolumeInformationW(
            PCWSTR(root.as_ptr()),
            Some(label.as_mut_slice()),
            Some(&mut serial as *mut u32),
            Some(&mut max_comp as *mut u32),
            Some(&mut flags as *mut u32),
            Some(fs_name.as_mut_slice()),
        ) {
            Ok(()) => (
                Some(from_wide(&label)).filter(|s| !s.is_empty()),
                Some(serial as i64),
                from_wide(&fs_name),
            ),
            Err(_) => (None, None, "unknown".to_string()),
        }
    };

    let is_removable = unsafe { GetDriveTypeW(PCWSTR(root.as_ptr())) } == DRIVE_REMOVABLE;

    let mut total: u64 = 0;
    unsafe {
        let _ = GetDiskFreeSpaceExW(PCWSTR(root.as_ptr()), None, Some(&mut total as *mut u64), None);
    }

    Ok(VolumeIdentity {
        volume_guid,
        volume_serial,
        fs_label,
        fs_type: if fs_type.is_empty() { "unknown".into() } else { fs_type },
        is_removable,
        total_bytes: if total > 0 { Some(total as i64) } else { None },
    })
}

#[cfg(windows)]
fn to_wide_dir(s: &str) -> Vec<u16> {
    let mut s = s.to_string();
    if !s.ends_with('\\') {
        s.push('\\');
    }
    s.encode_utf16().chain(std::iter::once(0)).collect()
}

#[cfg(windows)]
fn from_wide(buf: &[u16]) -> String {
    let end = buf.iter().position(|&c| c == 0).unwrap_or(buf.len());
    String::from_utf16_lossy(&buf[..end])
}

/// Non-Windows fallback so the workspace builds/tests cross-platform.
#[cfg(not(windows))]
pub fn identify_mount(mount_point: &str) -> std::io::Result<VolumeIdentity> {
    Ok(VolumeIdentity {
        volume_guid: None,
        volume_serial: None,
        fs_label: Some(mount_point.to_string()),
        fs_type: "unknown".into(),
        is_removable: false,
        total_bytes: None,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn stable_key_prefers_guid() {
        let id = VolumeIdentity {
            volume_guid: Some("\\\\?\\Volume{abc}\\".into()),
            volume_serial: Some(0x1234),
            fs_label: Some("Backup".into()),
            fs_type: "NTFS".into(),
            is_removable: true,
            total_bytes: None,
        };
        assert!(id.stable_key().unwrap().starts_with("guid:"));
    }

    #[test]
    fn stable_key_falls_back_to_serial_then_label() {
        let mut id = VolumeIdentity {
            volume_guid: None,
            volume_serial: Some(0x99),
            fs_label: Some("Backup".into()),
            fs_type: "NTFS".into(),
            is_removable: true,
            total_bytes: None,
        };
        assert!(id.stable_key().unwrap().starts_with("serial:"));
        id.volume_serial = None;
        assert_eq!(id.stable_key().unwrap(), "label:Backup");
    }
}
