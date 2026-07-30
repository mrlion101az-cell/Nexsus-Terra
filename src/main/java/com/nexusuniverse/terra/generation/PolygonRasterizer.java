package com.nexusuniverse.terra.generation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts 2D polygon/line geometry (building footprints, water outlines,
 * road centerlines -- already projected into block-space X/Z) into the
 * actual set of block columns they cover.
 *
 * v0.1.5 fixed two real bugs here that were the direct cause of the
 * "everything looks like striped glass slabs" output:
 *
 *  1. thickLine's half-width was `Math.max(1, width / 2)`, so a request
 *     for a 1-wide line (used for building walls) actually stamped a 3x3
 *     square at every step -- walls came out 3 blocks thick, and every
 *     other odd width was off by one too.
 *  2. thickLine returned the same block coordinate many times over (once
 *     per overlapping step), so any caller that tried to use the list
 *     index as a position along the wall -- like the window pattern did --
 *     got garbage, producing vertical stripes instead of windows.
 *
 * Both are fixed: widths are now exact, output is deduplicated, and
 * `outline()` exists specifically to give callers a true ordered walk
 * around a perimeter with a usable along-path index.
 */
public class PolygonRasterizer {

    /**
     * Classic scanline polygon fill: for each integer Z row spanning the
     * polygon's bounding box, finds every edge crossing on that row,
     * sorts them, and fills between each pair of crossings.
     */
    public static List<int[]> fillInterior(List<double[]> vertices) {
        List<int[]> result = new ArrayList<>();
        if (vertices.size() < 3) {
            return result;
        }

        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double[] v : vertices) {
            minZ = Math.min(minZ, v[1]);
            maxZ = Math.max(maxZ, v[1]);
        }

        for (int z = (int) Math.floor(minZ); z <= (int) Math.ceil(maxZ); z++) {
            List<Double> crossings = new ArrayList<>();
            double scanZ = z + 0.5;

            for (int i = 0; i < vertices.size(); i++) {
                double[] a = vertices.get(i);
                double[] b = vertices.get((i + 1) % vertices.size());
                double az = a[1], bz = b[1];

                if ((az <= scanZ && bz > scanZ) || (bz <= scanZ && az > scanZ)) {
                    double t = (scanZ - az) / (bz - az);
                    double crossX = a[0] + t * (b[0] - a[0]);
                    crossings.add(crossX);
                }
            }

            crossings.sort(Double::compareTo);
            for (int i = 0; i + 1 < crossings.size(); i += 2) {
                int startX = (int) Math.round(crossings.get(i));
                int endX = (int) Math.round(crossings.get(i + 1));
                for (int x = startX; x <= endX; x++) {
                    result.add(new int[]{x, z});
                }
            }
        }

