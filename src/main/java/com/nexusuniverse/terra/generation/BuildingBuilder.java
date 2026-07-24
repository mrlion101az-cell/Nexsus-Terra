package com.nexusuniverse.terra.generation;

import com.nexusuniverse.terra.geo.OsmFeature;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds one actual building from its OSM footprint + tags: real height
 * (from "height" or "building:levels" tags when present), material
 * varied by building type, evenly-spaced windows along every wall, a
 * door opening, and floor slabs between levels.
 *
 * Still a flat roof -- true pitched/gabled roof geometry is a real next
 * step, not attempted here. Roof material is varied by building type to
 * at least suggest the right feel (dark wood tones for houses, stone for
 * institutional) without pretending to be a slope it isn't.
 */
public class BuildingBuilder {

    private static final int BLOCKS_PER_LEVEL = 3;
    private static final int DEFAULT_LEVELS = 2;
    private static final int WINDOW_SPACING = 4;

    public List<BlockPlacement> build(List<double[]> footprint, OsmFeature feature, int baseHeight, int radiusBlocks) {
        List<BlockPlacement> placements = new ArrayList<>();

        int totalHeight = resolveHeight(feature);
        Material wallMaterial = resolveWallMaterial(feature.subtype());
        Material windowMaterial = Material.GLASS_PANE;

        List<double[]> closed = closeLoop(footprint);
        double distanceSoFar = 0;

        for (int i = 0; i + 1 < closed.size(); i++) {
            double[] a = closed.get(i);
            double[] b = closed.get(i + 1);
            double dx = b[0] - a[0];
            double dz = b[1] - a[1];
            double edgeLength = Math.sqrt(dx * dx + dz * dz);
            int steps = Math.max(1, (int) Math.ceil(edgeLength));

            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps;
                int cx = (int) Math.round(a[0] + dx * t);
                int cz = (int) Math.round(a[1] + dz * t);
                if (outsideRadius(cx, cz, radiusBlocks)) {
                    continue;
                }

                double posAlongWall = distanceSoFar + edgeLength * t;
                boolean isWindowColumn = ((int) Math.round(posAlongWall)) % WINDOW_SPACING == 0;
                boolean isDoorColumn = (i == 0 && s == steps / 2);

                for (int level = 1; level <= totalHeight; level++) {
                    if (isDoorColumn && level <= 2) {
                        placements.add(new BlockPlacement(cx, baseHeight + level, cz, Material.AIR));
                        continue;
                    }
                    boolean floorMiddleLevel = ((level - 1) % BLOCKS_PER_LEVEL) == 1;
                    if (isWindowColumn && level > 1 && floorMiddleLevel) {
                        placements.add(new BlockPlacement(cx, baseHeight + level, cz, windowMaterial));
                    } else {
                        placements.add(new BlockPlacement(cx, baseHeight + level, cz, wallMaterial));
                    }
                }
            }
            distanceSoFar += edgeLength;
        }

        List<int[]> footprintFill = PolygonRasterizer.fillInterior(footprint);

        for (int level = BLOCKS_PER_LEVEL; level < totalHeight; level += BLOCKS_PER_LEVEL) {
            for (int[] xz : footprintFill) {
                if (outsideRadius(xz[0], xz[1], radiusBlocks)) {
                    continue;
                }
                placements.add(new BlockPlacement(xz[0], baseHeight + level, xz[1], Material.OAK_PLANKS));
            }
        }

        if (isPitchedRoofType(feature.subtype())) {
            placements.addAll(buildPitchedRoof(footprint, footprintFill, baseHeight + totalHeight, radiusBlocks));
        } else {
            Material roof = roofMaterial(feature.subtype());
            for (int[] xz : footprintFill) {
                if (outsideRadius(xz[0], xz[1], radiusBlocks)) {
                    continue;
                }
                placements.add(new BlockPlacement(xz[0], baseHeight + totalHeight + 1, xz[1], roof));
            }
        }

