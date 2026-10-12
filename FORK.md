# Fork 说明（Jayve/HyperCeiler）

本仓库是 [ReChronoRain/HyperCeiler](https://github.com/ReChronoRain/HyperCeiler) 的 fork，
用于：

1. 修复 `AntiQues`（设备名称违规词校验）在 Android 15 上不生效的问题；
2. 自动同步上游并自动构建 debug APK。

上游变更请只向上游仓库提 Issue/PR；本文件描述本 fork 的差异与用法。

## 1. AntiQues 修复

### 问题

开启"禁用设备名称敏感词校验"后，Android 15（SDK 35）上热点名称依然每次开热点都被改回
机型名（`ro.product.marketname`）。

### 根因

模块按 SDK 把系统框架的规则拆成两份：

| 分支 | 注解 | 注册的 AntiQues |
| --- | --- | --- |
| `SystemFrameworkB` | `@HookBase(targetPackage = "system", minSdk = 36)` | `rules.systemframework.others.AntiQues`（框架侧，能治本） |
| `SystemFrameworkV` | `@HookBase(targetPackage = "system", maxSdk = 35)` | `rules.systemsettings.AntiQues`（只 hook 设置应用里的类） |

`ModuleMatcher` 会按设备 SDK 过滤，所以 Android 15 只加载 `V` 分支；而 `V` 分支注册的规则
管的是 `com.android.settings.*`（`MiuiDeviceNameEditFragment` / `EditTetherFragment` /
`DeviceNameCheckManager` / `MiuiBTUtils`），这些类并不存在于 `system_server`，实际生效的
框架侧入口（`com.android.server.wifi.Utils#checkDeviceNameIsIllegalSync`）从来没被挂上。

顺带一提：设置侧的 hook 会把 `miui.os.Build.IS_INTERNATIONAL_BUILD` 临时置 true，从而跳过
联网检查，名字也就不会写进 WN 的本地缓存；而框架侧用的恰好是"只查缓存"的同步接口，
缓存未命中返回 `-1`，调用方又把非 0 一律当作违规，于是名字被重置。

### 改动

- `library/libhook/.../app/SystemFramework/SystemFrameworkV.java`：
  改为注册框架侧的 `rules.systemframework.others.AntiQues`（与 `B` 分支一致）；
  设置应用侧的那套仍由 `SystemSettingsV`/`SystemSettingsB` 在 `com.android.settings` 进程负责。
- `library/libhook/.../rules/systemframework/others/AntiQues.java`：
  挂载失败时保留外层 Hook 以便下次重试、把失败原因写进日志（原实现静默吞掉异常），
  成功挂载后再卸载外层 Hook。

### 验证

1. 模块作用域勾选"系统框架"，开启"禁用设备名称敏感词校验"；
2. 重启（Hook 在 `system_server` 启动、Wi-Fi 服务加载时挂载）；
3. 设置里改好热点名 → 开热点 → `adb shell dumpsys wifi` 查看
   `WifiApConfigStore config: ssid = ...` 是否保持不变；
   若被改写，logcat 里会出现 `WifiOptimizationImpl: the hotspot ssid is illegal, reset to
   default device name !`。

## 2. 自动同步 + 自动构建

工作流：`.github/workflows/fork_ci.yml`（Fork Sync & Build）

- 触发：`push` 到 `main`、每天定时（21:00 UTC）、手动 `workflow_dispatch`；
- `sync_upstream`：把 `upstream/main` 合并进 `main` 并推送；冲突时中止合并并自动开/更新
  带 `sync-conflict` 标签的 Issue 提示手动处理；
- `build`：同步后（或 push 后）用 JDK 25 执行 `./gradlew assembleDebug`，上传 APK 产物。

`.github/workflows/ci_build.yml` 增加了 `github.repository == 'ReChronoRain/HyperCeiler'`
判断：上游 CI 依赖官方签名密钥，fork 里跑必然失败，交由 `fork_ci.yml` 负责。
`release_build.yml` 未改动（只在 `[release]`/`[beta]` 提交时才触发）。

## 3. 私有依赖的离线兜底

上游把 `com.sevtinge.hyperceiler:hyperceiler-expansion:1.0.0` 发布在 GitHub Packages 私有
仓库，fork 默认没有 `read:packages` 凭据，构建会在依赖解析阶段失败。因此：

- `local-maven-src/expansion-compat/`：补齐主仓库实际引用到的成员
  （`SignUtils.isSignCheckPass` / `getSHA256Signature`、`TokenUtils.getDeviceToken`）；
- `local-maven/`：编译产物，由 `settings.gradle.kts` 注册为最后一个仓库兜底；
- 提供凭据时（`GIT_ACTOR`/`GIT_TOKEN` 或 `signing.properties` 中的 `gpr.user`/`gpr.key`）
  Gradle 会优先使用官方产物，兜底产物不参与构建；
- 缺失凭据时不再直接让配置阶段抛异常，只打印 WARNING。

想用官方扩展库：在 fork 的 `Settings → Secrets and variables → Actions` 里添加
`GIT_ACTOR`（GitHub 用户名）与 `GIT_TOKEN`（具备 `read:packages` 的 PAT）。

重新生成兜底产物：`./tools/build-expansion-compat.ps1`（需要 JDK 17+ 与 Android SDK）。

## 本地构建

- JDK 25（`kotlin.jvmToolchain(25)`）、Android SDK（`compileSdk 37`）；
- 无 `signing.properties` 时构建 debug 变体（用默认 debug 签名）；
- 无 GitHub Packages 凭据时自动使用 `local-maven` 兜底产物。
