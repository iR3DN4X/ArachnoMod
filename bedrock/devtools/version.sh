#!/bin/sh
# Prints the add-on's version (e.g. 1.3.0), or stops with the reason. Every version in BOTH
# manifests must agree - the header, every module, the BP's dependency on the RP - and both pack
# names must say it ("Netherite Octoarachnopod v1.3.0 (Behavior Pack)"), because the name is the
# only place the game's pack list shows which copy you are looking at. Used by package.sh and
# deploy.sh.
#
#   sh devtools/version.sh
here=$(cd "$(dirname "$0")/.." && pwd)
perl -0777 -e '
  my (%seen, %name);
  for my $f (@ARGV) {
    open my $h, "<", $f or die "$f: $!\n";
    my $t = <$h>;
    $seen{join(".", $1, $2, $3)}++ while $t =~ /"version":\s*\[\s*(\d+),\s*(\d+),\s*(\d+)\s*\]/g;
    ($name{$f}) = $t =~ /"header".*?"name":\s*"([^"]*)"/s or die "$f: no pack name\n";
  }
  my @v = sort keys %seen;
  die "no version in the manifests\n" unless @v;
  die "manifest versions differ: @v\n" if @v > 1;
  for my $f (@ARGV) {
    die "$f: pack name \"$name{$f}\" does not say v$v[0]\n" unless $name{$f} =~ /\bv\Q$v[0]\E(?![\d.])/;
  }
  print $v[0];
' "$here/behavior_pack/manifest.json" "$here/resource_pack/manifest.json"
