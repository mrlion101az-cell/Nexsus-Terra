package com.nexusuniverse.terra.generation;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

/**
 * Basic building interiors: a structural floor slab at every storey (so multi-storey buildings
 * are actually divided into floors, not one hollow shaft), an optional carpet rug on top of it,
 * and one small, safe furniture cluster per floor picked from the building's category.
 *
 * This deliberately does NOT attempt real room subdivision -- interior walls, doors, and a
 * proper room graph are a much bigger problem, and getting it wrong (a wall through a doorway,
 * furniture clipping a window) would look worse than not attempting it. Furniture is placed once
 * near the centre of each floor's footprint instead of scattered through several rooms, which
 * keeps it clear of exterior walls for any reasonably-shaped building without needing to know
 * where interior walls would even go. We don't know what these buildings actually look like
 * inside from OSM data alone -- this is a reasonable, generic stand-in, not a claim of accuracy.
 *
 * Floors and rugs use a genuine two-tone checker pattern rather than one flat material -- that
 * single change does more for "this looks like someone furnished it" than almost anything else
 * here, and it's cheap and safe to do (just alternating two known-good materials by position).
 */
public class InteriorStyle {

    public static Material floorMaterial(String subtype, int variant) {
        return floorPair(subtype, variant)[0];
    }

    public static Material floorMaterialSecondary(String subtype, int variant) {
        return floorPair(subtype, variant)[1];
    }

    private static Material[] floorPair(String subtype, int variant) {
        if (subtype == null) return new Material[]{Material.SMOOTH_STONE, Material.POLISHED_ANDESITE};
        switch (subtype) {
            case "house":
            case "detached":
            case "semidetached_house":
            case "bungalow":
            case "cabin":
            case "hut":
            case "cottage": {
                Material[][] pairs = {
                        {Material.OAK_PLANKS, Material.DARK_OAK_PLANKS},
                        {Material.SPRUCE_PLANKS, Material.STRIPPED_SPRUCE_LOG},
                        {Material.DARK_OAK_PLANKS, Material.OAK_PLANKS}
                };
                return pairs[Math.abs(variant) % pairs.length];
            }
            case "residential":
            case "apartments":
            case "dormitory":
            case "terrace":
            case "hotel":
                return new Material[]{Material.OAK_PLANKS, Material.DARK_OAK_PLANKS};
            case "commercial":
            case "retail":
            case "supermarket":
            case "office":
            case "civic":
            case "government":
            case "school":
            case "university":
            case "hospital":
            case "public":
                return new Material[]{Material.LIGHT_GRAY_CONCRETE, Material.WHITE_CONCRETE};
            case "industrial":
            case "warehouse":
            case "manufacture":
                return new Material[]{Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE};
            case "church":
            case "cathedral":
            case "chapel":
            case "mosque":
            case "temple":
                return new Material[]{Material.SMOOTH_STONE, Material.POLISHED_ANDESITE};
            default:
                return new Material[]{Material.SMOOTH_STONE, Material.POLISHED_ANDESITE};
        }
    }

    // Null means "no rug for this category" (industrial floors, for instance, shouldn't get one).
    public static Material rugMaterial(String subtype, int variant) {
        Material[] pair = rugPair(subtype, variant);
        return pair == null ? null : pair[0];
    }

    public static Material rugMaterialSecondary(String subtype, int variant) {
        Material[] pair = rugPair(subtype, variant);
        return pair == null ? null : pair[1];
    }

    private static Material[] rugPair(String subtype, int variant) {
        if (subtype == null) return null;
        // Each pair is two colours that read as a deliberate two-tone rug pattern (like a real
        // patterned area rug) rather than two random carpet colours that happen to be adjacent.
        Material[][] warmPairs = {
                {Material.ORANGE_CARPET, Material.LIGHT_BLUE_CARPET},
                {Material.RED_CARPET, Material.WHITE_CARPET},
                {Material.BROWN_CARPET, Material.ORANGE_CARPET},
                {Material.GREEN_CARPET, Material.WHITE_CARPET}
        };
        Material[][] coolPairs = {
                {Material.LIGHT_GRAY_CARPET, Material.CYAN_CARPET},
                {Material.GRAY_CARPET, Material.LIGHT_GRAY_CARPET},
                {Material.BLUE_CARPET, Material.WHITE_CARPET},
                {Material.CYAN_CARPET, Material.GRAY_CARPET}
        };
        switch (subtype) {
            case "house":
            case "detached":
            case "semidetached_house":
            case "bungalow":
            case "cabin":
            case "hut":
            case "cottage":
            case "residential":
            case "apartments":
            case "dormitory":
            case "terrace":
            case "hotel":
                return warmPairs[Math.abs(variant) % warmPairs.length];
            case "commercial":
            case "retail":
            case "supermarket":
            case "office":
            case "civic":
            case "government":
            case "school":
            case "university":
            case "hospital":
            case "public":
                return coolPairs[Math.abs(variant) % coolPairs.length];
            case "church":
            case "cathedral":
            case "chapel":
            case "mosque":
            case "temple":
                return new Material[]{Material.RED_CARPET, Material.PURPLE_CARPET};
            default:
                return null;
        }
    }

