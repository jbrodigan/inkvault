#!/usr/bin/env fish
# Modernize NeoLAB's Android SDK 2.0 sample so it builds on a current toolchain.
#
# Device/shell: the OCR host (fish shell).
#
# What it fixes (grounded in the real repo, read 2026-06-21):
#   The sample is already mostly current — AGP 8.10.0, Gradle wrapper 8.11.1, namespace set,
#   compileSdk 33, and it consumes the SDK as the source module :NASDK (../NASDK2.0_Studio/app).
#   The one real breakage is dead **jcenter()** (shut down in 2021): with it listed, dependency
#   resolution fails. This swaps jcenter() -> mavenCentral() everywhere. That's the whole fix.
#
# Usage:  ./modernize-neo-sample.fish [path-to-clone]   (default: ./Android-SDK2.0)

set repo $argv[1]
test -z "$repo"; and set repo "Android-SDK2.0"

if not test -d "$repo"
    echo ">> cloning NeoLAB SDK (full repo — the sample references ../NASDK2.0_Studio/app)…"
    git clone https://github.com/NeoSmartpen/Android-SDK2.0.git "$repo"; or exit 1
end

# JDK check — AGP 8.10 needs JDK 17+.
set jver (javac -version 2>&1 | string match -r '\d+' | head -n1)
if test -z "$jver"; or test "$jver" -lt 17
    echo "!! JDK 17+ required (AGP 8.10). Point JAVA_HOME at a JDK 17 and re-run."
end

echo ">> replacing dead jcenter() with mavenCentral() in all Gradle files…"
for f in (find "$repo" -name 'build.gradle' -o -name 'build.gradle.kts' -o -name 'settings.gradle')
    if grep -q 'jcenter()' "$f"
        sed -i 's/jcenter()/mavenCentral()/g' "$f"
        echo "   patched $f"
    end
end

chmod +x "$repo/NASDK2.0_sample_code/gradlew" 2>/dev/null

echo ""
echo ">> done. Build the sample:"
echo "   cd $repo/NASDK2.0_sample_code"
echo "   ./gradlew :NASDK_sample_code:assembleDebug"
echo ""
echo ">> then install + grant BLE perms + run (see android/docs/PHASE0_GO_NOGO.md):"
echo "   adb install -r NASDK_sample_code/build/outputs/apk/debug/*-debug.apk"
echo "   set PKG (adb shell pm list packages | grep -i neolab | sed 's/package://')"
echo "   adb shell pm grant \$PKG android.permission.BLUETOOTH_SCAN"
echo "   adb shell pm grant \$PKG android.permission.BLUETOOTH_CONNECT"
echo ""
echo "   (Optional: if compileSdk 33 warns on an Android tablet, bump compileSdk/targetSdk to 34"
echo "    in NASDK_sample_code/build.gradle — not required to install/run.)"
