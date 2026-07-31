package com.nexusuniverse.terra.generation;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

public class StreetFurniture {
    public static List<BlockPlacement> lampPost(int x, int groundY, int z, int worldMaxY) {
        List<BlockPlacement> out = new ArrayList<>();
        for (int i = 1; i <= 3; ++i) {
            int y = groundY + i;
            if (y >= worldMaxY - 1) {
                return out;
            }
            out.add(new BlockPlacement(x, y, z, Material.IRON_BARS));
        }
        int lampY = groundY + 4;
        if (lampY < worldMaxY - 1) {
            out.add(new BlockPlacement(x, lampY, z, Material.SEA_LANTERN));
        }
        return out;
    }

    private enum Species {
        OAK(Material.OAK_LOG, Material.OAK_LEAVES),
        BIRCH(Material.BIRCH_LOG, Material.BIRCH_LEAVES),
        SPRUCE(Material.SPRUCE_LOG, Material.SPRUCE_LEAVES),
        DARK_OAK(Material.DARK_OAK_LOG, Material.DARK_OAK_LEAVES),
        ACACIA(Material.ACACIA_LOG, Material.ACACIA_LEAVES);

        final Material log;
        final Material leaves;

        Species(Material log, Material leaves) {
            this.log = log;
            this.leaves = leaves;
        }
    }

    public static List<BlockPlacement> tree(int x, int groundY, int z, int worldMaxY, int variant) {
        List<BlockPlacement> out = new ArrayList<>();
        int v = Math.abs(variant);
        Species species = Species.values()[v % Species.values().length];
        int sizeRoll = (v / 7) % 3;

        switch (species) {
            case SPRUCE:
                buildConiferTree(out, x, groundY, z, worldMaxY, species, sizeRoll);
                break;
            case ACACIA:
                buildUmbrellaTree(out, x, groundY, z, worldMaxY, species, sizeRoll);
                break;
            default:
                buildRoundTree(out, x, groundY, z, worldMaxY, species, sizeRoll);
                break;
        }
        return out;
    }

    private static boolean placeLog(List<BlockPlacement> out, int x, int y, int z, int worldMaxY, Material material) {
        if (y >= worldMaxY - 1) {
            return false;
        }
        out.add(new BlockPlacement(x, y, z, material));
        return true;
    }

    private static void placeLeaf(List<BlockPlacement> out, int x, int y, int z, int worldMaxY, Material material) {
        if (y >= worldMaxY - 1) {
            return;
        }
        out.add(new BlockPlacement(x, y, z, material));
    }

    // Classic round-crowned tree (oak, birch, dark oak) with a size-varying trunk and canopy.
    private static void buildRoundTree(List<BlockPlacement> out, int x, int groundY, int z, int worldMaxY, Species species, int sizeRoll) {
        int trunkHeight = 4 + sizeRoll;
        for (int i = 1; i <= trunkHeight; ++i) {
            if (!placeLog(out, x, groundY + i, z, worldMaxY, species.log)) {
                return;
            }
        }
        int crownBase = groundY + trunkHeight - 1;
        int radius = 2 + (sizeRoll == 2 ? 1 : 0);
        for (int dy = 0; dy <= 3; ++dy) {
            int layerRadius = dy >= 2 ? radius - 1 : radius;
            if (layerRadius < 1) layerRadius = 1;
            for (int dx = -layerRadius; dx <= layerRadius; ++dx) {
                for (int dz = -layerRadius; dz <= layerRadius; ++dz) {
                    if (dx == 0 && dz == 0 && dy < 3) continue;
                    if (Math.abs(dx) == layerRadius && Math.abs(dz) == layerRadius && layerRadius > 1) continue;
                    int y = crownBase + dy;
                    placeLeaf(out, x + dx, y, z + dz, worldMaxY, species.leaves);
                }
            }
        }
    }

    // Tall, tapering conifer silhouette for spruce.
    private static void buildConiferTree(List<BlockPlacement> out, int x, int groundY, int z, int worldMaxY, Species species, int sizeRoll) {
        int trunkHeight = 6 + sizeRoll * 2;
        for (int i = 1; i <= trunkHeight; ++i) {
            if (!placeLog(out, x, groundY + i, z, worldMaxY, species.log)) {
                return;
            }
        }
        int layers = 4 + sizeRoll;
        int baseY = groundY + 2;
        for (int layer = 0; layer < layers; ++layer) {
            int y = baseY + (layer * (trunkHeight - 2)) / Math.max(1, layers - 1);
            int radius = Math.max(1, (layers - layer) / 2 + 1);
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (Math.abs(dx) + Math.abs(dz) > radius) continue;
                    if (dx == 0 && dz == 0) continue;
                    placeLeaf(out, x + dx, y, z + dz, worldMaxY, species.leaves);
                }
            }
        }
        placeLeaf(out, x, groundY + trunkHeight + 1, z, worldMaxY, species.leaves);
    }

    // Flat, wide umbrella canopy for acacia.
    private static void buildUmbrellaTree(List<BlockPlacement> out, int x, int groundY, int z, int worldMaxY, Species species, int sizeRoll) {
        int trunkHeight = 4 + sizeRoll;
        int lean = sizeRoll % 2 == 0 ? 1 : -1;
        int cx = x;
        for (int i = 1; i <= trunkHeight; ++i) {
            if (i > trunkHeight / 2) {
                cx = x + lean;
            }
            if (!placeLog(out, cx, groundY + i, z, worldMaxY, species.log)) {
                return;
            }
        }
        int radius = 3 + sizeRoll;
        int canopyY = groundY + trunkHeight;
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                if (dx * dx + dz * dz > radius * radius) continue;
                placeLeaf(out, cx + dx, canopyY, z + dz, worldMaxY, species.leaves);
            }
        }
    }

    public static int hash(int x, int z) {
        int h = x * 73856093 ^ z * 19349663;
        return Math.abs(h);
    }
}