    /**
     * One furniture cluster for a single floor, centred at (cx, floorY, cz) -- floorY is the
     * walkable surface, so everything here places starting at floorY + 1. floorIndex lets a
     * multi-storey residential building cycle through different "room" flavours per floor
     * (kitchen downstairs, bedroom upstairs) instead of repeating the exact same furniture on
     * every level.
     */
    public static List<BlockPlacement> furniture(int cx, int floorY, int cz, String subtype, int floorIndex, int worldMaxY, int variant) {
        List<BlockPlacement> out = new ArrayList<>();
        int y = floorY + 1;
        if (y >= worldMaxY - 1 || subtype == null) {
            return out;
        }
        BlockFace[] cardinals = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        BlockFace roll = cardinals[Math.abs(variant) % cardinals.length];

        switch (subtype) {
            case "house":
            case "detached":
            case "semidetached_house":
            case "bungalow":
            case "cabin":
            case "hut":
            case "cottage":
            case "residential":
            case "apartments":
            case "dormitory":
            case "terrace":
            case "hotel":
                buildResidentialRoom(out, cx, y, cz, worldMaxY, floorIndex, roll, variant);
                break;
            case "commercial":
            case "retail":
            case "supermarket":
                buildStockroom(out, cx, y, cz, worldMaxY);
                break;
            case "office":
            case "civic":
            case "government":
            case "school":
            case "university":
            case "hospital":
            case "public":
                buildOffice(out, cx, y, cz, worldMaxY, roll);
                break;
            case "industrial":
            case "warehouse":
            case "manufacture":
                buildWorkshop(out, cx, y, cz, worldMaxY);
                break;
            case "church":
            case "cathedral":
            case "chapel":
            case "mosque":
            case "temple":
                buildAltar(out, cx, y, cz, worldMaxY);
                break;
            default:
                out.add(new BlockPlacement(cx, y, cz, Material.BOOKSHELF));
                break;
        }
        return out;
    }

    /**
     * A light fixture hanging from the ceiling on a short chain, rather than just sitting on a
     * surface -- one of the single biggest "someone actually decorated this" cues in a real
     * interior, and cheap to add: a chain (or two, side by side for a slightly wider fixture)
     * dropping from the ceiling to a lantern/lit block. ceilingY is the solid block above the
     * room, so the fixture is built downward from ceilingY - 1.
     */
    public static List<BlockPlacement> hangingLight(int cx, int ceilingY, int cz, int worldMaxY, int variant) {
        List<BlockPlacement> out = new ArrayList<>();
        int chainY = ceilingY - 1;
        if (chainY <= 0 || chainY >= worldMaxY - 1) {
            return out;
        }
        boolean wide = Math.abs(variant) % 3 == 0;
        Material light = Math.abs(variant) % 2 == 0 ? Material.SHROOMLIGHT : Material.GLOWSTONE;
        out.add(new BlockPlacement(cx, chainY, cz, Material.CHAIN));
        out.add(new BlockPlacement(cx, chainY - 1, cz, light));
        if (wide && chainY - 1 > 0) {
            out.add(new BlockPlacement(cx + 1, chainY, cz, Material.CHAIN));
            out.add(new BlockPlacement(cx + 1, chainY - 1, cz, light));
        }
        return out;
    }

    // A potted plant on a small pedestal -- a stair or slab base with leaves above it, standing
    // in for the kind of tall planter seen flanking a lobby or hallway rather than a single
    // flower-pot block, which reads as far too small next to full-height furniture.
    public static List<BlockPlacement> pottedPlantPedestal(int x, int y, int z, int worldMaxY, int variant) {
        List<BlockPlacement> out = new ArrayList<>();
        if (y + 2 >= worldMaxY - 1) {
            return out;
        }
        Material leaf = Math.abs(variant) % 2 == 0 ? Material.OAK_LEAVES : Material.SPRUCE_LEAVES;
        out.add(new BlockPlacement(x, y, z, Material.POLISHED_ANDESITE_SLAB));
        out.add(new BlockPlacement(x, y + 1, z, leaf));
        out.add(new BlockPlacement(x, y + 2, z, leaf));
        return out;
    }

