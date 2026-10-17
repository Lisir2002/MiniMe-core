import java.io.FileInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")

    kotlin("plugin.compose")
    kotlin("plugin.serialization") version "2.4.21"
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("app.cash.sqldelight")
    id("io.gitlab.arturbosch.detekt")
    // JaCoCo：单元测试覆盖率采集（报告任务见文件末尾 jacocoTestReport）。
    id("jacoco")
    // OWASP Dependency-Check：依赖 CVE 扫描（版本在根 build.gradle.kts 以 apply false 声明）。
    // 任务仅在 .github/workflows/dependency-check.yml（每周/手动）执行，不进主 CI 门禁，
    // 避免 NVD 数据下载超时影响日常构建。
    id("org.owasp.dependencycheck")
}

// 品牌 / 产物参数单一事实源（应用名、APK产物前缀、keystore 文件名、图标族名），定义见 branding.gradle.kts。
// apply(from=…) 场景下 cross-script 顶层 const 不能直接 import，故经 extra 读取。
apply(from = file("branding.gradle.kts"))
val brandAppName: String = extra["brandAppName"] as String
val brandIconFamily: String = extra["brandIconFamily"] as String

// OWASP Dependency-Check 配置（12.x 官方 Kotlin DSL 用法）。
// - failBuildOnCVSS=7.0：仅 CVSS ≥ 7.0（高危/严重）失败，低分只报告，避免噪音阻断；
// - 输出 HTML + JSON，由 dependency-check.yml 上传为 CI artifact；
// - NVD API key 经仓库 Secret（NVD_API_KEY）的 env 注入，无 key 时限流 30 次/滚动窗口，
//   已配 delay/maxRetryCount 兜底（见 workflow）。
dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JSON")
    nvd {
        apiKey = System.getenv("NVD_API_KEY")
        delay = 4000
        maxRetryCount = 3
    }
}

// 从本地 keystore.properties 读取 release 签名密钥（已 gitignore，不入库）。
// CI 构建前从 GitHub Secrets 恢复为 app/minime.jks + app/keystore.properties；
// 本地开发需自行创建该文件（从安全渠道获取密钥）。
//
// P1-04 签名口令治理（懒加载 + 任务图 fail-closed）：
//   - 配置期（sync / help / compileDebugKotlin / test）绝不因 keystore.properties 缺失而失败——
//     文件不存在时 Properties 为空，下面的 release signingConfig 仅占位、不读真实口令。
//   - 缺密钥绝不静默回退 debug keystore 当正式签名；真正的"缺密钥/指纹不符即失败"判断
//     推迟到文件底部 gradle.taskGraph.whenReady 守卫：仅当任务图含产出正式包的任务时才校验。
//   - 各字段可由 keystore.properties 提供，也可由环境变量覆盖（CI 本地调试同样适用）：
//     storeFile←KEYSTORE_STORE_FILE，storePassword←KEYSTORE_STORE_PASSWORD，
//     keyAlias←KEYSTORE_KEY_ALIAS，keyPassword←KEYSTORE_KEY_PASSWORD，
//     expectedCertSha256←EXPECTED_CERT_SHA256。
val keystorePropertiesFile = file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// 解析单个签名字段：优先 keystore.properties，其次环境变量；都没有返回 null。
// 配置期只解析、不抛错（抛错一律留给 taskGraph.whenReady 守卫）。
fun ksProp(key: String, env: String): String? {
    val fromFile = keystoreProperties.getProperty(key)
    if (!fromFile.isNullOrBlank()) return fromFile.trim()
    val fromEnv = System.getenv(env)
    return fromEnv?.trim()?.takeIf { it.isNotEmpty() }
}

// release 签名字段（懒解析结果；storeFile 缺省指向 app/minime.jks）。
val releaseStoreFilePath: String = ksProp("storeFile", "KEYSTORE_STORE_FILE") ?: "minime.jks"
val releaseJksFile: java.io.File = file(releaseStoreFilePath)
val releaseStorePassword: String? = ksProp("storePassword", "KEYSTORE_STORE_PASSWORD")
val releaseKeyAlias: String? = ksProp("keyAlias", "KEYSTORE_KEY_ALIAS") ?: "minime"
val releaseKeyPassword: String? = ksProp("keyPassword", "KEYSTORE_KEY_PASSWORD")
// 官方签名证书 SHA-256 白名单（小写 hex，可带冒号/空格；缺失则 release 任务 fail-closed）。
val releaseExpectedCertSha256: String? = ksProp("expectedCertSha256", "EXPECTED_CERT_SHA256")

