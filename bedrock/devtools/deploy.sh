#!/bin/sh
# Installs both packs as Minecraft's DEVELOPMENT packs, in folders named after the version
# (development_behavior_packs/arachnomod_bp_v1.3.0, development_resource_packs/arachnomod_rp_v1.3.0),
# and proves the copies are byte-identical to the source. Every older arachnomod_bp* /
# arachnomod_rp* dev folder is removed first, so exactly one dev version is ever installed and
# no file deleted from the source lingers there. Quit the game fully before, relaunch after.
#
#   bash devtools/deploy.sh
set -e
here=$(cd "$(dirname "$0")/.." && pwd)
ver=$(sh "$here/devtools/version.sh")
mojang="$(cygpath -u "${APPDATA:?}")/Minecraft Bedrock/Users/Shared/games/com.mojang"
[ -d "$mojang" ] || { echo "no Bedrock data folder at $mojang"; exit 1; }
if tasklist 2>/dev/null | grep -qi "Minecraft.Windows.exe"; then
  echo "Minecraft is running - quit it fully first"; exit 1
fi

for d in "$mojang"/development_behavior_packs/arachnomod_bp* "$mojang"/development_resource_packs/arachnomod_rp*; do
  [ -d "$d" ] && rm -rf "$d" && echo "removed $(basename "$(dirname "$d")")/$(basename "$d")"
done
bp="$mojang/development_behavior_packs/arachnomod_bp_v$ver"
rp="$mojang/development_resource_packs/arachnomod_rp_v$ver"
cp -r "$here/behavior_pack" "$bp"
cp -r "$here/resource_pack" "$rp"
diff -r "$here/behavior_pack" "$bp"
diff -r "$here/resource_pack" "$rp"
echo "deployed v$ver: arachnomod_bp_v$ver + arachnomod_rp_v$ver, byte-identical to the source"

# An imported copy (a .mcaddon from CurseForge) has the same pack ids - the pack list then shows
# two entries. The names say which is which; this just points it out.
for f in "$mojang"/behavior_packs/*/manifest.json "$mojang"/resource_packs/*/manifest.json; do
  [ -f "$f" ] || continue
  if grep -q -E "7d9aaa5f-a5ba-4178-89b9-fb5d4e4110c9|11f2778b-579c-4607-8824-0dd8c02fb711" "$f"; then
    echo "note: an imported copy is also installed: ${f%/manifest.json}"
  fi
done
