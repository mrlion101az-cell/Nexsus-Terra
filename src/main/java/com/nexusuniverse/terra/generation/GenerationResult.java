package com.nexusuniverse.terra.generation;

import java.util.List;

/**
 * Output of a full generation pass.
 *
 * heightMap exists so BlockPlacementTask can sweep the area clear of
 * pre-existing terrain and trees before placing anything. Doing that as
 * explicit AIR BlockPlacements would mean millions of objects held in
 * memory at once (a 250m radius is ~196,000 columns); handing over a
 * compact int grid and letting the task walk it procedurally costs
 * about a megabyte instead.
 *
 * heightMap is indexed [x + radius][z + radius] and holds the absolute
 * world Y of the ground surface for that column, or Integer.MIN_VALUE
 * for columns outside the generation circle.
 */
public record GenerationResult(List<BlockPlacement> placements, int[][] heightMap, int radius) {

    public static final int OUTSIDE = Integer.MIN_VALUE;

    public int heightAt(int localX, int localZ) {
        int ix = localX + radius;
        int iz = localZ + radius;
        if (ix < 0 || iz < 0 || ix >= heightMap.length || iz >= heightMap[ix].length) {
            return OUTSIDE;
        }
        return heightMap[ix][iz];
    }
}
