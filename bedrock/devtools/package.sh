#!/bin/sh
# Builds the release .mcaddon (both packs) and proves it unpacks to exactly the source packs.
#
#   bash devtools/package.sh                 -> a NEW folder BEDROCK_ADDON_STAGED/v<ver>_<date>_<time>
#   bash devtools/package.sh <output dir>
#
# Every build is staged in a folder of its own (the user's rule, 2026-10-25), next to a
# BUILD_INFO.txt with its size and SHA-1, so an upload never picks up a copy that was silently
# replaced. It refuses to overwrite a .mcaddon that is already staged.
#
# The version comes from the manifests (devtools/version.sh). Bump it in BOTH manifests (header,
# every module, the BP's dependency on the RP, and the "vX.Y.Z" in both pack names) before
# packaging a new release - version.sh refuses anything that disagrees. Uses Windows' own
# tar.exe (bsdtar), which writes a standard zip with forward-slash paths.
set -e
here=$(cd "$(dirname "$0")/.." && pwd)
ver=$(sh "$here/devtools/version.sh")
built=$(date +%Y-%m-%d_%H%M)
out=${1:-"$here/../BEDROCK_ADDON_STAGED/v${ver}_$built"}
file="NetheriteOctoarachnopod-Bedrock-$ver.mcaddon"
[ -e "$out/$file" ] && { echo "$out/$file is already staged - stage this build in a new folder"; exit 1; }
mkdir -p "$out"
out=$(cd "$out" && pwd)
tar=/c/Windows/System32/tar.exe
[ -x "$tar" ] || tar=tar
stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
cp -r "$here/behavior_pack" "$stage/NetheriteOctoarachnopod_BP"
cp -r "$here/resource_pack" "$stage/NetheriteOctoarachnopod_RP"
(cd "$stage" && "$tar" -a -c -f addon.zip NetheriteOctoarachnopod_BP NetheriteOctoarachnopod_RP)
mv "$stage/addon.zip" "$out/$file"
mkdir "$stage/check"
(cd "$stage/check" && "$tar" -x -f "$out/$file")
diff -r "$stage/check/NetheriteOctoarachnopod_BP" "$here/behavior_pack"
diff -r "$stage/check/NetheriteOctoarachnopod_RP" "$here/resource_pack"
{
  echo "File:    $file"
  echo "Version: $ver"
  echo "Built:   $(date '+%Y-%m-%d %H:%M')"
  echo "Bytes:   $(wc -c < "$out/$file" | tr -d ' ')"
  echo "SHA-1:   $(sha1sum "$out/$file" | cut -d' ' -f1)"
} > "$out/BUILD_INFO.txt"
echo "$out/$file: both packs byte-identical to the source"
