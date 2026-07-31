/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  java.lang.Math
 *  java.lang.Object
 */
package com.nexusuniverse.terra.geo;

import com.nexusuniverse.terra.geo.GeoPoint;
import java.lang.Math;
import java.lang.Object;

public class GeoProjection {
    private static final double METERS_PER_DEGREE_LAT = 111320.0;
    private final double originLat;
    private final double originLon;
    private final double metersPerDegreeLon;
    private final double scale;

    public GeoProjection(double originLat, double originLon, double scale) {
        this.originLat = originLat;
        this.originLon = originLon;
        this.scale = scale;
        this.metersPerDegreeLon = 111320.0 * Math.cos((double)Math.toRadians((double)originLat));
    }

    public double[] toBlockOffset(double lat, double lon) {
        double eastMeters = (lon - this.originLon) * this.metersPerDegreeLon;
        double northMeters = (lat - this.originLat) * 111320.0;
        double blockX = eastMeters * this.scale;
        double blockZ = -northMeters * this.scale;
        return new double[]{blockX, blockZ};
    }

    public GeoPoint toLatLon(double blockX, double blockZ) {
        double eastMeters = blockX / this.scale;
        double northMeters = -blockZ / this.scale;
        double lon = this.originLon + eastMeters / this.metersPerDegreeLon;
        double lat = this.originLat + northMeters / 111320.0;
        return new GeoPoint(lat, lon);
    }

    public double radiusMetersToDegreesLat(double radiusMeters) {
        return radiusMeters / 111320.0;
    }

    public double radiusMetersToDegreesLon(double radiusMeters) {
        return radiusMeters / this.metersPerDegreeLon;
    }
}
