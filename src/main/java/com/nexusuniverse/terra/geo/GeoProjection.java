package com.nexusuniverse.terra.geo;

/**
 * Converts real-world lat/lon into local Minecraft block coordinates,
 * relative to a chosen origin point. Uses a flat equirectangular
 * projection -- not accurate for continental distances, but well within
 * a few centimeters of error across the kind of radius (a few hundred
 * meters to a couple kilometers) this tool actually generates in one
 * request. Full Mercator/UTM projection math would be overkill here and
 * add distortion of its own at this scale.
 */
public class GeoProjection {

    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    private final double originLat;
    private final double originLon;
    private final double metersPerDegreeLon;
    /** Blocks per meter. 1.0 = 1 block per real-world meter. */
    private final double scale;

    public GeoProjection(double originLat, double originLon, double scale) {
        this.originLat = originLat;
        this.originLon = originLon;
        this.scale = scale;
        this.metersPerDegreeLon = METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(originLat));
    }

    /**
     * Converts a lat/lon into block-space X/Z offsets from the origin.
     * Minecraft's +Z is south, so northward movement (increasing
     * latitude) must produce a negative Z offset.
     */
    public double[] toBlockOffset(double lat, double lon) {
        double eastMeters = (lon - originLon) * metersPerDegreeLon;
        double northMeters = (lat - originLat) * METERS_PER_DEGREE_LAT;
        double blockX = eastMeters * scale;
        double blockZ = -northMeters * scale;
        return new double[]{blockX, blockZ};
    }

    /** Inverse of toBlockOffset -- used to build the bounding box for API queries. */
    public GeoPoint toLatLon(double blockX, double blockZ) {
        double eastMeters = blockX / scale;
        double northMeters = -blockZ / scale;
        double lon = originLon + (eastMeters / metersPerDegreeLon);
        double lat = originLat + (northMeters / METERS_PER_DEGREE_LAT);
        return new GeoPoint(lat, lon);
    }

    /** Degrees of latitude corresponding to a given real-world meter radius. */
    public double radiusMetersToDegreesLat(double radiusMeters) {
        return radiusMeters / METERS_PER_DEGREE_LAT;
    }

    /** Degrees of longitude corresponding to a given real-world meter radius, at this origin's latitude. */
    public double radiusMetersToDegreesLon(double radiusMeters) {
        return radiusMeters / metersPerDegreeLon;
    }
}
