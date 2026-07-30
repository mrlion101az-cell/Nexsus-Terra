package com.nexusuniverse.terra.generation;

import org.bukkit.Material;

public class RoadStyle {

    public static Material materialFor(String highwayTag) {
        return switch (highwayTag) {
            case "motorway", "trunk", "primary", "secondary" -> Material.BLACKSTONE;
            case "tertiary", "residential", "unclassified" -> Material.GRAY_CONCRETE;
            case "service", "track" -> Material.COARSE_DIRT;
            case "footway", "path", "pedestrian", "steps" -> Material.SMOOTH_STONE_SLAB;
            case "cycleway" -> Material.RED_CONCRETE_POWDER;
            default -> Material.GRAVEL;
        };
    }
}
