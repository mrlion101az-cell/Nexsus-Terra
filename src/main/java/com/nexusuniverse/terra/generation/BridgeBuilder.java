package com.nexusuniverse.terra.generation;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Bridge decks.
 *
 * Until v0.1.7 a road tagged `bridge=yes` was rasterized exactly like
 * any other road: one block of surface laid at whatever the terrain
 * height happened to be underneath. Over a river that meant the
 * carriageway dived straight into the water and came out the other
 * side, which is the single most obviously broken thing you could see
 * in a generated city with a river through it.
 *
 * A bridge deck is instead held at one level height across its whole
 * span, with railings along both edges and support pillars dropped
 * down to whatever is below at intervals.
 */
public class BridgeBuilder {

    private static final int PILLAR_SPACING = 9;
    private static final int MAX_PILLAR_DROP = 40;

    public record Deck(List<BlockPlacement> placements, Set<Long> deckCells, int deckY) {}

    /**
     * @param centreline    projected block-space centreline of the bridge
     * @param width         carriageway width in blocks
     * @param deckMaterial  road surface material
     * @param railMaterial  railing material
     * @param groundHeightAt returns terrain height for a block column
     * @param worldMaxY     world build ceiling
     */
    public static Deck build(List<double[]> centreline, int width,
                              Material deckMaterial, Material railMaterial,
                              BiFunction<Integer, Integer, Integer> groundHeightAt,
                              int clearance, int worldMaxY) {

        List<BlockPlacement> out = new ArrayList<>();
        Set<Long> deckCells = new HashSet<>();

        List<int[]> surface = PolygonRasterizer.thickLine(centreline, width);
        List<int[]> withRails = PolygonRasterizer.thickLine(centreline, width + 2);
        if (surface.isEmpty()) {
            return new Deck(out, deckCells, 0);
        }

        // A bridge is level. Take the highest ground along the span and lift
        // the deck clear of it, so the deck sits above the banks at both ends
        // rather than sinking into whichever end happens to be higher.
        int highestGround = Integer.MIN_VALUE;
        for (int[] xz : surface) {
            highestGround = Math.max(highestGround, groundHeightAt.apply(xz[0], xz[1]));
        }
        int deckY = Math.min(worldMaxY - 6, highestGround + clearance);

        Set<Long> surfaceKeys = new HashSet<>();
        for (int[] xz : surface) {
            surfaceKeys.add(pack(xz[0], xz[1]));
        }

        // Deck: surface plus a slab of structure directly beneath it, so the
        // bridge reads as having thickness when seen from the riverbank.
        for (int[] xz : surface) {
            out.add(new BlockPlacement(xz[0], deckY, xz[1], deckMaterial));
            out.add(new BlockPlacement(xz[0], deckY - 1, xz[1], railMaterial));
            deckCells.add(pack(xz[0], xz[1]));
        }

        // Railings sit on the cells that are in the widened strip but not the
        // carriageway itself, so they line the edges without narrowing the road.
        for (int[] xz : withRails) {
            long key = pack(xz[0], xz[1]);
            if (surfaceKeys.contains(key)) continue;
            out.add(new BlockPlacement(xz[0], deckY, xz[1], railMaterial));
            if (deckY + 1 < worldMaxY - 1) {
                out.add(new BlockPlacement(xz[0], deckY + 1, xz[1], Material.IRON_BARS));
            }
            deckCells.add(key);
        }

        // Support pillars down to the ground, spaced along the span.
        for (int[] xz : surface) {
            if (Math.floorMod(xz[0], PILLAR_SPACING) != 0 || Math.floorMod(xz[1], PILLAR_SPACING) != 0) {
                continue;
            }
            int ground = groundHeightAt.apply(xz[0], xz[1]);
            int bottom = Math.max(ground, deckY - MAX_PILLAR_DROP);
            for (int y = deckY - 2; y >= bottom; y--) {
                out.add(new BlockPlacement(xz[0], y, xz[1], Material.STONE_BRICKS));
            }
        }

        return new Deck(out, deckCells, deckY);
    }

    private static long pack(int x, int z) {
        return ((long) (x & 0xFFFF) << 16) | (long) (z & 0xFFFF);
    }
}
