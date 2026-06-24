# InkVault — competitive research & improvement roadmap

Research date 2026-06-22. We surveyed existing CLIs/APIs/MCPs/Python libs/open-source apps across
every feature area of InkVault, then distilled the wins. **Headline: the research validates most of
our architecture** and points to a small set of high-leverage upgrades.

## What the research confirms we already got right
- **Ink rendering** — our `strokeOutline` (Freehand.kt) is the perfect-freehand algorithm (MIT,
  steveruizok). There is **no Kotlin port** of perfect-freehand, so hand-rolling the math was the
  correct call.
- **OCR serving** — for single-user, one-page-at-a-time OCR on a 16 GB GPU, **Ollama is the right
  engine** (vLLM/SGLang only pull ahead for bulk/parallel batches). Our baked `num_ctx` Modelfile is
  exactly the documented fix for the context-length gotcha (the `/v1` endpoint can't set it
  per-request). *(Minor: Ollama's default at 16 GB is now ~4096, not 2048 — still far too small for a
  page image, so baking 32768 stands.)*
- **Exporting InkML** (stroke order + pressure + time) is our biggest latent advantage — online HTR
  beats image-only OCR.
- **Vault model** — exporting an ink file + a queryable transcript beside it is the established idiom
  (Obsidian Excalidraw plugin, Petrify). Theme-aware ink (Saber) and page templates (Xournal++) are
  validated UX choices we already made.

## High-leverage upgrades (prioritized)

### Tier 1 — biggest functionality win
1. **On-device handwriting OCR via Google ML Kit Digital Ink Recognition — ✅ SHIPPED.**
   On-device, free, 300+ languages, ~100 ms/line, purpose-built for **stroke** input — it consumes
   exactly our captured X/Y/time. Gives **instant, offline transcription with no GPU and no NAS**,
   complementing the OCR host/Ollama pipeline (which stays the heavy/best-quality path). One-time model
   download from Google, then fully local. License: proprietary free SDK, Android-only — fine, we're
   an Android app. Implemented as `ocr/OnDeviceInk` + a page-detail **"Transcribe on device"** action
   that stores the transcript through the existing transcript→search→`.md`-refresh plumbing.
   *Upgrade path: a language picker (currently en-US).*
   https://developers.google.com/ml-kit/vision/digital-ink-recognition

1b. **"Transcribe to printed font" — ✅ SHIPPED.** A non-destructive Ink/Text toggle in page detail
   renders the OCR transcript in a clean, selectable printed font (the model leading apps use — Nebo —
   not fragile in-place ink replacement).

