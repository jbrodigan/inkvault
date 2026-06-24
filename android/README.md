# Neo / LAMY Smartpen Capture App (Android)

**Local-first** Android app that replaces Neo Studio 2 for capture, storage, and export. Data is
on-device first, then synced to **targets you explicitly select** — a local SAF folder (watched by
Syncthing/Nextcloud/etc.), a NAS over Tailscale, **or a cloud provider you pick** (Drive, Dropbox,
OneDrive, WebDAV, S3) — via a runtime "Sync method" dropdown. Transcription is handed to an
external OCR you run. **One hard rule: no tracking/telemetry/ads, and no outbound network except
to the targets you select.**

**Read [`DESIGN.md`](DESIGN.md) first** — it's the authoritative doc (verified SDK surface,
config keys, phased plan, LAMY GO/NO-GO).

## Where we are: Phase 0 (de-risk) — not past the gate

Per the brief, Phase 0 must finish and be confirmed before Phase 1:
study the SDK ✅, document its API ✅ (DESIGN.md), modernize the build, run the **dual-pen
connection spike**, and report **GO/NO-GO on the LAMY NWP-F80**.

> **The GO/NO-GO is PENDING — it needs real hardware.** A physical pen can't be connected in a
> CI/sandbox environment, so the spike must run on real devices.

## Phase 0 spike — run on each device

> Dev host: **the OCR host (fish shell)** (`adb` runs from here)

```fish
# Build & install the debug APK (after adding the kr.neolab.sdk module — see below)
# The Gradle wrapper is committed, so ./gradlew just works (downloads Gradle 8.14.3 on first run).
cd android
./gradlew :app:installDebug

# Launch the spike and watch the live dot stream:
adb shell am start -n com.inkvault/.spike.PenConnectSpikeActivity
adb logcat -s SPIKE
```

Run it twice — once with the **M1+** on an Android 14+ phone, once with the **LAMY safari** on
an Android tablet. Confirm: pairs → handshakes (`PEN_AUTHORIZED`) → streams dots. If the LAMY fails,
note exactly where (pairing / handshake / dot format) and record it in `DESIGN.md`.

The spike file (`app/src/main/java/com/inkvault/spike/PenConnectSpikeActivity.kt`) contains the
**verified** real-`PenCtrl` sequence to swap in for hardware; out of the box it runs a fake path
so it builds without the SDK.

## Adding the Neo SDK module

The GPL-3.0 `kr.neolab.sdk` library is **not vendored** here. To go live:
1. Add it as a module (`:neosdk`) or `app/libs/<jar>`, and `implementation(...)` it in
   `app/build.gradle.kts` (placeholder line is there).
2. In `di/ServiceLocator.kt`, swap `FakeNeoPenSdk()` → `NeoSdkAdapter(applicationContext)` and
   uncomment the verified translation in `pen/NeoSdkAdapter.kt`.

## Configuration

No endpoints are hardcoded. Copy `app/src/main/assets/config.example.json` → `config.json` and
fill in your NAS / OCR values. Keys are documented in `DESIGN.md`.

## What exists vs. what's deferred

- **Aligned foundation (Phase 1 preview, built before the brief — to be reviewed):** Room as
  source of truth (`data/`), persist-first live-stroke capture + crash recovery (`ingest/`),
  Ncode auto-organization (`organize/` — **needs finished-notebook locking, see DESIGN.md**),
  BLE reconnect logic (`pen/` — **to become a foreground service in Phase 1**), Compose UI.
- **Removed earlier:** the old NeoLAB-style cloud-backup/account code. Sync is now a runtime
  dropdown over a pluggable `StorageProvider` (local + user-selected cloud) in Phase 3; OCR-trigger
  dropdown in Phase 4. The only network is to targets you pick — never a NeoLAB account or telemetry.

## Tests (JVM, no device)

```fish
./gradlew :app:testDebugUnitTest
```
`StrokeIngestorTest` (no lost strokes + crash recovery) and `AutoOrganizerTest` (Ncode
auto-filing). Phase 2 will add **sync-completeness tests that prove no page loss** on offline
retrieval.

## Honest status

Not yet compiled in a full Android SDK environment — treat the build as
un-smoke-tested until you run it on the OCR host. The SDK API surface in `DESIGN.md` was read from
source, not memory.
