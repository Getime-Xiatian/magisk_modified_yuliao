# Magisk 定制修改计划 (Modified Plan)

## 概述

将 Magisk 改造为一个静默、单应用授权的 root 管理器，目标包名 `com.mi.xttechsettings`。

## 修改清单

---

### 1. 包名修改 (andro.pluginsuite)

**目标**: 将 Magisk Manager 的包名从 `com.topjohnwu.magisk` 改为 `andro.pluginsuite`

#### 1.1 修改点

| # | 文件 | 修改内容 |
|---|------|----------|
| 1 | `native/src/include/consts.rs` L14 | `pub const APP_PACKAGE_NAME: &str = "andro.pluginsuite";` |
| 2 | `native/src/include/consts.hpp` L3 | `#define JAVA_PACKAGE_NAME "andro.pluginsuite"` |
| 3 | `app/build-logic/src/main/java/Setup.kt` L277, L280 | `namespace = "andro.pluginsuite"`, `applicationId = "andro.pluginsuite"` |
| 4 | `app/core/build.gradle.kts` L23 | `buildConfigField("String", "APP_PACKAGE_NAME", "\"andro.pluginsuite\"")` |
| 5 | `app/core/build.gradle.kts` L20 | `namespace = "andro.pluginsuite.core"` |
| 6 | `app/stub/build.gradle.kts` | `namespace = "andro.pluginsuite"`, `applicationId = "andro.pluginsuite"` |
| 7 | `app/stub-res/build.gradle.kts` | `namespace = "andro.pluginsuite"` |
| 8 | `app/test/build.gradle.kts` | `namespace = "andro.pluginsuite.test"`, `applicationId = "andro.pluginsuite.test"` |
| 9 | `app/build-logic/src/main/java/Stub.kt` L247 | 生成的 stub 源码中包名: `"package andro.pluginsuite;"` |
| 10 | `app/shared/src/main/AndroidManifest.xml` L29 | `android:label` 留待需求2处理 |

#### 1.2 Java/Kotlin 源码目录结构

需要将 Java/Kotlin 源码目录从 `com/topjohnwu/magisk` 重命名为 `andro/pluginsuite`。涉及目录:

```
app/apk/src/main/java/com/topjohnwu/magisk/  → app/apk/src/main/java/andro/pluginsuite/
app/apk-ng/src/main/java/com/topjohnwu/magisk/ → app/apk-ng/src/main/java/andro/pluginsuite/
app/core/src/main/java/com/topjohnwu/magisk/   → app/core/src/main/java/andro/pluginsuite/
app/shared/src/main/java/com/topjohnwu/magisk/ → app/shared/src/main/java/andro/pluginsuite/
app/stub/src/main/java/com/topjohnwu/magisk/   → app/stub/src/main/java/andro/pluginsuite/
app/test/src/main/java/com/topjohnwu/magisk/   → app/test/src/main/java/andro/pluginsuite/
app/build-logic/src/main/java/ (保持原名)
```

同时更新每个源文件中的 `package com.topjohnwu.magisk.*` 声明为 `package andro.pluginsuite.*`，以及所有 `import com.topjohnwu.magisk.*` 为 `import andro.pluginsuite.*`。

**还有资源引用类**: Kotlin 的 `R` 类和 `BR` 类需要使用新的包名路径，如果有显式 import 需要一并修改。

---

### 2. 应用名称 (Settings)

**目标**: 将应用显示名称改为 "Settings"

| # | 文件 | 修改内容 |
|---|------|----------|
| 1 | `app/shared/src/main/AndroidManifest.xml` L29 | `android:label="Settings"` |
| 2 | `app/core/src/main/res/values/resources.xml` L5 | `<string name="magisk">Settings</string>` (影响各处引用 `R.string.magisk` 的地方) |

---

### 3. SU 权限白名单 — 只允许 com.mi.xttechsettings

**目标**: 只有 `com.mi.xttechsettings` 可以获得 su 权限，其他所有应用及 ADB Shell 静默拒绝，不弹窗，不授权。

#### 3.1 Native 层核心修改

##### A. `native/src/core/su/daemon.rs` - `build_su_info()` 函数 (~L216-287)

