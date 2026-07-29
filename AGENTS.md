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

(Add project-specific notes here as they accumulate.)
