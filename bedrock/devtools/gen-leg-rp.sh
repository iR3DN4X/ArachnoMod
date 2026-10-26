#!/usr/bin/env bash
# Regenerates resource_pack/animations/leg.animation.json and resource_pack/entity/leg.entity.json.
# Run from anywhere:  bash devtools/gen-leg-rp.sh   (don't hand-edit those two files)
#
# The leg model draws every bone straight from the entity's properties (the round-4 picture, the
# one confirmed in game) PLUS a per-value offset:
#
#     drawn = property + offset
#
# The client glides the four JOINTS (x0..z3) over one tick - Java's 1-tick display interpolation:
# when a joint property changes, its offset jumps to (old drawn - new property) so the picture does
# not move that frame, then shrinks linearly to 0 by the end of the tick. Each segment is then aimed
# along its two glided joints (yaw/pitch/length offsets = glided direction - the server's), so the
# segments always meet. Gliding the joints, not the angles, matters: the near-vertical foot segment
# swings its Euler yaw/roll by ~90 degrees for a tiny change in direction, and gliding those tears
# the joint open. Roll (the square cross-section's twist) is drawn unglided.
#
# EVERY variable is declared in `initialize` (runs once when the client creates the entity), and
# every variable the model reads is guarded with `?? 0`. In Bedrock, reading a variable that was
# never assigned STOPS the expression ("your code will stop with a content error"), so its
# assignment never happens and the next frame stops too - rounds 5, 6 and 7 drew NO legs because
# of this (the model read unset offsets, collapsing every segment to length 0).
#
# Offsets are only ever ADDED, so if the client script misbehaves in any way they are 0 and the legs
# still draw exactly from the properties. A leg entity is never moved (a re-park spawns replacement
# legs and retires the old ones, see render.js), so only an entity's first frame with data snaps.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RP="$ROOT/resource_pack"
SEGS=(0 1 2)
JOINTS=(0 1 2 3)
AX=(x y z)

prop() { echo "q.property('arachno:$1')"; }
g() { echo "($(prop $1) + v.o_$1)"; }   # a glided joint coordinate

# ---------------------------------------------------------------- pre_animation statements
S=()
S+=("v.dt = (q.delta_time > 0) ? q.delta_time : 0.05;")
S+=("v.since = v.since + v.dt;")
# a leg entity never moves (re-parks spawn replacements), so only its first frame WITH DATA snaps:
# a new entity is first drawn with default properties (all 0) and its data follows a frame later -
# gliding from that collapsed pose would sweep the new legs out from the body
S+=("v.snap = v.ready ? 0 : 1;")
sum=""
for j in "${JOINTS[@]}"; do for c in "${AX[@]}"; do
  S+=("v.d_$c$j = $(prop $c$j) - v.l_$c$j;")
  sum+="${sum:+ + }math.abs(v.d_$c$j)"
done; done
S+=("v.chg = ($sum > 0.00001) ? 1 : 0;")
for j in "${JOINTS[@]}"; do for c in "${AX[@]}"; do
  S+=("v.s_$c$j = v.snap ? 0 : (v.chg ? v.o_$c$j - v.d_$c$j : v.s_$c$j);")
done; done
# ADAPTIVE GLIDE LENGTH: updates do not always arrive every 1/20 s - an in-game video showed them
# coming in uneven bursts, so a fixed 1-tick glide finished early and the legs sat still until the
# next update (stop-start "dragging"). v.ui tracks the real time between updates (smoothed; gaps
# over 0.15 s - the spider standing still - are ignored) and each glide lasts that long, so the
# legs are always moving toward the latest pose. At an even 20 Hz it is exactly Java's 1 tick.
S+=("v.ui = (v.chg && v.since < 0.15) ? v.ui * 0.5 + math.clamp(v.since, 0.03, 0.15) * 0.5 : v.ui;")
S+=("v.since = v.chg ? 0 : v.since;")
S+=("v.t = v.chg ? v.dt : v.t + v.dt;")   # count THIS frame: the glide ends exactly on the new pose
S+=("v.al = math.clamp(v.t / math.clamp(v.ui, 0.03, 0.15), 0, 1);")
# THE HOP (round 32, render.js stepHop): a step the engine began and finished inside one tick slid
# the foot along the ground. The server sends how high to arc it (arachno:hop); the foot (joint 3)
# rises and falls on a parabola over the glide - 0 at both ends, so the leg still lands exactly on
# the server's pose - and the knee (joint 2) by half as much, so the shin keeps its length.
S+=("v.hy = v.snap ? 0 : $(prop hop) * 4 * v.al * (1 - v.al);")
for j in "${JOINTS[@]}"; do for c in "${AX[@]}"; do
  lift=""
  [ "$c$j" = "y3" ] && lift=" + v.hy"
  [ "$c$j" = "y2" ] && lift=" + v.hy * 0.5"
  S+=("v.o_$c$j = v.s_$c$j * (1 - v.al)$lift;")
  S+=("v.l_$c$j = $(prop $c$j);")
