#!/usr/bin/env fish
# One-shot setup for the InkVault OCR node on the OCR host (OCR_PIPELINE.md Stage 1).
# Idempotent: safe to re-run. Needs sudo for the systemd drop-in.
#
#   ./setup-ocr-host.fish
#
# Does: install the Ollama env drop-in, restart Ollama, build the qwen3-vl-ocr model,
#       then smoke-test the endpoint. Run from this tools/ directory.

set -l here (dirname (status --current-filename))

echo "==> Installing Ollama systemd drop-in"
sudo install -Dm644 $here/ollama-inkvault.conf /etc/systemd/system/ollama.service.d/inkvault.conf
sudo systemctl daemon-reload
sudo systemctl restart ollama

echo "==> Waiting for Ollama to come back up"
for i in (seq 1 30)
    if curl -sf http://127.0.0.1:11434/api/tags >/dev/null 2>&1
        break
    end
    sleep 1
end

echo "==> Building qwen3-vl-ocr (32K context baked in)"
if not ollama list | grep -q '^qwen3-vl-ocr'
    ollama create qwen3-vl-ocr -f $here/qwen3-vl-ocr.Modelfile
else
    echo "    qwen3-vl-ocr already exists — recreating to pick up Modelfile changes"
    ollama create qwen3-vl-ocr -f $here/qwen3-vl-ocr.Modelfile
end

set -l lan (ip -4 -o addr show up scope global | awk '{print $4}' | cut -d/ -f1 | head -1)
echo "==> Smoke test (text-only ping; image OCR is exercised by the watcher)"
curl -sf http://127.0.0.1:11434/v1/chat/completions \
    -H 'Content-Type: application/json' \
    -d '{"model":"qwen3-vl-ocr","messages":[{"role":"user","content":"reply with the single word OK"}]}' \
    | string match -q '*OK*' && echo "    model responds ✓" || echo "    ⚠ model did not respond as expected"

echo ""
echo "Done. Reachable off-box at: http://$lan:11434"
echo "Put that IP (or an ocr-host.lan DNS rewrite) into ocr-watcher.compose.yaml's OCR_URL."
