package com.nexusuniverse.terra.generation;

import com.nexusuniverse.terra.geo.OsmFeature;
import org.bukkit.Material;

/**
 * Turns an OSM building's tags into concrete generation parameters.
 *
 * v0.1.5: MAX_WALL_HEIGHT was 45, which in a real downtown (Manhattan,
 * where buildings are commonly 100-180m) meant essentially every
 * building clamped to the same 45-block ceiling -- so the "real
 * heights" feature added in v0.1.4 produced a uniform skyline anyway.
 * Raised substantially, with the actual ceiling now enforced against
 * the world's build limit at generation time instead of guessed here.
 *
 * Also added: storey height, a trim material used to band each floor
 * line, and a separate window material per building type, all of which
 * exist to give walls visible internal structure rather than one flat
 * expanse of a single block.
 */
public class BuildingStyle {

    private static final int MIN_WALL_HEIGHT = 4;
    private static final int MAX_WALL_HEIGHT = 140;

    public final Material wallMaterial;
    public final Material trimMaterial;
    public final Material windowMaterial;
    public final Material roofMaterial;
    public final int wallHeight;
    public final int storeyHeight;
    /** Ground floor is a glazed shopfront rather than solid wall. */
    public final boolean storefront;
    /** Gabled roof instead of a flat cap. */
    public final boolean pitchedRoof;

    private BuildingStyle(Material wallMaterial, Material trimMaterial, Material windowMaterial,
                           Material roofMaterial, int wallHeight, int storeyHeight,
                           boolean storefront, boolean pitchedRoof) {
        this.wallMaterial = wallMaterial;
        this.trimMaterial = trimMaterial;
        this.windowMaterial = windowMaterial;
        this.roofMaterial = roofMaterial;
        this.wallHeight = wallHeight;
        this.storeyHeight = storeyHeight;
        this.storefront = storefront;
        this.pitchedRoof = pitchedRoof;
    }

    public static BuildingStyle forFeature(OsmFeature feature, int defaultWallHeight, int blocksPerLevel, int maxAllowedHeight) {
        int storeyHeight = storeyHeightFor(feature, blocksPerLevel);
        int wallHeight = heightFor(feature, defaultWallHeight, storeyHeight, maxAllowedHeight);
        return new BuildingStyle(
                wallMaterialFor(feature),
                trimMaterialFor(feature),
                windowMaterialFor(feature),
                roofMaterialFor(feature),
                wallHeight,
                storeyHeight,
                storefrontFor(feature),
                pitchedRoofFor(feature, wallHeight));
    }

    private static boolean storefrontFor(OsmFeature feature) {
        return switch (feature.subtype()) {
            case "commercial", "retail", "supermarket", "office", "hotel", "restaurant", "cafe" -> true;
            default -> false;
        };
    }

    /**
     * Pitched roofs suit houses and other small low buildings. A tall
     * tower with a gable on top looks absurd, and OSM's own roof:shape
     * tag wins outright when it's present.
     */
    private static boolean pitchedRoofFor(OsmFeature feature, int wallHeight) {
        String shape = feature.tag("roof:shape", null);
        if (shape != null) {
            return switch (shape) {
                case "gabled", "hipped", "pitched", "gambrel", "half-hipped", "round" -> true;
                case "flat" -> false;
                default -> false;
            };
        }

        if (wallHeight > 20) return false;

        return switch (feature.subtype()) {
            case "house", "detached", "semidetached_house", "bungalow", "cabin",
                 "terrace", "hut", "shed", "garage", "garages", "farm", "barn" -> true;
            case "church", "cathedral", "chapel", "temple" -> true;
            default -> false;
        };
    }

    /** Commercial/office floors are taller than residential ones in reality. */
    private static int storeyHeightFor(OsmFeature feature, int blocksPerLevel) {
        return switch (feature.subtype()) {
            case "commercial", "retail", "office", "supermarket", "industrial", "warehouse" -> 5;
            case "church", "cathedral", "chapel", "mosque", "temple" -> 6;
            default -> Math.max(3, blocksPerLevel);
        };
    }

