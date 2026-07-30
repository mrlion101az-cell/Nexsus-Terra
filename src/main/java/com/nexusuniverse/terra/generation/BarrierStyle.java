package com.nexusuniverse.terra.generation;

import org.bukkit.Material;

public class BarrierStyle {

    /** Null means the barrier type shouldn't be built (kerbs, bollards, gates and so on). */
    public static Material materialFor(String barrierTag) {
        return switch (barrierTag) {
            case "fence", "wooden_fence" -> Material.OAK_FENCE;
            case "wall", "city_wall", "retaining_wall" -> Material.COBBLESTONE_WALL;
            case "hedge" -> Material.OAK_LEAVES;
            case "guard_rail", "handrail" -> Material.IRON_BARS;
            default -> null;
        };
    }

    public static int heightFor(String barrierTag) {
        return switch (barrierTag) {
            case "wall", "city_wall" -> 3;
            case "hedge" -> 2;
            case "retaining_wall" -> 2;
            default -> 1;
        };
    }
}
