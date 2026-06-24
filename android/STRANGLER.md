# Strangling the NeoLAB SDK over time

Goal: keep the SDK's *working* value (Ncode dot decoding + the BLE pen protocol) while
progressively replacing its *broken/legacy* parts, until the app could run with little or none of
the original SDK — without ever rewriting the app.

## The seam
Everything routes through one boundary: **`com.inkvault.pen.NeoPenSdk`** in **`:pencore`**.

```
:app ──depends on──> :pencore  (NeoPenSdk interface, PenDot, PenConnState, FakeNeoPenSdk)
                        ▲
        binds one of   │
   ┌────────────────────┼─────────────────────────┐
:neosdk (NeoSdkAdapter, the only kr.neolab importer)   FakeNeoPenSdk (tests/dev)
```

The app never imports `kr.neolab.sdk`. CI builds `:app` + `:pencore` against `FakeNeoPenSdk`; the
real SDK lives in the gated `:neosdk` module. To swap *any* implementation, you change one line in
`di/ServiceLocator.kt` — nothing else moves.

## How to gut it, one piece at a time (strangler fig)
Each step adds a small in-house implementation behind the **same** `NeoPenSdk` boundary, guarded by
the fake + unit tests, then flips the binding. Nothing downstream changes.

1. **BLE transport** — replace the SDK's `BTLEAdt`/GATT layer with our own
   `BluetoothGatt`-based connector (we already saw the exact service/characteristic UUIDs in the
   GO/NO-GO log: notify char `64cd86b1-…`). Keep using the SDK only for packet parsing.
2. **Packet framing** — reimplement the `c0 … c1` DLE framing + checksum we observed
   (`ProtocolParser20`) in Kotlin; feed decoded payloads to the SDK's command handlers, or…
3. **Command/handshake** — reimplement `reqPenInfo` / pen-status / password / offline-list requests
   from the captured packet shapes. At this point the SDK is only doing dot→coordinate math.
4. **Dot/Ncode decode** — the last and hardest piece (the real reason to keep the SDK). Replace
   only if/when it's worth it; until then it stays quarantined in `:neosdk`.

Each replacement is its own `NeoPenSdk`-implementing class (or an internal collaborator of the
adapter), swapped in via `ServiceLocator`, with `FakeNeoPenSdk`-style tests proving parity before
the flip. Captured real-pen data (via `tools/logcat_to_replay.py`) is the regression fixture.

## Why not gut it all now
Steps 1–3 are tractable from the captured packets; step 4 (Ncode decode) is months of work the
brief says to wrap, not rewrite. So we strangle opportunistically: every time the SDK's legacy
behavior bites us, we replace *that* piece behind the seam — and the blast radius is one file.
