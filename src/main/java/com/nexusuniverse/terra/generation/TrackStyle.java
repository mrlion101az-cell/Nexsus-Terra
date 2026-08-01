package com.nexusuniverse.terra.generation;

import org.bukkit.Material;

public class TrackStyle {
    public static boolean isRenderable(String railwayTag) {
        if (railwayTag == null) return false;
        switch (railwayTag) {
            case "rail":
            case "light_rail":
            case "tram":
            case "narrow_gauge":
            case "subway":
            case "monorail":
            case "preserved":
                return true;
            default:
                return false;
        }
    }

    public static int bedWidth(String railwayTag) {
        if (railwayTag == null) return 4;
        switch (railwayTag) {
            case "rail":
            case "preserved":
                return 5;
            case "light_rail":
            case "narrow_gauge":
            case "monorail":
                return 4;
            case "tram":
            case "subway":
                return 3;
            default:
                return 4;
        }
    }

    public static Material bedMaterial(String railwayTag) {
        if ("tram".equals(railwayTag)) return Material.POLISHED_ANDESITE;
        if ("subway".equals(railwayTag)) return Material.COBBLED_DEEPSLATE;
        return Material.GRAVEL;
    }

    public static Material sleeperMaterial() {
        return Material.STRIPPED_OAK_WOOD;
    }

    public static Material railMaterial() {
        return Material.IRON_BARS;
    }
}
