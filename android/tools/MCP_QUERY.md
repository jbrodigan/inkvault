# Make your InkVault notes AI-queryable (MCP)

InkVault exports a plain **Markdown vault** (one `<pageId>.md` per page: frontmatter + page image
embed + OCR transcript, sitting next to the `.png/.pdf/.svg/.inkml/.json`). That folder is already a
drop-in knowledge base — point an MCP server at it and any MCP client can
read, search, and reason over your handwriting transcripts. **No InkVault code required.**

Pick by how smart you need search to be:

## 1. Baseline — official Filesystem MCP (read/grep, zero setup)

The reference server gives allow-listed read/search over a directory. Point it at the synced vault:

```jsonc
// MCP client config (e.g. mcpServers entry)
{
  "mcpServers": {
    "inkvault": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/mnt/tank/inkvault"]
    }
  }
}
```

- Repo: https://github.com/modelcontextprotocol/servers/tree/main/src/filesystem — **MIT**, official.
- Good for: "find the page where I wrote about X", reading a transcript, listing recent notes.
- Limit: keyword/grep only — exact-match misses OCR typos.

## 2. Semantic search — a Markdown RAG MCP (handles OCR noise)

Handwriting OCR makes spelling errors that keyword search can't find; vector search matches by
meaning. Both of these index a plain Markdown folder (vault-format-agnostic), so they work on the
InkVault export directly — no Obsidian required:

- **markdown-vault-mcp** — hybrid FTS5 **+** vector search, frontmatter-aware, incremental reindex
  (suits a vault that grows on every pen sync). https://github.com/pvliesdonk/markdown-vault-mcp
- **vault-mcp** — semantic RAG with live sync + quality-based chunking.
  https://github.com/robbiemu/vault-mcp

> These are smaller/solo projects — check recent commits before depending on one. Run it on the NAS
> (or the OCR host) pointed at `/mnt/tank/inkvault`.

## 3. If you use Obsidian as the front end

Open the vault in Obsidian and add the **Local REST API** plugin (it now ships a built-in MCP
server), or run **mcp-obsidian** for live vault ops (append/search/patch through the running app):
https://github.com/MarkusPfundstein/mcp-obsidian · https://github.com/coddingtonbear/obsidian-local-rest-api

## Worth building an InkVault-specific MCP server later?

Not yet (YAGNI) — the generic servers cover read + search of the exported vault. Build a thin one
*on top of* the filesystem server only if you want InkVault-specific tools the generic ones can't
express, e.g.:

- `get_stroke_data(pageId)` — raw Ncode coordinates/timing from the `.inkml`, for re-OCR or analysis.
- `reocr_page(pageId, engine)` — re-run OCR (Ollama / on-device) and patch the transcript.
- `link_transcript_to_strokes(line)` — jump from a Markdown line back to its source ink region.
- `search_with_ocr_confidence` — surface low-confidence transcript lines for review.

Keep it minimal and layer it over the filesystem server rather than reimplementing file I/O.
