package com.nexusuniverse.terra.generation;

import org.bukkit.Material;

public class BarrierStyle {
    public static Material materialFor(String barrierTag) {
        if (barrierTag == null) return null;
        switch (barrierTag) {
            case "fence":
            case "wooden_fence":
                return Material.OAK_FENCE;
            case "wall":
            case "city_wall":
            case "retaining_wall":
                return Material.COBBLESTONE_WALL;
            case "hedge":
                return Material.OAK_LEAVES;
            case "guard_rail":
            case "handrail":
                return Material.IRON_BARS;
            default:
                return null;
        }
    }

    public static int heightFor(String barrierTag) {
        if (barrierTag == null) return 1;
        switch (barrierTag) {
            case "wall":
            case "city_wall":
                return 3;
            case "hedge":
            case "retaining_wall":
                return 2;
            default:
                return 1;
        }
    }
}
