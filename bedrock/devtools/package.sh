#!/bin/sh
# Builds the release .mcaddon (both packs) and proves it unpacks to exactly the source packs.
#
#   bash devtools/package.sh <output dir>
#
# The version comes from behavior_pack/manifest.json. Bump it in BOTH manifests (header, every
# module, and the BP's dependency on the RP) before packaging a new release. Uses Windows' own
# tar.exe (bsdtar), which writes a standard zip with forward-slash paths.
set -e
here=$(cd "$(dirname "$0")/.." && pwd)
out=${1:?usage: package.sh <output dir>}
mkdir -p "$out"
out=$(cd "$out" && pwd)
ver=$(perl -0777 -ne 'print join(".", $1, $2, $3) if /"header".*?"version":\s*\[\s*(\d+),\s*(\d+),\s*(\d+)\s*\]/s' "$here/behavior_pack/manifest.json")
[ -n "$ver" ] || { echo "no version in behavior_pack/manifest.json"; exit 1; }
rp=$(perl -0777 -ne 'print join(".", $1, $2, $3) if /"header".*?"version":\s*\[\s*(\d+),\s*(\d+),\s*(\d+)\s*\]/s' "$here/resource_pack/manifest.json")
[ "$ver" = "$rp" ] || { echo "manifest versions differ: BP $ver, RP $rp"; exit 1; }
tar=/c/Windows/System32/tar.exe
[ -x "$tar" ] || tar=tar
stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
cp -r "$here/behavior_pack" "$stage/NetheriteOctoarachnopod_BP"
cp -r "$here/resource_pack" "$stage/NetheriteOctoarachnopod_RP"
file="NetheriteOctoarachnopod-Bedrock-$ver.mcaddon"
(cd "$stage" && "$tar" -a -c -f addon.zip NetheriteOctoarachnopod_BP NetheriteOctoarachnopod_RP)
mv "$stage/addon.zip" "$out/$file"
mkdir "$stage/check"
(cd "$stage/check" && "$tar" -x -f "$out/$file")
diff -r "$stage/check/NetheriteOctoarachnopod_BP" "$here/behavior_pack"
diff -r "$stage/check/NetheriteOctoarachnopod_RP" "$here/resource_pack"
echo "$out/$file: both packs byte-identical to the source"
