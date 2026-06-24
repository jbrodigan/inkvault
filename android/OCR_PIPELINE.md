# InkVault OCR / sync pipeline — full plan (OCR host + TrueNAS + tailnet)

Tailored to the real stack discovered on 2026-06-22. This is the staging → implementation →
execution → maintenance → optimization plan, with the **friction points + fixes** found by research
and the **components we'd have to write** (flagged "PROPOSAL — needs approval").

## The stack (as measured)

| Node | Address | Role | Notes |
|---|---|---|---|
| OCR host | tailnet `<OCR_TAILNET_IP>`, LAN `192.168.X.X` | **OCR/GPU** | a CUDA GPU (≥16 GB VRAM), Ollama running, `qwen3-vl` pulled, docker+python, fish |
| TrueNAS SCALE | tailnet `<NAS_TAILNET_IP>`, LAN `192.168.X.X` | **storage + watcher + Syncthing** | docker 29, python 3.13, apps run as the NAS app's uid:gid, Tailscale is per-app **sidecars** (no host CLI) |
| Android tablet | tailnet `<TAILNET_IP>` | capture (LAMY) | OneUI — aggressive battery killer |
| Android 14+ phone | tailnet `<TAILNET_IP>` | capture (M1+) | OneUI |

Flow: **pen → app → export (PNG+InkML+SVG+JSON) → Syncthing → NAS dataset → watcher → OCR host Ollama → `page.txt` → back to app for search.**

> **Deploying Stage 1 + 3?** The copy-paste runbook and ready-to-use files are in
> [`tools/OCR_DEPLOY.md`](tools/OCR_DEPLOY.md): `setup-ocr-host.fish`, `ollama-inkvault.conf`,
> `qwen3-vl-ocr.Modelfile`, `ocr-watcher.compose.yaml`. The snippets below are the rationale.

---

## ⚠️ Critical findings (these bite silently)

