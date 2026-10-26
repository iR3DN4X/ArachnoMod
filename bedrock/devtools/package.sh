#!/bin/sh
# Builds the release .mcaddon (both packs) and proves it unpacks to exactly the source packs.
#
#   bash devtools/package.sh <output dir>
#
# The version comes from the manifests (devtools/version.sh). Bump it in BOTH manifests (header,
# every module, the BP's dependency on the RP, and the "vX.Y.Z" in both pack names) before
# packaging a new release - version.sh refuses anything that disagrees. Uses Windows' own
# tar.exe (bsdtar), which writes a standard zip with forward-slash paths.
set -e
here=$(cd "$(dirname "$0")/.." && pwd)
out=${1:?usage: package.sh <output dir>}
ver=$(sh "$here/devtools/version.sh")
mkdir -p "$out"
out=$(cd "$out" && pwd)
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
