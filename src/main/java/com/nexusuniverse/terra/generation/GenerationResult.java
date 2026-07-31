/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  java.lang.Integer
 *  java.lang.Object
 *  java.util.List
 */
package com.nexusuniverse.terra.generation;

import com.nexusuniverse.terra.generation.BlockPlacement;
import java.lang.Integer;
import java.lang.Object;
import java.util.List;

public record GenerationResult(List<BlockPlacement> placements, int[][] heightMap, int radius) {
    public static final int OUTSIDE = Integer.MIN_VALUE;

    public int heightAt(int localX, int localZ) {
        int ix = localX + this.radius;
        int iz = localZ + this.radius;
        if (ix < 0 || iz < 0 || ix >= this.heightMap.length || iz >= this.heightMap[ix].length) {
            return Integer.MIN_VALUE;
        }
        return this.heightMap[ix][iz];
    }
}
