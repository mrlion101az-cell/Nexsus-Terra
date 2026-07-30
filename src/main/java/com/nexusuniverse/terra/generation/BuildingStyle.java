package com.nexusuniverse.terra.generation;

import com.nexusuniverse.terra.geo.OsmFeature;
import org.bukkit.Material;

/**
 * Turns an OSM building's tags into concrete generation parameters.
 * Before v0.1.4 every building used the same fixed 6-block wall height
 * and the same STONE_BRICKS material regardless of what it actually
 * was in the real world, which is why a whole city block rendered as
 * one indistinguishable grey mass (see the v0.1.3 screenshot).
 */
public class BuildingStyle {

    private static final int MIN_WALL_HEIGHT = 3;
    private static final int MAX_WALL_HEIGHT = 45;

    public final Material wallMaterial;
    public final Material roofMaterial;
    public final int wallHeight;

    private BuildingStyle(Material wallMaterial, Material roofMaterial, int wallHeight) {
        this.wallMaterial = wallMaterial;
        this.roofMaterial = roofMaterial;
        this.wallHeight = wallHeight;
    }

    public static BuildingStyle forFeature(OsmFeature feature, int defaultWallHeight, int blocksPerLevel) {
        return new BuildingStyle(
                wallMaterialFor(feature),
                roofMaterialFor(feature),
                heightFor(feature, defaultWallHeight, blocksPerLevel));
    }

    private static int heightFor(OsmFeature feature, int defaultWallHeight, int blocksPerLevel) {
        // Prefer an explicit real-world height in meters (~1 block per meter
        // at this projection's scale) over building:levels, over the flat default.
        String heightTag = feature.tag("height", null);
        if (heightTag != null) {
            Double meters = parseLeadingNumber(heightTag);
            if (meters != null) {
                return clampHeight((int) Math.round(meters));
            }
        }

        String levelsTag = feature.tag("building:levels", null);
        if (levelsTag != null) {
            Double levels = parseLeadingNumber(levelsTag);
            if (levels != null) {
                return clampHeight((int) Math.round(levels * blocksPerLevel));
            }
        }

        return clampHeight(defaultWallHeight);
    }

    private static int clampHeight(int height) {
        return Math.max(MIN_WALL_HEIGHT, Math.min(MAX_WALL_HEIGHT, height));
    }

    /** OSM height/levels tags are sometimes "12.5 m" or "3;4" (multiple values) - grab the first number. */
    private static Double parseLeadingNumber(String raw) {
        StringBuilder digits = new StringBuilder();
        boolean seenDigit = false;
        for (char c : raw.trim().toCharArray()) {
            if (Character.isDigit(c) || c == '.') {
                digits.append(c);
                seenDigit = true;
            } else if (seenDigit) {
                break;
            }
        }
        if (digits.isEmpty()) return null;
        try {
            return Double.parseDouble(digits.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Material wallMaterialFor(OsmFeature feature) {
        String explicit = feature.tag("building:material", null);
        if (explicit != null) {
            Material fromTag = switch (explicit) {
                case "brick" -> Material.BRICKS;
                case "wood", "timber_framing" -> Material.OAK_PLANKS;
                case "concrete" -> Material.LIGHT_GRAY_CONCRETE;
                case "glass" -> Material.WHITE_STAINED_GLASS;
                case "stone" -> Material.STONE_BRICKS;
                default -> null;
            };
            if (fromTag != null) return fromTag;
        }

        return switch (feature.subtype()) {
            case "house", "detached", "semidetached_house", "bungalow", "cabin" -> Material.MUD_BRICKS;
            case "residential", "apartments", "dormitory", "terrace" -> Material.BRICKS;
            case "commercial", "retail", "office", "supermarket" -> Material.SMOOTH_QUARTZ;
            case "industrial", "warehouse", "manufacture" -> Material.COBBLED_DEEPSLATE;
            case "garage", "garages", "shed", "hut" -> Material.STRIPPED_OAK_WOOD;
            case "church", "cathedral", "chapel", "mosque", "temple" -> Material.CALCITE;
            case "school", "university", "hospital", "public", "civic", "government" -> Material.SMOOTH_SANDSTONE;
            default -> Material.STONE_BRICKS;
        };
    }

    private static Material roofMaterialFor(OsmFeature feature) {
        String roofColor = feature.tag("roof:colour", null);
        if (roofColor != null) {
            Material fromColor = switch (roofColor.toLowerCase()) {
                case "red", "#ff0000" -> Material.RED_TERRACOTTA;
                case "brown" -> Material.BROWN_TERRACOTTA;
                case "grey", "gray" -> Material.GRAY_CONCRETE;
                case "black" -> Material.BLACK_CONCRETE;
                default -> null;
            };
            if (fromColor != null) return fromColor;
        }

        return switch (feature.subtype()) {
            case "house", "detached", "semidetached_house", "bungalow", "cabin" -> Material.RED_TERRACOTTA;
            case "residential", "apartments", "dormitory", "terrace" -> Material.BROWN_TERRACOTTA;
            case "commercial", "retail", "office", "supermarket" -> Material.LIGHT_GRAY_CONCRETE;
            case "industrial", "warehouse", "manufacture" -> Material.GRAY_CONCRETE;
            case "garage", "garages", "shed", "hut" -> Material.STRIPPED_OAK_WOOD;
            case "church", "cathedral", "chapel", "mosque", "temple" -> Material.CUT_COPPER;
            default -> Material.STONE_BRICK_SLAB;
        };
    }
}
