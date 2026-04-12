plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.meshsos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.meshsos"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Keep NCNN model assets uncompressed for mmap loading
    androidResources {
        noCompress += listOf("ncnn.bin", "ncnn.param")
    }
}

dependencies {
    // Force consistent Kotlin stdlib across all transitive deps
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.2.20"))

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    // Coroutines
    implementation(libs.coroutines.android)

    // Google
    implementation(libs.nearby)
    implementation(libs.location)

    // Nordic BLE
    implementation(libs.nordic.ble)

    // DataStore
    implementation(libs.datastore.prefs)

    // WorkManager
    implementation(libs.work.runtime)

    // Gson
    implementation(libs.gson)

    // Permissions helper
    implementation(libs.accompanist.permissions)

    // CameraX (live preview + image analysis for detection)
    implementation(libs.camerax.core)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.camerax.camera2)
}

// ── Auto-download NCNN pre-built SDK before native build ─────────────────────
val downloadNcnnSdk by tasks.registering {
    val sdkDir = file("src/main/cpp/ncnn-sdk")
    outputs.dir(sdkDir)
    doLast {
        if (!sdkDir.exists()) {
            val ver = "20240820"
            val url = "https://github.com/Tencent/ncnn/releases/download/$ver/ncnn-$ver-android.zip"
            val zip = file("src/main/cpp/ncnn-sdk.zip")
            logger.lifecycle("Downloading NCNN Android SDK v$ver …")
            ant.invokeMethod("get", mapOf("src" to url, "dest" to zip))
            logger.lifecycle("Extracting …")
            copy { from(zipTree(zip)); into(file("src/main/cpp")) }
            file("src/main/cpp/ncnn-$ver-android").renameTo(sdkDir)
            zip.delete()
            logger.lifecycle("NCNN SDK ready at $sdkDir")
        }
    }
}
tasks.matching { it.name.contains("externalNativeBuild", ignoreCase = true) }.configureEach {
    dependsOn(downloadNcnnSdk)
}
