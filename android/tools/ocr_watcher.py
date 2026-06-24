#!/usr/bin/env python3
"""NAS-side OCR watcher (the "split workload" path).

Poll an export folder, transcribe each new/changed page PNG via an OpenAI-compatible
vision endpoint (Ollama / vLLM on your GPU box), and write `<page>.txt` next to it.
Storage + queue + retries stay on the NAS; inference runs on the GPU desktop — the
app needs no OCR code (it already exports PNG+InkML+SVG+JSON).

Stdlib only (no pip install). Robust against the real pipeline:
  - re-transcribes when a page is edited (PNG newer than its TXT),
  - strips Qwen3-VL `<think>…</think>` leakage from the output,
  - skips Syncthing temp/dotfiles (and a final PNG only exists once fully synced),
  - atomic writes (never a half file), and keeps going on errors.

Env vars (all optional):
  WATCH_DIR        folder of exported pages         (default: /data)
  OCR_URL          base URL of the model server     (default: http://ocr-host.lan:11434)
  OCR_MODEL        model tag                         (default: qwen3-vl-ocr  — see OCR_PIPELINE.md)
  POLL_SECONDS     poll interval                     (default: 15)
  OCR_SEND_STROKES 1 = also feed the InkML pen-stroke trajectory as text context (experimental;
                   see below). Default off.

Run:  WATCH_DIR=/mnt/tank/inkvault OCR_URL=http://192.168.X.X:11434 ./ocr_watcher.py

Experimental — strokes-as-text (OCR_SEND_STROKES=1):
  We export the W3C InkML trajectory (X/Y/pressure/time) beside each PNG. Google's "Representing
  Online Handwriting for Recognition in Large Vision-Language Models" (arXiv 2402.15307) shows a
  VLM fed BOTH the rendered image AND the stroke coordinates as text matches/beats dedicated online
  HTR — stroke order disambiguates cursive the image can't. This flag serializes a downsampled
  polyline per stroke into the prompt. It's model-dependent (helps models that can use it, may add
  noise to others) — A/B it on your handwriting before leaving it on.
"""
import base64
import json
import os
import re
import sys
import time
import urllib.request

WATCH = os.environ.get("WATCH_DIR", "/data")
OCR_URL = os.environ.get("OCR_URL", "http://ocr-host.lan:11434").rstrip("/") + "/v1/chat/completions"
MODEL = os.environ.get("OCR_MODEL", "qwen3-vl-ocr")
POLL = int(os.environ.get("POLL_SECONDS", "15"))
MAX_FAILS = int(os.environ.get("OCR_MAX_FAILS", "5"))  # give up on a page after this many tries/session
SEND_STROKES = os.environ.get("OCR_SEND_STROKES", "") not in ("", "0", "false", "False")
MAX_STROKE_POINTS = 600  # cap total points sent so the coordinate text can't blow the context
SYSTEM = ("You are a handwriting transcriber. Output ONLY the verbatim text of the page, "
          "preserving line breaks. No reasoning, no <think>, no commentary.")

# Qwen3-VL can leak a reasoning block even when asked not to (its template ignores think:false),
# so strip any <think>…</think> defensively. See OCR_PIPELINE.md.
THINK = re.compile(r"<think>.*?</think>", re.DOTALL | re.IGNORECASE)
TRACE = re.compile(r"<trace>(.*?)</trace>", re.DOTALL)


