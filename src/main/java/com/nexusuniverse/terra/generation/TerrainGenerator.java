package com.nexusuniverse.terra.generation;

import com.nexusuniverse.terra.TerraConfig;
import com.nexusuniverse.terra.geo.ElevationClient;
import com.nexusuniverse.terra.geo.GeoPoint;
import com.nexusuniverse.terra.geo.GeoProjection;
import com.nexusuniverse.terra.geo.OsmFeature;
import com.nexusuniverse.terra.geo.OverpassClient;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Orchestrates the real-world-to-Minecraft pipeline: builds an
 * elevation grid, queries OSM features, and rasterizes both into an
 * ordered list of block placements.
 *
 * Layer order matters and is deliberate. Later passes overwrite earlier
 * ones at the same coordinate (placements are keyed by position), so
 * the sequence runs: terrain, landuse surfaces, railways, pavements,
 * road surfaces, markings, water, bridges, barriers, then buildings.
 * Bridges come after water on purpose -- a bridge deck must survive the
 * river it crosses.
 *
 * Everything here runs off the main thread; only the block writes in
 * BlockPlacementTask touch the world.
 */
public class TerrainGenerator {

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
                                                          int worldBaseY, int worldMaxY, TerraConfig config,
                                                          java.util.function.IntConsumer elevationProgress) {
        GeoProjection projection = new GeoProjection(originLat, originLon, 1.0);
        int radiusBlocks = radiusMeters;
        int step = config.elevationSampleStep();

        List<GeoPoint> samplePoints = new ArrayList<>();
        Map<String, GeoPoint> gridPointsByKey = new HashMap<>();

        int gridExtent = ((radiusBlocks + step - 1) / step) * step;
        for (int gx = -gridExtent; gx <= gridExtent; gx += step) {
            for (int gz = -gridExtent; gz <= gridExtent; gz += step) {
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
                        radiusBlocks, gridExtent, worldBaseY, worldMaxY, config));
    }

    private GenerationResult assemble(GeoProjection projection, Map<GeoPoint, Double> elevationMap,
                                       Map<String, GeoPoint> gridPointsByKey, List<OsmFeature> features,
                                       int radiusBlocks, int gridExtent, int worldBaseY, int worldMaxY,
                                       TerraConfig config) {

        Map<Long, BlockPlacement> placements = new LinkedHashMap<>();

        GeoPoint originPoint = gridPointsByKey.get("0,0");
        Double originElevation = originPoint == null ? null : elevationMap.get(originPoint);
        if (originElevation == null) {
            logger.warning("[NexusTerra] Origin elevation sample missing; defaulting to 0m. Heights may be offset.");
            originElevation = 0.0;
        }

        HeightSampler heightSampler = new HeightSampler(
                elevationMap, gridPointsByKey, originElevation, gridExtent, config.elevationSampleStep());

        int span = radiusBlocks * 2 + 1;
        int[][] heightMap = new int[span][span];
        for (int[] row : heightMap) {
            java.util.Arrays.fill(row, GenerationResult.OUTSIDE);
        }

        // ---- 1. Base terrain -------------------------------------------------
        for (int x = -radiusBlocks; x <= radiusBlocks; x++) {
            for (int z = -radiusBlocks; z <= radiusBlocks; z++) {
                if (x * x + z * z > radiusBlocks * radiusBlocks) continue;
                int height = worldBaseY + (int) Math.round(heightSampler.heightAt(x, z));
                heightMap[x + radiusBlocks][z + radiusBlocks] = height;

                for (int depth = TERRAIN_COLUMN_DEPTH; depth >= 2; depth--) {
                    put(placements, x, height - depth, z, Material.STONE);
                }
                put(placements, x, height - 1, z, Material.DIRT);
                put(placements, x, height, z, Material.GRASS_BLOCK);
            }
        }

        java.util.function.BiFunction<Integer, Integer, Integer> groundAt =
                (x, z) -> worldBaseY + (int) Math.round(heightSampler.heightAt(x, z));

        // ---- 2. Landuse, leisure and parking surfaces ------------------------
        if (config.landuse()) {
            for (OsmFeature feature : features) {
                if (feature.category() != OsmFeature.Category.LANDUSE) continue;

                Material surface = SurfaceStyle.surfaceFor(feature.subtype());
                int treeSpacing = config.trees() ? SurfaceStyle.treeSpacingFor(feature.subtype()) : 0;
                if (surface == null && treeSpacing == 0) continue;

                List<int[]> area = PolygonRasterizer.fillRings(projectRings(projection, feature));
                boolean markings = SurfaceStyle.hasParkingMarkings(feature.subtype());

                for (int[] xz : area) {
                    if (outsideRadius(xz, radiusBlocks)) continue;
                    int height = groundAt.apply(xz[0], xz[1]);

                    if (surface != null) {
                        Material material = surface;
                        if (markings && Math.floorMod(xz[0], 6) == 0 && Math.floorMod(xz[1], 3) != 0) {
                            material = Material.WHITE_CONCRETE;
                        }
                        put(placements, xz[0], height, xz[1], material);
                    }

                    if (treeSpacing > 0
                            && Math.floorMod(xz[0], treeSpacing) == 0
                            && Math.floorMod(xz[1], treeSpacing) == 0) {
                        emitAll(placements, StreetFurniture.tree(xz[0], height, xz[1], worldMaxY,
                                StreetFurniture.hash(xz[0], xz[1])), radiusBlocks);
                    }
                }
            }
        }

        // ---- 3. Railways -----------------------------------------------------
        int tracksPlaced = 0;
        if (config.railways()) {
            for (OsmFeature feature : features) {
                if (feature.category() != OsmFeature.Category.RAILWAY) continue;
                if (!TrackStyle.isRenderable(feature.subtype())) continue;
                if (config.skipTunnels() && feature.isTunnel()) continue;

                List<double[]> line = projectVertices(projection, feature.vertices());
                int bedWidth = TrackStyle.bedWidth(feature.subtype());
                Material bed = TrackStyle.bedMaterial(feature.subtype());

                for (int[] xz : PolygonRasterizer.thickLine(line, bedWidth)) {
                    if (outsideRadius(xz, radiusBlocks)) continue;
                    put(placements, xz[0], groundAt.apply(xz[0], xz[1]), xz[1], bed);
                }
                // Sleepers across the bed, then the running rails on top.
                for (int[] xza : PolygonRasterizer.outline(line)) {
                    if (outsideRadius(xza, radiusBlocks)) continue;
                    int y = groundAt.apply(xza[0], xza[1]);
                    if (xza[2] % 3 == 0) {
                        put(placements, xza[0], y, xza[1], TrackStyle.sleeperMaterial());
                    }
                    if (y + 1 < worldMaxY - 1) {
                        put(placements, xza[0], y + 1, xza[1], TrackStyle.railMaterial());
                    }
                }
                tracksPlaced++;
            }
        }

        // ---- 4. Road pavements, surfaces, markings ---------------------------
        Map<Long, int[]> sidewalkCells = new LinkedHashMap<>();
        Set<Long> roadCells = new HashSet<>();
        Set<Long> bridgeCells = new HashSet<>();

        if (config.sidewalks()) {
            for (OsmFeature feature : features) {
                if (!isBuildableRoad(feature, config)) continue;
                if (feature.isBridge()) continue; // bridges carry their own edges
                int width = PolygonRasterizer.widthForRoadType(feature.subtype());
                if (width < config.sidewalkMinRoadWidth()) continue;

                List<double[]> line = projectVertices(projection, feature.vertices());
                for (int[] xz : PolygonRasterizer.thickLine(line, width + 4)) {
                    if (outsideRadius(xz, radiusBlocks)) continue;
                    int height = groundAt.apply(xz[0], xz[1]);
                    put(placements, xz[0], height, xz[1], Material.SMOOTH_STONE);
                    sidewalkCells.putIfAbsent(packXZ(xz[0], xz[1]), new int[]{xz[0], xz[1], height});
                }
            }
        }

        for (OsmFeature feature : features) {
            if (!isBuildableRoad(feature, config)) continue;
            if (feature.isBridge() && config.bridges()) continue; // handled in the bridge pass

            List<double[]> line = projectVertices(projection, feature.vertices());
            int width = PolygonRasterizer.widthForRoadType(feature.subtype());
            Material roadMaterial = RoadStyle.materialFor(feature.subtype());
            for (int[] xz : PolygonRasterizer.thickLine(line, width)) {
                if (outsideRadius(xz, radiusBlocks)) continue;
                put(placements, xz[0], groundAt.apply(xz[0], xz[1]), xz[1], roadMaterial);
                roadCells.add(packXZ(xz[0], xz[1]));
            }
        }

        if (config.centreMarkings()) {
            for (OsmFeature feature : features) {
                if (!isBuildableRoad(feature, config)) continue;
                if (feature.isBridge() && config.bridges()) continue;
                int width = PolygonRasterizer.widthForRoadType(feature.subtype());
                if (width < config.centreMarkingMinWidth()) continue;

                List<double[]> line = projectVertices(projection, feature.vertices());
                for (int[] xza : PolygonRasterizer.outline(line)) {
                    if (outsideRadius(xza, radiusBlocks)) continue;
                    if (xza[2] % 6 >= 3) continue;
                    put(placements, xza[0], groundAt.apply(xza[0], xza[1]), xza[1], Material.WHITE_CONCRETE);
                }
            }
        }

        // ---- 5. Water --------------------------------------------------------
        if (config.water()) {
            for (OsmFeature feature : features) {
                if (feature.category() != OsmFeature.Category.WATER) continue;

                List<int[]> footprint = PolygonRasterizer.fillRings(projectRings(projection, feature));
                if (footprint.isEmpty()) continue;

                int surfaceHeight = Integer.MAX_VALUE;
                for (int[] xz : footprint) {
                    if (outsideRadius(xz, radiusBlocks)) continue;
                    surfaceHeight = Math.min(surfaceHeight, groundAt.apply(xz[0], xz[1]));
                }
                if (surfaceHeight == Integer.MAX_VALUE) continue;

                for (int[] xz : footprint) {
                    if (outsideRadius(xz, radiusBlocks)) continue;
                    put(placements, xz[0], surfaceHeight, xz[1], Material.WATER);
                    put(placements, xz[0], surfaceHeight - 1, xz[1], Material.WATER);
                    put(placements, xz[0], surfaceHeight - 2, xz[1], Material.SAND);
                }
            }
        }

        // ---- 6. Bridges (after water, so decks survive the river) ------------
        int bridgesPlaced = 0;
        if (config.bridges()) {
            for (OsmFeature feature : features) {
                if (feature.category() != OsmFeature.Category.ROAD || !feature.isBridge()) continue;
                if (config.skipTunnels() && feature.isTunnel()) continue;

                List<double[]> line = projectVertices(projection, feature.vertices());
                if (line.size() < 2) continue;
                int width = PolygonRasterizer.widthForRoadType(feature.subtype());

                // Extra lift per positive layer keeps stacked overpasses apart.
                int clearance = config.bridgeClearance() + Math.max(0, feature.layer()) * 5;

                BridgeBuilder.Deck deck = BridgeBuilder.build(line, width,
                        RoadStyle.materialFor(feature.subtype()), Material.STONE_BRICKS,
                        groundAt, clearance, worldMaxY);

                emitAll(placements, deck.placements(), radiusBlocks);
                bridgeCells.addAll(deck.deckCells());
                bridgesPlaced++;
            }
        }

        // ---- 7. Lamp posts (pavement only, never on a bridge deck) -----------
        int lamps = 0;
        if (config.lampPosts()) {
            for (Map.Entry<Long, int[]> entry : sidewalkCells.entrySet()) {
                if (roadCells.contains(entry.getKey()) || bridgeCells.contains(entry.getKey())) continue;
                int[] cell = entry.getValue();
                if (Math.floorMod(cell[0], config.lampSpacing()) != 0
                        || Math.floorMod(cell[1], config.lampSpacing()) != 0) continue;
                emitAll(placements, StreetFurniture.lampPost(cell[0], cell[2], cell[1], worldMaxY), radiusBlocks);
                lamps++;
            }
        }

        // ---- 8. Barriers -----------------------------------------------------
        int barriersPlaced = 0;
        if (config.barriers()) {
            for (OsmFeature feature : features) {
                if (feature.category() != OsmFeature.Category.BARRIER) continue;
                Material material = BarrierStyle.materialFor(feature.subtype());
                if (material == null) continue;
                int height = BarrierStyle.heightFor(feature.subtype());

                List<double[]> line = projectVertices(projection, feature.vertices());
                for (int[] xz : PolygonRasterizer.thickLine(line, 1)) {
                    if (outsideRadius(xz, radiusBlocks)) continue;
                    long key = packXZ(xz[0], xz[1]);
                    // Never fence off a carriageway or a bridge deck.
                    if (roadCells.contains(key) || bridgeCells.contains(key)) continue;
                    int ground = groundAt.apply(xz[0], xz[1]);
                    for (int i = 1; i <= height; i++) {
                        if (ground + i >= worldMaxY - 1) break;
                        put(placements, xz[0], ground + i, xz[1], material);
                    }
                }
                barriersPlaced++;
            }
        }

        // ---- 9. Buildings ----------------------------------------------------
        int buildingsPlaced = 0;
        for (OsmFeature feature : features) {
            if (feature.category() != OsmFeature.Category.BUILDING) continue;

            List<List<double[]>> rings = projectRings(projection, feature);
            if (rings.isEmpty() || rings.get(0).size() < 3) continue;
            List<double[]> outerRing = rings.get(0);

            int headroom = Math.min(config.maxWallHeight(), worldMaxY - worldBaseY - 4);
            BuildingStyle style = BuildingStyle.forFeature(feature,
                    config.defaultWallHeight(), config.blocksPerLevel(), headroom);

            List<int[]> perimeter = PolygonRasterizer.outline(closeLoop(outerRing));
            List<int[]> interior = PolygonRasterizer.fillRings(rings);
            if (perimeter.isEmpty()) continue;

            double[] first = outerRing.get(0);
            int baseHeight = groundAt.apply((int) Math.round(first[0]), (int) Math.round(first[1]));

            for (int[] xz : interior) {
                if (outsideRadius(xz, radiusBlocks)) continue;
                put(placements, xz[0], baseHeight, xz[1], style.trimMaterial);
            }

            int doorIndex = Math.max(1, perimeter.size() / 4);

            for (int level = 1; level <= style.wallHeight; level++) {
                for (int[] xza : perimeter) {
                    if (outsideRadius(xza, radiusBlocks)) continue;
                    Material material = wallMaterialAt(style, level, xza[2], doorIndex, config);
                    if (material == null) continue;
                    put(placements, xza[0], baseHeight + level, xza[1], material);
                }
            }

            int roofY = baseHeight + style.wallHeight + 1;
            if (roofY < worldMaxY - 1) {
                if (style.pitchedRoof && config.pitchedRoofs()) {
                    emitAll(placements, RoofBuilder.pitched(interior, roofY, style.roofMaterial, worldMaxY), radiusBlocks);
                    emitAll(placements, RoofBuilder.pitched(stripIndex(perimeter), roofY - 1,
                            style.wallMaterial, worldMaxY), radiusBlocks);
                } else {
                    for (int[] xz : interior) {
                        if (outsideRadius(xz, radiusBlocks)) continue;
                        put(placements, xz[0], roofY, xz[1], style.roofMaterial);
                    }
                    for (int[] xza : perimeter) {
                        if (outsideRadius(xza, radiusBlocks)) continue;
                        put(placements, xza[0], roofY, xza[1], style.trimMaterial);
                        if (roofY + 1 < worldMaxY - 1) {
                            put(placements, xza[0], roofY + 1, xza[1], style.trimMaterial);
                        }
                    }
                    if (config.rooftopDetails()) {
                        emitAll(placements, RoofBuilder.flatRoofDetails(interior, roofY, style.wallHeight,
                                style.trimMaterial, worldMaxY), radiusBlocks);
                    }
                }
            }
            buildingsPlaced++;
        }

        logger.info("[NexusTerra] Assembled " + placements.size() + " block placements from "
                + features.size() + " OSM feature(s): " + buildingsPlaced + " building(s), "
                + bridgesPlaced + " bridge(s), " + tracksPlaced + " rail line(s), "
                + barriersPlaced + " barrier(s), " + lamps + " lamp post(s).");

        return new GenerationResult(new ArrayList<>(placements.values()), heightMap, radiusBlocks);
    }

    private boolean isBuildableRoad(OsmFeature feature, TerraConfig config) {
        if (feature.category() != OsmFeature.Category.ROAD) return false;
        if (config.skipTunnels() && feature.isTunnel()) return false;
        return true;
    }

    private Material wallMaterialAt(BuildingStyle style, int level, int alongIndex, int doorIndex, TerraConfig config) {
        int storeyHeight = Math.max(3, style.storeyHeight);
        int levelInStorey = (level - 1) % storeyHeight;

        boolean nearDoor = Math.abs(alongIndex - doorIndex) <= 1;
        if (nearDoor && level <= 2) {
            return null;
        }

        if (config.shopfronts() && style.storefront && level < storeyHeight && level < style.wallHeight) {
            boolean pier = Math.floorMod(alongIndex, 6) == 0;
            return pier ? style.trimMaterial : style.windowMaterial;
        }

        if (levelInStorey == 0) {
            return style.trimMaterial;
        }

        if (level >= style.wallHeight) {
            return style.wallMaterial;
        }

        boolean windowColumn = Math.floorMod(alongIndex, config.windowSpacing()) < config.windowWidth();
        boolean windowRow = levelInStorey >= 1 && levelInStorey <= storeyHeight - 2;
        if (windowColumn && windowRow) {
            return style.windowMaterial;
        }

        return style.wallMaterial;
    }

    private void emitAll(Map<Long, BlockPlacement> placements, List<BlockPlacement> batch, int radiusBlocks) {
        for (BlockPlacement bp : batch) {
            if (outsideRadius(new int[]{bp.x(), bp.z()}, radiusBlocks)) continue;
            put(placements, bp.x(), bp.y(), bp.z(), bp.material());
        }
    }

    private void put(Map<Long, BlockPlacement> placements, int x, int y, int z, Material material) {
        placements.put(packXYZ(x, y, z), new BlockPlacement(x, y, z, material));
    }

    private static long packXYZ(int x, int y, int z) {
        return ((long) (x & 0xFFFF) << 32) | ((long) (z & 0xFFFF) << 16) | (long) ((y + 2048) & 0xFFFF);
    }

    private static long packXZ(int x, int z) {
        return ((long) (x & 0xFFFF) << 16) | (long) (z & 0xFFFF);
    }

    private List<double[]> projectVertices(GeoProjection projection, List<GeoPoint> vertices) {
        List<double[]> result = new ArrayList<>(vertices.size());
        for (GeoPoint v : vertices) {
            result.add(projection.toBlockOffset(v.lat(), v.lon()));
        }
        return result;
    }

    private List<List<double[]>> projectRings(GeoProjection projection, OsmFeature feature) {
        List<List<double[]>> rings = new ArrayList<>(feature.rings().size());
        for (List<GeoPoint> ring : feature.rings()) {
            rings.add(projectVertices(projection, ring));
        }
        return rings;
    }

    private List<int[]> stripIndex(List<int[]> perimeter) {
        List<int[]> cells = new ArrayList<>(perimeter.size());
        for (int[] xza : perimeter) {
            cells.add(new int[]{xza[0], xza[1]});
        }
        return cells;
    }

    private List<double[]> closeLoop(List<double[]> vertices) {
        if (vertices.isEmpty()) return vertices;
        double[] first = vertices.get(0);
        double[] last = vertices.get(vertices.size() - 1);
        if (first[0] == last[0] && first[1] == last[1]) return vertices;
        List<double[]> closed = new ArrayList<>(vertices);
        closed.add(first);
        return closed;
    }

    private boolean outsideRadius(int[] xz, int radiusBlocks) {
        return (long) xz[0] * xz[0] + (long) xz[1] * xz[1] > (long) radiusBlocks * radiusBlocks;
    }

    /** Bilinear interpolation over the sparse elevation sample grid. */
    private record HeightSampler(Map<GeoPoint, Double> elevationMap, Map<String, GeoPoint> gridPointsByKey,
                                  double baseElevation, int gridExtent, int step) {

        double heightAt(int x, int z) {
            int gx0 = Math.floorDiv(x, step) * step;
            int gz0 = Math.floorDiv(z, step) * step;
            int gx1 = gx0 + step;
            int gz1 = gz0 + step;

            double h00 = sample(gx0, gz0);
            double h10 = sample(gx1, gz0);
            double h01 = sample(gx0, gz1);
            double h11 = sample(gx1, gz1);

            double tx = (double) (x - gx0) / step;
            double tz = (double) (z - gz0) / step;

            double top = h00 + (h10 - h00) * tx;
            double bottom = h01 + (h11 - h01) * tx;
            return (top + (bottom - top) * tz) - baseElevation;
        }

        private double sample(int gx, int gz) {
            int clampedX = Math.max(-gridExtent, Math.min(gridExtent, gx));
            int clampedZ = Math.max(-gridExtent, Math.min(gridExtent, gz));
            GeoPoint point = gridPointsByKey.get(clampedX + "," + clampedZ);
            if (point == null) return baseElevation;
            Double elevation = elevationMap.get(point);
            return elevation != null ? elevation : baseElevation;
        }
    }
}