1. **Ollama's default context is 2048 tokens → garbled/looping OCR on images.** A page image blows
   past 2048 and Qwen3-VL emits repeated/garbage text. **Fix: bake `num_ctx 32768` into a custom
   model** (the OpenAI endpoint can't set `num_ctx` per request, so a Modelfile is the only reliable
   way). [ollama#13084]
2. **`"think": false` is silently ignored on `qwen3-vl`** — the VL template lacks the thinking toggle,
   so you can't disable reasoning via the API. **Fix: use the Instruct (non-thinking) variant AND
   strip `<think>…</think>` in the watcher** as belt-and-suspenders. [ollama#14798]
3. **OneUI will kill Syncthing in the background** unless it's battery-exempted — and a kill mid-write
   can corrupt Syncthing's DB. **Fix: Syncthing-Fork + "Unrestricted" battery + disable "put app to
   sleep" + autostart.** [Catfriend1 wiki]
4. **The NAS host has no tailscale CLI** (Tailscale is per-app sidecars), and the OCR host's Ollama is bound
   to its *tailnet* IP — so a NAS container can't reach it by default. **Fix: bind Ollama to
   `0.0.0.0` and have the NAS reach the OCR host over the shared LAN** (both on `192.168.X.X`) — far simpler
   than a tailscale sidecar with SOCKS5 proxying.
5. **TrueNAS apps run as the NAS app's uid:gid** — the synced dataset must grant that uid modify or you hit
   "permission hell". [TrueNAS docs]

---

## Stage 0 — Prereqs / decisions
- **Data pool** built (waiting on the 2 HDDs). Create a dataset e.g. `tank/inkvault`, owner
  `apps:apps` (the NAS app's uid:gid), recordsize default.
- **DHCP reservation** for the OCR host's LAN IP (so `OCR_URL` is stable) — or a local DNS rewrite
  `ocr-host.lan → 192.168.X.X`. MagicDNS hostnames are the tailnet-resilient
  alternative if you later route the watcher over the tailnet.

## Stage 1 — OCR node (OCR host) — *mostly done; needs tuning*

**1a. Ollama service env** (`sudo systemctl edit ollama` drop-in):
```ini
[Service]
Environment="OLLAMA_HOST=0.0.0.0:11434"        # LAN + tailnet reachable (was tailnet-only)
Environment="OLLAMA_KEEP_ALIVE=-1"             # keep the model resident (dedicated GPU)
Environment="OLLAMA_FLASH_ATTENTION=1"         # faster + less KV memory at long ctx
Environment="OLLAMA_KV_CACHE_TYPE=q8_0"        # halve KV memory, negligible quality loss
Environment="OLLAMA_NUM_PARALLEL=1"            # 1 page at a time → all VRAM for ctx
```
`sudo systemctl restart ollama`

**1b. Custom OCR model with the right context** — PROPOSAL (OCR host, ~6 lines):
```Dockerfile
# ~/qwen3-vl-ocr.Modelfile
FROM qwen3-vl:8b
PARAMETER num_ctx 32768
PARAMETER temperature 0
SYSTEM "You are a handwriting transcriber. Output ONLY the verbatim text of the page, preserving line breaks. No reasoning, no <think>, no commentary."
```
`ollama create qwen3-vl-ocr -f ~/qwen3-vl-ocr.Modelfile` → the watcher then calls model
`qwen3-vl-ocr`, guaranteeing 32K context regardless of the HTTP path.

**VRAM math (16 GB):** 8B q4 weights ≈ 6 GB + 32K KV at q8 with flash-attn ≈ a few GB + image tokens
→ comfortably under 16 GB at `NUM_PARALLEL=1`. If a dense page ever OOMs, cap image size (Stage 6).

## Stage 2 — Transport (Syncthing)

**2a. Tablet — Syncthing-Fork (Catfriend1, from F-Droid; the official app is archived).**
- Welcome wizard → **grant battery exemption**. Then OneUI: Settings → Apps → Syncthing-Fork →
  Battery → **Unrestricted**; turn off "Put app to sleep"/"Deep sleep"; enable **Autostart**.
- Add the NAS as a device using its **tailnet IP `<NAS_TAILNET_IP>`** (so it connects directly over the
  tailnet — no relays, better battery). [transport = tailscale, not global discovery]
- Share the app's **export folder** as **Send & Receive** (PNG/InkML flow out; `.txt` flows back in).

**2b. NAS — Syncthing (TrueNAS catalog app)** pointed at the **same `tank/inkvault` dataset**,
folder **Send & Receive**, owner the NAS app's uid:gid.

**2c. `.stignore`** in the folder (both ends) so we never trip over Syncthing's own temp files:
```
(?d).syncthing.*.tmp
(?d)~syncthing~*
*.part
```
*(Safe by design anyway: Syncthing writes a temp then atomically renames, so a final `page.png` only
exists when fully synced — the watcher never sees a partial.)* [Syncthing syncing docs]

## Stage 3 — Watcher (NAS custom app)

**3a. Improved watcher** — PROPOSAL (update `android/tools/ocr_watcher.py`):
- re-OCR when the PNG is **newer than** its `.txt` (handles edited/re-exported pages),
- **strip `<think>…</think>`** before writing (Qwen leakage insurance),
- call model **`qwen3-vl-ocr`**, skip dotfiles/temp.

**3b. TrueNAS Custom App** (Apps → Discover → Custom App → Install via YAML) — PROPOSAL:
```yaml
services:
  ocr-watcher:
    image: python:3.13-slim
    restart: unless-stopped
    user: "<NAS_UID>:<NAS_GID>"
    environment:
      - WATCH_DIR=/data
      - OCR_URL=http://192.168.X.X:11434      # OCR host LAN IP (or ocr-host.lan)
      - OCR_MODEL=qwen3-vl-ocr
    volumes:
      - /mnt/tank/inkvault:/data              # the shared dataset
      - /mnt/tank/inkvault/.ocr_watcher.py:/ocr_watcher.py:ro
    command: ["python", "/ocr_watcher.py"]
```
Dataset ACL: grant the NAS app's uid modify (read PNG, write TXT).

## Stage 4 — App side: transcripts → search — PROPOSAL (needs approval)
The app already exports to the SAF folder Syncthing watches, so `page.txt` lands **right next to**
each page after the round-trip. Build:
- a small **import worker** that scans the export tree for `<pageId>.txt`, stores it on the page
  (`transcript` column, Room migration v8), and
- wire it into **search (#6)** — full-text over transcripts (LIKE first; FTS4 if it ever needs it).

That closes the loop: write on paper → transcript searchable in-app, all on your own infra.

---

## Maintenance
- **Updates:** `ollama pull qwen3-vl:8b` then re-`ollama create qwen3-vl-ocr`; watcher image is
  `python:3.13-slim` (no app deps); Syncthing apps auto-update via the catalog.
- **OCR host offline:** the watcher just keeps retrying each cycle; pages queue on the NAS dataset and
  drain when the GPU box is back. No loss, no dupes (idempotent).
- **Backups:** the `tank/inkvault` dataset gets ZFS snapshots (TrueNAS) — exports +
  transcripts are recoverable; the app's Room DB on-device is the other copy.
- **Observability:** `docker logs ocr-watcher` shows `ok page → 412 chars`; add a tiny heartbeat file
  if you want external monitoring (e.g. for Uptime-Kuma).

## Optimization (later)
- **Accuracy:** if Qwen3-VL isn't sharp enough on your hand, your config's alternates `olmOCR-2-7B` /
  `GLM-OCR` are stronger OCR-specialist models — but they're HF weights → run via **vLLM in Docker**
  on the OCR host (docker + a ≥16 GB GPU). One container, OpenAI-compatible, same watcher.
- **Throughput:** raise `OLLAMA_NUM_PARALLEL` only if you batch many pages and have VRAM headroom
  (remember it multiplies KV by parallelism).
- **Image size vs tokens:** the app exports ~150 dpi PNG; bumping DPI improves small-handwriting OCR
  but costs tokens/VRAM. We can add a higher-res "OCR export" toggle if needed, or cap Qwen's
  `max_pixels` to bound cost.
- **Online HTR:** the InkML we already export (stroke order + pressure + time) beats image-only OCR;
  a future stroke-aware model (or appending the trace as text context) is the accuracy ceiling.

## Sources
Ollama FAQ (keep-alive/flash-attn/KV/parallel); ollama#13084 (num_ctx→garbled images), #14798
(qwen3-vl think toggle ignored); ollama.com/library/qwen3-vl; Catfriend1 Syncthing-Fork battery wiki;
Syncthing syncing docs (temp-file atomicity); TrueNAS custom-app + uid:gid permissions docs;
Tailscale TrueNAS / sidecar / MagicDNS docs.
