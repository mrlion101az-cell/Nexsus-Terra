package com.nexusuniverse.terra.generation;

import com.nexusuniverse.terra.geo.ElevationClient;
import com.nexusuniverse.terra.geo.GeoPoint;
import com.nexusuniverse.terra.geo.GeoProjection;
import com.nexusuniverse.terra.geo.OsmFeature;
import com.nexusuniverse.terra.geo.OverpassClient;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Orchestrates the whole real-world-to-Minecraft pipeline: builds an
 * elevation grid, queries OSM features, and rasterizes both into a final
 * ordered list of block placements (terrain first, then roads, then
 * water, then buildings on top -- so overlapping features layer
 * correctly when BlockPlacementTask applies them in order).
 *
 * Everything in this class runs off the main thread (network calls are
 * async, and the rasterization math itself is pure computation with no
 * Bukkit API calls) -- only the actual block.setType() calls in
 * BlockPlacementTask need to happen on the main thread.
 */
public class TerrainGenerator {

    private static final int ELEVATION_SAMPLE_STEP_BLOCKS = 8;
    private static final int DEFAULT_WALL_HEIGHT = 6;
    private static final int BLOCKS_PER_LEVEL = 3;

    private final ElevationClient elevationClient;
    private final OverpassClient overpassClient;
    private final Logger logger;

    public TerrainGenerator(ElevationClient elevationClient, OverpassClient overpassClient, Logger logger) {
        this.elevationClient = elevationClient;
        this.overpassClient = overpassClient;
        this.logger = logger;
    }

    public CompletableFuture<List<BlockPlacement>> generate(double originLat, double originLon, int radiusMeters, int worldBaseY) {
        GeoProjection projection = new GeoProjection(originLat, originLon, 1.0);
        int radiusBlocks = radiusMeters;

        List<GeoPoint> samplePoints = new ArrayList<>();
        Map<String, GeoPoint> gridPointsByKey = new HashMap<>();
        for (int gx = -radiusBlocks; gx <= radiusBlocks; gx += ELEVATION_SAMPLE_STEP_BLOCKS) {
            for (int gz = -radiusBlocks; gz <= radiusBlocks; gz += ELEVATION_SAMPLE_STEP_BLOCKS) {
                GeoPoint point = projection.toLatLon(gx, gz);
                samplePoints.add(point);
                gridPointsByKey.put(gx + "," + gz, point);
            }
        }

        double south = projection.toLatLon(0, radiusBlocks).lat();
        double north = projection.toLatLon(0, -radiusBlocks).lat();
        double west = projection.toLatLon(-radiusBlocks, 0).lon();
        double east = projection.toLatLon(radiusBlocks, 0).lon();

        CompletableFuture<Map<GeoPoint, Double>> elevationFuture = elevationClient.lookup(samplePoints);
        CompletableFuture<List<OsmFeature>> osmFuture = overpassClient.queryBoundingBox(south, west, north, east);

        return elevationFuture.thenCombine(osmFuture, (elevationMap, features) ->
                assemble(projection, elevationMap, gridPointsByKey, features, radiusBlocks, worldBaseY));
    }

