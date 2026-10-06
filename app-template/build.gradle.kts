plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization") version "2.2.21"
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

    // 复用主应用官方签名密钥（app/minime.jks）
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("app/minime.jks")
            storePassword = "9d4d4f44c1eaf5bcd57c68c5c4dab893"
            keyAlias = "minime"
            keyPassword = "9d4d4f44c1eaf5bcd57c68c5c4dab893"
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
