# OCR deploy runbook — Stage 1 (OCR host) + Stage 3 (NAS)

The copy-paste companion to `OCR_PIPELINE.md`. Files referenced here all live in this `tools/`
directory. Stage 1 you can do **now**; Stage 3 needs the `tank/inkvault` dataset (the arriving HDDs).

## Stage 1 — OCR node (OCR host) — do now

```fish
cd android/tools
./setup-ocr-host.fish
```

That script (idempotent) does all of Stage 1:

1. **Ollama env** — installs `ollama-inkvault.conf` as a systemd drop-in and restarts Ollama:
   binds `0.0.0.0` (so the NAS can reach it over the LAN — finding #4), keeps the model resident,
   flash-attention on, KV cache `q8_0`, one page at a time.
2. **Custom model** — `ollama create qwen3-vl-ocr` from `qwen3-vl-ocr.Modelfile`, baking
   `num_ctx 32768` (finding #1 — the OpenAI endpoint can't set context per request).
3. **Smoke test** — pings the endpoint and prints the LAN URL to use as `OCR_URL`.

Verify by hand if you like:
```fish
systemctl show ollama -p Environment        # the five OLLAMA_* vars
ollama list | grep qwen3-vl-ocr             # the model exists
curl http://192.168.X.X:11434/api/tags  # reachable off-box
```

If `ollama list` shows your base model under a different tag (e.g. `qwen3-vl:8b` not `:latest`),
edit the `FROM` line in `qwen3-vl-ocr.Modelfile` to match and re-run the script — otherwise the
build triggers a redundant pull.

> **VRAM (16 GB):** 8B q4 weights ≈ 6 GB + 32K KV at q8/flash-attn + image tokens → comfortably
> under 16 GB at `NUM_PARALLEL=1`. If a dense page ever OOMs, cap image size (OCR_PIPELINE.md Stage 6).

## Stage 3 — Watcher (NAS) — after the data pool exists

The improved watcher (`ocr_watcher.py`) is already written: re-OCRs when a PNG is newer than its
`.txt`, strips `<think>…</think>`, calls `qwen3-vl-ocr`, skips dotfiles, atomic writes. Deploy it as
a TrueNAS Custom App:

1. **Dataset** — create `tank/inkvault` (owner `apps:apps` = the NAS app's uid:gid, modify ACL — finding #5).
2. **Stage the script outside the synced folder** so Syncthing never replicates it:
   ```sh
   mkdir -p /mnt/tank/apps/ocr-watcher
   cp ocr_watcher.py /mnt/tank/apps/ocr-watcher/ocr_watcher.py
   chown -R "$APP_UID:$APP_GID" /mnt/tank/apps/ocr-watcher  # the NAS app's uid:gid
   ```
3. **Install the app** — Apps → Discover → Custom App → *Install via YAML*, paste
   `ocr-watcher.compose.yaml`. Set `OCR_URL` to the OCR host LAN IP the Stage-1 script printed
   (give the OCR host a DHCP reservation, or add a local DNS rewrite `ocr-host.lan → 192.168.X.X`).
4. **Watch it work** — the app's Logs tab shows `ok <pageId> → N chars`. Drop a test `*.png` into the
   dataset and confirm a `.txt` appears beside it.

## Stage 2 — Transport (Syncthing) — after the data pool exists

The tablet ↔ NAS sync that closes the loop: write on paper → `.png` syncs to the NAS → watcher OCRs
on the OCR host → `.txt` syncs back → the app's Search screen imports it and re-exports a refreshed `.md`.
Step-by-step (Syncthing-Fork battery exemption, NAS catalog app, `.stignore`, end-to-end check) is in
[`SYNCTHING_SETUP.md`](SYNCTHING_SETUP.md).

## Making the vault AI-queryable
Once transcripts are flowing, point an MCP server at the export folder so any MCP client can search
and reason over your handwriting — see [`MCP_QUERY.md`](MCP_QUERY.md). No InkVault code required.

## Maintenance
- **Model update:** `ollama pull qwen3-vl` then re-run `./setup-ocr-host.fish` (re-creates the model).
- **OCR host offline:** the watcher retries each cycle; pages queue on the NAS and drain when it's back
  (idempotent — no loss, no dupes).
- **Backups:** ZFS-snapshot `tank/inkvault`; the on-device Room DB is the second copy.
