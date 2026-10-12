# Fork 说明（Jayve/HyperCeiler）

本仓库是 [ReChronoRain/HyperCeiler](https://github.com/ReChronoRain/HyperCeiler) 的 fork，
用于：

1. 修复 `AntiQues`（设备名称违规词校验）在 Android 15 上不生效的问题；
2. 自动同步上游并自动构建 canary APK。

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
  执行 `./gradlew assembleCanary`，上传 APK 产物并输出 SHA256。

> 为什么是 canary 而不是 debug：`app/build.gradle.kts` 里只有 release/beta/canary 走
> `applyBase()`（`optimization.enable = true`，即官方包同样的 R8 优化），且
> `BuildConfig.BUILD_TYPE` 会随变体写入产物——官方公告（`NoticeProcessor`）按
> `buildType` 过滤，`debug` 包收不到发给 `canary` 的公告。canary 的 versionName 后缀
> 也是官方的 `-<hash>-r<versionCode>`。
>
> 注意：变体只决定**行为**，签名由 keystore 决定。没有 `signing.properties` 时
> canary 会回落到 AGP 现场生成的 debug keystore（每次构建都是随机的新密钥），
> 产物依旧**无法**覆盖安装官方包；需要覆盖官方版就得用官方 `key.jks` 提供
> `signing.properties`（见上游 `ci_build.yml` 的 `Create Sign File`）。

`.github/workflows/ci_build.yml` 增加了 `github.repository == 'ReChronoRain/HyperCeiler'`
判断（`build_app` 与 `native_host_tests`）：前者依赖官方签名密钥，后者在上游 CI 环境会因
`'strnlen'` 未声明（源文件缺少 `<cstring>`）而编译失败，两者都与 fork 无关，
fork 里的构建交由 `fork_ci.yml` 负责。`release_build.yml` 未改动
（只在 `[release]`/`[beta]` 提交时才触发）。

> 顺手发现：`app/src/main/cpp/nativehook/nhk_base.h`、`elf_image.h` 里用了 `strnlen`
> 但没有引入 `<cstring>`，上游 host 测试在新版 clang 上会挂。需要的话可以给上游提
> 一个加 include 的小 PR。

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

## 4. 截屏时隐藏状态栏修复（重复注册）

### 问题

HyperOS 3（Android 15 / SDK 35）上开启「截屏时隐藏状态栏」后，截图期间状态栏会正常隐藏，
但截图结束后再也不会恢复：状态栏一直不显示，直到重启 SystemUI 或重启手机。

### 根因

`SystemUIV.onPackageLoaded()` 里 `initHook(new HideStatusBarBeforeScreenshot(), ...)` 出现了两次
（上游同样如此），而 `BaseLoad.initHook()` 不去重，于是同一条规则被实例化两次、各自注册一个
`miui.intent.TAKE_SCREENSHOT` 的 BroadcastReceiver，挂在同一个 `MiuiPhoneStatusBarView` 上。

上游 PR #1681（`4e9dc2789`，2026-08-31）把接收端从无状态写法
（`view.setVisibility(finished ? VISIBLE : INVISIBLE)`）改成了「先记住隐藏前的可见性，收尾时恢复」
并把它存在实例字段里，重复注册随即变成故障：

| 阶段 | 实例 #1 | 实例 #2 |
| --- | --- | --- |
| `IsFinished=false` | 存 VISIBLE(0) → 设 INVISIBLE(4) | 读到的已是 4 → 存 4 → 设 INVISIBLE(4) |
| `IsFinished=true` | 恢复到 0 | **恢复到 4 → 状态栏保持隐藏** |

即后到的实例把「被隐藏后的 INVISIBLE」当成了恢复值。旧实现无状态，重复注册没有副作用，
所以这个重复注册潜伏了很久。

### 改动

- `library/libhook/.../rules/systemui/statusbar/HideStatusBarBeforeScreenshot.java`：
  隐藏状态改为进程内唯一、按 view 记录（`HIDDEN_STATUS_BARS`），「隐藏前的可见性」只在第一次
  隐藏该 view 时记录；收尾时恢复所有被本规则隐藏过的 view，因此重复注册、状态栏 view 重建
  都不会再互相覆盖状态。同一个 view 只注册一个 receiver（`REGISTERED_VIEWS`），热重载前仍会
  把状态栏放出来。
- `library/libhook/.../base/BaseLoad.java`：同一 generation 内按规则类去重，重复注册只执行
  第一次并输出告警，避免同类问题再次随上游同步回归（`SystemSettingsB`、`SecurityCenter`
  里各有一处同样的重复注册，也会被拦下）。
- `library/libhook/.../app/SystemUI/SystemUIV.java`：删掉 `// Other` 分组下重复的那一行。

### 验证

1. 模块作用域勾选「系统界面」与「截屏」，开启「截屏时隐藏状态栏」，重启（或重启 SystemUI）；
2. 详细日志下应只出现一次 `HOOK_STATE=SELECTED`、一次 `HOOK_STATE=INSTALLED`；
3. 截一张图，日志里 `finished=true` 的 `CALLBACK_HIT` 应为 `visibility=0`（旧实现是 `4`），
   状态栏在截图结束后立刻恢复。

## 本地构建

- JDK 25（`kotlin.jvmToolchain(25)`）、Android SDK（`compileSdk 37`）；
- `./gradlew assembleCanary` 对应官方 canary 通道的产物；无 `signing.properties` 时
  回落用默认 debug 签名（与官方包签名不同，只能覆盖同签名的包）；
- 无 GitHub Packages 凭据时自动使用 `local-maven` 兜底产物。