    private List<BlockPlacement> assemble(GeoProjection projection, Map<GeoPoint, Double> elevationMap,
                                           Map<String, GeoPoint> gridPointsByKey, List<OsmFeature> features,
                                           int radiusBlocks, int worldBaseY) {
        List<BlockPlacement> placements = new ArrayList<>();

        Double originElevation = elevationMap.get(gridPointsByKey.get("0,0"));
        if (originElevation == null) {
            logger.warning("[NexusTerra] Origin elevation sample missing; defaulting to 0m. Heights may be offset.");
            originElevation = 0.0;
        }
        final double baseElevation = originElevation;

        HeightSampler heightSampler = new HeightSampler(elevationMap, gridPointsByKey, baseElevation, radiusBlocks);

        // 1. Base terrain, one column per block within the radius circle.
        for (int x = -radiusBlocks; x <= radiusBlocks; x++) {
            for (int z = -radiusBlocks; z <= radiusBlocks; z++) {
                if (x * x + z * z > radiusBlocks * radiusBlocks) {
                    continue;
                }
                int height = worldBaseY + (int) Math.round(heightSampler.heightAt(x, z));
                placements.add(new BlockPlacement(x, height - 4, z, Material.STONE));
                placements.add(new BlockPlacement(x, height - 1, z, Material.DIRT));
                placements.add(new BlockPlacement(x, height, z, Material.GRASS_BLOCK));
            }
        }

        // 2. Roads.
        for (OsmFeature feature : features) {
            if (feature.category() != OsmFeature.Category.ROAD) {
                continue;
            }
            List<double[]> line = projectVertices(projection, feature.vertices());
            int width = PolygonRasterizer.widthForRoadType(feature.subtype());
            for (int[] xz : PolygonRasterizer.thickLine(line, width)) {
                if (outsideRadius(xz, radiusBlocks)) {
                    continue;
                }
                int height = worldBaseY + (int) Math.round(heightSampler.heightAt(xz[0], xz[1]));
                placements.add(new BlockPlacement(xz[0], height, xz[1], Material.GRAVEL));
            }
        }

        // 3. Water. V1 follows terrain height rather than flattening to a
        // single lake surface -- known limitation, noted in the README.
        for (OsmFeature feature : features) {
            if (feature.category() != OsmFeature.Category.WATER) {
                continue;
            }
            List<double[]> polygon = projectVertices(projection, feature.vertices());
            for (int[] xz : PolygonRasterizer.fillInterior(polygon)) {
                if (outsideRadius(xz, radiusBlocks)) {
                    continue;
                }
                int height = worldBaseY + (int) Math.round(heightSampler.heightAt(xz[0], xz[1]));
                placements.add(new BlockPlacement(xz[0], height, xz[1], Material.WATER));
                placements.add(new BlockPlacement(xz[0], height - 1, xz[1], Material.WATER));
            }
        }

        // 4. Buildings -- hollow shell (perimeter walls per level + flat roof cap),
        // not a solid block mass, so the interior is actually enterable.
        for (OsmFeature feature : features) {
            if (feature.category() != OsmFeature.Category.BUILDING) {
                continue;
            }
            List<double[]> footprint = projectVertices(projection, feature.vertices());
            int wallHeight = DEFAULT_WALL_HEIGHT;

            List<int[]> perimeter = PolygonRasterizer.thickLine(closeLoop(footprint), 1);
            List<int[]> roofCap = PolygonRasterizer.fillInterior(footprint);

            // Use the footprint's first vertex to determine a single base
            // height for the whole building, rather than following terrain
            // noise per-wall-block -- buildings should have a flat base.
            double[] first = footprint.get(0);
            int baseHeight = worldBaseY + (int) Math.round(heightSampler.heightAt((int) Math.round(first[0]), (int) Math.round(first[1])));

            for (int level = 1; level <= wallHeight; level++) {
                for (int[] xz : perimeter) {
                    if (outsideRadius(xz, radiusBlocks)) {
                        continue;
                    }
                    placements.add(new BlockPlacement(xz[0], baseHeight + level, xz[1], Material.STONE_BRICKS));
                }
            }
            for (int[] xz : roofCap) {
                if (outsideRadius(xz, radiusBlocks)) {
                    continue;
                }
                placements.add(new BlockPlacement(xz[0], baseHeight + wallHeight + 1, xz[1], Material.STONE_BRICK_SLAB));
            }
        }

        logger.info("[NexusTerra] Assembled " + placements.size() + " block placements from " + features.size() + " OSM feature(s).");
        return placements;
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
                                  double baseElevation, int radiusBlocks) {

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
            int clampedX = Math.max(-radiusBlocks, Math.min(radiusBlocks, gx));
            int clampedZ = Math.max(-radiusBlocks, Math.min(radiusBlocks, gz));
            GeoPoint point = gridPointsByKey.get(clampedX + "," + clampedZ);
            if (point == null) {
                return baseElevation;
            }
            Double elevation = elevationMap.get(point);
            return elevation != null ? elevation : baseElevation;
        }
    }
}
