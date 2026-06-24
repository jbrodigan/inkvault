# :penspike — real Phase 0 wrapper spike

A standalone app that connects to one Neo/LAMY pen through the **real** `kr.neolab.sdk` and logs
live dots — the secondary instrument for the LAMY GO/NO-GO (the primary is NeoLAB's own sample;
see `../docs/PHASE0_GO_NOGO.md`). All SDK calls use the verified API surface in `../DESIGN.md`.

It is **excluded from the default build/CI** (which builds `:app`) and is only included once the
SDK is present — so the GPL SDK never has to live in this repo for CI to pass.

## Enable + build + run

```fish
# OCR host (fish shell)
# 1) Provide the SDK. Easiest: build the SDK module's AAR from the cloned repo and copy it here.
mkdir -p android/penspike/libs
cp /path/to/Android-SDK2.0/NASDK2.0_Studio/app/build/outputs/aar/*.aar android/penspike/libs/
#    (or drop any built kr.neolab.sdk .jar/.aar into android/penspike/libs/)
#    Alternatively wire the SDK source module — see build.gradle.kts option (b).

# 2) Build + install + run (settings.gradle.kts now auto-includes :penspike because libs has an aar)
cd android
./gradlew :penspike:installDebug
adb shell am start -n com.inkvault.penspike/.PenSpikeActivity
adb logcat -s SPIKE
```

The app requests `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` at launch, scans for a pen (name containing
neo/pen/lamy/nwp/dimo), connects, and logs each `MSG`/`DOT` line on screen and to logcat.

## Reading the result

- `MSG type=0x05 … AUTHORIZED` then `DOT …` lines with moving `x/y` and non-zero `note/page` → **GO**.
- Fails at scan/connect (no `0x02`) → pairing/BLE issue. `0x03 connect FAILED` → handshake. Dots
  all-zero/garbage → dot-format issue. Record which, per `../docs/PHASE0_GO_NOGO.md`.

Capture the logcat (`adb logcat -s SPIKE > lamy_session.txt`) — it doubles as a replay fixture.