// 版本号策略（四段 x.x.x.x，最高 999.999.999.999，从 0.0.0.1 起迭代）：
//   - 正式版 / RC：完全由 git tag 驱动（v0.0.0.1 / v0.0.0.1-rcN / v0.0.0.10 / v0.0.1.0），
//     提示词内不定死；tag 语义（bug 修复→rc 迭代、功能→0.0.0.10 跨度、重构→0.0.1.0 跨度且第4位归零）
//     只在打 tag 时由维护者按规则执行，构建侧只要能正确解析任意四段 tag 即可。
//   - dev 基线：没有可用的四段 tag 时（旧三段 tag / 无 tag / 无 git 环境）回退到 "0.0.1-dev..."，
//     "0.0.0.1" 是由 tag 打出来的正式号，不作为代码内常量写死。
val BASE_VERSION = "0.0.1"

// 版本号解析：解析 git describe 产物。
//   匹配四段 tag：正式 "0.0.0.1"、"0.0.0.1-rcN"；以及 tag 后的日常提交
//   "0.0.0.1-rcN-<n>-g<hash>[dirty]" → "0.0.0.1-rcN-dev.<n>+<hash>[dirty]"。
//   其余（旧三段 tag 如 1.x / 0.6.0-rc 等、无 v 前缀、无 git）一律 fallback，防版本号回跳。
//   【必须 --match "v[0-9]*"】git describe 默认不区分 tag 来源、只取「最近任意 tag」，
//   仓库内其它应用（logviewer-v*）的 tag 一旦成为最近 tag，本函数正则即失配、
//   版本号被静默降级为 dev 值（versionCode 同时从 tag 公式退化为提交数公式，破坏单调性）。
//   加 --match 把候选限定为主应用自己的 v<数字>* tag，隔离其它应用的 tag 命名空间。
fun gitVersionName(): String = try {
    val process = Runtime.getRuntime().exec(
        arrayOf("git", "describe", "--tags", "--always", "--dirty", "--match", "v[0-9]*"),
        null,
        rootProject.projectDir
    )
    process.waitFor()
    val raw = process.inputStream.bufferedReader().readText().trim()
    val version = raw.removePrefix("v")
    if (version.isNotEmpty()) {
        val fourSegDev = Regex("""^(\d+\.\d+\.\d+\.\d+(?:-[a-zA-Z0-9]+)?)-(\d+)-g([0-9a-f]+)(.*)$""")
        val match = fourSegDev.matchEntire(version)
        if (match != null) {
            val (base, count, hash, dirty) = match.destructured
            "$base-dev.$count+$hash$dirty"
        } else if (Regex("""^\d+\.\d+\.\d+\.\d+(-rc\d+)?$""").matches(version)) {
            version
        } else {
            "$BASE_VERSION-dev"
        }
    } else {
        "$BASE_VERSION-dev"
    }
} catch (e: Exception) {
    "$BASE_VERSION-dev"
}

