import java.io.FileInputStream
import java.io.File as _File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization") version "2.2.21"
}

// 统一签名策略：复用主应用唯一官方密钥（app/minime.jks + keystore.properties，CI 从 Secrets 恢复）。
// P1-04：配置期不读密钥内容、不硬失败（懒加载）；缺密钥/指纹不符的 fail-closed 判断
// 推迟到文件底部 gradle.taskGraph.whenReady 守卫。各字段可由 keystore.properties 或环境变量提供。
val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun ksProp(key: String, env: String): String? {
    val fromFile = keystoreProperties.getProperty(key)
    if (!fromFile.isNullOrBlank()) return fromFile.trim()
    val fromEnv = System.getenv(env)
    return fromEnv?.trim()?.takeIf { it.isNotEmpty() }
}

val releaseStoreFileName: String = ksProp("storeFile", "KEYSTORE_STORE_FILE") ?: "minime.jks"
val releaseJksFile: _File = rootProject.file("app/$releaseStoreFileName")
val releaseStorePassword: String? = ksProp("storePassword", "KEYSTORE_STORE_PASSWORD")
val releaseKeyAlias: String? = ksProp("keyAlias", "KEYSTORE_KEY_ALIAS") ?: "minime"
val releaseKeyPassword: String? = ksProp("keyPassword", "KEYSTORE_KEY_PASSWORD")
val releaseExpectedCertSha256: String? = ksProp("expectedCertSha256", "EXPECTED_CERT_SHA256")

android {
    namespace = "com.minime.template"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.minime.template"
        minSdk = 26
        targetSdk = 34
        // 版本号四段格式（与主应用规范一致）：0.0.0.10
        // versionCode = 10_000_000 + A*1_000_000_000 + B*10_000_000 + C*10_000 + D*10
        versionCode = 10_000_100
        versionName = "0.0.0.10"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // 统一签名策略：复用主应用唯一官方密钥（app/minime.jks + keystore.properties）。
    // 懒加载：配置期占位、不失败、不回退 debug；fail-closed 由文件底部 taskGraph.whenReady 守卫执行。
    signingConfigs {
        create("release") {
            storeFile = releaseJksFile
            if (releaseStorePassword != null) storePassword = releaseStorePassword
            if (releaseKeyAlias != null) keyAlias = releaseKeyAlias
            if (releaseKeyPassword != null) keyPassword = releaseKeyPassword
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

// ── P1-04 任务图守卫：release 产出 fail-closed ─────────────────────────────
gradle.taskGraph.whenReady {
    val hasReleaseArtifactTask = allTasks.any { t ->
        val n = t.name
        (n.contains("Release") || n.contains("Bundle")) && !n.contains("Debug")
    }
    if (!hasReleaseArtifactTask) return@whenReady

    fun failRelease(msg: String): Nothing = throw GradleException(
        "[P1-04 release 签名守卫] $msg\n" +
            "  → CI 需从 GitHub Secrets 恢复 app/keystore.properties（含 storePassword/keyAlias/keyPassword/expectedCertSha256）" +
            "与 app/minime.jks；本地出 release 包须从安全渠道获取官方密钥。"
    )

    if (!keystorePropertiesFile.exists()) {
        failRelease("缺少 app/keystore.properties。")
    }
    if (!releaseJksFile.exists()) {
        failRelease("缺少签名密钥文件 ${releaseJksFile.path}（app/minime.jks）。")
    }
    val storePw = releaseStorePassword
        ?: failRelease("缺少 storePassword（keystore.properties 或 KEYSTORE_STORE_PASSWORD）。")
    val keyAliasV = releaseKeyAlias
        ?: failRelease("缺少 keyAlias（keystore.properties 或 KEYSTORE_KEY_ALIAS）。")
    if (releaseKeyPassword == null) {
        failRelease("缺少 keyPassword（keystore.properties 或 KEYSTORE_KEY_PASSWORD）。")
    }
    val expectedRaw = releaseExpectedCertSha256
        ?: failRelease("缺少官方证书 SHA-256 白名单 expectedCertSha256（keystore.properties 或 EXPECTED_CERT_SHA256）。")

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

    if (!MessageDigest.isEqual(actualSha256.toByteArray(), expectedNorm.toByteArray())) {
        failRelease("签名证书 SHA-256 与官方白名单不匹配。\n    实际: $actualSha256\n    预期: $expectedNorm")
    }
}
