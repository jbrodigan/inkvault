pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "InkVault"
include(":app")
include(":pencore")   // pen-abstraction boundary; the app depends on this, never on the SDK directly

// :neosdk = the quarantined NeoLAB SDK driver (the only code that imports kr.neolab.sdk). It is
// built ONLY when you provide the (patched) SDK — drop the SDK jar/aar into neosdk/libs/, or wire
// the SDK source module per neosdk/README.md. Until then CI builds :app + :pencore with the fake.
val neosdkLibs = file("neosdk/libs")
if (neosdkLibs.isDirectory &&
    neosdkLibs.listFiles()?.any { it.extension == "jar" || it.extension == "aar" } == true
) {
    include(":neosdk")
}

// :penspike imports the real GPL Neo SDK, so include it only when the SDK is present:
// drop the SDK jar/aar into penspike/libs/ (or wire the source module — see penspike/build.gradle.kts).
// This keeps the default CI build (which builds :app) green without the SDK.
val penspikeLibs = file("penspike/libs")
if (penspikeLibs.isDirectory &&
    penspikeLibs.listFiles()?.any { it.extension == "jar" || it.extension == "aar" } == true
) {
    include(":penspike")
}
