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
- `build`：先检查 GitHub Packages 凭据（缺失时立即以明确原因失败，见下一节），再用 JDK 25
  执行 `./gradlew assembleDebug`，上传 APK 产物并输出 SHA256。

`.github/workflows/ci_build.yml` 增加了 `github.repository == 'ReChronoRain/HyperCeiler'`
判断：上游 CI 依赖官方签名密钥，fork 里跑必然失败，交由 `fork_ci.yml` 负责。
`release_build.yml` 未改动（只在 `[release]`/`[beta]` 提交时才触发）。

## 3. 依赖与凭据（CI 构建的前提）

上游把两类依赖都放在 GitHub Packages 仓库
`https://maven.pkg.github.com/ReChronoRain/HyperCeiler`：

- `fan.miuix:*`（16 个 miuix 组件，Maven Central / aliyun / JitPack 上都没有）；
- `com.sevtinge.hyperceiler:hyperceiler-expansion:1.0.0`（本仓库私有扩展库）。

这些包在网页上是公开可见的（`https://github.com/orgs/ReChronoRain/packages?package_type=maven`），
但 GitHub 的 Maven 端点**强制鉴权**——匿名请求返回 401。因此 fork 的 CI 必须提供
`read:packages` 凭据，`fork_ci.yml` 会在构建前检查并给出明确报错。

添加凭据（一次性）：

```bash
# 1) 生成 PAT：Settings → Developer settings → Personal access tokens (classic)
#    → 勾选 read:packages（fine-grained token 需要 "Packages: Read"）
# 2) 写入 fork 的 Actions secrets
gh secret set GIT_ACTOR --repo Jayve/HyperCeiler --body "Jayve"
gh secret set GIT_TOKEN  --repo Jayve/HyperCeiler --body "<你的 PAT>"
```

之后 `Fork Sync & Build` 的构建步骤即可正常解析依赖。

### 离线兜底（可选，仅覆盖扩展库）

`hyperceiler-expansion` 只有 3 个成员被主仓库引用，因此这里补了一份可离线使用的实现：

- `local-maven-src/expansion-compat/`：`SignUtils.isSignCheckPass` / `getSHA256Signature`、
  `TokenUtils.getDeviceToken` 的兜底实现（源码）；
- `local-maven/`：编译产物，由 `settings.gradle.kts` 注册为最后一个仓库；
- 提供凭据时 Gradle 优先使用官方产物，兜底产物不参与构建；
- `settings.gradle.kts` 在凭据缺失时不再让配置阶段抛异常，只打印 WARNING。

注意：`fan.miuix:*` 没有替代品，所以该兜底**不能**让 CI 完全脱离凭据。

重新生成兜底产物：`./tools/build-expansion-compat.ps1`（需要 JDK 17+ 与 Android SDK）。

## 本地构建

- JDK 25（`kotlin.jvmToolchain(25)`）、Android SDK（`compileSdk 37`）；
- 无 `signing.properties` 时构建 debug 变体（用默认 debug 签名）；
- 无 GitHub Packages 凭据时自动使用 `local-maven` 兜底产物。