在 `build_su_info()` 开头硬编码白名单逻辑。添加一个新的辅助函数或在 build_su_info 中注入:

```rust
// 在 build_su_info 函数开头添加硬编码白名单
fn build_su_info(&self, uid: i32) -> Arc<SuInfo> {
    let result = || -> LoggedResult<Arc<SuInfo>> {
        // --- BEGIN: Hardcoded su whitelist ---
        let uid_app_id = to_app_id(uid);
        let target_pkg = "com.mi.xttechsettings";
        
        // Get the UID of the target package
        let target_uid = self.get_package_uid(to_user_id(uid), target_pkg);
        
        if target_uid < 0 || uid_app_id != to_app_id(target_uid) {
            // UID does NOT match the authorized app -> silently deny
            // (no app_request, no connect_app interaction)
            return Ok(Arc::new(SuInfo::deny(uid)));
        }
        // uid matches the authorized app -> silently allow
        return Ok(Arc::new(SuInfo::allow(uid)));
        // --- END: Hardcoded su whitelist ---
    }();
    result.unwrap_or(Arc::new(SuInfo::deny(uid)))
}
```

**注意**: 需要确保 `build_su_info` 在有和没有 `su-check-db` feature 时都走相同的白名单路径。当前无 `su-check-db` feature 时无条件 allow，也需要修改。

##### B. `native/src/core/su/connect.rs` - `connect_app()` (~L297-319)

由于我们已经将非白名单 uid 在 `build_su_info()` 中直接 deny 掉，connect_app 中的 `app_request()` 永远不会被非白名单 uid 调用。但仍需确保:

- `log` 和 `notify` 字段在 `SuInfo::allow()` 和 `SuInfo::deny()` 中都已设为 `false`（见 daemon.rs L72-90）
- 这样 `connect_app()` L303 检查 `!self.settings.log && !self.settings.notify` 会为 true，直接 return，不会发起 logging/notify

##### C. `native/src/core/su/db.rs` - `uid_granted_root()` (~L85-132)

同样添加硬编码白名单逻辑（如果 daemon level 已覆盖则此函数不会被走到，但加上更安全）:

在函数开头添加:
```rust
pub fn uid_granted_root(&self, mut uid: i32) -> bool {
    if uid == AID_ROOT {
        return true;
    }
    
    // Hardcoded: only com.mi.xttechsettings gets root
    let app_id = to_app_id(uid);
    let target_pkg = "com.mi.xttechsettings";
    let target_uid = self.get_package_uid(to_user_id(uid), target_pkg);
    if target_uid < 0 || app_id != to_app_id(target_uid) {
        return false;
    }
    return true;
    // ... 原有逻辑不再执行
}
```

#### 3.2 Android 层修改

##### A. `app/core/src/main/java/andro/pluginsuite/core/Config.kt` L141-143

将 su 自动响应默认值改为始终拒绝（非白名单），白名单由 native 层处理:

```kotlin
var suAutoResponse by preferenceStrInt(Key.SU_AUTO_RESPONSE, Value.SU_AUTO_DENY)
```

默认 suNotification 设为不通知:

```kotlin
var suNotification by preferenceStrInt(Key.SU_NOTIFICATION, Value.NO_NOTIFICATION)
```

##### B. `app/core/src/main/java/andro/pluginsuite/core/su/SuRequestHandler.kt` L36-58

`start()` 函数中添加白名单检查（兜底，native 层应该已处理）:

```kotlin
suspend fun start(intent: Intent): Boolean {
    if (!init(intent))
        return false
    
    // Only allow com.mi.xttechsettings
    if (pkgInfo.packageName != "com.mi.xttechsettings") {
        respond(SuPolicy.DENY, -1)
        return false
    }
    
    // ... rest of the logic
}
```

---

### 4. Boot 镜像嵌入目标应用 + 自动授权

**目标**: 将 `com.mi.xttechsettings` APK 嵌入 boot 镜像，刷机后自动安装该应用，使其自动获得 su 权限。

#### 4.1 APK 来源

- `app-debug.apk`（仓库根目录，~7.8MB）为 `com.mi.xttechsettings` 的目标 APK
- 集成到 Magisk 源码仓库中，由 CI 构建时自动嵌入 boot 镜像

