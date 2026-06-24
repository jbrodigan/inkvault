# Modernize NeoLAB's SDK sample (so the GO/NO-GO sample builds)

NeoLAB's `NASDK2.0_sample_code` is the authoritative instrument for the LAMY GO/NO-GO
(`../../docs/PHASE0_GO_NOGO.md`). Good news: it's **already mostly current** — verified from the
live repo on 2026-06-21:

| Thing | State in the repo |
|-------|-------------------|
| Android Gradle Plugin | **8.10.0** (modern) |
| Gradle wrapper | **8.11.1** |
| `namespace` | set (`kr.neolab.samplecode`) |
| compileSdk / minSdk / targetSdk | 33 / 21 / 33 |
| SDK dependency | source module `:NASDK` → `../NASDK2.0_Studio/app` |
| **Repositories** | **`jcenter()`** ← the one real problem |

`jcenter()` was shut down in 2021, so builds fail resolving dependencies while it's listed.
**The fix is just `jcenter()` → `mavenCentral()`.** That's what `modernize-neo-sample.fish` does
(idempotent; only touches files that contain `jcenter()`).

## Run it

```fish
# the OCR host (fish shell)
cd android/tools/neo-sample-modernization
./modernize-neo-sample.fish ~/code/Android-SDK2.0   # clones the repo there if missing
```

Then follow the printed build/install/grant/run commands (also in
`../../docs/PHASE0_GO_NOGO.md`). Because the sample pulls the SDK from `../NASDK2.0_Studio/app`,
keep the **whole** repo clone intact — don't copy just the sample folder.

## Honesty

I can't run an Android build in this environment (its network blocks Google's Android hosts), so
this is grounded in the repo's real files but **not executed here**. The `jcenter()` removal is
the definite blocker; if the build then trips on something else (e.g. a build-tools or compileSdk
nag on an Android tablet), send me the error and I'll patch it — likely a one-line `compileSdk 34`
bump. Requires JDK 17+ (AGP 8.10).
