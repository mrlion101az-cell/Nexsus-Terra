package com.nexusuniverse.terra.generation;

import com.nexusuniverse.terra.geo.GeoPoint;
import com.nexusuniverse.terra.geo.OsmFeature;
import org.bukkit.Material;

public class BuildingStyle {
    private static final int MIN_WALL_HEIGHT = 4;
    private static final int MAX_WALL_HEIGHT = 140;
    public final Material wallMaterial;
    public final Material trimMaterial;
    public final Material windowMaterial;
    public final Material roofMaterial;
    public final int wallHeight;
    public final int storeyHeight;
    public final boolean storefront;
    public final boolean pitchedRoof;

    private BuildingStyle(Material wallMaterial, Material trimMaterial, Material windowMaterial, Material roofMaterial, int wallHeight, int storeyHeight, boolean storefront, boolean pitchedRoof) {
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
        return new BuildingStyle(wallMaterialFor(feature), trimMaterialFor(feature), windowMaterialFor(feature), roofMaterialFor(feature), wallHeight, storeyHeight, storefrontFor(feature), pitchedRoofFor(feature, wallHeight));
    }

    private static boolean storefrontFor(OsmFeature feature) {
        String subtype = feature.subtype();
        if (subtype == null) return false;
        switch (subtype) {
            case "commercial":
            case "retail":
            case "supermarket":
            case "office":
            case "hotel":
            case "restaurant":
            case "cafe":
                return true;
            default:
                return false;
        }
    }

    private static boolean pitchedRoofFor(OsmFeature feature, int wallHeight) {
        String shapeTag = feature.tag("roof:shape", null);
        if (shapeTag != null) {
            switch (shapeTag) {
                case "gabled":
                case "hipped":
                case "pitched":
                case "gambrel":
                case "half-hipped":
                case "round":
                    return true;
                case "flat":
                default:
                    return false;
            }
        }
        if (wallHeight > 20) {
            return false;
        }
        String subtype = feature.subtype();
        if (subtype == null) return false;
        switch (subtype) {
            case "house":
            case "detached":
            case "semidetached_house":
            case "bungalow":
            case "cabin":
            case "terrace":
            case "hut":
            case "shed":
            case "garage":
            case "garages":
            case "farm":
            case "barn":
            case "church":
            case "cathedral":
            case "chapel":
            case "temple":
                return true;
            default:
                return false;
        }
    }

    private static int storeyHeightFor(OsmFeature feature, int blocksPerLevel) {
        String subtype = feature.subtype();
        if (subtype != null) {
            switch (subtype) {
                case "commercial":
                case "retail":
                case "office":
                case "supermarket":
                case "industrial":
                case "warehouse":
                    return 5;
                case "church":
                case "cathedral":
                case "chapel":
                case "mosque":
                case "temple":
                    return 6;
            }
        }
        return Math.max(3, blocksPerLevel);
    }

    private static int heightFor(OsmFeature feature, int defaultWallHeight, int storeyHeight, int maxAllowedHeight) {
        Double meters;
        String heightTag = feature.tag("height", null);
        if (heightTag != null && (meters = parseLeadingNumber(heightTag)) != null) {
            return clampHeight((int) Math.round(meters), maxAllowedHeight);
        }
        Double levels;
        String levelsTag = feature.tag("building:levels", null);
        if (levelsTag != null && (levels = parseLeadingNumber(levelsTag)) != null) {
            return clampHeight((int) Math.round(levels * storeyHeight), maxAllowedHeight);
        }
        return clampHeight(defaultWallHeight, maxAllowedHeight);
    }

    private static int clampHeight(int height, int maxAllowedHeight) {
        int ceiling = Math.min(140, Math.max(4, maxAllowedHeight));
        return Math.max(4, Math.min(ceiling, height));
    }

