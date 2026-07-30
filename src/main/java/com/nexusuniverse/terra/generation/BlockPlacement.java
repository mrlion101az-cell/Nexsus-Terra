package com.nexusuniverse.terra.generation;

import org.bukkit.Material;

/** One block to be placed at a specific position, relative to the world's origin. */
public record BlockPlacement(int x, int y, int z, Material material) {
}