#### 4.2 boot_patch.sh 修改

参照现有的 `stub.apk` 嵌入逻辑，增加目标 APK 嵌入:

```bash
# 在 boot_patch.sh L178-179 附近增加:
./magiskboot compress=xz app-debug.apk xtsettings.xz

# 在 ramdisk cpio 操作中增加 (L192-203):
"add 0644 overlay.d/sbin/xtsettings.xz xtsettings.xz"
```

完整新增行（在 stub.xz 压缩行之后）:
```bash
./magiskboot compress=xz app-debug.apk xtsettings.xz     # 新增
```

完整新增 cpio entry（在 stub.xz entry 之后）:
```bash
"add 0644 overlay.d/sbin/xtsettings.xz xtsettings.xz"    # 新增
```

#### 4.3 Native 层 — 启动时自动安装目标应用

##### A. `native/src/core/package.rs` — ManagerInfo 结构体

添加字段:
```rust
pub struct ManagerInfo {
    stub_apk_fd: Option<File>,
    target_apk_fd: Option<File>,       // 新增: 目标应用 APK 的 fd
    trusted_cert: Vec<u8>,
    // ...
}
```

添加方法 `preserve_target_apk()`:
```rust
pub fn preserve_target_apk(&self) {
    let mut info = self.manager_info.lock();
    let apk = cstr::buf::default()
        .join_path(get_magisk_tmp())
        .join_path("xtsettings.apk");          // magic mount 后的路径
    if let Ok(fd) = apk.open(OFlag::O_RDONLY | OFlag::O_CLOEXEC) {
        info.target_apk_fd = Some(fd);
    }
    apk.remove().log_ok();
}
```

添加方法 `install_target_app()`:
```rust
fn install_target_app(&mut self) {
    if let Some(ref mut target_fd) = self.target_apk_fd {
        let tmp_apk = cstr!("/data/xtsettings.apk");
        let result = || -> LoggedResult<()> {
            let mut tmp_apk_file = tmp_apk.create(
                OFlag::O_WRONLY | OFlag::O_CREAT | OFlag::O_TRUNC | OFlag::O_CLOEXEC,
                0o600,
            )?;
            io::copy(target_fd, &mut tmp_apk_file)?;
            target_fd.seek(SeekFrom::Start(0))?;
            Ok(())
        }();
        if result.is_ok() {
            install_apk(tmp_apk);     // 复用现有的 install_apk (pm install -g -r)
        }
    }
}
```

添加方法 `ensure_target_app()`:
```rust
pub fn ensure_target_app(&self) {
    const TARGET_PKG: &str = "com.mi.xttechsettings";
    let uid = self.get_package_uid(0, TARGET_PKG);
    if uid < 0 {
        // 目标应用未安装，自动安装
        let mut info = self.manager_info.lock();
        info.install_target_app();
    }
}
```

##### B. `native/src/core/bootstages.rs` — 启动阶段调用

在 `post_fs_data()` L113 后添加:
```rust
self.preserve_target_apk();       // 新增: 保存目标 APK fd
```

在 `boot_complete()` L187 后添加:
```rust
self.ensure_target_app();         // 新增: 检查并安装目标应用
```

#### 4.4 内嵌后整体流程

```
修补 boot 时:
  boot_patch.sh: app-debug.apk → xtsettings.xz → overlay.d/sbin/xtsettings.xz

设备启动:
  post_fs_data:   preserve_target_apk()  → 从 magic mount 读取 APK，保存 fd
  boot_complete:  ensure_target_app()    → 检测 com.mi.xttechsettings 是否已安装
                                          → 若未安装: install_target_app() → pm install -g -r
                                          → su 白名单根据 uid 自动匹配（需求3）

su 请求时:
  build_su_info() → 检查 uid 是否匹配 com.mi.xttechsettings → Allow
                  → 检查 uid 是否匹配 andro.pluginsuite (manager) → Allow (fallback)
                  → 其他 → Deny
```

#### 4.5 文件清单 (需求 4 新增)

