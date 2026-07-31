/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  java.lang.Double
 *  java.lang.Math
 *  java.lang.Object
 *  java.lang.String
 *  java.util.ArrayList
 *  java.util.LinkedHashMap
 *  java.util.List
 */
package com.nexusuniverse.terra.generation;

import java.lang.Double;
import java.lang.Math;
import java.lang.Object;
import java.lang.String;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class PolygonRasterizer {
    public static List<int[]> fillInterior(List<double[]> vertices) {
        ArrayList<int[]> result = new ArrayList<>();
        if (vertices.size() < 3) {
            return result;
        }
        double minZ = Double.MAX_VALUE;
        double maxZ = -1.7976931348623157E308;
        for (double[] v : vertices) {
            minZ = Math.min(minZ, v[1]);
            maxZ = Math.max(maxZ, v[1]);
        }
        for (int z = (int) Math.floor(minZ); z <= (int) Math.ceil(maxZ); ++z) {
            int i;
            ArrayList<Double> crossings = new ArrayList<>();
            double scanZ = (double) z + 0.5;
            for (i = 0; i < vertices.size(); ++i) {
                double[] a = vertices.get(i);
                double[] b = vertices.get((i + 1) % vertices.size());
                double az = a[1];
                double bz = b[1];
                if (!(az <= scanZ && bz > scanZ) && (!(bz <= scanZ) || !(az > scanZ))) continue;
                double t = (scanZ - az) / (bz - az);
                double crossX = a[0] + t * (b[0] - a[0]);
                crossings.add(crossX);
            }
            crossings.sort(Double::compareTo);
            // Two edges meeting at a vertex that sits close to this scanline both register a
            // crossing at nearly the same X. Left alone, that leaves an odd crossing count and
            // the pairing loop below silently drops the trailing span -- which is exactly what
            // was punching small holes in building roofs/floors on real-world footprints.
            for (int j = crossings.size() - 1; j > 0; --j) {
                if (Math.abs(crossings.get(j) - crossings.get(j - 1)) < 0.01) {
                    crossings.remove(j);
                }
            }
            i = 0;
            while (i + 1 < crossings.size()) {
                int startX = (int) Math.round(crossings.get(i));
                int endX = (int) Math.round(crossings.get(i + 1));
                int x = startX;
                while (x <= endX) {
                    result.add(new int[]{x++, z});
                }
                i += 2;
            }
        }
        return result;
    }

    public static List<int[]> fillRings(List<List<double[]>> rings) {
        ArrayList<int[]> result = new ArrayList<>();
        if (rings.isEmpty()) {
            return result;
        }
        if (rings.size() == 1) {
            return PolygonRasterizer.fillInterior(rings.get(0));
        }
        double minZ = Double.MAX_VALUE;
        double maxZ = -1.7976931348623157E308;
        int usableRings = 0;
        for (List<double[]> ring : rings) {
            if (ring.size() < 3) continue;
            ++usableRings;
            for (double[] v : ring) {
                minZ = Math.min(minZ, v[1]);
                maxZ = Math.max(maxZ, v[1]);
            }
        }
        if (usableRings == 0) {
            return result;
        }
        for (int z = (int) Math.floor(minZ); z <= (int) Math.ceil(maxZ); ++z) {
            ArrayList<Double> crossings = new ArrayList<>();
            double scanZ = (double) z + 0.5;
            for (List<double[]> ring : rings) {
                if (ring.size() < 3) continue;
                for (int i = 0; i < ring.size(); ++i) {
                    double[] a = ring.get(i);
                    double[] b = ring.get((i + 1) % ring.size());
                    double az = a[1];
                    double bz = b[1];
                    if (!(az <= scanZ && bz > scanZ) && (!(bz <= scanZ) || !(az > scanZ))) continue;
                    double t = (scanZ - az) / (bz - az);
                    crossings.add(a[0] + t * (b[0] - a[0]));
                }
            }
            crossings.sort(Double::compareTo);
            for (int j = crossings.size() - 1; j > 0; --j) {
                if (Math.abs(crossings.get(j) - crossings.get(j - 1)) < 0.01) {
                    crossings.remove(j);
                }
            }
            int i = 0;
            while (i + 1 < crossings.size()) {
                int startX = (int) Math.round(crossings.get(i));
                int endX = (int) Math.round(crossings.get(i + 1));
                int x = startX;
                while (x <= endX) {
                    result.add(new int[]{x++, z});
                }
                i += 2;
            }
        }
        return result;
    }

    public static List<int[]> thickLine(List<double[]> polyline, int width) {
        int lo = -((width - 1) / 2);
        int hi = width / 2;
        LinkedHashMap unique = new LinkedHashMap();
        int i = 0;
        while (i + 1 < polyline.size()) {
            double[] a = (double[])polyline.get(i);
            double[] b = (double[])polyline.get(i + 1);
            double dx = b[0] - a[0];
            double dz = b[1] - a[1];
            double length = Math.sqrt((double)(dx * dx + dz * dz));
            int steps = Math.max((int)1, (int)((int)Math.ceil((double)length)));
            for (int s = 0; s <= steps; ++s) {
                double t = (double)s / (double)steps;
                int cx = (int)Math.round((double)(a[0] + dx * t));
                int cz = (int)Math.round((double)(a[1] + dz * t));
                for (int ox = lo; ox <= hi; ++ox) {
                    for (int oz = lo; oz <= hi; ++oz) {
                        int x = cx + ox;
                        int z = cz + oz;
                        unique.putIfAbsent((Object)PolygonRasterizer.packXZ(x, z), (Object)new int[]{x, z});
                    }
                }
            }
            ++i;
        }
        return new ArrayList(unique.values());
    }

    public static List<int[]> outline(List<double[]> closedPolygon) {
        LinkedHashMap<Long, int[]> unique = new LinkedHashMap<>();
        double cumulative = 0.0;
        int i = 0;
        while (i + 1 < closedPolygon.size()) {
            double[] a = closedPolygon.get(i);
            double[] b = closedPolygon.get(i + 1);
            double dx = b[0] - a[0];
            double dz = b[1] - a[1];
            double length = Math.sqrt(dx * dx + dz * dz);
            int steps = Math.max(1, (int) Math.ceil(length));
            for (int s = 0; s <= steps; ++s) {
                double t = (double) s / (double) steps;
                int x = (int) Math.round(a[0] + dx * t);
                int z = (int) Math.round(a[1] + dz * t);
                long key = PolygonRasterizer.packXZ(x, z);
                // "along" tracks true distance travelled along the perimeter, not just the count
                // of unique voxel columns seen so far. On a diagonal wall the two diverge: fewer
                // unique columns are visited per unit of real length than on a straight wall, which
                // used to desync the window/pier rhythm and show up as diagonal banding.
                int along = (int) Math.round(cumulative + length * t);
                if (unique.containsKey(key)) continue;
                unique.put(key, new int[]{x, z, along});
            }
            cumulative += length;
            ++i;
        }
        return new ArrayList<>(unique.values());
    }

    private static long packXZ(int x, int z) {
        return (long)(x & 0xFFFF) << 16 | (long)(z & 0xFFFF);
    }

    public static int widthForRoadType(String highwayTag) {
        if (highwayTag == null) return 4;
        switch (highwayTag) {
            case "motorway":
            case "trunk":
                return 11;
            case "primary":
                return 9;
            case "secondary":
                return 7;
            case "tertiary":
            case "residential":
            case "unclassified":
                return 5;
            case "service":
            case "track":
                return 3;
            case "footway":
            case "path":
            case "pedestrian":
            case "steps":
                return 2;
            case "cycleway":
                return 3;
            default:
                return 4;
        }
    }
}
