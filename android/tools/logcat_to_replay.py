#!/usr/bin/env python3
"""Convert a captured pen-spike logcat into a replay fixture (.jsonl).

Device/shell: the OCR host. Run after the Phase 0 / penspike spike:
    adb logcat -s SPIKE > lamy_session.txt
    python3 logcat_to_replay.py lamy_session.txt > app/src/test/resources/replay/lamy_session.jsonl

The penspike logs each dot as:
    DOT s=<sec> o=<own> note=<note> page=<pg>  x=<x> y=<y>  p=<pressure> type=<dotType>
This turns those into ReplayDot lines the replay harness/tests already consume.

NOTE on `type`→`phase`: the SDK's dotType integer encoding is flagged "verify against source"
in DESIGN.md. The default mapping below (0=DOWN, 1=MOVE, 2=UP) is an assumption — confirm it
against the captured data and adjust DOT_TYPE if needed. Stdlib only; no dependencies.
"""
import json
import re
import sys

DOT_TYPE = {0: "DOWN", 1: "MOVE", 2: "UP"}  # confirm against verified Dot.dotType encoding

DOT_RE = re.compile(
    r"DOT\s+s=(?P<s>-?\d+)\s+o=(?P<o>-?\d+)\s+note=(?P<note>-?\d+)\s+page=(?P<page>-?\d+)\s+"
    r"x=(?P<x>-?[\d.]+)\s+y=(?P<y>-?[\d.]+)\s+p=(?P<p>-?\d+)\s+type=(?P<type>-?\d+)"
)


def convert(lines):
    t = 0
    for line in lines:
        m = DOT_RE.search(line)
        if not m:
            continue
        g = m.groupdict()
        pressure = float(g["p"])
        if pressure > 1.0:           # pen reports 0..255; normalize to 0..1
            pressure = round(pressure / 255.0, 4)
        yield {
            "section": int(g["s"]),
            "owner": int(g["o"]),
            "note": int(g["note"]),
            "page": int(g["page"]),
            "x": float(g["x"]),
            "y": float(g["y"]),
            "pressure": pressure,
            "phase": DOT_TYPE.get(int(g["type"]), "MOVE"),
            "t": t,                  # monotonic; ordering is what the pipeline needs
            "color": -16777216,      # black; logcat carries no color
        }
        t += 1


def main():
    src = open(sys.argv[1]) if len(sys.argv) > 1 else sys.stdin
    count = 0
    for dot in convert(src):
        print(json.dumps(dot))
        count += 1
    print(f"# converted {count} dots", file=sys.stderr)


if __name__ == "__main__":
    main()
