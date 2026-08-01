package com.nexusuniverse.terra.generation;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

public record BlockPlacement(int x, int y, int z, Material material, String label, BlockFace facing, Boolean bedHead) {
    public BlockPlacement(int x, int y, int z, Material material) {
        this(x, y, z, material, null, null, null);
    }

    public BlockPlacement(int x, int y, int z, Material material, String label) {
        this(x, y, z, material, label, null, null);
    }

    /** For directional blocks (stairs, etc.) where orientation actually matters -- benches, arches. */
    public static BlockPlacement facing(int x, int y, int z, Material material, BlockFace facing) {
        return new BlockPlacement(x, y, z, material, null, facing, null);
    }

    /** One half of a bed. Both halves share the same facing; head/foot is what tells them apart. */
    public static BlockPlacement bedHalf(int x, int y, int z, Material material, BlockFace facing, boolean isHead) {
        return new BlockPlacement(x, y, z, material, null, facing, isHead);
    }
}
