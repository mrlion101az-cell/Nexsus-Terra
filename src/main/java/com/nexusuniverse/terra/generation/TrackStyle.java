package com.nexusuniverse.terra.generation;

import org.bukkit.Material;

/**
 * Railways and barriers -- two whole feature classes the generator
 * never even asked Overpass for before v0.1.7. Rail corridors and
 * fence lines carry a lot of a real place's texture, especially in
 * industrial areas and suburbs where they define the shape of every
 * plot.
 */
public class TrackStyle {

    public static boolean isRenderable(String railwayTag) {
        return switch (railwayTag) {
            case "rail", "light_rail", "tram", "narrow_gauge", "subway", "monorail", "preserved" -> true;
            // "abandoned"/"razed"/"proposed" exist in OSM but shouldn't be built.
            default -> false;
        };
    }

    public static int bedWidth(String railwayTag) {
        return switch (railwayTag) {
            case "rail", "preserved" -> 5;
            case "light_rail", "narrow_gauge", "monorail" -> 4;
            case "tram", "subway" -> 3;
            default -> 4;
        };
    }

    public static Material bedMaterial(String railwayTag) {
        return switch (railwayTag) {
            case "tram" -> Material.POLISHED_ANDESITE;
            case "subway" -> Material.COBBLED_DEEPSLATE;
            default -> Material.GRAVEL;
        };
    }

    public static Material sleeperMaterial() {
        return Material.STRIPPED_OAK_WOOD;
    }

    public static Material railMaterial() {
        return Material.IRON_BARS;
    }
}
