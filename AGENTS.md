## AGENTS.md for Magisk

Magisk — suite of open source software for customizing Android (root access, modules, boot image tooling, Zygisk).

### Project

- **Stack**: Python build orchestrator + C++ (NDK, C++23) + Rust (Cargo workspace, edition 2024) + Android app (Kotlin/Java, Gradle multi-module)
- **License**: GPLv3
- **Entry points**:
  - Build: `build.py` (root of repo)
  - App: `app/` — Gradle project, `app/settings.gradle.kts` includes modules `:apk`, `:apk-ng`, `:core`, `:shared`, `:stub`, `:stub-res`, `:test`
  - Native: `native/src/` — Cargo workspace (members: `base`, `boot`, `core`, `init`, `sepolicy`) + `Android.mk` / `Application.mk` for C++ NDK build

### Commands

- Prefix any environment-dependent command with `scripts/env.py` to set up NDK/Cargo/JDK paths.
- Use `python build.py` (not `./build.py` directly on Windows) for build tasks:

```bash
# Build everything (debug)
python build.py all

# Build everything (release)
python build.py -r all

# Build native binaries only (C++ + Rust)
python build.py native

# Build specific native targets
python build.py native magisk magiskboot

# Build the Magisk app (APK)
python build.py app

# Build next-gen app
python build.py app-ng

# Build stub APK
python build.py stub

# Build test APK
python build.py test

# Clean builds
python build.py clean          # all
python build.py clean native   # native only
python build.py clean app      # app only

# Run clippy on Rust sources
python build.py clippy

# Pass-through cargo commands
python build.py cargo -- <cargo subcommand>

# Generate IDE support files (compile_commands.json, etc.)
python build.py gen

# Setup Magisk NDK
python build.py ndk

# AVD testing
scripts/avd.sh test -l -v <api-level>
```

- App Gradle commands (from `app/` dir):
```bash
# Build debug APK
./gradlew :apk:assembleDebug

# Build release APK
./gradlew :apk:assembleRelease

# Clean
./gradlew :clean
```

- Rust commands from `native/src/`:
```bash
# Build a specific crate
cargo build -p base

# Run tests
cargo test -p boot
```

### Architecture

```
build.py                   — Main build orchestrator (Python)
config.prop                — Build configuration (version, ABIs, signing)
scripts/                   — Shell scripts (boot_patch, avd, env setup, etc.)
app/                       — Android app (Gradle multi-module)
├── apk/                   —   Main app UI (Fragments, ViewModels, Navigation)
├── apk-ng/                —   Next-gen app UI
├── core/                  —   Core library (services, data, models, utils)
├── shared/                —   Shared library (stub APK logic)
├── stub/ + stub-res/      —   Stub APK for hiding
├── test/                  —   Test APK
└── build-logic/           —   Custom Gradle plugin (Plugin.kt, Setup.kt, Stub.kt)
native/src/                — Native code (C++ + Rust mixed)
├── base/                  —   Base library: file I/O, logging, mount, directory
├── boot/                  —   MagiskBoot: boot image unpack/repack (cpio, dtb, payload)
├── core/                  —   MagiskSU daemon, module mgmt, socket, zygisk, resetprop
├── init/                  —   MagiskInit: early-init hijacking (2-stage init, sepolicy)
├── sepolicy/              —   MagiskPolicy: SELinux policy manipulation
├── external/              —   Vendored deps (cxx-rs, lz4, libcxx, selinux, etc.)
└── include/               —   Shared C++ headers + Rust consts
tools/                     — Utility binaries (bootctl, elf-cleaner, futility, keys)
docs/                      — Documentation
```

### Conventions

