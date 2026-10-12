#!/usr/bin/perl
# Builds the CAMO table - every vanilla block -> the texture its top face shows, and the step and
# landing sounds of its own sound family - from the GAME'S OWN resource packs:
# data/resource_packs/vanilla*/blocks.json + textures/terrain_texture.json, merged in version
# order (each later pack adds and overrides), the same data the game draws blocks with.
#
#   bash devtools/gen-camo.sh          (wraps this; finds the installed game)
#
# Output: behavior_pack/scripts/camo_table.js - TEXTURES (index -> path; 0 = netherite; the
# see-through ones last, from FIRST_TRANSLUCENT on, so the legs draw them blended) and BLOCKS
# (block id -> [texture, step sound, landing sound]). gen-leg-rp.sh reads TEXTURES from it.
# Re-run after a game update to pick up new blocks.
use strict; use warnings; use JSON::PP;

my $root = shift or die "usage: camo-table.pl <resource_packs dir>\n";
opendir my $dh, $root or die "no $root\n";
my @packs = grep { /^vanilla(_\d+(\.\d+)*)?$/ } readdir $dh;
closedir $dh;
sub ver { my ($n) = @_; return [0] if $n eq 'vanilla'; my ($v) = $n =~ /_(.*)$/; return [map { $_ + 0 } split /\./, $v]; }
sub cmpver { my ($a, $b) = (ver($_[0]), ver($_[1])); for my $i (0 .. 3) { my $c = ($a->[$i] // 0) <=> ($b->[$i] // 0); return $c if $c; } 0 }
@packs = sort { cmpver($a, $b) } @packs;

my $json = JSON::PP->new->relaxed;
sub load { my ($f) = @_; return undef unless -f $f; local $/; open my $h, '<', $f or return undef; my $t = <$h>; close $h; return eval { $json->decode($t) }; }

my (%blocks, %terrain);
for my $p (@packs) {
  my $b = load("$root/$p/blocks.json");
  if ($b) { for my $k (keys %$b) { next if $k eq 'format_version'; $blocks{$k} = { %{ $blocks{$k} // {} }, %{ $b->{$k} } }; } }
  my $t = load("$root/$p/textures/terrain_texture.json");
  if ($t && $t->{texture_data}) { $terrain{$_} = $t->{texture_data}{$_} for keys %{ $t->{texture_data} }; }
}

# Textures the game colours by BIOME while drawing (grey in the file, nothing in terrain_texture.json
# says so): grass tops, the tinted leaves, vines, lily pads, grass tufts, sugar cane.
our $BIOME_TINTED = qr{/(grass_top|grass_side|leaves_(oak|big_oak|jungle|acacia|birch|spruce)(_opaque)?|mangrove_leaves|vine|waterlily|tallgrass|fern|reeds)$};

# A terrain key -> [path, tinted]. Variants (arrays) and per-state lists take the first entry.
sub resolve {
  my ($key) = @_;
  return unless defined $key;
  my $e = $terrain{$key} or return;
  my $tx = $e->{textures};
  $tx = $tx->[0] while ref $tx eq 'ARRAY';
  return unless defined $tx;
  if (ref $tx eq 'HASH') {
    my $path = $tx->{path} or return;
    return [$path, ($tx->{overlay_color} || $tx->{tint_color} || $path =~ $BIOME_TINTED) ? 1 : 0];
  }
  return [$tx, $tx =~ $BIOME_TINTED ? 1 : 0];
}
sub up { my ($s) = @_; return $s unless ref $s; return $s->{up} // $s->{side} // $s->{north} // (sort values %$s)[0]; }
sub side { my ($s) = @_; return undef unless ref $s; return $s->{side} // $s->{north}; }

# The face a foot stands on: the TOP, as the world draws it. Biome-tinted textures (grass, leaves)
# are grey in the atlas, so for those the pre-coloured "carried" (inventory) version is used.
sub texture_for {
  my ($b) = @_;
  my $world = resolve(up($b->{textures}));
  return $world if $world && !$world->[1];
  my $carried = $b->{carried_textures} ? resolve(up($b->{carried_textures})) : undef;
  return $carried if $carried && !$carried->[1] && $carried->[0] =~ m{^textures/blocks/};
  my $sideTex = resolve(side($b->{textures}));
  return $sideTex if $sideTex && !$sideTex->[1];
  return $world // $carried;
}

# Sound family -> [step event, landing event]. Every event named here exists in the game's sound
# definitions (Mojang's bedrock-samples sound_definitions.json); families without their own use
# the sound the game itself gives them (sounds.json interactive block_sounds), else stone.
my %STEP = map { $_ => 1 } qw(amethyst_block amethyst_cluster ancient_debris azalea azalea_leaves bamboo_wood
  bamboo_wood_hanging_sign basalt big_dripleaf bone_block calcite candle cave_vines chain cherry_leaves cherry_wood
  cherry_wood_hanging_sign chiseled_bookshelf cloth copper copper_bulb copper_grate coral decorated_pot deepslate
  deepslate_bricks dirt_with_roots dripstone_block frog_spawn froglight grass gravel hanging_roots hanging_sign heavy_core
  honey_block iron ladder moss nether_brick nether_gold_ore nether_sprouts nether_wart nether_wood nether_wood_hanging_sign
  netherite netherrack nylium pink_petals pointed_dripstone powder_snow roots sand sculk sculk_catalyst sculk_sensor
  sculk_shrieker sculk_vein shroomlight slime snow soul_sand soul_soil sponge spore_blossom stem stone suspicious_gravel
  suspicious_sand tuff tuff_bricks vines web wet_sponge wood);
my %FALL = map { $_ => 1 } qw(amethyst_block amethyst_cluster ancient_debris azalea azalea_leaves bamboo_wood basalt
  big_dripleaf bone_block calcite cave_vines chain cherry_leaves cherry_wood cloth copper copper_bulb coral deepslate
  deepslate_bricks dirt_with_roots dripstone_block grass gravel hanging_roots honey_block ladder moss nether_brick
  nether_gold_ore nether_sprouts nether_wart nether_wood netherite netherrack nylium pink_petals pointed_dripstone
  powder_snow roots sand sculk_sensor sculk_shrieker shroomlight slime snow soul_sand soul_soil spore_blossom stem
  stone tuff vines wood);
my %FAMILY = (normal => 'stone', glass => 'stone', metal => 'stone', anvil => 'stone', turtle_egg => 'stone',
  terracotta => 'stone', itemframe => 'wood', moss_block => 'moss', moss_carpet => 'moss', mud => 'gravel',
  packed_mud => 'gravel', muddy_mangrove_roots => 'gravel', mud_bricks => 'stone', mangrove_roots => 'wood',
  polished_tuff => 'tuff', shelf => 'wood', fungus => 'nylium', eyeblossom => 'grass', poplar_leaves => 'grass',
  sweet_berry_bush => 'grass', bamboo_sapling => 'grass', lantern => 'chain', scaffolding => 'wood', bamboo => 'wood',
  resin => 'stone', resin_brick => 'stone', cinnabar => 'stone', sulfur => 'stone');
sub sounds {
  my ($family) = @_;
  $family //= 'stone';
  my $base = $family;
  $base =~ s/_(chest|chest_weathered|chest_oxidized|chest_exposed|golem_statue)$// if $family =~ /^copper_/;
  my $f = $STEP{$base} ? $base : ($FAMILY{$base} // 'stone');
  my $step = $STEP{$f} ? "step.$f" : 'step.stone';
  my $fall = $FALL{$f} ? "fall.$f" : 'fall.stone';
  return ($step, $fall);
}

my $translucent = qr{glass|/ice$|frosted_ice|slime|honey_block|portal};
my (%pathOf, @opaque, @clear);
for my $name (sort keys %blocks) {
  my $r = texture_for($blocks{$name}) or next;
  my $path = $r->[0];
  next if $path =~ m{^textures/blocks/(still_|flowing_)?(water|lava)};
  $pathOf{$name} = $path;
}
my %seen = ('textures/blocks/netherite_block' => 1);
for my $p (sort values %pathOf) { next if $seen{$p}++; if ($p =~ $translucent) { push @clear, $p } else { push @opaque, $p } }
my @tex = ('textures/blocks/netherite_block', @opaque, @clear);   # 0 = the netherite spider
my %index; $index{$tex[$_]} = $_ for 0 .. $#tex;
my $firstClear = 1 + scalar @opaque;

# Block ids the game uses today that blocks.json still files under an older name.
my %ALIAS = (grass_block => 'grass', dirt_path => 'grass_path', bricks => 'brick_block', magma_block => 'magma',
  slime_block => 'slime', melon => 'melon_block', terracotta => 'hardened_clay', red_nether_bricks => 'red_nether_brick',
  nether_bricks => 'nether_brick', end_stone_bricks => 'end_bricks', snow_block => 'snow', cobweb => 'web',
  lily_pad => 'waterlily', rooted_dirt => 'dirt_with_roots', waxed_copper_block => 'waxed_copper', note_block => 'noteblock',
  jack_o_lantern => 'lit_pumpkin', flowering_azalea_leaves => 'azalea_leaves_flowered', sugar_cane => 'reeds',
  short_grass => 'tallgrass', stone_bricks => 'stonebrick', mushroom_stem => 'brown_mushroom_block',
  quartz_pillar => 'quartz_block', smooth_stone_slab => 'smooth_stone', cobblestone_wall => 'cobblestone');

print "// GENERATED by devtools/camo-table.pl from the game's own vanilla resource packs - do not edit.\n";
print "// Packs merged: ", scalar(@packs), " ($packs[0] .. $packs[-1]); blocks: ", scalar(keys %pathOf), "; textures: ", scalar(@tex), ".\n";
print "// TEXTURES: leg texture index -> path (0 = netherite; from FIRST_TRANSLUCENT on, drawn blended).\n";
print "// BLOCKS: block id -> [texture index, step sound, landing sound]. ALIASES: today's id -> blocks.json's.\n\n";
print "export const FIRST_TRANSLUCENT = $firstClear;\n\n";
print "// BEGIN TEXTURES\nexport const TEXTURES = [\n";
print "  \"$_\",\n" for @tex;
print "];\n// END TEXTURES\n\n";
print "export const BLOCKS = {\n";
for my $name (sort keys %pathOf) {
  my ($step, $fall) = sounds($blocks{$name}{sound});
  print "  \"$name\": [$index{$pathOf{$name}}, \"$step\", \"$fall\"],\n";
}
print "};\n\nexport const ALIASES = {\n";
for my $k (sort keys %ALIAS) { print "  \"$k\": \"$ALIAS{$k}\",\n" if $pathOf{$ALIAS{$k}} && !$pathOf{$k}; }
print "};\n";
