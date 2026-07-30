package com.nexusuniverse.terra.generation;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Small props that make a generated street read as inhabited rather
 * than as an empty architectural model: lamp posts along pavements and
 * simple trees on parkland.
 *
 * Everything here is placed deterministically from world coordinates,
 * never from a random source, so regenerating the same area twice
 * produces an identical result.
 */
public class StreetFurniture {

    public static List<BlockPlacement> lampPost(int x, int groundY, int z, int worldMaxY) {
        List<BlockPlacement> out = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            int y = groundY + i;
            if (y >= worldMaxY - 1) return out;
            out.add(new BlockPlacement(x, y, z, Material.IRON_BARS));
        }
        int lampY = groundY + 4;
        if (lampY < worldMaxY - 1) {
            out.add(new BlockPlacement(x, lampY, z, Material.SEA_LANTERN));
        }
        return out;
    }

    /**
     * A deliberately simple tree - a short trunk and a leaf blob. Not
     * trying to imitate vanilla tree generation, just enough mass to
     * read as a tree at street scale without costing hundreds of blocks
     * each across a whole park.
     */
    public static List<BlockPlacement> tree(int x, int groundY, int z, int worldMaxY, int variant) {
        List<BlockPlacement> out = new ArrayList<>();
        int trunkHeight = 4 + (variant % 3);

        for (int i = 1; i <= trunkHeight; i++) {
            int y = groundY + i;
            if (y >= worldMaxY - 1) return out;
            out.add(new BlockPlacement(x, y, z, Material.OAK_LOG));
        }

        int crownBase = groundY + trunkHeight;
        int radius = 2;
        for (int dy = 0; dy <= 2; dy++) {
            int layerRadius = dy == 2 ? radius - 1 : radius;
            for (int dx = -layerRadius; dx <= layerRadius; dx++) {
                for (int dz = -layerRadius; dz <= layerRadius; dz++) {
                    if (dx == 0 && dz == 0 && dy < 2) continue; // leave the trunk column
                    if (Math.abs(dx) == layerRadius && Math.abs(dz) == layerRadius) continue; // round the corners
                    int y = crownBase + dy;
                    if (y >= worldMaxY - 1) continue;
                    out.add(new BlockPlacement(x + dx, y, z + dz, Material.OAK_LEAVES));
                }
            }
        }
        return out;
    }

    /**
     * Stable pseudo-random value for a coordinate. Used to vary tree
     * shape and to thin out furniture without a Random instance, whose
     * sequence would depend on iteration order.
     */
    public static int hash(int x, int z) {
        int h = x * 73856093 ^ z * 19349663;
        return Math.abs(h);
    }
}
