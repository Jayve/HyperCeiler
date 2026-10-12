# expansion-compat（离线兜底产物源码）

`com.sevtinge.hyperceiler:hyperceiler-expansion` 由上游发布在 GitHub Packages
（`https://maven.pkg.github.com/ReChronoRain/HyperCeiler`）。没有 `read:packages` 凭据时
fork 里无法解析该依赖。

> 注意：同一仓库还托管了 `fan.miuix:*`（Maven Central/JitPack 上都没有），所以本兜底
> **无法**让 CI 完全脱离凭据；它只是在官方扩展库不可用时提供一个可用替代，详见 FORK.md。

本目录只补齐 HyperCeiler 主仓库实际引用到的成员：

| 类 | 成员 | 被谁使用 |
| --- | --- | --- |
| `expansion.utils.SignUtils` | `isSignCheckPass(Context)`、`getSHA256Signature(Context)` | `NoticeProcessor`、`DeviceInfoBuilder`、`HomePageBannerManager` |
| `expansion.utils.TokenUtils` | `getDeviceToken(String, String, String, String)` | `libhook/utils/api/DeviceHelper.kt` |

编译产物放在 `local-maven/`，由 `settings.gradle.kts` 注册为本地 Maven 仓库，
**仅在上游私有仓库不可用时兜底**：只要设置了 `GIT_ACTOR`/`GIT_TOKEN`（或
`signing.properties` 里的 `gpr.user`/`gpr.key`），Gradle 会优先使用官方产物。

重新生成产物（Windows）：

```powershell
.\tools\build-expansion-compat.ps1
```

其它平台等价命令：

```bash
javac -nowarn -source 17 -target 17 -cp $ANDROID_HOME/platforms/android-37/android.jar \
  -d /tmp/expansion-compat $(find local-maven-src/expansion-compat -name '*.java')
jar --create --file local-maven/com/sevtinge/hyperceiler/hyperceiler-expansion/1.0.0/hyperceiler-expansion-1.0.0.jar \
  -C /tmp/expansion-compat .
```