        return result;
    }

    /**
     * Scanline fill across several rings at once, applying the even-odd
     * rule over all of them together. That is what gives multipolygon
     * relations their holes for free: a point inside an inner ring
     * crosses an even number of edges and so is left unfilled, without
     * needing to know which rings were tagged outer and which inner.
     */
    public static List<int[]> fillRings(List<List<double[]>> rings) {
        List<int[]> result = new ArrayList<>();
        if (rings.isEmpty()) {
            return result;
        }
        if (rings.size() == 1) {
            return fillInterior(rings.get(0));
        }

        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        int usableRings = 0;
        for (List<double[]> ring : rings) {
            if (ring.size() < 3) continue;
            usableRings++;
            for (double[] v : ring) {
                minZ = Math.min(minZ, v[1]);
                maxZ = Math.max(maxZ, v[1]);
            }
        }
        if (usableRings == 0) {
            return result;
        }

        for (int z = (int) Math.floor(minZ); z <= (int) Math.ceil(maxZ); z++) {
            List<Double> crossings = new ArrayList<>();
            double scanZ = z + 0.5;

            for (List<double[]> ring : rings) {
                if (ring.size() < 3) continue;
                for (int i = 0; i < ring.size(); i++) {
                    double[] a = ring.get(i);
                    double[] b = ring.get((i + 1) % ring.size());
                    double az = a[1], bz = b[1];
                    if ((az <= scanZ && bz > scanZ) || (bz <= scanZ && az > scanZ)) {
                        double t = (scanZ - az) / (bz - az);
                        crossings.add(a[0] + t * (b[0] - a[0]));
                    }
                }
            }

            crossings.sort(Double::compareTo);
            for (int i = 0; i + 1 < crossings.size(); i += 2) {
                int startX = (int) Math.round(crossings.get(i));
                int endX = (int) Math.round(crossings.get(i + 1));
                for (int x = startX; x <= endX; x++) {
                    result.add(new int[]{x, z});
                }
            }
        }

        return result;
    }

    /**
     * Rasterizes a polyline into a strip exactly `width` blocks across,
     * centered on the line. Output is deduplicated -- overlapping steps
     * no longer emit the same coordinate repeatedly.
     */
    public static List<int[]> thickLine(List<double[]> polyline, int width) {
        // Exact width: a 1-wide line is 1 block, 5-wide is 5, 4-wide is 4.
        // The old Math.max(1, width/2) made 1-wide lines 3 blocks thick.
        int lo = -((width - 1) / 2);
        int hi = width / 2;

        Map<Long, int[]> unique = new LinkedHashMap<>();

        for (int i = 0; i + 1 < polyline.size(); i++) {
            double[] a = polyline.get(i);
            double[] b = polyline.get(i + 1);
            double dx = b[0] - a[0];
            double dz = b[1] - a[1];
            double length = Math.sqrt(dx * dx + dz * dz);
            int steps = Math.max(1, (int) Math.ceil(length));

            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps;
                int cx = (int) Math.round(a[0] + dx * t);
                int cz = (int) Math.round(a[1] + dz * t);
                for (int ox = lo; ox <= hi; ox++) {
                    for (int oz = lo; oz <= hi; oz++) {
                        int x = cx + ox;
                        int z = cz + oz;
                        unique.putIfAbsent(packXZ(x, z), new int[]{x, z});
                    }
                }
            }
        }

        return new ArrayList<>(unique.values());
    }

    /**
     * Walks a closed polygon's perimeter exactly one block wide and
     * returns each distinct block in path order as {x, z, alongIndex},
     * where alongIndex counts steps travelled around the perimeter.
     *
     * That third value is the whole point of this method: it lets
     * callers space features (windows, columns, lamp posts) evenly
     * *along a wall*, which is impossible with thickLine's unordered,
     * previously-duplicated output.
     */
    public static List<int[]> outline(List<double[]> closedPolygon) {
        Map<Long, int[]> unique = new LinkedHashMap<>();
        int along = 0;

        for (int i = 0; i + 1 < closedPolygon.size(); i++) {
            double[] a = closedPolygon.get(i);
            double[] b = closedPolygon.get(i + 1);
            double dx = b[0] - a[0];
            double dz = b[1] - a[1];
            double length = Math.sqrt(dx * dx + dz * dz);
            int steps = Math.max(1, (int) Math.ceil(length));

            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps;
                int x = (int) Math.round(a[0] + dx * t);
                int z = (int) Math.round(a[1] + dz * t);
                long key = packXZ(x, z);
                if (!unique.containsKey(key)) {
                    unique.put(key, new int[]{x, z, along});
                    along++;
                }
            }
        }

        return new ArrayList<>(unique.values());
    }

    private static long packXZ(int x, int z) {
        return ((long) (x & 0xFFFF) << 16) | (long) (z & 0xFFFF);
    }

    /** Road width in blocks, by OSM highway tag. */
    public static int widthForRoadType(String highwayTag) {
        return switch (highwayTag) {
            case "motorway", "trunk" -> 11;
            case "primary" -> 9;
            case "secondary" -> 7;
            case "tertiary", "residential", "unclassified" -> 5;
            case "service", "track" -> 3;
            case "footway", "path", "pedestrian", "steps" -> 2;
            case "cycleway" -> 3;
            default -> 4;
        };
    }
}
