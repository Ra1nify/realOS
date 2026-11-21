// Thin wrapper module around DexKit.
// The submodule lives at libs/dexkit/DexKit (upstream https://github.com/LuckyPray/DexKit).
// The upstream dexkit-android module is not included (it pulls dokka/publish/signing plugins);
// instead this references its source dir and native build script, keeping only the android library build.
// Upstream dexkit-android/build.gradle points sourceSets.java at the :dexkit module's
// dexkit/src/main/java, and CMakeLists at dexkit-android/CMakeLists.txt
// (add_subdirectory(../Core Core) + native-bridge.cpp). The same wiring is copied here.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.luckypray.dexkit"
    // Upstream uses compileSdk=34; keep it to avoid pulling in unrelated new SDK APIs.
    compileSdk = 37
    compileSdkMinor = 1
    // Upstream asks for NDK 26.1 while this machine has 30.0.16248370. The prefab cxx package does not
    // depend on the NDK version, so the local one is used to avoid a download. Install the official one if CMake complains.
    ndkVersion = "30.0.16248370"

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Build arm64-v8a only: every target device is arm64, and each extra ABI is a full CMake build.
            // DexKit native is the main source of build time.
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                // Do not use upstream -DANDROID_STL=none (that pairs with rikka cxx); use the NDK default STL.
                arguments("-DDEXKIT_ENABLE_INTERNAL_METRICS=OFF")
                arguments("-DDEXKIT_ENABLE_INTERNAL_METRICS_API=OFF")
                arguments("-DCMAKE_BUILD_TYPE=Release")
                cppFlags += listOf("-std=c++20", "-Os", "-ffunction-sections", "-fdata-sections", "-fvisibility=hidden", "-fvisibility-inlines-hidden", "-Wno-unused-value", "-Wno-unused-variable")
                cFlags += listOf("-std=c18", "-Os", "-ffunction-sections", "-fdata-sections", "-fvisibility=hidden")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("DexKit/dexkit-android/src/main/AndroidManifest.xml")
            java.directories.add("DexKit/dexkit/src/main/java")
            // AGP 9 has built-in Kotlin support, so the kotlin source dir must be declared explicitly; with
            // java.directories only, the Kotlin task is NO-SOURCE and classes like DexKitBridge never reach classes.jar.
            kotlin.directories.add("DexKit/dexkit/src/main/java")
        }
    }

    externalNativeBuild {
        cmake {
            // Wrapper-owned CMakeLists (bypasses prefab, see the file header).
            path = file("CMakeLists.txt")
        }
    }
}

dependencies {
    implementation("com.google.flatbuffers:flatbuffers-java:23.5.26")
}
