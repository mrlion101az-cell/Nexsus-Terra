/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  java.lang.Integer
 *  java.lang.Long
 *  java.lang.Math
 *  java.lang.Object
 *  java.util.ArrayList
 *  java.util.HashSet
 *  java.util.List
 *  java.util.Set
 *  org.bukkit.Material
 */
package com.nexusuniverse.terra.generation;

import com.nexusuniverse.terra.generation.BlockPlacement;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Math;
import java.lang.Object;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;

public class RoofBuilder {
    private static final int MAX_PITCH_RISE = 9;

    public static List<BlockPlacement> pitched(List<int[]> interior, int roofBaseY, Material roofMaterial, int worldMaxY) {
        ArrayList<BlockPlacement> out = new ArrayList<>();
        if (interior.isEmpty()) {
            return out;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] xz : interior) {
            minX = Math.min(minX, xz[0]);
            maxX = Math.max(maxX, xz[0]);
            minZ = Math.min(minZ, xz[1]);
            maxZ = Math.max(maxZ, xz[1]);
        }
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        boolean ridgeAlongZ = width <= depth;
        double centre = ridgeAlongZ ? (double) (minX + maxX) / 2.0 : (double) (minZ + maxZ) / 2.0;
        double halfSpan = Math.max(1.0, (double) (ridgeAlongZ ? width : depth) / 2.0);
        int rise = Math.min(9, (int) Math.ceil(halfSpan));
        // Expand the footprint by one block in each cardinal direction so the roof overhangs the
        // wall face like a real eave, instead of stopping flush with the walls (which read flat).
        LinkedHashMap<Long, int[]> withEaves = new LinkedHashMap<>();
        int[][] neighbourOffsets = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] xz : interior) {
            for (int[] o : neighbourOffsets) {
                int ex = xz[0] + o[0];
                int ez = xz[1] + o[1];
                withEaves.putIfAbsent(RoofBuilder.pack(ex, ez), new int[]{ex, ez});
            }
        }
        for (int[] xz : withEaves.values()) {
            double along = ridgeAlongZ ? (double) xz[0] : (double) xz[1];
            double t = 1.0 - Math.min(1.0, Math.abs(along - centre) / halfSpan);
            int h = (int) Math.round(t * (double) rise);
            for (int d = 0; d <= 1; ++d) {
                int y = roofBaseY + h - d;
                if (y < roofBaseY - 1 || y >= worldMaxY - 1) continue;
                out.add(new BlockPlacement(xz[0], y, xz[1], roofMaterial));
            }
        }
        return out;
    }

    public static List<BlockPlacement> flatRoofDetails(List<int[]> interior, int roofY, int wallHeight, Material trim, int worldMaxY, int variant) {
        int az;
        int ax;
        ArrayList<BlockPlacement> out = new ArrayList<>();
        if (interior.size() < 40) {
            return out;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] xz2 : interior) {
            minX = Math.min(minX, xz2[0]);
            maxX = Math.max(maxX, xz2[0]);
            minZ = Math.min(minZ, xz2[1]);
            maxZ = Math.max(maxZ, xz2[1]);
        }
        HashSet<Long> inside = new HashSet<>();
        for (int[] xz3 : interior) {
            inside.add(RoofBuilder.pack(xz3[0], xz3[1]));
        }
        Material[] acMaterials = {Material.IRON_BLOCK, Material.LIGHT_GRAY_CONCRETE, Material.GRAY_CONCRETE};
        Material acMaterial1 = acMaterials[Math.abs(variant) % acMaterials.length];
        Material acMaterial2 = acMaterials[Math.abs(variant / 7) % acMaterials.length];
        RoofBuilder.addBox((List<BlockPlacement>)out, (Set<Long>)inside, worldMaxY, RoofBuilder.lerp(minX, maxX, 0.33), RoofBuilder.lerp(minZ, maxZ, 0.35), roofY + 1, 2, 2, acMaterial1);
        RoofBuilder.addBox((List<BlockPlacement>)out, (Set<Long>)inside, worldMaxY, RoofBuilder.lerp(minX, maxX, 0.68), RoofBuilder.lerp(minZ, maxZ, 0.6), roofY + 1, 2, 2, acMaterial2);
        RoofBuilder.addBox((List<BlockPlacement>)out, (Set<Long>)inside, worldMaxY, RoofBuilder.lerp(minX, maxX, 0.5), RoofBuilder.lerp(minZ, maxZ, 0.5), roofY + 1, 3, 3, trim);
        if (wallHeight >= 35 && inside.contains((Object)RoofBuilder.pack(ax = RoofBuilder.lerp(minX, maxX, 0.5), az = RoofBuilder.lerp(minZ, maxZ, 0.5)))) {
            int y;
            for (int i = 4; i <= 12 && (y = roofY + i) < worldMaxY - 1; ++i) {
                out.add(new BlockPlacement(ax, y, az, Material.IRON_BARS));
            }
            int capY = roofY + 13;
            if (capY < worldMaxY - 1) {
                out.add(new BlockPlacement(ax, capY, az, Material.LIGHTNING_ROD));
            }
        } else if (Math.floorMod(variant, 3) == 0) {
            // Shorter buildings don't get the tall antenna mast, but a lot of real rooftops
            // still have a lightning rod -- give roughly a third of them a small standalone one.
            int rx = RoofBuilder.lerp(minX, maxX, 0.15);
            int rz = RoofBuilder.lerp(minZ, maxZ, 0.8);
            if (inside.contains((Object)RoofBuilder.pack(rx, rz))) {
                int rodY = roofY + 1;
                if (rodY < worldMaxY - 1) {
                    out.add(new BlockPlacement(rx, rodY, rz, Material.LIGHTNING_ROD));
                }
            }
        }
        // Skylights -- small glass patches let into the roof surface itself, not raised like the
        // HVAC boxes. Gated so not every flat roof gets one, for variety street to street.
        if (Math.floorMod(variant, 2) == 0) {
            RoofBuilder.addBox((List<BlockPlacement>)out, (Set<Long>)inside, worldMaxY, RoofBuilder.lerp(minX, maxX, 0.18), RoofBuilder.lerp(minZ, maxZ, 0.72), roofY, 2, 1, Material.LIGHT_BLUE_STAINED_GLASS);
        }
        if (Math.floorMod(variant, 5) == 0) {
            RoofBuilder.addBox((List<BlockPlacement>)out, (Set<Long>)inside, worldMaxY, RoofBuilder.lerp(minX, maxX, 0.8), RoofBuilder.lerp(minZ, maxZ, 0.22), roofY, 2, 1, Material.GLASS);
        }
        return out;
    }

    private static void addBox(List<BlockPlacement> out, Set<Long> inside, int worldMaxY, int cx, int cz, int baseY, int size, int height, Material material) {
        for (int dx = 0; dx < size; ++dx) {
            for (int dz = 0; dz < size; ++dz) {
                int y;
                int x = cx + dx;
                int z = cz + dz;
                if (!inside.contains((Object)RoofBuilder.pack(x, z))) continue;
                for (int dy = 0; dy < height && (y = baseY + dy) < worldMaxY - 1; ++dy) {
                    out.add(new BlockPlacement(x, y, z, material));
                }
            }
        }
    }

    private static int lerp(int lo, int hi, double t) {
        return (int)Math.round((double)((double)lo + (double)(hi - lo) * t));
    }

    private static long pack(int x, int z) {
        return (long)(x & 0xFFFF) << 16 | (long)(z & 0xFFFF);
    }
}
