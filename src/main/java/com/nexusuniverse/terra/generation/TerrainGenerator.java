/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  java.lang.Double
 *  java.lang.Integer
 *  java.lang.Long
 *  java.lang.Math
 *  java.lang.Object
 *  java.lang.String
 *  java.util.ArrayList
 *  java.util.Arrays
 *  java.util.HashMap
 *  java.util.HashSet
 *  java.util.Iterator
 *  java.util.LinkedHashMap
 *  java.util.List
 *  java.util.Map
 *  java.util.Map$Entry
 *  java.util.concurrent.CompletableFuture
 *  java.util.function.BiFunction
 *  java.util.function.IntConsumer
 *  java.util.logging.Logger
 *  org.bukkit.Material
 */
package com.nexusuniverse.terra.generation;

import com.nexusuniverse.terra.TerraConfig;
import com.nexusuniverse.terra.generation.BarrierStyle;
import com.nexusuniverse.terra.generation.BlockPlacement;
import com.nexusuniverse.terra.generation.BridgeBuilder;
import com.nexusuniverse.terra.generation.BuildingStyle;
import com.nexusuniverse.terra.generation.GenerationResult;
import com.nexusuniverse.terra.generation.PolygonRasterizer;
import com.nexusuniverse.terra.generation.RoadStyle;
import com.nexusuniverse.terra.generation.RoofBuilder;
import com.nexusuniverse.terra.generation.StreetFurniture;
import com.nexusuniverse.terra.generation.SurfaceStyle;
import com.nexusuniverse.terra.generation.TrackStyle;
import com.nexusuniverse.terra.geo.ElevationClient;
import com.nexusuniverse.terra.geo.GeoPoint;
import com.nexusuniverse.terra.geo.GeoProjection;
import com.nexusuniverse.terra.geo.OsmFeature;
import com.nexusuniverse.terra.geo.OverpassClient;
import java.lang.Double;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Math;
import java.lang.Object;
import java.lang.String;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

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

    public CompletableFuture<GenerationResult> generate(double originLat, double originLon, int radiusMeters, int worldBaseY, int worldMaxY, TerraConfig config, IntConsumer elevationProgress) {
        GeoProjection projection = new GeoProjection(originLat, originLon, 1.0);
        int radiusBlocks = radiusMeters;
        int step = effectiveElevationStep(radiusBlocks, config.elevationSampleStep());
        ArrayList samplePoints = new ArrayList();
        HashMap gridPointsByKey = new HashMap();
        int gridExtent = (radiusBlocks + step - 1) / step * step;
        for (int gx = -gridExtent; gx <= gridExtent; gx += step) {
            for (int gz = -gridExtent; gz <= gridExtent; gz += step) {
                GeoPoint point = projection.toLatLon(gx, gz);
                samplePoints.add(point);
                gridPointsByKey.put((Object)(gx + "," + gz), (Object)point);
            }
        }
        double south = projection.toLatLon(0.0, radiusBlocks).lat();
        double north = projection.toLatLon(0.0, -radiusBlocks).lat();
        double west = projection.toLatLon(-radiusBlocks, 0.0).lon();
        double east = projection.toLatLon(radiusBlocks, 0.0).lon();
        CompletableFuture<Map<GeoPoint, Double>> elevationFuture = this.elevationClient.lookup((List<GeoPoint>)samplePoints, elevationProgress);
        CompletableFuture<List<OsmFeature>> osmFuture = this.overpassClient.queryBoundingBox(south, west, north, east);
        int effectiveStep = step;
        return elevationFuture.thenCombine(osmFuture, (arg_0, arg_1) -> this.assemble(projection, (Map<GeoPoint, Double>)arg_0, (Map<String, GeoPoint>)gridPointsByKey, (List<OsmFeature>)arg_1, radiusBlocks, gridExtent, worldBaseY, worldMaxY, config, effectiveStep));
    }

    /**
     * The configured elevation-sample-step is a fine-detail choice suited to a typical (small)
     * radius. Held fixed at a large radius, it would blow up the elevation API point count --
     * that count grows with the square of the grid dimension -- for no real benefit, since
     * real-world elevation doesn't meaningfully change block-by-block over hundreds of metres.
     * This coarsens the step automatically as the requested radius grows, so a big generation
     * stays fast without needing to remember to retune elevation-sample-step by hand every time
     * a bigger radius is requested.
     *
     * Scales continuously (radius/60, floored at the configured value) rather than jumping
     * between fixed tiers -- a hard jump right at some specific radius would itself have been a
     * visible discontinuity in terrain quality between two generations a few metres apart.
     * Capped at 32 so an extremely large radius doesn't get sampled so coarsely that the terrain
     * stops resembling the real place at all.
     */
    public static int effectiveElevationStep(int radiusBlocks, int configuredStep) {
        int base = Math.max(2, configuredStep);
        int scaled = Math.max(base, radiusBlocks / 60);
        return Math.min(scaled, 32);
    }

    private GenerationResult assemble(GeoProjection projection, Map<GeoPoint, Double> elevationMap, Map<String, GeoPoint> gridPointsByKey, List<OsmFeature> features, int radiusBlocks, int gridExtent, int worldBaseY, int worldMaxY, TerraConfig config, int elevationStep) {
        int[] xz;
        int[][] heightMap;
        Double originElevation;
        LinkedHashMap<Long, BlockPlacement> placements = new LinkedHashMap<>();
        GeoPoint originPoint = (GeoPoint)((Object)gridPointsByKey.get((Object)"0,0"));
        Double d = originElevation = originPoint == null ? null : (Double)elevationMap.get((Object)originPoint);
        if (originElevation == null) {
            this.logger.warning("[NexusTerra] Origin elevation sample missing; defaulting to 0m. Heights may be offset.");
            originElevation = 0.0;
        }
        HeightSampler heightSampler = new HeightSampler(elevationMap, gridPointsByKey, originElevation, gridExtent, elevationStep);
        int span = radiusBlocks * 2 + 1;
        for (int[] row : heightMap = new int[span][span]) {
            Arrays.fill((int[])row, (int)Integer.MIN_VALUE);
        }

        // Raw per-block elevation is mathematically smooth (bilinear interpolation between real
        // sample points), but rounding it to a single integer block height PER COLUMN, independently,
        // is what turns a genuinely gentle real-world slope into a staircase of small cliffs --
        // especially once a coarser elevation-sample-step is in play (see effectiveElevationStep
        // above). A short smoothing pass over the raw height grid before rounding removes that
        // block-to-block noise while leaving real, large-scale hills and slopes (which span many
        // blocks) essentially untouched -- this isn't a workaround for coarse sampling, it directly
        // targets the "jagged instead of a natural slope" complaint regardless of what caused it.
        double[][] rawHeights = new double[span][span];
        for (double[] row : rawHeights) {
            Arrays.fill(row, Double.NaN);
        }
        for (int x2 = -radiusBlocks; x2 <= radiusBlocks; ++x2) {
            for (int z2 = -radiusBlocks; z2 <= radiusBlocks; ++z2) {
                if (x2 * x2 + z2 * z2 > radiusBlocks * radiusBlocks) continue;
                rawHeights[x2 + radiusBlocks][z2 + radiusBlocks] = heightSampler.heightAt(x2, z2);
            }
        }
        double[][] smoothedHeights = smoothHeights(rawHeights, span);

        for (int x2 = -radiusBlocks; x2 <= radiusBlocks; ++x2) {
            for (int z2 = -radiusBlocks; z2 <= radiusBlocks; ++z2) {
                int height;
                if (x2 * x2 + z2 * z2 > radiusBlocks * radiusBlocks) continue;
                heightMap[x2 + radiusBlocks][z2 + radiusBlocks] = height = worldBaseY + (int)Math.round(smoothedHeights[x2 + radiusBlocks][z2 + radiusBlocks]);
                // Mostly plain stone, with a small chance of andesite/diorite/cobblestone worked
                // in per layer -- a uniform stone cliff face reads as artificial; real exposed rock
                // has some variation to it. Subtle on purpose: this is texture, not a pattern.
                int rockHash = StreetFurniture.hash(x2 * 7 + 3, z2 * 11 + 5);
                for (int depth = 5; depth >= 2; --depth) {
                    Material rock = Material.STONE;
                    if (Math.floorMod(rockHash + depth * 97, 9) == 0) {
                        Material[] rockVariants = {Material.ANDESITE, Material.DIORITE, Material.COBBLESTONE};
                        rock = rockVariants[Math.floorMod(rockHash / 3, rockVariants.length)];
                    }
                    this.put((Map<Long, BlockPlacement>)placements, x2, height - depth, z2, rock);
                }
                this.put((Map<Long, BlockPlacement>)placements, x2, height - 1, z2, Material.DIRT);
                this.put((Map<Long, BlockPlacement>)placements, x2, height, z2, Material.GRASS_BLOCK);
            }
        }
        BiFunction<Integer, Integer, Integer> groundAt = (x, z) -> {
            int ix = x + radiusBlocks;
            int iz = z + radiusBlocks;
            if (ix >= 0 && ix < span && iz >= 0 && iz < span) {
                double smoothed = smoothedHeights[ix][iz];
                if (!Double.isNaN(smoothed)) {
                    return worldBaseY + (int) Math.round(smoothed);
                }
            }
            // Falls outside the precomputed grid (can happen mid-calculation for something like a
            // bridge path before it's clipped to the radius) -- fall back to the raw sampler rather
            // than indexing out of bounds. Any such point gets discarded by the outsideRadius check
            // wherever it's actually emitted, so it never reaches the world regardless.
            return worldBaseY + (int) Math.round(heightSampler.heightAt(x, z));
        };
        int fountainsPlaced = 0;
        if (config.landuse()) {
            for (OsmFeature feature : features) {
                int treeSpacing;
                if (feature.category() != OsmFeature.Category.LANDUSE) continue;
                Material surface = SurfaceStyle.surfaceFor(feature.subtype());
                int n = treeSpacing = config.trees() ? SurfaceStyle.treeSpacingFor(feature.subtype()) : 0;
                if (surface == null && treeSpacing == 0) continue;
                List<int[]> area = PolygonRasterizer.fillRings(this.projectRings(projection, feature));
                boolean markings = SurfaceStyle.hasParkingMarkings(feature.subtype());
                boolean checkeredPlaza = TerrainGenerator.isPlazaLike(feature.subtype());
                for (int[] xz2 : area) {
                    if (this.outsideRadius(xz2, radiusBlocks)) continue;
                    int height = (Integer)groundAt.apply(xz2[0], xz2[1]);
                    if (surface != null) {
                        Material material = surface;
                        if (markings && Math.floorMod((int)xz2[0], (int)6) == 0 && Math.floorMod((int)xz2[1], (int)3) != 0) {
                            material = Material.WHITE_CONCRETE;
                        } else if (checkeredPlaza && Math.floorMod(Math.floorDiv(xz2[0], 2) + Math.floorDiv(xz2[1], 2), 2) == 0) {
                            // A real plaza/square is very often a two-tone paver checker, not one
                            // flat surface -- 2x2 brick tiles alternating with the base surface.
                            material = Material.BRICKS;
                        }
                        this.put((Map<Long, BlockPlacement>)placements, xz2[0], height, xz2[1], material);
                        int undergrowthChance = config.trees() ? SurfaceStyle.undergrowthChanceFor(material) : 0;
                        if (undergrowthChance > 0) {
                            int vHash = StreetFurniture.hash(xz2[0] * 92821 + 11, xz2[1] * 92821 + 17);
                            if (Math.floorMod(vHash, 100) < undergrowthChance) {
                                int decoY = height + 1;
                                if (decoY < worldMaxY - 1) {
                                    // Most undergrowth rolls are flat ground cover (grass/flowers/
                                    // fern); a small fraction become a real bush cluster instead --
                                    // enough to actually read as vegetation variety, not so much
                                    // that it starts looking like an accidental forest.
                                    if (Math.floorMod(vHash, 700) < 20) {
                                        this.emitAll((Map<Long, BlockPlacement>)placements, StreetFurniture.bush(xz2[0], height, xz2[1], worldMaxY, vHash), radiusBlocks);
                                    } else {
                                        this.put((Map<Long, BlockPlacement>)placements, xz2[0], decoY, xz2[1], StreetFurniture.undergrowth(material, vHash));
                                    }
                                }
                            }
                        }
                    }
                    if (treeSpacing <= 0) continue;
                    int treeGx = Math.floorDiv(xz2[0], treeSpacing);
                    int treeGz = Math.floorDiv(xz2[1], treeSpacing);
                    int cellHash = StreetFurniture.hash(treeGx, treeGz);
                    if (Math.floorMod(cellHash, 100) < 8) continue;
                    int jitterRange = Math.max(1, treeSpacing / 3);
                    int jitterX = Math.floorMod(cellHash, jitterRange * 2 + 1) - jitterRange;
                    int jitterZ = Math.floorMod(cellHash / 7, jitterRange * 2 + 1) - jitterRange;
                    int treeTargetX = treeGx * treeSpacing + treeSpacing / 2 + jitterX;
                    int treeTargetZ = treeGz * treeSpacing + treeSpacing / 2 + jitterZ;
                    if (xz2[0] != treeTargetX || xz2[1] != treeTargetZ) continue;
                    this.emitAll((Map<Long, BlockPlacement>)placements, StreetFurniture.tree(xz2[0], height, xz2[1], worldMaxY, cellHash), radiusBlocks);
                }
                // A fountain at the centroid of a sufficiently large park/plaza/square -- once
                // per qualifying polygon, not scattered like street furniture.
                if (config.fountains() && isPlazaLike(feature.subtype()) && area.size() >= 80) {
                    long sumX = 0;
                    long sumZ = 0;
                    for (int[] c : area) {
                        sumX += c[0];
                        sumZ += c[1];
                    }
                    int centroidX = (int) (sumX / area.size());
                    int centroidZ = (int) (sumZ / area.size());
                    if (!this.outsideRadius(new int[]{centroidX, centroidZ}, radiusBlocks)) {
                        int centroidHeight = (Integer) groundAt.apply(centroidX, centroidZ);
                        this.emitAll((Map<Long, BlockPlacement>) placements,
                                StreetFurniture.fountain(centroidX, centroidHeight, centroidZ, worldMaxY, StreetFurniture.hash(centroidX, centroidZ)),
                                radiusBlocks);
                        ++fountainsPlaced;
                    }
                }
            }
        }
        int tracksPlaced = 0;
        if (config.railways()) {
            for (OsmFeature feature : features) {
                if (feature.category() != OsmFeature.Category.RAILWAY || !TrackStyle.isRenderable(feature.subtype()) || config.skipTunnels() && feature.isTunnel()) continue;
                List<double[]> line = this.projectVertices(projection, feature.vertices());
                int bedWidth = TrackStyle.bedWidth(feature.subtype());
                Material bed = TrackStyle.bedMaterial(feature.subtype());
                for (int[] xz2 : PolygonRasterizer.thickLine(line, bedWidth)) {
                    if (this.outsideRadius(xz2, radiusBlocks)) continue;
                    this.put((Map<Long, BlockPlacement>)placements, xz2[0], (Integer)groundAt.apply(xz2[0], xz2[1]), xz2[1], bed);
                }
                for (int[] xza : PolygonRasterizer.outline(line)) {
                    if (this.outsideRadius(xza, radiusBlocks)) continue;
                    int y = (Integer)groundAt.apply(xza[0], xza[1]);
                    if (xza[2] % 3 == 0) {
                        this.put((Map<Long, BlockPlacement>)placements, xza[0], y, xza[1], TrackStyle.sleeperMaterial());
                    }
                    if (y + 1 >= worldMaxY - 1) continue;
                    this.put((Map<Long, BlockPlacement>)placements, xza[0], y + 1, xza[1], TrackStyle.railMaterial());
                }
                ++tracksPlaced;
            }
        }
        LinkedHashMap<Long, int[]> sidewalkCells = new LinkedHashMap<>();
        HashSet roadCells = new HashSet();
        HashSet bridgeCells = new HashSet();
        if (config.sidewalks()) {
            for (OsmFeature feature : features) {
                int width;
                if (!this.isBuildableRoad(feature, config) || feature.isBridge() || (width = PolygonRasterizer.widthForRoadType(feature.subtype())) < config.sidewalkMinRoadWidth()) continue;
                List<double[]> line = this.projectVertices(projection, feature.vertices());
                Iterator y = PolygonRasterizer.thickLine(line, width + 4).iterator();
                while (y.hasNext()) {
                    xz = (int[]) y.next();
                    if (this.outsideRadius(xz, radiusBlocks)) continue;
                    int height = groundAt.apply(xz[0], xz[1]);
                    this.put(placements, xz[0], height, xz[1], Material.SMOOTH_STONE);
                    sidewalkCells.putIfAbsent(TerrainGenerator.packXZ(xz[0], xz[1]), new int[]{xz[0], xz[1], height});
                }
            }
        }
        HashMap<Long, Integer> roadFeatureHits = new HashMap<>();
        for (OsmFeature feature : features) {
            if (!this.isBuildableRoad(feature, config) || feature.isBridge() && config.bridges()) continue;
            List<double[]> line = this.projectVertices(projection, feature.vertices());
            int width = PolygonRasterizer.widthForRoadType(feature.subtype());
            Material roadMaterial = RoadStyle.materialFor(feature.subtype());
            for (int[] xz3 : PolygonRasterizer.thickLine(line, width)) {
                if (this.outsideRadius(xz3, radiusBlocks)) continue;
                this.put((Map<Long, BlockPlacement>)placements, xz3[0], (Integer)groundAt.apply(xz3[0], xz3[1]), xz3[1], roadMaterial);
                long cellKey = TerrainGenerator.packXZ(xz3[0], xz3[1]);
                roadCells.add(cellKey);
                roadFeatureHits.merge(cellKey, 1, Integer::sum);
            }
        }
        if (config.centreMarkings()) {
            for (OsmFeature feature : features) {
                int width;
                if (!this.isBuildableRoad(feature, config) || feature.isBridge() && config.bridges() || (width = PolygonRasterizer.widthForRoadType(feature.subtype())) < config.centreMarkingMinWidth()) continue;
                // A real major road (motorway/trunk/primary/secondary -- the same tier that gets
                // the darker blacktop material) has a solid yellow centre line, not a dashed white
                // one; dashed white is for the lesser residential/tertiary tier.
                boolean majorRoad = TerrainGenerator.isMajorRoad(feature.subtype());
                List<double[]> line = this.projectVertices(projection, feature.vertices());
                for (int[] xza : PolygonRasterizer.outline(line)) {
                    if (this.outsideRadius(xza, radiusBlocks)) continue;
                    if (majorRoad) {
                        this.put((Map<Long, BlockPlacement>)placements, xza[0], (Integer)groundAt.apply(xza[0], xza[1]), xza[1], Material.YELLOW_CONCRETE);
                    } else {
                        if (xza[2] % 6 >= 3) continue;
                        this.put((Map<Long, BlockPlacement>)placements, xza[0], (Integer)groundAt.apply(xza[0], xza[1]), xza[1], Material.WHITE_CONCRETE);
                    }
                }
            }
        }
        if (config.water()) {
            for (OsmFeature feature : features) {
                if (feature.category() != OsmFeature.Category.WATER) continue;
                if (!"water".equals(feature.subtype())) {
                    List<double[]> line = this.projectVertices(projection, feature.vertices());
                    if (line.size() < 2) continue;
                    int width = TerrainGenerator.waterwayWidthFor(feature.subtype());
                    for (int[] xz5 : PolygonRasterizer.thickLine(line, width)) {
                        if (this.outsideRadius(xz5, radiusBlocks)) continue;
                        int ground = groundAt.apply(xz5[0], xz5[1]);
                        this.put(placements, xz5[0], ground, xz5[1], Material.WATER);
                        this.put(placements, xz5[0], ground - 1, xz5[1], Material.WATER);
                        this.put(placements, xz5[0], ground - 2, xz5[1], Material.SAND);
                    }
                    continue;
                }
                List<int[]> footprint;
                if ((footprint = PolygonRasterizer.fillRings(this.projectRings(projection, feature))).isEmpty()) continue;
                int surfaceHeight = Integer.MAX_VALUE;
                Iterator roadMaterial = footprint.iterator();
                while (roadMaterial.hasNext()) {
                    xz = (int[]) roadMaterial.next();
                    if (this.outsideRadius(xz, radiusBlocks)) continue;
                    surfaceHeight = Math.min(surfaceHeight, groundAt.apply(xz[0], xz[1]));
                }
                if (surfaceHeight == Integer.MAX_VALUE) continue;
                roadMaterial = footprint.iterator();
                while (roadMaterial.hasNext()) {
                    xz = (int[]) roadMaterial.next();
                    if (this.outsideRadius(xz, radiusBlocks)) continue;
                    this.put(placements, xz[0], surfaceHeight, xz[1], Material.WATER);
                    this.put(placements, xz[0], surfaceHeight - 1, xz[1], Material.WATER);
                    this.put(placements, xz[0], surfaceHeight - 2, xz[1], Material.SAND);
                }
            }
        }
        int bridgesPlaced = 0;
        if (config.bridges()) {
            for (OsmFeature feature : features) {
                List<double[]> line;
                if (feature.category() != OsmFeature.Category.ROAD || !feature.isBridge() || config.skipTunnels() && feature.isTunnel() || (line = this.projectVertices(projection, feature.vertices())).size() < 2) continue;
                int width = PolygonRasterizer.widthForRoadType(feature.subtype());
                int clearance = config.bridgeClearance() + Math.max((int)0, (int)feature.layer()) * 5;
                BridgeBuilder.Deck deck = BridgeBuilder.build(line, width, RoadStyle.materialFor(feature.subtype()), Material.STONE_BRICKS, (BiFunction<Integer, Integer, Integer>)groundAt, clearance, worldMaxY);
                this.emitAll((Map<Long, BlockPlacement>)placements, deck.placements(), radiusBlocks);
                bridgeCells.addAll(deck.deckCells());
                ++bridgesPlaced;
            }
        }
        int lamps = 0;
        if (config.lampPosts()) {
            for (Map.Entry<Long, int[]> entry : sidewalkCells.entrySet()) {
                int[] cell;
                if (roadCells.contains(entry.getKey()) || bridgeCells.contains(entry.getKey()) || Math.floorMod((int)(cell = (int[])entry.getValue())[0], (int)config.lampSpacing()) != 0 || Math.floorMod((int)cell[1], (int)config.lampSpacing()) != 0) continue;
                this.emitAll((Map<Long, BlockPlacement>)placements, StreetFurniture.lampPost(cell[0], cell[2], cell[1], worldMaxY, StreetFurniture.hash(cell[0], cell[1])), radiusBlocks);
                ++lamps;
            }
        }
        int benchesPlaced = 0;
        if (config.benches()) {
            for (Map.Entry<Long, int[]> entry : sidewalkCells.entrySet()) {
                int[] cell;
                if (roadCells.contains(entry.getKey()) || bridgeCells.contains(entry.getKey()) || Math.floorMod((int)(cell = (int[])entry.getValue())[0], (int)config.benchSpacing()) != 0 || Math.floorMod((int)cell[1], (int)config.benchSpacing()) != 0) continue;
                this.emitAll((Map<Long, BlockPlacement>)placements, StreetFurniture.bench(cell[0], cell[2], cell[1], worldMaxY, StreetFurniture.hash(cell[0], cell[1])), radiusBlocks);
                ++benchesPlaced;
            }
        }
        int plantersPlaced = 0;
        if (config.planters()) {
            // Offset by a few blocks so planters don't land on the exact same cell pattern as
            // benches or lamps -- still periodic, just out of phase with the other two.
            int planterSpacing = Math.max(3, config.benchSpacing() - 4);
            for (Map.Entry<Long, int[]> entry : sidewalkCells.entrySet()) {
                int[] cell;
                if (roadCells.contains(entry.getKey()) || bridgeCells.contains(entry.getKey())
                        || Math.floorMod((int) (cell = (int[]) entry.getValue())[0] + 2, planterSpacing) != 0
                        || Math.floorMod(cell[1] + 2, planterSpacing) != 0) continue;
                this.emitAll((Map<Long, BlockPlacement>) placements, StreetFurniture.planter(cell[0], cell[2], cell[1], worldMaxY, StreetFurniture.hash(cell[0], cell[1])), radiusBlocks);
                ++plantersPlaced;
            }
        }
        int signsPlaced = 0;
        if (config.streetSigns()) {
            for (OsmFeature feature : features) {
                if (!this.isBuildableRoad(feature, config) || feature.isBridge()) continue;
                String name = feature.tag("name", null);
                if (name == null || name.isBlank()) continue;
                List<double[]> line = this.projectVertices(projection, feature.vertices());
                if (line.size() < 2) continue;
                double[] a = line.get(0);
                double[] b = line.get(1);
                double dx = b[0] - a[0];
                double dz = b[1] - a[1];
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len < 0.001) continue;
                double px = -dz / len;
                double pz = dx / len;
                int roadWidth = PolygonRasterizer.widthForRoadType(feature.subtype());
                int offset = roadWidth / 2 + 2;
                int sx = (int) Math.round(a[0] + px * offset);
                int sz = (int) Math.round(a[1] + pz * offset);
                if (this.outsideRadius(new int[]{sx, sz}, radiusBlocks)) continue;
                int ground = groundAt.apply(sx, sz);
                if (ground + 2 >= worldMaxY - 1) continue;
                String label = name.length() > 15 ? name.substring(0, 15) : name;
                this.put(placements, sx, ground + 1, sz, Material.OAK_FENCE);
                this.put(placements, sx, ground + 2, sz, Material.OAK_SIGN, label);
                ++signsPlaced;
            }
        }
        int polesPlaced = 0;
        if (config.telephonePoles()) {
            for (Map.Entry<Long, int[]> entry : sidewalkCells.entrySet()) {
                int[] cell = entry.getValue();
                if (roadCells.contains(entry.getKey()) || bridgeCells.contains(entry.getKey())
                        || Math.floorMod(cell[0], config.telephonePoleSpacing()) != 0
                        || Math.floorMod(cell[1], config.telephonePoleSpacing()) != 0) continue;
                this.emitAll(placements, StreetFurniture.telephonePole(cell[0], cell[2], cell[1], worldMaxY, StreetFurniture.hash(cell[0], cell[1])), radiusBlocks);
                ++polesPlaced;
            }
        }
        int trafficLightsPlaced = 0;
        if (config.trafficLights()) {
            HashMap<Long, int[]> sidewalkByBucket = new HashMap<>();
            for (int[] cell : sidewalkCells.values()) {
                long bucketKey = TerrainGenerator.packXZ(Math.floorDiv(cell[0], 8), Math.floorDiv(cell[1], 8));
                sidewalkByBucket.putIfAbsent(bucketKey, cell);
            }
            HashSet<Long> intersectionClusters = new HashSet<>();
            for (Map.Entry<Long, Integer> entry : roadFeatureHits.entrySet()) {
                if (entry.getValue() < 2) continue;
                int[] xz7 = TerrainGenerator.unpackXZ(entry.getKey());
                if (this.outsideRadius(xz7, radiusBlocks)) continue;
                long clusterKey = TerrainGenerator.packXZ(Math.floorDiv(xz7[0], 8), Math.floorDiv(xz7[1], 8));
                if (!intersectionClusters.add(clusterKey)) continue;
                int[] sidewalkCell = sidewalkByBucket.get(clusterKey);
                int lx = sidewalkCell != null ? sidewalkCell[0] : xz7[0];
                int lz = sidewalkCell != null ? sidewalkCell[1] : xz7[1];
                if (roadCells.contains(TerrainGenerator.packXZ(lx, lz))) continue;
                int ground = groundAt.apply(lx, lz);
                this.emitAll(placements, StreetFurniture.trafficLight(lx, ground, lz, worldMaxY), radiusBlocks);
                ++trafficLightsPlaced;
            }
        }
        int crosswalksPlaced = 0;
        if (config.crosswalks()) {
            // Reuses the same multi-way-intersection detection as traffic lights. The actual
            // direction of travel through a given intersection isn't known here (that would need
            // real per-road direction vectors resolved at this exact point, which isn't available
            // this cheaply) -- so the stripe orientation is picked per-intersection from a hash
            // instead of computed from road geometry. It won't always be perfectly perpendicular
            // to traffic, but painting is strictly bounded to cells that are already confirmed
            // road cells, so a wrong guess can never put paint somewhere that isn't a road.
            HashSet<Long> crosswalkClusters = new HashSet<>();
            int reach = 4;
            for (Map.Entry<Long, Integer> entry : roadFeatureHits.entrySet()) {
                if (entry.getValue() < 2) continue;
                int[] center = TerrainGenerator.unpackXZ(entry.getKey());
                if (this.outsideRadius(center, radiusBlocks)) continue;
                long clusterKey = TerrainGenerator.packXZ(Math.floorDiv(center[0], 8), Math.floorDiv(center[1], 8));
                if (!crosswalkClusters.add(clusterKey)) continue;
                boolean stripesAlongX = Math.floorMod(StreetFurniture.hash(center[0], center[1]), 2) == 0;
                boolean anyPainted = false;
                for (int d1 = -reach; d1 <= reach; ++d1) {
                    if (Math.floorMod(d1, 4) >= 2) continue; // gap between stripes
                    for (int d2 = -reach; d2 <= reach; ++d2) {
                        int cx = stripesAlongX ? center[0] + d1 : center[0] + d2;
                        int cz = stripesAlongX ? center[1] + d2 : center[1] + d1;
                        if (this.outsideRadius(new int[]{cx, cz}, radiusBlocks)) continue;
                        long cellKey = TerrainGenerator.packXZ(cx, cz);
                        if (!roadCells.contains(cellKey)) continue;
                        int ground = groundAt.apply(cx, cz);
                        this.put(placements, cx, ground, cz, Material.WHITE_CONCRETE);
                        anyPainted = true;
                    }
                }
                if (anyPainted) ++crosswalksPlaced;
            }
        }
        int barriersPlaced = 0;
        if (config.barriers()) {
            for (OsmFeature feature : features) {
                Material material;
                if (feature.category() != OsmFeature.Category.BARRIER || (material = BarrierStyle.materialFor(feature.subtype())) == null) continue;
                int height = BarrierStyle.heightFor(feature.subtype());
                List<double[]> line = this.projectVertices(projection, feature.vertices());
                for (int[] xz4 : PolygonRasterizer.thickLine(line, 1)) {
                    long key;
                    if (this.outsideRadius(xz4, radiusBlocks) || roadCells.contains((Object)(key = TerrainGenerator.packXZ(xz4[0], xz4[1]))) || bridgeCells.contains((Object)key)) continue;
                    int ground = (Integer)groundAt.apply(xz4[0], xz4[1]);
                    for (int i = 1; i <= height && ground + i < worldMaxY - 1; ++i) {
                        this.put((Map<Long, BlockPlacement>)placements, xz4[0], ground + i, xz4[1], material);
                    }
                }
                ++barriersPlaced;
            }
        }
        int buildingsPlaced = 0;
        int interiorsPlaced = 0;
        for (OsmFeature feature : features) {
            List<List<double[]>> rings;
            if (feature.category() != OsmFeature.Category.BUILDING || (rings = this.projectRings(projection, feature)).isEmpty() || ((List)rings.get(0)).size() < 3) continue;
            List outerRing = (List)rings.get(0);
            int headroom = Math.min((int)config.maxWallHeight(), (int)(worldMaxY - worldBaseY - 4));
            BuildingStyle style = BuildingStyle.forFeature(feature, config.defaultWallHeight(), config.blocksPerLevel(), headroom);
            List<int[]> perimeter = PolygonRasterizer.outline(this.closeLoop((List<double[]>)outerRing));
            List<int[]> interior = PolygonRasterizer.fillRings(rings);
            if (perimeter.isEmpty()) continue;
            // Real buildings on a sloped lot sit on a level pad, not on ground that changes
            // height under every wall -- use the lowest point across the footprint as the floor
            // level (so the building is never buried into the uphill side), then backfill the
            // rest of this loop below.
            int baseHeight = Integer.MAX_VALUE;
            for (int[] xzp : perimeter) {
                if (this.outsideRadius(xzp, radiusBlocks)) continue;
                baseHeight = Math.min(baseHeight, (Integer) groundAt.apply(xzp[0], xzp[1]));
            }
            if (baseHeight == Integer.MAX_VALUE) {
                double[] first = (double[]) outerRing.get(0);
                baseHeight = (Integer) groundAt.apply(((int) Math.round((double) first[0])), ((int) Math.round((double) first[1])));
            }
            for (int[] xz5 : interior) {
                if (this.outsideRadius(xz5, radiusBlocks)) continue;
                this.put((Map<Long, BlockPlacement>)placements, xz5[0], baseHeight, xz5[1], style.trimMaterial);
            }
            // Foundation fill: on the uphill side, real terrain sits above the chosen floor
            // level -- give it a retaining wall up to grade instead of a floating floor sitting
            // under a wall of exposed dirt. On the downhill side, real terrain drops below the
            // floor -- fill a footing down to grade instead of the building appearing to hover.
            int maxFoundationDepth = 20;
            for (int[] xzf : interior) {
                if (this.outsideRadius(xzf, radiusBlocks)) continue;
                int actualGround = (Integer) groundAt.apply(xzf[0], xzf[1]);
                if (actualGround > baseHeight) {
                    int fillTop = Math.min(actualGround, baseHeight + maxFoundationDepth);
                    for (int y = baseHeight + 1; y <= fillTop; ++y) {
                        this.put((Map<Long, BlockPlacement>)placements, xzf[0], y, xzf[1], style.trimMaterial);
                    }
                } else if (actualGround < baseHeight) {
                    int fillBottom = Math.max(actualGround + 1, baseHeight - maxFoundationDepth);
                    for (int y = fillBottom; y < baseHeight; ++y) {
                        this.put((Map<Long, BlockPlacement>)placements, xzf[0], y, xzf[1], style.trimMaterial);
                    }
                }
            }
            int doorIndex = Math.max((int)1, (int)(perimeter.size() / 4));
            // Vertical service-line seams down the wall face -- drainpipes/conduit. Picked per
            // building from a hash so it's consistent for that building but varies across a
            // street: some buildings get iron pipes, some get a copper line (LIGHTNING_ROD reads
            // as a thin rod when stacked vertically -- there's no literal "copper bar" block in
            // vanilla, but stacked lightning rods give the same thin-rod silhouette in copper's
            // colour), and roughly a quarter of buildings get none at all for variety. Only ever
            // overrides a plain wall cell -- never windows, trim, storefronts, or the door gap.
            int pipeSeed = StreetFurniture.hash(perimeter.get(0)[0], perimeter.get(0)[1]);
            boolean hasPipes = config.buildingPipes() && Math.floorMod(pipeSeed, 4) != 0;
            Material pipeMaterial = Math.floorMod(pipeSeed, 3) == 0 ? Material.LIGHTNING_ROD : Material.IRON_BARS;
            int pipeSpacing = 8;
            int pipeOffset = Math.floorMod(pipeSeed, pipeSpacing);
            for (int level = 1; level <= style.wallHeight; ++level) {
                for (int[] xza : perimeter) {
                    Material material;
                    if (this.outsideRadius(xza, radiusBlocks) || (material = this.wallMaterialAt(style, level, xza[2], doorIndex, config)) == null) continue;
                    if (hasPipes && material == style.wallMaterial && Math.floorMod(xza[2], pipeSpacing) == pipeOffset) {
                        material = pipeMaterial;
                    }
                    this.put((Map<Long, BlockPlacement>)placements, xza[0], baseHeight + level, xza[1], material);
                }
            }
            if ((config.interiorFloors() || config.interiorFurniture()) && !interior.isEmpty() && interior.size() <= 2000) {
                interiorsPlaced += this.buildInterior(placements, feature, interior, style, baseHeight, radiusBlocks, worldMaxY, config);
            }
            // A named building gets its own facade sign, the same idea as the named-street signs
            // below -- reads its name straight from OSM rather than making one up. Placed one
            // cell OUTSIDE the wall face (not replacing the wall cell itself), backed by the wall
            // block behind it, so it's a real wall-mounted sign rather than something floating
            // with no support. "Outside" is only a rough estimate (direction from the building's
            // own centroid to the door, snapped to a cardinal direction) since true wall-normal
            // geometry isn't computed anywhere in this pipeline -- good enough for a mostly-convex
            // building footprint, which covers the large majority of real ones.
            if (config.streetSigns() && !interior.isEmpty() && doorIndex < perimeter.size()) {
                String buildingName = feature.tag("name", null);
                if (buildingName != null && !buildingName.isBlank()) {
                    int[] doorCell = perimeter.get(doorIndex);
                    long sumBx = 0;
                    long sumBz = 0;
                    for (int[] c : interior) {
                        sumBx += c[0];
                        sumBz += c[1];
                    }
                    int buildingCentroidX = (int) (sumBx / interior.size());
                    int buildingCentroidZ = (int) (sumBz / interior.size());
                    int ddx = doorCell[0] - buildingCentroidX;
                    int ddz = doorCell[1] - buildingCentroidZ;
                    BlockFace outward;
                    int ox;
                    int oz;
                    if (Math.abs(ddx) >= Math.abs(ddz)) {
                        outward = ddx >= 0 ? BlockFace.EAST : BlockFace.WEST;
                        ox = ddx >= 0 ? 1 : -1;
                        oz = 0;
                    } else {
                        outward = ddz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
                        ox = 0;
                        oz = ddz >= 0 ? 1 : -1;
                    }
                    int signX = doorCell[0] + ox;
                    int signZ = doorCell[1] + oz;
                    int signY = baseHeight + Math.min(3, Math.max(2, style.wallHeight - 1));
                    long signCellKey = TerrainGenerator.packXZ(signX, signZ);
                    if (!this.outsideRadius(new int[]{signX, signZ}, radiusBlocks) && signY < worldMaxY - 1
                            && !roadCells.contains(signCellKey) && !bridgeCells.contains(signCellKey)) {
                        String label = buildingName.length() > 15 ? buildingName.substring(0, 15) : buildingName;
                        placements.put(TerrainGenerator.packXYZ(signX, signY, signZ),
                                new BlockPlacement(signX, signY, signZ, Material.OAK_WALL_SIGN, label, outward, null));
                    }
                }
            }
            // A little landscaping right at the building's own base -- a few flower/bush clusters
            // just outside the wall, not the whole perimeter landscaped, and never on a road,
            // bridge, or sidewalk cell (a plant sitting on top of pavement looks wrong even
            // though the coordinate math would allow it).
            if (config.trees() && !interior.isEmpty()) {
                long sumLx = 0;
                long sumLz = 0;
                for (int[] c : interior) {
                    sumLx += c[0];
                    sumLz += c[1];
                }
                int landscapeCx = (int) (sumLx / interior.size());
                int landscapeCz = (int) (sumLz / interior.size());
                int plantingsPlaced = 0;
                int maxPlantings = 4;
                int stride = Math.max(1, perimeter.size() / 8);
                for (int i = 0; i < perimeter.size() && plantingsPlaced < maxPlantings; i += stride) {
                    int[] cell = perimeter.get(i);
                    int ldx = cell[0] - landscapeCx;
                    int ldz = cell[1] - landscapeCz;
                    int lox;
                    int loz;
                    if (Math.abs(ldx) >= Math.abs(ldz)) {
                        lox = ldx >= 0 ? 1 : -1;
                        loz = 0;
                    } else {
                        lox = 0;
                        loz = ldz >= 0 ? 1 : -1;
                    }
                    int px = cell[0] + lox;
                    int pz = cell[1] + loz;
                    if (this.outsideRadius(new int[]{px, pz}, radiusBlocks)) continue;
                    long plantKey = TerrainGenerator.packXZ(px, pz);
                    if (roadCells.contains(plantKey) || bridgeCells.contains(plantKey) || sidewalkCells.containsKey(plantKey)) continue;
                    int plantHash = StreetFurniture.hash(px, pz);
                    if (Math.floorMod(plantHash, 100) >= 45) continue;
                    int plantGround = groundAt.apply(px, pz);
                    this.emitAll(placements, StreetFurniture.bush(px, plantGround, pz, worldMaxY, plantHash), radiusBlocks);
                    ++plantingsPlaced;
                }
            }
            int roofY = baseHeight + style.wallHeight + 1;
            if (roofY < worldMaxY - 1) {
                if (style.pitchedRoof && config.pitchedRoofs()) {
                    this.emitAll((Map<Long, BlockPlacement>)placements, RoofBuilder.pitched(interior, roofY, style.roofMaterial, worldMaxY), radiusBlocks);
                    this.emitAll((Map<Long, BlockPlacement>)placements, RoofBuilder.pitched(this.stripIndex(perimeter), roofY - 1, style.wallMaterial, worldMaxY), radiusBlocks);
                } else {
                    for (int[] xz6 : interior) {
                        if (this.outsideRadius(xz6, radiusBlocks)) continue;
                        this.put((Map<Long, BlockPlacement>)placements, xz6[0], roofY, xz6[1], style.roofMaterial);
                    }
                    for (int[] xza : perimeter) {
                        if (this.outsideRadius(xza, radiusBlocks)) continue;
                        this.put((Map<Long, BlockPlacement>)placements, xza[0], roofY, xza[1], style.trimMaterial);
                        if (roofY + 1 >= worldMaxY - 1) continue;
                        this.put((Map<Long, BlockPlacement>)placements, xza[0], roofY + 1, xza[1], style.trimMaterial);
                    }
                    // Roofline cresting -- a flat trim band reads as unfinished compared to a real
                    // roofline, which almost always has SOME edge treatment: chain-link fencing
                    // on utilitarian buildings, or a castle-like up-down parapet (crenellation) on
                    // civic/religious ones with more ornate architecture.
                    if (roofY + 2 < worldMaxY - 1) {
                        String roofSubtype = feature.subtype();
                        boolean chainLinkRoof = roofSubtype != null && (roofSubtype.equals("industrial") || roofSubtype.equals("warehouse")
                                || roofSubtype.equals("manufacture") || roofSubtype.equals("commercial") || roofSubtype.equals("retail") || roofSubtype.equals("supermarket"));
                        boolean crenellatedRoof = roofSubtype != null && (roofSubtype.equals("church") || roofSubtype.equals("cathedral") || roofSubtype.equals("chapel")
                                || roofSubtype.equals("mosque") || roofSubtype.equals("temple") || roofSubtype.equals("civic") || roofSubtype.equals("government")
                                || roofSubtype.equals("school") || roofSubtype.equals("university"));
                        if (chainLinkRoof) {
                            for (int[] xza : perimeter) {
                                if (this.outsideRadius(xza, radiusBlocks)) continue;
                                this.put((Map<Long, BlockPlacement>)placements, xza[0], roofY + 2, xza[1], Material.IRON_BARS);
                            }
                        } else if (crenellatedRoof) {
                            for (int[] xza : perimeter) {
                                if (this.outsideRadius(xza, radiusBlocks) || Math.floorMod(xza[2], 2) != 0) continue;
                                this.put((Map<Long, BlockPlacement>)placements, xza[0], roofY + 2, xza[1], style.trimMaterial);
                            }
                        }
                    }
                    if (config.rooftopDetails()) {
                        this.emitAll((Map<Long, BlockPlacement>)placements, RoofBuilder.flatRoofDetails(interior, roofY, style.wallHeight, style.trimMaterial, worldMaxY, pipeSeed), radiusBlocks);
                    }
                }
            }
            ++buildingsPlaced;
        }
        this.logger.info("[NexusTerra] Assembled " + placements.size() + " block placements from " + features.size() + " OSM feature(s): " + buildingsPlaced + " building(s) (" + interiorsPlaced + " floor(s) furnished), " + bridgesPlaced + " bridge(s), " + tracksPlaced + " rail line(s), " + barriersPlaced + " barrier(s), " + lamps + " lamp post(s), " + benchesPlaced + " bench(es), " + plantersPlaced + " planter(s), " + signsPlaced + " street sign(s), " + polesPlaced + " utility pole(s), " + trafficLightsPlaced + " traffic light(s), " + crosswalksPlaced + " crosswalk(s), " + fountainsPlaced + " fountain(s).");
        return new GenerationResult((List<BlockPlacement>)new ArrayList(placements.values()), heightMap, radiusBlocks);
    }

    // A mild 3x3 weighted-average blur, applied twice (~1.4-block effective radius). Strong
    // enough to remove single-block stair-stepping between neighbouring columns, gentle enough
    // that a real hill spanning dozens of blocks is essentially unaffected -- this is smoothing
    // out rounding noise, not flattening terrain. NaN cells (outside the circular radius) are
    // skipped both as sources and as write targets.
    private static double[][] smoothHeights(double[][] raw, int span) {
        double[][] current = raw;
        for (int pass = 0; pass < 2; ++pass) {
            double[][] next = new double[span][span];
            for (int i = 0; i < span; ++i) {
                for (int j = 0; j < span; ++j) {
                    if (Double.isNaN(current[i][j])) {
                        next[i][j] = Double.NaN;
                        continue;
                    }
                    double sum = 0;
                    double weightTotal = 0;
                    for (int di = -1; di <= 1; ++di) {
                        for (int dj = -1; dj <= 1; ++dj) {
                            int ni = i + di;
                            int nj = j + dj;
                            if (ni < 0 || ni >= span || nj < 0 || nj >= span) continue;
                            double v = current[ni][nj];
                            if (Double.isNaN(v)) continue;
                            double weight = (di == 0 && dj == 0) ? 4.0 : (di == 0 || dj == 0) ? 2.0 : 1.0;
                            sum += v * weight;
                            weightTotal += weight;
                        }
                    }
                    next[i][j] = weightTotal > 0 ? sum / weightTotal : current[i][j];
                }
            }
            current = next;
        }
        return current;
    }

    private boolean isBuildableRoad(OsmFeature feature, TerraConfig config) {
        if (feature.category() != OsmFeature.Category.ROAD) {
            return false;
        }
        return !config.skipTunnels() || !feature.isTunnel();
    }

    private static boolean isMajorRoad(String highwayTag) {
        if (highwayTag == null) return false;
        switch (highwayTag) {
            case "motorway":
            case "trunk":
            case "primary":
            case "secondary":
                return true;
            default:
                return false;
        }
    }

    private static boolean isPlazaLike(String landuseTag) {
        if (landuseTag == null) return false;
        switch (landuseTag) {
            case "park":
            case "garden":
            case "village_green":
            case "recreation_ground":
            case "plaza":
            case "square":
            case "pedestrian":
                return true;
            default:
                return false;
        }
    }

    private static Material sillMaterialFor(Material trim) {
        if (trim == Material.DEEPSLATE_BRICKS) return Material.DEEPSLATE_BRICK_SLAB;
        if (trim == Material.DEEPSLATE_TILES) return Material.DEEPSLATE_TILE_SLAB;
        if (trim == Material.STONE_BRICKS) return Material.STONE_BRICK_SLAB;
        if (trim == Material.POLISHED_ANDESITE) return Material.POLISHED_ANDESITE_SLAB;
        if (trim == Material.POLISHED_DIORITE) return Material.POLISHED_DIORITE_SLAB;
        if (trim == Material.CUT_SANDSTONE) return Material.CUT_SANDSTONE_SLAB;
        if (trim == Material.SMOOTH_SANDSTONE) return Material.SMOOTH_SANDSTONE_SLAB;
        if (trim == Material.STRIPPED_SPRUCE_WOOD) return Material.SPRUCE_SLAB;
        if (trim == Material.SMOOTH_STONE) return Material.SMOOTH_STONE_SLAB;
        return Material.SMOOTH_STONE_SLAB;
    }

    private Material wallMaterialAt(BuildingStyle style, int level, int alongIndex, int doorIndex, TerraConfig config) {
        boolean windowRow;
        boolean nearDoor;
        int storeyHeight = Math.max((int)3, (int)style.storeyHeight);
        int levelInStorey = (level - 1) % storeyHeight;
        boolean bl = nearDoor = Math.abs((int)(alongIndex - doorIndex)) <= 1;
        if (nearDoor && level <= 2) {
            return null;
        }
        if (level == 1) {
            return style.trimMaterial;
        }
        if (config.shopfronts() && style.storefront && level < storeyHeight && level < style.wallHeight) {
            if (level == storeyHeight - 1 && style.awningMaterial != null) {
                return style.awningMaterial;
            }
            boolean pier = Math.floorMod((int)alongIndex, (int)6) == 0;
            return pier ? style.trimMaterial : style.windowMaterial;
        }
        if (levelInStorey == 0) {
            return style.trimMaterial;
        }
        if (level >= style.wallHeight) {
            return style.wallMaterial;
        }
        boolean windowColumn = Math.floorMod((int)alongIndex, (int)config.windowSpacing()) < config.windowWidth();
        boolean bl2 = windowRow = levelInStorey >= 1 && levelInStorey <= storeyHeight - 2;
        if (windowColumn && windowRow) {
            if (levelInStorey == 1 && storeyHeight - 2 > 1) {
                return TerrainGenerator.sillMaterialFor(style.trimMaterial);
            }
            return style.windowMaterial;
        }
        return style.wallMaterial;
    }

    // Floor slabs at every storey above the ground floor, an optional carpet rug near the centre
    // of each one, and one small furniture cluster per floor picked by building category. Capped
    // at a sane floor count so an unusually tall or tightly-storeyed tower can't runaway-generate
    // hundreds of furnished floors. Returns how many floors actually got furniture.
    private static final int MAX_INTERIOR_FLOORS = 60;

    private int buildInterior(Map<Long, BlockPlacement> placements, OsmFeature feature, List<int[]> interior,
                               BuildingStyle style, int baseHeight, int radiusBlocks, int worldMaxY, TerraConfig config) {
        int storeyHeight = Math.max(3, style.storeyHeight);
        int floorCount = Math.min(MAX_INTERIOR_FLOORS, Math.max(1, style.wallHeight / storeyHeight));
        String subtype = feature.subtype();

        long sumX = 0;
        long sumZ = 0;
        HashSet<Long> interiorCells = new HashSet<>();
        for (int[] c : interior) {
            sumX += c[0];
            sumZ += c[1];
            interiorCells.add(TerrainGenerator.packXZ(c[0], c[1]));
        }
        int centroidX = (int) (sumX / interior.size());
        int centroidZ = (int) (sumZ / interior.size());
        int variant = StreetFurniture.hash(centroidX, centroidZ);
        boolean centroidInRadius = !this.outsideRadius(new int[]{centroidX, centroidZ}, radiusBlocks);
        // Offset the pendant light a couple of blocks from the furniture cluster so they don't
        // stack, but only if that offset cell is actually confirmed inside the footprint -- on
        // a small or irregularly-shaped building the offset could otherwise land through a wall
        // or outside the building entirely. Falls back to the centroid, which is always safe.
        int lightX = centroidX;
        int lightZ = centroidZ;
        if (interiorCells.contains(TerrainGenerator.packXZ(centroidX + 2, centroidZ))) {
            lightX = centroidX + 2;
        } else if (interiorCells.contains(TerrainGenerator.packXZ(centroidX, centroidZ + 2))) {
            lightZ = centroidZ + 2;
        }

        int floorsFurnished = 0;
        for (int floorIndex = 0; floorIndex < floorCount; floorIndex++) {
            int floorY = baseHeight + floorIndex * storeyHeight;
            if (floorY >= worldMaxY - 1) break;

            if (floorIndex > 0 && config.interiorFloors()) {
                Material floorA = InteriorStyle.floorMaterial(subtype, variant + floorIndex);
                Material floorB = InteriorStyle.floorMaterialSecondary(subtype, variant + floorIndex);
                for (int[] xz : interior) {
                    if (this.outsideRadius(xz, radiusBlocks)) continue;
                    Material tile = Math.floorMod(xz[0] + xz[1], 2) == 0 ? floorA : floorB;
                    this.put(placements, xz[0], floorY, xz[1], tile);
                }
                Material rugA = InteriorStyle.rugMaterial(subtype, variant + floorIndex);
                Material rugB = InteriorStyle.rugMaterialSecondary(subtype, variant + floorIndex);
                if (rugA != null && floorY + 1 < worldMaxY - 1) {
                    for (int[] xz : interior) {
                        if (this.outsideRadius(xz, radiusBlocks)) continue;
                        int dx = xz[0] - centroidX;
                        int dz = xz[1] - centroidZ;
                        if (dx * dx + dz * dz <= 9) {
                            Material rugTile = Math.floorMod(xz[0] + xz[1], 2) == 0 ? rugA : rugB;
                            this.put(placements, xz[0], floorY + 1, xz[1], rugTile);
                        }
                    }
                }
            }

            if (config.interiorFurniture() && centroidInRadius) {
                this.emitAll(placements, InteriorStyle.furniture(centroidX, floorY, centroidZ, subtype, floorIndex, worldMaxY, variant + floorIndex), radiusBlocks);
                // A pendant light hanging from that floor's own ceiling -- this is the single
                // detail that most reliably makes an interior look deliberately lit rather than
                // just furnished in the dark.
                int ceilingY = floorY + storeyHeight;
                this.emitAll(placements, InteriorStyle.hangingLight(lightX, ceilingY, lightZ, worldMaxY, variant + floorIndex), radiusBlocks);
                ++floorsFurnished;
            }
        }
        return floorsFurnished;
    }

    private void emitAll(Map<Long, BlockPlacement> placements, List<BlockPlacement> batch, int radiusBlocks) {
        for (BlockPlacement bp : batch) {
            if (this.outsideRadius(new int[]{bp.x(), bp.z()}, radiusBlocks)) continue;
            this.put(placements, bp.x(), bp.y(), bp.z(), bp.material());
        }
    }

    private void put(Map<Long, BlockPlacement> placements, int x, int y, int z, Material material) {
        placements.put(TerrainGenerator.packXYZ(x, y, z), new BlockPlacement(x, y, z, material));
    }

    private void put(Map<Long, BlockPlacement> placements, int x, int y, int z, Material material, String label) {
        placements.put(TerrainGenerator.packXYZ(x, y, z), new BlockPlacement(x, y, z, material, label));
    }

    private static long packXYZ(int x, int y, int z) {
        return (long)(x & 0xFFFF) << 32 | (long)(z & 0xFFFF) << 16 | (long)(y + 2048 & 0xFFFF);
    }

    private static int waterwayWidthFor(String waterwayTag) {
        if (waterwayTag == null) return 2;
        switch (waterwayTag) {
            case "river":
                return 5;
            case "canal":
                return 4;
            case "stream":
                return 2;
            case "drain":
            case "ditch":
                return 1;
            default:
                return 2;
        }
    }

    private static long packXZ(int x, int z) {
        return (long)(x & 0xFFFF) << 16 | (long)(z & 0xFFFF);
    }

    private static int[] unpackXZ(long key) {
        int x = (int) (short) ((key >> 16) & 0xFFFF);
        int z = (int) (short) (key & 0xFFFF);
        return new int[]{x, z};
    }

    private List<double[]> projectVertices(GeoProjection projection, List<GeoPoint> vertices) {
        ArrayList result = new ArrayList(vertices.size());
        for (GeoPoint v : vertices) {
            result.add(projection.toBlockOffset(v.lat(), v.lon()));
        }
        return result;
    }

    private List<List<double[]>> projectRings(GeoProjection projection, OsmFeature feature) {
        ArrayList rings = new ArrayList(feature.rings().size());
        for (List ring : feature.rings()) {
            rings.add(this.projectVertices(projection, (List<GeoPoint>)ring));
        }
        return rings;
    }

    private List<int[]> stripIndex(List<int[]> perimeter) {
        ArrayList cells = new ArrayList(perimeter.size());
        for (int[] xza : perimeter) {
            cells.add(new int[]{xza[0], xza[1]});
        }
        return cells;
    }

    private List<double[]> closeLoop(List<double[]> vertices) {
        double[] last;
        if (vertices.isEmpty()) {
            return vertices;
        }
        double[] first = (double[])vertices.get(0);
        if (first[0] == (last = (double[])vertices.get(vertices.size() - 1))[0] && first[1] == last[1]) {
            return vertices;
        }
        ArrayList closed = new ArrayList(vertices);
        closed.add(first);
        return closed;
    }

    private boolean outsideRadius(int[] xz, int radiusBlocks) {
        return (long)xz[0] * (long)xz[0] + (long)xz[1] * (long)xz[1] > (long)radiusBlocks * (long)radiusBlocks;
    }

    private record HeightSampler(Map<GeoPoint, Double> elevationMap, Map<String, GeoPoint> gridPointsByKey, double baseElevation, int gridExtent, int step) {
        double heightAt(int x, int z) {
            int gx0 = Math.floorDiv((int)x, (int)this.step) * this.step;
            int gz0 = Math.floorDiv((int)z, (int)this.step) * this.step;
            int gx1 = gx0 + this.step;
            int gz1 = gz0 + this.step;
            double h00 = this.sample(gx0, gz0);
            double h10 = this.sample(gx1, gz0);
            double h01 = this.sample(gx0, gz1);
            double h11 = this.sample(gx1, gz1);
            double tx = (double)(x - gx0) / (double)this.step;
            double tz = (double)(z - gz0) / (double)this.step;
            double top = h00 + (h10 - h00) * tx;
            double bottom = h01 + (h11 - h01) * tx;
            return top + (bottom - top) * tz - this.baseElevation;
        }

        private double sample(int gx, int gz) {
            int clampedZ;
            int clampedX = Math.max((int)(-this.gridExtent), (int)Math.min((int)this.gridExtent, (int)gx));
            GeoPoint point = (GeoPoint)((Object)this.gridPointsByKey.get((Object)(clampedX + "," + (clampedZ = Math.max((int)(-this.gridExtent), (int)Math.min((int)this.gridExtent, (int)gz))))));
            if (point == null) {
                return this.baseElevation;
            }
            Double elevation = (Double)this.elevationMap.get((Object)point);
            return elevation != null ? elevation : this.baseElevation;
        }
    }
}
