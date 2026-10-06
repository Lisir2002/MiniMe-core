plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization") version "2.2.21"
}

// 从主应用 app/keystore.properties 读取 release 签名密钥（统一签名策略，CI 从 Secrets 恢复）
val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties = java.util.Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(java.io.FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.minime.template"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.minime.template"
        minSdk = 26
        targetSdk = 34
        // 版本号四段格式（与主应用规范一致）：0.0.0.1
        // versionCode = 10_000_000 + A*1_000_000_000 + B*10_000_000 + C*10_000 + D*10
        versionCode = 10_000_010
        versionName = "0.0.0.1"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // 统一签名策略：复用主应用唯一官方密钥（app/minime.jks + keystore.properties）
    signingConfigs {
        create("release") {
            require(keystorePropertiesFile.exists()) {
                "release 正式签名密钥缺失：缺少 app/keystore.properties（唯一官方密钥，CI 从 Secrets 恢复）。"
            }
            storeFile = rootProject.file("app/" + (keystoreProperties["storeFile"] as String))
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
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
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