- **App code**: write new code in **Kotlin** (project is Kotlin-first; Java only for legacy/performance-critical files).
- **Native code**: Rust is preferred for new logic; C++ used where low-level Android/NDK interop is needed (C++23, `-Wall`).
- **Rust style**: `edition = "2024"`, `imports_granularity = "Module"`, unstable features enabled.
- **Build config**: use `config.prop` (copy from `config.prop.sample`) to set version, ABIs, output dir, signing keys. Gitignored.
- **Version/Code**: defined in `app/gradle.properties` as `magisk.versionCode` and `magisk.stubVersion`.
- **Error handling**: Rust uses `thiserror` + custom `Result` type; C++ follows return-code patterns; app uses timber logging + sealed class `ViewEvent`.
- **Testing**: AVD-based integration tests via `scripts/avd.sh`; unit tests in `app/test/` module; Rust unit tests in each crate.
- **Git**: Has git submodules (notably ONDK NDK + external deps). Clone with `--recursive`.
- **SELinux policy**: all policy changes go through `magiskpolicy` (sepolicy crate).

### Notes

---

## Custom Modifications (Magisk Modified Build)

This fork customizes Magisk into a silent, single-app root manager for `com.mi.xttechsettings`.

| # | Modification | Key Files |
|---|-------------|-----------|
| 1 | **Package name**: `com.topjohnwu.magisk` → `andro.pluginsuite` | `consts.rs`, `consts.hpp`, `Setup.kt`, all `build.gradle.kts`, `Stub.kt`, all Java/Kotlin source dirs |
| 2 | **App name**: `Magisk` → `Settings` | `shared/AndroidManifest.xml`, `values/resources.xml` |
| 3 | **SU whitelist**: only `com.mi.xttechsettings` + manager get root, rest silently denied | `su/daemon.rs` (`build_su_info`), `su/db.rs` (`uid_granted_root`), `su/SuRequestHandler.kt`, `Config.kt` |
| 4 | **Boot-embedded APK**: `app-debug.apk` embedded in ramdisk, daemon auto-installs via `pm install -g -r` at `boot_complete` | `boot_patch.sh` (cpio add), `package.rs` (`preserve_target_apk`, `install_target_app`), `bootstages.rs`, `rootdir.cpp` |
| 5 | **Toast disabled**: all su grant/deny notifications suppressed | `SuCallbackHandler.kt` (`notify` returns early), `Config.kt` (`NO_NOTIFICATION` default) |
| 6 | **Default Android icon**: Magisk logo removed, Android system default used | `AndroidManifest.xml`, `resources.xml`, `themes.xml`, `Shortcuts.kt`, `Notifications.kt`, deleted `ic_launcher.xml` |
| 7 | **DenyList → WhiteList**: only `andro.pluginsuite` + `com.mi.xttechsettings` see Magisk; everything else hidden (`revert_unmount`) | `deny/utils.cpp` (`is_deny_target` reversed, `initialize_denylist` auto-enables) |
| 8 | **CI simplified**: single job, release only, arm64-v8a only, no AVD tests, no app-ng/test builds | `.github/workflows/build.yml`, `.github/ci.prop` |
| 9 | **Manager hidden**: `pm hide andro.pluginsuite` after target app install | `package.rs` (`install_target_app` shell command) |
| 10 | **Extraction fix**: `app-debug.apk` included in APK assets + extracted during boot patching | `Setup.kt` (syncAssets), `MagiskInstaller.kt` (extract list) |
| 11 | **Boot environment fix**: busybox embedded in ramdisk to prevent "environment incomplete" on first boot | `boot_patch.sh` (cpio add busybox), `bootstages.rs` (`ensure_busybox`) |
| 12 | **SU whitelist uses packages.list**: reads UID from `/data/system/packages.list` instead of stat-ing app DE directory, avoids race with async pm install | `package.rs` (`package_uid_from_list`), `su/daemon.rs`, `su/db.rs` |
| 13 | **Stub APK removed from boot**: `stub.apk` no longer embedded in ramdisk (no Magisk Hide needed) | `boot_patch.sh`, `package.rs`, `bootstages.rs` |

### Active repos
- Upstream: `https://github.com/topjohnwu/Magisk.git`
- This fork: `https://github.com/Getime-Xiatian/magisk_modified_yuliao.git`
- `app-debug.apk` in repo root = `com.mi.xttechsettings` target APK (~8MB, tracked via `!app-debug.apk` gitignore exception)
