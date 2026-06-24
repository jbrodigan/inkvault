# Syncthing setup — tablet ↔ NAS (OCR_PIPELINE.md Stage 2)

The transport that carries pages out to the NAS and transcripts back to the tablet. Do this once the
`tank/inkvault` dataset exists (Stage 3) and the OCR host OCR node is up (Stage 1). It moves files
**only** between your own two devices over the tailnet — no cloud, no relays.

```
tablet (InkVault export folder)  ⇄  Syncthing  ⇄  NAS dataset  →  watcher → OCR host → .txt ⇄ back
```

## 2a. Tablet — Syncthing-Fork (Catfriend1)

OneUI aggressively kills background apps, and a mid-write kill can corrupt Syncthing's database — so
use the **Syncthing-Fork** build (the official app is archived) and exempt it from battery
management.

1. Install **Syncthing-Fork** from F-Droid.
2. Run the welcome wizard → **grant the battery exemption** when asked.
3. Then OneUI belt-and-suspenders: **Settings → Apps → Syncthing-Fork → Battery → Unrestricted**;
   turn **off** "Put app to sleep" / "Deep sleep"; enable **Autostart**.
4. Add the NAS as a device by its **tailnet IP `<NAS_TAILNET_IP>`** (direct over the tailnet — no global
   discovery, no relays, better battery). Approve the pairing prompt on the NAS side.
5. Share the app's **export folder** (the SAF folder you picked in InkVault → Settings → Sync method
   → Local folder) as **Send & Receive** — PNG/InkML/PDF/MD/SVG/JSON flow out; `.txt` transcripts
   flow back in.

> Find the tablet's folder path: it's the tree you granted in InkVault's Settings. Point Syncthing
> at that same directory so exports and transcripts share one folder.

## 2b. NAS — Syncthing (TrueNAS catalog app)

1. Apps → install **Syncthing** from the catalog; give it host-path access to **`/mnt/tank/inkvault`**
   (the same dataset the watcher uses), owner **the NAS app's uid:gid**.
2. Accept the tablet's device request; share the folder as **Send & Receive**, pointed at
   `/mnt/tank/inkvault`.
3. Confirm both sides show the folder **Up to Date**.

## 2c. `.stignore` (both ends)

Drop this in the shared folder so Syncthing's own temp files never reach the watcher:

```
(?d).syncthing.*.tmp
(?d)~syncthing~*
*.part
```

*(Safe by design anyway: Syncthing writes a temp file then atomically renames, so a final
`page.png` only exists once fully synced — the watcher never sees a partial. The `.stignore`
just keeps the folder tidy and avoids OCR-ing a half file on a slow link.)*

## Verifying the whole loop

1. Write a page on paper (or capture in-app) → InkVault exports it to the folder.
2. Watch it appear on the NAS (`/mnt/tank/inkvault/<pageId>.png`).
3. The watcher OCRs it on the OCR host and writes `<pageId>.txt` (watcher logs: `ok <pageId> → N chars`).
4. The `.txt` syncs back to the tablet; open **Search** in InkVault — the page is now full-text
   searchable, and its `.md` note refreshes with the transcript.

If a step stalls: tablet not syncing → re-check the battery exemption (2a.3); NAS can't reach the OCR host
→ Stage 1 `OLLAMA_HOST=0.0.0.0` + the right `OCR_URL`; permission errors → the dataset must grant
**the NAS app's uid** modify.
