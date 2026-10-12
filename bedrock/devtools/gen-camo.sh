#!/usr/bin/env bash
# Regenerates behavior_pack/scripts/camo_table.js from the installed game's vanilla resource packs,
# then the leg's resource-pack files (their texture list comes from that table).
#   bash devtools/gen-camo.sh            (or: bash devtools/gen-camo.sh "<game>/data/resource_packs")
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PACKS="${1:-}"
if [ -z "$PACKS" ]; then
  for d in "/c/Program Files/WindowsApps/"Microsoft.MinecraftUWP_*_x64__8wekyb3d8bbwe/data/resource_packs \
           "/c/XboxGames/Minecraft for Windows/Content/data/resource_packs"; do
    [ -f "$d/vanilla/blocks.json" ] && PACKS="$d"
  done
fi
[ -n "$PACKS" ] || { echo "can't find the game's resource_packs folder; pass it as an argument" >&2; exit 1; }
perl "$ROOT/devtools/camo-table.pl" "$PACKS" > "$ROOT/behavior_pack/scripts/camo_table.js.tmp"
mv "$ROOT/behavior_pack/scripts/camo_table.js.tmp" "$ROOT/behavior_pack/scripts/camo_table.js"
head -2 "$ROOT/behavior_pack/scripts/camo_table.js" | tail -1
bash "$ROOT/devtools/gen-leg-rp.sh"
