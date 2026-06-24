import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Standalone Phase 0 spike app. It imports the real kr.neolab.sdk, so it is built ONLY when the
// SDK is present (see android/settings.gradle.kts — :penspike is included only if penspike/libs
// has a jar/aar, or you wire the SDK source module below). This keeps the main CI build (which
// builds :app) green without the GPL SDK.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.inkvault.penspike"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.inkvault.penspike"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")

    // --- Neo SDK (pick ONE) ---
    // (a) Drop a built SDK jar/aar into penspike/libs/  (this picks it up automatically):
    implementation(fileTree("libs") { include("*.jar", "*.aar") })
    // (b) OR consume the SDK source module. In settings.gradle.kts add, next to include(":penspike"):
    //       include(":NASDK")
    //       project(":NASDK").projectDir = file("/abs/path/to/Android-SDK2.0/NASDK2.0_Studio/app")
    //     then here:  implementation(project(":NASDK"))
}
