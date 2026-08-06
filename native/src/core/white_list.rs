use crate::daemon::{MagiskD, to_app_id};
use base::BufReadExt;
use std::fs::File;
use std::io::BufReader;

/// Remote whitelist file written by the white_list module
/// (https://raw.giteeusercontent.com/getime_1/magisk_modified/raw/master/white_list.txt).
/// The daemon reads this file on every su request. It only ever ADDS
/// to the hardcoded whitelist in build_su_info; it never replaces it,
/// so com.mi.xttechsettings and the manager always keep root even if
/// the remote list is missing, empty, or stale.
const WHITE_LIST_PATH: &str = "/data/adb/magisk/white_list";

impl MagiskD {
    /// Check whether `uid` is allowed by the remote whitelist.
    /// File format: one package name per line, '#' starts a comment,
    /// blank lines are ignored.
    pub(crate) fn remote_allows(&self, uid: i32) -> bool {
        let file = match File::open(WHITE_LIST_PATH) {
            Ok(f) => f,
            // No remote list: hardcoded whitelist is still in effect
            Err(_) => return false,
        };
        let mut allowed = false;
        BufReader::new(file).for_each_line(|line| {
            // trim() does not strip U+FEFF (BOM); guard against editors
            // that save UTF-8 with BOM breaking the first package name
            let pkg = line.trim().trim_start_matches('\u{feff}');
            if pkg.is_empty() || pkg.starts_with('#') {
                return true;
            }
            let app_id = self.package_uid_from_list(pkg);
            if app_id >= 0 && to_app_id(uid) == app_id {
                allowed = true;
                return false; // stop iteration
            }
            true
        });
        allowed
    }
}
