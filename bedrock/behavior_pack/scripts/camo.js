// ACTIVE CAMOUFLAGE for the camo variant (Java v1.1.1, entity/GroundCamo.kt).
//
// Java repaints each leg as the block under its foot and plays that block's own step sound. A
// Bedrock leg is one entity drawing one texture, picked by its int property `arachno:tex` from the
// list in camo_table.js - which is GENERATED from the game's own resource packs (every vanilla
// block's top-face texture and its sound family; devtools/gen-camo.sh), so every block the game
// knows has its own look and its own footsteps, as in Java. A block the table doesn't know (one
// added by a later game version, before the table is regenerated) falls back to a close relative
// - its base block for slabs/stairs/walls, the unwaxed copper - and finally to stone.

import { BLOCKS, ALIASES, TEXTURES } from "./camo_table.js";

export const NETHERITE_TEX = 0;
const STONE = BLOCKS.stone || [TEXTURES.indexOf("textures/blocks/stone"), "step.stone", "fall.stone"];
const MOSS = BLOCKS.moss_block || STONE;
export const CAMO_SPAWN_TEX = MOSS[0];   // Java's moss placeholder until each leg first lands

// Snow a foot sinks into rather than stands on (no collision - see blocks.js) is still the surface
// you SEE: snowy biomes are carpeted in thin snow layers over grass, and a spider wearing the grass
// underneath stood out green against the white. (Java skips one-layer snow the same way; Bedrock
// counts every layer as cover.)
const SNOW_COVER = new Set(["snow_layer", "powder_snow"]);

const cache = new Map();
export const unknownBlocks = new Set();   // ids that fell back to stone (for the developer check)

function lookup(id) {
  if (BLOCKS[id]) return BLOCKS[id];
  if (ALIASES[id] && BLOCKS[ALIASES[id]]) return BLOCKS[ALIASES[id]];
  const tries = [];
  if (id.startsWith("waxed_")) tries.push(id.slice(6));
  tries.push(`${id}_block`, id.replace(/_block$/, ""));
  const base = id.replace(/_(double_slab|slab|stairs|wall|fence_gate|fence|pressure_plate|button|trapdoor|door)$/, "");
  if (base !== id) tries.push(base, `${base}s`, `${base}_block`, `${base}_planks`, base.replace(/_brick$/, "_bricks"));
  for (const t of tries) {
    if (BLOCKS[t]) return BLOCKS[t];
    if (ALIASES[t] && BLOCKS[ALIASES[t]]) return BLOCKS[ALIASES[t]];
  }
  return null;
}

/** { tex, step, fall } for a block id (no "minecraft:"): the leg texture index and the block's
 *  own step and landing sound events. */
export function camoFor(id) {
  let hit = cache.get(id);
  if (hit) return hit;
  let row = lookup(id);
  if (!row) { row = STONE; unknownBlocks.add(id); }
  hit = { tex: row[0], step: row[1], fall: row[2] };
  cache.set(id, hit);
  return hit;
}

/** GroundCamo.blockUnder: the first block with collision at or just below a point (skipping grass
 *  tufts and other decoration - a foot rests on the dirt, not the tuft), or null over air. Snow on
 *  top of that block wins: a foot a hair below the grass top, or brushing the side of the next
 *  block up a snowy slope, starts its scan inside the grass and would flash green for a frame. */
export function blockUnder(blocks, pos, depth = 3) {
  const x = Math.floor(pos.x), z = Math.floor(pos.z);
  const startY = Math.floor(pos.y + 0.01);
  for (let dy = 0; dy <= depth; dy++) {
    const y = startY - dy;
    const c = blocks.cell(x, y, z);
    if (c.air || !c.loaded) continue;
    if (SNOW_COVER.has(c.id)) return c.id;
    if (c.collision) {
      const above = blocks.cell(x, y + 1, z);
      return SNOW_COVER.has(above.id) ? above.id : c.id;
    }
  }
  return null;
}