    private static int heightFor(OsmFeature feature, int defaultWallHeight, int storeyHeight, int maxAllowedHeight) {
        // Prefer an explicit real-world height in meters (~1 block per meter
        // at this projection's scale) over building:levels, over the default.
        String heightTag = feature.tag("height", null);
        if (heightTag != null) {
            Double meters = parseLeadingNumber(heightTag);
            if (meters != null) {
                return clampHeight((int) Math.round(meters), maxAllowedHeight);
            }
        }

        String levelsTag = feature.tag("building:levels", null);
        if (levelsTag != null) {
            Double levels = parseLeadingNumber(levelsTag);
            if (levels != null) {
                return clampHeight((int) Math.round(levels * storeyHeight), maxAllowedHeight);
            }
        }

        return clampHeight(defaultWallHeight, maxAllowedHeight);
    }

    private static int clampHeight(int height, int maxAllowedHeight) {
        int ceiling = Math.min(MAX_WALL_HEIGHT, Math.max(MIN_WALL_HEIGHT, maxAllowedHeight));
        return Math.max(MIN_WALL_HEIGHT, Math.min(ceiling, height));
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
                case "glass" -> Material.CYAN_STAINED_GLASS;
                case "stone" -> Material.STONE_BRICKS;
                case "metal" -> Material.IRON_BLOCK;
                default -> null;
            };
            if (fromTag != null) return fromTag;
        }

        return switch (feature.subtype()) {
            case "house", "detached", "semidetached_house", "bungalow", "cabin" -> Material.MUD_BRICKS;
            case "residential", "apartments", "dormitory", "terrace" -> Material.BRICKS;
            case "commercial", "retail", "supermarket" -> Material.SMOOTH_QUARTZ;
            case "office" -> Material.LIGHT_GRAY_CONCRETE;
            case "hotel" -> Material.POLISHED_ANDESITE;
            case "industrial", "warehouse", "manufacture" -> Material.COBBLED_DEEPSLATE;
            case "garage", "garages", "shed", "hut" -> Material.STRIPPED_OAK_WOOD;
            case "church", "cathedral", "chapel", "mosque", "temple" -> Material.CALCITE;
            case "school", "university", "hospital", "public", "civic", "government" -> Material.SMOOTH_SANDSTONE;
            default -> Material.STONE_BRICKS;
        };
    }

    /** Used to band every floor line, so tall walls read as having storeys. */
    private static Material trimMaterialFor(OsmFeature feature) {
        return switch (feature.subtype()) {
            case "house", "detached", "semidetached_house", "bungalow", "cabin" -> Material.STRIPPED_SPRUCE_WOOD;
            case "residential", "apartments", "dormitory", "terrace" -> Material.DEEPSLATE_BRICKS;
            case "commercial", "retail", "supermarket", "office" -> Material.GRAY_CONCRETE;
            case "hotel" -> Material.POLISHED_DIORITE;
            case "industrial", "warehouse", "manufacture" -> Material.DEEPSLATE_TILES;
            case "church", "cathedral", "chapel", "mosque", "temple" -> Material.SMOOTH_STONE;
            case "school", "university", "hospital", "public", "civic", "government" -> Material.CUT_SANDSTONE;
            default -> Material.POLISHED_ANDESITE;
        };
    }

    private static Material windowMaterialFor(OsmFeature feature) {
        return switch (feature.subtype()) {
            case "commercial", "retail", "office", "supermarket", "hotel" -> Material.LIGHT_BLUE_STAINED_GLASS;
            case "industrial", "warehouse", "manufacture" -> Material.GRAY_STAINED_GLASS;
            case "church", "cathedral", "chapel", "mosque", "temple" -> Material.PURPLE_STAINED_GLASS;
            default -> Material.GLASS_PANE;
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
                case "green" -> Material.GREEN_TERRACOTTA;
                default -> null;
            };
            if (fromColor != null) return fromColor;
        }

        return switch (feature.subtype()) {
            case "house", "detached", "semidetached_house", "bungalow", "cabin" -> Material.RED_TERRACOTTA;
            case "residential", "apartments", "dormitory", "terrace" -> Material.BROWN_TERRACOTTA;
            case "commercial", "retail", "supermarket", "office" -> Material.LIGHT_GRAY_CONCRETE;
            case "hotel" -> Material.POLISHED_DEEPSLATE;
            case "industrial", "warehouse", "manufacture" -> Material.GRAY_CONCRETE;
            case "garage", "garages", "shed", "hut" -> Material.STRIPPED_OAK_WOOD;
            case "church", "cathedral", "chapel", "mosque", "temple" -> Material.CUT_COPPER;
            default -> Material.SMOOTH_STONE;
        };
    }
}
