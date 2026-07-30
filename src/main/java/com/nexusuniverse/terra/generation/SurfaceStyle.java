package com.nexusuniverse.terra.generation;

import org.bukkit.Material;

/**
 * Ground treatment for OSM landuse / leisure / amenity areas.
 *
 * Up to v0.1.5 these features were parsed out of the Overpass response
 * and then silently discarded -- TerrainGenerator only ever handled
 * ROAD, WATER and BUILDING. That's why generated downtowns had large
 * blank patches of default grass where the real place has a park, a
 * plaza, or a parking lot.
 */
public class SurfaceStyle {

    /** Returns null when the area should keep default terrain rather than being paved over. */
    public static Material surfaceFor(String subtype) {
        return switch (subtype) {
            case "park", "garden", "grass", "meadow", "village_green",
                 "recreation_ground", "greenfield", "common" -> Material.GRASS_BLOCK;
            case "forest", "wood", "nature_reserve" -> Material.PODZOL;
            case "parking", "garages" -> Material.GRAY_CONCRETE;
            case "pitch", "golf_course", "sports_centre", "stadium" -> Material.MOSS_BLOCK;
            case "playground" -> Material.SAND;
            case "farmland", "farmyard", "orchard", "vineyard", "allotments" -> Material.COARSE_DIRT;
            case "cemetery", "grave_yard" -> Material.PODZOL;
            case "industrial", "quarry", "landfill" -> Material.GRAY_CONCRETE;
            case "commercial", "retail" -> Material.STONE;
            case "construction", "brownfield", "greyfield" -> Material.COARSE_DIRT;
            case "railway" -> Material.GRAVEL;
            case "pedestrian", "plaza", "square" -> Material.POLISHED_ANDESITE;
            case "swimming_pool" -> Material.WATER;
            // "residential" deliberately falls through: paving whole
            // neighbourhoods flat looks far worse than leaving terrain.
            default -> null;
        };
    }

    /**
     * Roughly how many blocks apart to scatter trees on this surface.
     * Returns 0 for surfaces that should stay clear.
     */
    public static int treeSpacingFor(String subtype) {
        return switch (subtype) {
            case "forest", "wood", "nature_reserve" -> 6;
            case "park", "garden", "village_green", "recreation_ground" -> 11;
            case "cemetery", "grave_yard" -> 14;
            case "orchard" -> 8;
            default -> 0;
        };
    }

    /** Painted stall markings look right on parking, wrong on grass. */
    public static boolean hasParkingMarkings(String subtype) {
        return "parking".equals(subtype);
    }
}
