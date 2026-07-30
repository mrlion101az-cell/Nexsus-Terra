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
        return generate(originLat, originLon, radiusMeters, worldBaseY, null);
    }

    public CompletableFuture<List<BlockPlacement>> generate(double originLat, double originLon, int radiusMeters,
                                                              int worldBaseY, java.util.function.IntConsumer elevationProgress) {
        GeoProjection projection = new GeoProjection(originLat, originLon, 1.0);
        int radiusBlocks = radiusMeters;

        List<GeoPoint> samplePoints = new ArrayList<>();
        Map<String, GeoPoint> gridPointsByKey = new HashMap<>();

        // Align the sample grid to zero and extend it to the next full sample
        // step. The original grid started at -radius, which meant radii such
        // as 300 never sampled (0,0). Calling ConcurrentHashMap#get(null)
        // later caused the exact "key is null" crash seen in console.
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
                        radiusBlocks, gridExtent, worldBaseY));
    }

    private List<BlockPlacement> assemble(GeoProjection projection, Map<GeoPoint, Double> elevationMap,
                                           Map<String, GeoPoint> gridPointsByKey, List<OsmFeature> features,
                                           int radiusBlocks, int gridExtent, int worldBaseY) {
        List<BlockPlacement> placements = new ArrayList<>();

        GeoPoint originPoint = gridPointsByKey.get("0,0");
        Double originElevation = originPoint == null ? null : elevationMap.get(originPoint);
        if (originElevation == null) {
            logger.warning("[NexusTerra] Origin elevation sample missing; defaulting to 0m. Heights may be offset.");
            originElevation = 0.0;
        }
        final double baseElevation = originElevation;

        HeightSampler heightSampler = new HeightSampler(
                elevationMap, gridPointsByKey, baseElevation, gridExtent);

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
            Material roadMaterial = RoadStyle.materialFor(feature.subtype());
            for (int[] xz : PolygonRasterizer.thickLine(line, width)) {
                if (outsideRadius(xz, radiusBlocks)) {
                    continue;
                }
                int height = worldBaseY + (int) Math.round(heightSampler.heightAt(xz[0], xz[1]));
                placements.add(new BlockPlacement(xz[0], height, xz[1], roadMaterial));
            }
        }

        // 3. Water. Flattened to the lowest terrain point under each water
        // feature's footprint (like a real lake sitting in a basin) instead
        // of following per-column terrain noise, which used to produce a
        // stair-stepped mess rather than a recognizable flat lake surface.
        for (OsmFeature feature : features) {
            if (feature.category() != OsmFeature.Category.WATER) {
                continue;
            }
            List<double[]> polygon = projectVertices(projection, feature.vertices());
            List<int[]> footprint = PolygonRasterizer.fillInterior(polygon);
            if (footprint.isEmpty()) {
                continue;
            }

            int surfaceHeight = Integer.MAX_VALUE;
            for (int[] xz : footprint) {
                if (outsideRadius(xz, radiusBlocks)) {
                    continue;
                }
                surfaceHeight = Math.min(surfaceHeight,
                        worldBaseY + (int) Math.round(heightSampler.heightAt(xz[0], xz[1])));
            }
            if (surfaceHeight == Integer.MAX_VALUE) {
                continue; // whole footprint was outside the radius
            }

            for (int[] xz : footprint) {
                if (outsideRadius(xz, radiusBlocks)) {
                    continue;
                }
                placements.add(new BlockPlacement(xz[0], surfaceHeight, xz[1], Material.WATER));
                placements.add(new BlockPlacement(xz[0], surfaceHeight - 1, xz[1], Material.WATER));
                placements.add(new BlockPlacement(xz[0], surfaceHeight - 2, xz[1], Material.SAND));
            }
        }

        // 4. Buildings -- hollow shell (perimeter walls per level + flat roof cap),
        // not a solid block mass, so the interior is actually enterable.
        // v0.1.4: wall height now comes from real OSM height/building:levels
        // tags instead of a flat 6 for every building, wall/roof material
        // varies by building type, and a simple periodic window pattern
        // breaks up what used to read as one solid grey slab.
        for (OsmFeature feature : features) {
            if (feature.category() != OsmFeature.Category.BUILDING) {
                continue;
            }
            List<double[]> footprint = projectVertices(projection, feature.vertices());
            BuildingStyle style = BuildingStyle.forFeature(feature, DEFAULT_WALL_HEIGHT, BLOCKS_PER_LEVEL);

            List<int[]> perimeter = PolygonRasterizer.thickLine(closeLoop(footprint), 1);
            List<int[]> roofCap = PolygonRasterizer.fillInterior(footprint);

            // Use the footprint's first vertex to determine a single base
            // height for the whole building, rather than following terrain
            // noise per-wall-block -- buildings should have a flat base.
            double[] first = footprint.get(0);
            int baseHeight = worldBaseY + (int) Math.round(heightSampler.heightAt((int) Math.round(first[0]), (int) Math.round(first[1])));

            for (int level = 1; level <= style.wallHeight; level++) {
                for (int i = 0; i < perimeter.size(); i++) {
                    int[] xz = perimeter.get(i);
                    if (outsideRadius(xz, radiusBlocks)) {
                        continue;
                    }
                    Material material = wallMaterialAt(style, level, i);
                    placements.add(new BlockPlacement(xz[0], baseHeight + level, xz[1], material));
                }
            }
            for (int[] xz : roofCap) {
                if (outsideRadius(xz, radiusBlocks)) {
                    continue;
                }
                placements.add(new BlockPlacement(xz[0], baseHeight + style.wallHeight + 1, xz[1], style.roofMaterial));
            }
        }

        logger.info("[NexusTerra] Assembled " + placements.size() + " block placements from " + features.size() + " OSM feature(s).");
        return placements;
    }

    /**
     * Simple periodic window pattern: every 4th column along the wall gets a
     * glass opening on levels 2 and up (never the ground floor, so it still
     * reads as a wall from outside at eye level), and a single door-sized
     * gap is cut at the very first perimeter column on level 1. Nowhere near
     * as expressive as real per-building window layouts would be, but it's
     * enough to stop every wall reading as one flat slab of stone.
     */
    private Material wallMaterialAt(BuildingStyle style, int level, int perimeterIndex) {
        if (level == 1 && perimeterIndex == 0) {
            return Material.AIR; // doorway
        }
        boolean windowColumn = perimeterIndex % 4 == 0;
        boolean windowLevel = level >= 2 && level < style.wallHeight; // not ground floor, not roofline
        if (windowColumn && windowLevel) {
            return Material.GLASS;
        }
        return style.wallMaterial;
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
