// What the AI needs to know about single blocks, on the Bedrock Script API.
//
// Java asks a block for its COLLISION SHAPE (empty or not), whether its top face is STURDY, and
// for its fluid state and tags. Bedrock's stable Script API has none of that (Block.isSolid is
// pre-release, and even it calls fences "not solid"), so this classifies by what it does have:
// isAir, isLiquid, isWaterlogged and the typeId. Everything that is not air and not a liquid is
// treated as having collision, EXCEPT a list of vanilla blocks that have none (plants, flowers,
// torches, rails, buttons, signs...). Partial blocks (slabs, stairs, fences, walls, panes,
// carpets) and leaves DO collide but have no sturdy top - which is also what Java says: you
// cannot stand a torch on a fence post, a bottom slab or leaves.
//
// One getBlock per block, cached for the rest of the tick: the chase's door hunt reads a few
// thousand blocks in one go, and native calls are what a Bedrock script tick spends its time on.

const NO_COLLISION = new Set([
  "air", "cave_air", "void_air", "light_block", "structure_void",
  "short_grass", "tall_grass", "grass", "tallgrass", "fern", "large_fern", "deadbush", "dead_bush",
  "seagrass", "tall_seagrass", "kelp", "kelp_plant", "vine", "glow_lichen", "sculk_vein",
  "torch", "soul_torch", "redstone_torch", "unlit_redstone_torch", "redstone_wire",
  "rail", "golden_rail", "detector_rail", "activator_rail", "lever", "tripwire", "trip_wire",
  "tripwire_hook", "reeds", "sugar_cane", "wheat", "carrots", "potatoes", "beetroot",
  "melon_stem", "pumpkin_stem", "sweet_berry_bush", "web", "cobweb", "fire", "soul_fire",
  "snow_layer", "powder_snow", "nether_sprouts", "hanging_roots", "spore_blossom",
  "red_flower", "yellow_flower", "double_plant", "sapling", "dandelion", "poppy", "blue_orchid",
  "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose",
  "torchflower", "sunflower", "lilac", "rose_bush", "peony", "pitcher_plant", "closed_eyeblossom",
  "open_eyeblossom", "wildflowers", "leaf_litter", "bush", "firefly_bush", "short_dry_grass",
  "tall_dry_grass", "frog_spawn", "bubble_column", "brown_mushroom", "red_mushroom",
  "crimson_roots", "warped_roots", "crimson_fungus", "warped_fungus", "end_rod_placeholder",
]);
const NO_COLLISION_SUFFIX = /(_sapling|_button|_pressure_plate|_sign|_banner|_torch|_rail|_tulip|_coral|_coral_fan|_coral_wall_fan|_roots|_fungus|_vines|_vines_plant|_flower|_propagule|_petals)$/;
const DOOR_LIKE = /(door|trapdoor|fence_gate)$/;
const LEAVES = /leaves/;
// Collides, but the top is not a full sturdy face. (Anchored where a loose match would catch a full
// block: "^bed" alone also matched BEDROCK, so the AI never counted bedrock as ground; "chain" caught
// chain_command_block.)
const PARTIAL = /(slab|stairs|fence|_wall|cobblestone_wall|pane|iron_bars|carpet|snow_layer|(^|_)chain$|lantern|candle|ladder|scaffolding|_bed$|^bed$|cake|cauldron|chest|enchanting_table|end_portal_frame|hopper|daylight_detector|stonecutter|bell|anvil|grindstone|lectern|campfire|conduit|skull|_head$|flower_pot|composter|sea_pickle|turtle_egg|sniffer_egg|amethyst_cluster|_bud|pointed_dripstone|big_dripleaf|small_dripleaf|azalea|mangrove_roots|lily_pad|waterlily|cocoa)/;

/** Everything one block can tell the AI. Unloaded blocks come back { loaded: false }. */
export class BlockInfo {
  constructor(loaded, id, air, liquid, water, waterlogged, topSlab) {
    this.loaded = loaded;
    this.id = id;
    this.air = air;
    this.liquid = liquid;                       // a water/lava block itself
    this.fluid = liquid || waterlogged;         // Java's "fluid state is not empty"
    this.water = water || waterlogged;          // Java's FluidTags.WATER
    this.door = loaded && DOOR_LIKE.test(id);
    this.leaves = loaded && LEAVES.test(id);
    this.collision = loaded && !air && !liquid && !NO_COLLISION.has(id) && !NO_COLLISION_SUFFIX.test(id);
    this.sturdyTop = this.collision && !this.leaves && (!PARTIAL.test(id) || topSlab);
  }
}
const UNLOADED = new BlockInfo(false, "", false, false, false, false, false);

export class BlockWorld {
  constructor(dimension) {
    this.dimension = dimension;
    this.cache = new Map();
    this.reads = 0;   // getBlock calls since the counter was last read (for the perf HUD)
    let range = { min: -64, max: 320 };
    try { if (dimension.heightRange) range = dimension.heightRange; } catch (e) {}
    this.minY = range.min;
    this.maxY = range.max;
    this.hasCeiling = dimension.id === "minecraft:nether";
  }

  /** Forget everything: blocks may have changed. Called once per tick. */
  newTick() { this.cache.clear(); }

  cell(x, y, z) {
    const key = `${x},${y},${z}`;
    let info = this.cache.get(key);
    if (info) return info;
    info = UNLOADED;
    if (y >= this.minY && y < this.maxY) {
      try {
        this.reads++;
        const b = this.dimension.getBlock({ x, y, z });
        if (b) {
          const id = (b.typeId || "").replace(/^minecraft:/, "");
          let topSlab = false;
          if (/slab$/.test(id)) {
            try { topSlab = b.permutation.getState("minecraft:vertical_half") === "top"; } catch (e) {}
          }
          let waterlogged = false;
          try { waterlogged = !!b.isWaterlogged; } catch (e) {}
          const liquid = !!b.isLiquid;
          info = new BlockInfo(true, id, !!b.isAir, liquid, liquid && /water/.test(id), waterlogged, topSlab);
        }
      } catch (e) { /* unloaded chunk or outside the world */ }
    }
    this.cache.set(key, info);
    return info;
  }

  /** Java's level.hasChunk: is the column at block (bx, bz) loaded? (Probed at height y.) */
  hasChunk(bx, bz, y) {
    const yy = Math.max(this.minY, Math.min(this.maxY - 1, Math.floor(y)));
    return this.cell(bx, yy, bz).loaded;
  }

  /** Heightmap stand-in: the Y of the topmost non-air block in the column, or null. */
  topY(x, z) {
    try {
      this.reads++;
      const b = this.dimension.getTopmostBlock({ x, z });
      return b ? b.location.y : null;
    } catch (e) {
      return null;
    }
  }
}
