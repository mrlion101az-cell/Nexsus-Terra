package com.nexusuniverse.terra.generation;

import org.bukkit.Material;

public record BlockPlacement(int x, int y, int z, Material material, String label) {
    public BlockPlacement(int x, int y, int z, Material material) {
        this(x, y, z, material, null);
    }
}
