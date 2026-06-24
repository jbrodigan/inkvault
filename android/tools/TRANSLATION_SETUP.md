# High-quality translation — setup

InkVault's translator is **quality-first and private**. The research (WMT24 human eval) is clear:
modern translation LLMs match or beat the commercial online providers, and Google's *on-device* NMT
(ML Kit) is explicitly the "casual/offline" tier — the "slop" to avoid. So InkVault uses a tiered
engine:

1. **Primary — a translation LLM on your OCR host** (over the tailnet). One model covers *every*
   language, there's nothing to download per-language, and nothing leaves your network. This is the
   "extremely good" path.
2. **Fallback — ML Kit on-device** (downloadable ~30 MB packs). Honest "offline, basic quality" tier
   for when the box isn't reachable. The app labels it as such.

Source language auto-detects on-device (ML Kit Language ID); the default target is your device
language. Both are overridable in the page's **Text** view (the "From"/"To" pickers).

## Set up the primary (quality) engine on the OCR host

Pick a model and serve it with the OCR Ollama you already run:

| Model | Pull | License | Notes |
|---|---|---|---|
| **EuroLLM-9B** | community GGUF → `ollama create eurollm -f Modelfile` | **Apache-2.0** ✅ | Recommended default — top open quality with a clean license; ~6 GB Q4, fits a CUDA GPU (≥16 GB VRAM) with room. |
| **Tower+ 9B** (Unbabel) | community GGUF | CC-BY-NC-4.0 (non-commercial) | State-of-the-art open translation; fine for personal use. |
| **Qwen2.5/Qwen3 7–14B** | `ollama pull qwen2.5:7b` | Apache-2.0 ✅ | Strong general fallback if you don't want a specialist. |

EuroLLM/Tower aren't first-class Ollama library tags yet, so create from a GGUF:

```fish
# example: a Q4_K_M GGUF you downloaded for EuroLLM-9B-Instruct
printf 'FROM ./EuroLLM-9B-Instruct-Q4_K_M.gguf\nPARAMETER temperature 0.2\n' > eurollm.Modelfile
ollama create eurollm -f eurollm.Modelfile
```

The translation endpoint is just your Ollama (same box as OCR), so `OLLAMA_HOST=0.0.0.0` (from the
OCR setup) already exposes it on the LAN/tailnet.

## Point the app at it

In **Settings → Translation**:
- **Translation endpoint:** `http://ocr-host.lan:11434` (or the OCR host LAN/tailnet IP).
- **Translation model:** the tag you created, e.g. `eurollm`.

Leave both blank to use only the on-device offline translator.

## Use it

Open a page → **Text** view (the "Aa"/document toggle in the page bar) → the transcript renders in a
printed font. Pick **From** (or leave Auto) and **To**, then **Translate**. With the endpoint set,
the LLM does the work; otherwise the app falls back to ML Kit (and offers to download that language
pack for offline use).

## Why this beats Google Translate
- **Quality:** a Tower/EuroLLM-class model is at or above the commercial providers in human eval;
  ML Kit (your fallback) is strictly Google's *lesser* offline tier.
- **Privacy:** the quality path is your own GPU over your own tailnet; the fallback is fully
  on-device. Nothing touches a third-party cloud.
- **No per-language zoo:** one server model = all languages; downloads only matter for the offline
  fallback.
