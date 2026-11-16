plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.aaronmaxlab.maxplayer"
    compileSdk {
        version = release(37)
    }

    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        applicationId = "com.aaronmaxlab.maxplayer"
        minSdk = 24
        targetSdk = 37
        versionCode = 8026
        versionName = "3.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters += setOf(
                "armeabi-v7a",
                "arm64-v8a",
                "x86",
                "x86_64"
            )
        }
    }

    packaging { jniLibs { useLegacyPackaging = true } }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.documentfile)
    implementation(libs.recyclerview)
    implementation(libs.flexbox)
    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.ui.compose.material3)
    implementation(libs.media3.session)
    implementation(libs.glide)
    implementation(libs.zxing)
    implementation(libs.java.websocket)
    implementation(libs.zxing.embedded)
    implementation(libs.ads.sdk)
    implementation(libs.splash.screen)
    implementation(files("libs/mpvengine-release.aar"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)


}