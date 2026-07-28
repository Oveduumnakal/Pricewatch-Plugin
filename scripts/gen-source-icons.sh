#!/usr/bin/env bash
# Regenerates the collection-log source glyph PNGs from their hand-authored SVGs.
# Requires rsvg-convert (librsvg). Run from the repo root.
set -euo pipefail
src="icons/source"
dest="src/main/resources/com/oveduumnakal"
for svg in "$src"/*.svg; do
  base=$(basename "$svg" .svg)
  rsvg-convert -w 32 -h 32 "$svg" -o "$dest/source_${base}.png"
  echo "generated source_${base}.png"
done