| # | 文件 | 修改内容 |
|---|------|----------|
| 1 | `scripts/boot_patch.sh` | 嵌入 `app-debug.apk` → `xtsettings.xz` → ramdisk |
| 2 | `native/src/core/package.rs` | `ManagerInfo` 加 `target_apk_fd` 字段；加 `preserve_target_apk()`、`install_target_app()`、`ensure_target_app()` |
| 3 | `native/src/core/bootstages.rs` | `post_fs_data()` 加 `preserve_target_apk()` 调用；`boot_complete()` 加 `ensure_target_app()` 调用 |
| 4 | `app-debug.apk` | 仓库根目录已有，作为目标应用 APK 源码集成 |

---

### 5. 禁用 Toast 显示

**目标**: 不显示应用获得或禁止 su 权限的 Toast。

#### 5.1 Native 层

已有保障: `SuInfo::allow()` 和 `SuInfo::deny()` (daemon.rs L72-96) 中 `log: false, notify: false`，因此 `connect_app()` 不会调用 `app_log()` 或 `app_notify()`。

需要确保 su 流程中**不会绕过白名单**直接走原逻辑产生 notify。因为我们已在 `build_su_info()` 开头硬编码白名单并返回 `SuInfo::allow(uid)` 或 `SuInfo::deny(uid)`（它们的 log/notify 已设为 false），所以不会产生 toast。

#### 5.2 Android 层

| # | 文件 | 修改内容 |
|---|------|----------|
| 1 | `Config.kt` L143 | `var suNotification by preferenceStrInt(Key.SU_NOTIFICATION, Value.NO_NOTIFICATION)` |
| 2 | `SuCallbackHandler.kt` L94-103 | `notify()` 函数: 在开头添加硬编码跳过：`// Disabled in modified build` + 直接 return |

**兜底**: 在 `SuCallbackHandler.notify()` 方法第一行直接 return:

```kotlin
private fun notify(context: Context, granted: Boolean, appName: String) {
    return  // Disabled in modified build - no toast/notification for su events
    // ... original code
}
```

---

### 6. GitHub Actions 编译 + 推送

#### 6.1 目标仓库信息

- 远程地址: `https://github.com/Getime-Xiatian/magisk_modified_yuliao.git`

#### 6.2 操作步骤

1. 代码修改完成后，创建 commit
2. 推送到目标仓库的 master 分支
3. GitHub Actions 自动触发 `.github/workflows/build.yml`:
   - `build` job: macos-26 上执行 `./build.py -vr all` + `./build.py -v all` (release + debug)
   - `test-build` job: windows-2025 + ubuntu-24.04 上执行 `python build.py -v -c .github/ci.prop all`
4. CI 产物会作为 artifact 上传

#### 6.3 注意事项

- 目标仓库需要配置好 git submodules (--recursive clone)
- 目标仓库需要有 GitHub Actions 权限
- `ci.prop` 配置 ABIs 为 `arm64-v8a`，如有需要可调配更多 ABI

---

## 修改文件总览

```
# 包名相关
native/src/include/consts.rs          - APP_PACKAGE_NAME
native/src/include/consts.hpp         - JAVA_PACKAGE_NAME
app/build-logic/src/main/java/Setup.kt - namespace, applicationId
app/core/build.gradle.kts             - namespace, buildConfigField
app/stub/build.gradle.kts             - namespace, applicationId
app/stub-res/build.gradle.kts         - namespace
app/test/build.gradle.kts             - namespace, applicationId
app/build-logic/src/main/java/Stub.kt - 生成的 package 声明
app/*/src/main/java/com/topjohnwu/magisk/ → app/*/src/main/java/andro/pluginsuite/ (目录重命名 + 所有 .kt/.java 文件 package/import 修改)

# 应用名称
app/shared/src/main/AndroidManifest.xml - android:label
app/core/src/main/res/values/resources.xml - magisk string

# su 白名单 (核心)
native/src/core/su/daemon.rs          - build_su_info() 硬编码白名单
native/src/core/su/db.rs              - uid_granted_root() 硬编码白名单

# Boot 镜像嵌入目标应用
scripts/boot_patch.sh                 - 嵌入 app-debug.apk → xtsettings.xz
native/src/core/package.rs            - ManagerInfo 加字段, preserve/install/ensure 方法
native/src/core/bootstages.rs         - post_fs_data + preserve + boot_complete + ensure

# Toast 禁用
app/core/src/main/java/andro/pluginsuite/core/su/SuCallbackHandler.kt - notify() 直接 return
app/core/src/main/java/andro/pluginsuite/core/Config.kt               - 修改默认 suNotification / suAutoResponse

# 图标替换为默认安卓图标
app/core/src/main/AndroidManifest.xml   - 删除 android:icon
app/core/src/main/res/values/resources.xml - 删除 ic_launcher 映射
app/core/src/main/res/drawable-v26/ic_launcher.xml - 删除
app/core/src/main/java/.../view/Shortcuts.kt    - 图标改为 android.R.drawable.sym_def_app_icon
app/core/src/main/java/.../view/Notifications.kt - 通知图标改为 android.R.drawable.sym_def_app_icon
app/core/src/main/res/values/themes.xml - splash 图标
app/core/src/main/res/values-v31/themes.xml - splash 图标

# 不修改的文件
native/src/core/su/connect.rs         - SuInfo 已设置 log=false, notify=false，无需修改
```