def stroke_hint(png_path: str) -> str:
    """Compact 'pen drew these polylines' text from the sibling .inkml, or '' if absent/disabled.

    Each InkML <trace> is 'X Y F T, X Y F T, …'; we keep X,Y (capture order), uniformly downsample
    the whole page to MAX_STROKE_POINTS, and round to ints to keep the token cost bounded.
    """
    if not SEND_STROKES:
        return ""
    inkml = png_path[:-4] + ".inkml"
    try:
        with open(inkml) as f:
            xml = f.read()
    except OSError:
        return ""
    strokes = []
    for body in TRACE.findall(xml):
        pts = []
        for pt in body.split(","):
            f = pt.split()
            if len(f) >= 2:
                try:
                    pts.append((float(f[0]), float(f[1])))
                except ValueError:
                    pass
        if pts:
            strokes.append(pts)
    total = sum(len(s) for s in strokes)
    if total == 0:
        return ""
    step = max(1, total // MAX_STROKE_POINTS)  # uniform downsample across the page
    lines = []
    for s in strokes:
        thinned = s[::step] or [s[0]]
        lines.append(" ".join(f"{int(round(x))},{int(round(y))}" for x, y in thinned))
    return (
        "Supplementary signal: the pen captured these strokes in writing order, one polyline per "
        "stroke as 'x,y' points (paper coordinates). Use them to disambiguate cursive; transcribe "
        "what was written, not the coordinates.\n" + "\n".join(lines)
    )


def transcribe(png_path: str) -> str:
    img = base64.b64encode(open(png_path, "rb").read()).decode()
    content = [
        {"type": "text", "text": "Transcribe this handwritten page verbatim."},
        {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{img}"}},
    ]
    hint = stroke_hint(png_path)
    if hint:
        content.append({"type": "text", "text": hint})
    body = {
        "model": MODEL,
        "temperature": 0,
        "messages": [
            {"role": "system", "content": SYSTEM},
            {"role": "user", "content": content},
        ],
    }
    req = urllib.request.Request(
        OCR_URL, data=json.dumps(body).encode(), headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=600) as r:
        text = json.load(r)["choices"][0]["message"]["content"]
    return THINK.sub("", text).strip()


def needs_ocr(png: str, out: str) -> bool:
    """True if there's no transcript yet, or the page changed since it was last transcribed."""
    try:
        return os.path.getmtime(out) < os.path.getmtime(png)  # re-OCR edited/re-exported pages
    except OSError:
        return True  # no .txt yet


def find_pngs(root: str) -> list:
    """All page PNGs under root, recursively — the app files pages in type/label sub-folders
    (pnb/Work/…, plnr/2026/06_June/…). Skips Syncthing temp/dot files and hidden dirs."""
    found = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if not d.startswith(".")]  # don't descend .stfolder etc.
        for fn in filenames:
            if fn.lower().endswith(".png") and not fn.startswith("."):
                found.append(os.path.join(dirpath, fn))
    return sorted(found)


def run_pass(fails: dict) -> None:
    """One sweep of the folder. [fails] counts consecutive failures per page so a permanently-bad
    one (e.g. a corrupt PNG) is given up on after MAX_FAILS instead of retried every poll."""
    pngs = find_pngs(WATCH)
    ok = failed = skipped = 0
    for png in pngs:
        out = png[:-4] + ".txt"  # the .txt lands next to its .png, in the same sub-folder
        if not needs_ocr(png, out):
            continue
        rel = os.path.relpath(png, WATCH)
        if fails.get(png, 0) >= MAX_FAILS:
            skipped += 1  # already gave up on this one this session (logged when it hit the cap)
            continue
        try:
            text = transcribe(png)
            tmp = out + ".part"
            with open(tmp, "w") as f:
                f.write(text)
            os.replace(tmp, out)  # atomic: never leaves a half-written file
            fails.pop(png, None)
            ok += 1
            print(f"ok  {rel} ({len(text)} chars)", flush=True)
        except Exception as e:  # noqa: BLE001 — keep going; retry next pass unless it hits the cap
            n = fails[png] = fails.get(png, 0) + 1
            failed += 1
            giving_up = " — giving up this session" if n >= MAX_FAILS else f" (attempt {n}/{MAX_FAILS})"
            print(f"err {rel}: {e}{giving_up}", file=sys.stderr, flush=True)
    if ok or failed or skipped:  # quiet when there was nothing to do
        print(f"pass: {ok} transcribed, {failed} failed, {skipped} skipped (of {len(pngs)} pages)", flush=True)


def main() -> None:
    once = "--once" in sys.argv[1:]
    print(f"watching {WATCH} -> {OCR_URL} ({MODEL})" + ("" if once else f", every {POLL}s"), flush=True)
    fails: dict = {}
    while True:
        if not os.path.isdir(WATCH):
            print(f"!! {WATCH} not found yet", file=sys.stderr, flush=True)
            if once:
                return
            time.sleep(POLL)
            continue
        run_pass(fails)
        if once:  # single sweep, for a cron job / systemd timer instead of the internal loop
            return
        time.sleep(POLL)


if __name__ == "__main__":
    main()
