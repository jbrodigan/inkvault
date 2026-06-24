#!/usr/bin/env python3
"""
Ncode coordinate calibration: turn ruler-traced lines into one physical scale.

The smartpen reports positions in Ncode units, not millimetres. To render/print a
page at true physical size we need mm-per-unit. This fits it from calibration
traces: draw straight lines of known length along a ruler, log them with the
measurement harness, and this does a least-squares fit of

    captured_units = a * known_cm + b

across every trace. The slope `a` is the real scale (units/cm); the intercept `b`
absorbs the fixed per-stroke lead-in/out (the pen catching a little ink before/after
the marks). Reports mm-per-unit, the intercept in mm, and R² so you see the fit
quality instead of eyeballing one ratio.

Input: the harness's measurements .xlsx (columns: known_cm, x, y, phase, ...). Needs
openpyxl (pip install openpyxl). Traces are split into individual strokes by the
DOWN phase, so a horizontal and a vertical line of the *same* known length stay
separate (one xlsx can hold both axes). Degenerate taps — the pen's wake-up dot —
are dropped automatically (span below TAP_MIN).

Note: stdlib-only math (no numpy) — a handful of points don't need it. openpyxl
is the one dep, only to read the harness's native .xlsx; export CSV and adapt read()
if you'd rather drop it.
"""
import sys
from collections import defaultdict
from math import hypot

# A stroke whose bbox diagonal is under this (Ncode units) is a wake tap / artifact, not a
# calibration line. Well under the shortest real trace (a 1 cm line is ~4 units).
TAP_MIN = 1.0


def read_strokes(path):
    """[(known_cm, [(x, y), ...]), ...] — one entry per pen stroke (split on DOWN)."""
    import openpyxl
    wb = openpyxl.load_workbook(path, data_only=True)
    ws = wb.active
    rows = ws.iter_rows(values_only=True)
    header = [str(c).strip().lower() if c is not None else "" for c in next(rows)]
    ci = {name: header.index(name) for name in ("known_cm", "x", "y", "phase")}
    strokes, cur = [], None
    for r in rows:
        cm, x, y, phase = (r[ci[k]] for k in ("known_cm", "x", "y", "phase"))
        if phase == "DOWN":
            if cur:
                strokes.append(cur)
            cur = (float(cm), [])
        if cur is not None and x is not None and y is not None:
            cur[1].append((float(x), float(y)))
    if cur:
        strokes.append(cur)
    return strokes


def span_xy(points):
    """(dx, dy) bbox extents in Ncode units."""
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    return max(xs) - min(xs), max(ys) - min(ys)


def span(points):
    """Bbox diagonal in Ncode units — works for horizontal and vertical traces alike."""
    dx, dy = span_xy(points)
    return hypot(dx, dy)


def fit(samples):
    """Least-squares units = a*cm + b over [(cm, units)]. Returns (a, b, r2)."""
    n = len(samples)
    if n < 2:
        raise ValueError("need at least two traces of different known lengths")
    sx = sum(cm for cm, _ in samples)
    sy = sum(u for _, u in samples)
    sxx = sum(cm * cm for cm, _ in samples)
    sxy = sum(cm * u for cm, u in samples)
    denom = n * sxx - sx * sx
    if denom == 0:
        raise ValueError("all traces have the same known length; vary them")
    a = (n * sxy - sx * sy) / denom
    b = (sy - a * sx) / n
    mean = sy / n
    ss_tot = sum((u - mean) ** 2 for _, u in samples)
    ss_res = sum((u - (a * cm + b)) ** 2 for cm, u in samples)
    r2 = 1.0 - ss_res / ss_tot if ss_tot else 1.0
    return a, b, r2


def calibrate(strokes):
    samples = []  # (known_cm, dominant-axis span, axis)
    for cm, pts in strokes:
        if span(pts) < TAP_MIN:
            continue  # a wake tap / artifact, not a calibration line
        dx, dy = span_xy(pts)
        samples.append((cm, max(dx, dy), "X" if dx >= dy else "Y"))
    samples.sort()
    a, b, r2 = fit([(cm, u) for cm, u, _ in samples])
    return samples, a, b, r2


def _report(samples, a, b, r2):
    print("known_cm   captured_units   units/cm   axis")
    for cm, u, axis in samples:
        print(f"  {cm:7.2f}   {u:13.2f}   {u / cm:7.3f}   {axis}")
    print()
    print(f"fit: units = {a:.4f} * cm + {b:.3f}   (R^2 = {r2:.4f})")
    print(f"  scale         : {a:.3f} units/cm")
    print(f"  MM_PER_UNIT   : {10.0 / a:.3f} mm/unit")
    print(f"  lead-in/out   : {b:.2f} units = {b * 10.0 / a:.2f} mm per stroke")
    by_axis = defaultdict(list)
    for cm, u, axis in samples:
        by_axis[axis].append(u / cm)
    if len(by_axis) == 2:
        xm = sum(by_axis["X"]) / len(by_axis["X"])
        ym = sum(by_axis["Y"]) / len(by_axis["Y"])
        print(f"  isotropy      : X={xm:.3f} u/cm, Y={ym:.3f} u/cm  (Y/X = {ym / xm:.3f})")


