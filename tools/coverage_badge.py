#!/usr/bin/env python3
"""Generate a shields-style coverage badge SVG from an lcov report."""

import sys
from pathlib import Path


def color_for(pct: float) -> str:
    if pct >= 90:
        return "#4c1"
    if pct >= 80:
        return "#97ca00"
    if pct >= 70:
        return "#dfb317"
    if pct >= 60:
        return "#fe7d37"
    return "#e05d44"


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: coverage_badge.py <lcov.info> <out.svg>", file=sys.stderr)
        return 2

    lcov_path = Path(sys.argv[1])
    out_path = Path(sys.argv[2])

    found = hit = 0
    for line in lcov_path.read_text().splitlines():
        if line.startswith("LF:"):
            found += int(line[3:] or 0)
        elif line.startswith("LH:"):
            hit += int(line[3:] or 0)

    pct = (100.0 * hit / found) if found else 0.0
    label = f"{pct:.1f}%"
    color = color_for(pct)
    label_width = 70
    value_width = 52 if len(label) <= 4 else 58
    width = label_width + value_width
    value_center = label_width + value_width / 2

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(
        f'''<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="20" role="img" aria-label="coverage: {label}">
  <title>coverage: {label}</title>
  <g>
    <rect width="{label_width}" height="20" fill="#555"/>
    <rect x="{label_width}" width="{value_width}" height="20" fill="{color}"/>
    <g fill="#fff" font-family="Verdana,DejaVu Sans,sans-serif" font-size="11" text-anchor="middle">
      <text x="{label_width / 2}" y="14">coverage</text>
      <text x="{value_center}" y="14">{label}</text>
    </g>
  </g>
</svg>
'''
    )
    print(f"coverage {label} -> {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())