1c. **Translator (extremely good, not Google-Translate slop) — ✅ SHIPPED.** Tiered, quality-first:
   primary = a translation LLM (EuroLLM-9B / Tower+ 9B) on the user's OCR host over the tailnet —
   one model, every language, no per-language downloads, beats commercial NMT in WMT24 human eval;
   fallback = ML Kit on-device (labelled "offline, basic"). Source auto-detects on-device (ML Kit
   Language ID); default target = device language. UI = "From"/"To" pickers in the Text view; setup
   in `tools/TRANSLATION_SETUP.md`. (WMT24: arXiv 2402... see that doc's sources.)

2. **Make the exported vault AI-queryable via MCP** — done as docs (`tools/MCP_QUERY.md`): point the
   official Filesystem MCP (MIT) or a Markdown-RAG MCP (semantic search over noisy OCR) at the export
   folder. Zero app code. Optional future: a thin InkVault MCP exposing `get_stroke_data` / `reocr_page`.

### Tier 2 — accuracy / efficiency
3. **Feed strokes-as-text to the VLM** (watcher `OCR_SEND_STROKES=1`, shipped opt-in). Google's
   arXiv 2402.15307 shows image + stroke-coordinate text matches/beats dedicated online HTR in a VLM.
   Experimental per-model; A/B on your hand. https://arxiv.org/abs/2402.15307
4. **Alternate OCR models to A/B** against qwen3-vl, all Apache-2.0 / open weights:
   - **GLM-OCR** (0.9B) — tiny, low VRAM (~2–4 GB), claims handwriting + structured Markdown out.
     Best size/quality/license tradeoff. https://github.com/zai-org/GLM-OCR
   - **olmOCR-2** (Qwen2.5-VL-7B) — 82.4 on olmOCR-Bench, trained incl. handwriting; runs on
     vLLM/SGLang. https://github.com/allenai/olmocr
   - **Qwen2.5-VL-7B** — more battle-tested than Qwen3-VL on vLLM base64 today; **Nanonets-OCR2-3B**
     (Qwen2.5-VL-3B fine-tune on handwritten forms) is a lighter option.
   - Caution: **TrOCR** handwritten checkpoints and **Nougat** weights are non-commercial (IAM /
     CC-BY-NC); **docTR/Calamari/Granite-Docling/Nougat** are print-focused — weak on cursive.

### Tier 3 — look/feel & live-capture latency
5. **Jetpack `androidx.ink` (official Ink API).** Brushes + **low-latency front-buffered authoring**
   + stroke **geometry for hit-testing** (better lasso/erase) + compact serialization, with
   `ink-stroke-modeler` smoothing/prediction inside. Could replace our hand-rolled outline renderer
   and sharpen lasso selection. Caveat: **alpha** (1.1.0-alpha04) — pin a version, expect churn.
   https://developer.android.com/jetpack/androidx/releases/ink
   - Cheap independent win: **motion prediction** (`androidx.input:input-motionprediction`) to mask
     stylus latency, renderer-agnostic.
6. **Compose canvas perf** — if/when lag appears: `Modifier.drawWithCache` to cache committed-stroke
   `Path` geometry, a separate layer for the in-progress stroke, `graphicsLayer` for pan/zoom, and
   keep stroke-state reads in the draw phase. (Not built now — no measured lag; perfect-freehand
   outlines are O(points) and cheap. Listed as the on-demand optimization.)
7. **Material 3 Expressive — ✅ SHIPPED (theme-level).** `MaterialExpressiveTheme` brings the
   Expressive spring `MotionScheme` app-wide while we keep brand color/shape/type. It needs
   `material3:1.5.0-alpha22` → `compose:1.12.0-alpha03`, which requires **AGP 9.1+ / compileSdk 37**
   — so we did the **toolchain upgrade**: AGP 8.13→9.2.0, Gradle 8.14.3→9.4.1, compileSdk 36→37
   (Android 17 went stable June 2026), with `android.builtInKotlin=false` to keep our plugin setup.
   *Future: adopt the Expressive components (`FloatingToolbar` tool palette, `ButtonGroup`) too; drop
   the explicit `material3` version and the builtInKotlin opt-out once 1.5.0 / built-in Kotlin are the
   stable default (the opt-out is removed in AGP 10).*

### Reference implementations to mine (don't reinvent)
- **Wacom `universal-ink-library`** (Apache-2.0) — robust InkML I/O + IAM-OnDB/CROHME dataset
  mapping; useful for any NAS-side stroke processing or an online-HTR step.
  https://github.com/Wacom-Developer/universal-ink-library
- **NeoLAB iOS-SDK3.0 / WEB-SDK2.0** (GPLv3) — the *freshest* references for the BLE wire protocol
  and offline `.data` page semantics (better than the 2017 Android SDK) when we implement offline
  pull. **cheind/py-microdots** (MIT) — Anoto dot-decode primitive if we ever do camera capture.
- **PellelNitram/OnlineHTR** + **Xournal++ HTR** — open online-HTR you could self-host/control;
  arXiv 2506.20255 is a fusion (image+stroke) architecture matching our exact data.

## Sources
ML Kit Digital Ink: https://developers.google.com/ml-kit/vision/digital-ink-recognition ·
arXiv 2402.15307 (online ink in VLMs) · arXiv 2506.20255 (fusion HTR) ·
GLM-OCR https://github.com/zai-org/GLM-OCR · olmOCR https://github.com/allenai/olmocr ·
Qwen3-VL https://huggingface.co/Qwen/Qwen3-VL-8B-Instruct ·
androidx.ink https://developer.android.com/jetpack/androidx/releases/ink ·
ink-stroke-modeler https://github.com/google/ink-stroke-modeler ·
perfect-freehand https://github.com/steveruizok/perfect-freehand ·
compose-material3 https://developer.android.com/jetpack/androidx/releases/compose-material3 ·
universal-ink-library https://github.com/Wacom-Developer/universal-ink-library ·
NeoSmartpen SDKs https://github.com/NeoSmartpen · py-microdots https://github.com/cheind/py-microdots ·
OnlineHTR https://github.com/PellelNitram/OnlineHTR ·
Filesystem MCP https://github.com/modelcontextprotocol/servers/tree/main/src/filesystem ·
markdown-vault-mcp https://github.com/pvliesdonk/markdown-vault-mcp
