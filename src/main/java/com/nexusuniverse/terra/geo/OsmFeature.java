package com.nexusuniverse.terra.geo;

import java.util.List;

/**
 * One parsed OpenStreetMap "way" -- a building footprint, road
 * centerline, water body outline, or landuse area. The vertex list is
 * the way's real-world geometry, in order, as returned directly by
 * Overpass's "out geom" mode (no separate node-lookup pass needed).
 */
public record OsmFeature(Category category, String subtype, List<GeoPoint> vertices) {

    public enum Category { BUILDING, ROAD, WATER, LANDUSE }
}