def page_extent(strokes, page_w_cm, page_h_cm, type_id, display_name, code):
    """From corner/edge boundary traces, derive the writable dot-rectangle (the union bbox of all
    real strokes — corners pushed to the extremes, edges spanning each dimension) and print a
    paste-ready NotebookType + PageGeometry. Tap/wake artefacts are dropped first."""
    pts = [p for _, stroke in strokes if span(stroke) >= TAP_MIN for p in stroke]
    if not pts:
        raise ValueError("no usable boundary strokes (all below the tap threshold)")
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    w_mm, h_mm = page_w_cm * 10.0, page_h_cm * 10.0
    # Implied scale lower bound: the writable area can't be wider/taller than the sheet.
    scale_lb = max((x1 - x0) / page_w_cm, (y1 - y0) / page_h_cm)
    print(f"writable rect (units): x[{x0:.2f}..{x1:.2f}] y[{y0:.2f}..{y1:.2f}]")
    print(f"  -> {x1 - x0:.2f} x {y1 - y0:.2f} units on a {page_w_cm} x {page_h_cm} cm sheet")
    print(f"  implied scale >= {scale_lb:.3f} units/cm (writable must fit the sheet); "
          f"current MM_PER_UNIT 2.32 = 4.31 u/cm")
    const = type_id.upper()
    print("\n--- paste into NotebookType.kt ---")
    print(f"""        val {const} = NotebookType(
            id = "{type_id}",
            displayName = "{display_name}",
            code = "{code}",
            geometry = PageGeometry({x0:.1f}f, {y0:.1f}f, {x1:.1f}f, {y1:.1f}f, {w_mm:.1f}f, {h_mm:.1f}f),
        )
        // then register its Ncode book id:  private val BY_BOOK = mapOf(438 to PROFESSIONAL, <book> to {const})""")


def planner_layout(samples) -> str:
    """From a few observed (page, year, month) samples off the planner's printed dated pages, return a
    paste-ready plannerLayout. Evenly-spaced months → the compact monthly() form; else explicit
    sections. Pure (returns the snippet) so the logic is covered by --selftest."""
    samples = sorted(samples)
    pages = [s[0] for s in samples]
    diffs = [pages[i + 1] - pages[i] for i in range(len(pages) - 1)]
    if diffs and len(set(diffs)) == 1:
        ppm, first, y0, m0 = diffs[0], pages[0], samples[0][1], samples[0][2]
        return (f"            // evenly spaced ({ppm} pages/month); set months to the planner's total.\n"
                f"            plannerLayout = PlannerLayout.monthly(firstPage = {first}, "
                f"pagesPerMonth = {ppm}, startYear = {y0}, startMonth = {m0}, months = {len(samples)}),")
    rows = ",\n".join(
        f"                PlannerLayout.Section(fromPage = {p}, year = {y}, month = {m})"
        for p, y, m in samples
    )
    return f"            plannerLayout = PlannerLayout(listOf(\n{rows},\n            )),"


def _selftest():
    # Synthetic ground truth: 3.8 units/cm, 0.5-unit lead-in/out, exact -> R^2 == 1.
    truth_a, truth_b = 3.8, 0.5
    samples = [(cm, truth_a * cm + truth_b) for cm in (1.0, 5.0, 10.0)]
    a, b, r2 = fit(samples)
    assert abs(a - truth_a) < 1e-9 and abs(b - truth_b) < 1e-9 and abs(r2 - 1.0) < 1e-9

    # planner_layout: evenly-spaced months -> monthly(); uneven -> explicit sections.
    even = planner_layout([(100, 2026, 6), (130, 2026, 7), (160, 2026, 8)])
    assert "PlannerLayout.monthly(firstPage = 100, pagesPerMonth = 30, startYear = 2026" in even, even
    uneven = planner_layout([(100, 2026, 6), (125, 2026, 7), (160, 2026, 8)])
    assert "PlannerLayout.Section(fromPage = 100, year = 2026, month = 6)" in uneven, uneven
    assert "monthly(" not in uneven

    print("selftest OK")


if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1] == "--selftest":
        _selftest()
    elif len(sys.argv) >= 5 and sys.argv[1] == "--page-extent":
        # --page-extent <xlsx> <page_w_cm> <page_h_cm> [type_id] [display_name] [code]
        xlsx, w, h = sys.argv[2], float(sys.argv[3]), float(sys.argv[4])
        type_id = sys.argv[5] if len(sys.argv) > 5 else "newtype"
        display = sys.argv[6] if len(sys.argv) > 6 else "New Notebook"
        code = sys.argv[7] if len(sys.argv) > 7 else type_id[:4]
        page_extent(read_strokes(xlsx), w, h, type_id, display, code)
    elif len(sys.argv) >= 3 and sys.argv[1] == "--planner-layout":
        # --planner-layout PAGE:YEAR:MONTH PAGE:YEAR:MONTH …  (from the planner's printed dated pages)
        samples = [tuple(int(x) for x in tok.split(":")) for tok in sys.argv[2:]]
        print("--- paste into the planner's NotebookType ---")
        print(planner_layout(samples))
    elif len(sys.argv) == 2:
        _report(*calibrate(read_strokes(sys.argv[1])))
    else:
        print(
            f"usage: {sys.argv[0]} measurements.xlsx            # scale fit (ruler traces)\n"
            f"       {sys.argv[0]} --page-extent measurements.xlsx <w_cm> <h_cm> [id] [name] [code]\n"
            f"       {sys.argv[0]} --planner-layout PAGE:YEAR:MONTH …\n"
            f"       {sys.argv[0]} --selftest",
            file=sys.stderr,
        )
        sys.exit(2)
