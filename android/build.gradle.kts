// Top-level build file. Plugin versions are declared here with `apply false`
// and applied in the module build scripts. Versions verified mutually-compatible
// against source (2026-06): KSP only goes to Kotlin 2.3.x, so Kotlin is pinned to
// 2.2.21 (the newest with a confirmed KSP pairing) rather than the absolute latest.
// AGP 9.2.0 (needs Gradle 9.4.1, JDK 17, compileSdk 37) — upgraded to allow Material 3 Expressive.
plugins {
    id("com.android.application") version "9.2.0" apply false
    id("com.android.library") version "9.2.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
}
