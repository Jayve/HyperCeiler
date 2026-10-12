@file:Suppress("UnstableApiUsage")

data class GprCredentials(val user: String, val key: String)

/**
 * 读取 GitHub Packages 凭据，用于解析官方私有扩展库 hyperceiler-expansion。
 *
 * <p>缺失凭据时不再让配置阶段直接失败：跳过该私有仓库，改由仓库内
 * {@code local-maven/} 的离线兜底产物提供同名依赖（见 FORK.md）。</p>
 */
fun loadGprCredentials(): GprCredentials? {
    // 优先从环境变量读取
    val envUser = System.getenv("GIT_ACTOR")?.takeIf { it.isNotBlank() }
    val envKey = System.getenv("GIT_TOKEN")?.takeIf { it.isNotBlank() }

    if (envUser != null && envKey != null) {
        return GprCredentials(envUser, envKey)
    }

    // 从 signing.properties 读取
    val propsFile = File(rootDir, "signing.properties")
    if (propsFile.exists()) {
        val props = java.util.Properties().apply {
            propsFile.inputStream().use { load(it) }
        }

        val user = props.getProperty("gpr.user")?.takeIf { it.isNotBlank() }
        val key = props.getProperty("gpr.key")?.takeIf { it.isNotBlank() }

        if (user != null && key != null) {
            return GprCredentials(user, key)
        }
    }

    println(
        "WARNING: GitHub Packages credentials are missing (GIT_ACTOR/GIT_TOKEN or " +
            "signing.properties with 'gpr.user'/'gpr.key'). The private " +
            "'com.sevtinge.hyperceiler:hyperceiler-expansion' artifact will be resolved from the " +
            "offline fallback in local-maven/ instead. See FORK.md for details."
    )
    return null
}

val gprCredentials: GprCredentials? by lazy { loadGprCredentials() }

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        // Mirrors first: several artifacts on google()/mavenCentral() are redirected
        // to github.com release assets, which is unreachable here and stalls the build.
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        mavenLocal()
        // 官方私有扩展库；仅在提供了凭据时注册，否则交由末尾的离线兜底仓库
        gprCredentials?.let { creds ->
            maven("https://maven.pkg.github.com/ReChronoRain/HyperCeiler") {
                credentials {
                    username = creds.user
                    password = creds.key
                }
            }
        }
        maven("https://jitpack.io")
        maven("https://api.xposed.info")
        // 私有产物不可用时的本地兜底（源码见 local-maven-src/expansion-compat）
        maven(rootDir.resolve("local-maven"))
    }
}

rootProject.name = "HyperCeiler"

include(
    "app",
    // ":library:hook",
    ":library:libhook",
    // ":library:xposed-api-101",
    ":library:core",
    ":library:provision",
    ":library:common",
    ":library:processor",
    ":library:hidden-api",
)
