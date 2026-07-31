package com.nexusuniverse.terra.generation;

import org.bukkit.Material;

public class SurfaceStyle {
    public static Material surfaceFor(String landuseTag) {
        if (landuseTag == null) return null;
        switch (landuseTag) {
            case "park":
            case "garden":
            case "grass":
            case "meadow":
            case "village_green":
            case "recreation_ground":
            case "common":
            case "playground":
                return Material.GRASS_BLOCK;
            case "forest":
            case "wood":
            case "nature_reserve":
                return Material.PODZOL;
            case "parking":
            case "garages":
                return Material.GRAY_CONCRETE;
            case "pitch":
            case "golf_course":
            case "sports_centre":
            case "stadium":
                return Material.MOSS_BLOCK;
            case "greenfield":
                return Material.SAND;
            case "farmland":
            case "farmyard":
            case "orchard":
            case "vineyard":
            case "allotments":
                return Material.COARSE_DIRT;
            case "cemetery":
            case "grave_yard":
                return Material.PODZOL;
            case "industrial":
            case "quarry":
            case "landfill":
                return Material.GRAY_CONCRETE;
            case "commercial":
            case "retail":
                return Material.STONE;
            case "construction":
            case "brownfield":
            case "greyfield":
                return Material.COARSE_DIRT;
            case "railway":
                return Material.GRAVEL;
            case "pedestrian":
            case "plaza":
            case "square":
                return Material.POLISHED_ANDESITE;
            case "swimming_pool":
                return Material.WATER;
            default:
                return null;
        }
    }

    public static int treeSpacingFor(String landuseTag) {
        if (landuseTag == null) return 0;
        switch (landuseTag) {
            case "forest":
            case "wood":
            case "nature_reserve":
                return 6;
            case "park":
            case "garden":
            case "village_green":
            case "recreation_ground":
                return 11;
            case "cemetery":
            case "grave_yard":
                return 14;
            case "orchard":
                return 8;
            default:
                return 0;
        }
    }

    public static boolean hasParkingMarkings(String landuseTag) {
        return "parking".equals(landuseTag);
    }
}
