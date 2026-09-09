plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.quzhi.lite"
    compileSdk = 36

    signingConfigs {
        create("release") {
            storeFile = file(providers.gradleProperty("quzhiReleaseStoreFile").get())
            storePassword = providers.gradleProperty("quzhiReleaseStorePassword").get()
            keyAlias = providers.gradleProperty("quzhiReleaseKeyAlias").get()
            keyPassword = providers.gradleProperty("quzhiReleaseKeyPassword").get()
        }
    }

    defaultConfig {
        applicationId = "com.quzhi.lite"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "0.4.0"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
    }
}

@Suppress("DEPRECATION")
android.applicationVariants.all {
    if (buildType.name == "release") {
        outputs.all {
            (this as com.android.build.gradle.api.ApkVariantOutput).outputFileName =
                "quzhi-lite-${android.defaultConfig.versionName}.apk"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")

    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
