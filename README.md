# Magisk Modified — PluginSuite

基于 [topjohnwu/Magisk](https://github.com/topjohnwu/Magisk) 深度定制的 Root 方案。

## 特性

- **包名**: `andro.pluginsuite` (原 `com.topjohnwu.magisk`)
- **应用名**: Settings（桌面不显示图标，安装后自动 `pm hide`）
- **白名单模式**: 仅 `com.mi.xttechsettings` 可获取 root 权限，其余应用 / ADB / Shell 均静默拒绝
- **DenyList → WhiteList**: Zygisk 排除列表反转，仅 `andro.pluginsuite` + `com.mi.xttechsettings` 可见，其余默认全部排除
- **Boot 镜像内嵌**: 修补 boot 时自动将管理器 APK 写入 `/.backup/`，开机后自动安装
- **Boot 环境自动修复**: busybox 嵌入 ramdisk，首次刷入不再提示"环境不完整"
- **全局静默**: 无 Toast、无弹窗、无通知
- **无更新检查**: 设置中已移除更新相关 UI 和后台定时任务

## 构建

```bash
# 设置 NDK
python build.py ndk

# 构建全部 (release)
python build.py -r all

# 仅构建 native
python build.py -r native

# 仅构建 APK
python build.py -r app
```

CI 配置为 GitHub Actions，推送 `master` 自动编译，产物仅 arm64-v8a release APK。

## 修补 Boot 镜像

使用编译产出的 `app-release.apk` 在手机上通过 Magisk 修补 boot 镜像，刷入后：

1. MagiskInit 在 `pre-init` 阶段提取文件到 tmpfs，`post-fs-data` 自动将 busybox 复制到 `/data/adb/magisk/`
2. 环境初始化完整，DenyList/WhiteList、Zygisk、模块脚本全部正常启动
3. `boot-complete` 后自动 `pm install` 安装 `com.mi.xttechsettings` 并执行 `pm hide andro.pluginsuite`
4. `com.mi.xttechsettings` 自动获取 root 权限，无需弹窗确认

## 架构变更

| 组件 | 变更 |
|------|------|
| `native/src/core/su/daemon.rs` | `build_su_info` / `uid_granted_root` 硬编码白名单 |
| `native/src/core/deny/utils.cpp` | `is_deny_target` 白名单逻辑，开机强制开启 DenyList |
| `native/src/core/package.rs` | `target_apk_fd` / `install_target_app` 内嵌 APK 安装 + `pm hide` |
| `native/src/core/bootstages.rs` | `post-fs-data` / `boot-complete` 阶段调用，`ensure_busybox()` 恢复 busybox |
| `scripts/boot_patch.sh` | 嵌入 APK + busybox 到 ramdisk |
| `app/core/src/main/.../Config.kt` | 默认 `suAutoResponse=SU_AUTO_DENY`，`suNotification=NO_NOTIFICATION` |
| `app/core/src/main/.../SuCallbackHandler.kt` | `notify()` 被禁用 |
| `native/src/core/package.rs` | `package_uid_from_list()` 从 `packages.list` 查 UID，避免异步安装竞态 |

## 开发者

- [@topjohnwu](https://github.com/topjohnwu) — Magisk 原作者
- [@Getime-Xiatian](https://github.com/Getime-Xiatian) — 本修改版维护者

## License

```
Magisk, including all git submodules are free software:
you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation,
either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
```
