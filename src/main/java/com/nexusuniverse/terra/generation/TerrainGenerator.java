package com.nexusuniverse.terra.generation;

import com.nexusuniverse.terra.geo.ElevationClient;
import com.nexusuniverse.terra.geo.GeoPoint;
import com.nexusuniverse.terra.geo.GeoProjection;
import com.nexusuniverse.terra.geo.OsmFeature;
import com.nexusuniverse.terra.geo.OverpassClient;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Orchestrates the whole real-world-to-Minecraft pipeline: builds an
 * elevation grid, queries OSM features, and rasterizes both into a final
 * ordered list of block placements (terrain, then road sidewalks, road
 * surfaces and markings, then water, then buildings on top).
 *
 * Everything in this class runs off the main thread (network calls are
 * async, and the rasterization math itself is pure computation with no
 * Bukkit API calls) -- only the actual block.setType() calls in
 * BlockPlacementTask need to happen on the main thread.
 *
 * v0.1.5 changes, all driven by what the v0.1.4 test render actually
 * looked like in game:
 *  - Placements are deduplicated by coordinate (last write wins), which
 *    both fixes layering and cuts the block count enormously now that
 *    thickLine no longer emits ~9 duplicates per wall block.
 *  - Base terrain columns are filled solid; they previously placed
 *    stone at height-4 and dirt at height-1 with nothing between,
 *    leaving two air gaps in every single ground column.
 *  - Building walls get per-storey trim bands and properly spaced
 *    windows derived from a real along-wall index, replacing the
 *    index-into-a-duplicated-list hack that produced glass striping.
 *  - Flat roofs get a parapet edge; roads get sidewalks and dashed
 *    centre markings.
 */
public class TerrainGenerator {

    private static final int ELEVATION_SAMPLE_STEP_BLOCKS = 8;
    private static final int DEFAULT_WALL_HEIGHT = 8;
    private static final int BLOCKS_PER_LEVEL = 4;
    private static final int TERRAIN_COLUMN_DEPTH = 5;

    private final ElevationClient elevationClient;
    private final OverpassClient overpassClient;
    private final Logger logger;

    public TerrainGenerator(ElevationClient elevationClient, OverpassClient overpassClient, Logger logger) {
        this.elevationClient = elevationClient;
        this.overpassClient = overpassClient;
        this.logger = logger;
    }

    public CompletableFuture<GenerationResult> generate(double originLat, double originLon, int radiusMeters,
                                                          int worldBaseY, int worldMaxY,
                                                          java.util.function.IntConsumer elevationProgress) {
        GeoProjection projection = new GeoProjection(originLat, originLon, 1.0);
        int radiusBlocks = radiusMeters;

        List<GeoPoint> samplePoints = new ArrayList<>();
        Map<String, GeoPoint> gridPointsByKey = new HashMap<>();

        int gridExtent = ((radiusBlocks + ELEVATION_SAMPLE_STEP_BLOCKS - 1)
                / ELEVATION_SAMPLE_STEP_BLOCKS) * ELEVATION_SAMPLE_STEP_BLOCKS;
        for (int gx = -gridExtent; gx <= gridExtent; gx += ELEVATION_SAMPLE_STEP_BLOCKS) {
            for (int gz = -gridExtent; gz <= gridExtent; gz += ELEVATION_SAMPLE_STEP_BLOCKS) {
                GeoPoint point = projection.toLatLon(gx, gz);
                samplePoints.add(point);
                gridPointsByKey.put(gx + "," + gz, point);
            }
        }

        double south = projection.toLatLon(0, radiusBlocks).lat();
        double north = projection.toLatLon(0, -radiusBlocks).lat();
        double west = projection.toLatLon(-radiusBlocks, 0).lon();
        double east = projection.toLatLon(radiusBlocks, 0).lon();

        CompletableFuture<Map<GeoPoint, Double>> elevationFuture = elevationClient.lookup(samplePoints, elevationProgress);
        CompletableFuture<List<OsmFeature>> osmFuture = overpassClient.queryBoundingBox(south, west, north, east);

        return elevationFuture.thenCombine(osmFuture, (elevationMap, features) ->
                assemble(projection, elevationMap, gridPointsByKey, features,
                        radiusBlocks, gridExtent, worldBaseY, worldMaxY));
    }

