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
 */
public class InteriorStyle {

    public static Material floorMaterial(String subtype, int variant) {
        if (subtype == null) return Material.SMOOTH_STONE;
        switch (subtype) {
            case "house":
            case "detached":
            case "semidetached_house":
            case "bungalow":
            case "cabin":
            case "hut":
            case "cottage": {
                Material[] variants = {Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.DARK_OAK_PLANKS};
                return variants[Math.abs(variant) % variants.length];
            }
            case "residential":
            case "apartments":
            case "dormitory":
            case "terrace":
            case "hotel":
                return Material.OAK_PLANKS;
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
                return Material.LIGHT_GRAY_CONCRETE;
            case "industrial":
            case "warehouse":
            case "manufacture":
                return Material.GRAY_CONCRETE;
            case "church":
            case "cathedral":
            case "chapel":
            case "mosque":
            case "temple":
                return Material.SMOOTH_STONE;
            default:
                return Material.SMOOTH_STONE;
        }
    }

    // Null means "no rug for this category" (industrial floors, for instance, shouldn't get one).
    public static Material rugMaterial(String subtype, int variant) {
        if (subtype == null) return null;
        Material[] warmCarpets = {Material.RED_CARPET, Material.ORANGE_CARPET, Material.BROWN_CARPET, Material.GREEN_CARPET};
        Material[] coolCarpets = {Material.LIGHT_GRAY_CARPET, Material.CYAN_CARPET, Material.BLUE_CARPET, Material.GRAY_CARPET};
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
                return warmCarpets[Math.abs(variant) % warmCarpets.length];
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
                return coolCarpets[Math.abs(variant) % coolCarpets.length];
            case "church":
            case "cathedral":
            case "chapel":
            case "mosque":
            case "temple":
                return Material.RED_CARPET;
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
                buildResidentialRoom(out, cx, y, cz, worldMaxY, floorIndex, roll);
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

    // Cycles kitchen -> bedroom -> living room per floor, so a multi-storey house or apartment
    // building reads as different rooms going up instead of the same furniture repeated.
    private static void buildResidentialRoom(List<BlockPlacement> out, int cx, int y, int cz, int worldMaxY, int floorIndex, BlockFace facing) {
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
                break;
            default: // living room
                out.add(new BlockPlacement(cx, y, cz, Material.BOOKSHELF));
                out.add(BlockPlacement.facing(cx + 1, y, cz, Material.OAK_STAIRS, facing));
                break;
        }
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
