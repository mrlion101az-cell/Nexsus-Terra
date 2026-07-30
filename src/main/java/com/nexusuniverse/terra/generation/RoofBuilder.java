package com.nexusuniverse.terra.generation;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Roof geometry and rooftop clutter.
 *
 * Through v0.1.5 every roof in the generator was a single flat cap at
 * one Y level, which is defensible for a downtown office block and
 * completely wrong for a house. It also meant that looking down over a
 * generated city -- which is most of how you actually view one -- you
 * saw nothing but featureless slabs.
 */
public class RoofBuilder {

    private static final int MAX_PITCH_RISE = 9;

    /**
     * Gabled roof: finds the footprint's shorter axis, runs a ridge down
     * the long axis, and slopes the roof off both sides. Each column
     * places two blocks deep so the stepped slope has no see-through
     * gaps between adjacent heights.
     */
    public static List<BlockPlacement> pitched(List<int[]> interior, int roofBaseY, Material roofMaterial, int worldMaxY) {
        List<BlockPlacement> out = new ArrayList<>();
        if (interior.isEmpty()) return out;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] xz : interior) {
            minX = Math.min(minX, xz[0]);
            maxX = Math.max(maxX, xz[0]);
            minZ = Math.min(minZ, xz[1]);
            maxZ = Math.max(maxZ, xz[1]);
        }

        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        boolean ridgeAlongZ = width <= depth; // slope across the narrow axis

        double centre = ridgeAlongZ ? (minX + maxX) / 2.0 : (minZ + maxZ) / 2.0;
        double halfSpan = Math.max(1.0, (ridgeAlongZ ? width : depth) / 2.0);
        int rise = Math.min(MAX_PITCH_RISE, (int) Math.ceil(halfSpan));

        for (int[] xz : interior) {
            double along = ridgeAlongZ ? xz[0] : xz[1];
            double t = 1.0 - Math.min(1.0, Math.abs(along - centre) / halfSpan);
            int h = (int) Math.round(t * rise);

            for (int d = 0; d <= 1; d++) {
                int y = roofBaseY + h - d;
                if (y < roofBaseY - 1 || y >= worldMaxY - 1) continue;
                out.add(new BlockPlacement(xz[0], y, xz[1], roofMaterial));
            }
        }
        return out;
    }

    /**
     * Clutter for flat roofs: a couple of HVAC blocks, a stairwell
     * penthouse, and an antenna mast on anything tall. Placement is
     * derived from the footprint's own bounding box, so it's stable
     * across regenerations rather than jittering each run.
     */
    public static List<BlockPlacement> flatRoofDetails(List<int[]> interior, int roofY, int wallHeight,
                                                        Material trim, int worldMaxY) {
        List<BlockPlacement> out = new ArrayList<>();
        if (interior.size() < 40) return out; // tiny roofs stay clean

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] xz : interior) {
            minX = Math.min(minX, xz[0]);
            maxX = Math.max(maxX, xz[0]);
            minZ = Math.min(minZ, xz[1]);
            maxZ = Math.max(maxZ, xz[1]);
        }

        java.util.Set<Long> inside = new java.util.HashSet<>();
        for (int[] xz : interior) {
            inside.add(pack(xz[0], xz[1]));
        }

        // Two HVAC units at roughly one third and two thirds across.
        addBox(out, inside, worldMaxY,
                lerp(minX, maxX, 0.33), lerp(minZ, maxZ, 0.35), roofY + 1, 2, 2, Material.IRON_BLOCK);
        addBox(out, inside, worldMaxY,
                lerp(minX, maxX, 0.68), lerp(minZ, maxZ, 0.6), roofY + 1, 2, 2, Material.IRON_BLOCK);

        // Stairwell penthouse near the middle.
        addBox(out, inside, worldMaxY,
                lerp(minX, maxX, 0.5), lerp(minZ, maxZ, 0.5), roofY + 1, 3, 3, trim);

        // Antenna mast, tall buildings only.
        if (wallHeight >= 35) {
            int ax = lerp(minX, maxX, 0.5);
            int az = lerp(minZ, maxZ, 0.5);
            if (inside.contains(pack(ax, az))) {
                for (int i = 4; i <= 12; i++) {
                    int y = roofY + i;
                    if (y >= worldMaxY - 1) break;
                    out.add(new BlockPlacement(ax, y, az, Material.IRON_BARS));
                }
            }
        }
        return out;
    }

    private static void addBox(List<BlockPlacement> out, java.util.Set<Long> inside, int worldMaxY,
                                int cx, int cz, int baseY, int size, int height, Material material) {
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                if (!inside.contains(pack(x, z))) continue;
                for (int dy = 0; dy < height; dy++) {
                    int y = baseY + dy;
                    if (y >= worldMaxY - 1) break;
                    out.add(new BlockPlacement(x, y, z, material));
                }
            }
        }
    }

    private static int lerp(int lo, int hi, double t) {
        return (int) Math.round(lo + (hi - lo) * t);
    }

    private static long pack(int x, int z) {
        return ((long) (x & 0xFFFF) << 16) | (long) (z & 0xFFFF);
    }
}
