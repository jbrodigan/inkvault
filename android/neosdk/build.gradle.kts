import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The ONLY module that imports kr.neolab.sdk. It adapts the (patched) NeoLAB SDK to our clean
// `NeoPenSdk` boundary in :pencore. Built only when the SDK is present (settings.gradle.kts gates
// it on neosdk/libs containing a jar/aar), so the GPL SDK never has to live in this repo for CI.
// See README.md for how to drop in your patched SDK, and android/STRANGLER.md for the plan to
// progressively replace the SDK's internals behind this same boundary.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.inkvault.neosdk"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":pencore"))

    // The patched NeoLAB SDK. Pick ONE:
    // (a) drop a built jar/aar into neosdk/libs/ (auto-detected):
    implementation(fileTree("libs") { include("*.jar", "*.aar") })
    // (b) OR wire the SDK source module — add to settings.gradle.kts:
    //       include(":NASDK"); project(":NASDK").projectDir = file("/path/Android-SDK2.0/NASDK2.0_Studio/app")
    //     then:  implementation(project(":NASDK"))
}