    private GenerationResult assemble(GeoProjection projection, Map<GeoPoint, Double> elevationMap,
                                       Map<String, GeoPoint> gridPointsByKey, List<OsmFeature> features,
                                       int radiusBlocks, int gridExtent, int worldBaseY, int worldMaxY) {

        // Coordinate-keyed so a later feature cleanly overwrites an earlier
        // one at the same block instead of both being placed in sequence.
        Map<Long, BlockPlacement> placements = new LinkedHashMap<>();

        GeoPoint originPoint = gridPointsByKey.get("0,0");
        Double originElevation = originPoint == null ? null : elevationMap.get(originPoint);
        if (originElevation == null) {
            logger.warning("[NexusTerra] Origin elevation sample missing; defaulting to 0m. Heights may be offset.");
            originElevation = 0.0;
        }
        final double baseElevation = originElevation;

        HeightSampler heightSampler = new HeightSampler(
                elevationMap, gridPointsByKey, baseElevation, gridExtent);

        int span = radiusBlocks * 2 + 1;
        int[][] heightMap = new int[span][span];
        for (int[] row : heightMap) {
            java.util.Arrays.fill(row, GenerationResult.OUTSIDE);
        }

        // 1. Base terrain -- solid columns, no air gaps.
        for (int x = -radiusBlocks; x <= radiusBlocks; x++) {
            for (int z = -radiusBlocks; z <= radiusBlocks; z++) {
                if (x * x + z * z > radiusBlocks * radiusBlocks) {
                    continue;
                }
                int height = worldBaseY + (int) Math.round(heightSampler.heightAt(x, z));
                heightMap[x + radiusBlocks][z + radiusBlocks] = height;

                for (int depth = TERRAIN_COLUMN_DEPTH; depth >= 2; depth--) {
                    put(placements, x, height - depth, z, Material.STONE);
                }
                put(placements, x, height - 1, z, Material.DIRT);
                put(placements, x, height, z, Material.GRASS_BLOCK);
            }
        }

        // 2a. Sidewalks -- laid down for every road first, so a neighbouring
        // road's pavement can't stamp over an adjacent road's surface later.
        for (OsmFeature feature : features) {
            if (feature.category() != OsmFeature.Category.ROAD) continue;
            int width = PolygonRasterizer.widthForRoadType(feature.subtype());
            if (width < 4) continue; // footpaths and tracks don't get kerbs

            List<double[]> line = projectVertices(projection, feature.vertices());
            for (int[] xz : PolygonRasterizer.thickLine(line, width + 4)) {
                if (outsideRadius(xz, radiusBlocks)) continue;
                int height = worldBaseY + (int) Math.round(heightSampler.heightAt(xz[0], xz[1]));
                put(placements, xz[0], height, xz[1], Material.SMOOTH_STONE);
            }
        }

        // 2b. Road surfaces.
        for (OsmFeature feature : features) {
            if (feature.category() != OsmFeature.Category.ROAD) continue;
            List<double[]> line = projectVertices(projection, feature.vertices());
            int width = PolygonRasterizer.widthForRoadType(feature.subtype());
            Material roadMaterial = RoadStyle.materialFor(feature.subtype());
            for (int[] xz : PolygonRasterizer.thickLine(line, width)) {
                if (outsideRadius(xz, radiusBlocks)) continue;
                int height = worldBaseY + (int) Math.round(heightSampler.heightAt(xz[0], xz[1]));
                put(placements, xz[0], height, xz[1], roadMaterial);
            }
        }

        // 2c. Dashed centre markings on roads wide enough to warrant them.
        for (OsmFeature feature : features) {
            if (feature.category() != OsmFeature.Category.ROAD) continue;
            int width = PolygonRasterizer.widthForRoadType(feature.subtype());
            if (width < 7) continue;

            List<double[]> line = projectVertices(projection, feature.vertices());
            List<int[]> centre = PolygonRasterizer.outline(line);
            for (int[] xza : centre) {
                if (outsideRadius(xza, radiusBlocks)) continue;
                if (xza[2] % 6 >= 3) continue; // dash on, dash off
                int height = worldBaseY + (int) Math.round(heightSampler.heightAt(xza[0], xza[1]));
                put(placements, xza[0], height, xza[1], Material.WHITE_CONCRETE);
            }
        }

        // 3. Water, flattened to one surface height per feature.
        for (OsmFeature feature : features) {
            if (feature.category() != OsmFeature.Category.WATER) continue;

            List<double[]> polygon = projectVertices(projection, feature.vertices());
            List<int[]> footprint = PolygonRasterizer.fillInterior(polygon);
            if (footprint.isEmpty()) continue;

            int surfaceHeight = Integer.MAX_VALUE;
            for (int[] xz : footprint) {
                if (outsideRadius(xz, radiusBlocks)) continue;
                surfaceHeight = Math.min(surfaceHeight,
                        worldBaseY + (int) Math.round(heightSampler.heightAt(xz[0], xz[1])));
            }
            if (surfaceHeight == Integer.MAX_VALUE) continue;

            for (int[] xz : footprint) {
                if (outsideRadius(xz, radiusBlocks)) continue;
                put(placements, xz[0], surfaceHeight, xz[1], Material.WATER);
                put(placements, xz[0], surfaceHeight - 1, xz[1], Material.WATER);
                put(placements, xz[0], surfaceHeight - 2, xz[1], Material.SAND);
            }
        }

        // 4. Buildings: hollow shell with per-storey trim bands, spaced
        // windows, an interior floor, and a parapet around the roof edge.
        int buildingsPlaced = 0;
        for (OsmFeature feature : features) {
            if (feature.category() != OsmFeature.Category.BUILDING) continue;

            List<double[]> rawFootprint = projectVertices(projection, feature.vertices());
            if (rawFootprint.size() < 3) continue;

            int headroom = worldMaxY - worldBaseY - 4;
            BuildingStyle style = BuildingStyle.forFeature(feature, DEFAULT_WALL_HEIGHT, BLOCKS_PER_LEVEL, headroom);

            List<double[]> closed = closeLoop(rawFootprint);
            List<int[]> perimeter = PolygonRasterizer.outline(closed);
            List<int[]> interior = PolygonRasterizer.fillInterior(rawFootprint);
            if (perimeter.isEmpty()) continue;

            double[] first = rawFootprint.get(0);
            int baseHeight = worldBaseY + (int) Math.round(
                    heightSampler.heightAt((int) Math.round(first[0]), (int) Math.round(first[1])));

            // Interior ground floor, so buildings aren't open to bare grass.
            for (int[] xz : interior) {
                if (outsideRadius(xz, radiusBlocks)) continue;
                put(placements, xz[0], baseHeight, xz[1], style.trimMaterial);
            }

            // Pick one doorway roughly a quarter of the way round the
            // perimeter -- index 0 tends to land on a corner, which looked
            // wrong in the v0.1.4 render.
            int doorIndex = Math.max(1, perimeter.size() / 4);

            for (int level = 1; level <= style.wallHeight; level++) {
                for (int[] xza : perimeter) {
                    if (outsideRadius(xza, radiusBlocks)) continue;
                    Material material = wallMaterialAt(style, level, xza[2], doorIndex);
                    if (material == null) continue; // doorway gap: leave it open
                    put(placements, xza[0], baseHeight + level, xza[1], material);
                }
            }

            int roofY = baseHeight + style.wallHeight + 1;
            if (roofY < worldMaxY - 1) {
                for (int[] xz : interior) {
                    if (outsideRadius(xz, radiusBlocks)) continue;
                    put(placements, xz[0], roofY, xz[1], style.roofMaterial);
                }
                // Parapet: a low lip around the roof edge. Cheap, but it's
                // what stops a flat roof reading as a bare tabletop.
                for (int[] xza : perimeter) {
                    if (outsideRadius(xza, radiusBlocks)) continue;
                    put(placements, xza[0], roofY, xza[1], style.trimMaterial);
                    if (roofY + 1 < worldMaxY - 1) {
                        put(placements, xza[0], roofY + 1, xza[1], style.trimMaterial);
                    }
                }
            }
            buildingsPlaced++;
        }

        logger.info("[NexusTerra] Assembled " + placements.size() + " block placements from "
                + features.size() + " OSM feature(s), including " + buildingsPlaced + " building(s).");

        return new GenerationResult(new ArrayList<>(placements.values()), heightMap, radiusBlocks);
    }

