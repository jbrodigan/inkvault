# `tools/` — host-side scripts & runbooks

Nothing here is part of the Android build. These run on your machines (the OCR host (fish shell), TrueNAS,
etc.) to deploy the OCR pipeline, calibrate notebooks, and feed the test harness.

## Scripts

| File | What it does | Run |
|---|---|---|
| `calibrate_ncode.py` | Ncode calibration. Three modes: **scale fit** from ruler traces; **`--page-extent`** → paste-ready `PageGeometry`/`NotebookType` from corner/edge traces; **`--planner-layout`** → paste-ready `PlannerLayout` from dated-page samples. Stdlib + openpyxl. | `python3 calibrate_ncode.py --selftest`<br>`… measurements.xlsx`<br>`… --page-extent measurements.xlsx 13.75 21 id "Name" code`<br>`… --planner-layout 100:2026:6 130:2026:7` |
| `ocr_watcher.py` | NAS-side OCR watcher: polls the export folder (recursively, into the type/label sub-folders), transcribes each page PNG via an OpenAI-compatible vision endpoint, writes `<base>.txt` beside it. `--once` for cron; `OCR_MAX_FAILS` caps retries; prints a per-sweep tally. Stdlib only. | `WATCH_DIR=/mnt/tank/inkvault OCR_URL=http://ocr-host.lan:11434 ./ocr_watcher.py`<br>`… ./ocr_watcher.py --once` |
| `logcat_to_replay.py` | Converts an `adb logcat -s SPIKE` capture into a `.jsonl` replay fixture (`app/src/test/resources/replay/`) so a real pen session drives the automated pipeline tests. | `adb logcat -s SPIKE \| python3 logcat_to_replay.py > session.jsonl` |
| `setup-ocr-host.fish` | One-shot OCR host setup for the Ollama + Qwen3-VL OCR backend. | `./setup-ocr-host.fish` |

## Configs & runbooks

| File | Purpose |
|---|---|
| `qwen3-vl-ocr.Modelfile` | Ollama model definition for the OCR vision model. |
| `ollama-inkvault.conf` | Ollama service config for the OCR box. |
| `ocr-watcher.compose.yaml` | Compose unit to run `ocr_watcher.py` as a service on the NAS. |
| `OCR_DEPLOY.md` | Full deploy plan for the split OCR pipeline (OCR host GPU + NAS watcher). |
| `SYNCTHING_SETUP.md` | Syncthing folder wiring between the device, NAS, and OCR box. |
| `TRANSLATION_SETUP.md` | Setting up the translation LLM endpoint (quality path; ML Kit is the offline fallback). |
| `MCP_QUERY.md` | Notes on the MCP query surface. |
| `neo-sample-modernization/` | Notes/patches for modernizing the NeoLAB SDK sample. |

## Adding a new notebook type

See [`../NOTEBOOK_SETUP.md`](../NOTEBOOK_SETUP.md) — measure 8 traces, run `calibrate_ncode.py
--page-extent`, paste into `NotebookType.kt`.
