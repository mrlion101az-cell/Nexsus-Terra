package com.nexusuniverse.terra.generation;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts 2D polygon/line geometry (building footprints, water outlines,
 * road centerlines -- already projected into block-space X/Z) into the
 * actual set of block columns they cover.
 *
 * This is the part of the whole pipeline I'd flag as most worth
 * real-world tuning first -- the algorithms themselves (scanline fill,
 * thick-line stepping) are standard and correctly implemented, but the
 * *parameters* (road width per type, how buildings without a height tag
 * get their wall height) are reasonable defaults, not the result of
 * comparing against real generated output yet.
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
     * Rasterizes a polyline (road centerline) into a thick strip of block
     * columns, walking each segment in ~1-block steps and marking a
     * square of the given width centered on each step.
     */
    public static List<int[]> thickLine(List<double[]> polyline, int width) {
        List<int[]> result = new ArrayList<>();
        int half = Math.max(1, width / 2);

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
                for (int ox = -half; ox <= half; ox++) {
                    for (int oz = -half; oz <= half; oz++) {
                        result.add(new int[]{cx + ox, cz + oz});
                    }
                }
            }
        }

        return result;
    }

    /** Road width in blocks, by OSM highway tag. Reasonable defaults, not tuned against real output yet. */
    public static int widthForRoadType(String highwayTag) {
        return switch (highwayTag) {
            case "motorway", "trunk" -> 10;
            case "primary" -> 8;
            case "secondary" -> 6;
            case "tertiary", "residential" -> 5;
            case "service", "track" -> 3;
            case "footway", "path", "pedestrian" -> 2;
            default -> 4;
        };
    }
}