        return placements;
    }

    /**
     * Real sloped roof: finds the footprint's bounding box, picks the
     * longer axis as the ridge direction, and slopes two planes down from
     * a center ridge line to the wall tops on either side -- an actual
     * gable roof, not a flat cap pretending to be one. Reserved for
     * building types that realistically have pitched roofs (houses);
     * everything else keeps the flat cap, which is also realistic (most
     * commercial/industrial/apartment buildings genuinely have flat roofs).
     */
    private List<BlockPlacement> buildPitchedRoof(List<double[]> footprint, List<int[]> footprintFill, int wallTopY, int radiusBlocks) {
        List<BlockPlacement> placements = new ArrayList<>();

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double[] v : footprint) {
            minX = Math.min(minX, v[0]);
            maxX = Math.max(maxX, v[0]);
            minZ = Math.min(minZ, v[1]);
            maxZ = Math.max(maxZ, v[1]);
        }
        double width = maxX - minX;
        double depth = maxZ - minZ;

        boolean ridgeAlongX = width >= depth;
        double centerX = (minX + maxX) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        double halfSpan = (ridgeAlongX ? depth : width) / 2.0;
        if (halfSpan < 1.0) {
            halfSpan = 1.0;
        }

        int roofRise = (int) Math.max(2, Math.min(6, Math.round(halfSpan)));

        for (int[] xz : footprintFill) {
            if (outsideRadius(xz[0], xz[1], radiusBlocks)) {
                continue;
            }
            double distanceFromRidge = ridgeAlongX ? Math.abs(xz[1] - centerZ) : Math.abs(xz[0] - centerX);
            double slopeFraction = Math.min(1.0, distanceFromRidge / halfSpan);
            int roofY = wallTopY + 1 + (int) Math.round(roofRise * (1.0 - slopeFraction));
            // Solid, non-directional material -- the placement pipeline only
            // does plain setType() with no facing/half data, so stairs here
            // would all default to the same orientation regardless of actual
            // slope direction and look visually wrong. A whole-block stepped
            // slope in a solid material is the honest result of what this
            // pipeline can actually place correctly.
            placements.add(new BlockPlacement(xz[0], roofY, xz[1], Material.DARK_OAK_PLANKS));
        }

        return placements;
    }

    private boolean isPitchedRoofType(String buildingType) {
        return switch (buildingType) {
            case "house", "detached", "semidetached_house", "terrace" -> true;
            default -> false;
        };
    }

    private int resolveHeight(OsmFeature feature) {
        String heightTag = feature.tags().get("height");
        if (heightTag != null) {
            try {
                String numeric = heightTag.replaceAll("[^0-9.]", "");
                if (!numeric.isEmpty()) {
                    return Math.max(3, (int) Math.round(Double.parseDouble(numeric)));
                }
            } catch (NumberFormatException ignored) {
                // fall through to next strategy
            }
        }

        String levelsTag = feature.tags().get("building:levels");
        if (levelsTag != null) {
            try {
                int levels = (int) Math.round(Double.parseDouble(levelsTag));
                return Math.max(1, levels) * BLOCKS_PER_LEVEL;
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }

        return DEFAULT_LEVELS * BLOCKS_PER_LEVEL;
    }

    private Material resolveWallMaterial(String buildingType) {
        return switch (buildingType) {
            case "house", "detached", "semidetached_house", "terrace", "residential" -> Material.BRICKS;
            case "apartments", "dormitory" -> Material.SMOOTH_STONE;
            case "commercial", "retail", "office" -> Material.WHITE_CONCRETE;
            case "industrial", "warehouse" -> Material.GRAY_CONCRETE;
            case "church", "cathedral", "chapel" -> Material.STONE_BRICKS;
            case "school", "university" -> Material.SANDSTONE;
            default -> Material.STONE_BRICKS;
        };
    }

    private Material roofMaterial(String buildingType) {
        return switch (buildingType) {
            case "house", "detached", "semidetached_house", "terrace", "residential" -> Material.DARK_OAK_SLAB;
            case "commercial", "retail", "office" -> Material.SMOOTH_STONE_SLAB;
            case "industrial", "warehouse" -> Material.GRAY_CONCRETE;
            default -> Material.STONE_BRICK_SLAB;
        };
    }

    private List<double[]> closeLoop(List<double[]> vertices) {
        if (vertices.isEmpty()) {
            return vertices;
        }
        double[] first = vertices.get(0);
        double[] last = vertices.get(vertices.size() - 1);
        if (first[0] == last[0] && first[1] == last[1]) {
            return vertices;
        }
        List<double[]> closed = new ArrayList<>(vertices);
        closed.add(first);
        return closed;
    }

    private boolean outsideRadius(int x, int z, int radiusBlocks) {
        return (long) x * x + (long) z * z > (long) radiusBlocks * radiusBlocks;
    }
}
