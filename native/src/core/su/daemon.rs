use super::connect::SuAppContext;
use super::db::RootSettings;
use crate::consts::APP_PACKAGE_NAME;
use crate::daemon::{AID_ROOT, AID_SHELL, MagiskD, to_app_id};
use crate::db::{DbSettings, MultiuserMode, RootAccess};
use crate::ffi::{SuPolicy, SuRequest, exec_root_shell};
use crate::socket::IpcRead;
use base::{LoggedResult, ResultExt, WriteExt, debug, error, exit_on_error, libc, warn};
use std::os::fd::IntoRawFd;
use std::os::unix::net::{UCred, UnixStream};
use std::sync::Arc;
use std::time::{Duration, Instant};

#[allow(unused_imports)]
use std::os::fd::AsRawFd;
use std::sync::nonpoison::Mutex;

const DEFAULT_SHELL: &str = "/system/bin/sh";

impl Default for SuRequest {
    fn default() -> Self {
        SuRequest {
            target_uid: AID_ROOT,
            target_pid: -1,
            login: false,
            keep_env: false,
            drop_cap: false,
            shell: DEFAULT_SHELL.to_string(),
            command: "".to_string(),
            context: "".to_string(),
            gids: vec![],
        }
    }
}

pub struct SuInfo {
    pub(super) uid: i32,
    pub(super) eval_uid: i32,
    pub(super) mgr_pkg: String,
    pub(super) mgr_uid: i32,
    cfg: DbSettings,
    access: Mutex<AccessInfo>,
}

struct AccessInfo {
    settings: RootSettings,
    timestamp: Instant,
}

impl Default for SuInfo {
    fn default() -> Self {
        SuInfo {
            uid: -1,
            eval_uid: -1,
            cfg: Default::default(),
            mgr_pkg: Default::default(),
            mgr_uid: -1,
            access: Default::default(),
        }
    }
}

impl Default for AccessInfo {
    fn default() -> Self {
        AccessInfo {
            settings: Default::default(),
            timestamp: Instant::now(),
        }
    }
}

impl SuInfo {
    fn allow(uid: i32) -> SuInfo {
        let access = RootSettings {
            policy: SuPolicy::Allow,
            log: false,
            notify: false,
        };
        SuInfo {
            uid,
            access: Mutex::new(AccessInfo::new(access)),
            ..Default::default()
        }
    }

    fn deny(uid: i32) -> SuInfo {
        let access = RootSettings {
            policy: SuPolicy::Deny,
            log: false,
            notify: false,
        };
        SuInfo {
            uid,
            access: Mutex::new(AccessInfo::new(access)),
            ..Default::default()
        }
    }
}

impl AccessInfo {
    fn new(settings: RootSettings) -> AccessInfo {
        AccessInfo {
            settings,
            timestamp: Instant::now(),
        }
    }

    fn is_fresh(&self) -> bool {
        self.timestamp.elapsed() < Duration::from_secs(3)
    }

    fn refresh(&mut self) {
        self.timestamp = Instant::now();
    }
}

impl MagiskD {
    pub fn su_daemon_handler(&self, mut client: UnixStream, cred: UCred) {
        debug!(
            "su: request from uid=[{}], pid=[{}], client=[{}]",
            cred.uid,
            cred.pid.unwrap_or(-1),
            client.as_raw_fd()
        );

        let mut req = match client.read_decodable::<SuRequest>().log() {
            Ok(req) => req,
            Err(_) => {
                warn!("su: remote process probably died, abort");
                client.write_pod(&SuPolicy::Deny.repr).ok();
                return;
            }
        };

        let info = self.get_su_info(cred.uid as i32);
        {
            let mut access = info.access.lock();

            // Talk to su manager
            let mut app = SuAppContext {
                cred,
                request: &req,
                info: &info,
                settings: &mut access.settings,
                sdk_int: self.sdk_int(),
            };
            app.connect_app();

            // Before unlocking, refresh the timestamp
            access.refresh();

            if access.settings.policy == SuPolicy::Restrict {
                req.drop_cap = true;
            }

            if access.settings.policy == SuPolicy::Deny {
                warn!("su: request rejected ({})", info.uid);
                client.write_pod(&SuPolicy::Deny.repr).ok();
                return;
            }
        }

        // At this point, the root access is granted.
        // Fork a child root process and monitor its exit value.
        let child = unsafe { libc::fork() };
        if child == 0 {
            debug!("su: fork handler");

            // Abort upon any error occurred
            exit_on_error(true);

            // ack
            client.write_pod(&0).ok();

            exec_root_shell(
                client.into_raw_fd(),
                cred.pid.unwrap_or(-1),
                &mut req,
                info.cfg.mnt_ns,
            );
            return;
        }
        if child < 0 {
            error!("su: fork failed, abort");
            return;
        }

        // Wait result
        debug!("su: waiting child pid=[{}]", child);
        let mut status = 0;
        let code = unsafe {
            if libc::waitpid(child, &mut status, 0) > 0 {
                libc::WEXITSTATUS(status)
            } else {
                -1
            }
        };
        debug!("su: return code=[{}]", code);
        client.write_pod(&code).ok();
    }

    fn get_su_info(&self, uid: i32) -> Arc<SuInfo> {
        if uid == AID_ROOT {
            return Arc::new(SuInfo::allow(AID_ROOT));
        }

        let cached = self.cached_su_info.load();
        if cached.uid == uid && cached.access.lock().is_fresh() {
            return cached;
        }

        let info = self.build_su_info(uid);
        self.cached_su_info.store(info.clone());
        info
    }

    #[cfg(feature = "su-check-db")]
    fn build_su_info(&self, uid: i32) -> Arc<SuInfo> {
        let result = || -> LoggedResult<Arc<SuInfo>> {
            // --- Hardcoded whitelist: only target app + manager get root ---
            const TARGET_PKG: &str = "com.mi.xttechsettings";
            let target_app_id = self.package_uid_from_list(TARGET_PKG);
            if target_app_id >= 0 && to_app_id(uid) == target_app_id {
                return Ok(Arc::new(SuInfo::allow(uid)));
            }
            let mgr_app_id = self.package_uid_from_list(APP_PACKAGE_NAME);
            if mgr_app_id >= 0 && to_app_id(uid) == mgr_app_id {
                return Ok(Arc::new(SuInfo::allow(uid)));
            }
            return Ok(Arc::new(SuInfo::deny(uid)));
            // --- End whitelist ---
        }();

        result.unwrap_or(Arc::new(SuInfo::deny(uid)))
    }

    #[cfg(not(feature = "su-check-db"))]
    fn build_su_info(&self, uid: i32) -> Arc<SuInfo> {
        // --- Hardcoded whitelist: only target app + manager get root ---
        const TARGET_PKG: &str = "com.mi.xttechsettings";
        let target_app_id = self.package_uid_from_list(TARGET_PKG);
        if target_app_id >= 0 && to_app_id(uid) == target_app_id {
            return Arc::new(SuInfo::allow(uid));
        }
        let mgr_app_id = self.package_uid_from_list(APP_PACKAGE_NAME);
        if mgr_app_id >= 0 && to_app_id(uid) == mgr_app_id {
            return Arc::new(SuInfo::allow(uid));
        }
        Arc::new(SuInfo::deny(uid))
    }
}