done; done
for s in "${SEGS[@]}"; do
  e=$((s + 1))
  S+=("v.gx = $(g x$e) - $(g x$s);")
  S+=("v.gy = $(g y$e) - $(g y$s);")
  S+=("v.gz = $(g z$e) - $(g z$s);")
  S+=("v.gh = math.sqrt(v.gx * v.gx + v.gz * v.gz);")
  S+=("v.gl = math.sqrt(v.gh * v.gh + v.gy * v.gy);")
  # a segment of (almost) zero length has no direction: keep the server's angles
  S+=("v.o_a$s = (v.gl > 0.0001) ? math.atan2(v.gx, v.gz) - $(prop a$s) : 0;")
  S+=("v.o_a$s = v.o_a$s - 360 * math.round(v.o_a$s / 360);")
  S+=("v.o_p$s = (v.gl > 0.0001) ? math.atan2(-v.gy, v.gh) - $(prop p$s) : 0;")
  S+=("v.o_l$s = ($(prop l$s) > 0) ? v.gl - $(prop l$s) : 0;")
done
S+=("v.ready = ($(prop t0) > 0) ? 1 : 0;")

# every variable assigned above starts at 0 (sorted, de-duplicated)
mapfile -t VARS < <(printf '%s\n' "${S[@]}" | grep -o '^v\.[a-z_0-9]* =' | sed 's/ =$//' | sort -u)
I=()
for var in "${VARS[@]}"; do
  if [ "$var" = "v.ui" ]; then I+=("$var = 0.05;"); else I+=("$var = 0;"); fi   # glide length starts at 1 tick
done

emit() {   # emit NAME ARRAY...: a JSON string array
  local name="$1"; shift; local arr=("$@"); local count=${#arr[@]}
  printf '        "%s": [\n' "$name"
  for i in "${!arr[@]}"; do
    local sep=","; [ "$i" -eq $((count - 1)) ] && sep=""
    printf '          "%s"%s\n' "${arr[$i]}" "$sep"
  done
  printf '        ],\n'
}

# ---------------------------------------------------------------- textures (camo_table.js is the source)
# Index i of camo_table.js TEXTURES = texture "t<i>"; the render controller picks one per leg with
# the int property arachno:tex (0 = netherite), and draws indices from FIRST_TRANSLUCENT on blended
# (glass, ice, slime, honey). camo_table.js itself is generated by devtools/gen-camo.sh.
TABLE="$ROOT/behavior_pack/scripts/camo_table.js"
mapfile -t TEX < <(sed -n '/BEGIN TEXTURES/,/END TEXTURES/p' "$TABLE" | grep -o '"textures/[^"]*"' | tr -d '"')
[ "${#TEX[@]}" -gt 1 ] || { echo "no textures found in camo_table.js" >&2; exit 1; }
FIRST_CLEAR=$(grep -o 'FIRST_TRANSLUCENT = [0-9]*' "$TABLE" | grep -o '[0-9]*$')
[ -n "$FIRST_CLEAR" ] || { echo "no FIRST_TRANSLUCENT in camo_table.js" >&2; exit 1; }
TEXJSON='"default": "textures/blocks/netherite_block"'
SKINS=""
for i in "${!TEX[@]}"; do
  TEXJSON+=", \"t$i\": \"${TEX[$i]}\""
  SKINS+="${SKINS:+, }\"Texture.t$i\""
done

{
cat <<'EOF'
{
  "format_version": "1.10.0",
  "minecraft:client_entity": {
    "description": {
      "identifier": "arachno:leg",
      "materials": { "default": "entity_alphatest", "blend": "entity_alphablend" },
EOF
printf '      "textures": { %s },
' "$TEXJSON"
cat <<'EOF'
      "geometry": { "default": "geometry.arachno_leg" },
      "animations": { "pose": "animation.arachno_leg.pose" },
      "scripts": {
EOF
emit initialize "${I[@]}"
emit pre_animation "${S[@]}"
cat <<'EOF'
        "animate": [ "pose" ]
      },
      "render_controllers": [ "controller.render.arachno_leg" ]
    }
  }
}
EOF
} > "$RP/entity/leg.entity.json"

# ---------------------------------------------------------------- animation
v() { echo "($(prop $1) + (v.o_$1 ?? 0))"; }
{
cat <<'EOF'
{
  "format_version": "1.8.0",
  "animations": {
    "animation.arachno_leg.pose": {
      "loop": true,
      "bones": {
        "root": { "rotation": [0, "180 - q.body_y_rotation", 0] },
EOF
for s in "${SEGS[@]}"; do
  last=","; [ "$s" -eq 2 ] && last=""
  cat <<EOF

        "off$s": { "position": ["-16 * $(v x$s)", "16 * $(v y$s)", "16 * $(v z$s)"] },
        "yaw$s": { "rotation": [0, "-$(v a$s)", 0] },
        "pitch$s": { "rotation": ["-$(v p$s)", 0, 0] },
        "roll$s": { "rotation": [0, 0, "$(prop r$s)"] },
        "seg$s": { "scale": ["$(prop t$s)", "$(prop t$s)", "$(v l$s)"] }$last
EOF
done
cat <<'EOF'
      }
    }
  }
}
EOF
} > "$RP/animations/leg.animation.json"

# ---------------------------------------------------------------- render controller: one texture per leg
cat > "$RP/render_controllers/leg.render_controllers.json" <<EOF
{
  "format_version": "1.10.0",
  "render_controllers": {
    "controller.render.arachno_leg": {
      "arrays": {
        "textures": { "Array.skins": [ $SKINS ] },
        "materials": { "Array.mats": [ "Material.default", "Material.blend" ] }
      },
      "geometry": "Geometry.default",
      "materials": [ { "*": "Array.mats[q.property('arachno:tex') >= $FIRST_CLEAR]" } ],
      "textures": [ "Array.skins[q.property('arachno:tex')]" ]
    }
  }
}
EOF

echo "initialize: ${#I[@]} variables, pre_animation: ${#S[@]} statements"
