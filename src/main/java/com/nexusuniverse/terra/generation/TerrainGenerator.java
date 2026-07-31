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
        int step = config.elevationSampleStep();
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
        return elevationFuture.thenCombine(osmFuture, (arg_0, arg_1) -> this.lambda$generate$0(projection, (Map)gridPointsByKey, radiusBlocks, gridExtent, worldBaseY, worldMaxY, config, arg_0, arg_1));
    }

    private GenerationResult assemble(GeoProjection projection, Map<GeoPoint, Double> elevationMap, Map<String, GeoPoint> gridPointsByKey, List<OsmFeature> features, int radiusBlocks, int gridExtent, int worldBaseY, int worldMaxY, TerraConfig config) {
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
        HeightSampler heightSampler = new HeightSampler(elevationMap, gridPointsByKey, originElevation, gridExtent, config.elevationSampleStep());
        int span = radiusBlocks * 2 + 1;
        for (int[] row : heightMap = new int[span][span]) {
            Arrays.fill((int[])row, (int)Integer.MIN_VALUE);
        }
        for (int x2 = -radiusBlocks; x2 <= radiusBlocks; ++x2) {
            for (int z2 = -radiusBlocks; z2 <= radiusBlocks; ++z2) {
                int height;
                if (x2 * x2 + z2 * z2 > radiusBlocks * radiusBlocks) continue;
                heightMap[x2 + radiusBlocks][z2 + radiusBlocks] = height = worldBaseY + (int)Math.round((double)heightSampler.heightAt(x2, z2));
                for (int depth = 5; depth >= 2; --depth) {
                    this.put((Map<Long, BlockPlacement>)placements, x2, height - depth, z2, Material.STONE);
                }
                this.put((Map<Long, BlockPlacement>)placements, x2, height - 1, z2, Material.DIRT);
                this.put((Map<Long, BlockPlacement>)placements, x2, height, z2, Material.GRASS_BLOCK);
            }
        }
        BiFunction<Integer, Integer, Integer> groundAt = (x, z) -> worldBaseY + (int) Math.round(heightSampler.heightAt(x, z));
        if (config.landuse()) {
            for (OsmFeature feature : features) {
                int treeSpacing;
                if (feature.category() != OsmFeature.Category.LANDUSE) continue;
                Material surface = SurfaceStyle.surfaceFor(feature.subtype());
                int n = treeSpacing = config.trees() ? SurfaceStyle.treeSpacingFor(feature.subtype()) : 0;
                if (surface == null && treeSpacing == 0) continue;
                List<int[]> area = PolygonRasterizer.fillRings(this.projectRings(projection, feature));
                boolean markings = SurfaceStyle.hasParkingMarkings(feature.subtype());
                for (int[] xz2 : area) {
                    if (this.outsideRadius(xz2, radiusBlocks)) continue;
                    int height = (Integer)groundAt.apply(xz2[0], xz2[1]);
                    if (surface != null) {
                        Material material = surface;
                        if (markings && Math.floorMod((int)xz2[0], (int)6) == 0 && Math.floorMod((int)xz2[1], (int)3) != 0) {
                            material = Material.WHITE_CONCRETE;
                        }
                        this.put((Map<Long, BlockPlacement>)placements, xz2[0], height, xz2[1], material);
                        int undergrowthChance = config.trees() ? SurfaceStyle.undergrowthChanceFor(material) : 0;
                        if (undergrowthChance > 0) {
                            int vHash = StreetFurniture.hash(xz2[0] * 92821 + 11, xz2[1] * 92821 + 17);
                            if (Math.floorMod(vHash, 100) < undergrowthChance) {
                                int decoY = height + 1;
                                if (decoY < worldMaxY - 1) {
                                    this.put((Map<Long, BlockPlacement>)placements, xz2[0], decoY, xz2[1], StreetFurniture.undergrowth(material, vHash));
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
                List<double[]> line = this.projectVertices(projection, feature.vertices());
                for (int[] xza : PolygonRasterizer.outline(line)) {
                    if (this.outsideRadius(xza, radiusBlocks) || xza[2] % 6 >= 3) continue;
                    this.put((Map<Long, BlockPlacement>)placements, xza[0], (Integer)groundAt.apply(xza[0], xza[1]), xza[1], Material.WHITE_CONCRETE);
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
                this.emitAll((Map<Long, BlockPlacement>)placements, StreetFurniture.lampPost(cell[0], cell[2], cell[1], worldMaxY), radiusBlocks);
                ++lamps;
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
        for (OsmFeature feature : features) {
            List<List<double[]>> rings;
            if (feature.category() != OsmFeature.Category.BUILDING || (rings = this.projectRings(projection, feature)).isEmpty() || ((List)rings.get(0)).size() < 3) continue;
            List outerRing = (List)rings.get(0);
            int headroom = Math.min((int)config.maxWallHeight(), (int)(worldMaxY - worldBaseY - 4));
            BuildingStyle style = BuildingStyle.forFeature(feature, config.defaultWallHeight(), config.blocksPerLevel(), headroom);
            List<int[]> perimeter = PolygonRasterizer.outline(this.closeLoop((List<double[]>)outerRing));
            List<int[]> interior = PolygonRasterizer.fillRings(rings);
            if (perimeter.isEmpty()) continue;
            double[] first = (double[])outerRing.get(0);
            int baseHeight = (Integer)groundAt.apply(((int)Math.round((double)first[0])), ((int)Math.round((double)first[1])));
            for (int[] xz5 : interior) {
                if (this.outsideRadius(xz5, radiusBlocks)) continue;
                this.put((Map<Long, BlockPlacement>)placements, xz5[0], baseHeight, xz5[1], style.trimMaterial);
            }
            int doorIndex = Math.max((int)1, (int)(perimeter.size() / 4));
            for (int level = 1; level <= style.wallHeight; ++level) {
                for (int[] xza : perimeter) {
                    Material material;
                    if (this.outsideRadius(xza, radiusBlocks) || (material = this.wallMaterialAt(style, level, xza[2], doorIndex, config)) == null) continue;
                    this.put((Map<Long, BlockPlacement>)placements, xza[0], baseHeight + level, xza[1], material);
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
                    if (config.rooftopDetails()) {
                        this.emitAll((Map<Long, BlockPlacement>)placements, RoofBuilder.flatRoofDetails(interior, roofY, style.wallHeight, style.trimMaterial, worldMaxY), radiusBlocks);
                    }
                }
            }
            ++buildingsPlaced;
        }
        this.logger.info("[NexusTerra] Assembled " + placements.size() + " block placements from " + features.size() + " OSM feature(s): " + buildingsPlaced + " building(s), " + bridgesPlaced + " bridge(s), " + tracksPlaced + " rail line(s), " + barriersPlaced + " barrier(s), " + lamps + " lamp post(s), " + signsPlaced + " street sign(s), " + polesPlaced + " utility pole(s), " + trafficLightsPlaced + " traffic light(s).");
        return new GenerationResult((List<BlockPlacement>)new ArrayList(placements.values()), heightMap, radiusBlocks);
    }

    private boolean isBuildableRoad(OsmFeature feature, TerraConfig config) {
        if (feature.category() != OsmFeature.Category.ROAD) {
            return false;
        }
        return !config.skipTunnels() || !feature.isTunnel();
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
            return style.windowMaterial;
        }
        return style.wallMaterial;
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

    private /* synthetic */ GenerationResult lambda$generate$0(GeoProjection projection, Map gridPointsByKey, int radiusBlocks, int gridExtent, int worldBaseY, int worldMaxY, TerraConfig config, Map elevationMap, List features) {
        return this.assemble(projection, (Map<GeoPoint, Double>)elevationMap, (Map<String, GeoPoint>)gridPointsByKey, (List<OsmFeature>)features, radiusBlocks, gridExtent, worldBaseY, worldMaxY, config);
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