    private static Double parseLeadingNumber(String raw) {
        StringBuilder digits = new StringBuilder();
        boolean seenDigit = false;
        for (char c : raw.trim().toCharArray()) {
            if (Character.isDigit(c) || c == '.') {
                digits.append(c);
                seenDigit = true;
                continue;
            }
            if (seenDigit) break;
        }
        if (digits.length() == 0) {
            return null;
        }
        try {
            return Double.parseDouble(digits.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int hashFeature(OsmFeature feature) {
        java.util.List<GeoPoint> verts = feature.vertices();
        if (verts.isEmpty()) return 0;
        GeoPoint p = verts.get(0);
        long bits = Double.doubleToLongBits(p.lat()) * 31 + Double.doubleToLongBits(p.lon());
        return Math.abs((int) (bits ^ (bits >>> 32)));
    }

    private static Material wallMaterialFor(OsmFeature feature) {
        String materialTag = feature.tag("building:material", null);
        if (materialTag != null) {
            Material override = switch (materialTag) {
                case "brick" -> Material.BRICKS;
                case "wood", "timber_framing" -> Material.OAK_PLANKS;
                case "concrete" -> Material.LIGHT_GRAY_CONCRETE;
                case "glass" -> Material.CYAN_STAINED_GLASS;
                case "stone" -> Material.STONE_BRICKS;
                case "metal" -> Material.IRON_BLOCK;
                default -> null;
            };
            if (override != null) {
                return override;
            }
        }
        String subtype = feature.subtype();
        if (subtype == null) return Material.STONE_BRICKS;
        switch (subtype) {
            case "house":
            case "detached":
            case "semidetached_house":
            case "bungalow":
            case "cabin": {
                Material[] variants = {Material.MUD_BRICKS, Material.BRICKS, Material.SMOOTH_SANDSTONE, Material.TERRACOTTA};
                return variants[(hashFeature(feature) & 0x7fffffff) % variants.length];
            }
            case "residential":
            case "apartments":
            case "dormitory":
            case "terrace": {
                Material[] variants = {Material.BRICKS, Material.STONE_BRICKS, Material.SMOOTH_QUARTZ};
                return variants[(hashFeature(feature) & 0x7fffffff) % variants.length];
            }
            case "commercial":
            case "retail":
            case "supermarket": {
                Material[] variants = {Material.SMOOTH_QUARTZ, Material.CUT_SANDSTONE, Material.POLISHED_DIORITE};
                return variants[(hashFeature(feature) & 0x7fffffff) % variants.length];
            }
            case "office": {
                Material[] variants = {Material.LIGHT_GRAY_CONCRETE, Material.CYAN_STAINED_GLASS, Material.SMOOTH_STONE};
                return variants[(hashFeature(feature) & 0x7fffffff) % variants.length];
            }
            case "hotel":
                return Material.POLISHED_ANDESITE;
            case "industrial":
            case "warehouse":
            case "manufacture": {
                Material[] variants = {Material.COBBLED_DEEPSLATE, Material.IRON_BLOCK, Material.GRAY_CONCRETE};
                return variants[(hashFeature(feature) & 0x7fffffff) % variants.length];
            }
            case "garage":
            case "garages":
            case "shed":
            case "hut":
                return Material.STRIPPED_OAK_WOOD;
            case "church":
            case "cathedral":
            case "chapel":
            case "mosque":
            case "temple":
                return Material.CALCITE;
            case "school":
            case "university":
            case "hospital":
            case "public":
            case "civic":
            case "government": {
                Material[] variants = {Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, Material.CALCITE};
                return variants[(hashFeature(feature) & 0x7fffffff) % variants.length];
            }
            default:
                return Material.STONE_BRICKS;
        }
    }

    private static Material trimMaterialFor(OsmFeature feature) {
        String subtype = feature.subtype();
        if (subtype == null) return Material.POLISHED_ANDESITE;
        switch (subtype) {
            case "house":
            case "detached":
            case "semidetached_house":
            case "bungalow":
            case "cabin":
                return Material.STRIPPED_SPRUCE_WOOD;
            case "residential":
            case "apartments":
            case "dormitory":
            case "terrace":
                return Material.DEEPSLATE_BRICKS;
            case "commercial":
            case "retail":
            case "supermarket":
            case "office":
                return Material.GRAY_CONCRETE;
            case "hotel":
                return Material.POLISHED_DIORITE;
            case "industrial":
            case "warehouse":
            case "manufacture":
                return Material.DEEPSLATE_TILES;
            case "church":
            case "cathedral":
            case "chapel":
            case "mosque":
            case "temple":
                return Material.SMOOTH_STONE;
            case "school":
            case "university":
            case "hospital":
            case "public":
            case "civic":
            case "government":
                return Material.CUT_SANDSTONE;
            default:
                return Material.POLISHED_ANDESITE;
        }
    }

    private static Material windowMaterialFor(OsmFeature feature) {
        String subtype = feature.subtype();
        if (subtype == null) return Material.GLASS_PANE;
        switch (subtype) {
            case "commercial":
            case "retail":
            case "office":
            case "supermarket":
            case "hotel":
                return Material.LIGHT_BLUE_STAINED_GLASS;
            case "industrial":
            case "warehouse":
            case "manufacture":
                return Material.GRAY_STAINED_GLASS;
            case "church":
            case "cathedral":
            case "chapel":
            case "mosque":
            case "temple":
                return Material.PURPLE_STAINED_GLASS;
            default:
                return Material.GLASS_PANE;
        }
    }

    private static Material roofMaterialFor(OsmFeature feature) {
        String colourTag = feature.tag("roof:colour", null);
        if (colourTag != null) {
            Material override = switch (colourTag.toLowerCase()) {
                case "red", "#ff0000" -> Material.RED_TERRACOTTA;
                case "brown" -> Material.BROWN_TERRACOTTA;
                case "grey", "gray" -> Material.GRAY_CONCRETE;
                case "black" -> Material.BLACK_CONCRETE;
                case "green" -> Material.GREEN_TERRACOTTA;
                default -> null;
            };
            if (override != null) {
                return override;
            }
        }
        String subtype = feature.subtype();
        if (subtype == null) return Material.SMOOTH_STONE;
        switch (subtype) {
            case "house":
            case "detached":
            case "semidetached_house":
            case "bungalow":
            case "cabin": {
                Material[] variants = {Material.RED_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.LIGHT_GRAY_CONCRETE};
                return variants[(hashFeature(feature) & 0x7fffffff) % variants.length];
            }
            case "residential":
            case "apartments":
            case "dormitory":
            case "terrace":
                return Material.BROWN_TERRACOTTA;
            case "commercial":
            case "retail":
            case "supermarket":
            case "office":
                return Material.LIGHT_GRAY_CONCRETE;
            case "hotel":
                return Material.POLISHED_DEEPSLATE;
            case "industrial":
            case "warehouse":
            case "manufacture":
                return Material.GRAY_CONCRETE;
            case "garage":
            case "garages":
            case "shed":
            case "hut":
                return Material.STRIPPED_OAK_WOOD;
            case "church":
            case "cathedral":
            case "chapel":
            case "mosque":
            case "temple":
                return Material.CUT_COPPER;
            default:
                return Material.SMOOTH_STONE;
        }
    }
}