    // Cycles kitchen -> bedroom -> living room per floor, so a multi-storey house or apartment
    // building reads as different rooms going up instead of the same furniture repeated.
    private static void buildResidentialRoom(List<BlockPlacement> out, int cx, int y, int cz, int worldMaxY, int floorIndex, BlockFace facing, int variant) {
        int roomType = Math.abs(floorIndex) % 3;
        switch (roomType) {
            case 0: // kitchen
                out.add(new BlockPlacement(cx - 1, y, cz, Material.SMOKER));
                out.add(new BlockPlacement(cx, y, cz, Material.CRAFTING_TABLE));
                out.add(new BlockPlacement(cx + 1, y, cz, Material.BARREL));
                break;
            case 1: // bedroom -- a real 2-block bed, correctly oriented
                addBed(out, cx, y, cz, worldMaxY, facing);
                out.add(new BlockPlacement(cx + 1, y, cz, Material.CHEST));
                out.addAll(pottedPlantPedestal(cx - 1, y, cz, worldMaxY, variant));
                break;
            default: // living room -- a real 3-piece couch (two facing stairs + a slab seat
                // between them) around a small coffee table, not a single stray stair block
                out.addAll(buildCouch(cx, y, cz, facing));
                break;
        }
    }

    // Three-piece couch: two stair "arms" flanking a slab seat, all facing the same way, with a
    // low coffee table (slab) a step in front of it.
    private static List<BlockPlacement> buildCouch(int cx, int y, int cz, BlockFace facing) {
        List<BlockPlacement> out = new ArrayList<>();
        int[] along = offsetFor(rotate90(facing));
        int[] forward = offsetFor(facing);
        out.add(BlockPlacement.facing(cx - along[0], y, cz - along[1], Material.OAK_STAIRS, facing));
        out.add(new BlockPlacement(cx, y, cz, Material.OAK_SLAB));
        out.add(BlockPlacement.facing(cx + along[0], y, cz + along[1], Material.OAK_STAIRS, facing));
        out.add(new BlockPlacement(cx + forward[0] * 2, y, cz + forward[1] * 2, Material.SMOOTH_STONE_SLAB));
        return out;
    }

    private static BlockFace rotate90(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    private static void addBed(List<BlockPlacement> out, int cx, int y, int cz, int worldMaxY, BlockFace facing) {
        if (y >= worldMaxY - 1) return;
        int[] offset = offsetFor(facing);
        int headX = cx + offset[0];
        int headZ = cz + offset[1];
        out.add(BlockPlacement.bedHalf(cx, y, cz, Material.RED_BED, facing, false));
        out.add(BlockPlacement.bedHalf(headX, y, headZ, Material.RED_BED, facing, true));
    }

    private static int[] offsetFor(BlockFace facing) {
        return switch (facing) {
            case NORTH -> new int[]{0, -1};
            case SOUTH -> new int[]{0, 1};
            case EAST -> new int[]{1, 0};
            case WEST -> new int[]{-1, 0};
            default -> new int[]{0, 1};
        };
    }

    private static void buildStockroom(List<BlockPlacement> out, int cx, int y, int cz, int worldMaxY) {
        out.add(new BlockPlacement(cx - 1, y, cz, Material.BARREL));
        out.add(new BlockPlacement(cx, y, cz, Material.CHEST));
        out.add(new BlockPlacement(cx + 1, y, cz, Material.BARREL));
        if (y + 1 < worldMaxY - 1) {
            out.add(new BlockPlacement(cx, y + 1, cz, Material.BOOKSHELF));
        }
    }

    private static void buildOffice(List<BlockPlacement> out, int cx, int y, int cz, int worldMaxY, BlockFace facing) {
        out.add(BlockPlacement.facing(cx, y, cz, Material.LECTERN, facing));
        out.add(new BlockPlacement(cx + 1, y, cz, Material.BOOKSHELF));
        out.add(new BlockPlacement(cx - 1, y, cz, Material.BOOKSHELF));
    }

    private static void buildWorkshop(List<BlockPlacement> out, int cx, int y, int cz, int worldMaxY) {
        out.add(new BlockPlacement(cx, y, cz, Material.SMITHING_TABLE));
        out.add(new BlockPlacement(cx + 1, y, cz, Material.FURNACE));
        out.add(new BlockPlacement(cx - 1, y, cz, Material.BARREL));
    }

    private static void buildAltar(List<BlockPlacement> out, int cx, int y, int cz, int worldMaxY) {
        out.add(new BlockPlacement(cx, y, cz, Material.LECTERN));
        if (y + 1 < worldMaxY - 1) {
            out.add(new BlockPlacement(cx, y + 1, cz, Material.CANDLE));
        }
    }
}
