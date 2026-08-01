package com.nexusuniverse.terra.generation;

import org.bukkit.Material;

public class RoadStyle {
    public static Material materialFor(String highwayTag) {
        if (highwayTag == null) return Material.GRAVEL;
        switch (highwayTag) {
            case "motorway":
            case "trunk":
            case "primary":
            case "secondary":
                return Material.BLACKSTONE;
            case "tertiary":
            case "residential":
            case "unclassified":
                return Material.GRAY_CONCRETE;
            case "service":
            case "track":
                return Material.COARSE_DIRT;
            case "footway":
            case "path":
            case "pedestrian":
            case "steps":
                return Material.SMOOTH_STONE_SLAB;
            case "cycleway":
                return Material.RED_CONCRETE_POWDER;
            default:
                return Material.GRAVEL;
        }
    }
}
