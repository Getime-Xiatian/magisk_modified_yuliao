use crate::daemon::{MagiskD, to_app_id};
use base::BufReadExt;
use std::fs::File;
use std::io::BufReader;

/// Local whitelist file at /sdcard, editable by the user (or any app with
/// storage permission). The daemon reads this file on every su request.
/// It only ever ADDS to the hardcoded whitelist in build_su_info; it never
/// replaces it, so the hardcoded packages and the manager always keep root
/// even if the file is missing, empty, or stale.
const WHITE_LIST_PATH: &str = "/sdcard/white_list.txt";

/// Template written on first boot when the file does not exist yet.
/// One package name per line, '#' starts a comment, blank lines ignored.
const WHITE_LIST_TEMPLATE: &str = "# Magisk su whitelist\n\
# One package name per line; '#' starts a comment; blank lines are ignored.\n\
# The hardcoded whitelist (com.mi.xttechsettings, andro.pluginsuite, ...) is\n\
# never affected by this file: it can only add root access, not remove it.\n\
# Changes take effect on the next su request; restart the target app after editing.\n";

impl MagiskD {
    /// Create the whitelist template on /sdcard if it does not exist yet.
    pub(crate) fn ensure_white_list(&self) {
        if std::fs::write(WHITE_LIST_PATH, WHITE_LIST_TEMPLATE).is_err() {
            // /sdcard may not be mounted yet (e.g. before user unlock);
            // the file will be created again on the next boot_complete
            return;
        }
    }

    /// Check whether `uid` is allowed by the whitelist file.
    pub(crate) fn remote_allows(&self, uid: i32) -> bool {
        let file = match File::open(WHITE_LIST_PATH) {
            Ok(f) => f,
            // No whitelist file: hardcoded whitelist is still in effect
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