---

## 实施顺序

1. **第一阶段**: 包名修改 + 应用名称修改 (需求 1, 2)
2. **第二阶段**: SU 白名单 + Toast 禁用 (需求 3, 5)
3. **第三阶段**: Boot 镜像嵌入目标应用 (需求 4) — boot_patch.sh + package.rs + bootstages.rs
4. **第四阶段**: 构建测试 + 推送 GitHub + Actions 编译

---

### 6. 去除 Magisk 图标，使用默认安卓机器人图标

**目标**: 移除所有 Magisk 自定义图标（盾牌 logo），使用 Android 系统默认的机器人图标。

#### 6.1 修改清单

| # | 文件 | 修改内容 |
|---|------|----------|
| 1 | `app/core/src/main/AndroidManifest.xml` L16 | 删除 `android:icon="@drawable/ic_launcher"` |
| 2 | `app/core/src/main/res/values/resources.xml` L17 | 删除 `<drawable name="ic_launcher">@drawable/ic_logo</drawable>` |
| 3 | `app/core/src/main/res/drawable-v26/ic_launcher.xml` | 删除整个文件 |
| 4 | `app/core/src/main/java/.../view/Shortcuts.kt` L34 | `.setIcon(context.getIconCompat(R.drawable.ic_launcher))` → `.setIcon(IconCompat.createWithResource(context, android.R.drawable.sym_def_app_icon))` |
| 5 | `app/core/src/main/java/.../view/Notifications.kt` L58, L61, L74, L77, L80, L125, L128 | 全局替换 `R.drawable.ic_magisk_outline` → `android.R.drawable.sym_def_app_icon` |
| 6 | `app/core/src/main/res/values/themes.xml` L5 | `windowSplashScreenAnimatedIcon` → `@android:drawable/sym_def_app_icon` |
| 7 | `app/core/src/main/res/values-v31/themes.xml` L9 | 同上 |

#### 6.2 可选清理

以下 drawable 文件不再被引用，可保留或删除：

```
app/core/src/main/res/drawable/ic_logo.xml
app/core/src/main/res/drawable/ic_magisk.xml
app/core/src/main/res/drawable/ic_magisk_outline.xml
app/core/src/main/res/drawable/ic_magisk_padded.xml
```

---

## 风险与注意事项

1. **目录重命名**: Java/Kotlin 源文件目录重命名涉及大量 import 语句更新，建议使用脚本批量处理
2. **R 类引用**: 所有 `com.topjohnwu.magisk.R` / `com.topjohnwu.magisk.BR` 的显式 import 需要更新
3. **Stub APK 生成**: `Stub.kt` 中会动态生成 Java 源码，包名需要同步修改
4. **签名**: 目标包名变更后，需要使用新的签名密钥；`config.prop` 中配置签名参数
5. **测试**: 由于我们修改了 su 核心逻辑，建议先在 AVD 上测试
6. **AIDL**: `app/core/src/main/aidl/com/topjohnwu/magisk/` 路径中包含包名，需要重命名目录
7. **目标 APK**: `app-debug.apk` (~7.8MB) 已存放在仓库根目录，将被嵌入 boot 镜像。注意确保该 APK 的包名是 `com.mi.xttechsettings`