// versionCode 从 git tag 四段版本号映射生成（语义见 AGENTS.md「版本号规范」），随版本单调递增：
//   versionCode = BASE + A*1_000_000_000 + B*10_000_000 + C*10_000 + D*10
//   - A/B 段当前预留为 0，实际迭代在 C/D 段；C 段间距 10_000 > D 段最大映射 999*10=9990，
//     保证「C+1 且 D 归零」时仍严格单调；rcN 与同版本正式号映射相同，RC 转正允许相等覆盖升级。
// 历史教训：早期用 git rev-list --count HEAD（提交数）作 versionCode，一旦 rebase/squash 改写历史，
// 提交数回退会导致新版本 versionCode < 旧版、Android 升级判定失效。现改为 tag 驱动，与提交历史
// 解耦——历史可随时压缩/重写，versionCode 依然单调。
// 无 tag（dev 构建 / 旧三段 tag / 无 git 环境）时回退 BASE + 提交数（dev 包不发布，仅保证可覆盖安装）。
// 与 gitVersionName 同样必须 --match "v[0-9]*"（原因见上），否则其它应用的 tag 会劫持这里的解析。
// CI 额外校验 versionCode 单调（见 .github/workflows/android-release.yml），映射规则与本函数一致。
fun gitVersionCode(): Int = try {
    val describeProcess = Runtime.getRuntime().exec(
        arrayOf("git", "describe", "--tags", "--always", "--dirty", "--match", "v[0-9]*"),
        null,
        rootProject.projectDir
    )
    describeProcess.waitFor()
    val raw = describeProcess.inputStream.bufferedReader().readText().trim().removePrefix("v")
    val tagMatch = Regex("""^(\d+)\.(\d+)\.(\d+)\.(\d+)(?:-rc\d+)?$""").matchEntire(raw)
    if (tagMatch != null) {
        val (a, b, c, d) = tagMatch.destructured
        (10_000_000L + a.toLong() * 1_000_000_000L + b.toLong() * 10_000_000L +
            c.toLong() * 10_000L + d.toLong() * 10L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    } else {
        val countProcess = Runtime.getRuntime().exec(
            arrayOf("git", "rev-list", "--count", "HEAD"),
            null,
            rootProject.projectDir
        )
        countProcess.waitFor()
        10_000_000 + (countProcess.inputStream.bufferedReader().readText().trim().toIntOrNull() ?: 0)
    }
} catch (e: Exception) {
    10_000_000
}

android {
    namespace = "com.mini.me_core"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    signingConfigs {
        // 签名策略（唯一官方密钥，见 branding.gradle.kts「签名策略」）：
        //   release = 唯一官方 keystore（app/minime.jks + keystore.properties，**不入库**，
        //     CI 从 GitHub Secrets 恢复，本地构建需维护者自备）。
        //   每次构建都使用这一把密钥，缺失即报错，**禁止静默回退 debug keystore 当正式签名**。
        //   debug = 固定 debug keystore（仅用于开发期调试安装，data 与 release 隔离）。
        val customDebugKeystore = file("/root/Android/Sdk/debug.keystore")
        val defaultDebugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
        val fallbackDebugKeystore = when {
            customDebugKeystore.exists() -> customDebugKeystore
            defaultDebugKeystore.exists() -> defaultDebugKeystore
            else -> defaultDebugKeystore  // 两者都不存在：指向默认路径，AGP 会自动创建
        }

        create("androidDebug") {
            storeFile = fallbackDebugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            enableV1Signing = true
            enableV2Signing = true
        }

        create("release") {
            // 唯一官方密钥（app/minime.jks + keystore.properties，CI 从 GitHub Secrets 恢复）。
            // 懒加载：仅当字段已解析到时才填充；缺失时留空占位——
            //   - 配置期（sync/help/compileDebug/test）不抛错；
            //   - 绝不回退 debug keystore 当正式签名；
            //   - 真正的 fail-closed 由文件底部 gradle.taskGraph.whenReady 守卫执行
            //     （缺密钥文件 / 缺字段 / 缺证书白名单 / 指纹不符 → 立即 throw）。
            storeFile = releaseJksFile
            if (releaseStorePassword != null) storePassword = releaseStorePassword
            if (releaseKeyAlias != null) keyAlias = releaseKeyAlias
            if (releaseKeyPassword != null) keyPassword = releaseKeyPassword
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    defaultConfig {
        applicationId = "com.mini.me_core"
        minSdk = 26
        // 锁定 targetSdk 28：Android 10+（API 29+）的 W^X/SELinux 策略禁止执行 App 可写
        // 数据目录里的文件，PRoot 二进制将无法运行（同 Termux 的取舍）。代价：不能上 Google Play。
        targetSdk = 28
        versionCode = gitVersionCode()
        versionName = gitVersionName()

        // 品牌参数注入（单一事实源：branding.gradle.kts）：
        //   - 应用显示名：resValue 覆盖 res/values/strings.xml 的 app_name（构建期生效，配置改动即全局改名）；
        //     英文翻译仍由 values-en/strings.xml 提供（本品牌名中英一致）。
        //   - launcher 图标资源族名：manifestPlaceholder 注入 android:icon（见 AndroidManifest.xml）。
        resValue("string", "app_name", brandAppName)
        manifestPlaceholders["brandIcon"] = brandIconFamily

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 仅保留 arm64-v8a：所有变体（debug/release）都只打包 arm64 native 库。
        //   - 真机 arm64 原生运行；
        //   - 模拟器用 arm64 系统镜像（Android Studio AVD 已默认支持 arm64 镜像）；
        //   - 容器内仍支持双架构：arm64 rootfs 原生执行，x86_64 rootfs 经 QEMU 转译运行
        //     （见 assets/container/ 双架构资产与 ContainerInstaller 运行时选择逻辑）。
        // 历史教训：曾在 defaultConfig 配双 ABI、release 块用 abiFilters.clear() 覆盖，
        //   但 AGP 8.x 的 defaultConfig/buildType NdkOptions 是 union 合并而非替换，
        //   clear() 只清空 buildType 自身空集合，defaultConfig 的 x86_64 仍被合并进来，
        //   导致 release APK 始终双 ABI（72MB）。根因修复：defaultConfig 直接只配 arm64-v8a。
        ndk { abiFilters += listOf("arm64-v8a") }

        // ── Native viewer/editor 核心（libminimeviewer）──
    }

    // 容器资产：sourceSets.main.assets 挂 _armAssets，其内同时含 container/arm（arm64 容器 rootfs + proot）
    // 与 container/x86_64（x86_64 rootfs + arm64 宿主 qemu 转译器 + x86_64 宿主原生 proot），
    // 一并打进 APK。注意：这是容器内的用户态二进制资产，不是 App 自身的 native so——
    // App native 层（libminimeviewer/libsqlcipher/libtermux 等）仅 arm64-v8a（见 defaultConfig.ndk.abiFilters）。
    // 运行时由 EnvironmentDetector 按宿主架构选装/选用对应 rootfs 与 proot（见 ContainerInstaller）。
    sourceSets {
        getByName("main") {
            assets.srcDir("src/_armAssets")
        }
    }

    buildTypes {
        // debug 加包名后缀 .debug → applicationId 变 com.mini.me_core.debug，与 release（com.mini.me_core）
        // 可同机共存、互不覆盖。IDE 跑 debug 不再因签名不同卸载已装的正式版。
        // 注意：因 applicationId 不同，debug 变体私有目录为 /data/data/com.mini.me_core.debug/，
        // release 已解压的容器 rootfs 与工作区项目在 debug 下不可见（需重新解压/clone），属预期隔离行为。
        debug {
            applicationIdSuffix = ".debug"
            // signingConfigs.androidDebug 已经在顶层保证永远有值（优先 custom → 默认 debug keystore，
            // 都不存在则指向默认路径让 AGP 自动建），这里直接绑定即可，不用再做 exists 判断。
            signingConfig = signingConfigs.getByName("androidDebug")

            // 统一让 debug APK 也做 DEX ZIP DEFLATE 压缩：
            //   开发期分发 debug 给他人时体积稳定在 ~35 MB，而非 AGP 默认 STORE 导致的 90 MB 膨胀。
            //   代价：模拟器首次安装需解压 DEX → oat 目录，慢约 2~5 秒；
            //   注：release 构建按用户策略是"所有测试/发布都用发行版"，这里仅给 debug 做体积收敛的友好默认。
            packaging {
                dex { useLegacyPackaging = false }
                jniLibs { useLegacyPackaging = false }
                // BouncyCastle 1.78.1 起 bcpkix/bcprov/bcutil 与 jspecify 的 jar 均含
                // META-INF/versions/9/OSGI-INF/MANIFEST.MF（多发布 jar 的 OSGi 元数据），
                // 合并 java 资源时路径重复冲突；该文件是 OSGi 捆绑元数据，Android 端不使用，排除。
                resources {
                    excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
                }
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            // signingConfigs.release 懒加载绑定：
            //   - CI/本地有密钥 → 正式 release 签名；
            //   - 无密钥 → signingConfig 占位但由文件底部 taskGraph.whenReady 守卫在执行前 throw，
            //     绝不会用 debug keystore 签出可安装的正式 APK。
            signingConfig = signingConfigs.getByName("release")

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // release 构建的体积/性能深度优化：
            //   debugSymbolLevel=none  —— 不向 APK / AAB 注入 native 调试符号表，省 ~1MB+。
            //   isPseudoLocalesEnabled=false —— 关闭伪本地化资源，省少量体积。
            //   ABI 过滤统一在 defaultConfig.ndk.abiFilters 配置（仅 arm64-v8a），
            //     不在 release 块重复设置——AGP 8.x defaultConfig/buildType NdkOptions 是 union
            //     合并关系，buildType 块的 abiFilters.clear() 无法清除 defaultConfig 的值。
            ndk {
                debugSymbolLevel = "none"
            }
            isPseudoLocalesEnabled = false

            // 利用 R8 / D8 的 advancedMode 与 useLegacyPackaging=false：
            //   useLegacyPackaging=false 让 APK 内的 .dex / .so 保持压缩状态被直接加载
            //   （需要 APK Signature Scheme v2，AGP 默认开启），省掉安装期解压副本占用的空间。
            packaging {
                jniLibs { useLegacyPackaging = false }
                dex { useLegacyPackaging = false }
                // 同 debug：排除 BC 1.78.1 / jspecify 多发布 jar 的 OSGi 元数据冲突路径。
                resources {
                    excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // sora-editor language-textmate 在 API < 33 需要 coreLibrary desugaring
        // （tm4e 内部使用 java.util.regex 新 API；Joni 正则也依赖 desugared stdlib）。
        isCoreLibraryDesugaringEnabled = true
    }

    // RC92：MigrationSchemaConsistencyTest 失败详情 println 到 stdout，
    // 默认 Gradle 吞掉测试 stdout/stderr，CI 日志看不到具体不一致项。
    // 开启 showStandardStreams 让失败详情直接出现在 CI 控制台日志。
    testOptions {
        // 让 android.util.Log 等桩方法返回默认值而非抛「not mocked」，便于被测代码间接调用日志不崩。
        unitTests.isReturnDefaultValues = true
        // 注意：不要开启 unitTests.isIncludeAndroidResources —— Robolectric 会因此加载合并
        // 清单并初始化真实 Application（Hilt + 数据库 + 密钥链），该链路在 JVM 测试环境
        // 必然失败（DEK 无法解密）。Robolectric 测试按需用 @Config(application=...) 覆盖。
        unitTests.all {
            it.testLogging {
                showStandardStreams = true
                showExceptions = true
                showStackTraces = true
                showCauses = true
            }
        }
    }

    @Suppress("DEPRECATION")
    kotlinOptions {
        jvmTarget = "17"
        // 全局 opt-in：Compose / Material3 实验性 API（FilterChip/ElevatedAssistChip/TooltipBox 等）
        // —— CI 的 -Werror 会把 experimental warning 当成 error，这里一次性白名单。
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
    }

    buildFeatures {
        compose = true
        // RC67a 新增：Android Gradle Plugin 8+ 默认不生成 BuildConfig.java（仅 buildConfig=true 时才生成）。
        // P0-2 assertContinuity 需要 BuildConfig.DEBUG 来区分 Debug/Release：
        //   Debug 构建发现 SCHEMA_GAP 直接抛异常 → 开发者/CI 立刻看见坏版本；
        //   Release 构建只写日志 → 保持启动安全语义（永不阻断启动）。
        buildConfig = true
    }



    // 全局打包选项：统一排除重复 META-INF 通知；
    // 所有变体（debug/release）都强制 dex/jniLibs 走 ZIP DEFLATE 压缩（useLegacyPackaging=false）。
    //
    // 背景：AGP 8.9.x 对 debug 变体的 packagingOptions 有隐藏的"debuggable → 强制 STORE"兜底逻辑，
    // 单独在 buildTypes.debug.packaging 里设置 dex.useLegacyPackaging=false 不一定能生效，
    // 表现就是 debug APK 的 DEX 全是 STORE（未压缩）→ 90MB 级。
    // 所以把全局默认放到 android.packaging 顶层（所有变体先继承这份值），
    // 再配合 buildTypes.release 的显式设置（release 本来就会压缩），
    // 最终让 debug APK 的 DEX 也稳定回到 DEFLATE（~35MB 级别，与旧 armsolo 一致）。
    packaging {
        dex { useLegacyPackaging = false }
        jniLibs { useLegacyPackaging = false }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/INDEX.LIST"
            excludes += "/sshj.properties"
            excludes += "/kotlin-tooling-metadata.json"
            excludes += "/DebugProbesKt.bin"
        }
    }

    // targetSdk 故意锁定 28（PRoot 需在 app 可写目录执行二进制，Android 10+ W^X 禁止），
    // 代价是不进 Google Play——故关闭该平台的过期 targetSdk 检查。
    // 同时关闭 release 构建的 lint 检查：本仓库只出 GitHub Release 不上 Play，
    // lintVital 在 R8/打包阶段额外吃 CPU 与内存（2 核 7GB runner 易 OOM），且其发现不阻塞发布。
    lint {
        disable += "ExpiredTargetSdkVersion"
        checkReleaseBuilds = false
        // baseline 渐进式：现有 229 个 error 全部录入 app/lint-baseline.xml 视为「已知存量」。
        // CI 显式跑 :app:lintRelease 时，baseline 内的既有 issue 被抑制，任何「新增 error」直接 fail。
        // checkReleaseBuilds=false 仍保证 assembleRelease 不自动跑 lint（避免 2 核 runner OOM），
        // lintVital 仍在下方禁用；lint 门禁只通过 CI 显式步骤触发。
        baseline = file("lint-baseline.xml")
        // baseline 已生成（收录现有 229 个 error）。开启 abortOnError：CI 显式跑 :app:lintRelease 时，
        // baseline 内既有 issue 被抑制，任何「新增 error」直接 fail。warning 不触发 abort。
        abortOnError = true
    }
}

// 彻底禁用 lintVital 任务（现为 debug/release 各一个），
// 使其不进入 assembleRelease 的任务图——比 lint.checkReleaseBuilds=false 更省构建开销与内存。
// 仅在 release 任务图执行前禁用，避免影响开发期 debug lint。
gradle.projectsEvaluated {
    tasks.matching { it.name.startsWith("lintVital") }.configureEach { enabled = false }
}

// ── 数据保全：applicationId 白名单硬校验 ──────────────────────────────
// 包名（applicationId）变更在 Android 眼里是"全新安装"，私有数据目录随之隔离，
// 历史对话会全部"消失"（历史上已因此多次数据丢失，见 AGENTS.md 包名稳定性条款）。
// 这里在配置期对每个 variant 做白名单校验：包名不在白名单内 → 构建直接失败，
// 与单测 ApplicationIdStabilityTest（release classpath 断言）、CI 发版门禁（Tag 间一致性）
// 构成三重防线，杜绝 rebrand 误改包名再次造成用户数据丢失。
androidComponents {
    onVariants(selector().all()) { variant ->
        val id: String = variant.applicationId.get()
        require(id in ALLOWED_APPLICATION_IDS) {
            "applicationId=$id 不在白名单 $ALLOWED_APPLICATION_IDS 内。禁止变更包名——" +
                "包名变更在 Android 上是全新安装，会导致用户历史对话不可见（详见 AGENTS.md 包名稳定性条款）。" +
                "如需 rebrand 请只改应用名/图标/namespace，勿改 applicationId。"
        }
    }
}

/** 允许的 applicationId 白名单：release=com.mini.me_core，debug 带 .debug 后缀（与 release 数据隔离）。 */
val ALLOWED_APPLICATION_IDS = setOf("com.mini.me_core", "com.mini.me_core.debug")

// ── P1-04 任务图守卫：release 产出 fail-closed ─────────────────────────────
// 配置期不读密钥（懒加载），但一旦任务图里出现"产出正式包"的任务
// （任务名含 Release 或 Bundle 且不含 Debug，如 assembleRelease/bundleRelease/packageRelease），
// 立即在任务执行前校验官方密钥与证书指纹；任一条件不满足即 throw GradleException，
// 阻止正式包被 debug keystore 或非官方密钥签出。debug/help/compileDebug/test 任务图不含此类任务，不受影响。
gradle.taskGraph.whenReady {
    // 仅当任务图含「:app 模块产出正式包」的任务时才校验签名：
    // :app:assembleRelease / :app:bundleRelease。其他模块的中间任务、
    // 纯编译（compileReleaseKotlin）、lint、test 均不触发。
    val hasReleaseArtifactTask = allTasks.any { t ->
        val p = t.path
        p == ":app:assembleRelease" || p == ":app:bundleRelease"
    }
    if (!hasReleaseArtifactTask) return@whenReady

    fun failRelease(msg: String): Nothing = throw GradleException(
        "[P1-04 release 签名守卫] $msg\n" +
            "  → 正式 release 必须使用唯一官方密钥。CI 需从 GitHub Secrets 恢复 " +
            "app/keystore.properties（含 storePassword/keyAlias/keyPassword/expectedCertSha256）" +
            "与 app/minime.jks；本地开发如需出 release 包，须从安全渠道获取官方密钥放入 app/。"
    )

    if (!keystorePropertiesFile.exists()) {
        failRelease("缺少 app/keystore.properties（storePassword/keyAlias/keyPassword/expectedCertSha256）。")
    }
    if (!releaseJksFile.exists()) {
        failRelease("缺少签名密钥文件 ${releaseJksFile.path}（app/minime.jks）。")
    }
    val storePw = releaseStorePassword
        ?: failRelease("缺少 storePassword（keystore.properties 或环境变量 KEYSTORE_STORE_PASSWORD）。")
    val keyAliasV = releaseKeyAlias
        ?: failRelease("缺少 keyAlias（keystore.properties 或环境变量 KEYSTORE_KEY_ALIAS）。")
    if (releaseKeyPassword == null) {
        failRelease("缺少 keyPassword（keystore.properties 或环境变量 KEYSTORE_KEY_PASSWORD）。")
    }
    val expectedRaw = releaseExpectedCertSha256
        ?: failRelease("缺少官方证书 SHA-256 白名单 expectedCertSha256（keystore.properties 或环境变量 EXPECTED_CERT_SHA256）。")

    // 从官方 keystore 取出别名证书并计算 SHA-256（先后兼容 PKCS12 / JKS 两种容器类型）。
    val certEncoded = run {
        val errors = mutableListOf<String>()
        var encoded: ByteArray? = null
        for (type in listOf("PKCS12", "JKS")) {
            try {
                val ks = KeyStore.getInstance(type)
                FileInputStream(releaseJksFile).use { ks.load(it, storePw.toCharArray()) }
                val cert = ks.getCertificate(keyAliasV)
                    ?: error("别名 $keyAliasV 不存在于 $type")
                encoded = cert.encoded
                break
            } catch (e: Exception) {
                errors += "$type: ${e.message}"
            }
        }
        encoded ?: failRelease("无法从 $releaseJksFile 读取别名 $keyAliasV 的证书（${errors.joinToString("; ")}）。")
    }
    val actualSha256 = MessageDigest.getInstance("SHA-256").digest(certEncoded)
        .joinToString("") { "%02x".format(it) }
    val expectedNorm = expectedRaw.replace(":", "").replace(" ", "").trim().lowercase()

    // 常量时间比较，防时序侧信道；缺失白名单值/比对不一致均 fail-closed（上方已保证两者非空）。
    if (!MessageDigest.isEqual(actualSha256.toByteArray(), expectedNorm.toByteArray())) {
        failRelease("签名证书 SHA-256 与官方白名单不匹配。\n    实际: $actualSha256\n    预期: $expectedNorm")
    }
}

dependencies {
    // custom_lint 框架：加载 :lint-checks 模块注册的自定义规则，:app:lintRelease 时自动执行。
    // 该依赖只在 lint 任务 classpath 生效，不打进 APK、不影响运行时。
    lintChecks(project(":lint-checks"))

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.animation)

    // Lifecycle + ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt 依赖注入
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // 网络请求
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // HTML 解析与清洗 (用于 WebFetchTool)
    implementation(libs.jsoup)

    // 协程
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Kotlin 序列化
    implementation(libs.kotlinx.serialization.json)

    // 内置 MCP 服务器（Streamable HTTP）：Ktor CIO 起 HTTP 监听 + SSE，供外部 MCP 客户端连入。
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)

    // YAML 解析 (用于 Skill Frontmatter)
    implementation(libs.snakeyaml)

    // 远程同步 (SFTP/FTP) 与内置 FTP 服务端
    implementation(libs.sshj)
    implementation(libs.bouncycastle)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.commons.net)
    implementation(libs.ftpserver.core)
    implementation(libs.slf4j.simple)

    // 容器：解压 Alpine rootfs tar.gz
    implementation(libs.commons.compress)
    implementation(libs.xz)

    // Termux 开源终端组件
    implementation(project(":terminal-emulator"))
    implementation(project(":terminal-view"))

    // Material Icons
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    // Markdown Renderer
    implementation(libs.multiplatform.markdown.renderer.m3)
    implementation(libs.multiplatform.markdown.renderer.code)
    implementation(libs.highlights.jvm)

    // sora-editor + TextMate 高亮引擎（替代 tree-sitter）
    // LGPL-2.1：在「开源许可」页注明 sora-editor。
    implementation(platform(libs.sora.editor.bom))
    implementation(libs.sora.editor)
    implementation(libs.sora.language.textmate)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Core Android
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.webkit)

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    // mockk：mock final class（如 ZthTierRepository），纯 JVM 单测即用
    testImplementation("io.mockk:mockk:1.14.11")
    implementation(libs.json)

    // SQLDelight
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines.extensions)
    testImplementation("app.cash.sqldelight:sqlite-driver:2.4.1")
    implementation(libs.androidx.sqlite.framework)

    // 数据库加密
    implementation(libs.sqlcipher)
    // Jetpack Security：EncryptedSharedPreferences（统一密钥管理存储DEK）
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // 内存泄漏检测
    debugImplementation(libs.leakcanary.android)
}

// ── 新数据层（data-layer-redesign）─ SQLDelight 6 库拓扑（设计文档 §4 / §12）──
// 核心 5 域各自独立 Database 类（独立版本链）+ infra 一个 Database 类承载全部 Store。
// 生成规则（SQLDelight 2.x）：Database 类在公共包 com.mini.mecore.datalayer.sqldelight；
// 查询类/数据类在 packageName + 相对 srcDir 的目录路径（agent/ → .sqldelight.agent 等）。
// 文件布局：src/main/sqldelight/<域>/<域>/<文件>.sq（.sq 必须位于 srcDir 的子目录「包目录」）。
// dialect = sqlite 3.38：schema 编译期校验按 3.38；运行期由设备 SQLite 提供（语句均兼容 3.18+，
// 已避免 UPSERT 等 3.24+ 语法以兼容 targetSdk=28 / Android 9 的 SQLite 3.22）。
sqldelight {
    databases {
        create("AgentDb") {
            packageName.set("com.mini.mecore.datalayer.sqldelight")
            srcDirs("src/main/sqldelight/agent")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.4.1")
        }
        create("CredentialsDb") {
            packageName.set("com.mini.mecore.datalayer.sqldelight")
            srcDirs("src/main/sqldelight/credentials")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.4.1")
        }
        create("SettingsDb") {
            packageName.set("com.mini.mecore.datalayer.sqldelight")
            srcDirs("src/main/sqldelight/settings")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.4.1")
        }
        create("WorkspaceDb") {
            packageName.set("com.mini.mecore.datalayer.sqldelight")
            srcDirs("src/main/sqldelight/workspace")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.4.1")
        }
        create("T2iDb") {
            packageName.set("com.mini.mecore.datalayer.sqldelight")
            srcDirs("src/main/sqldelight/t2i")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.4.1")
        }
        create("InfraDb") {
            packageName.set("com.mini.mecore.datalayer.sqldelight")
            srcDirs("src/main/sqldelight/infra")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.4.1")
        }
    }
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    source.setFrom(files("src/main/java"))
    parallel = true
    // 存量问题记账在 baseline（来自 main 基线），门禁只拦截本次改动引入的新问题。
    baseline = file("$rootDir/config/detekt/baseline.xml")
    // 报告产物供 CI 归档（xml 便于机器解析，html 便于人看）。
    reports {
        xml.required.set(true)
        html.required.set(true)
        sarif.required.set(false)
        md.required.set(false)
    }
}

jacoco {
    toolVersion = "0.8.12"
}

/**
 * 覆盖率报告（release 变体纯 JVM 单测）。
 *
 * 只统计 app 自身源码：排除 R/BuildConfig/Hilt 生成类、SQLDelight 生成代码与 Compose 编译产物，
 * 否则覆盖率数字会被生成代码稀释到无参考价值。
 *
 * 当前 CI 只做「报告 + 归档」，不设阻断阈值——基线覆盖率尚未建立，硬阈值会一上来就把
 * 所有合并堵死。等指标稳定后，可仿照本任务新增一个 JacocoCoverageVerification 任务
 * （在 violationRules 里配 minimum，再挂到 CI 的 check 上）收紧为硬门禁。
 */
tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates JaCoCo coverage report for release unit tests."
    dependsOn("testReleaseUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val kotlinClasses = layout.buildDirectory.dir("tmp/kotlin-classes/release").get().asFile
    sourceDirectories.setFrom(files("$projectDir/src/main/java", "$projectDir/src/main/kotlin"))
    classDirectories.setFrom(
        fileTree(kotlinClasses) {
            exclude(
                "**/R.class", "**/R\$*.class", "**/BuildConfig*", "**/Manifest*",
                "**/*_Factory.class", "**/*_Provide*Factory.class", "**/*HiltModules*.class",
                "**/*_MembersInjector.class", "**/*_Impl.class",
                "**/com/mini/mecore/datalayer/**", // SQLDelight 生成代码
                "**/*ComposableSingletons*", "**/*PreviewKt*",
            )
        }
    )
    executionData.setFrom(
        fileTree(layout.buildDirectory.get().asFile) {
            include("jacoco/testReleaseUnitTest.exec", "outputs/unit_test_code_coverage/**/*.exec")
        }
    )
}
