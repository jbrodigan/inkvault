import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.inkvault"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.inkvault"
        // The Neo SDK supports KitKat, but modern BLE scanning + coroutines/WorkManager
        // are far more reliable from API 24 up. Raise the floor deliberately.
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Bring-up only: inject the (locked) pen's password at build time, e.g.
        //   ./gradlew :app:installDebug -PpenPassword=XXXX
        // Never committed, empty in CI. Upgrade path: in-app secure password entry (Phase 1 finish).
        buildConfigField("String", "PEN_PASSWORD", "\"${project.findProperty("penPassword") ?: ""}\"")
        // Bring-up only: the pen's BLE MAC to auto-connect to on launch (until a scan/pair screen
        // exists — Phase 1). e.g. -PpenMac=AA:BB:CC:DD:EE:FF . Empty → no auto-connect.
        buildConfigField("String", "PEN_MAC", "\"${project.findProperty("penMac") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // The pen-abstraction boundary. The app talks to NeoPenSdk; the NeoLAB SDK never appears here.
    implementation(project(":pencore"))

    // Real pen driver — linked ONLY when the patched SDK AAR has been built into neosdk/libs
    // (android/neosdk/patch-and-build-sdk.fish). CI/dev build without it, against the fake. The
    // app still never imports kr.neolab.sdk; ServiceLocator looks the adapter up reflectively.
    if (rootProject.file("neosdk/libs").listFiles()?.any { it.extension == "aar" || it.extension == "jar" } == true) {
        implementation(project(":neosdk"))
    }

    val composeBom = platform("androidx.compose:compose-bom:2026.05.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    // Material 3 Expressive (spring MotionScheme + new components) is on the 1.5.0-alpha track until
    // it graduates to stable later in 2026; pin it over the BOM. The toolchain (AGP 9.2 / Gradle 9.4
    // / compileSdk 37) was upgraded specifically to support it. Note: drop the explicit version
    // and go back to BOM-managed `material3` once 1.5.0 ships stable.
    implementation("androidx.compose.material3:material3:1.5.0-alpha22")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Persistence — the source of truth. Room 2.8.x is KSP2-ready.
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Durable background sync.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Phase 3 export: persisted settings + SAF folder writes.
    // 1.1.7 is the last 1.1.x with a confirmed 16 KB-aligned libdatastore_shared_counter.so.
    // Note: do NOT jump to 1.2.0 — it regressed the .so back to 4 KB alignment
    // (issuetracker 357653528 / flutter#182898); re-verify with check_elf_alignment.sh before any 1.2.x.
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Print a page via the system print dialog (PrintHelper renders a bitmap).
    implementation("androidx.print:print:1.0.0")

    // On-device handwriting OCR straight from captured strokes (offline after a one-time model
    // download; no GPU/NAS). ML Kit Digital Ink consumes our X/Y/time pen data — see OnDeviceInk.
    // STAY ON 18.1.0. Empirically (CI run on commit 5843169, a real 2m39s build) 19.0.0 *resolves*
    // as a dependency but its `com.google.mlkit.vision.digitalink` package is gone — 29 unresolved-
    // reference compile errors, all in OnDeviceInk.kt (DigitalInkRecognition, Ink, WritingArea, …).
    // So the deep-research "bump to 19.0.0 for the 16 KB fix" lead is REFUTED by the build: 18.1.0
    // is the last artifact that actually carries the digitalink API we compile against. The
    // libdigitalink.so 16 KB-alignment liability (mlkit#938) therefore stays Google-side; we
    // fail-soft (runCatching in OnDeviceInk) so OCR degrades gracefully rather than crashing.
    implementation("com.google.mlkit:digital-ink-recognition:18.1.0")

    // Translation: on-device language detection (source auto-detect) + the offline fallback engine.
    // The high-quality path is a translation LLM on the user's GPU box (HTTP); see Translator.
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.google.mlkit:translate:17.0.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // The real Neo SDK jar would be dropped here, e.g.:
    // implementation(files("libs/neosmartpen-sdk-2.1.10.jar"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("androidx.room:room-testing:2.8.4")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("com.google.truth:truth:1.4.4")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