    /**
     * Decides what a single wall block should be.
     *
     * alongIndex is a true walk-distance around the perimeter (from
     * PolygonRasterizer.outline), which is what makes evenly spaced
     * windows possible -- v0.1.4 used a raw list index into a list that
     * contained ~9 duplicates of every block, so the pattern smeared into
     * vertical stripes instead.
     *
     * Returns null to mean "leave this block empty" (the doorway).
     */
    private Material wallMaterialAt(BuildingStyle style, int level, int alongIndex, int doorIndex) {
        int storeyHeight = Math.max(3, style.storeyHeight);
        int levelInStorey = (level - 1) % storeyHeight;

        // Doorway: two blocks tall, two wide, on the ground storey.
        boolean nearDoor = Math.abs(alongIndex - doorIndex) <= 1;
        if (nearDoor && level <= 2) {
            return null;
        }

        // Floor line: a solid band of trim at each storey boundary.
        if (levelInStorey == 0) {
            return style.trimMaterial;
        }

        // Roofline course stays solid so the parapet has something to sit on.
        if (level >= style.wallHeight) {
            return style.wallMaterial;
        }

        // Windows: a 2-wide opening every 5 blocks along the wall, occupying
        // the middle of each storey rather than the full height.
        boolean windowColumn = Math.floorMod(alongIndex, 5) < 2;
        boolean windowRow = levelInStorey >= 1 && levelInStorey <= storeyHeight - 2;
        if (windowColumn && windowRow) {
            return style.windowMaterial;
        }

        return style.wallMaterial;
    }