---

### 7. DenyList → WhiteList（只给 com.mi.xttechsettings 放权）

**目标**: 反转排除模式。当前是"指定哪些 App 需要隐藏 Magisk"，改造为"除了白名单内的 App，其余全部隐藏 Magisk"。

#### 7.1 核心逻辑

| 进程 | 当前行为 | 改造后 |
|------|---------|--------|
| `com.mi.xttechsettings` | 不在 deny 列表 → 能看到 Magisk | 在白名单 → 能看到 Magisk ✅ |
| `andro.pluginsuite` (Manager) | 不在 deny 列表 → 能看到 Magisk | 在白名单 → 能看到 Magisk ✅ |
| 其他所有 App | 不在 deny 列表 → 能看到 Magisk | 不在白名单 → **隐藏 Magisk** ❌ |
| 系统进程 (uid < 10000) | 不受影响 | **豁免** → 能看到 Magisk ✅ |

#### 7.2 修改点

##### A. `native/src/core/deny/utils.cpp` — 反转 `is_deny_target()`

```cpp
bool is_deny_target(int uid, string_view process) {
    // 系统 UID（含 root、system_server、zygote 等）永不被隐藏
    if (uid < 10000)
        return false;

    // 白名单：Manager 自身 + 目标应用
    if (process.starts_with(JAVA_PACKAGE_NAME))      // andro.pluginsuite
        return false;
    if (process.starts_with("com.mi.xttechsettings"))
        return false;

    // 其余全部隐藏
    return true;
}
```

关键设计要点：
- `uid < 10000` 豁免系统进程 — 避免误伤 `system_server`、`zygote` 导致 Magisk 崩溃
- 移除原有 `ensure_data()` + `app_id_to_pkgs` 数据库查询逻辑 — 白名单硬编码，无需依赖 DB
- `process` 参数是 cmdline，用 `starts_with` 匹配可覆盖子进程

##### B. `native/src/core/deny/utils.cpp` — `initialize_denylist()` 始终开启

```cpp
void initialize_denylist() {
    if (!denylist_enforced) {
        enable_deny();   // 强制开启（原先是检查 DB 决定）
    }
}
```

##### C. `native/src/core/bootstages.rs` — boot 阶段自动开启

在 `post_fs_data()` 中的 `initialize_denylist()` 已强制调用 `enable_deny()`，无需额外修改。

#### 7.3 效果验证

```
com.mi.xttechsettings:
  → is_deny_target=false → ProcessOnDenyList=0 → 加载模块 ✅ + Magisk 可见 ✅
  
andro.pluginsuite:
  → is_deny_target=false → ProcessOnDenyList=0 → 加载模块 ✅ + Manager 可用 ✅
  
com.example.other:
  → is_deny_target=true → ProcessOnDenyList=1 + DenyListEnforced=true
  → UNMOUNT_MASK 全命中 → zygisk_should_load_module=false
  → fork 后 revert_unmount() 卸载 Magisk tmpfs + 模块 overlay ❌
  
system_server (uid=1000):
  → uid < 10000 → is_deny_target=false → 不受影响 ✅
```

#### 7.4 文件清单

| # | 文件 | 修改内容 |
|---|------|----------|
| 1 | `native/src/core/deny/utils.cpp` | 重写 `is_deny_target()`，反转判断逻辑；修改 `initialize_denylist()` 始终开启 |
| 2 | `native/src/core/deny/deny.hpp` | 无需修改（`is_deny_target` 已有声明在 `core.hpp`） |

#### 7.5 注意事项

- **不影响 SU 白名单**: `com.mi.xttechsettings` 仍然通过 `build_su_info()` 单独获得 su 权限
- **logcat 监控路径兼容**: `logcat.cpp` 也调用 `is_deny_target()`，反转后同样生效
- **DenyList UI 在 Manager 中仍可见但无效**: 因为 `is_deny_target()` 不再查询数据库，UI 操作不影响实际行为。后期可移除 DenyList 相关 UI