    private void put(Map<Long, BlockPlacement> placements, int x, int y, int z, Material material) {
        placements.put(packXYZ(x, y, z), new BlockPlacement(x, y, z, material));
    }

    private static long packXYZ(int x, int y, int z) {
        return ((long) (x & 0xFFFF) << 32) | ((long) (z & 0xFFFF) << 16) | (long) ((y + 2048) & 0xFFFF);
    }

    private List<double[]> projectVertices(GeoProjection projection, List<GeoPoint> vertices) {
        List<double[]> result = new ArrayList<>(vertices.size());
        for (GeoPoint v : vertices) {
            result.add(projection.toBlockOffset(v.lat(), v.lon()));
        }
        return result;
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

    private boolean outsideRadius(int[] xz, int radiusBlocks) {
        return (long) xz[0] * xz[0] + (long) xz[1] * xz[1] > (long) radiusBlocks * radiusBlocks;
    }

    /** Bilinear interpolation over the sparse elevation sample grid. */
    private record HeightSampler(Map<GeoPoint, Double> elevationMap, Map<String, GeoPoint> gridPointsByKey,
                                  double baseElevation, int gridExtent) {

        double heightAt(int x, int z) {
            int gx0 = Math.floorDiv(x, ELEVATION_SAMPLE_STEP_BLOCKS) * ELEVATION_SAMPLE_STEP_BLOCKS;
            int gz0 = Math.floorDiv(z, ELEVATION_SAMPLE_STEP_BLOCKS) * ELEVATION_SAMPLE_STEP_BLOCKS;
            int gx1 = gx0 + ELEVATION_SAMPLE_STEP_BLOCKS;
            int gz1 = gz0 + ELEVATION_SAMPLE_STEP_BLOCKS;

            double h00 = sample(gx0, gz0);
            double h10 = sample(gx1, gz0);
            double h01 = sample(gx0, gz1);
            double h11 = sample(gx1, gz1);

            double tx = (double) (x - gx0) / ELEVATION_SAMPLE_STEP_BLOCKS;
            double tz = (double) (z - gz0) / ELEVATION_SAMPLE_STEP_BLOCKS;

            double top = h00 + (h10 - h00) * tx;
            double bottom = h01 + (h11 - h01) * tx;
            return (top + (bottom - top) * tz) - baseElevation;
        }

        private double sample(int gx, int gz) {
            int clampedX = Math.max(-gridExtent, Math.min(gridExtent, gx));
            int clampedZ = Math.max(-gridExtent, Math.min(gridExtent, gz));
            GeoPoint point = gridPointsByKey.get(clampedX + "," + clampedZ);
            if (point == null) {
                return baseElevation;
            }
            Double elevation = elevationMap.get(point);
            return elevation != null ? elevation : baseElevation;
        }
    }
}
